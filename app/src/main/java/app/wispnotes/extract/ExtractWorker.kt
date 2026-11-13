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
import app.wispnotes.vault.SafVaultRepository
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Runs after a note is saved: fetches the page and moves the note's `extracted` from pending to done, partial or failed. */
class ExtractWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val name = inputData.getString(KEY_FILE) ?: return Result.failure()
        val repo = SafVaultRepository(applicationContext)
        val current = repo.editInboxNote(name) { it }.getOrElse { return Result.failure() }
        val url = Frontmatter.text(current, "url") ?: return Result.failure()
        val attempts = Frontmatter.text(current, "extract_attempts")?.toIntOrNull() ?: 0
        if (attempts >= MAX_ATTEMPTS) {
            val failed = repo.editInboxNote(name) { Frontmatter.set(it, mapOf("extracted" to "failed")) }
                .getOrElse { return Result.failure() }
            NoteEntry.parse(name, failed)?.let { WispDb.get(applicationContext).notes().put(it) }
            return Result.success()
        }
        val nextAttempt = attempts + 1
        repo.editInboxNote(name) { Frontmatter.set(it, mapOf("extract_attempts" to nextAttempt.toString())) }
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

        val updated = repo.editInboxNote(name) { Merge.apply(it, found) }.getOrElse { return Result.failure() }
        NoteEntry.parse(name, updated)?.let { WispDb.get(applicationContext).notes().put(it) }
        return Result.success()
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
