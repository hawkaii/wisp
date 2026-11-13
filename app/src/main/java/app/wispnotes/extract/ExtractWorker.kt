package app.wispnotes.extract

import android.content.Context
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

        val found = withContext(Dispatchers.IO) { Extractors.extract(url, OkHttpFetcher::get) }
        // Nothing found: try again (offline, slow server) before recording "failed".
        if (found.status == "failed" && runAttemptCount < MAX_RETRIES) return Result.retry()

        val updated = repo.editInboxNote(name) { Merge.apply(it, found) }.getOrElse { return Result.failure() }
        NoteEntry.parse(name, updated)?.let { WispDb.get(applicationContext).notes().put(it) }
        return Result.success()
    }

    companion object {
        private const val KEY_FILE = "file"
        private const val MAX_RETRIES = 2

        fun enqueue(context: Context, fileName: String) {
            val request = OneTimeWorkRequestBuilder<ExtractWorker>()
                .setInputData(workDataOf(KEY_FILE to fileName))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("extract-$fileName", ExistingWorkPolicy.KEEP, request)
        }
    }
}
