package com.example.model

const val DEFAULT_PROPERTY_ID = "default_house"

enum class ElectricityMode(val label: String) {
    FIXED_RATE("Fixed ₹/kWh"),
    MAIN_BILL_SPLIT("Main Bill Split");

    companion object {
        fun fromRaw(raw: String?): ElectricityMode =
            entries.firstOrNull { it.name == raw } ?: FIXED_RATE
    }
}

enum class MeterTrackingMode(val label: String, val shortLabel: String) {
    SUB_METER("Sub-Meter (Landlord Bills)", "Sub-Meter"),
    DIRECT_GOVT_METER("Direct Govt Meter (Tenant Pays)", "Direct Govt");

    companion object {
        fun fromRaw(raw: String?): MeterTrackingMode =
            entries.firstOrNull { it.name == raw } ?: SUB_METER
    }
}

enum class PaymentMode(val label: String) {
    UPI("UPI"),
    CASH("Cash"),
    BANK("Bank");

    companion object {
        fun fromRaw(raw: String?): PaymentMode =
            entries.firstOrNull { it.name == raw } ?: UPI
    }
}

enum class LedgerFilter(val label: String) {
    ALL("All"),
    DUE("Due"),
    SETTLED("Settled"),
    ADVANCE("Advance"),
    PENDING_ENTRY("Unrecorded")
}

data class Property(
    val id: String = DEFAULT_PROPERTY_ID,
    val name: String = "Main House",
    val address: String = "",
    val trackMainMeter: Boolean = true,
    val consumerNumber: String = "",
    val discomId: String = "",
    val createdAtMs: Long = 0L,
    val updatedAtMs: Long = 0L
)

data class Tenant(
    val id: String = "",
    val propertyId: String = DEFAULT_PROPERTY_ID,
    val name: String = "",
    val roomNumber: String = "",
    val phone: String = "",
    val monthlyRent: Double = 0.0,
    val defaultMaintenance: Double = 0.0,
    val meterTrackingMode: String = MeterTrackingMode.SUB_METER.name,
    val initialMeterReading: Double = 0.0,
    val openingBalance: Double = 0.0,
    val active: Boolean = true,
    val createdAtMs: Long = 0L,
    val updatedAtMs: Long = 0L
) {
    val resolvedMeterMode: MeterTrackingMode
        get() = MeterTrackingMode.fromRaw(meterTrackingMode)

    val tracksSubMeter: Boolean
        get() = resolvedMeterMode == MeterTrackingMode.SUB_METER
}

data class MonthlyLedger(
    val id: String = "",
    val tenantId: String = "",
    val monthKey: String = "",
    val rentAmount: Double = 0.0,
    val prevReading: Double = 0.0,
    val currReading: Double = 0.0,
    val unitsConsumed: Double = 0.0,
    val electricityMode: String = ElectricityMode.FIXED_RATE.name,
    val ratePerUnit: Double = 0.0,
    val electricityAmount: Double = 0.0,
    val maintenanceAmount: Double = 0.0,
    val extraChargesAmount: Double = 0.0,
    val extraChargesNote: String = "",
    val previousBalance: Double = 0.0,
    val amountPaid: Double = 0.0,
    val paymentMode: String = PaymentMode.UPI.name,
    val paymentNote: String = "",
    val closingBalance: Double = 0.0,
    val createdAtMs: Long = 0L,
    val updatedAtMs: Long = 0L
) {
    val resolvedElectricityMode: ElectricityMode
        get() = ElectricityMode.fromRaw(electricityMode)

    val resolvedPaymentMode: PaymentMode
        get() = PaymentMode.fromRaw(paymentMode)
}

data class MainMeterLog(
    val id: String = "",
    val propertyId: String = DEFAULT_PROPERTY_ID,
    val monthKey: String = "",
    val consumerNumber: String = "",
    val prevReading: Double = 0.0,
    val currReading: Double = 0.0,
    val unitsConsumed: Double = 0.0,
    val govtBillAmount: Double = 0.0,
    val billPaid: Boolean = false,
    val notes: String = "",
    val createdAtMs: Long = 0L,
    val updatedAtMs: Long = 0L
)

data class BillingSettings(
    val id: String = "preferences",
    val defaultRatePerUnit: Double = 8.0,
    val defaultElectricityMode: String = ElectricityMode.FIXED_RATE.name,
    val consumerNumber: String = "",
    val updatedAtMs: Long = 0L
) {
    val resolvedDefaultMode: ElectricityMode
        get() = ElectricityMode.fromRaw(defaultElectricityMode)
}

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}
