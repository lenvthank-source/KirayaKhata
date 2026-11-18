package com.example.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "properties")
data class PropertyEntity(
    @PrimaryKey val id: String,
    val name: String,
    val address: String,
    val trackMainMeter: Boolean,
    val consumerNumber: String,
    val discomId: String,
    val createdAtMs: Long,
    val updatedAtMs: Long
)

@Entity(tableName = "tenants")
data class TenantEntity(
    @PrimaryKey val id: String,
    val propertyId: String,
    val name: String,
    val roomNumber: String,
    val phone: String,
    val monthlyRent: Double,
    val defaultMaintenance: Double,
    val meterTrackingMode: String,
    val initialMeterReading: Double,
    val openingBalance: Double,
    val active: Boolean,
    val createdAtMs: Long,
    val updatedAtMs: Long
)

@Entity(
    tableName = "monthly_ledgers",
    foreignKeys = [
        ForeignKey(
            entity = TenantEntity::class,
            parentColumns = ["id"],
            childColumns = ["tenantId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("tenantId"), Index("monthKey")]
)
data class MonthlyLedgerEntity(
    @PrimaryKey val id: String,
    val tenantId: String,
    val monthKey: String,
    val rentAmount: Double,
    val prevReading: Double,
    val currReading: Double,
    val unitsConsumed: Double,
    val electricityMode: String,
    val ratePerUnit: Double,
    val electricityAmount: Double,
    val maintenanceAmount: Double,
    val extraChargesAmount: Double,
    val extraChargesNote: String,
    val previousBalance: Double,
    val amountPaid: Double,
    val paymentMode: String,
    val paymentNote: String,
    val closingBalance: Double,
    val createdAtMs: Long,
    val updatedAtMs: Long
)

@Entity(tableName = "main_meter_logs")
data class MainMeterLogEntity(
    @PrimaryKey val id: String,
    val propertyId: String,
    val monthKey: String,
    val consumerNumber: String,
    val prevReading: Double,
    val currReading: Double,
    val unitsConsumed: Double,
    val govtBillAmount: Double,
    val billPaid: Boolean,
    val notes: String,
    val createdAtMs: Long,
    val updatedAtMs: Long
)

@Entity(tableName = "billing_settings")
data class BillingSettingsEntity(
    @PrimaryKey val id: String = "preferences",
    val defaultRatePerUnit: Double,
    val defaultElectricityMode: String,
    val consumerNumber: String,
    val updatedAtMs: Long
)
