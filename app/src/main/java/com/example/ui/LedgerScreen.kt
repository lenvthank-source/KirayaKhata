package com.example.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ElectricBolt
import androidx.compose.material.icons.rounded.HomeWork
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.domain.BillingCalculator
import com.example.domain.MonthlyDashboardSummary
import com.example.domain.TenantMonthSummary
import com.example.model.ElectricityMode
import com.example.model.LedgerFilter
import com.example.model.PaymentMode
import com.example.ocr.BillScanUriHelper
import com.example.ocr.ExtractedBillData
import kotlin.math.abs

@Composable
fun LedgerScreen(
    data: KirayaDashboardData,
    isSaving: Boolean,
    isScanningBill: Boolean,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSelectMonth: (String) -> Unit,
    onSelectPropertyFilter: (String?) -> Unit,
    onManageProperties: () -> Unit,
    onSelectFilter: (LedgerFilter) -> Unit,
    onOpenAddTenant: () -> Unit,
    onOpenBackupRestore: () -> Unit,
    onScanBill: (Uri, (ExtractedBillData) -> Unit) -> Unit,
    onSaveMonthlyEntry: (
        tenantId: String,
        monthKey: String,
        rentAmount: Double,
        prevReading: Double,
        currReading: Double,
        electricityMode: ElectricityMode,
        ratePerUnit: Double,
        electricityAmount: Double,
        maintenanceAmount: Double,
        extraChargesAmount: Double,
        extraChargesNote: String,
        previousBalance: Double,
        amountPaid: Double,
        paidDateMs: Long,
        paymentMode: PaymentMode,
        paymentNote: String,
        onDone: () -> Unit
    ) -> Unit,
    onDeleteMonthlyEntry: (ledgerId: String, onDone: () -> Unit) -> Unit
) {
    var editingSummary by remember { mutableStateOf<TenantMonthSummary?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
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
                    selectedPropertyId = data.selectedPropertyId,
                    onSelectProperty = onSelectPropertyFilter,
                    onManageProperties = onManageProperties
                )
            }
        }

        item {
            MonthlySnapshotCard(summary = data.dashboardSummary)
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LedgerFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = data.selectedFilter == filter,
                        onClick = { onSelectFilter(filter) },
                        label = { Text(filter.label) },
                        modifier = Modifier.testTag("filter_${filter.name.lowercase()}")
                    )
                }
            }
        }

        if (data.tenants.isEmpty()) {
            item {
                EmptySectionCard(
                    icon = Icons.Rounded.HomeWork,
                    title = "No tenants added yet",
                    subtitle = "Add your rooms, flats, or houses to start tracking monthly rent, maintenance, sub-meter readings, and balances.",
                    action = {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = onOpenAddTenant,
                                modifier = Modifier.testTag("empty_add_tenant_button")
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Add First Tenant")
                            }
                            OutlinedButton(
                                onClick = onOpenBackupRestore,
                                modifier = Modifier.testTag("empty_restore_backup_button")
                            ) {
                                Text("Restore from Backup")
                            }
                        }
                    }
                )
            }
        } else if (data.filteredSummaries.isEmpty()) {
            item {
                EmptySectionCard(
                    icon = Icons.AutoMirrored.Rounded.ReceiptLong,
                    title = "No entries match \"${data.selectedFilter.label}\"",
                    subtitle = if (data.multiHouseEnabled) {
                        "Try switching houses or selecting All above."
                    } else {
                        "Try selecting All above."
                    },
                    action = if ((data.multiHouseEnabled && data.selectedPropertyId != null) || data.selectedFilter != LedgerFilter.ALL) {
                        {
                            OutlinedButton(
                                onClick = {
                                    if (data.multiHouseEnabled) {
                                        onSelectPropertyFilter(null)
                                    }
                                    onSelectFilter(LedgerFilter.ALL)
                                }
                            ) {
                                Text("Reset Filters")
                            }
                        }
                    } else null
                )
            }
        } else if (data.multiHouseEnabled) {
            data.groupedLedgerSections.forEach { group ->
                item(key = "ledger_header_${group.property.id}") {
                    PropertySectionHeader(
                        property = group.property,
                        itemCount = group.items.size
                    )
                }
                items(
                    items = group.items,
                    key = { it.tenant.id }
                ) { summary ->
                    TenantLedgerRowCard(
                        summary = summary,
                        onClick = { editingSummary = summary }
                    )
                }
            }
        } else {
            items(
                items = data.filteredSummaries,
                key = { it.tenant.id }
            ) { summary ->
                TenantLedgerRowCard(
                    summary = summary,
                    onClick = { editingSummary = summary }
                )
            }
        }

        item { Spacer(Modifier.height(72.dp)) }
    }

    val targetSummary = editingSummary
    if (targetSummary != null) {
        RecordEntryBottomSheet(
            summary = targetSummary,
            data = data,
            isSaving = isSaving,
            isScanningBill = isScanningBill,
            onScanBill = onScanBill,
            onDismiss = { editingSummary = null },
            onSave = { rent, prev, curr, mode, rate, elec, maint, extra, extraNote, prevBal, paid, paidDateMs, payMode, note ->
                onSaveMonthlyEntry(
                    targetSummary.tenant.id,
                    data.selectedMonthKey,
                    rent,
                    prev,
                    curr,
                    mode,
                    rate,
                    elec,
                    maint,
                    extra,
                    extraNote,
                    prevBal,
                    paid,
                    paidDateMs,
                    payMode,
                    note
                ) {
                    editingSummary = null
                }
            },
            onDelete = targetSummary.ledger?.let { existingLedger ->
                {
                    onDeleteMonthlyEntry(existingLedger.id) {
                        editingSummary = null
                    }
                }
            }
        )
    }
}

