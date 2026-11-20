package com.example.ui

import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.backup.AppThemeMode
import com.example.backup.BackupController
import com.example.backup.BackupFrequency
import com.example.backup.BackupStatusState
import com.example.backup.BillOcrSettings
import com.example.backup.DriveAuthOutcome
import com.example.backup.DriveBackupMetadata
import com.example.backup.GoogleDriveAuthHelper
import com.example.data.LocalLedgerRepository
import com.example.domain.BillingCalculator
import com.example.domain.MeterReconciliation
import com.example.domain.MonthlyDashboardSummary
import com.example.domain.ReconciliationChartData
import com.example.domain.TenantMonthSummary
import com.example.model.BillingSettings
import com.example.model.DEFAULT_PROPERTY_ID
import com.example.model.ElectricityMode
import com.example.model.LedgerFilter
import com.example.model.MainMeterLog
import com.example.model.MeterTrackingMode
import com.example.model.MonthlyLedger
import com.example.model.PaymentMode
import com.example.model.Property
import com.example.model.Tenant
import com.example.model.UiState
import com.example.ocr.BillOcrEngine
import com.example.ocr.ExtractedBillData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PropertyLedgerGroup(
    val property: Property,
    val items: List<TenantMonthSummary>
)

data class PropertyTenantGroup(
    val property: Property,
    val tenants: List<Tenant>
)

data class KirayaDashboardData(
    val multiHouseEnabled: Boolean,
    val properties: List<Property>,
    val selectedPropertyId: String?,
    val activeMainMeterProperty: Property,
    val searchQuery: String,
    val selectedMonthKey: String,
    val selectedFilter: LedgerFilter,
    val showVacatedTenants: Boolean,
    val tenants: List<Tenant>,
    val allLedgers: List<MonthlyLedger>,
    val mainMeterLogs: List<MainMeterLog>,
    val mainMeterLogsForActiveProperty: List<MainMeterLog>,
    val settings: BillingSettings,
    val monthSummaries: List<TenantMonthSummary>,
    val filteredSummaries: List<TenantMonthSummary>,
    val groupedLedgerSections: List<PropertyLedgerGroup>,
    val groupedTenantSections: List<PropertyTenantGroup>,
    val dashboardSummary: MonthlyDashboardSummary,
    val currentMainMeterLog: MainMeterLog?,
    val reconciliation: MeterReconciliation,
    val chartData: ReconciliationChartData
)

enum class PendingDriveAction {
    BACKUP_NOW,
    CHECK_REMOTE_FOR_RESTORE,
    CHECK_REMOTE_SILENT
}

data class DriveConsentPrompt(
    val intentSender: IntentSender,
    val action: PendingDriveAction
)

