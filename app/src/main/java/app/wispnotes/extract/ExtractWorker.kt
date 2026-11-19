package app.wispnotes.extract

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.wispnotes.index.NoteEntry
import app.wispnotes.index.WispDb
import app.wispnotes.note.Frontmatter
import app.wispnotes.note.Source
import app.wispnotes.note.Template
import app.wispnotes.vault.SafVaultRepository
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Runs after a note is saved: fetches the page and moves the note's `extracted` from pending to done, partial or failed. */
class ExtractWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val name = inputData.getString(KEY_FILE) ?: return Result.failure()
        val repo = SafVaultRepository(applicationContext)
        val current = repo.editNote(name) { it }.getOrElse { return Result.failure() }
        val url = Frontmatter.text(current, "url") ?: return Result.failure()
        // The template the note was saved with, or null for a note saved before templates existed.
        // Read from the vault only when the note names one: no extra SAF work for plain notes.
        val templateName = Frontmatter.text(current, "wisp_template")
        val template = if (templateName == null) {
            null
        } else {
            withContext(Dispatchers.IO) {
                repo.readTemplates().mapNotNull { (_, text) -> Template.fromJson(text).getOrNull() }
            }.firstOrNull { it.name == templateName }
        }
        val attempts = Frontmatter.text(current, "extract_attempts")?.toIntOrNull() ?: 0
        if (attempts >= MAX_ATTEMPTS) {
            val failed = repo.editNote(name) { Frontmatter.set(it, mapOf("extracted" to "failed")) }
                .getOrElse { return Result.failure() }
            NoteEntry.parse(name, failed)?.let { WispDb.get(applicationContext).notes().put(it) }
            return Result.success()
        }
        val nextAttempt = attempts + 1
        repo.editNote(name) { Frontmatter.set(it, mapOf("extract_attempts" to nextAttempt.toString())) }
            .getOrElse { return Result.failure() }

        var found = withContext(Dispatchers.IO) { Extractors.extract(url, OkHttpFetcher::get, OkHttpFetcher::post) }
        Log.d(TAG, "stage=fast_path status=${found.status} transcript_status=${found.transcriptStatus} count=${found.transcript.size}")
        if (Source.detect(url).source == "youtube" && found.transcriptStatus != "done") {
            Log.d(TAG, "stage=webview_start reason=fast_path_incomplete")
            val fallback = YoutubeTranscriptWebView.fetch(applicationContext, url)
            Log.d(TAG, "stage=webview_result count=${fallback?.size ?: 0} error=${fallback == null}")
            if (!fallback.isNullOrEmpty()) {
                found = found.copy(transcript = fallback, transcriptStatus = "done")
            } else if (found.transcriptStatus != "none") {
                found = found.copy(transcriptStatus = "none")
            }
        }
        // Nothing found: try again (offline, slow server) before recording "failed".
        if (found.status == "failed" && nextAttempt < MAX_ATTEMPTS) return Result.retry()

        found = withContext(Dispatchers.IO) { keepPicture(repo, name, url, found) }
        val updated = repo.editNote(name) { Merge.apply(it, found, template) }.getOrElse { return Result.failure() }
        NoteEntry.parse(name, updated)?.let { WispDb.get(applicationContext).notes().put(it) }
        return Result.success()
    }

    /**
     * Instagram's `og:image` is a signed CDN link with an `oe=` expiry about four days out
     * (measured 15 Nov 2026: `oe=6AFEE63B` -> 19 Nov). Stored as a URL, the card goes blank a week
     * later and never comes back. So the bytes are fetched once, while the link still works, and
     * written next to the note. Any failure falls back to the URL unchanged.
     */
    private suspend fun keepPicture(repo: SafVaultRepository, noteFile: String, url: String, found: Extracted): Extracted {
        val remote = found.thumbnail ?: return found
        if (!remote.startsWith("http")) return found
        if (Source.detect(url).source != "instagram") return found // the case with a signed link; other sources keep stable URLs
        val bytes = OkHttpFetcher.getBytes(remote) ?: return found
        val ext = Images.extension(bytes) ?: return found
        val saved = repo.writeFile(Images.fileName(noteFile, ext), Images.mime(ext), bytes)
            .getOrNull() ?: return found
        Log.d(TAG, "stage=picture_kept file=$saved bytes=${bytes.size}")
        return found.copy(thumbnail = saved)
    }

    companion object {
        private const val TAG = "WispTranscript"
        private const val KEY_FILE = "file"
        const val MAX_ATTEMPTS = 3

        fun enqueue(context: Context, fileName: String, replace: Boolean = false) {
            val request = OneTimeWorkRequestBuilder<ExtractWorker>()
                .setInputData(workDataOf(KEY_FILE to fileName))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "extract-$fileName",
                if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
                request,
            )
        }
    }
}
