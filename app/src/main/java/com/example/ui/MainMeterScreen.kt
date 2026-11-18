package com.example.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CompareArrows
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ElectricMeter
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.backup.BackupStatusState
import com.example.domain.BillingCalculator
import com.example.domain.DiscomCatalog
import com.example.domain.DiscomRegion
import com.example.domain.IndianDiscom
import com.example.domain.MeterReconciliation
import com.example.model.BillingSettings
import com.example.model.ElectricityMode
import com.example.model.MainMeterLog
import com.example.ocr.BillScanUriHelper
import com.example.ocr.ExtractedBillData

@Composable
fun MainMeterScreen(
    data: KirayaDashboardData,
    backupStatus: BackupStatusState,
    selectedDiscomId: String,
    isSaving: Boolean,
    isScanningBill: Boolean,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSelectMonth: (String) -> Unit,
    onSelectPropertyFilter: (String?) -> Unit,
    onManageProperties: () -> Unit,
    onSetPropertyTrackMainMeter: (propertyId: String, trackMainMeter: Boolean) -> Unit,
    onSelectDiscom: (propertyId: String, discomId: String) -> Unit,
    onScanBill: (Uri, (ExtractedBillData) -> Unit) -> Unit,
    onNotify: (String) -> Unit,
    onSaveMainMeterLog: (
        propertyId: String,
        monthKey: String,
        consumerNumber: String,
        prevReading: Double,
        currReading: Double,
        govtBillAmount: Double,
        billPaid: Boolean,
        notes: String,
        onDone: () -> Unit
    ) -> Unit,
    onSaveBillingSettings: (
        defaultRatePerUnit: Double,
        defaultElectricityMode: ElectricityMode,
        consumerNumber: String
    ) -> Unit,
    onOpenSettings: () -> Unit
) {
    var showMainMeterSheet by remember { mutableStateOf(false) }
    var showDiscomPickerSheet by remember { mutableStateOf(false) }

    val activeProp = data.activeMainMeterProperty
    val effectiveDiscomId = remember(activeProp.discomId, selectedDiscomId) {
        activeProp.discomId.ifBlank { selectedDiscomId }
    }
    val selectedDiscom = remember(effectiveDiscomId) { DiscomCatalog.findById(effectiveDiscomId) }
    val activeConsumerNumber = remember(data.currentMainMeterLog, activeProp.consumerNumber, data.settings) {
        data.currentMainMeterLog?.consumerNumber?.takeIf { it.isNotBlank() }
            ?: activeProp.consumerNumber.takeIf { it.isNotBlank() }
            ?: data.settings.consumerNumber
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            MonthSwitcherBar(
                monthKey = data.selectedMonthKey,
                onPreviousMonth = onPreviousMonth,
                onNextMonth = onNextMonth,
                onSelectMonth = onSelectMonth
            )
        }

        if (data.multiHouseEnabled) {
            item {
                PropertyFilterRow(
                    properties = data.properties,
                    selectedPropertyId = activeProp.id,
                    onSelectProperty = onSelectPropertyFilter,
                    onManageProperties = onManageProperties,
                    showAllOption = false
                )
            }
        }

        if (!activeProp.trackMainMeter) {
            item {
                EmptySectionCard(
                    icon = Icons.Rounded.ElectricMeter,
                    title = if (data.multiHouseEnabled) {
                        "Main Meter Off for ${activeProp.name}"
                    } else {
                        "Main Meter Monitoring Off"
                    },
                    subtitle = "Tenants have their own direct govt meters and pay electricity bills themselves. Main meter & sub-meter monitoring is disabled.",
                    action = {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (data.multiHouseEnabled) {
                                OutlinedButton(
                                    onClick = onManageProperties,
                                    modifier = Modifier.testTag("main_meter_manage_houses_button")
                                ) {
                                    Text("Manage Houses")
                                }
                            }
                            Button(
                                onClick = { onSetPropertyTrackMainMeter(activeProp.id, true) },
                                modifier = Modifier.testTag("enable_main_meter_button")
                            ) {
                                Text("Enable Tracking")
                            }
                        }
                    }
                )
            }
        } else {
            item {
                GovtMainMeterCard(
                    propertyName = activeProp.name,
                    showPropertyName = data.multiHouseEnabled,
                    monthKey = data.selectedMonthKey,
                    log = data.currentMainMeterLog,
                    onRecordClick = { showMainMeterSheet = true }
                )
            }

            item {
                DiscomQuickCheckCard(
                    selectedDiscom = selectedDiscom,
                    consumerNumber = activeConsumerNumber,
                    onPickDiscom = { showDiscomPickerSheet = true },
                    onNotify = onNotify
                )
            }

            item {
                ReconciliationCard(reconciliation = data.reconciliation)
            }

            item {
                TenantConsumptionChartCard(
                    monthKey = data.selectedMonthKey,
                    chartData = data.chartData
                )
            }

            item {
                MainVsTenantsReconciliationChartCard(
                    monthKey = data.selectedMonthKey,
                    chartData = data.chartData
                )
            }
        }

        item {
            BillingDefaultsCard(
                settings = data.settings,
                isSaving = isSaving,
                onSave = onSaveBillingSettings
            )
        }

        item {
            BackupSummaryCard(
                status = backupStatus,
                onOpenSettings = onOpenSettings
            )
        }

        if (activeProp.trackMainMeter && data.mainMeterLogsForActiveProperty.isNotEmpty()) {
            item {
                Text(
                    text = if (data.multiHouseEnabled) {
                        "Govt Main Meter History • ${activeProp.name}"
                    } else {
                        "Govt Main Meter History"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            items(
                items = data.mainMeterLogsForActiveProperty,
                key = { it.id.ifBlank { "${it.propertyId}_${it.monthKey}" } }
            ) { log ->
                MainMeterHistoryRow(log = log)
            }
        }

        item { Spacer(Modifier.height(72.dp)) }
    }

    if (showMainMeterSheet) {
        val suggestedPrev = data.currentMainMeterLog?.prevReading
            ?: BillingCalculator.resolvePreviousMainReading(
                data.selectedMonthKey,
                data.mainMeterLogsForActiveProperty
            )

        MainMeterFormBottomSheet(
            monthKey = data.selectedMonthKey,
            existing = data.currentMainMeterLog,
            suggestedPrevReading = suggestedPrev,
            defaultConsumerNumber = activeProp.consumerNumber.ifBlank { data.settings.consumerNumber },
            selectedDiscom = selectedDiscom,
            isSaving = isSaving,
            isScanningBill = isScanningBill,
            onScanBill = onScanBill,
            onDismiss = { showMainMeterSheet = false },
            onSave = { consumerNo, prev, curr, billAmt, paid, notes ->
                onSaveMainMeterLog(
                    activeProp.id,
                    data.selectedMonthKey,
                    consumerNo,
                    prev,
                    curr,
                    billAmt,
                    paid,
                    notes
                ) {
                    showMainMeterSheet = false
                }
            }
        )
    }

    if (showDiscomPickerSheet) {
        DiscomPickerBottomSheet(
            selectedDiscomId = effectiveDiscomId,
            onSelectDiscom = { id ->
                onSelectDiscom(activeProp.id, id)
                showDiscomPickerSheet = false
            },
            onDismiss = { showDiscomPickerSheet = false }
        )
    }
}

@Composable
private fun GovtMainMeterCard(
    propertyName: String,
    showPropertyName: Boolean,
    monthKey: String,
    log: MainMeterLog?,
    onRecordClick: () -> Unit
) {
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Column(
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Rounded.ElectricMeter,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Govt Main Meter",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (showPropertyName) {
                                "$propertyName • ${BillingCalculator.formatMonthFull(monthKey)}"
                            } else {
                                BillingCalculator.formatMonthFull(monthKey)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (log != null) {
                    Surface(
                        color = if (log.billPaid) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(50),
                        border = if (log.billPaid) BorderStroke(1.dp, MaterialTheme.colorScheme.outline) else null
                    ) {
                        Text(
                            text = if (log.billPaid) "Bill Paid" else "Unpaid",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (log.billPaid) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }

            if (log != null) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Govt Bill", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = BillingCalculator.formatInr(log.govtBillAmount),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Main Units", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = BillingCalculator.formatUnits(log.unitsConsumed),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }

                Text(
                    text = "Reading: ${BillingCalculator.formatReading(log.prevReading)} → ${BillingCalculator.formatReading(log.currReading)} kWh" +
                        if (log.consumerNumber.isNotBlank()) " • Consumer #${log.consumerNumber}" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (log.notes.isNotBlank()) {
                    Text(
                        text = log.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Text(
                    text = "No bill recorded for ${BillingCalculator.formatMonthShort(monthKey)} yet. Enter manually or scan a bill PDF/photo with free offline OCR.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Button(
                onClick = onRecordClick,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("record_main_meter_button")
            ) {
                Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (log == null) "Scan or Enter Bill" else "Edit / Scan Bill")
            }
        }
    }
}

@Composable
private fun DiscomQuickCheckCard(
    selectedDiscom: IndianDiscom?,
    consumerNumber: String,
    onPickDiscom: () -> Unit,
    onNotify: (String) -> Unit
) {
    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current
    val uriHandler = LocalUriHandler.current

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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.Rounded.AccountBalance,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            text = selectedDiscom?.shortName ?: "Select Electricity Board (DISCOM)",
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = selectedDiscom?.region?.label
                                ?: "Delhi, Maharashtra, UP, Bihar, Rajasthan, Haryana, South & East",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            if (selectedDiscom != null) {
                val idLabel = selectedDiscom.consumerLabel
                Text(
                    text = if (consumerNumber.isNotBlank()) {
                        "$idLabel: $consumerNumber"
                    } else {
                        "Save your $idLabel in Main Meter Log or Default Settings for 1-tap copy."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = "Pick your DISCOM to jump straight to its official bill-view portal (no BBPS or payment gateway).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onPickDiscom,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("select_discom_button")
                ) {
                    Text(if (selectedDiscom == null) "Select Board" else "Change Board")
                }

                Button(
                    onClick = {
                        if (selectedDiscom != null) {
                            if (consumerNumber.isNotBlank()) {
                                clipboardManager.setText(AnnotatedString(consumerNumber))
                                onNotify("Copied ${selectedDiscom.consumerLabel} ($consumerNumber)")
                            } else {
                                onNotify("Opening ${selectedDiscom.shortName} official portal")
                            }
                            runCatching {
                                uriHandler.openUri(selectedDiscom.portalUrl)
                            }.onFailure {
                                onNotify("Unable to open browser for ${selectedDiscom.shortName}")
                            }
                        }
                    },
                    enabled = selectedDiscom != null,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("check_official_bill_button")
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Official Bill")
                }
            }
        }
    }
}

@Composable
private fun ReconciliationCard(reconciliation: MeterReconciliation) {
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
                    Icons.AutoMirrored.Rounded.CompareArrows,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Main vs Sub-Meter Reconciliation",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            ReconciliationRow(
                label = "Main Govt Meter Units",
                value = BillingCalculator.formatUnits(reconciliation.mainUnits)
            )
            ReconciliationRow(
                label = "Sum of Tenant Sub-Meters",
                value = BillingCalculator.formatUnits(reconciliation.subMeterUnitsSum)
            )
            ReconciliationRow(
                label = "Common Area / Line Loss",
                value = BillingCalculator.formatUnits(reconciliation.differenceUnits),
                highlightError = reconciliation.differenceUnits < 0.0
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            ReconciliationRow(
                label = "Govt Bill Amount",
                value = BillingCalculator.formatInr(reconciliation.govtBillAmount)
            )
            ReconciliationRow(
                label = "Electricity Billed to Tenants",
                value = BillingCalculator.formatInr(reconciliation.tenantBilledElectricity)
            )
            if (reconciliation.effectiveMainRate > 0.0 || reconciliation.effectiveSubSplitRate > 0.0) {
                Text(
                    text = "Avg Main Rate: ${BillingCalculator.formatInr(reconciliation.effectiveMainRate)}/kWh • Full Bill Split Rate: ${BillingCalculator.formatInr(reconciliation.effectiveSubSplitRate)}/kWh",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ReconciliationRow(
    label: String,
    value: String,
    highlightError: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (highlightError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun BillingDefaultsCard(
    settings: BillingSettings,
    isSaving: Boolean,
    onSave: (
        defaultRatePerUnit: Double,
        defaultElectricityMode: ElectricityMode,
        consumerNumber: String
    ) -> Unit
) {
    var mode by remember(settings.defaultElectricityMode) {
        mutableStateOf(settings.resolvedDefaultMode)
    }
    var rateText by remember(settings.defaultRatePerUnit) {
        mutableStateOf(formatNumberInputWithZero(settings.defaultRatePerUnit))
    }
    var consumerText by remember(settings.consumerNumber) {
        mutableStateOf(settings.consumerNumber)
    }

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
                    Icons.Rounded.Tune,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Default Electricity Settings",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                ElectricityMode.entries.forEachIndexed { idx, entry ->
                    SegmentedButton(
                        selected = mode == entry,
                        onClick = { mode = entry },
                        shape = SegmentedButtonDefaults.itemShape(idx, ElectricityMode.entries.size)
                    ) {
                        Text(entry.label, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = rateText,
                    onValueChange = { rateText = it },
                    label = { Text("Default Rate (₹/kWh)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = consumerText,
                    onValueChange = { consumerText = it },
                    label = { Text("Govt Consumer #") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            OutlinedButton(
                onClick = {
                    val rate = rateText.toDoubleOrNull() ?: settings.defaultRatePerUnit
                    onSave(rate.coerceAtLeast(0.0), mode, consumerText)
                },
                enabled = !isSaving,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("save_billing_settings_button")
            ) {
                Text("Save Default Preferences")
            }
        }
    }
}

@Composable
private fun BackupSummaryCard(
    status: BackupStatusState,
    onOpenSettings: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Settings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(30.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = "App Settings, Backup & OCR",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = when {
                            status.isGoogleConnected && status.lastBackupTimeMs > 0L ->
                                "${status.connectedEmail} • ${formatBackupTime(status.lastBackupTimeMs)}"
                            status.isGoogleConnected ->
                                "${status.connectedEmail} • ${status.frequency.label}"
                            status.lastBackupTimeMs > 0L ->
                                "Last exported ${formatBackupTime(status.lastBackupTimeMs)}"
                            else -> "Theme, Google Drive sync, .db export & Gemini API"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.width(8.dp))
            OutlinedButton(
                onClick = onOpenSettings,
                modifier = Modifier.testTag("open_backup_from_settings_button")
            ) {
                Text("Settings")
            }
        }
    }
}

@Composable
private fun MainMeterHistoryRow(log: MainMeterLog) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = BillingCalculator.formatMonthFull(log.monthKey),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "${BillingCalculator.formatReading(log.prevReading)} → ${BillingCalculator.formatReading(log.currReading)} (${BillingCalculator.formatUnits(log.unitsConsumed)})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = BillingCalculator.formatInr(log.govtBillAmount),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (log.billPaid) "Paid" else "Unpaid",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (log.billPaid) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainMeterFormBottomSheet(
    monthKey: String,
    existing: MainMeterLog?,
    suggestedPrevReading: Double,
    defaultConsumerNumber: String,
    selectedDiscom: IndianDiscom?,
    isSaving: Boolean,
    isScanningBill: Boolean,
    onScanBill: (Uri, (ExtractedBillData) -> Unit) -> Unit,
    onDismiss: () -> Unit,
    onSave: (
        consumerNumber: String,
        prevReading: Double,
        currReading: Double,
        govtBillAmount: Double,
        billPaid: Boolean,
        notes: String
    ) -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var consumerNo by remember(monthKey) {
        mutableStateOf(existing?.consumerNumber ?: defaultConsumerNumber)
    }
    var prevText by remember(monthKey) {
        mutableStateOf(formatNumberInputWithZero(existing?.prevReading ?: suggestedPrevReading))
    }
    var currText by remember(monthKey) {
        mutableStateOf(if (existing != null) formatNumberInputWithZero(existing.currReading) else "")
    }
    var billText by remember(monthKey) {
        mutableStateOf(if (existing != null) formatNumberInputWithZero(existing.govtBillAmount) else "")
    }
    var billPaid by remember(monthKey) { mutableStateOf(existing?.billPaid ?: false) }
    var notes by remember(monthKey) { mutableStateOf(existing?.notes ?: "") }
    var validationError by remember { mutableStateOf<String?>(null) }
    var ocrStatusBanner by remember { mutableStateOf<String?>(null) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    val applyExtracted: (ExtractedBillData) -> Unit = { extracted ->
        if (!extracted.consumerNumber.isNullOrBlank()) {
            consumerNo = extracted.consumerNumber
        }
        if (extracted.prevReading != null) {
            prevText = formatNumberInputWithZero(extracted.prevReading)
        }
        val basePrev = extracted.prevReading ?: prevText.toDoubleOrNull() ?: suggestedPrevReading
        if (extracted.currReading != null) {
            currText = formatNumberInputWithZero(extracted.currReading)
        } else if (extracted.unitsConsumed != null) {
            currText = formatNumberInputWithZero(basePrev + extracted.unitsConsumed)
        }
        if (extracted.billAmount != null) {
            billText = formatNumberInputWithZero(extracted.billAmount)
        }
        if (extracted.summaryNote.isNotBlank() && notes.isBlank()) {
            notes = extracted.summaryNote
        }
        validationError = null
        ocrStatusBanner = if (extracted.hasAnyUsefulField) {
            "Auto-filled via ${extracted.engineLabel}"
        } else {
            "No bill numbers detected — please verify or enter manually"
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = pendingCameraUri
        pendingCameraUri = null
        if (success && uri != null) {
            onScanBill(uri, applyExtracted)
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onScanBill(uri, applyExtracted)
        }
    }

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onScanBill(uri, applyExtracted)
        }
    }

    val prevVal = prevText.toDoubleOrNull() ?: 0.0
    val currVal = currText.toDoubleOrNull() ?: prevVal
    val units = BillingCalculator.roundToTwoDecimals((currVal - prevVal).coerceAtLeast(0.0))

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Govt Main Meter • ${BillingCalculator.formatMonthFull(monthKey)}",
                style = MaterialTheme.typography.titleLarge
            )

            // Free Offline OCR / Optional Gemini Bill Scanner
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Scan or Upload Bill (Free OCR)",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val uri = BillScanUriHelper.createTempCameraImageUri(context)
                                pendingCameraUri = uri
                                cameraLauncher.launch(uri)
                            },
                            enabled = !isScanningBill && !isSaving,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("main_scan_camera_button")
                        ) {
                            Icon(Icons.Rounded.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Camera", style = MaterialTheme.typography.labelMedium)
                        }
                        OutlinedButton(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            enabled = !isScanningBill && !isSaving,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("main_scan_photo_button")
                        ) {
                            Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Photo", style = MaterialTheme.typography.labelMedium)
                        }
                        OutlinedButton(
                            onClick = {
                                pdfPickerLauncher.launch(arrayOf("application/pdf"))
                            },
                            enabled = !isScanningBill && !isSaving,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("main_scan_pdf_button")
                        ) {
                            Icon(Icons.Rounded.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("PDF", style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    if (isScanningBill) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Text(
                                text = "Extracting readings & bill amount…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else if (!ocrStatusBanner.isNullOrBlank()) {
                        Text(
                            text = ocrStatusBanner!!,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            OutlinedTextField(
                value = consumerNo,
                onValueChange = { consumerNo = it },
                label = {
                    val labelPrefix = selectedDiscom?.consumerLabel ?: "Consumer / CA Number"
                    Text("$labelPrefix (Optional)")
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = prevText,
                    onValueChange = { prevText = it; validationError = null },
                    label = { Text("Prev Reading") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("main_prev_input")
                )
                OutlinedTextField(
                    value = currText,
                    onValueChange = { currText = it; validationError = null },
                    label = { Text("Curr Reading") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("main_curr_input")
                )
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Main Meter Units Consumed: ${BillingCalculator.formatUnits(units)}",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(12.dp)
                )
            }

            OutlinedTextField(
                value = billText,
                onValueChange = { billText = it; validationError = null },
                label = { Text("Govt Electricity Bill Amount (₹)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("main_bill_input")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Govt Bill Paid",
                    style = MaterialTheme.typography.bodyLarge
                )
                Switch(
                    checked = billPaid,
                    onCheckedChange = { billPaid = it }
                )
            }

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes (e.g. billing cycle, subsidy, motor usage)") },
                maxLines = 3,
                modifier = Modifier.fillMaxWidth()
            )

            if (validationError != null) {
                Text(
                    text = validationError!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Button(
                onClick = {
                    val pPrev = prevText.toDoubleOrNull()
                    val pCurr = if (currText.isBlank()) pPrev else currText.toDoubleOrNull()
                    val pBill = if (billText.isBlank()) 0.0 else billText.toDoubleOrNull()
                    when {
                        pPrev == null || pPrev < 0.0 -> validationError = "Enter a valid previous reading."
                        pCurr == null || pCurr < pPrev -> validationError = "Current reading cannot be less than previous reading."
                        pBill == null || pBill < 0.0 -> validationError = "Enter a valid non-negative bill amount."
                        else -> onSave(consumerNo, pPrev, pCurr, pBill, billPaid, notes)
                    }
                },
                enabled = !isSaving,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("save_main_meter_button")
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Save Govt Main Meter Log")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DiscomPickerBottomSheet(
    selectedDiscomId: String,
    onSelectDiscom: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }

    val filteredByRegion = remember(query) {
        val q = query.trim().lowercase()
        DiscomRegion.entries.mapNotNull { region ->
            val items = DiscomCatalog.allDiscoms.filter { discom ->
                discom.region == region && (
                    q.isEmpty() ||
                        discom.shortName.lowercase().contains(q) ||
                        discom.fullName.lowercase().contains(q) ||
                        region.label.lowercase().contains(q)
                    )
            }
            if (items.isEmpty()) null else region to items
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Select Electricity Board (DISCOM)",
                style = MaterialTheme.typography.titleLarge
            )

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                placeholder = { Text("Search BSES, MSEDCL, UPPCL, BESCOM…") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(380.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                filteredByRegion.forEach { (region, discoms) ->
                    item(key = "header_${region.name}") {
                        Text(
                            text = region.label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                        )
                    }
                    items(items = discoms, key = { it.id }) { discom ->
                        val isSelected = discom.id == selectedDiscomId
                        Surface(
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.surfaceVariant
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                            shape = MaterialTheme.shapes.small,
                            border = BorderStroke(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectDiscom(discom.id) }
                                .testTag("discom_option_${discom.id}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { onSelectDiscom(discom.id) }
                                )
                                Spacer(Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = discom.shortName,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "${discom.fullName} • ${discom.consumerLabel}",
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
    }
}
