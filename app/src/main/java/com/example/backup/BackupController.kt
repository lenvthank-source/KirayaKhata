package com.example.backup

import android.content.Context
import android.net.Uri
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.data.local.KirayaDatabaseHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class BackupController(
    private val appContext: Context,
    scope: CoroutineScope
) {
    private val prefs = BackupPreferences(appContext)
    private val driveClient = GoogleDriveBackupClient()

    val statusState: StateFlow<BackupStatusState> = prefs.statusFlow.stateIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = BackupStatusState()
    )

    val selectedDiscomId: StateFlow<String> = prefs.selectedDiscomIdFlow.stateIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = ""
    )

    val ocrSettings: StateFlow<BillOcrSettings> = prefs.ocrSettingsFlow.stateIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = BillOcrSettings()
    )

    val themeMode: StateFlow<AppThemeMode> = prefs.themeModeFlow.stateIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = AppThemeMode.DARK
    )

    val multiHouseEnabled: StateFlow<Boolean> = prefs.multiHouseEnabledFlow.stateIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = false
    )

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _remoteBackupInfo = MutableStateFlow<DriveBackupMetadata?>(null)
    val remoteBackupInfo: StateFlow<DriveBackupMetadata?> = _remoteBackupInfo.asStateFlow()

    suspend fun setSelectedDiscomId(discomId: String) {
        prefs.setSelectedDiscomId(discomId)
    }

    suspend fun saveOcrSettings(preferGemini: Boolean, apiKey: String) {
        prefs.saveOcrSettings(preferGemini, apiKey)
    }

    suspend fun setThemeMode(mode: AppThemeMode) {
        prefs.setThemeMode(mode)
    }

    suspend fun setMultiHouseEnabled(enabled: Boolean) {
        prefs.setMultiHouseEnabled(enabled)
    }

    suspend fun connectGoogleAccount(activityContext: Context): Result<GoogleAccountInfo> {
        _isBusy.value = true
        return try {
            val result = GoogleDriveAuthHelper.signInWithGoogle(activityContext)
            result.onSuccess { account ->
                prefs.setConnectedAccount(account.email, account.displayName)
            }
            result
        } finally {
            _isBusy.value = false
        }
    }

    suspend fun disconnectGoogleAccount() {
        _isBusy.value = true
        try {
            GoogleDriveAuthHelper.disconnectGoogle(appContext)
            prefs.clearConnectedAccount()
            _remoteBackupInfo.value = null
            WorkManager.getInstance(appContext).cancelUniqueWork(DriveBackupWorker.UNIQUE_WORK_NAME)
        } finally {
            _isBusy.value = false
        }
    }

    suspend fun setBackupFrequency(frequency: BackupFrequency) {
        prefs.setFrequency(frequency)
        val workManager = WorkManager.getInstance(appContext)
        when (frequency) {
            BackupFrequency.MANUAL -> {
                workManager.cancelUniqueWork(DriveBackupWorker.UNIQUE_WORK_NAME)
            }
            BackupFrequency.DAILY -> {
                val request = PeriodicWorkRequestBuilder<DriveBackupWorker>(1, TimeUnit.DAYS)
                    .setConstraints(
                        Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build()
                    )
                    .build()
                workManager.enqueueUniquePeriodicWork(
                    DriveBackupWorker.UNIQUE_WORK_NAME,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    request
                )
            }
            BackupFrequency.WEEKLY -> {
                val request = PeriodicWorkRequestBuilder<DriveBackupWorker>(7, TimeUnit.DAYS)
                    .setConstraints(
                        Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build()
                    )
                    .build()
                workManager.enqueueUniquePeriodicWork(
                    DriveBackupWorker.UNIQUE_WORK_NAME,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    request
                )
            }
        }
    }

    suspend fun requestDriveConsentOrToken(): Result<DriveAuthOutcome> {
        return GoogleDriveAuthHelper.requestDriveAuthorization(appContext)
    }

    suspend fun performDriveBackup(accessToken: String): Result<DriveBackupMetadata> {
        _isBusy.value = true
        val tempFile = File(appContext.cacheDir, "manual_drive_snapshot.db")
        return try {
            withContext(Dispatchers.IO) {
                KirayaDatabaseHolder.exportDatabaseSnapshot(appContext, tempFile)
            }
            val result = driveClient.uploadDatabaseBackup(accessToken, tempFile)
            result.fold(
                onSuccess = { meta ->
                    prefs.recordBackupSuccess(meta.modifiedTimeMs, meta.sizeBytes)
                    _remoteBackupInfo.value = meta
                    Result.success(meta)
                },
                onFailure = { error ->
                    prefs.recordError(error.localizedMessage)
                    Result.failure(error)
                }
            )
        } catch (e: Exception) {
            prefs.recordError(e.localizedMessage)
            Result.failure(e)
        } finally {
            if (tempFile.exists()) tempFile.delete()
            _isBusy.value = false
        }
    }

    suspend fun checkRemoteBackup(accessToken: String): Result<DriveBackupMetadata?> {
        _isBusy.value = true
        return try {
            val result = driveClient.findLatestBackup(accessToken)
            result.onSuccess { meta ->
                _remoteBackupInfo.value = meta
            }.onFailure { error ->
                prefs.recordError(error.localizedMessage)
            }
            result
        } finally {
            _isBusy.value = false
        }
    }

    suspend fun restoreFromDrive(accessToken: String): Result<Unit> {
        _isBusy.value = true
        val tempFile = File(appContext.cacheDir, "drive_restore_candidate.db")
        return try {
            val meta = _remoteBackupInfo.value
                ?: driveClient.findLatestBackup(accessToken).getOrElse { return Result.failure(it) }
                ?: return Result.failure(IllegalStateException("No KirayaKhata backup found in your Google Drive."))

            driveClient.downloadBackup(accessToken, meta.fileId, tempFile)
                .getOrElse { return Result.failure(it) }

            withContext(Dispatchers.IO) {
                KirayaDatabaseHolder.replaceDatabaseFromFile(appContext, tempFile)
            }
            prefs.recordBackupSuccess(meta.modifiedTimeMs, meta.sizeBytes)
            Result.success(Unit)
        } catch (e: Exception) {
            prefs.recordError(e.localizedMessage)
            Result.failure(e)
        } finally {
            if (tempFile.exists()) tempFile.delete()
            _isBusy.value = false
        }
    }

    suspend fun exportLocalDbToUri(uri: Uri): Result<Long> {
        _isBusy.value = true
        val tempFile = File(appContext.cacheDir, "local_export_snapshot.db")
        return try {
            withContext(Dispatchers.IO) {
                KirayaDatabaseHolder.exportDatabaseSnapshot(appContext, tempFile)
                val outStream = appContext.contentResolver.openOutputStream(uri)
                    ?: throw IOException("Unable to open destination file for writing.")
                outStream.use { output ->
                    tempFile.inputStream().use { input ->
                        input.copyTo(output)
                    }
                }
                tempFile.length()
            }.let { bytes ->
                prefs.recordBackupSuccess(System.currentTimeMillis(), bytes)
                Result.success(bytes)
            }
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            if (tempFile.exists()) tempFile.delete()
            _isBusy.value = false
        }
    }

    suspend fun importLocalDbFromUri(uri: Uri): Result<Unit> {
        _isBusy.value = true
        val tempFile = File(appContext.cacheDir, "local_restore_candidate.db")
        return try {
            withContext(Dispatchers.IO) {
                val inStream = appContext.contentResolver.openInputStream(uri)
                    ?: throw IOException("Unable to open selected backup file.")
                inStream.use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                KirayaDatabaseHolder.replaceDatabaseFromFile(appContext, tempFile)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            if (tempFile.exists()) tempFile.delete()
            _isBusy.value = false
        }
    }
}
