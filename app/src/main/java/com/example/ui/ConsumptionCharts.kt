package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.StackedBarChart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.BillingCalculator
import com.example.domain.ReconciliationChartData
import com.example.model.MonthlyLedger
import kotlin.math.abs

@Composable
fun TenantConsumptionChartCard(
    monthKey: String,
    chartData: ReconciliationChartData,
    modifier: Modifier = Modifier
) {
    val segments = chartData.tenantSegments
    val maxUnits = segments.maxOfOrNull { it.unitsConsumed }?.coerceAtLeast(1.0) ?: 1.0
    val barColor = MaterialTheme.colorScheme.tertiary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val borderColor = MaterialTheme.colorScheme.outlineVariant

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .fillMaxWidth()
            .testTag("tenant_consumption_chart_card")
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
                    Icon(
                        Icons.Rounded.BarChart,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Tenant Electricity Usage",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "${BillingCalculator.formatMonthFull(monthKey)} • Sub-meter kWh by tenant",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (chartData.subMeterUnitsSum > 0.0) {
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(
                            text = BillingCalculator.formatUnits(chartData.subMeterUnitsSum),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            if (segments.isEmpty()) {
                Text(
                    text = "Record tenant sub-meter readings in the Ledger tab to see each tenant's unit consumption bar chart.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    segments.forEach { item ->
                        val fraction = (item.unitsConsumed / maxUnits).toFloat().coerceIn(0f, 1f)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${item.roomNumber} • ${item.tenantName}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "${BillingCalculator.formatUnits(item.unitsConsumed)} (${item.sharePercent.toInt()}%)",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                            }

                            Canvas(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(14.dp)
                            ) {
                                val radius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                                drawRoundRect(
                                    color = trackColor,
                                    size = size,
                                    cornerRadius = radius
                                )
                                drawRoundRect(
                                    color = borderColor,
                                    size = size,
                                    cornerRadius = radius,
                                    style = Stroke(width = 0.75.dp.toPx())
                                )
                                if (fraction > 0f) {
                                    drawRoundRect(
                                        color = barColor,
                                        size = Size(
                                            width = (size.width * fraction).coerceAtLeast(8.dp.toPx()),
                                            height = size.height
                                        ),
                                        cornerRadius = radius
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MainVsTenantsReconciliationChartCard(
    monthKey: String,
    chartData: ReconciliationChartData,
    modifier: Modifier = Modifier
) {
    val mainMeterColor = MaterialTheme.colorScheme.secondary
    val tenantAccentA = MaterialTheme.colorScheme.tertiary
    val tenantAccentB = MaterialTheme.colorScheme.primary
    val excessColor = MaterialTheme.colorScheme.error
    val surfaceBg = MaterialTheme.colorScheme.surface
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val borderColor = MaterialTheme.colorScheme.outlineVariant
    val tenantColors = listOf(
        tenantAccentA,
        tenantAccentB,
        tenantAccentA.copy(alpha = 0.65f),
        tenantAccentB.copy(alpha = 0.65f)
    )

    val combinedBarTotal = if (chartData.differenceUnits > 0.0) {
        chartData.subMeterUnitsSum + chartData.differenceUnits
    } else {
        chartData.subMeterUnitsSum
    }
    val scaleMax = maxOf(chartData.mainUnits, combinedBarTotal, 1.0)

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .fillMaxWidth()
            .testTag("main_vs_tenants_reconciliation_chart_card")
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
                    Icon(
                        Icons.Rounded.StackedBarChart,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Main vs. All Tenants Graph",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "${BillingCalculator.formatMonthFull(monthKey)} • Consolidated kWh comparison",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                val diff = chartData.differenceUnits
                val diffLabel = when {
                    abs(diff) <= 0.01 && (chartData.mainUnits > 0.0 || chartData.subMeterUnitsSum > 0.0) ->
                        "Exact Match"
                    diff > 0.01 -> "+${BillingCalculator.formatUnits(diff)} Gap"
                    diff < -0.01 -> "${BillingCalculator.formatUnits(diff)} Excess"
                    else -> "No Data"
                }
                val badgeBg = when {
                    diff < -0.01 -> MaterialTheme.colorScheme.errorContainer
                    diff > 0.01 -> MaterialTheme.colorScheme.secondaryContainer
                    abs(diff) <= 0.01 && (chartData.mainUnits > 0.0 || chartData.subMeterUnitsSum > 0.0) ->
                        MaterialTheme.colorScheme.primaryContainer
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
                val badgeFg = when {
                    diff < -0.01 -> MaterialTheme.colorScheme.onErrorContainer
                    diff > 0.01 -> MaterialTheme.colorScheme.onSecondaryContainer
                    abs(diff) <= 0.01 && (chartData.mainUnits > 0.0 || chartData.subMeterUnitsSum > 0.0) ->
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                Surface(
                    color = badgeBg,
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = diffLabel,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = badgeFg,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            if (chartData.mainUnits <= 0.0 && chartData.subMeterUnitsSum <= 0.0) {
                Text(
                    text = "Enter or scan your Govt Main Meter bill and at least one tenant sub-meter reading to visualize the difference between main meter consumption and tenant totals.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                // Bar 1: Govt Main Meter
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Govt Main Meter Total",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = BillingCalculator.formatUnits(chartData.mainUnits),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(22.dp)
                    ) {
                        val radius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                        drawRoundRect(color = trackColor, size = size, cornerRadius = radius)
                        val frac = (chartData.mainUnits / scaleMax).toFloat().coerceIn(0f, 1f)
                        if (frac > 0f) {
                            drawRoundRect(
                                color = mainMeterColor,
                                size = Size((size.width * frac).coerceAtLeast(10.dp.toPx()), size.height),
                                cornerRadius = radius
                            )
                        }
                    }
                }

                // Bar 2: All Tenants Stacked + Common Area / Loss Difference (Hatched)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "All Tenants (${chartData.tenantSegments.size}) + Difference",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = BillingCalculator.formatUnits(chartData.subMeterUnitsSum),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }

                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(22.dp)
                    ) {
                        val radius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                        drawRoundRect(color = trackColor, size = size, cornerRadius = radius)
                        var cursorX = 0f

                        chartData.tenantSegments.forEachIndexed { idx, seg ->
                            val segFrac = (seg.unitsConsumed / scaleMax).toFloat().coerceIn(0f, 1f)
                            val segW = size.width * segFrac
                            if (segW > 0f) {
                                val fill = tenantColors[idx % tenantColors.size]
                                drawRect(
                                    color = fill,
                                    topLeft = Offset(cursorX, 0f),
                                    size = Size(segW, size.height)
                                )
                                if (cursorX > 0f) {
                                    drawLine(
                                        color = surfaceBg,
                                        start = Offset(cursorX, 0f),
                                        end = Offset(cursorX, size.height),
                                        strokeWidth = 1.5.dp.toPx()
                                    )
                                }
                                cursorX += segW
                            }
                        }

                        // Common Area / Line Loss Difference (Amber Hatched segment)
                        if (chartData.differenceUnits > 0.01) {
                            val diffFrac = (chartData.differenceUnits / scaleMax).toFloat().coerceIn(0f, 1f)
                            val diffW = (size.width * diffFrac).coerceAtMost(size.width - cursorX)
                            if (diffW > 1f) {
                                clipRect(
                                    left = cursorX,
                                    top = 0f,
                                    right = cursorX + diffW,
                                    bottom = size.height
                                ) {
                                    drawRect(
                                        color = mainMeterColor.copy(alpha = 0.16f),
                                        topLeft = Offset(cursorX, 0f),
                                        size = Size(diffW, size.height)
                                    )
                                    val step = 6.dp.toPx()
                                    var x = cursorX - size.height
                                    while (x < cursorX + diffW + size.height) {
                                        drawLine(
                                            color = mainMeterColor,
                                            start = Offset(x, size.height),
                                            end = Offset(x + size.height, 0f),
                                            strokeWidth = 1.5.dp.toPx()
                                        )
                                        x += step
                                    }
                                    drawRect(
                                        color = mainMeterColor,
                                        topLeft = Offset(cursorX, 0f),
                                        size = Size(diffW, size.height),
                                        style = Stroke(width = 1.dp.toPx())
                                    )
                                }
                            }
                        }

                        drawRoundRect(
                            color = borderColor,
                            size = size,
                            cornerRadius = radius,
                            style = Stroke(width = 0.75.dp.toPx())
                        )
                    }
                }

                // Semantic Legend
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    chartData.tenantSegments.forEachIndexed { idx, seg ->
                        LegendChip(
                            label = "${seg.roomNumber}: ${BillingCalculator.formatUnits(seg.unitsConsumed)}",
                            swatchColor = tenantColors[idx % tenantColors.size],
                            isHatched = false,
                            borderColor = borderColor
                        )
                    }
                    if (chartData.differenceUnits > 0.01) {
                        LegendChip(
                            label = "Common / Loss: ${BillingCalculator.formatUnits(chartData.differenceUnits)}",
                            swatchColor = mainMeterColor,
                            isHatched = true,
                            borderColor = mainMeterColor
                        )
                    } else if (chartData.differenceUnits < -0.01) {
                        LegendChip(
                            label = "Sub-Meter Excess: ${BillingCalculator.formatUnits(-chartData.differenceUnits)}",
                            swatchColor = excessColor,
                            isHatched = false,
                            borderColor = excessColor
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendChip(
    label: String,
    swatchColor: Color,
    isHatched: Boolean,
    borderColor: Color
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (isHatched) {
            Canvas(
                modifier = Modifier
                    .size(12.dp)
                    .clip(RoundedCornerShape(3.dp))
            ) {
                drawLine(
                    color = borderColor,
                    start = Offset(0f, size.height),
                    end = Offset(size.width, 0f),
                    strokeWidth = 1.5.dp.toPx()
                )
                drawRect(color = borderColor, style = Stroke(width = 1.dp.toPx()))
            }
        } else {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(swatchColor)
                    .border(0.75.dp, borderColor, RoundedCornerShape(3.dp))
            )
        }
        Spacer(Modifier.width(5.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun TenantPassbookUsageChartCard(
    ledgers: List<MonthlyLedger>,
    modifier: Modifier = Modifier
) {
    if (ledgers.isEmpty()) return
    val recent = ledgers.sortedBy { it.monthKey }.takeLast(6)
    val maxUnits = recent.maxOfOrNull { it.unitsConsumed }?.coerceAtLeast(1.0) ?: 1.0
    val barColor = MaterialTheme.colorScheme.tertiary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .fillMaxWidth()
            .testTag("tenant_passbook_usage_chart")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Monthly Sub-Meter Trend (kWh)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.tertiary
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                recent.forEach { entry ->
                    val frac = (entry.unitsConsumed / maxUnits).toFloat().coerceIn(0.06f, 1f)
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        Text(
                            text = "${entry.unitsConsumed.toInt()} kWh",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                        Spacer(Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.62f)
                                .height(64.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(trackColor),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height((64f * frac).dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(barColor)
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = BillingCalculator.formatMonthShort(entry.monthKey).take(3),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
