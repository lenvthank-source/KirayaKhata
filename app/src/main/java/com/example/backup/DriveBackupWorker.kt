package com.example.backup

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.local.KirayaDatabaseHolder
import java.io.File

class DriveBackupWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val prefs = BackupPreferences(applicationContext)
        val status = prefs.getCurrentStatus()

        if (!status.isGoogleConnected || status.frequency == BackupFrequency.MANUAL) {
            return Result.success()
        }

        val accessToken = GoogleDriveAuthHelper.authorizeDriveSilent(applicationContext)
        if (accessToken.isNullOrBlank()) {
            prefs.recordError("Drive permission needs renewal. Open Backup & Restore to back up.")
            return Result.failure()
        }

        val tempSnapshot = File(applicationContext.cacheDir, "worker_kiraya_snapshot.db")
        return try {
            KirayaDatabaseHolder.exportDatabaseSnapshot(applicationContext, tempSnapshot)
            val client = GoogleDriveBackupClient()
            val uploadResult = client.uploadDatabaseBackup(accessToken, tempSnapshot)
            uploadResult.fold(
                onSuccess = { meta ->
                    prefs.recordBackupSuccess(
                        timeMs = meta.modifiedTimeMs,
                        sizeBytes = meta.sizeBytes
                    )
                    Result.success()
                },
                onFailure = { error ->
                    Log.w(TAG, "Scheduled backup failed", error)
                    prefs.recordError(error.localizedMessage ?: "Scheduled Drive backup failed.")
                    Result.retry()
                }
            )
        } catch (e: Exception) {
            Log.w(TAG, "Unexpected worker failure", e)
            prefs.recordError(e.localizedMessage ?: "Scheduled Drive backup failed.")
            Result.retry()
        } finally {
            if (tempSnapshot.exists()) {
                tempSnapshot.delete()
            }
        }
    }

    companion object {
        const val UNIQUE_WORK_NAME = "kiraya_drive_periodic_backup"
        private const val TAG = "DriveBackupWorker"
    }
}