class KirayaViewModel(
    private val appContext: Context,
    private val repository: LocalLedgerRepository
) : ViewModel() {

    private val backupController = BackupController(appContext.applicationContext, viewModelScope)
    private val billOcrEngine = BillOcrEngine()

    private val _selectedMonthKey = MutableStateFlow(BillingCalculator.currentMonthKey())
    val selectedMonthKey: StateFlow<String> = _selectedMonthKey.asStateFlow()

    private val _selectedFilter = MutableStateFlow(LedgerFilter.ALL)
    val selectedFilter: StateFlow<LedgerFilter> = _selectedFilter.asStateFlow()

    private val _showVacatedTenants = MutableStateFlow(false)
    val showVacatedTenants: StateFlow<Boolean> = _showVacatedTenants.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedPropertyId = MutableStateFlow<String?>(null)
    val selectedPropertyId: StateFlow<String?> = _selectedPropertyId.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _isScanningBill = MutableStateFlow(false)
    val isScanningBill: StateFlow<Boolean> = _isScanningBill.asStateFlow()

    private val _snackbarEvents = MutableSharedFlow<String>(extraBufferCapacity = 6)
    val snackbarEvents = _snackbarEvents.asSharedFlow()

    val backupStatus: StateFlow<BackupStatusState> = backupController.statusState
    val selectedDiscomId: StateFlow<String> = backupController.selectedDiscomId
    val ocrSettings: StateFlow<BillOcrSettings> = backupController.ocrSettings
    val themeMode: StateFlow<AppThemeMode> = backupController.themeMode
    val multiHouseEnabled: StateFlow<Boolean> = backupController.multiHouseEnabled
    val isBackupBusy: StateFlow<Boolean> = backupController.isBusy
    val remoteBackupInfo: StateFlow<DriveBackupMetadata?> = backupController.remoteBackupInfo

    private val _driveConsentPrompt = MutableSharedFlow<DriveConsentPrompt>(extraBufferCapacity = 2)
    val driveConsentPrompt = _driveConsentPrompt.asSharedFlow()

    private val _showDriveRestoreDialog = MutableStateFlow<DriveBackupMetadata?>(null)
    val showDriveRestoreDialog: StateFlow<DriveBackupMetadata?> = _showDriveRestoreDialog.asStateFlow()

    private fun <T> Flow<T>.toUiStateFlow(label: String): Flow<UiState<T>> =
        this.map<T, UiState<T>> { UiState.Success(it) }
            .catch { error ->
                if (error is CancellationException) throw error
                Log.w(TAG, "Error observing $label", error)
                emit(UiState.Error(error.localizedMessage ?: "Failed to load $label"))
            }

    private val propertiesState = repository.observeProperties().toUiStateFlow("properties")
    private val tenantsState = repository.observeTenants().toUiStateFlow("tenants")
    private val ledgersState = repository.observeMonthlyLedgers().toUiStateFlow("monthly ledgers")
    private val mainLogsState = repository.observeMainMeterLogs().toUiStateFlow("main meter logs")
    private val settingsState = repository.observeBillingSettings().toUiStateFlow("billing settings")

    private data class CombinedInputs(
        val properties: UiState<List<Property>>,
        val tenants: UiState<List<Tenant>>,
        val ledgers: UiState<List<MonthlyLedger>>,
        val mainLogs: UiState<List<MainMeterLog>>,
        val settings: UiState<BillingSettings>
    )

    private data class UiControlFilters(
        val monthKey: String,
        val filter: LedgerFilter,
        val showVacated: Boolean,
        val searchQuery: String,
        val selectedPropertyId: String?
    )

    private val dbInputsFlow = combine(
        propertiesState,
        tenantsState,
        ledgersState,
        mainLogsState,
        settingsState
    ) { p, t, l, m, s ->
        CombinedInputs(p, t, l, m, s)
    }

    private val uiFiltersFlow = combine(
        _selectedMonthKey,
        _selectedFilter,
        _showVacatedTenants,
        _searchQuery,
        _selectedPropertyId
    ) { monthKey, filter, showVacated, query, propId ->
        UiControlFilters(monthKey, filter, showVacated, query, propId)
    }

    val uiState: StateFlow<UiState<KirayaDashboardData>> = combine(
        dbInputsFlow,
        uiFiltersFlow,
        backupController.multiHouseEnabled
    ) { inputs, ui, multiHouse ->
        val firstError = listOf(
            inputs.properties,
            inputs.tenants,
            inputs.ledgers,
            inputs.mainLogs,
            inputs.settings
        ).filterIsInstance<UiState.Error>().firstOrNull()
        if (firstError != null) {
            return@combine firstError
        }

        val propertiesSuccess = inputs.properties as? UiState.Success
        val tenantsSuccess = inputs.tenants as? UiState.Success
        val ledgersSuccess = inputs.ledgers as? UiState.Success
        val mainLogsSuccess = inputs.mainLogs as? UiState.Success
        val settingsSuccess = inputs.settings as? UiState.Success

        if (propertiesSuccess == null ||
            tenantsSuccess == null ||
            ledgersSuccess == null ||
            mainLogsSuccess == null ||
            settingsSuccess == null
        ) {
            return@combine UiState.Loading
        }

        val properties = propertiesSuccess.data.ifEmpty {
            listOf(Property(id = DEFAULT_PROPERTY_ID, name = "Main House", trackMainMeter = true))
        }
        val propertyMap = properties.associateBy { it.id }
        val fallbackProperty = properties.first()
        val effectiveSelectedPropertyId = if (multiHouse) {
            ui.selectedPropertyId?.takeIf { it in propertyMap }
        } else {
            null
        }

        val tenants = tenantsSuccess.data
        val allLedgers = ledgersSuccess.data
        val mainLogs = mainLogsSuccess.data
        val settings = settingsSuccess.data
        val monthKey = ui.monthKey

        val activeOrMonthTenants = tenants.filter { tenant ->
            tenant.active || allLedgers.any { it.tenantId == tenant.id && it.monthKey == monthKey }
        }

        val allMonthSummaries = activeOrMonthTenants.map { tenant ->
            BillingCalculator.buildTenantMonthSummary(
                tenant = tenant,
                monthKey = monthKey,
                allLedgers = allLedgers
            )
        }

        val propertyScopedSummaries = if (effectiveSelectedPropertyId == null) {
            allMonthSummaries
        } else {
            allMonthSummaries.filter { it.tenant.propertyId == effectiveSelectedPropertyId }
        }

        // First page (Ledger) does not have a search bar, so searchQuery applies only to Tenants tab.
        val filteredSummaries = when (ui.filter) {
            LedgerFilter.ALL -> propertyScopedSummaries
            LedgerFilter.DUE -> propertyScopedSummaries.filter { it.closingBalance > 0.005 }
            LedgerFilter.SETTLED -> propertyScopedSummaries.filter { it.isRecorded && it.closingBalance in -0.005..0.005 }
            LedgerFilter.ADVANCE -> propertyScopedSummaries.filter { it.closingBalance < -0.005 }
            LedgerFilter.PENDING_ENTRY -> propertyScopedSummaries.filter { !it.isRecorded }
        }

        val ledgerByPropertyId = filteredSummaries.groupBy { summary ->
            if (summary.tenant.propertyId in propertyMap) summary.tenant.propertyId else fallbackProperty.id
        }
        val groupedLedgerSections = properties.mapNotNull { prop ->
            val items = ledgerByPropertyId[prop.id].orEmpty()
            if (items.isEmpty()) null else PropertyLedgerGroup(prop, items)
        }

        val queryLower = ui.searchQuery.trim().lowercase()
        fun matchesTenantSearch(tenant: Tenant): Boolean {
            if (queryLower.isEmpty()) return true
            val propName = if (multiHouse) propertyMap[tenant.propertyId]?.name.orEmpty().lowercase() else ""
            return tenant.name.lowercase().contains(queryLower) ||
                tenant.roomNumber.lowercase().contains(queryLower) ||
                (propName.isNotEmpty() && propName.contains(queryLower))
        }

        val visibleTenants = tenants
            .filter { ui.showVacated || it.active }
            .filter { effectiveSelectedPropertyId == null || it.propertyId == effectiveSelectedPropertyId }
            .filter { matchesTenantSearch(it) }

        val tenantsByPropertyId = visibleTenants.groupBy { tenant ->
            if (tenant.propertyId in propertyMap) tenant.propertyId else fallbackProperty.id
        }
        val groupedTenantSections = properties.mapNotNull { prop ->
            val list = tenantsByPropertyId[prop.id].orEmpty()
            if (list.isEmpty()) null else PropertyTenantGroup(prop, list)
        }

        val dashboardSummary = BillingCalculator.buildMonthlyDashboard(propertyScopedSummaries)

        val activeMainProperty = if (multiHouse) {
            if (effectiveSelectedPropertyId != null) {
                propertyMap[effectiveSelectedPropertyId] ?: fallbackProperty
            } else {
                properties.firstOrNull { it.trackMainMeter } ?: fallbackProperty
            }
        } else {
            propertyMap[DEFAULT_PROPERTY_ID]
                ?: properties.firstOrNull { it.trackMainMeter }
                ?: fallbackProperty
        }

        val mainLogsForActive = mainLogs.filter { it.propertyId == activeMainProperty.id }
        val currentMainLog = mainLogsForActive.firstOrNull { it.monthKey == monthKey }
        val subMeterTenantIds = if (multiHouse) {
            tenants
                .filter { it.propertyId == activeMainProperty.id && it.tracksSubMeter }
                .map { it.id }
                .toSet()
        } else {
            tenants
                .filter { it.tracksSubMeter }
                .map { it.id }
                .toSet()
        }

        val reconciliation = BillingCalculator.buildReconciliation(
            monthKey = monthKey,
            mainLog = currentMainLog,
            allLedgers = allLedgers,
            includedSubMeterTenantIds = subMeterTenantIds
        )
        val chartData = BillingCalculator.buildConsumptionChartData(
            monthKey = monthKey,
            tenants = tenants,
            mainLog = currentMainLog,
            allLedgers = allLedgers,
            propertyIdFilter = if (multiHouse) activeMainProperty.id else null
        )

        UiState.Success(
            KirayaDashboardData(
                multiHouseEnabled = multiHouse,
                properties = properties,
                selectedPropertyId = effectiveSelectedPropertyId,
                activeMainMeterProperty = activeMainProperty,
                searchQuery = ui.searchQuery,
                selectedMonthKey = monthKey,
                selectedFilter = ui.filter,
                showVacatedTenants = ui.showVacated,
                tenants = tenants,
                allLedgers = allLedgers,
                mainMeterLogs = mainLogs,
                mainMeterLogsForActiveProperty = mainLogsForActive,
                settings = settings,
                monthSummaries = allMonthSummaries,
                filteredSummaries = filteredSummaries,
                groupedLedgerSections = groupedLedgerSections,
                groupedTenantSections = groupedTenantSections,
                dashboardSummary = dashboardSummary,
                currentMainMeterLog = currentMainLog,
                reconciliation = reconciliation,
                chartData = chartData
            )
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = UiState.Loading
    )

    fun selectMonth(monthKey: String) {
        _selectedMonthKey.value = monthKey
    }

    fun shiftMonth(deltaMonths: Int) {
        _selectedMonthKey.value = BillingCalculator.shiftMonthKey(_selectedMonthKey.value, deltaMonths)
    }

    fun selectFilter(filter: LedgerFilter) {
        _selectedFilter.value = filter
    }

    fun toggleShowVacatedTenants() {
        _showVacatedTenants.value = !_showVacatedTenants.value
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectPropertyFilter(propertyId: String?) {
        _selectedPropertyId.value = propertyId
    }

    fun saveProperty(
        propertyId: String?,
        name: String,
        address: String = "",
        trackMainMeter: Boolean = true,
        consumerNumber: String = "",
        discomId: String = "",
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _isSaving.value = true
            val result = repository.saveProperty(
                propertyId = propertyId,
                name = name,
                address = address,
                trackMainMeter = trackMainMeter,
                consumerNumber = consumerNumber,
                discomId = discomId
            )
            _isSaving.value = false
            result.fold(
                onSuccess = {
                    _snackbarEvents.tryEmit(if (propertyId == null) "House added" else "House updated")
                    onSuccess()
                },
                onFailure = { e ->
                    _snackbarEvents.tryEmit(e.localizedMessage ?: "Failed to save house")
                }
            )
        }
    }

    fun setPropertyTrackMainMeter(propertyId: String, trackMainMeter: Boolean) {
        viewModelScope.launch {
            val result = repository.setPropertyTrackMainMeter(propertyId, trackMainMeter)
            result.fold(
                onSuccess = {
                    _snackbarEvents.tryEmit(
                        if (trackMainMeter) "Main meter monitoring enabled" else "Main meter monitoring turned off"
                    )
                },
                onFailure = { e ->
                    _snackbarEvents.tryEmit(e.localizedMessage ?: "Failed to update house meter setting")
                }
            )
        }
    }

    fun setPropertyDiscomId(propertyId: String, discomId: String) {
        viewModelScope.launch {
            repository.setPropertyDiscomId(propertyId, discomId)
            backupController.setSelectedDiscomId(discomId)
        }
    }

    fun deleteProperty(propertyId: String, fallbackPropertyId: String) {
        viewModelScope.launch {
            _isSaving.value = true
            val result = repository.deletePropertyAndReassign(propertyId, fallbackPropertyId)
            _isSaving.value = false
            result.fold(
                onSuccess = {
                    if (_selectedPropertyId.value == propertyId) {
                        _selectedPropertyId.value = null
                    }
                    _snackbarEvents.tryEmit("House deleted; tenants moved safely")
                },
                onFailure = { e ->
                    _snackbarEvents.tryEmit(e.localizedMessage ?: "Failed to delete house")
                }
            )
        }
    }

    fun saveTenant(
        tenantId: String?,
        propertyId: String = DEFAULT_PROPERTY_ID,
        name: String,
        roomNumber: String,
        phone: String,
        monthlyRent: Double,
        defaultMaintenance: Double = 0.0,
        meterTrackingMode: MeterTrackingMode = MeterTrackingMode.SUB_METER,
        initialMeterReading: Double,
        openingBalance: Double,
        active: Boolean,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            _isSaving.value = true
            val result = repository.saveTenant(
                tenantId = tenantId,
                propertyId = propertyId,
                name = name,
                roomNumber = roomNumber,
                phone = phone,
                monthlyRent = monthlyRent,
                defaultMaintenance = BillingCalculator.roundToTwoDecimals(defaultMaintenance),
                meterTrackingMode = meterTrackingMode,
                initialMeterReading = initialMeterReading,
                openingBalance = openingBalance,
                active = active
            )
            _isSaving.value = false
            result.fold(
                onSuccess = {
                    _snackbarEvents.tryEmit(if (tenantId == null) "Tenant added" else "Tenant updated")
                    onSuccess()
                },
                onFailure = { e ->
                    _snackbarEvents.tryEmit(e.localizedMessage ?: "Failed to save tenant")
                }
            )
        }
    }

    fun setTenantActive(tenantId: String, active: Boolean) {
        viewModelScope.launch {
            val result = repository.setTenantActive(tenantId, active)
            result.fold(
                onSuccess = {
                    _snackbarEvents.tryEmit(if (active) "Tenant marked active" else "Tenant marked vacated")
                },
                onFailure = { e ->
                    _snackbarEvents.tryEmit(e.localizedMessage ?: "Failed to update tenant status")
                }
            )
        }
    }

    fun deleteTenant(tenantId: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _isSaving.value = true
            val result = repository.deleteTenantAndLedgers(tenantId)
            _isSaving.value = false
            result.fold(
                onSuccess = {
                    _snackbarEvents.tryEmit("Tenant and records deleted")
                    onSuccess()
                },
                onFailure = { e ->
                    _snackbarEvents.tryEmit(e.localizedMessage ?: "Failed to delete tenant")
                }
            )
        }
    }

    fun saveMonthlyLedger(
        tenantId: String,
        monthKey: String,
        rentAmount: Double,
        prevReading: Double,
        currReading: Double,
        electricityMode: ElectricityMode,
        ratePerUnit: Double,
        electricityAmount: Double,
        maintenanceAmount: Double = 0.0,
        extraChargesAmount: Double = 0.0,
        extraChargesNote: String = "",
        previousBalance: Double,
        amountPaid: Double,
        paymentMode: PaymentMode,
        paymentNote: String,
        onSuccess: () -> Unit
    ) {
        val units = BillingCalculator.roundToTwoDecimals((currReading - prevReading).coerceAtLeast(0.0))
        val closing = BillingCalculator.roundToTwoDecimals(
            previousBalance + rentAmount + electricityAmount + maintenanceAmount + extraChargesAmount - amountPaid
        )
        viewModelScope.launch {
            _isSaving.value = true
            val result = repository.saveMonthlyLedger(
                tenantId = tenantId,
                monthKey = monthKey,
                rentAmount = BillingCalculator.roundToTwoDecimals(rentAmount),
                prevReading = BillingCalculator.roundToTwoDecimals(prevReading),
                currReading = BillingCalculator.roundToTwoDecimals(currReading),
                unitsConsumed = units,
                electricityMode = electricityMode,
                ratePerUnit = BillingCalculator.roundToTwoDecimals(ratePerUnit),
                electricityAmount = BillingCalculator.roundToTwoDecimals(electricityAmount),
                maintenanceAmount = BillingCalculator.roundToTwoDecimals(maintenanceAmount),
                extraChargesAmount = BillingCalculator.roundToTwoDecimals(extraChargesAmount),
                extraChargesNote = extraChargesNote,
                previousBalance = BillingCalculator.roundToTwoDecimals(previousBalance),
                amountPaid = BillingCalculator.roundToTwoDecimals(amountPaid),
                paymentMode = paymentMode,
                paymentNote = paymentNote,
                closingBalance = closing
            )
            _isSaving.value = false
            result.fold(
                onSuccess = {
                    _snackbarEvents.tryEmit("Monthly entry saved")
                    onSuccess()
                },
                onFailure = { e ->
                    _snackbarEvents.tryEmit(e.localizedMessage ?: "Failed to save monthly entry")
                }
            )
        }
    }

    fun deleteMonthlyLedger(ledgerId: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            val result = repository.deleteMonthlyLedger(ledgerId)
            result.fold(
                onSuccess = {
                    _snackbarEvents.tryEmit("Monthly entry deleted")
                    onSuccess()
                },
                onFailure = { e ->
                    _snackbarEvents.tryEmit(e.localizedMessage ?: "Failed to delete entry")
                }
            )
        }
    }

    fun saveMainMeterLog(
        propertyId: String = DEFAULT_PROPERTY_ID,
        monthKey: String,
        consumerNumber: String,
        prevReading: Double,
        currReading: Double,
        govtBillAmount: Double,
        billPaid: Boolean,
        notes: String,
        onSuccess: () -> Unit
    ) {
        val units = BillingCalculator.roundToTwoDecimals((currReading - prevReading).coerceAtLeast(0.0))
        viewModelScope.launch {
            _isSaving.value = true
            val result = repository.saveMainMeterLog(
                propertyId = propertyId,
                monthKey = monthKey,
                consumerNumber = consumerNumber,
                prevReading = BillingCalculator.roundToTwoDecimals(prevReading),
                currReading = BillingCalculator.roundToTwoDecimals(currReading),
                unitsConsumed = units,
                govtBillAmount = BillingCalculator.roundToTwoDecimals(govtBillAmount),
                billPaid = billPaid,
                notes = notes
            )
            _isSaving.value = false
            result.fold(
                onSuccess = {
                    _snackbarEvents.tryEmit("Main meter log saved")
                    onSuccess()
                },
                onFailure = { e ->
                    _snackbarEvents.tryEmit(e.localizedMessage ?: "Failed to save main meter log")
                }
            )
        }
    }

    fun saveBillingSettings(
        defaultRatePerUnit: Double,
        defaultElectricityMode: ElectricityMode,
        consumerNumber: String
    ) {
        viewModelScope.launch {
            _isSaving.value = true
            val result = repository.saveBillingSettings(
                defaultRatePerUnit = BillingCalculator.roundToTwoDecimals(defaultRatePerUnit),
                defaultElectricityMode = defaultElectricityMode,
                consumerNumber = consumerNumber
            )
            _isSaving.value = false
            result.fold(
                onSuccess = {
                    _snackbarEvents.tryEmit("Billing settings updated")
                },
                onFailure = { e ->
                    _snackbarEvents.tryEmit(e.localizedMessage ?: "Failed to save settings")
                }
            )
        }
    }

    // --- Backup & Google Drive Orchestration ---

    fun connectGoogleAccount(activityContext: Context, checkRemoteAfter: Boolean = true) {
        viewModelScope.launch {
            val result = backupController.connectGoogleAccount(activityContext)
            result.fold(
                onSuccess = { account ->
                    _snackbarEvents.tryEmit("Connected ${account.email}")
                    if (checkRemoteAfter) {
                        requestDriveAction(PendingDriveAction.CHECK_REMOTE_SILENT)
                    }
                },
                onFailure = { error ->
                    _snackbarEvents.tryEmit(error.localizedMessage ?: "Could not connect Google account")
                }
            )
        }
    }

    fun disconnectGoogleAccount() {
        viewModelScope.launch {
            backupController.disconnectGoogleAccount()
            _snackbarEvents.tryEmit("Google account disconnected. Local data stays safe.")
        }
    }

    fun updateBackupFrequency(activityContext: Context, frequency: BackupFrequency) {
        viewModelScope.launch {
            if (frequency != BackupFrequency.MANUAL && !backupStatus.value.isGoogleConnected) {
                val connectRes = backupController.connectGoogleAccount(activityContext)
                if (connectRes.isFailure) {
                    _snackbarEvents.tryEmit(
                        connectRes.exceptionOrNull()?.localizedMessage
                            ?: "Connect a Google account to enable scheduled backups"
                    )
                    return@launch
                }
            }
            backupController.setBackupFrequency(frequency)
            _snackbarEvents.tryEmit("Backup schedule: ${frequency.label}")
            if (frequency != BackupFrequency.MANUAL) {
                requestDriveAction(PendingDriveAction.CHECK_REMOTE_SILENT)
            }
        }
    }

    fun backupToGoogleDriveNow(activityContext: Context) {
        viewModelScope.launch {
            if (!backupStatus.value.isGoogleConnected) {
                val connectRes = backupController.connectGoogleAccount(activityContext)
                if (connectRes.isFailure) {
                    _snackbarEvents.tryEmit(
                        connectRes.exceptionOrNull()?.localizedMessage ?: "Google sign-in required for Drive backup"
                    )
                    return@launch
                }
            }
            requestDriveAction(PendingDriveAction.BACKUP_NOW)
        }
    }

    fun checkAndPromptDriveRestore(activityContext: Context) {
        viewModelScope.launch {
            if (!backupStatus.value.isGoogleConnected) {
                val connectRes = backupController.connectGoogleAccount(activityContext)
                if (connectRes.isFailure) {
                    _snackbarEvents.tryEmit(
                        connectRes.exceptionOrNull()?.localizedMessage ?: "Connect Google account to restore from Drive"
                    )
                    return@launch
                }
            }
            requestDriveAction(PendingDriveAction.CHECK_REMOTE_FOR_RESTORE)
        }
    }

    private suspend fun requestDriveAction(action: PendingDriveAction) {
        val authOutcome = backupController.requestDriveConsentOrToken()
        authOutcome.fold(
            onSuccess = { outcome ->
                when (outcome) {
                    is DriveAuthOutcome.Authorized -> executeDriveActionWithToken(action, outcome.accessToken)
                    is DriveAuthOutcome.NeedsConsent -> _driveConsentPrompt.tryEmit(
                        DriveConsentPrompt(outcome.intentSender, action)
                    )
                }
            },
            onFailure = { error ->
                _snackbarEvents.tryEmit(error.localizedMessage ?: "Google Drive authorization failed")
            }
        )
    }

    fun onDriveConsentResult(action: PendingDriveAction, intentData: Intent?) {
        viewModelScope.launch {
            val tokenResult = GoogleDriveAuthHelper.extractAccessTokenFromIntent(appContext, intentData)
            tokenResult.fold(
                onSuccess = { token -> executeDriveActionWithToken(action, token) },
                onFailure = { error ->
                    _snackbarEvents.tryEmit(error.localizedMessage ?: "Google Drive access was not granted")
                }
            )
        }
    }

    private suspend fun executeDriveActionWithToken(action: PendingDriveAction, accessToken: String) {
        when (action) {
            PendingDriveAction.BACKUP_NOW -> {
                val res = backupController.performDriveBackup(accessToken)
                res.fold(
                    onSuccess = {
                        _snackbarEvents.tryEmit("Backed up securely to Google Drive")
                    },
                    onFailure = { error ->
                        _snackbarEvents.tryEmit(error.localizedMessage ?: "Google Drive backup failed")
                    }
                )
            }
            PendingDriveAction.CHECK_REMOTE_FOR_RESTORE -> {
                val res = backupController.checkRemoteBackup(accessToken)
                res.fold(
                    onSuccess = { meta ->
                        if (meta == null) {
                            _snackbarEvents.tryEmit("No KirayaKhata backup found in this Google Drive account")
                        } else {
                            _showDriveRestoreDialog.value = meta
                        }
                    },
                    onFailure = { error ->
                        _snackbarEvents.tryEmit(error.localizedMessage ?: "Failed to check Google Drive backup")
                    }
                )
            }
            PendingDriveAction.CHECK_REMOTE_SILENT -> {
                backupController.checkRemoteBackup(accessToken)
            }
        }
    }

    fun dismissDriveRestoreDialog() {
        _showDriveRestoreDialog.value = null
    }

    fun confirmDriveRestore() {
        _showDriveRestoreDialog.value = null
        viewModelScope.launch {
            val authOutcome = backupController.requestDriveConsentOrToken()
            authOutcome.fold(
                onSuccess = { outcome ->
                    when (outcome) {
                        is DriveAuthOutcome.Authorized -> {
                            val res = backupController.restoreFromDrive(outcome.accessToken)
                            res.fold(
                                onSuccess = {
                                    _snackbarEvents.tryEmit("Ledger restored from Google Drive")
                                },
                                onFailure = { error ->
                                    _snackbarEvents.tryEmit(error.localizedMessage ?: "Drive restore failed")
                                }
                            )
                        }
                        is DriveAuthOutcome.NeedsConsent -> {
                            _driveConsentPrompt.tryEmit(
                                DriveConsentPrompt(outcome.intentSender, PendingDriveAction.CHECK_REMOTE_FOR_RESTORE)
                            )
                        }
                    }
                },
                onFailure = { error ->
                    _snackbarEvents.tryEmit(error.localizedMessage ?: "Drive authorization failed")
                }
            )
        }
    }

    val isDirectDriveSignInAvailable: Boolean
        get() = GoogleDriveAuthHelper.isGoogleSignInConfigured(appContext)

    fun exportLocalBackup(uri: Uri) {
        viewModelScope.launch {
            val res = backupController.exportLocalDbToUri(uri)
            res.fold(
                onSuccess = { bytes ->
                    val kb = (bytes / 1024L).coerceAtLeast(1L)
                    _snackbarEvents.tryEmit("Backup saved successfully ($kb KB)")
                },
                onFailure = { error ->
                    _snackbarEvents.tryEmit(error.localizedMessage ?: "Failed to save backup file")
                }
            )
        }
    }

    fun shareBackupToDrive(activityContext: Context) {
        viewModelScope.launch {
            val res = backupController.prepareShareableBackupSnapshot()
            res.fold(
                onSuccess = { shared ->
                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/octet-stream"
                        putExtra(Intent.EXTRA_STREAM, shared.uri)
                        putExtra(Intent.EXTRA_SUBJECT, shared.fileName)
                        putExtra(Intent.EXTRA_TITLE, shared.fileName)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    val chooser = Intent.createChooser(sendIntent, "Save or Share KirayaKhata Backup")
                    if (activityContext !is android.app.Activity) {
                        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    try {
                        activityContext.startActivity(chooser)
                    } catch (_: android.content.ActivityNotFoundException) {
                        _snackbarEvents.tryEmit("No app found to share backup file")
                    }
                },
                onFailure = { error ->
                    _snackbarEvents.tryEmit(error.localizedMessage ?: "Failed to prepare backup file")
                }
            )
        }
    }

    fun importLocalBackup(uri: Uri) {
        viewModelScope.launch {
            val res = backupController.importLocalDbFromUri(uri)
            res.fold(
                onSuccess = {
                    _snackbarEvents.tryEmit("Database restored successfully")
                },
                onFailure = { error ->
                    _snackbarEvents.tryEmit(error.localizedMessage ?: "Invalid or corrupt backup file")
                }
            )
        }
    }

    fun selectDiscom(discomId: String) {
        viewModelScope.launch {
            backupController.setSelectedDiscomId(discomId)
        }
    }

    fun saveOcrSettings(preferGemini: Boolean, apiKey: String) {
        viewModelScope.launch {
            backupController.saveOcrSettings(preferGemini, apiKey)
            val label = if (preferGemini && apiKey.isNotBlank()) {
                "Gemini AI OCR enabled with your saved key"
            } else {
                "Offline ML Kit OCR selected"
            }
            _snackbarEvents.tryEmit(label)
        }
    }

    fun setThemeMode(mode: AppThemeMode) {
        viewModelScope.launch {
            backupController.setThemeMode(mode)
        }
    }

    fun setMultiHouseEnabled(enabled: Boolean) {
        viewModelScope.launch {
            if (!enabled) {
                _selectedPropertyId.value = null
            }
            backupController.setMultiHouseEnabled(enabled)
            _snackbarEvents.tryEmit(
                if (enabled) "Multi-house management enabled" else "Switched to single-house mode"
            )
        }
    }

    fun scanBillFromUri(uri: Uri, onExtracted: (ExtractedBillData) -> Unit) {
        viewModelScope.launch {
            _isScanningBill.value = true
            val settings = ocrSettings.value
            val result = billOcrEngine.extractBillData(
                context = appContext,
                uri = uri,
                preferGemini = settings.preferGeminiOcr,
                userGeminiApiKey = settings.userGeminiApiKey
            )
            _isScanningBill.value = false
            result.fold(
                onSuccess = { data ->
                    onExtracted(data)
                    val msg = if (data.hasAnyUsefulField) {
                        "Scanned via ${data.engineLabel}"
                    } else {
                        "No bill numbers detected — verify or enter manually"
                    }
                    _snackbarEvents.tryEmit(msg)
                },
                onFailure = { error ->
                    _snackbarEvents.tryEmit(error.localizedMessage ?: "Could not scan bill")
                }
            )
        }
    }

    fun notifyMessage(message: String) {
        _snackbarEvents.tryEmit(message)
    }

    private companion object {
        const val TAG = "KirayaVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
