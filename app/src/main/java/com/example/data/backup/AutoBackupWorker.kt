package com.example.data.backup

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.NoorApplication

class AutoBackupWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val app = applicationContext as? NoorApplication ?: return Result.success()
            val repo = app.repository
            val settings = repo.settings.value
            val schedule = settings.backupSchedule
            if (schedule == "off") return Result.success()

            val intervalDays = when (schedule) {
                "7" -> 7L
                "15" -> 15L
                "30", "monthly" -> 30L
                "custom" -> settings.backupCustomDays.toLong().coerceIn(1L, 365L)
                else -> 30L
            }
            val now = System.currentTimeMillis()
            val elapsedMs = now - settings.lastBackupAt
            if (settings.lastBackupAt == 0L || elapsedMs >= intervalDays * 86_400_000L) {
                repo.saveAutoBackupSnapshot()
            }
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
