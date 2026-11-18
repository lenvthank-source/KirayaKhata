package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ElectricMeter
import androidx.compose.material.icons.rounded.HomeWork
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.domain.BillingCalculator
import com.example.model.Property
import java.util.Calendar
import java.util.Locale

private val fullMonthList = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December"
)

@Composable
fun MonthSwitcherBar(
    monthKey: String,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSelectMonth: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showMonthYearPicker by remember { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = 1.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = onPreviousMonth,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("prev_month_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                    contentDescription = "Previous month",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            Row(
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { showMonthYearPicker = true }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .testTag("month_picker_trigger"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.CalendarMonth,
                    contentDescription = "Choose month and year",
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = BillingCalculator.formatMonthFull(monthKey),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.width(2.dp))
                Icon(
                    imageVector = Icons.Rounded.ArrowDropDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            IconButton(
                onClick = onNextMonth,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("next_month_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = "Next month",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }

    if (showMonthYearPicker) {
        MonthYearPickerDialog(
            currentMonthKey = monthKey,
            onConfirm = { pickedKey ->
                showMonthYearPicker = false
                onSelectMonth(pickedKey)
            },
            onDismiss = { showMonthYearPicker = false }
        )
    }
}

@Composable
private fun MonthYearPickerDialog(
    currentMonthKey: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val currentCalYear = remember { Calendar.getInstance().get(Calendar.YEAR) }
    val parts = currentMonthKey.split("-")
    val initialYear = parts.getOrNull(0)?.toIntOrNull() ?: currentCalYear
    val initialMonth = (parts.getOrNull(1)?.toIntOrNull() ?: 1).coerceIn(1, 12)

    val minYear = minOf(initialYear - 5, currentCalYear - 10)
    val maxYear = maxOf(initialYear + 3, currentCalYear + 5)
    val yearsList = remember(minYear, maxYear) { (minYear..maxYear).toList() }

    var selectedYear by remember(currentMonthKey) { mutableIntStateOf(initialYear) }
    var selectedMonth by remember(currentMonthKey) { mutableIntStateOf(initialMonth) }

    val monthListState = rememberLazyListState()
    val yearListState = rememberLazyListState()

    LaunchedEffect(Unit) {
        val mIndex = (selectedMonth - 1).coerceIn(0, 11)
        monthListState.scrollToItem((mIndex - 2).coerceAtLeast(0))
        val yIndex = yearsList.indexOf(selectedYear).coerceAtLeast(0)
        yearListState.scrollToItem((yIndex - 2).coerceAtLeast(0))
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = MaterialTheme.shapes.extraLarge,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp)
                .testTag("month_year_picker_dialog")
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Select Month & Year",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${fullMonthList[selectedMonth - 1]} $selectedYear",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    TextButton(
                        onClick = {
                            onConfirm(BillingCalculator.currentMonthKey())
                        },
                        modifier = Modifier.testTag("jump_current_month_button")
                    ) {
                        Text("This Month")
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Month",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1.35f)
                    )
                    Text(
                        text = "Year",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(0.85f)
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    LazyColumn(
                        state = monthListState,
                        modifier = Modifier.weight(1.35f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        itemsIndexed(fullMonthList) { idx, monthName ->
                            val monthNum = idx + 1
                            val isSelected = selectedMonth == monthNum
                            Surface(
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                },
                                shape = RoundedCornerShape(12.dp),
                                border = if (isSelected) {
                                    BorderStroke(1.2.dp, MaterialTheme.colorScheme.primary)
                                } else null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { selectedMonth = monthNum }
                                    .testTag("picker_month_$monthNum")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = monthName,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        }
                                    )
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Rounded.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    LazyColumn(
                        state = yearListState,
                        modifier = Modifier.weight(0.85f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        itemsIndexed(yearsList) { _, yearVal ->
                            val isSelected = selectedYear == yearVal
                            Surface(
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.secondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                },
                                shape = RoundedCornerShape(12.dp),
                                border = if (isSelected) {
                                    BorderStroke(1.2.dp, MaterialTheme.colorScheme.secondary)
                                } else null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { selectedYear = yearVal }
                                    .testTag("picker_year_$yearVal")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = yearVal.toString(),
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.onSecondaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text("Cancel")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val formattedKey = String.format(Locale.US, "%04d-%02d", selectedYear, selectedMonth)
                            onConfirm(formattedKey)
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("confirm_month_year_button")
                    ) {
                        Text("Apply")
                    }
                }
            }
        }
    }
}

private data class BadgeVisual(
    val label: String,
    val bgColor: Color,
    val textColor: Color,
    val borderStroke: BorderStroke?
)

@Composable
fun BalanceStatusBadge(
    closingBalance: Double,
    isRecorded: Boolean,
    modifier: Modifier = Modifier
) {
    val visual = when {
        !isRecorded && closingBalance > 0.005 -> BadgeVisual(
            label = "Pending • ${BillingCalculator.formatInr(closingBalance)}",
            bgColor = MaterialTheme.colorScheme.secondaryContainer,
            textColor = MaterialTheme.colorScheme.onSecondaryContainer,
            borderStroke = null
        )
        !isRecorded && closingBalance < -0.005 -> BadgeVisual(
            label = "Adv • ${BillingCalculator.formatInr(-closingBalance)}",
            bgColor = MaterialTheme.colorScheme.tertiaryContainer,
            textColor = MaterialTheme.colorScheme.onTertiaryContainer,
            borderStroke = null
        )
        !isRecorded -> BadgeVisual(
            label = "Not Recorded",
            bgColor = MaterialTheme.colorScheme.surfaceVariant,
            textColor = MaterialTheme.colorScheme.onSurfaceVariant,
            borderStroke = null
        )
        closingBalance > 0.005 -> BadgeVisual(
            label = "Due ${BillingCalculator.formatInr(closingBalance)}",
            bgColor = MaterialTheme.colorScheme.errorContainer,
            textColor = MaterialTheme.colorScheme.onErrorContainer,
            borderStroke = null
        )
        closingBalance < -0.005 -> BadgeVisual(
            label = "Advance ${BillingCalculator.formatInr(-closingBalance)}",
            bgColor = MaterialTheme.colorScheme.tertiaryContainer,
            textColor = MaterialTheme.colorScheme.onTertiaryContainer,
            borderStroke = null
        )
        else -> BadgeVisual(
            label = "Settled",
            bgColor = MaterialTheme.colorScheme.primaryContainer,
            textColor = MaterialTheme.colorScheme.onPrimaryContainer,
            borderStroke = null
        )
    }

    Surface(
        color = visual.bgColor,
        shape = RoundedCornerShape(50),
        border = visual.borderStroke,
        modifier = modifier
    ) {
        Text(
            text = visual.label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = visual.textColor,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}

@Composable
fun EmptySectionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = 1.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.size(56.dp)
            ) {
                Column(
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (action != null) {
                Spacer(Modifier.height(18.dp))
                action()
            }
        }
    }
}

fun formatNumberInput(value: Double): String {
    return if (value == 0.0) "" else if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
}

fun formatNumberInputWithZero(value: Double): String {
    return if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
}

@Composable
fun TenantSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String = "Search tenant, room, or house…",
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        leadingIcon = {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = "Search tenants",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.testTag("clear_search_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Clear search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        placeholder = {
            Text(
                text = placeholder,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("tenant_search_input")
    )
}

@Composable
fun PropertyFilterRow(
    properties: List<Property>,
    selectedPropertyId: String?,
    onSelectProperty: (String?) -> Unit,
    onManageProperties: () -> Unit,
    showAllOption: Boolean = true,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showAllOption) {
            FilterChip(
                selected = selectedPropertyId == null,
                onClick = { onSelectProperty(null) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.HomeWork,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                label = { Text("All Houses (${properties.size})") },
                modifier = Modifier.testTag("property_chip_all")
            )
        }

        properties.forEach { prop ->
            val selected = selectedPropertyId == prop.id
            FilterChip(
                selected = selected,
                onClick = { onSelectProperty(prop.id) },
                label = {
                    Text(
                        text = prop.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                modifier = Modifier.testTag("property_chip_${prop.id}")
            )
        }

        OutlinedButton(
            onClick = onManageProperties,
            shape = RoundedCornerShape(10.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            modifier = Modifier
                .heightIn(min = 36.dp)
                .testTag("manage_properties_button")
        ) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text("Houses", style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
fun PropertySectionHeader(
    property: Property,
    itemCount: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = Icons.Rounded.HomeWork,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = property.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!property.trackMainMeter) {
                Spacer(Modifier.width(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "Direct Govt Meters",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(50)
        ) {
            Text(
                text = if (itemCount == 1) "1 tenant" else "$itemCount tenants",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManagePropertiesBottomSheet(
    properties: List<Property>,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSaveProperty: (
        propertyId: String?,
        name: String,
        address: String,
        trackMainMeter: Boolean,
        consumerNumber: String,
        onDone: () -> Unit
    ) -> Unit,
    onToggleTrackMainMeter: (propertyId: String, trackMainMeter: Boolean) -> Unit,
    onDeleteProperty: (propertyId: String, fallbackPropertyId: String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var editingProperty by remember { mutableStateOf<Property?>(null) }
    var showForm by remember { mutableStateOf(false) }
    var nameText by remember { mutableStateOf("") }
    var addressText by remember { mutableStateOf("") }
    var consumerText by remember { mutableStateOf("") }
    var trackMain by remember { mutableStateOf(true) }
    var validationError by remember { mutableStateOf<String?>(null) }
    var deleteCandidate by remember { mutableStateOf<Property?>(null) }

    fun openEditor(prop: Property?) {
        editingProperty = prop
        nameText = prop?.name ?: ""
        addressText = prop?.address ?: ""
        consumerText = prop?.consumerNumber ?: ""
        trackMain = prop?.trackMainMeter ?: true
        validationError = null
        showForm = true
    }

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
                        text = "Manage Houses & Properties",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = "Group tenants by house and choose whether to monitor a Govt Main Meter per house.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!showForm) {
                    Button(
                        onClick = { openEditor(null) },
                        modifier = Modifier.testTag("add_house_button")
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Add")
                    }
                }
            }

            if (showForm) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    shape = MaterialTheme.shapes.large,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = if (editingProperty == null) "Add New House" else "Edit House",
                            style = MaterialTheme.typography.titleMedium
                        )
                        OutlinedTextField(
                            value = nameText,
                            onValueChange = { nameText = it; validationError = null },
                            label = { Text("House / Property Name *") },
                            placeholder = { Text("e.g. Sector 15 House, Plot 24") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("house_name_input")
                        )
                        OutlinedTextField(
                            value = addressText,
                            onValueChange = { addressText = it },
                            label = { Text("Address / Note (Optional)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = consumerText,
                            onValueChange = { consumerText = it },
                            label = { Text("Main Meter Consumer / CA # (Optional)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Track Govt Main Meter",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Turn off if tenants pay their own govt meters directly.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Switch(
                                checked = trackMain,
                                onCheckedChange = { trackMain = it },
                                modifier = Modifier.testTag("house_track_main_switch")
                            )
                        }

                        if (validationError != null) {
                            Text(
                                text = validationError!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { showForm = false }) {
                                Text("Cancel")
                            }
                            Spacer(Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    if (nameText.trim().isEmpty()) {
                                        validationError = "Please enter a house name."
                                    } else {
                                        onSaveProperty(
                                            editingProperty?.id,
                                            nameText.trim(),
                                            addressText.trim(),
                                            trackMain,
                                            consumerText.trim()
                                        ) {
                                            showForm = false
                                        }
                                    }
                                },
                                enabled = !isSaving,
                                modifier = Modifier.testTag("save_house_button")
                            ) {
                                Text(if (editingProperty == null) "Save House" else "Update")
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            properties.forEach { prop ->
                Surface(
                    color = MaterialTheme.colorScheme.surface,
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
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = prop.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (prop.address.isNotBlank()) {
                                    Text(
                                        text = prop.address,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (prop.consumerNumber.isNotBlank()) {
                                    Text(
                                        text = "Consumer #${prop.consumerNumber}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Row {
                                IconButton(onClick = { openEditor(prop) }) {
                                    Icon(Icons.Rounded.Edit, contentDescription = "Edit ${prop.name}")
                                }
                                if (properties.size > 1) {
                                    IconButton(onClick = { deleteCandidate = prop }) {
                                        Icon(
                                            Icons.Rounded.DeleteOutline,
                                            contentDescription = "Delete ${prop.name}",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.ElectricMeter,
                                    contentDescription = null,
                                    tint = if (prop.trackMainMeter) {
                                        MaterialTheme.colorScheme.secondary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = if (prop.trackMainMeter) {
                                        "Main Meter Monitored"
                                    } else {
                                        "Direct Govt Meters (No Main Meter)"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = prop.trackMainMeter,
                                onCheckedChange = { checked ->
                                    onToggleTrackMainMeter(prop.id, checked)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    val targetDelete = deleteCandidate
    if (targetDelete != null) {
        val fallback = properties.firstOrNull { it.id != targetDelete.id }
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text("Delete ${targetDelete.name}?") },
            text = {
                Text(
                    "Any tenants in ${targetDelete.name} will automatically move to ${fallback?.name ?: "your primary house"}."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        deleteCandidate = null
                        if (fallback != null) {
                            onDeleteProperty(targetDelete.id, fallback.id)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteCandidate = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}
