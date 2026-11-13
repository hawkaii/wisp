package app.wispnotes.review

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.wispnotes.MainActivity
import app.wispnotes.R
import app.wispnotes.index.NoteEntry
import app.wispnotes.vault.SafVaultRepository
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/** Once a day: if any done note is due for review, post one notification. Without the permission it stays quiet. */
class ReviewWorker(private val ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val today = LocalDate.now()
        val due = SafVaultRepository(ctx).listInboxNotes()
            .mapNotNull { NoteEntry.parse(it.fileName, it.content) }
            .count { it.isDue(today) }
        if (due > 0) notify(ctx, due)
        return Result.success()
    }

    companion object {
        private const val CHANNEL = "review"

        // shortcut: WorkManager picks the time of day (about 24h after the first run); pin it to a morning slot if that matters.
        fun schedule(ctx: Context) {
            val request = PeriodicWorkRequestBuilder<ReviewWorker>(1, TimeUnit.DAYS).build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("review-daily", ExistingPeriodicWorkPolicy.KEEP, request)
        }

        @SuppressLint("MissingPermission") // checked just below
        private fun notify(ctx: Context, due: Int) {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) return
            val nm = NotificationManagerCompat.from(ctx)
            nm.createNotificationChannel(
                NotificationChannelCompat.Builder(CHANNEL, NotificationManagerCompat.IMPORTANCE_DEFAULT).setName("Review reminders").build(),
            )
            val open = PendingIntent.getActivity(
                ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val text = if (due == 1) "1 note is ready to revisit." else "$due notes are ready to revisit."
            nm.notify(
                1,
                NotificationCompat.Builder(ctx, CHANNEL)
                    .setSmallIcon(R.drawable.ic_stat_ghost)
                    .setContentTitle("Wisp")
                    .setContentText(text)
                    .setContentIntent(open)
                    .setAutoCancel(true)
                    .build(),
            )
        }
    }
}
