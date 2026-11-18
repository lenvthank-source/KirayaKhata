package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PropertyDao {
    @Query("SELECT * FROM properties ORDER BY createdAtMs ASC, LOWER(name) ASC")
    fun observeAll(): Flow<List<PropertyEntity>>

    @Query("SELECT * FROM properties WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): PropertyEntity?

    @Query("SELECT COUNT(*) FROM properties")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PropertyEntity)

    @Query("UPDATE properties SET trackMainMeter = :trackMainMeter, updatedAtMs = :updatedAtMs WHERE id = :id")
    suspend fun setTrackMainMeter(id: String, trackMainMeter: Boolean, updatedAtMs: Long)

    @Query("UPDATE properties SET discomId = :discomId, updatedAtMs = :updatedAtMs WHERE id = :id")
    suspend fun setDiscomId(id: String, discomId: String, updatedAtMs: Long)

    @Query("DELETE FROM properties WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface TenantDao {
    @Query("SELECT * FROM tenants ORDER BY active DESC, LOWER(roomNumber) ASC, LOWER(name) ASC")
    fun observeAll(): Flow<List<TenantEntity>>

    @Query("SELECT * FROM tenants WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): TenantEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: TenantEntity)

    @Query("UPDATE tenants SET active = :active, updatedAtMs = :updatedAtMs WHERE id = :id")
    suspend fun setActive(id: String, active: Boolean, updatedAtMs: Long)

    @Query("UPDATE tenants SET propertyId = :targetPropertyId, updatedAtMs = :updatedAtMs WHERE propertyId = :oldPropertyId")
    suspend fun reassignProperty(oldPropertyId: String, targetPropertyId: String, updatedAtMs: Long)

    @Query("DELETE FROM tenants WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface MonthlyLedgerDao {
    @Query("SELECT * FROM monthly_ledgers")
    fun observeAll(): Flow<List<MonthlyLedgerEntity>>

    @Query("SELECT * FROM monthly_ledgers WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): MonthlyLedgerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: MonthlyLedgerEntity)

    @Query("DELETE FROM monthly_ledgers WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface MainMeterLogDao {
    @Query("SELECT * FROM main_meter_logs ORDER BY monthKey DESC")
    fun observeAll(): Flow<List<MainMeterLogEntity>>

    @Query("SELECT * FROM main_meter_logs WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): MainMeterLogEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: MainMeterLogEntity)

    @Query("DELETE FROM main_meter_logs WHERE propertyId = :propertyId")
    suspend fun deleteByPropertyId(propertyId: String)
}

@Dao
interface BillingSettingsDao {
    @Query("SELECT * FROM billing_settings WHERE id = 'preferences' LIMIT 1")
    fun observe(): Flow<BillingSettingsEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: BillingSettingsEntity)
}
