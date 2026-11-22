package com.example.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.model.DEFAULT_PROPERTY_ID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File

@Database(
    entities = [
        PropertyEntity::class,
        TenantEntity::class,
        MonthlyLedgerEntity::class,
        MainMeterLogEntity::class,
        BillingSettingsEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class KirayaDatabase : RoomDatabase() {
    abstract fun propertyDao(): PropertyDao
    abstract fun tenantDao(): TenantDao
    abstract fun monthlyLedgerDao(): MonthlyLedgerDao
    abstract fun mainMeterLogDao(): MainMeterLogDao
    abstract fun billingSettingsDao(): BillingSettingsDao
}

object KirayaDatabaseHolder {
    const val DB_NAME = "kiraya_khata.db"
    private val REQUIRED_TABLES = setOf(
        "tenants",
        "monthly_ledgers",
        "main_meter_logs",
        "billing_settings"
    )
    private val SQLITE_MAGIC = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)

    private val dbLock = Any()

    @Volatile
    private var instance: KirayaDatabase? = null

    @Volatile
    private var instanceDbPath: String? = null

    private val _dbGeneration = MutableStateFlow(0)
    val dbGeneration: StateFlow<Int> = _dbGeneration.asStateFlow()

    val MIGRATION_1_2: Migration = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            val now = System.currentTimeMillis()
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `properties` (
                    `id` TEXT NOT NULL,
                    `name` TEXT NOT NULL,
                    `address` TEXT NOT NULL,
                    `trackMainMeter` INTEGER NOT NULL,
                    `consumerNumber` TEXT NOT NULL,
                    `discomId` TEXT NOT NULL,
                    `createdAtMs` INTEGER NOT NULL,
                    `updatedAtMs` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT OR IGNORE INTO `properties` (
                    `id`, `name`, `address`, `trackMainMeter`, `consumerNumber`, `discomId`, `createdAtMs`, `updatedAtMs`
                ) VALUES (
                    '$DEFAULT_PROPERTY_ID', 'Main House', '', 1, '', '', $now, $now
                )
                """.trimIndent()
            )
            db.execSQL("ALTER TABLE `tenants` ADD COLUMN `propertyId` TEXT NOT NULL DEFAULT '$DEFAULT_PROPERTY_ID'")
            db.execSQL("ALTER TABLE `tenants` ADD COLUMN `meterTrackingMode` TEXT NOT NULL DEFAULT 'SUB_METER'")
            db.execSQL("ALTER TABLE `tenants` ADD COLUMN `defaultMaintenance` REAL NOT NULL DEFAULT 0.0")

            db.execSQL("ALTER TABLE `monthly_ledgers` ADD COLUMN `maintenanceAmount` REAL NOT NULL DEFAULT 0.0")
            db.execSQL("ALTER TABLE `monthly_ledgers` ADD COLUMN `extraChargesAmount` REAL NOT NULL DEFAULT 0.0")
            db.execSQL("ALTER TABLE `monthly_ledgers` ADD COLUMN `extraChargesNote` TEXT NOT NULL DEFAULT ''")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `main_meter_logs_new` (
                    `id` TEXT NOT NULL,
                    `propertyId` TEXT NOT NULL,
                    `monthKey` TEXT NOT NULL,
                    `consumerNumber` TEXT NOT NULL,
                    `prevReading` REAL NOT NULL,
                    `currReading` REAL NOT NULL,
                    `unitsConsumed` REAL NOT NULL,
                    `govtBillAmount` REAL NOT NULL,
                    `billPaid` INTEGER NOT NULL,
                    `notes` TEXT NOT NULL,
                    `createdAtMs` INTEGER NOT NULL,
                    `updatedAtMs` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO `main_meter_logs_new` (
                    `id`, `propertyId`, `monthKey`, `consumerNumber`, `prevReading`, `currReading`,
                    `unitsConsumed`, `govtBillAmount`, `billPaid`, `notes`, `createdAtMs`, `updatedAtMs`
                )
                SELECT
                    '$DEFAULT_PROPERTY_ID' || '_' || `monthKey`,
                    '$DEFAULT_PROPERTY_ID',
                    `monthKey`, `consumerNumber`, `prevReading`, `currReading`,
                    `unitsConsumed`, `govtBillAmount`, `billPaid`, `notes`, `createdAtMs`, `updatedAtMs`
                FROM `main_meter_logs`
                """.trimIndent()
            )
            db.execSQL("DROP TABLE `main_meter_logs`")
            db.execSQL("ALTER TABLE `main_meter_logs_new` RENAME TO `main_meter_logs`")
        }
    }

    val MIGRATION_2_3: Migration = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `tenants` ADD COLUMN `electricityRatePerUnit` REAL NOT NULL DEFAULT 8.0")
            db.execSQL("ALTER TABLE `tenants` ADD COLUMN `previousElectricityRatePerUnit` REAL NOT NULL DEFAULT 8.0")
            db.execSQL("ALTER TABLE `tenants` ADD COLUMN `rateEffectiveFromMonth` TEXT NOT NULL DEFAULT ''")
            db.execSQL(
                """
                UPDATE `tenants`
                SET `electricityRatePerUnit` = COALESCE((SELECT `defaultRatePerUnit` FROM `billing_settings` WHERE `id` = 'preferences' LIMIT 1), 8.0),
                    `previousElectricityRatePerUnit` = COALESCE((SELECT `defaultRatePerUnit` FROM `billing_settings` WHERE `id` = 'preferences' LIMIT 1), 8.0)
                """.trimIndent()
            )
            db.execSQL("ALTER TABLE `monthly_ledgers` ADD COLUMN `paidDateMs` INTEGER NOT NULL DEFAULT 0")
        }
    }

    private val seedDefaultPropertyCallback = object : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            val now = System.currentTimeMillis()
            db.execSQL(
                """
                INSERT OR IGNORE INTO `properties` (
                    `id`, `name`, `address`, `trackMainMeter`, `consumerNumber`, `discomId`, `createdAtMs`, `updatedAtMs`
                ) VALUES (
                    '$DEFAULT_PROPERTY_ID', 'Main House', '', 1, '', '', $now, $now
                )
                """.trimIndent()
            )
        }
    }

    fun getDatabase(context: Context): KirayaDatabase {
        val appContext = context.applicationContext
        val targetPath = appContext.getDatabasePath(DB_NAME).absolutePath
        val existing = instance
        if (existing != null && instanceDbPath == targetPath) return existing
        return synchronized(dbLock) {
            val current = instance
            if (current != null && instanceDbPath == targetPath) {
                current
            } else {
                current?.close()
                Room.databaseBuilder(
                    appContext,
                    KirayaDatabase::class.java,
                    DB_NAME
                )
                    .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .addCallback(seedDefaultPropertyCallback)
                    .build()
                    .also {
                        instance = it
                        instanceDbPath = targetPath
                    }
            }
        }
    }

    fun exportDatabaseSnapshot(context: Context, destinationFile: File): File {
        val appContext = context.applicationContext
        synchronized(dbLock) {
            val db = getDatabase(appContext)
            db.openHelper.writableDatabase
            instance?.close()
            instance = null
            instanceDbPath = null

            val dbFile = appContext.getDatabasePath(DB_NAME)
            require(dbFile.exists() && dbFile.length() > 0L) {
                "Local database file is not initialized yet."
            }
            destinationFile.parentFile?.mkdirs()
            dbFile.copyTo(destinationFile, overwrite = true)
            _dbGeneration.update { it + 1 }
            return destinationFile
        }
    }

    fun replaceDatabaseFromFile(context: Context, candidateFile: File) {
        validateBackupFile(candidateFile)
        val appContext = context.applicationContext
        synchronized(dbLock) {
            instance?.close()
            instance = null
            instanceDbPath = null

            val targetDb = appContext.getDatabasePath(DB_NAME)
            targetDb.parentFile?.mkdirs()

            listOf("-wal", "-shm", "-journal").forEach { suffix ->
                val sidecar = File("${targetDb.absolutePath}$suffix")
                if (sidecar.exists()) sidecar.delete()
            }

            candidateFile.copyTo(targetDb, overwrite = true)
            _dbGeneration.update { it + 1 }
        }
    }

    fun validateBackupFile(file: File) {
        require(file.exists() && file.length() >= 100L) {
            "Selected file is empty or unreadable."
        }
        val header = ByteArray(16)
        file.inputStream().use { stream ->
            val bytesRead = stream.read(header)
            require(bytesRead == 16 && header.contentEquals(SQLITE_MAGIC)) {
                "Selected file is not a valid SQLite (.db) backup."
            }
        }
        val checkDb = try {
            SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        } catch (e: Exception) {
            throw IllegalArgumentException("Could not open backup database: ${e.localizedMessage}", e)
        }
        try {
            val foundTables = mutableSetOf<String>()
            checkDb.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table'",
                null
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    foundTables += cursor.getString(0)
                }
            }
            require(foundTables.containsAll(REQUIRED_TABLES)) {
                "This SQLite file does not match KirayaKhata's database format."
            }
        } finally {
            checkDb.close()
        }
    }
}
