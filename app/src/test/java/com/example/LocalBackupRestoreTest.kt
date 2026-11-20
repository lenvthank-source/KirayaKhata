package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.backup.BackupPreferences
import com.example.data.LocalLedgerRepository
import com.example.data.local.KirayaDatabaseHolder
import com.example.model.ElectricityMode
import com.example.model.PaymentMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LocalBackupRestoreTest {

    @Test
    fun `export validate and restore database snapshot preserves tenant and ledger records`() {
        runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = LocalLedgerRepository(context)

        val tenantRes = repo.saveTenant(
            tenantId = "t1",
            name = "Ramesh Sharma",
            roomNumber = "Room 101",
            phone = "9876543210",
            monthlyRent = 8500.0,
            initialMeterReading = 1200.0,
            openingBalance = 0.0,
            active = true
        )
        assertTrue(tenantRes.isSuccess)

        val ledgerRes = repo.saveMonthlyLedger(
            tenantId = "t1",
            monthKey = "2026-11",
            rentAmount = 8500.0,
            prevReading = 1200.0,
            currReading = 1310.0,
            unitsConsumed = 110.0,
            electricityMode = ElectricityMode.FIXED_RATE,
            ratePerUnit = 8.0,
            electricityAmount = 880.0,
            previousBalance = 0.0,
            amountPaid = 9000.0,
            paymentMode = PaymentMode.UPI,
            paymentNote = "GPay",
            closingBalance = 380.0
        )
        assertTrue(ledgerRes.isSuccess)

        val snapshotFile = File(context.cacheDir, "test_backup.db")
        KirayaDatabaseHolder.exportDatabaseSnapshot(context, snapshotFile)
        assertTrue(snapshotFile.exists() && snapshotFile.length() > 100L)

        // Mutate live DB after snapshot
        repo.deleteTenantAndLedgers("t1")
        assertEquals(0, repo.observeTenants().first().size)

        // Verify invalid file is rejected
        val bogusFile = File(context.cacheDir, "bogus.db").apply {
            writeText("not a sqlite database at all, just plain text exceeding 100 bytes................................................")
        }
        try {
            KirayaDatabaseHolder.validateBackupFile(bogusFile)
            fail("Expected invalid backup file to throw")
        } catch (_: IllegalArgumentException) {
            // Expected
        }

        // Restore valid snapshot and confirm data returns
        KirayaDatabaseHolder.replaceDatabaseFromFile(context, snapshotFile)
        val restoredTenants = repo.observeTenants().first()
        val restoredLedgers = repo.observeMonthlyLedgers().first()

        assertEquals(1, restoredTenants.size)
        assertEquals("Ramesh Sharma", restoredTenants.first().name)
        assertEquals(1, restoredLedgers.size)
        assertEquals(380.0, restoredLedgers.first().closingBalance, 0.01)

        snapshotFile.delete()
        bogusFile.delete()
        }
    }

    @Test
    fun `restore v1 sqlite backup auto migrates to v2 with default Main House and preserves data`() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val repo = LocalLedgerRepository(context)
            val v1File = File(context.cacheDir, "legacy_v1_backup.db")
            if (v1File.exists()) v1File.delete()

            val sqlDb = android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(v1File, null)
            try {
                sqlDb.execSQL(
                    "CREATE TABLE tenants (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, roomNumber TEXT NOT NULL, phone TEXT NOT NULL, monthlyRent REAL NOT NULL, initialMeterReading REAL NOT NULL, openingBalance REAL NOT NULL, active INTEGER NOT NULL, createdAtMs INTEGER NOT NULL, updatedAtMs INTEGER NOT NULL)"
                )
                sqlDb.execSQL(
                    "CREATE TABLE monthly_ledgers (id TEXT NOT NULL PRIMARY KEY, tenantId TEXT NOT NULL, monthKey TEXT NOT NULL, rentAmount REAL NOT NULL, prevReading REAL NOT NULL, currReading REAL NOT NULL, unitsConsumed REAL NOT NULL, electricityMode TEXT NOT NULL, ratePerUnit REAL NOT NULL, electricityAmount REAL NOT NULL, previousBalance REAL NOT NULL, amountPaid REAL NOT NULL, paymentMode TEXT NOT NULL, paymentNote TEXT NOT NULL, closingBalance REAL NOT NULL, createdAtMs INTEGER NOT NULL, updatedAtMs INTEGER NOT NULL, FOREIGN KEY(tenantId) REFERENCES tenants(id) ON DELETE CASCADE)"
                )
                sqlDb.execSQL("CREATE INDEX IF NOT EXISTS index_monthly_ledgers_tenantId ON monthly_ledgers(tenantId)")
                sqlDb.execSQL("CREATE INDEX IF NOT EXISTS index_monthly_ledgers_monthKey ON monthly_ledgers(monthKey)")
                sqlDb.execSQL(
                    "CREATE TABLE main_meter_logs (monthKey TEXT NOT NULL PRIMARY KEY, consumerNumber TEXT NOT NULL, prevReading REAL NOT NULL, currReading REAL NOT NULL, unitsConsumed REAL NOT NULL, govtBillAmount REAL NOT NULL, billPaid INTEGER NOT NULL, notes TEXT NOT NULL, createdAtMs INTEGER NOT NULL, updatedAtMs INTEGER NOT NULL)"
                )
                sqlDb.execSQL(
                    "CREATE TABLE billing_settings (id TEXT NOT NULL PRIMARY KEY, defaultRatePerUnit REAL NOT NULL, defaultElectricityMode TEXT NOT NULL, consumerNumber TEXT NOT NULL, updatedAtMs INTEGER NOT NULL)"
                )
                sqlDb.execSQL(
                    "INSERT INTO tenants VALUES ('t_old', 'Mohan', 'Flat 2', '9999999999', 9000.0, 500.0, 0.0, 1, 1000, 1000)"
                )
                sqlDb.execSQL(
                    "INSERT INTO main_meter_logs VALUES ('2026-10', 'CA123', 1000.0, 1250.0, 250.0, 2000.0, 1, 'Oct bill', 1000, 1000)"
                )
                sqlDb.execSQL("PRAGMA user_version = 1")
            } finally {
                sqlDb.close()
            }

            KirayaDatabaseHolder.replaceDatabaseFromFile(context, v1File)
            val props = repo.observeProperties().first()
            val tenants = repo.observeTenants().first()
            val logs = repo.observeMainMeterLogs().first()

            assertTrue(props.any { it.id == com.example.model.DEFAULT_PROPERTY_ID && it.name == "Main House" })
            assertEquals(1, tenants.size)
            assertEquals("Mohan", tenants.first().name)
            assertEquals(com.example.model.DEFAULT_PROPERTY_ID, tenants.first().propertyId)
            assertEquals(com.example.model.MeterTrackingMode.SUB_METER.name, tenants.first().meterTrackingMode)
            assertEquals(1, logs.size)
            assertEquals("2026-10", logs.first().monthKey)
            assertEquals(com.example.model.DEFAULT_PROPERTY_ID, logs.first().propertyId)

            v1File.delete()
        }
    }

    @Test
    fun `multi house preference defaults to single house and toggles via settings`() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val prefs = BackupPreferences(context)

            assertFalse(prefs.multiHouseEnabledFlow.first())
            prefs.setMultiHouseEnabled(true)
            assertTrue(prefs.multiHouseEnabledFlow.first())
            prefs.setMultiHouseEnabled(false)
            assertFalse(prefs.multiHouseEnabledFlow.first())
        }
    }

    @Test
    fun `prepareShareableBackupSnapshot exports valid db via FileProvider and cleans stale snapshots`() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val backupsDir = File(context.cacheDir, "backups").apply { mkdirs() }
            val staleFile = File(backupsDir, "stale_old.db").apply { writeText("old") }
            assertTrue(staleFile.exists())

            val controller = com.example.backup.BackupController(
                appContext = context,
                scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined)
            )
            val result = controller.prepareShareableBackupSnapshot()
            assertTrue("Expected share snapshot to succeed: ${result.exceptionOrNull()}", result.isSuccess)

            val shared = result.getOrThrow()
            assertTrue(shared.fileName.startsWith("KirayaKhata_Backup_"))
            assertTrue(shared.fileName.endsWith(".db"))
            assertTrue(shared.sizeBytes > 100L)
            assertEquals("content", shared.uri.scheme)
            assertFalse("Stale snapshot should be cleaned up", staleFile.exists())

            val createdFile = File(backupsDir, shared.fileName)
            assertTrue(createdFile.exists())
            KirayaDatabaseHolder.validateBackupFile(createdFile)

            val status = BackupPreferences(context).statusFlow.first()
            assertTrue(status.lastBackupTimeMs > 0L)
            assertEquals(shared.sizeBytes, status.lastBackupSizeBytes)

            createdFile.delete()
        }
    }
}
