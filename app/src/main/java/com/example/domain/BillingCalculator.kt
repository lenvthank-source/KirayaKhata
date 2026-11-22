package com.example.domain

import com.example.model.MainMeterLog
import com.example.model.MonthlyLedger
import com.example.model.Tenant
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs
import kotlin.math.round

data class TenantMonthSummary(
    val tenant: Tenant,
    val ledger: MonthlyLedger?,
    val carriedPreviousBalance: Double,
    val suggestedPrevReading: Double,
    val totalPayable: Double,
    val amountPaid: Double,
    val closingBalance: Double,
    val isRecorded: Boolean
)

data class MonthlyDashboardSummary(
    val totalExpected: Double,
    val totalCollected: Double,
    val totalDue: Double,
    val totalAdvanceHeld: Double,
    val totalSubMeterUnits: Double,
    val totalElectricityBilled: Double,
    val recordedCount: Int,
    val activeTenantCount: Int
)

data class MeterReconciliation(
    val mainUnits: Double,
    val subMeterUnitsSum: Double,
    val differenceUnits: Double,
    val govtBillAmount: Double,
    val tenantBilledElectricity: Double,
    val effectiveMainRate: Double,
    val effectiveSubSplitRate: Double
)

data class TenantConsumptionPoint(
    val tenantId: String,
    val tenantName: String,
    val roomNumber: String,
    val unitsConsumed: Double,
    val electricityAmount: Double,
    val sharePercent: Double
)

data class ReconciliationChartData(
    val mainUnits: Double,
    val subMeterUnitsSum: Double,
    val differenceUnits: Double,
    val tenantSegments: List<TenantConsumptionPoint>
)

object BillingCalculator {

    private val monthNames = arrayOf(
        "Jan", "Feb", "Mar", "Apr", "May", "Jun",
        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
    )

