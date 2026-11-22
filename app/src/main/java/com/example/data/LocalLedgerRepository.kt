package com.example.data

import android.content.Context
import com.example.data.local.BillingSettingsEntity
import com.example.data.local.KirayaDatabase
import com.example.data.local.KirayaDatabaseHolder
import com.example.data.local.MainMeterLogEntity
import com.example.data.local.MonthlyLedgerEntity
import com.example.data.local.PropertyEntity
import com.example.data.local.TenantEntity
import com.example.domain.BillingCalculator
import com.example.model.BillingSettings
import com.example.model.DEFAULT_PROPERTY_ID
import com.example.model.ElectricityMode
import com.example.model.MainMeterLog
import com.example.model.MeterTrackingMode
import com.example.model.MonthlyLedger
import com.example.model.PaymentMode
import com.example.model.Property
import com.example.model.Tenant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import java.util.UUID
import kotlin.math.abs

@OptIn(ExperimentalCoroutinesApi::class)
class LocalLedgerRepository(private val appContext: Context) {

    private fun db(): KirayaDatabase = KirayaDatabaseHolder.getDatabase(appContext)

    fun observeProperties(): Flow<List<Property>> =
        KirayaDatabaseHolder.dbGeneration.flatMapLatest {
            db().propertyDao().observeAll().map { entities ->
                if (entities.isEmpty()) {
                    listOf(Property(id = DEFAULT_PROPERTY_ID, name = "Main House", trackMainMeter = true))
                } else {
                    entities.map { it.toModel() }
                }
            }
        }

    fun observeTenants(): Flow<List<Tenant>> =
        KirayaDatabaseHolder.dbGeneration.flatMapLatest {
            db().tenantDao().observeAll().map { entities ->
                entities.map { it.toModel() }
            }
        }

    fun observeMonthlyLedgers(): Flow<List<MonthlyLedger>> =
        KirayaDatabaseHolder.dbGeneration.flatMapLatest {
            db().monthlyLedgerDao().observeAll().map { entities ->
                entities.map { it.toModel() }
            }
        }

    fun observeMainMeterLogs(): Flow<List<MainMeterLog>> =
        KirayaDatabaseHolder.dbGeneration.flatMapLatest {
            db().mainMeterLogDao().observeAll().map { entities ->
                entities.map { it.toModel() }
            }
        }

    fun observeBillingSettings(): Flow<BillingSettings> =
        KirayaDatabaseHolder.dbGeneration.flatMapLatest {
            db().billingSettingsDao().observe().map { entity ->
                entity?.toModel() ?: BillingSettings()
            }
        }

