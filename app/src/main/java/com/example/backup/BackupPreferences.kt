package com.example.backup

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.backupDataStore: DataStore<Preferences> by preferencesDataStore(name = "backup_prefs")

enum class BackupFrequency(val label: String, val subtitle: String) {
    MANUAL(
        label = "Only when I tap",
        subtitle = "Manual backup and local file export only"
    ),
    DAILY(
        label = "Daily",
        subtitle = "Automatically back up to Google Drive every 24 hours"
    ),
    WEEKLY(
        label = "Weekly",
        subtitle = "Automatically back up to Google Drive every 7 days"
    );

    companion object {
        fun fromRaw(raw: String?): BackupFrequency =
            entries.firstOrNull { it.name == raw } ?: MANUAL
    }
}

data class BackupStatusState(
    val connectedEmail: String? = null,
    val connectedName: String? = null,
    val frequency: BackupFrequency = BackupFrequency.MANUAL,
    val lastBackupTimeMs: Long = 0L,
    val lastBackupSizeBytes: Long = 0L,
    val lastError: String? = null
) {
    val isGoogleConnected: Boolean get() = !connectedEmail.isNullOrBlank()
}

data class BillOcrSettings(
    val preferGeminiOcr: Boolean = false,
    val userGeminiApiKey: String = ""
) {
    val hasGeminiKey: Boolean get() = userGeminiApiKey.isNotBlank()
}

enum class AppThemeMode(val label: String, val subtitle: String) {
    SYSTEM(
        label = "System Default",
        subtitle = "Automatically match your phone's Dark or Light mode"
    ),
    DARK(
        label = "Dark",
        subtitle = "Deep charcoal surfaces with semantic color cues"
    ),
    LIGHT(
        label = "Light (White)",
        subtitle = "Crisp white cards with high-contrast semantic colors"
    );

    companion object {
        fun fromRaw(raw: String?): AppThemeMode =
            entries.firstOrNull { it.name == raw } ?: DARK
    }
}

class BackupPreferences(private val context: Context) {

    private object Keys {
        val CONNECTED_EMAIL = stringPreferencesKey("connected_email")
        val CONNECTED_NAME = stringPreferencesKey("connected_name")
        val FREQUENCY = stringPreferencesKey("backup_frequency")
        val LAST_BACKUP_TIME_MS = longPreferencesKey("last_backup_time_ms")
        val LAST_BACKUP_SIZE_BYTES = longPreferencesKey("last_backup_size_bytes")
        val LAST_ERROR = stringPreferencesKey("last_backup_error")
        val SELECTED_DISCOM_ID = stringPreferencesKey("selected_discom_id")
        val PREFER_GEMINI_OCR = booleanPreferencesKey("prefer_gemini_ocr")
        val USER_GEMINI_API_KEY = stringPreferencesKey("user_gemini_api_key")
        val APP_THEME_MODE = stringPreferencesKey("app_theme_mode")
        val MULTI_HOUSE_ENABLED = booleanPreferencesKey("multi_house_enabled")
    }

    val statusFlow: Flow<BackupStatusState> = context.backupDataStore.data.map { prefs ->
        BackupStatusState(
            connectedEmail = prefs[Keys.CONNECTED_EMAIL],
            connectedName = prefs[Keys.CONNECTED_NAME],
            frequency = BackupFrequency.fromRaw(prefs[Keys.FREQUENCY]),
            lastBackupTimeMs = prefs[Keys.LAST_BACKUP_TIME_MS] ?: 0L,
            lastBackupSizeBytes = prefs[Keys.LAST_BACKUP_SIZE_BYTES] ?: 0L,
            lastError = prefs[Keys.LAST_ERROR]
        )
    }

    val selectedDiscomIdFlow: Flow<String> = context.backupDataStore.data.map { prefs ->
        prefs[Keys.SELECTED_DISCOM_ID] ?: ""
    }

    val ocrSettingsFlow: Flow<BillOcrSettings> = context.backupDataStore.data.map { prefs ->
        BillOcrSettings(
            preferGeminiOcr = prefs[Keys.PREFER_GEMINI_OCR] ?: false,
            userGeminiApiKey = prefs[Keys.USER_GEMINI_API_KEY] ?: ""
        )
    }

    val themeModeFlow: Flow<AppThemeMode> = context.backupDataStore.data.map { prefs ->
        AppThemeMode.fromRaw(prefs[Keys.APP_THEME_MODE])
    }

    val multiHouseEnabledFlow: Flow<Boolean> = context.backupDataStore.data.map { prefs ->
        prefs[Keys.MULTI_HOUSE_ENABLED] ?: false
    }

    suspend fun getCurrentStatus(): BackupStatusState = statusFlow.first()

    suspend fun setConnectedAccount(email: String, name: String?) {
        context.backupDataStore.edit { prefs ->
            prefs[Keys.CONNECTED_EMAIL] = email
            if (!name.isNullOrBlank()) {
                prefs[Keys.CONNECTED_NAME] = name
            } else {
                prefs.remove(Keys.CONNECTED_NAME)
            }
            prefs.remove(Keys.LAST_ERROR)
        }
    }

    suspend fun clearConnectedAccount() {
        context.backupDataStore.edit { prefs ->
            prefs.remove(Keys.CONNECTED_EMAIL)
            prefs.remove(Keys.CONNECTED_NAME)
            prefs[Keys.FREQUENCY] = BackupFrequency.MANUAL.name
            prefs.remove(Keys.LAST_ERROR)
        }
    }

    suspend fun setFrequency(frequency: BackupFrequency) {
        context.backupDataStore.edit { prefs ->
            prefs[Keys.FREQUENCY] = frequency.name
        }
    }

    suspend fun recordBackupSuccess(timeMs: Long, sizeBytes: Long) {
        context.backupDataStore.edit { prefs ->
            prefs[Keys.LAST_BACKUP_TIME_MS] = timeMs
            prefs[Keys.LAST_BACKUP_SIZE_BYTES] = sizeBytes
            prefs.remove(Keys.LAST_ERROR)
        }
    }

    suspend fun recordError(message: String?) {
        context.backupDataStore.edit { prefs ->
            if (message.isNullOrBlank()) {
                prefs.remove(Keys.LAST_ERROR)
            } else {
                prefs[Keys.LAST_ERROR] = message
            }
        }
    }

    suspend fun setSelectedDiscomId(discomId: String) {
        context.backupDataStore.edit { prefs ->
            if (discomId.isBlank()) {
                prefs.remove(Keys.SELECTED_DISCOM_ID)
            } else {
                prefs[Keys.SELECTED_DISCOM_ID] = discomId
            }
        }
    }

    suspend fun saveOcrSettings(preferGemini: Boolean, apiKey: String) {
        context.backupDataStore.edit { prefs ->
            prefs[Keys.PREFER_GEMINI_OCR] = preferGemini
            val trimmed = apiKey.trim()
            if (trimmed.isEmpty()) {
                prefs.remove(Keys.USER_GEMINI_API_KEY)
            } else {
                prefs[Keys.USER_GEMINI_API_KEY] = trimmed
            }
        }
    }

    suspend fun setThemeMode(mode: AppThemeMode) {
        context.backupDataStore.edit { prefs ->
            prefs[Keys.APP_THEME_MODE] = mode.name
        }
    }

    suspend fun setMultiHouseEnabled(enabled: Boolean) {
        context.backupDataStore.edit { prefs ->
            prefs[Keys.MULTI_HOUSE_ENABLED] = enabled
        }
    }
}