    private val fullMonthNames = arrayOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December"
    )

    private fun formatIndianNumber(absRounded: Double): String {
        val longPart = absRounded.toLong()
        val fracPart = round((absRounded - longPart) * 100.0).toInt()
        val intDigits = longPart.toString()
        val groupedInt = if (intDigits.length <= 3) {
            intDigits
        } else {
            val lastThree = intDigits.takeLast(3)
            val prefix = intDigits.dropLast(3)
            val groupedPrefix = prefix.reversed().chunked(2).joinToString(",").reversed()
            "$groupedPrefix,$lastThree"
        }
        return when {
            fracPart == 0 -> groupedInt
            fracPart % 10 == 0 -> "$groupedInt.${fracPart / 10}"
            else -> "$groupedInt.${String.format(Locale.US, "%02d", fracPart)}"
        }
    }

    fun formatInr(amount: Double, showPositiveSign: Boolean = false): String {
        val rounded = roundToTwoDecimals(amount)
        val absText = formatIndianNumber(abs(rounded))
        return when {
            rounded < -0.005 -> "-₹$absText"
            rounded > 0.005 && showPositiveSign -> "+₹$absText"
            else -> "₹$absText"
        }
    }

    fun formatUnits(units: Double): String {
        return "${formatIndianNumber(abs(roundToTwoDecimals(units)))} kWh"
    }

    fun formatReading(reading: Double): String {
        return formatIndianNumber(abs(roundToTwoDecimals(reading)))
    }

    fun roundToTwoDecimals(value: Double): Double {
        return round(value * 100.0) / 100.0
    }

    fun currentMonthKey(): String {
        val cal = Calendar.getInstance()
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) + 1
        return String.format(Locale.US, "%04d-%02d", year, month)
    }

    fun shiftMonthKey(monthKey: String, deltaMonths: Int): String {
        val parts = monthKey.split("-")
        val year = parts.getOrNull(0)?.toIntOrNull() ?: 2026
        val month = parts.getOrNull(1)?.toIntOrNull() ?: 1
        val cal = Calendar.getInstance()
        cal.set(Calendar.YEAR, year)
        cal.set(Calendar.MONTH, month - 1)
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.add(Calendar.MONTH, deltaMonths)
        return String.format(
            Locale.US,
            "%04d-%02d",
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1
        )
    }

    fun formatMonthShort(monthKey: String): String {
        val parts = monthKey.split("-")
        val year = parts.getOrNull(0)?.toIntOrNull() ?: return monthKey
        val month = parts.getOrNull(1)?.toIntOrNull() ?: return monthKey
        val name = monthNames.getOrNull(month - 1) ?: return monthKey
        return "$name $year"
    }

    fun formatMonthFull(monthKey: String): String {
        val parts = monthKey.split("-")
        val year = parts.getOrNull(0)?.toIntOrNull() ?: return monthKey
        val month = parts.getOrNull(1)?.toIntOrNull() ?: return monthKey
        val name = fullMonthNames.getOrNull(month - 1) ?: return monthKey
        return "$name $year"
    }

    fun formatPaymentDate(epochMs: Long): String {
        if (epochMs <= 0L) return ""
        val cal = Calendar.getInstance().apply { timeInMillis = epochMs }
        val day = cal.get(Calendar.DAY_OF_MONTH)
        val month = monthNames.getOrNull(cal.get(Calendar.MONTH)) ?: ""
        val year = cal.get(Calendar.YEAR)
        return String.format(Locale.US, "%02d %s %04d", day, month, year)
    }

    fun formatPaymentDateShort(epochMs: Long): String {
        if (epochMs <= 0L) return ""
        val cal = Calendar.getInstance().apply { timeInMillis = epochMs }
        val day = cal.get(Calendar.DAY_OF_MONTH)
        val month = monthNames.getOrNull(cal.get(Calendar.MONTH)) ?: ""
        return String.format(Locale.US, "%02d %s", day, month)
    }

    /**
     * Resolves the sub-meter electricity unit rate for [tenant] in [monthKey] strictly forward-only:
     * 1. Already-recorded months always preserve their saved [MonthlyLedger.ratePerUnit].
     * 2. Unrecorded months strictly earlier than [Tenant.rateEffectiveFromMonth] fall back to
     *    the nearest prior ledger's rate or [Tenant.previousElectricityRatePerUnit].
     * 3. Unrecorded months from [Tenant.rateEffectiveFromMonth] onwards use [Tenant.electricityRatePerUnit].
     */
    fun resolveEffectiveElectricityRate(
        tenant: Tenant,
        monthKey: String,
        allLedgers: List<MonthlyLedger>,
        fallbackDefaultRate: Double = 8.0
    ): Double {
        val existing = allLedgers.firstOrNull { it.tenantId == tenant.id && it.monthKey == monthKey }
        if (existing != null) return existing.ratePerUnit

        if (tenant.rateEffectiveFromMonth.isNotBlank() && monthKey < tenant.rateEffectiveFromMonth) {
            val priorLedger = allLedgers
                .filter { it.tenantId == tenant.id && it.monthKey < monthKey }
                .maxByOrNull { it.monthKey }
            if (priorLedger != null) return priorLedger.ratePerUnit
            if (tenant.previousElectricityRatePerUnit > 0.0) return tenant.previousElectricityRatePerUnit
            return fallbackDefaultRate
        }

        return if (tenant.electricityRatePerUnit > 0.0) tenant.electricityRatePerUnit else fallbackDefaultRate
    }

    /**
     * Resolves the carry-forward balance entering [monthKey] for [tenant].
     * Uses the latest recorded MonthlyLedger before [monthKey], or [Tenant.openingBalance].
     * Positive = Due (Baaki), Negative = Advance (Jama).
     */
    fun resolvePreviousBalance(
        tenant: Tenant,
        monthKey: String,
        allLedgers: List<MonthlyLedger>
    ): Double {
        val prior = allLedgers
            .filter { it.tenantId == tenant.id && it.monthKey < monthKey }
            .maxByOrNull { it.monthKey }
        return prior?.closingBalance ?: tenant.openingBalance
    }

    /**
     * Resolves the starting sub-meter reading for [monthKey] for [tenant].
     * Uses the latest recorded MonthlyLedger's closing reading before [monthKey],
     * or [Tenant.initialMeterReading].
     */
    fun resolvePreviousSubReading(
        tenant: Tenant,
        monthKey: String,
        allLedgers: List<MonthlyLedger>
    ): Double {
        val prior = allLedgers
            .filter { it.tenantId == tenant.id && it.monthKey < monthKey }
            .maxByOrNull { it.monthKey }
        return prior?.currReading ?: tenant.initialMeterReading
    }

    fun resolvePreviousMainReading(
        monthKey: String,
        allMainLogs: List<MainMeterLog>
    ): Double {
        val prior = allMainLogs
            .filter { it.monthKey < monthKey }
            .maxByOrNull { it.monthKey }
        return prior?.currReading ?: 0.0
    }

    fun resolveCurrentNetBalance(
        tenant: Tenant,
        allLedgers: List<MonthlyLedger>
    ): Double {
        val latest = allLedgers
            .filter { it.tenantId == tenant.id }
            .maxByOrNull { it.monthKey }
        return latest?.closingBalance ?: tenant.openingBalance
    }

    fun buildTenantMonthSummary(
        tenant: Tenant,
        monthKey: String,
        allLedgers: List<MonthlyLedger>
    ): TenantMonthSummary {
        val existing = allLedgers.firstOrNull { it.tenantId == tenant.id && it.monthKey == monthKey }
        val prevBalance = existing?.previousBalance ?: resolvePreviousBalance(tenant, monthKey, allLedgers)
        val prevReading = existing?.prevReading ?: resolvePreviousSubReading(tenant, monthKey, allLedgers)

        return if (existing != null) {
            val payable = roundToTwoDecimals(
                existing.previousBalance +
                    existing.rentAmount +
                    existing.electricityAmount +
                    existing.maintenanceAmount +
                    existing.extraChargesAmount
            )
            TenantMonthSummary(
                tenant = tenant,
                ledger = existing,
                carriedPreviousBalance = prevBalance,
                suggestedPrevReading = prevReading,
                totalPayable = payable,
                amountPaid = existing.amountPaid,
                closingBalance = existing.closingBalance,
                isRecorded = true
            )
        } else {
            val payable = roundToTwoDecimals(prevBalance + tenant.monthlyRent + tenant.defaultMaintenance)
            TenantMonthSummary(
                tenant = tenant,
                ledger = null,
                carriedPreviousBalance = prevBalance,
                suggestedPrevReading = prevReading,
                totalPayable = payable,
                amountPaid = 0.0,
                closingBalance = payable,
                isRecorded = false
            )
        }
    }

    fun buildMonthlyDashboard(
        summaries: List<TenantMonthSummary>
    ): MonthlyDashboardSummary {
        var expected = 0.0
        var collected = 0.0
        var totalDue = 0.0
        var totalAdvance = 0.0
        var totalUnits = 0.0
        var totalElec = 0.0
        var recorded = 0

        for (item in summaries) {
            val l = item.ledger
            if (l != null) {
                recorded++
                expected += l.rentAmount + l.electricityAmount + l.maintenanceAmount + l.extraChargesAmount + l.previousBalance
                collected += l.amountPaid
                if (item.tenant.tracksSubMeter) {
                    totalUnits += l.unitsConsumed
                    totalElec += l.electricityAmount
                }
                if (l.closingBalance > 0.005) {
                    totalDue += l.closingBalance
                } else if (l.closingBalance < -0.005) {
                    totalAdvance += abs(l.closingBalance)
                }
            } else {
                expected += item.tenant.monthlyRent + item.tenant.defaultMaintenance + item.carriedPreviousBalance
                if (item.closingBalance > 0.005) {
                    totalDue += item.closingBalance
                } else if (item.closingBalance < -0.005) {
                    totalAdvance += abs(item.closingBalance)
                }
            }
        }

        return MonthlyDashboardSummary(
            totalExpected = roundToTwoDecimals(expected),
            totalCollected = roundToTwoDecimals(collected),
            totalDue = roundToTwoDecimals(totalDue),
            totalAdvanceHeld = roundToTwoDecimals(totalAdvance),
            totalSubMeterUnits = roundToTwoDecimals(totalUnits),
            totalElectricityBilled = roundToTwoDecimals(totalElec),
            recordedCount = recorded,
            activeTenantCount = summaries.size
        )
    }

    fun buildReconciliation(
        monthKey: String,
        mainLog: MainMeterLog?,
        allLedgers: List<MonthlyLedger>,
        includedSubMeterTenantIds: Set<String>? = null
    ): MeterReconciliation {
        val monthLedgers = allLedgers.filter {
            it.monthKey == monthKey && (includedSubMeterTenantIds == null || it.tenantId in includedSubMeterTenantIds)
        }
        val subUnitsSum = roundToTwoDecimals(monthLedgers.sumOf { it.unitsConsumed })
        val tenantElecBilled = roundToTwoDecimals(monthLedgers.sumOf { it.electricityAmount })
        val mainUnits = mainLog?.unitsConsumed ?: 0.0
        val govtBill = mainLog?.govtBillAmount ?: 0.0
        val effectiveMainRate = if (mainUnits > 0.0) roundToTwoDecimals(govtBill / mainUnits) else 0.0
        val effectiveSplitRate = if (subUnitsSum > 0.0) roundToTwoDecimals(govtBill / subUnitsSum) else 0.0

        return MeterReconciliation(
            mainUnits = roundToTwoDecimals(mainUnits),
            subMeterUnitsSum = subUnitsSum,
            differenceUnits = roundToTwoDecimals(mainUnits - subUnitsSum),
            govtBillAmount = roundToTwoDecimals(govtBill),
            tenantBilledElectricity = tenantElecBilled,
            effectiveMainRate = effectiveMainRate,
            effectiveSubSplitRate = effectiveSplitRate
        )
    }

    fun buildConsumptionChartData(
        monthKey: String,
        tenants: List<Tenant>,
        mainLog: MainMeterLog?,
        allLedgers: List<MonthlyLedger>,
        propertyIdFilter: String? = null
    ): ReconciliationChartData {
        val subMeterTenants = tenants.filter {
            it.tracksSubMeter && (propertyIdFilter == null || it.propertyId == propertyIdFilter)
        }
        val tenantMap = subMeterTenants.associateBy { it.id }
        val monthLedgers = allLedgers.filter { it.monthKey == monthKey && it.tenantId in tenantMap }
        val subUnitsSum = roundToTwoDecimals(monthLedgers.sumOf { it.unitsConsumed })
        val mainUnits = roundToTwoDecimals(mainLog?.unitsConsumed ?: 0.0)

        val points = monthLedgers
            .map { ledger ->
                val tenant = tenantMap[ledger.tenantId]
                val share = if (subUnitsSum > 0.0) {
                    roundToTwoDecimals((ledger.unitsConsumed / subUnitsSum) * 100.0)
                } else 0.0
                TenantConsumptionPoint(
                    tenantId = ledger.tenantId,
                    tenantName = tenant?.name ?: "Tenant",
                    roomNumber = tenant?.roomNumber ?: "Unit",
                    unitsConsumed = roundToTwoDecimals(ledger.unitsConsumed),
                    electricityAmount = roundToTwoDecimals(ledger.electricityAmount),
                    sharePercent = share
                )
            }
            .sortedByDescending { it.unitsConsumed }

        return ReconciliationChartData(
            mainUnits = mainUnits,
            subMeterUnitsSum = subUnitsSum,
            differenceUnits = roundToTwoDecimals(mainUnits - subUnitsSum),
            tenantSegments = points
        )
    }
}
