package com.example.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.HomeWork
import androidx.compose.material.icons.rounded.PeopleOutline
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.domain.BillingCalculator
import com.example.export.TenantStatementPdfGenerator
import com.example.model.DEFAULT_PROPERTY_ID
import com.example.model.MeterTrackingMode
import com.example.model.MonthlyLedger
import com.example.model.Property
import com.example.model.Tenant
import kotlin.math.abs

private enum class OpeningBalanceType(val label: String) {
    ZERO("Settled (₹0)"),
    DUE("Old Due (+)"),
    ADVANCE("Old Advance (-)")
}

@Composable
fun TenantsScreen(
    data: KirayaDashboardData,
    isSaving: Boolean,
    isExportingPdf: Boolean,
    showAddTenantSheet: Boolean,
    onSetShowAddTenantSheet: (Boolean) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSelectPropertyFilter: (String?) -> Unit,
    onManageProperties: () -> Unit,
    onToggleShowVacated: () -> Unit,
    onSaveTenant: (
        tenantId: String?,
        propertyId: String,
        name: String,
        roomNumber: String,
        phone: String,
        monthlyRent: Double,
        defaultMaintenance: Double,
        meterTrackingMode: MeterTrackingMode,
        initialMeterReading: Double,
        electricityRatePerUnit: Double,
        openingBalance: Double,
        active: Boolean,
        onDone: () -> Unit
    ) -> Unit,
    onSetTenantActive: (tenantId: String, active: Boolean) -> Unit,
    onDeleteTenant: (tenantId: String, onDone: () -> Unit) -> Unit,
    onShareTenantPdf: (Tenant, String, Boolean, List<MonthlyLedger>) -> Unit,
    onSaveTenantPdf: (Uri, Tenant, String, Boolean, List<MonthlyLedger>) -> Unit
) {
    var selectedTenantId by remember { mutableStateOf<String?>(null) }
    var editingTenant by remember { mutableStateOf<Tenant?>(null) }

    val selectedTenant = data.tenants.firstOrNull { it.id == selectedTenantId }

    if (selectedTenant != null) {
        val propertyName = data.properties.firstOrNull { it.id == selectedTenant.propertyId }?.name ?: "Main House"
        val tenantLedgers = data.allLedgers
            .filter { it.tenantId == selectedTenant.id }
            .sortedByDescending { it.monthKey }
        TenantPassbookScreen(
            tenant = selectedTenant,
            propertyName = propertyName,
            showPropertyName = data.multiHouseEnabled,
            ledgers = tenantLedgers,
            isSaving = isSaving,
            isExportingPdf = isExportingPdf,
            onBack = { selectedTenantId = null },
            onEdit = { editingTenant = selectedTenant },
            onToggleActive = { onSetTenantActive(selectedTenant.id, !selectedTenant.active) },
            onDelete = {
                onDeleteTenant(selectedTenant.id) {
                    selectedTenantId = null
                }
            },
            onSharePdf = {
                onShareTenantPdf(selectedTenant, propertyName, data.multiHouseEnabled, tenantLedgers)
            },
            onSavePdf = { uri ->
                onSaveTenantPdf(uri, selectedTenant, propertyName, data.multiHouseEnabled, tenantLedgers)
            }
        )
    } else {
        val activeCount = data.tenants.count { it.active }
        val vacatedCount = data.tenants.count { !it.active }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "$activeCount Active Tenants",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        if (data.multiHouseEnabled) {
                            val propSummary = "${data.properties.size} ${if (data.properties.size == 1) "house" else "houses"}"
                            Text(
                                text = if (vacatedCount > 0) "$vacatedCount vacated • $propSummary" else propSummary,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else if (vacatedCount > 0) {
                            Text(
                                text = "$vacatedCount vacated",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (vacatedCount > 0) {
                            FilterChip(
                                selected = data.showVacatedTenants,
                                onClick = onToggleShowVacated,
                                label = { Text("Vacated") }
                            )
                        }
                        Button(
                            onClick = { onSetShowAddTenantSheet(true) },
                            modifier = Modifier.testTag("add_tenant_button")
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Add")
                        }
                    }
                }
            }

            item {
                TenantSearchBar(
                    query = data.searchQuery,
                    onQueryChange = onSearchQueryChange,
                    placeholder = if (data.multiHouseEnabled) {
                        "Search tenant name, room, or house…"
                    } else {
                        "Search tenant name or room…"
                    }
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

            if (data.tenants.isEmpty()) {
                item {
                    EmptySectionCard(
                        icon = Icons.Rounded.PeopleOutline,
                        title = "No tenants added yet",
                        subtitle = if (data.multiHouseEnabled) {
                            "Add a tenant with their house, room/shop label, monthly rent, maintenance, and meter type."
                        } else {
                            "Add a tenant with their room/shop label, monthly rent, maintenance, and meter type."
                        },
                        action = {
                            Button(onClick = { onSetShowAddTenantSheet(true) }) {
                                Icon(Icons.Rounded.Add, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Add Tenant")
                            }
                        }
                    )
                }
            } else if (data.groupedTenantSections.isEmpty()) {
                item {
                    EmptySectionCard(
                        icon = Icons.Rounded.PeopleOutline,
                        title = if (data.searchQuery.isNotBlank()) {
                            "No tenants match \"${data.searchQuery.trim()}\""
                        } else {
                            "No tenants to display"
                        },
                        subtitle = if (data.multiHouseEnabled) {
                            "Clear search, switch house filters above, or add a new tenant."
                        } else {
                            "Clear search or add a new tenant."
                        },
                        action = {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (data.searchQuery.isNotBlank() || (data.multiHouseEnabled && data.selectedPropertyId != null)) {
                                    OutlinedButton(
                                        onClick = {
                                            onSearchQueryChange("")
                                            if (data.multiHouseEnabled) {
                                                onSelectPropertyFilter(null)
                                            }
                                        }
                                    ) {
                                        Text("Reset Filters")
                                    }
                                }
                                Button(onClick = { onSetShowAddTenantSheet(true) }) {
                                    Text("Add Tenant")
                                }
                            }
                        }
                    )
                }
            } else if (data.multiHouseEnabled) {
                data.groupedTenantSections.forEach { group ->
                    item(key = "tenant_header_${group.property.id}") {
                        PropertySectionHeader(
                            property = group.property,
                            itemCount = group.tenants.size
                        )
                    }
                    items(items = group.tenants, key = { it.id }) { tenant ->
                        val netBalance = BillingCalculator.resolveCurrentNetBalance(tenant, data.allLedgers)
                        TenantDirectoryCard(
                            tenant = tenant,
                            netBalance = netBalance,
                            onClick = { selectedTenantId = tenant.id }
                        )
                    }
                }
            } else {
                val flatTenants = data.groupedTenantSections.flatMap { it.tenants }
                items(items = flatTenants, key = { it.id }) { tenant ->
                    val netBalance = BillingCalculator.resolveCurrentNetBalance(tenant, data.allLedgers)
                    TenantDirectoryCard(
                        tenant = tenant,
                        netBalance = netBalance,
                        onClick = { selectedTenantId = tenant.id }
                    )
                }
            }

            item { Spacer(Modifier.height(72.dp)) }
        }
    }

    if (showAddTenantSheet || editingTenant != null) {
        val defaultProp = if (data.multiHouseEnabled) {
            data.selectedPropertyId ?: data.properties.firstOrNull()?.id ?: DEFAULT_PROPERTY_ID
        } else {
            DEFAULT_PROPERTY_ID
        }
        TenantFormBottomSheet(
            properties = data.properties,
            defaultPropertyId = defaultProp,
            showPropertySelector = data.multiHouseEnabled,
            defaultElectricityRate = data.settings.defaultRatePerUnit,
            effectiveMonthKey = data.selectedMonthKey,
            existing = editingTenant,
            isSaving = isSaving,
            onDismiss = {
                onSetShowAddTenantSheet(false)
                editingTenant = null
            },
            onSave = { propId, name, room, phone, rent, defaultMaint, meterMode, initMeter, elecRate, openBal, active ->
                onSaveTenant(
                    editingTenant?.id,
                    propId,
                    name,
                    room,
                    phone,
                    rent,
                    defaultMaint,
                    meterMode,
                    initMeter,
                    elecRate,
                    openBal,
                    active
                ) {
                    onSetShowAddTenantSheet(false)
                    editingTenant = null
                }
            }
        )
    }
}

@Composable
private fun TenantDirectoryCard(
    tenant: Tenant,
    netBalance: Double,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("tenant_card_${tenant.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = if (tenant.active) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.size(46.dp)
                ) {
                    Column(
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Person,
                            contentDescription = null,
                            tint = if (tenant.active) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = tenant.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (!tenant.tracksSubMeter) {
                            Spacer(Modifier.width(6.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "Direct Govt",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        if (!tenant.active) {
                            Spacer(Modifier.width(6.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "Vacated",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    val subtitle = buildString {
                        append("${tenant.roomNumber} • Rent ${BillingCalculator.formatInr(tenant.monthlyRent)}")
                        if (tenant.defaultMaintenance > 0.005) {
                            append(" + ${BillingCalculator.formatInr(tenant.defaultMaintenance)} maint")
                        }
                        if (tenant.tracksSubMeter) {
                            append(" • ${BillingCalculator.formatInr(tenant.electricityRatePerUnit)}/kWh")
                        }
                    }
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (tenant.phone.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Rounded.Phone,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = tenant.phone,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.width(8.dp))
            BalanceStatusBadge(closingBalance = netBalance, isRecorded = true)
        }
    }
}

@Composable
private fun TenantPassbookScreen(
    tenant: Tenant,
    propertyName: String,
    showPropertyName: Boolean,
    ledgers: List<MonthlyLedger>,
    isSaving: Boolean,
    isExportingPdf: Boolean,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onToggleActive: () -> Unit,
    onDelete: () -> Unit,
    onSharePdf: () -> Unit,
    onSavePdf: (Uri) -> Unit
) {
    BackHandler(onBack = onBack)
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val currentNetBalance = ledgers.firstOrNull()?.closingBalance ?: tenant.openingBalance

    val savePdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null) {
            onSavePdf(uri)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("passbook_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back to tenants list"
                        )
                    }
                    Column {
                        Text(
                            text = tenant.name,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = if (showPropertyName) {
                                "${tenant.roomNumber} • $propertyName"
                            } else {
                                "${tenant.roomNumber} • Rent Passbook"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("edit_tenant_button")
                    ) {
                        Icon(Icons.Rounded.Edit, contentDescription = "Edit tenant")
                    }
                    IconButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("delete_tenant_button")
                    ) {
                        Icon(
                            Icons.Rounded.DeleteOutline,
                            contentDescription = "Delete tenant",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = MaterialTheme.shapes.large,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Current Net Balance",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = when {
                                    currentNetBalance > 0.005 -> "Due ${BillingCalculator.formatInr(currentNetBalance)}"
                                    currentNetBalance < -0.005 -> "Advance ${BillingCalculator.formatInr(-currentNetBalance)}"
                                    else -> "Settled (₹0)"
                                },
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = when {
                                    currentNetBalance > 0.005 -> MaterialTheme.colorScheme.error
                                    else -> MaterialTheme.colorScheme.primary
                                }
                            )
                        }
                        OutlinedButton(onClick = onToggleActive, enabled = !isSaving) {
                            Text(if (tenant.active) "Mark Vacated" else "Reactivate")
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Monthly Rent", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(BillingCalculator.formatInr(tenant.monthlyRent), style = MaterialTheme.typography.titleSmall)
                        }
                        Column {
                            Text("Default Maint", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(BillingCalculator.formatInr(tenant.defaultMaintenance), style = MaterialTheme.typography.titleSmall)
                        }
                        Column {
                            Text(
                                if (tenant.tracksSubMeter) "Unit Rate" else "Meter Type",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                if (tenant.tracksSubMeter) "${BillingCalculator.formatInr(tenant.electricityRatePerUnit)}/kWh" else "Direct Govt",
                                style = MaterialTheme.typography.titleSmall
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onSharePdf,
                            enabled = !isExportingPdf,
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("share_tenant_pdf_button")
                        ) {
                            if (isExportingPdf) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Share PDF", maxLines = 1)
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                savePdfLauncher.launch(TenantStatementPdfGenerator.buildSuggestedFileName(tenant))
                            },
                            enabled = !isExportingPdf,
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("save_tenant_pdf_button")
                        ) {
                            Icon(Icons.Rounded.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Save PDF", maxLines = 1)
                        }
                    }
                }
            }
        }

        if (ledgers.isEmpty()) {
            item {
                EmptySectionCard(
                    icon = Icons.Rounded.PeopleOutline,
                    title = "No monthly records yet",
                    subtitle = "Go to the Ledger tab and tap ${tenant.name} to record their first monthly entry."
                )
            }
        } else {
            if (tenant.tracksSubMeter) {
                item {
                    TenantPassbookUsageChartCard(ledgers = ledgers)
                }
            }
            items(items = ledgers, key = { it.id }) { entry ->
                PassbookMonthRow(entry = entry, tracksSubMeter = tenant.tracksSubMeter)
            }
        }

        item { Spacer(Modifier.height(72.dp)) }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete ${tenant.name}?") },
            text = { Text("This permanently removes the tenant and all their monthly rent and payment history.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun PassbookMonthRow(
    entry: MonthlyLedger,
    tracksSubMeter: Boolean
) {
    val maintPlusExtra = entry.maintenanceAmount + entry.extraChargesAmount
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = BillingCalculator.formatMonthFull(entry.monthKey),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                BalanceStatusBadge(closingBalance = entry.closingBalance, isRecorded = true)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Rent: ${BillingCalculator.formatInr(entry.rentAmount)}",
                    style = MaterialTheme.typography.bodySmall
                )
                if (tracksSubMeter) {
                    Text(
                        text = "Elec (${BillingCalculator.formatUnits(entry.unitsConsumed)}): ${BillingCalculator.formatInr(entry.electricityAmount)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    Text(
                        text = "Direct Govt Meter (₹0)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }

            if (maintPlusExtra > 0.005) {
                Text(
                    text = buildString {
                        append("Maintenance: ${BillingCalculator.formatInr(entry.maintenanceAmount)}")
                        if (entry.extraChargesAmount > 0.005) {
                            append(" • Extra: ${BillingCalculator.formatInr(entry.extraChargesAmount)}")
                        }
                        if (entry.extraChargesNote.isNotBlank()) {
                            append(" (${entry.extraChargesNote})")
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (tracksSubMeter) {
                        "Meter: ${BillingCalculator.formatReading(entry.prevReading)} → ${BillingCalculator.formatReading(entry.currReading)} (@ ${BillingCalculator.formatInr(entry.ratePerUnit)}/u)"
                    } else {
                        "Tenant pays DISCOM directly"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Paid (${entry.resolvedPaymentMode.label}): ${BillingCalculator.formatInr(entry.amountPaid)}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (entry.amountPaid > 0.005 && entry.paidDateMs > 0L) {
                        Text(
                            text = "Paid on ${BillingCalculator.formatPaymentDate(entry.paidDateMs)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (entry.paymentNote.isNotBlank()) {
                Text(
                    text = "Note: ${entry.paymentNote}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TenantFormBottomSheet(
    properties: List<Property>,
    defaultPropertyId: String,
    showPropertySelector: Boolean,
    defaultElectricityRate: Double,
    effectiveMonthKey: String,
    existing: Tenant?,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (
        propertyId: String,
        name: String,
        roomNumber: String,
        phone: String,
        monthlyRent: Double,
        defaultMaintenance: Double,
        meterTrackingMode: MeterTrackingMode,
        initialMeterReading: Double,
        electricityRatePerUnit: Double,
        openingBalance: Double,
        active: Boolean
    ) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedPropertyId by remember(existing?.id, defaultPropertyId) {
        mutableStateOf(
            existing?.propertyId?.takeIf { id -> properties.any { it.id == id } }
                ?: defaultPropertyId
        )
    }
    var name by remember(existing?.id) { mutableStateOf(existing?.name ?: "") }
    var room by remember(existing?.id) { mutableStateOf(existing?.roomNumber ?: "") }
    var phone by remember(existing?.id) { mutableStateOf(existing?.phone ?: "") }
    var rentText by remember(existing?.id) { mutableStateOf(existing?.let { formatNumberInputWithZero(it.monthlyRent) } ?: "") }
    var maintenanceText by remember(existing?.id) {
        mutableStateOf(
            if (existing != null && existing.defaultMaintenance > 0.0) {
                formatNumberInputWithZero(existing.defaultMaintenance)
            } else ""
        )
    }
    var meterMode by remember(existing?.id) {
        val defaultProp = properties.firstOrNull { it.id == selectedPropertyId }
        mutableStateOf(
            existing?.resolvedMeterMode
                ?: if (defaultProp?.trackMainMeter == false) {
                    MeterTrackingMode.DIRECT_GOVT_METER
                } else {
                    MeterTrackingMode.SUB_METER
                }
        )
    }
    var initMeterText by remember(existing?.id) {
        mutableStateOf(existing?.let { formatNumberInputWithZero(it.initialMeterReading) } ?: "0")
    }
    var elecRateText by remember(existing?.id, defaultElectricityRate) {
        mutableStateOf(
            formatNumberInputWithZero(existing?.electricityRatePerUnit ?: defaultElectricityRate)
        )
    }

    val initialBalType = when {
        existing == null || abs(existing.openingBalance) < 0.005 -> OpeningBalanceType.ZERO
        existing.openingBalance > 0 -> OpeningBalanceType.DUE
        else -> OpeningBalanceType.ADVANCE
    }
    var balanceType by remember(existing?.id) { mutableStateOf(initialBalType) }
    var openingAmtText by remember(existing?.id) {
        mutableStateOf(
            if (existing != null && abs(existing.openingBalance) >= 0.005) {
                formatNumberInputWithZero(abs(existing.openingBalance))
            } else ""
        )
    }
    var validationError by remember { mutableStateOf<String?>(null) }

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
                text = if (existing == null) "Add New Tenant" else "Edit Tenant",
                style = MaterialTheme.typography.titleLarge
            )

            if (showPropertySelector) {
                Text(
                    text = "House / Property",
                    style = MaterialTheme.typography.titleSmall
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    properties.forEach { prop ->
                        FilterChip(
                            selected = selectedPropertyId == prop.id,
                            onClick = {
                                selectedPropertyId = prop.id
                                if (existing == null) {
                                    meterMode = if (prop.trackMainMeter) {
                                        MeterTrackingMode.SUB_METER
                                    } else {
                                        MeterTrackingMode.DIRECT_GOVT_METER
                                    }
                                }
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Rounded.HomeWork,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            label = { Text(prop.name) },
                            modifier = Modifier.testTag("tenant_form_property_${prop.id}")
                        )
                    }
                }
            }

            OutlinedTextField(
                value = room,
                onValueChange = { room = it; validationError = null },
                label = { Text("Room / Flat / Shop Label *") },
                placeholder = { Text("e.g. Room 101, 1st Floor Front") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("tenant_room_input")
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it; validationError = null },
                label = { Text("Tenant Name *") },
                placeholder = { Text("e.g. Ramesh Kumar") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("tenant_name_input")
            )

            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text("Mobile Number (Optional)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = rentText,
                    onValueChange = { rentText = it; validationError = null },
                    label = { Text("Monthly Rent (₹) *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("tenant_rent_input")
                )
                OutlinedTextField(
                    value = maintenanceText,
                    onValueChange = { maintenanceText = it; validationError = null },
                    label = { Text("Default Maint (₹/mo)") },
                    placeholder = { Text("0") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("tenant_maintenance_input")
                )
            }

            Text(
                text = "Electricity Meter Setup",
                style = MaterialTheme.typography.titleSmall
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                MeterTrackingMode.entries.forEachIndexed { idx, mode ->
                    SegmentedButton(
                        selected = meterMode == mode,
                        onClick = { meterMode = mode },
                        shape = SegmentedButtonDefaults.itemShape(idx, MeterTrackingMode.entries.size),
                        modifier = Modifier.testTag("meter_mode_${mode.name.lowercase()}")
                    ) {
                        Text(mode.shortLabel, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            if (meterMode == MeterTrackingMode.SUB_METER) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = initMeterText,
                        onValueChange = { initMeterText = it; validationError = null },
                        label = { Text("Initial Reading (kWh)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("tenant_meter_input")
                    )
                    OutlinedTextField(
                        value = elecRateText,
                        onValueChange = { elecRateText = it; validationError = null },
                        label = { Text("Unit Rate (₹/kWh) *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("tenant_rate_input")
                    )
                }
                val parsedDraftRate = elecRateText.toDoubleOrNull()
                if (existing != null && parsedDraftRate != null && abs(parsedDraftRate - existing.electricityRatePerUnit) > 0.005) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Rate change applies from ${BillingCalculator.formatMonthFull(effectiveMonthKey)} onwards. Earlier months keep their original rate.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            } else {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.65f),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Direct Govt Meter: Tenant has their own electricity connection and pays the DISCOM directly. No sub-meter or main meter monitoring needed for this tenant.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Text(
                text = "Opening Balance (Before First Recorded Month)",
                style = MaterialTheme.typography.titleSmall
            )

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                OpeningBalanceType.entries.forEachIndexed { idx, type ->
                    SegmentedButton(
                        selected = balanceType == type,
                        onClick = { balanceType = type },
                        shape = SegmentedButtonDefaults.itemShape(idx, OpeningBalanceType.entries.size)
                    ) {
                        Text(type.label, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            if (balanceType != OpeningBalanceType.ZERO) {
                OutlinedTextField(
                    value = openingAmtText,
                    onValueChange = { openingAmtText = it; validationError = null },
                    label = {
                        Text(
                            if (balanceType == OpeningBalanceType.DUE) "Existing Due Amount (₹)"
                            else "Existing Advance Held (₹)"
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
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
                    val parsedMeter = if (meterMode == MeterTrackingMode.DIRECT_GOVT_METER) {
                        existing?.initialMeterReading ?: 0.0
                    } else if (initMeterText.isBlank()) 0.0 else initMeterText.toDoubleOrNull()
                    val parsedElecRate = if (meterMode == MeterTrackingMode.DIRECT_GOVT_METER) {
                        existing?.electricityRatePerUnit ?: defaultElectricityRate
                    } else elecRateText.toDoubleOrNull()
                    val parsedOpenAmt = if (balanceType == OpeningBalanceType.ZERO) {
                        0.0
                    } else {
                        openingAmtText.toDoubleOrNull()
                    }
                    when {
                        room.trim().isEmpty() -> validationError = "Please enter a room or unit label."
                        name.trim().isEmpty() -> validationError = "Please enter the tenant's name."
                        parsedRent == null || parsedRent < 0.0 -> validationError = "Enter a valid monthly rent."
                        parsedMaint == null || parsedMaint < 0.0 -> validationError = "Enter a valid default maintenance amount."
                        parsedMeter == null || parsedMeter < 0.0 -> validationError = "Enter a valid initial meter reading."
                        parsedElecRate == null || parsedElecRate < 0.0 || parsedElecRate > 1000.0 ->
                            validationError = "Enter a valid electricity unit rate (₹0 to ₹1,000/kWh)."
                        parsedOpenAmt == null || parsedOpenAmt < 0.0 -> validationError = "Enter a valid opening balance amount."
                        else -> {
                            val signedOpening = when (balanceType) {
                                OpeningBalanceType.ZERO -> 0.0
                                OpeningBalanceType.DUE -> parsedOpenAmt
                                OpeningBalanceType.ADVANCE -> -parsedOpenAmt
                            }
                            onSave(
                                selectedPropertyId,
                                name.trim(),
                                room.trim(),
                                phone.trim(),
                                parsedRent,
                                parsedMaint,
                                meterMode,
                                parsedMeter,
                                parsedElecRate,
                                signedOpening,
                                existing?.active ?: true
                            )
                        }
                    }
                },
                enabled = !isSaving,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("save_tenant_button")
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(if (existing == null) "Save Tenant" else "Update Tenant")
                }
            }
        }
    }
}
