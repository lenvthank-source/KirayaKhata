package com.example.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.outlined.ElectricMeter
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ElectricMeter
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.data.LocalLedgerRepository
import com.example.model.UiState
import com.example.ui.theme.KirayaKhataTheme

private enum class KirayaTab(
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    LEDGER(R.string.tab_ledger, Icons.AutoMirrored.Rounded.ReceiptLong, Icons.AutoMirrored.Outlined.ReceiptLong),
    TENANTS(R.string.tab_tenants, Icons.Rounded.Group, Icons.Outlined.Group),
    MAIN_METER(R.string.tab_main_meter, Icons.Rounded.ElectricMeter, Icons.Outlined.ElectricMeter)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KirayaKhataApp() {
    val viewModel: KirayaViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                KirayaViewModel(
                    appContext = app.applicationContext,
                    repository = LocalLedgerRepository(app.applicationContext)
                )
            }
        }
    )

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val multiHouseEnabled by viewModel.multiHouseEnabled.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    val isScanningBill by viewModel.isScanningBill.collectAsStateWithLifecycle()
    val selectedDiscomId by viewModel.selectedDiscomId.collectAsStateWithLifecycle()
    val ocrSettings by viewModel.ocrSettings.collectAsStateWithLifecycle()
    val backupStatus by viewModel.backupStatus.collectAsStateWithLifecycle()
    val isBackupBusy by viewModel.isBackupBusy.collectAsStateWithLifecycle()
    val remoteBackupInfo by viewModel.remoteBackupInfo.collectAsStateWithLifecycle()
    val pendingDriveRestore by viewModel.showDriveRestoreDialog.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val activityContext = remember(context) { context.findActivity() ?: context }

    var activeTab by rememberSaveable { mutableStateOf(KirayaTab.LEDGER) }
    var showAddTenantSheet by rememberSaveable { mutableStateOf(false) }
    var showManagePropertiesSheet by rememberSaveable { mutableStateOf(false) }
    var showSettingsScreen by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.snackbarEvents.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    KirayaKhataTheme(themeMode = themeMode) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useNavigationRail = maxWidth >= 600.dp

            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = MaterialTheme.colorScheme.background,
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                topBar = {
                    if (!showSettingsScreen) {
                        TopAppBar(
                            title = {
                                Column {
                                    Text(
                                        text = stringResource(R.string.app_name),
                                        style = MaterialTheme.typography.headlineSmall
                                    )
                                    Text(
                                        text = stringResource(activeTab.labelRes),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            actions = {
                                IconButton(
                                    onClick = { showSettingsScreen = true },
                                    modifier = Modifier.testTag("top_bar_backup_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Settings,
                                        contentDescription = stringResource(R.string.settings_title),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.background
                            )
                        )
                    }
                },
                bottomBar = {
                    if (!showSettingsScreen && !useNavigationRail) {
                        NavigationBar(
                            windowInsets = WindowInsets.navigationBars,
                            containerColor = MaterialTheme.colorScheme.surface
                        ) {
                            KirayaTab.entries.forEach { tab ->
                                val selected = activeTab == tab
                                val title = stringResource(tab.labelRes)
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { activeTab = tab },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = title
                                        )
                                    },
                                    label = { Text(title) },
                                    modifier = Modifier.testTag("nav_${tab.name.lowercase()}")
                                )
                            }
                        }
                    }
                },
                floatingActionButton = {
                    if (!showSettingsScreen && activeTab == KirayaTab.LEDGER) {
                        FloatingActionButton(
                            onClick = {
                                activeTab = KirayaTab.TENANTS
                                showAddTenantSheet = true
                            },
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.testTag("fab_add_tenant")
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.add_tenant))
                        }
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (!showSettingsScreen && useNavigationRail) {
                        NavigationRail(
                            containerColor = MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .fillMaxHeight()
                                .windowInsetsPadding(WindowInsets.navigationBars)
                        ) {
                            Spacer(Modifier.height(12.dp))
                            KirayaTab.entries.forEach { tab ->
                                val selected = activeTab == tab
                                val title = stringResource(tab.labelRes)
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { activeTab = tab },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = title
                                        )
                                    },
                                    label = { Text(title) },
                                    modifier = Modifier.testTag("rail_${tab.name.lowercase()}")
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Box(
                            modifier = Modifier
                                .widthIn(max = 720.dp)
                                .fillMaxSize()
                        ) {
                            if (showSettingsScreen) {
                                BackupRestoreScreen(
                                    status = backupStatus,
                                    themeMode = themeMode,
                                    multiHouseEnabled = multiHouseEnabled,
                                    ocrSettings = ocrSettings,
                                    remoteBackupInfo = remoteBackupInfo,
                                    isBusy = isBackupBusy,
                                    showDirectDriveOAuth = viewModel.isDirectDriveSignInAvailable,
                                    pendingDriveRestore = pendingDriveRestore,
                                    driveConsentFlow = viewModel.driveConsentPrompt,
                                    onBack = { showSettingsScreen = false },
                                    onSelectThemeMode = viewModel::setThemeMode,
                                    onSetMultiHouseEnabled = viewModel::setMultiHouseEnabled,
                                    onOpenManageProperties = {
                                        showSettingsScreen = false
                                        showManagePropertiesSheet = true
                                    },
                                    onConnectGoogle = { viewModel.connectGoogleAccount(activityContext) },
                                    onDisconnectGoogle = viewModel::disconnectGoogleAccount,
                                    onSelectFrequency = { freq ->
                                        viewModel.updateBackupFrequency(activityContext, freq)
                                    },
                                    onBackupNow = { viewModel.backupToGoogleDriveNow(activityContext) },
                                    onRequestDriveRestore = { viewModel.checkAndPromptDriveRestore(activityContext) },
                                    onConfirmDriveRestore = viewModel::confirmDriveRestore,
                                    onDismissDriveRestore = viewModel::dismissDriveRestoreDialog,
                                    onDriveConsentResult = viewModel::onDriveConsentResult,
                                    onExportLocalDb = viewModel::exportLocalBackup,
                                    onShareBackupToDrive = { viewModel.shareBackupToDrive(activityContext) },
                                    onImportLocalDb = viewModel::importLocalBackup,
                                    onSaveOcrSettings = viewModel::saveOcrSettings
                                )
                            } else {
                                when (val state = uiState) {
                                    UiState.Loading -> {
                                        Column(
                                            modifier = Modifier.fillMaxSize(),
                                            verticalArrangement = Arrangement.Center,
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                            Spacer(Modifier.height(12.dp))
                                            Text(
                                                text = stringResource(R.string.loading_records),
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    is UiState.Error -> {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(24.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            EmptySectionCard(
                                                icon = Icons.Rounded.ErrorOutline,
                                                title = "Unable to load local records",
                                                subtitle = state.message
                                            )
                                        }
                                    }

                                    is UiState.Success -> {
                                        val data = state.data
                                        when (activeTab) {
                                            KirayaTab.LEDGER -> {
                                                LedgerScreen(
                                                    data = data,
                                                    isSaving = isSaving,
                                                    isScanningBill = isScanningBill,
                                                    onPreviousMonth = { viewModel.shiftMonth(-1) },
                                                    onNextMonth = { viewModel.shiftMonth(1) },
                                                    onSelectMonth = viewModel::selectMonth,
                                                    onSelectPropertyFilter = viewModel::selectPropertyFilter,
                                                    onManageProperties = { showManagePropertiesSheet = true },
                                                    onSelectFilter = viewModel::selectFilter,
                                                    onOpenAddTenant = {
                                                        activeTab = KirayaTab.TENANTS
                                                        showAddTenantSheet = true
                                                    },
                                                    onOpenBackupRestore = { showSettingsScreen = true },
                                                    onScanBill = viewModel::scanBillFromUri,
                                                    onSaveMonthlyEntry = viewModel::saveMonthlyLedger,
                                                    onDeleteMonthlyEntry = viewModel::deleteMonthlyLedger
                                                )
                                            }

                                            KirayaTab.TENANTS -> {
                                                TenantsScreen(
                                                    data = data,
                                                    isSaving = isSaving,
                                                    showAddTenantSheet = showAddTenantSheet,
                                                    onSetShowAddTenantSheet = { showAddTenantSheet = it },
                                                    onSearchQueryChange = viewModel::setSearchQuery,
                                                    onSelectPropertyFilter = viewModel::selectPropertyFilter,
                                                    onManageProperties = { showManagePropertiesSheet = true },
                                                    onToggleShowVacated = viewModel::toggleShowVacatedTenants,
                                                    onSaveTenant = viewModel::saveTenant,
                                                    onSetTenantActive = viewModel::setTenantActive,
                                                    onDeleteTenant = viewModel::deleteTenant
                                                )
                                            }

                                            KirayaTab.MAIN_METER -> {
                                                MainMeterScreen(
                                                    data = data,
                                                    backupStatus = backupStatus,
                                                    selectedDiscomId = selectedDiscomId,
                                                    isSaving = isSaving,
                                                    isScanningBill = isScanningBill,
                                                    onPreviousMonth = { viewModel.shiftMonth(-1) },
                                                    onNextMonth = { viewModel.shiftMonth(1) },
                                                    onSelectMonth = viewModel::selectMonth,
                                                    onSelectPropertyFilter = viewModel::selectPropertyFilter,
                                                    onManageProperties = { showManagePropertiesSheet = true },
                                                    onSetPropertyTrackMainMeter = viewModel::setPropertyTrackMainMeter,
                                                    onSelectDiscom = viewModel::setPropertyDiscomId,
                                                    onScanBill = viewModel::scanBillFromUri,
                                                    onNotify = viewModel::notifyMessage,
                                                    onSaveMainMeterLog = viewModel::saveMainMeterLog,
                                                    onSaveBillingSettings = viewModel::saveBillingSettings,
                                                    onOpenSettings = { showSettingsScreen = true }
                                                )
                                            }
                                        }

                                        if (showManagePropertiesSheet && multiHouseEnabled) {
                                            ManagePropertiesBottomSheet(
                                                properties = data.properties,
                                                isSaving = isSaving,
                                                onDismiss = { showManagePropertiesSheet = false },
                                                onSaveProperty = { propId, name, addr, trackMain, consumerNo, onDone ->
                                                    viewModel.saveProperty(
                                                        propertyId = propId,
                                                        name = name,
                                                        address = addr,
                                                        trackMainMeter = trackMain,
                                                        consumerNumber = consumerNo,
                                                        onSuccess = onDone
                                                    )
                                                },
                                                onToggleTrackMainMeter = viewModel::setPropertyTrackMainMeter,
                                                onDeleteProperty = viewModel::deleteProperty
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