@Composable
private fun MonthlySnapshotCard(summary: MonthlyDashboardSummary) {
    val progress = if (summary.activeTenantCount > 0) {
        summary.recordedCount.toFloat() / summary.activeTenantCount.toFloat()
    } else 0f

    Card(
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF0E2A22),
                            Color(0xFF13202E)
                        )
                    )
                )
                .padding(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Collected This Month",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.78f)
                        )
                        Text(
                            text = BillingCalculator.formatInr(summary.totalCollected),
                            style = MaterialTheme.typography.displaySmall,
                            color = Color.White
                        )
                    }
                    Surface(
                        color = Color.White.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.End
                        ) {
                            Text(
                                text = "Recorded",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.78f)
                            )
                            Text(
                                text = "${summary.recordedCount}/${summary.activeTenantCount}",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White
                            )
                        }
                    }
                }

                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(50)),
                    color = Color(0xFF2ED496),
                    trackColor = Color.White.copy(alpha = 0.18f)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SnapshotStatPill(
                        label = "Expected",
                        value = BillingCalculator.formatInr(summary.totalExpected),
                        modifier = Modifier.weight(1f)
                    )
                    SnapshotStatPill(
                        label = "Total Due",
                        value = BillingCalculator.formatInr(summary.totalDue),
                        modifier = Modifier.weight(1f)
                    )
                    SnapshotStatPill(
                        label = "Advance Held",
                        value = BillingCalculator.formatInr(summary.totalAdvanceHeld),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun SnapshotStatPill(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color.White.copy(alpha = 0.14f),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.78f)
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

@Composable
private fun TenantLedgerRowCard(
    summary: TenantMonthSummary,
    onClick: () -> Unit
) {
    val ledger = summary.ledger
    val tracksSubMeter = summary.tenant.tracksSubMeter
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(
            width = 1.dp,
            color = if (summary.isRecorded) {
                MaterialTheme.colorScheme.outlineVariant
            } else {
                MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f)
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("ledger_card_${summary.tenant.id}")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = summary.tenant.roomNumber,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        if (!tracksSubMeter) {
                            Spacer(Modifier.width(6.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "Direct Govt",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }
                        }
                        if (!summary.tenant.active) {
                            Spacer(Modifier.width(6.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "Vacated",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = summary.tenant.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                BalanceStatusBadge(
                    closingBalance = summary.closingBalance,
                    isRecorded = summary.isRecorded
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            if (ledger != null) {
                val maintPlusExtra = ledger.maintenanceAmount + ledger.extraChargesAmount
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    MiniMetric("Rent", BillingCalculator.formatInr(ledger.rentAmount))
                    if (tracksSubMeter) {
                        MiniMetric(
                            "Sub-Meter (${BillingCalculator.formatUnits(ledger.unitsConsumed)})",
                            BillingCalculator.formatInr(ledger.electricityAmount)
                        )
                    } else {
                        MiniMetric(
                            "Maint & Extra",
                            BillingCalculator.formatInr(maintPlusExtra)
                        )
                    }
                    MiniMetric(
                        label = if (ledger.amountPaid > 0.005 && ledger.paidDateMs > 0L) {
                            "Paid ${BillingCalculator.formatPaymentDateShort(ledger.paidDateMs)} • ${ledger.resolvedPaymentMode.label}"
                        } else {
                            "Paid (${ledger.resolvedPaymentMode.label})"
                        },
                        value = BillingCalculator.formatInr(ledger.amountPaid),
                        highlight = true
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val leftSubtitle = buildString {
                        if (tracksSubMeter) {
                            append("Meter: ${BillingCalculator.formatReading(ledger.prevReading)} → ${BillingCalculator.formatReading(ledger.currReading)}")
                            if (maintPlusExtra > 0.005) {
                                append(" • Maint/Extra: ${BillingCalculator.formatInr(maintPlusExtra)}")
                            }
                        } else {
                            append("Tenant pays own DISCOM bill")
                            if (ledger.extraChargesNote.isNotBlank()) {
                                append(" • ${ledger.extraChargesNote}")
                            }
                        }
                    }
                    Text(
                        text = leftSubtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    if (abs(ledger.previousBalance) > 0.005) {
                        Spacer(Modifier.width(8.dp))
                        val prevLabel = if (ledger.previousBalance > 0) {
                            "Prev Due: ${BillingCalculator.formatInr(ledger.previousBalance)}"
                        } else {
                            "Prev Adv: ${BillingCalculator.formatInr(-ledger.previousBalance)}"
                        }
                        Text(
                            text = prevLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        val rentLine = buildString {
                            append("Base Rent: ${BillingCalculator.formatInr(summary.tenant.monthlyRent)}")
                            if (summary.tenant.defaultMaintenance > 0.005) {
                                append(" + Maint: ${BillingCalculator.formatInr(summary.tenant.defaultMaintenance)}")
                            }
                        }
                        Text(
                            text = rentLine,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = if (tracksSubMeter) {
                                "Last Meter Reading: ${BillingCalculator.formatReading(summary.suggestedPrevReading)} kWh"
                            } else {
                                "Direct Govt Meter • Tenant pays electricity directly"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "Tap to Record",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniMetric(
    label: String,
    value: String,
    highlight: Boolean = false
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecordEntryBottomSheet(
    summary: TenantMonthSummary,
    data: KirayaDashboardData,
    isSaving: Boolean,
    isScanningBill: Boolean,
    onScanBill: (Uri, (ExtractedBillData) -> Unit) -> Unit,
    onDismiss: () -> Unit,
    onSave: (
        rentAmount: Double,
        prevReading: Double,
        currReading: Double,
        electricityMode: ElectricityMode,
        ratePerUnit: Double,
        electricityAmount: Double,
        maintenanceAmount: Double,
        extraChargesAmount: Double,
        extraChargesNote: String,
        previousBalance: Double,
        amountPaid: Double,
        paidDateMs: Long,
        paymentMode: PaymentMode,
        paymentNote: String
    ) -> Unit,
    onDelete: (() -> Unit)?
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val existing = summary.ledger
    val tracksSubMeter = summary.tenant.tracksSubMeter
    val propertyName = data.properties.firstOrNull { it.id == summary.tenant.propertyId }?.name
    val mainLog = data.mainMeterLogs.firstOrNull {
        it.propertyId == summary.tenant.propertyId && it.monthKey == data.selectedMonthKey
    }
    val resolvedDefaultRate = remember(summary.tenant, data.selectedMonthKey, data.allLedgers, data.settings.defaultRatePerUnit) {
        BillingCalculator.resolveEffectiveElectricityRate(
            tenant = summary.tenant,
            monthKey = data.selectedMonthKey,
            allLedgers = data.allLedgers,
            fallbackDefaultRate = data.settings.defaultRatePerUnit
        )
    }

    var rentText by remember(summary.tenant.id, data.selectedMonthKey) {
        mutableStateOf(formatNumberInputWithZero(existing?.rentAmount ?: summary.tenant.monthlyRent))
    }
    var prevReadingText by remember(summary.tenant.id, data.selectedMonthKey) {
        mutableStateOf(formatNumberInputWithZero(existing?.prevReading ?: summary.suggestedPrevReading))
    }
    var currReadingText by remember(summary.tenant.id, data.selectedMonthKey) {
        mutableStateOf(
            if (existing != null) formatNumberInputWithZero(existing.currReading) else ""
        )
    }
    var elecMode by remember(summary.tenant.id, data.selectedMonthKey) {
        mutableStateOf(existing?.resolvedElectricityMode ?: data.settings.resolvedDefaultMode)
    }
    var rateText by remember(summary.tenant.id, data.selectedMonthKey, resolvedDefaultRate) {
        mutableStateOf(formatNumberInputWithZero(existing?.ratePerUnit ?: resolvedDefaultRate))
    }
    var useProportionalSplit by remember { mutableStateOf(false) }
    var maintenanceText by remember(summary.tenant.id, data.selectedMonthKey) {
        mutableStateOf(
            formatNumberInputWithZero(existing?.maintenanceAmount ?: summary.tenant.defaultMaintenance)
        )
    }
    var extraChargesText by remember(summary.tenant.id, data.selectedMonthKey) {
        mutableStateOf(
            if (existing != null && existing.extraChargesAmount > 0.0) {
                formatNumberInputWithZero(existing.extraChargesAmount)
            } else ""
        )
    }
    var extraChargesNoteText by remember(summary.tenant.id, data.selectedMonthKey) {
        mutableStateOf(existing?.extraChargesNote ?: "")
    }
    var paidText by remember(summary.tenant.id, data.selectedMonthKey) {
        mutableStateOf(if (existing != null) formatNumberInputWithZero(existing.amountPaid) else "")
    }
    var paidDateMs by remember(summary.tenant.id, data.selectedMonthKey) {
        mutableStateOf(
            when {
                existing != null && existing.paidDateMs > 0L -> existing.paidDateMs
                existing != null && existing.amountPaid > 0.005 -> System.currentTimeMillis()
                else -> 0L
            }
        )
    }
    var showPaidDatePicker by remember { mutableStateOf(false) }
    var paymentMode by remember(summary.tenant.id, data.selectedMonthKey) {
        mutableStateOf(existing?.resolvedPaymentMode ?: PaymentMode.UPI)
    }
    var noteText by remember(summary.tenant.id, data.selectedMonthKey) {
        mutableStateOf(existing?.paymentNote ?: "")
    }
    var validationError by remember { mutableStateOf<String?>(null) }
    var ocrStatusBanner by remember { mutableStateOf<String?>(null) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    val applyExtracted: (ExtractedBillData) -> Unit = { extracted ->
        if (extracted.prevReading != null) {
            prevReadingText = formatNumberInputWithZero(extracted.prevReading)
        }
        if (extracted.currReading != null) {
            currReadingText = formatNumberInputWithZero(extracted.currReading)
        } else if (extracted.unitsConsumed != null) {
            val basePrev = prevReadingText.toDoubleOrNull() ?: 0.0
            currReadingText = formatNumberInputWithZero(basePrev + extracted.unitsConsumed)
        }
        ocrStatusBanner = if (extracted.hasAnyUsefulField) {
            "Auto-filled via ${extracted.engineLabel}"
        } else {
            "No clear reading found — please enter manually"
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

    val rentVal = rentText.toDoubleOrNull() ?: 0.0
    val prevVal = prevReadingText.toDoubleOrNull() ?: 0.0
    val currVal = currReadingText.toDoubleOrNull() ?: prevVal
    val units = if (tracksSubMeter) {
        BillingCalculator.roundToTwoDecimals((currVal - prevVal).coerceAtLeast(0.0))
    } else 0.0

    val otherSubMeterTenantIds = remember(data.tenants, summary.tenant.propertyId, summary.tenant.id) {
        data.tenants
            .filter { it.propertyId == summary.tenant.propertyId && it.tracksSubMeter && it.id != summary.tenant.id }
            .map { it.id }
            .toSet()
    }
    val otherTenantsSubUnits = remember(data.allLedgers, data.selectedMonthKey, otherSubMeterTenantIds) {
        data.allLedgers
            .filter { it.monthKey == data.selectedMonthKey && it.tenantId in otherSubMeterTenantIds }
            .sumOf { it.unitsConsumed }
    }
    val combinedSubUnits = otherTenantsSubUnits + units

    val effectiveRate = if (!tracksSubMeter) {
        0.0
    } else when (elecMode) {
        ElectricityMode.FIXED_RATE -> rateText.toDoubleOrNull() ?: 0.0
        ElectricityMode.MAIN_BILL_SPLIT -> {
            if (mainLog != null && mainLog.govtBillAmount > 0.0) {
                if (useProportionalSplit && combinedSubUnits > 0.0) {
                    BillingCalculator.roundToTwoDecimals(mainLog.govtBillAmount / combinedSubUnits)
                } else if (mainLog.unitsConsumed > 0.0) {
                    BillingCalculator.roundToTwoDecimals(mainLog.govtBillAmount / mainLog.unitsConsumed)
                } else if (combinedSubUnits > 0.0) {
                    BillingCalculator.roundToTwoDecimals(mainLog.govtBillAmount / combinedSubUnits)
                } else 0.0
            } else {
                rateText.toDoubleOrNull() ?: 0.0
            }
        }
    }

    val electricityAmount = if (tracksSubMeter) {
        BillingCalculator.roundToTwoDecimals(units * effectiveRate)
    } else 0.0

    val maintVal = maintenanceText.toDoubleOrNull() ?: 0.0
    val extraVal = extraChargesText.toDoubleOrNull() ?: 0.0
    val prevBalance = summary.carriedPreviousBalance
    val totalPayable = BillingCalculator.roundToTwoDecimals(
        prevBalance + rentVal + electricityAmount + maintVal + extraVal
    )
    val paidVal = paidText.toDoubleOrNull() ?: 0.0
    val closingBalance = BillingCalculator.roundToTwoDecimals(totalPayable - paidVal)

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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${summary.tenant.name} • ${summary.tenant.roomNumber}",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = buildString {
                            append(BillingCalculator.formatMonthFull(data.selectedMonthKey))
                            if (data.multiHouseEnabled && !propertyName.isNullOrBlank()) {
                                append(" • $propertyName")
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (onDelete != null) {
                    TextButton(
                        onClick = onDelete,
                        enabled = !isSaving
                    ) {
                        Icon(
                            Icons.Rounded.DeleteOutline,
                            contentDescription = "Delete monthly entry",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            if (abs(prevBalance) > 0.005) {
                Surface(
                    color = if (prevBalance > 0) MaterialTheme.colorScheme.errorContainer
                    else MaterialTheme.colorScheme.tertiaryContainer,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (prevBalance > 0) {
                            "Previous Month Due Carried Forward: ${BillingCalculator.formatInr(prevBalance)}"
                        } else {
                            "Previous Month Advance Credit: ${BillingCalculator.formatInr(-prevBalance)}"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = if (prevBalance > 0) MaterialTheme.colorScheme.onErrorContainer
                        else MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            OutlinedTextField(
                value = rentText,
                onValueChange = { rentText = it; validationError = null },
                label = { Text("Monthly Rent (₹)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("entry_rent_input")
            )

            if (tracksSubMeter) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Sub-Meter Electricity",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(
                            onClick = {
                                val uri = BillScanUriHelper.createTempCameraImageUri(context)
                                pendingCameraUri = uri
                                cameraLauncher.launch(uri)
                            },
                            enabled = !isScanningBill && !isSaving,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier
                                .height(36.dp)
                                .testTag("entry_scan_camera_button")
                        ) {
                            Icon(Icons.Rounded.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Scan", style = MaterialTheme.typography.labelSmall)
                        }
                        OutlinedButton(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            enabled = !isScanningBill && !isSaving,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier
                                .height(36.dp)
                                .testTag("entry_scan_photo_button")
                        ) {
                            Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Photo", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                if (isScanningBill) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text(
                            text = "Reading meter / bill photo…",
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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = prevReadingText,
                        onValueChange = { prevReadingText = it; validationError = null },
                        label = { Text("Prev Reading") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("entry_prev_reading_input")
                    )
                    OutlinedTextField(
                        value = currReadingText,
                        onValueChange = { currReadingText = it; validationError = null },
                        label = { Text("Curr Reading") },
                        placeholder = { Text(formatNumberInputWithZero(prevVal)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("entry_curr_reading_input")
                    )
                }

                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    ElectricityMode.entries.forEachIndexed { idx, mode ->
                        SegmentedButton(
                            selected = elecMode == mode,
                            onClick = { elecMode = mode },
                            shape = SegmentedButtonDefaults.itemShape(idx, ElectricityMode.entries.size)
                        ) {
                            Text(mode.label, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }

                if (elecMode == ElectricityMode.FIXED_RATE) {
                    OutlinedTextField(
                        value = rateText,
                        onValueChange = { rateText = it; validationError = null },
                        label = { Text("Rate per Unit (₹/kWh)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("entry_rate_input")
                    )
                } else {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (mainLog != null && mainLog.govtBillAmount > 0.0) {
                                Text(
                                    text = "Govt Main Bill: ${BillingCalculator.formatInr(mainLog.govtBillAmount)} (${BillingCalculator.formatUnits(mainLog.unitsConsumed)}) • Effective Rate: ${BillingCalculator.formatInr(effectiveRate)}/kWh",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilterChip(
                                        selected = !useProportionalSplit,
                                        onClick = { useProportionalSplit = false },
                                        label = { Text("Main Meter Rate") }
                                    )
                                    FilterChip(
                                        selected = useProportionalSplit,
                                        onClick = { useProportionalSplit = true },
                                        label = { Text("Split 100% by Sub-Meters") }
                                    )
                                }
                            } else {
                                Text(
                                    text = "No Govt Main Meter bill logged for ${propertyName ?: "this house"} in ${BillingCalculator.formatMonthShort(data.selectedMonthKey)} yet. Log it in Main Meter or enter a fallback rate.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                OutlinedTextField(
                                    value = rateText,
                                    onValueChange = { rateText = it },
                                    label = { Text("Fallback Rate (₹/kWh)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Rounded.ElectricBolt,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "${BillingCalculator.formatUnits(units)} × ${BillingCalculator.formatInr(effectiveRate)}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Text(
                            text = BillingCalculator.formatInr(electricityAmount),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ElectricBolt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                text = "Direct Govt Meter (Tenant Pays DISCOM)",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Text(
                                text = "No sub-meter reading needed; electricity added to rent is ₹0.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }
            }

            Text(
                text = "Maintenance & Extra Charges",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = maintenanceText,
                    onValueChange = { maintenanceText = it; validationError = null },
                    label = { Text("Maintenance (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("entry_maintenance_input")
                )
                OutlinedTextField(
                    value = extraChargesText,
                    onValueChange = { extraChargesText = it; validationError = null },
                    label = { Text("Extra Charges (₹)") },
                    placeholder = { Text("0") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("entry_extra_charges_input")
                )
            }

            OutlinedTextField(
                value = extraChargesNoteText,
                onValueChange = { extraChargesNoteText = it },
                label = { Text("Extra / Maintenance Note (Optional)") },
                placeholder = { Text("e.g. Water tanker, parking, plumbing repair") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("entry_extra_note_input")
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Total Payable: ${BillingCalculator.formatInr(totalPayable)}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                TextButton(
                    onClick = {
                        val full = totalPayable.coerceAtLeast(0.0)
                        paidText = formatNumberInputWithZero(full)
                        if (full > 0.005 && paidDateMs <= 0L) {
                            paidDateMs = System.currentTimeMillis()
                        }
                    }
                ) {
                    Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Fill Full Amount")
                }
            }

            OutlinedTextField(
                value = paidText,
                onValueChange = { next ->
                    paidText = next
                    validationError = null
                    val parsed = next.toDoubleOrNull() ?: 0.0
                    if (parsed > 0.005 && paidDateMs <= 0L) {
                        paidDateMs = System.currentTimeMillis()
                    }
                },
                label = { Text("Amount Paid by Tenant (₹)") },
                placeholder = { Text("0") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("entry_paid_input")
            )

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showPaidDatePicker = true }
                    .testTag("entry_paid_date_button")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CalendarToday,
                            contentDescription = "Select payment date",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Payment Date",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (paidVal > 0.005) {
                                    val effectiveDate = if (paidDateMs > 0L) paidDateMs else System.currentTimeMillis()
                                    BillingCalculator.formatPaymentDate(effectiveDate)
                                } else if (paidDateMs > 0L) {
                                    "${BillingCalculator.formatPaymentDate(paidDateMs)} (enter amount)"
                                } else {
                                    "Auto-fills today when amount entered"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    Text(
                        text = "Change",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                PaymentMode.entries.forEachIndexed { idx, mode ->
                    SegmentedButton(
                        selected = paymentMode == mode,
                        onClick = { paymentMode = mode },
                        shape = SegmentedButtonDefaults.itemShape(idx, PaymentMode.entries.size)
                    ) {
                        Text(mode.label, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            OutlinedTextField(
                value = noteText,
                onValueChange = { noteText = it },
                label = { Text("Payment Note (Optional)") },
                placeholder = { Text("e.g. GPay on 5th, ₹500 pending") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Surface(
                color = when {
                    closingBalance > 0.005 -> MaterialTheme.colorScheme.errorContainer
                    closingBalance < -0.005 -> MaterialTheme.colorScheme.tertiaryContainer
                    else -> MaterialTheme.colorScheme.primaryContainer
                },
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                val balTextColor = when {
                    closingBalance > 0.005 -> MaterialTheme.colorScheme.onErrorContainer
                    closingBalance < -0.005 -> MaterialTheme.colorScheme.onTertiaryContainer
                    else -> MaterialTheme.colorScheme.onPrimaryContainer
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = when {
                            closingBalance > 0.005 -> "Remaining Due (Baaki)"
                            closingBalance < -0.005 -> "Advance Credit (Jama)"
                            else -> "Account Status"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        color = balTextColor
                    )
                    Text(
                        text = when {
                            closingBalance > 0.005 -> BillingCalculator.formatInr(closingBalance)
                            closingBalance < -0.005 -> BillingCalculator.formatInr(-closingBalance)
                            else -> "Fully Settled"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = balTextColor
                    )
                }
            }

            if (validationError != null) {
                Text(
                    text = validationError!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Button(
                onClick = {
                    val parsedRent = rentText.toDoubleOrNull()
                    val parsedMaint = if (maintenanceText.isBlank()) 0.0 else maintenanceText.toDoubleOrNull()
                    val parsedExtra = if (extraChargesText.isBlank()) 0.0 else extraChargesText.toDoubleOrNull()
                    val parsedPrev = if (tracksSubMeter) prevReadingText.toDoubleOrNull() else 0.0
                    val parsedCurr = if (tracksSubMeter) {
                        if (currReadingText.isBlank()) parsedPrev else currReadingText.toDoubleOrNull()
                    } else 0.0
                    val parsedPaid = if (paidText.isBlank()) 0.0 else paidText.toDoubleOrNull()

                    when {
                        parsedRent == null || parsedRent < 0.0 ->
                            validationError = "Enter a valid non-negative rent amount."
                        parsedMaint == null || parsedMaint < 0.0 ->
                            validationError = "Enter a valid non-negative maintenance amount."
                        parsedExtra == null || parsedExtra < 0.0 ->
                            validationError = "Enter a valid non-negative extra charges amount."
                        parsedPrev == null || parsedPrev < 0.0 ->
                            validationError = "Enter a valid previous sub-meter reading."
                        parsedCurr == null || parsedCurr < parsedPrev ->
                            validationError = "Current reading cannot be less than previous reading."
                        parsedPaid == null || parsedPaid < 0.0 ->
                            validationError = "Enter a valid non-negative payment amount."
                        effectiveRate < 0.0 ->
                            validationError = "Electricity rate cannot be negative."
                        else -> {
                            val finalPaidDateMs = when {
                                parsedPaid <= 0.005 -> 0L
                                paidDateMs > 0L -> paidDateMs
                                else -> System.currentTimeMillis()
                            }
                            onSave(
                                parsedRent,
                                parsedPrev,
                                parsedCurr,
                                elecMode,
                                effectiveRate,
                                if (tracksSubMeter) {
                                    BillingCalculator.roundToTwoDecimals((parsedCurr - parsedPrev) * effectiveRate)
                                } else 0.0,
                                parsedMaint,
                                parsedExtra,
                                extraChargesNoteText.trim(),
                                prevBalance,
                                parsedPaid,
                                finalPaidDateMs,
                                paymentMode,
                                noteText
                            )
                        }
                    }
                },
                enabled = !isSaving,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("save_monthly_entry_button")
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Rounded.Payments, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Save Monthly Entry")
                }
            }
        }
    }

    if (showPaidDatePicker) {
        val initialUtcMillis = localMillisToUtcMidnight(
            if (paidDateMs > 0L) paidDateMs else System.currentTimeMillis()
        )
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialUtcMillis)
        DatePickerDialog(
            onDismissRequest = { showPaidDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { utcMillis ->
                            paidDateMs = utcMidnightToLocalNoon(utcMillis)
                        }
                        showPaidDatePicker = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPaidDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

private fun localMillisToUtcMidnight(localMillis: Long): Long {
    val localCal = java.util.Calendar.getInstance().apply { timeInMillis = localMillis }
    val utcCal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(
            localCal.get(java.util.Calendar.YEAR),
            localCal.get(java.util.Calendar.MONTH),
            localCal.get(java.util.Calendar.DAY_OF_MONTH),
            0,
            0,
            0
        )
    }
    return utcCal.timeInMillis
}

private fun utcMidnightToLocalNoon(utcMillis: Long): Long {
    val utcCal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
        timeInMillis = utcMillis
    }
    val localCal = java.util.Calendar.getInstance().apply {
        clear()
        set(
            utcCal.get(java.util.Calendar.YEAR),
            utcCal.get(java.util.Calendar.MONTH),
            utcCal.get(java.util.Calendar.DAY_OF_MONTH),
            12,
            0,
            0
        )
    }
    return localCal.timeInMillis
}