    suspend fun saveProperty(
        propertyId: String?,
        name: String,
        address: String = "",
        trackMainMeter: Boolean = true,
        consumerNumber: String = "",
        discomId: String = ""
    ): Result<String> {
        val trimmedName = name.trim()
        val trimmedAddr = address.trim()
        val trimmedConsumer = consumerNumber.trim()
        return try {
            require(trimmedName.length in 1..60) { "House / property name must be 1 to 60 characters." }
            require(trimmedAddr.length <= 120) { "Address must be at most 120 characters." }
            require(trimmedConsumer.length <= 40) { "Consumer number must be at most 40 characters." }

            val now = System.currentTimeMillis()
            val dao = db().propertyDao()
            val existing = if (!propertyId.isNullOrBlank()) dao.getById(propertyId) else null
            val id = existing?.id ?: propertyId?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
            dao.upsert(
                PropertyEntity(
                    id = id,
                    name = trimmedName,
                    address = trimmedAddr,
                    trackMainMeter = trackMainMeter,
                    consumerNumber = trimmedConsumer,
                    discomId = discomId.ifBlank { existing?.discomId ?: "" },
                    createdAtMs = existing?.createdAtMs ?: now,
                    updatedAtMs = now
                )
            )
            Result.success(id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun setPropertyTrackMainMeter(propertyId: String, trackMainMeter: Boolean): Result<Unit> =
        try {
            val dao = db().propertyDao()
            val existing = dao.getById(propertyId)
            val now = System.currentTimeMillis()
            if (existing == null && propertyId == DEFAULT_PROPERTY_ID) {
                dao.upsert(
                    PropertyEntity(
                        id = DEFAULT_PROPERTY_ID,
                        name = "Main House",
                        address = "",
                        trackMainMeter = trackMainMeter,
                        consumerNumber = "",
                        discomId = "",
                        createdAtMs = now,
                        updatedAtMs = now
                    )
                )
            } else {
                dao.setTrackMainMeter(propertyId, trackMainMeter, now)
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }

    suspend fun setPropertyDiscomId(propertyId: String, discomId: String): Result<Unit> =
        try {
            val dao = db().propertyDao()
            val existing = dao.getById(propertyId)
            val now = System.currentTimeMillis()
            if (existing == null && propertyId == DEFAULT_PROPERTY_ID) {
                dao.upsert(
                    PropertyEntity(
                        id = DEFAULT_PROPERTY_ID,
                        name = "Main House",
                        address = "",
                        trackMainMeter = true,
                        consumerNumber = "",
                        discomId = discomId,
                        createdAtMs = now,
                        updatedAtMs = now
                    )
                )
            } else {
                dao.setDiscomId(propertyId, discomId, now)
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }

    suspend fun deletePropertyAndReassign(propertyId: String, fallbackPropertyId: String): Result<Unit> =
        try {
            require(propertyId != fallbackPropertyId) { "Cannot delete the only house." }
            val now = System.currentTimeMillis()
            db().tenantDao().reassignProperty(propertyId, fallbackPropertyId, now)
            db().mainMeterLogDao().deleteByPropertyId(propertyId)
            db().propertyDao().deleteById(propertyId)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }

    suspend fun saveTenant(
        tenantId: String?,
        propertyId: String = DEFAULT_PROPERTY_ID,
        name: String,
        roomNumber: String,
        phone: String,
        monthlyRent: Double,
        defaultMaintenance: Double = 0.0,
        meterTrackingMode: MeterTrackingMode = MeterTrackingMode.SUB_METER,
        initialMeterReading: Double,
        electricityRatePerUnit: Double = 8.0,
        rateEffectiveMonth: String = "",
        openingBalance: Double,
        active: Boolean
    ): Result<Unit> {
        val trimmedName = name.trim()
        val trimmedRoom = roomNumber.trim()
        val trimmedPhone = phone.trim()
        val resolvedPropertyId = propertyId.ifBlank { DEFAULT_PROPERTY_ID }
        return try {
            require(trimmedName.length in 1..80) { "Tenant name must be 1 to 80 characters." }
            require(trimmedRoom.length in 1..40) { "Room or unit label must be 1 to 40 characters." }
            require(trimmedPhone.length <= 20) { "Phone number must be at most 20 characters." }
            require(monthlyRent in 0.0..10_000_000.0) { "Monthly rent must be between ₹0 and ₹1,00,00,000." }
            require(defaultMaintenance in 0.0..1_000_000.0) { "Maintenance must be between ₹0 and ₹10,00,000." }
            require(initialMeterReading in 0.0..9_999_999.0) { "Initial meter reading is out of valid range." }
            require(electricityRatePerUnit in 0.0..1000.0) { "Electricity rate must be between ₹0 and ₹1,000/unit." }
            require(openingBalance in -10_000_000.0..10_000_000.0) { "Opening balance is out of valid range." }

            val now = System.currentTimeMillis()
            val dao = db().tenantDao()
            val existing = if (!tenantId.isNullOrBlank()) dao.getById(tenantId) else null
            val id = existing?.id ?: tenantId?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
            val roundedRate = BillingCalculator.roundToTwoDecimals(electricityRatePerUnit)
            val rateChanged = existing != null && abs(existing.electricityRatePerUnit - roundedRate) > 0.0001
            val prevRate = when {
                existing == null -> roundedRate
                rateChanged -> existing.electricityRatePerUnit
                else -> existing.previousElectricityRatePerUnit
            }
            val effectiveFromMonth = when {
                existing == null -> ""
                rateChanged -> rateEffectiveMonth.ifBlank { BillingCalculator.currentMonthKey() }
                else -> existing.rateEffectiveFromMonth
            }
            dao.upsert(
                TenantEntity(
                    id = id,
                    propertyId = resolvedPropertyId,
                    name = trimmedName,
                    roomNumber = trimmedRoom,
                    phone = trimmedPhone,
                    monthlyRent = monthlyRent,
                    defaultMaintenance = defaultMaintenance,
                    meterTrackingMode = meterTrackingMode.name,
                    initialMeterReading = initialMeterReading,
                    electricityRatePerUnit = roundedRate,
                    previousElectricityRatePerUnit = prevRate,
                    rateEffectiveFromMonth = effectiveFromMonth,
                    openingBalance = openingBalance,
                    active = active,
                    createdAtMs = existing?.createdAtMs ?: now,
                    updatedAtMs = now
                )
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun setTenantActive(tenantId: String, active: Boolean): Result<Unit> =
        try {
            require(tenantId.isNotBlank()) { "Tenant ID must not be blank." }
            db().tenantDao().setActive(tenantId, active, System.currentTimeMillis())
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }

    suspend fun deleteTenantAndLedgers(tenantId: String): Result<Unit> =
        try {
            require(tenantId.isNotBlank()) { "Tenant ID must not be blank." }
            db().tenantDao().deleteById(tenantId)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }

    suspend fun saveMonthlyLedger(
        tenantId: String,
        monthKey: String,
        rentAmount: Double,
        prevReading: Double,
        currReading: Double,
        unitsConsumed: Double,
        electricityMode: ElectricityMode,
        ratePerUnit: Double,
        electricityAmount: Double,
        maintenanceAmount: Double = 0.0,
        extraChargesAmount: Double = 0.0,
        extraChargesNote: String = "",
        previousBalance: Double,
        amountPaid: Double,
        paidDateMs: Long = 0L,
        paymentMode: PaymentMode,
        paymentNote: String,
        closingBalance: Double
    ): Result<Unit> {
        val ledgerId = "${tenantId}_${monthKey}"
        val trimmedNote = paymentNote.trim()
        val trimmedExtraNote = extraChargesNote.trim()
        val resolvedPaidDateMs = if (amountPaid > 0.005) paidDateMs.coerceAtLeast(0L) else 0L
        return try {
            require(tenantId.isNotBlank()) { "Tenant ID must not be blank." }
            require(monthKey.matches(Regex("^[0-9]{4}-(0[1-9]|1[0-2])$"))) { "Invalid month format." }
            require(rentAmount in 0.0..10_000_000.0) { "Rent must be between ₹0 and ₹1,00,00,000." }
            require(prevReading in 0.0..9_999_999.0) { "Previous reading out of valid range." }
            require(currReading in prevReading..9_999_999.0) { "Current reading cannot be less than previous reading." }
            require(unitsConsumed in 0.0..9_999_999.0) { "Units consumed out of valid range." }
            require(ratePerUnit in 0.0..1000.0) { "Rate per unit must be between ₹0 and ₹1,000." }
            require(electricityAmount in 0.0..10_000_000.0) { "Electricity charge out of valid range." }
            require(maintenanceAmount in 0.0..1_000_000.0) { "Maintenance charge out of valid range." }
            require(extraChargesAmount in 0.0..1_000_000.0) { "Extra charges out of valid range." }
            require(trimmedExtraNote.length <= 120) { "Extra charges note must be at most 120 characters." }
            require(amountPaid in 0.0..20_000_000.0) { "Amount paid must be between ₹0 and ₹2,00,00,000." }
            require(trimmedNote.length <= 200) { "Payment note must be at most 200 characters." }

            val now = System.currentTimeMillis()
            val dao = db().monthlyLedgerDao()
            val existing = dao.getById(ledgerId)
            dao.upsert(
                MonthlyLedgerEntity(
                    id = ledgerId,
                    tenantId = tenantId,
                    monthKey = monthKey,
                    rentAmount = rentAmount,
                    prevReading = prevReading,
                    currReading = currReading,
                    unitsConsumed = unitsConsumed,
                    electricityMode = electricityMode.name,
                    ratePerUnit = ratePerUnit,
                    electricityAmount = electricityAmount,
                    maintenanceAmount = maintenanceAmount,
                    extraChargesAmount = extraChargesAmount,
                    extraChargesNote = trimmedExtraNote,
                    previousBalance = previousBalance,
                    amountPaid = amountPaid,
                    paidDateMs = resolvedPaidDateMs,
                    paymentMode = paymentMode.name,
                    paymentNote = trimmedNote,
                    closingBalance = closingBalance,
                    createdAtMs = existing?.createdAtMs ?: now,
                    updatedAtMs = now
                )
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteMonthlyLedger(ledgerId: String): Result<Unit> =
        try {
            require(ledgerId.isNotBlank()) { "Ledger ID must not be blank." }
            db().monthlyLedgerDao().deleteById(ledgerId)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }

    suspend fun saveMainMeterLog(
        propertyId: String = DEFAULT_PROPERTY_ID,
        monthKey: String,
        consumerNumber: String,
        prevReading: Double,
        currReading: Double,
        unitsConsumed: Double,
        govtBillAmount: Double,
        billPaid: Boolean,
        notes: String
    ): Result<Unit> {
        val resolvedProp = propertyId.ifBlank { DEFAULT_PROPERTY_ID }
        val logId = "${resolvedProp}_${monthKey}"
        val trimmedConsumer = consumerNumber.trim()
        val trimmedNotes = notes.trim()
        return try {
            require(monthKey.matches(Regex("^[0-9]{4}-(0[1-9]|1[0-2])$"))) { "Invalid month format." }
            require(trimmedConsumer.length <= 40) { "Consumer number must be at most 40 characters." }
            require(prevReading in 0.0..99_999_999.0) { "Previous main reading out of range." }
            require(currReading in prevReading..99_999_999.0) { "Current main reading cannot be less than previous reading." }
            require(unitsConsumed in 0.0..99_999_999.0) { "Main units consumed out of range." }
            require(govtBillAmount in 0.0..10_000_000.0) { "Govt bill amount out of range." }
            require(trimmedNotes.length <= 300) { "Notes must be at most 300 characters." }

            val now = System.currentTimeMillis()
            val dao = db().mainMeterLogDao()
            val existing = dao.getById(logId)
            dao.upsert(
                MainMeterLogEntity(
                    id = logId,
                    propertyId = resolvedProp,
                    monthKey = monthKey,
                    consumerNumber = trimmedConsumer,
                    prevReading = prevReading,
                    currReading = currReading,
                    unitsConsumed = unitsConsumed,
                    govtBillAmount = govtBillAmount,
                    billPaid = billPaid,
                    notes = trimmedNotes,
                    createdAtMs = existing?.createdAtMs ?: now,
                    updatedAtMs = now
                )
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveBillingSettings(
        defaultRatePerUnit: Double,
        defaultElectricityMode: ElectricityMode,
        consumerNumber: String
    ): Result<Unit> {
        val trimmedConsumer = consumerNumber.trim()
        return try {
            require(defaultRatePerUnit in 0.0..1000.0) { "Default rate must be between ₹0 and ₹1,000/unit." }
            require(trimmedConsumer.length <= 40) { "Consumer number must be at most 40 characters." }
            db().billingSettingsDao().upsert(
                BillingSettingsEntity(
                    id = "preferences",
                    defaultRatePerUnit = defaultRatePerUnit,
                    defaultElectricityMode = defaultElectricityMode.name,
                    consumerNumber = trimmedConsumer,
                    updatedAtMs = System.currentTimeMillis()
                )
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun PropertyEntity.toModel() = Property(
        id = id,
        name = name,
        address = address,
        trackMainMeter = trackMainMeter,
        consumerNumber = consumerNumber,
        discomId = discomId,
        createdAtMs = createdAtMs,
        updatedAtMs = updatedAtMs
    )

    private fun TenantEntity.toModel() = Tenant(
        id = id,
        propertyId = propertyId,
        name = name,
        roomNumber = roomNumber,
        phone = phone,
        monthlyRent = monthlyRent,
        defaultMaintenance = defaultMaintenance,
        meterTrackingMode = meterTrackingMode,
        initialMeterReading = initialMeterReading,
        electricityRatePerUnit = electricityRatePerUnit,
        previousElectricityRatePerUnit = previousElectricityRatePerUnit,
        rateEffectiveFromMonth = rateEffectiveFromMonth,
        openingBalance = openingBalance,
        active = active,
        createdAtMs = createdAtMs,
        updatedAtMs = updatedAtMs
    )

    private fun MonthlyLedgerEntity.toModel() = MonthlyLedger(
        id = id,
        tenantId = tenantId,
        monthKey = monthKey,
        rentAmount = rentAmount,
        prevReading = prevReading,
        currReading = currReading,
        unitsConsumed = unitsConsumed,
        electricityMode = electricityMode,
        ratePerUnit = ratePerUnit,
        electricityAmount = electricityAmount,
        maintenanceAmount = maintenanceAmount,
        extraChargesAmount = extraChargesAmount,
        extraChargesNote = extraChargesNote,
        previousBalance = previousBalance,
        amountPaid = amountPaid,
        paidDateMs = paidDateMs,
        paymentMode = paymentMode,
        paymentNote = paymentNote,
        closingBalance = closingBalance,
        createdAtMs = createdAtMs,
        updatedAtMs = updatedAtMs
    )

    private fun MainMeterLogEntity.toModel() = MainMeterLog(
        id = id,
        propertyId = propertyId,
        monthKey = monthKey,
        consumerNumber = consumerNumber,
        prevReading = prevReading,
        currReading = currReading,
        unitsConsumed = unitsConsumed,
        govtBillAmount = govtBillAmount,
        billPaid = billPaid,
        notes = notes,
        createdAtMs = createdAtMs,
        updatedAtMs = updatedAtMs
    )

    private fun BillingSettingsEntity.toModel() = BillingSettings(
        id = id,
        defaultRatePerUnit = defaultRatePerUnit,
        defaultElectricityMode = defaultElectricityMode,
        consumerNumber = consumerNumber,
        updatedAtMs = updatedAtMs
    )
}
