package com.example.ui

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.HomeWork
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.backup.AppThemeMode
import com.example.backup.BackupFrequency
import com.example.backup.BackupStatusState
import com.example.backup.BillOcrSettings
import com.example.backup.DriveBackupMetadata
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BackupRestoreScreen(
    status: BackupStatusState,
    themeMode: AppThemeMode,
    multiHouseEnabled: Boolean,
    ocrSettings: BillOcrSettings,
    remoteBackupInfo: DriveBackupMetadata?,
    isBusy: Boolean,
    showDirectDriveOAuth: Boolean,
    pendingDriveRestore: DriveBackupMetadata?,
    driveConsentFlow: kotlinx.coroutines.flow.Flow<DriveConsentPrompt>,
    onBack: () -> Unit,
    onSelectThemeMode: (AppThemeMode) -> Unit,
    onSetMultiHouseEnabled: (Boolean) -> Unit,
    onOpenManageProperties: () -> Unit,
    onConnectGoogle: () -> Unit,
    onDisconnectGoogle: () -> Unit,
    onSelectFrequency: (BackupFrequency) -> Unit,
    onBackupNow: () -> Unit,
    onRequestDriveRestore: () -> Unit,
    onConfirmDriveRestore: () -> Unit,
    onDismissDriveRestore: () -> Unit,
    onDriveConsentResult: (PendingDriveAction, android.content.Intent?) -> Unit,
    onExportLocalDb: (Uri) -> Unit,
    onShareBackupToDrive: () -> Unit,
    onImportLocalDb: (Uri) -> Unit,
    onSaveOcrSettings: (preferGemini: Boolean, apiKey: String) -> Unit
) {
    BackHandler(onBack = onBack)

    var pendingConsentAction by remember { mutableStateOf<PendingDriveAction?>(null) }
    var pendingLocalImportUri by remember { mutableStateOf<Uri?>(null) }

    var preferGeminiOcr by remember(ocrSettings.preferGeminiOcr) {
        mutableStateOf(ocrSettings.preferGeminiOcr)
    }
    var geminiKeyInput by remember(ocrSettings.userGeminiApiKey) {
        mutableStateOf(ocrSettings.userGeminiApiKey)
    }
    var showApiKey by remember { mutableStateOf(false) }

    val driveConsentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val action = pendingConsentAction
        pendingConsentAction = null
        if (action != null && result.resultCode == Activity.RESULT_OK) {
            onDriveConsentResult(action, result.data)
        }
    }

    LaunchedEffect(driveConsentFlow) {
        driveConsentFlow.collect { prompt ->
            pendingConsentAction = prompt.action
            driveConsentLauncher.launch(
                IntentSenderRequest.Builder(prompt.intentSender).build()
            )
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            onExportLocalDb(uri)
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            pendingLocalImportUri = uri
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("backup_back_button")
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back to app"
                    )
                }
                Spacer(Modifier.width(4.dp))
                Column {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Theme, Houses, Backup & Restore, Bill OCR & API",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Appearance & Theme Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = MaterialTheme.shapes.large,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.Palette,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Appearance & Theme",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Color memory map for faster visual scanning",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        AppThemeMode.entries.forEach { mode ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectThemeMode(mode) }
                                    .padding(vertical = 4.dp)
                                    .testTag("theme_mode_${mode.name.lowercase()}"),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = themeMode == mode,
                                    onClick = { onSelectThemeMode(mode) }
                                )
                                Spacer(Modifier.width(6.dp))
                                Column {
                                    Text(
                                        text = mode.label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = mode.subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Property Management Mode Card (Single House vs Multi-House)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = MaterialTheme.shapes.large,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.HomeWork,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Property Management",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = if (multiHouseEnabled) {
                                    "Multi-House Mode Active"
                                } else {
                                    "Single-House Mode (Default)"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSetMultiHouseEnabled(!multiHouseEnabled) }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Multi-House Management",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = if (multiHouseEnabled) {
                                    "Shows house filters, grouped tenant sections, and separate main meters per building."
                                } else {
                                    "Clean single-house layout. Enable if you manage tenants across multiple houses."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Switch(
                            checked = multiHouseEnabled,
                            onCheckedChange = onSetMultiHouseEnabled,
                            modifier = Modifier.testTag("multi_house_toggle_switch")
                        )
                    }

                    if (multiHouseEnabled) {
                        OutlinedButton(
                            onClick = onOpenManageProperties,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("settings_manage_houses_button")
                        ) {
                            Icon(Icons.Rounded.HomeWork, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Add or Edit Houses")
                        }
                    }
                }
            }
        }

        // Primary Backup & Restore Hero Card (Zero-Config Google Drive & Phone Storage)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = MaterialTheme.shapes.large,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Column(
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = if (status.lastBackupTimeMs > 0L) Icons.Rounded.CloudDone else Icons.Rounded.Backup,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Backup & Restore",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (status.lastBackupTimeMs > 0L) {
                                    "Last backup: ${formatBackupTime(status.lastBackupTimeMs)} • ${formatByteSize(status.lastBackupSizeBytes)}"
                                } else {
                                    "No backup created yet"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Text(
                        text = "Save a complete KirayaKhata database (.db) directly to Google Drive or Phone Storage, or restore it anytime — no account setup needed.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Button(
                        onClick = {
                            val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
                            exportLauncher.launch("KirayaKhata_Backup_$stamp.db")
                        },
                        enabled = !isBusy,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("export_local_db_button")
                    ) {
                        if (isBusy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(10.dp))
                            Text("Working…")
                        } else {
                            Icon(Icons.Rounded.FileUpload, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Save Backup (Drive / Phone)")
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onShareBackupToDrive,
                            enabled = !isBusy,
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("share_backup_to_drive_button")
                        ) {
                            Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Share to Drive", maxLines = 1)
                        }

                        OutlinedButton(
                            onClick = {
                                importLauncher.launch(
                                    arrayOf(
                                        "application/octet-stream",
                                        "application/x-sqlite3",
                                        "application/vnd.sqlite3",
                                        "*/*"
                                    )
                                )
                            },
                            enabled = !isBusy,
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("import_local_db_button")
                        ) {
                            Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Restore Backup", maxLines = 1)
                        }
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Tip: Choose Google Drive or Downloads in the file picker menu (☰). If Drive is hidden on Save, tap Share to Drive and select Save to Drive.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }
        }

        // Optional Direct Google Account Auto-Backup Card (only shown in builds configured with Google Sign-In)
        if (showDirectDriveOAuth) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = MaterialTheme.shapes.large,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (status.isGoogleConnected) Icons.Rounded.AccountCircle else Icons.Rounded.CloudOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Direct Google Account Auto-Backup",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }

                        if (remoteBackupInfo != null) {
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Google Drive copy found: ${formatBackupTime(remoteBackupInfo.modifiedTimeMs)} (${formatByteSize(remoteBackupInfo.sizeBytes)})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }

                        if (!status.lastError.isNullOrBlank()) {
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = status.lastError,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (status.isGoogleConnected) {
                                        status.connectedName?.takeIf { it.isNotBlank() } ?: "Google Account"
                                    } else {
                                        "Not connected"
                                    },
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = status.connectedEmail ?: "Optional — connect for background Drive sync",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            if (status.isGoogleConnected) {
                                OutlinedButton(
                                    onClick = onDisconnectGoogle,
                                    enabled = !isBusy,
                                    modifier = Modifier.testTag("disconnect_google_button")
                                ) {
                                    Text("Disconnect")
                                }
                            } else {
                                OutlinedButton(
                                    onClick = onConnectGoogle,
                                    enabled = !isBusy,
                                    modifier = Modifier.testTag("connect_google_button")
                                ) {
                                    Text("Connect")
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Rounded.Schedule,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Auto-Backup Schedule",
                                style = MaterialTheme.typography.titleSmall
                            )
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            BackupFrequency.entries.forEach { freq ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = !isBusy) { onSelectFrequency(freq) }
                                        .padding(vertical = 4.dp)
                                        .testTag("freq_${freq.name.lowercase()}"),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = status.frequency == freq,
                                        onClick = { if (!isBusy) onSelectFrequency(freq) }
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Column {
                                        Text(
                                            text = freq.label,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = freq.subtitle,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = onBackupNow,
                                enabled = !isBusy,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("drive_backup_now_button")
                            ) {
                                Icon(Icons.Rounded.Backup, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Back Up")
                            }

                            OutlinedButton(
                                onClick = onRequestDriveRestore,
                                enabled = !isBusy,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("restore_from_drive_button")
                            ) {
                                Icon(Icons.Rounded.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Restore")
                            }
                        }
                    }
                }
            }
        }

        // Smart Bill OCR & Optional User-Saved Gemini API Key Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = MaterialTheme.shapes.large,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.DocumentScanner,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Bill OCR & Optional API Key",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { preferGeminiOcr = false }
                            .padding(vertical = 4.dp)
                            .testTag("ocr_mode_offline"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = !preferGeminiOcr,
                            onClick = { preferGeminiOcr = false }
                        )
                        Spacer(Modifier.width(6.dp))
                        Column {
                            Text(
                                text = "Offline Free OCR (On-Device ML Kit — No API)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "100% free, unlimited, and works offline on camera photos, gallery bills & PDFs.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { preferGeminiOcr = true }
                            .padding(vertical = 4.dp)
                            .testTag("ocr_mode_gemini"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = preferGeminiOcr,
                            onClick = { preferGeminiOcr = true }
                        )
                        Spacer(Modifier.width(6.dp))
                        Column {
                            Text(
                                text = "Gemini AI OCR (Uses Your Saved API Key)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Uses your personal Google AI Studio free-tier key (20+ free requests/day). Automatically falls back to ML Kit if offline or blank.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    OutlinedTextField(
                        value = geminiKeyInput,
                        onValueChange = { geminiKeyInput = it },
                        label = { Text("Your Gemini API Key (Optional)") },
                        placeholder = { Text("AIza… (stored locally on this device only)") },
                        singleLine = true,
                        visualTransformation = if (showApiKey) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        trailingIcon = {
                            IconButton(onClick = { showApiKey = !showApiKey }) {
                                Icon(
                                    imageVector = if (showApiKey) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                    contentDescription = if (showApiKey) "Hide API key" else "Show API key"
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("gemini_api_key_input")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { onSaveOcrSettings(preferGeminiOcr, geminiKeyInput) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("save_ocr_settings_button")
                        ) {
                            Text("Save OCR Settings")
                        }
                        if (geminiKeyInput.isNotBlank() || ocrSettings.hasGeminiKey) {
                            OutlinedButton(
                                onClick = {
                                    geminiKeyInput = ""
                                    preferGeminiOcr = false
                                    onSaveOcrSettings(false, "")
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("clear_ocr_key_button")
                            ) {
                                Text("Clear")
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(48.dp)) }
    }

    if (pendingDriveRestore != null) {
        AlertDialog(
            onDismissRequest = onDismissDriveRestore,
            icon = {
                Icon(
                    Icons.Rounded.WarningAmber,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary
                )
            },
            title = { Text("Restore Google Drive Backup?") },
            text = {
                Text(
                    "Found backup from ${formatBackupTime(pendingDriveRestore.modifiedTimeMs)} (${formatByteSize(pendingDriveRestore.sizeBytes)}).\n\nRestoring will replace all tenants, rent entries, and main meter logs currently on this device."
                )
            },
            confirmButton = {
                Button(
                    onClick = onConfirmDriveRestore,
                    modifier = Modifier.testTag("confirm_drive_restore_button")
                ) {
                    Text("Restore Now")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissDriveRestore) {
                    Text("Cancel")
                }
            }
        )
    }

    val localUri = pendingLocalImportUri
    if (localUri != null) {
        AlertDialog(
            onDismissRequest = { pendingLocalImportUri = null },
            icon = {
                Icon(
                    Icons.Rounded.WarningAmber,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary
                )
            },
            title = { Text("Restore Backup File?") },
            text = {
                Text(
                    "The selected database file will be verified and will replace all tenants, monthly rent records, and main meter logs currently stored on this device."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        pendingLocalImportUri = null
                        onImportLocalDb(localUri)
                    },
                    modifier = Modifier.testTag("confirm_local_restore_button")
                ) {
                    Text("Verify & Restore")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingLocalImportUri = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

fun formatBackupTime(timeMs: Long): String {
    if (timeMs <= 0L) return "Never"
    val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.forLanguageTag("en-IN"))
    return sdf.format(Date(timeMs))
}

fun formatByteSize(bytes: Long): String {
    if (bytes <= 0L) return "0 KB"
    val kb = bytes / 1024.0
    return if (kb < 1024.0) {
        String.format(Locale.US, "%.0f KB", kb.coerceAtLeast(1.0))
    } else {
        String.format(Locale.US, "%.2f MB", kb / 1024.0)
    }
}
