package com.example

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.example.data.LocalLedgerRepository
import com.example.data.local.KirayaDatabaseHolder
import com.example.domain.BillingCalculator
import com.example.export.TenantStatementPdfGenerator
import com.example.model.DEFAULT_PROPERTY_ID
import com.example.model.ElectricityMode
import com.example.model.MeterTrackingMode
import com.example.model.MonthlyLedger
import com.example.model.PaymentMode
import com.example.model.Tenant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TenantFeaturesTest {

    @org.junit.Before
    fun clearFileProviderCache() {
        val cacheField = androidx.core.content.FileProvider::class.java.getDeclaredField("sCache")
        cacheField.isAccessible = true
        (cacheField.get(null) as? MutableMap<*, *>)?.clear()
    }

    @Test
    fun `changing tenant sub meter rate applies strictly forward from effective month`() {
        runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = LocalLedgerRepository(context)

        assertTrue(
            repo.saveTenant(
                tenantId = "t_rate",
                name = "Anil Verma",
                roomNumber = "Room 204",
                phone = "9811122233",
                monthlyRent = 10000.0,
                meterTrackingMode = MeterTrackingMode.SUB_METER,
                initialMeterReading = 500.0,
                electricityRatePerUnit = 9.5,
                rateEffectiveMonth = "2026-10",
                openingBalance = 0.0,
                active = true
            ).isSuccess
        )

        // Record October at initial ₹9.5/unit
        assertTrue(
            repo.saveMonthlyLedger(
                tenantId = "t_rate",
                monthKey = "2026-10",
                rentAmount = 10000.0,
                prevReading = 500.0,
                currReading = 600.0,
                unitsConsumed = 100.0,
                electricityMode = ElectricityMode.FIXED_RATE,
                ratePerUnit = 9.5,
                electricityAmount = 950.0,
                previousBalance = 0.0,
                amountPaid = 10950.0,
                paidDateMs = 1_791_500_000_000L,
                paymentMode = PaymentMode.UPI,
                paymentNote = "Oct rent",
                closingBalance = 0.0
            ).isSuccess
        )

        // Increase rate to ₹11.0/unit starting December 2026
        assertTrue(
            repo.saveTenant(
                tenantId = "t_rate",
                name = "Anil Verma",
                roomNumber = "Room 204",
                phone = "9811122233",
                monthlyRent = 10000.0,
                meterTrackingMode = MeterTrackingMode.SUB_METER,
                initialMeterReading = 500.0,
                electricityRatePerUnit = 11.0,
                rateEffectiveMonth = "2026-12",
                openingBalance = 0.0,
                active = true
            ).isSuccess
        )

        val tenantAfterChange = repo.observeTenants().first().first { it.id == "t_rate" }
        val ledgers = repo.observeMonthlyLedgers().first()

        assertEquals(11.0, tenantAfterChange.electricityRatePerUnit, 0.001)
        assertEquals(9.5, tenantAfterChange.previousElectricityRatePerUnit, 0.001)
        assertEquals("2026-12", tenantAfterChange.rateEffectiveFromMonth)

        // Past recorded month keeps ₹9.5
        assertEquals(
            9.5,
            BillingCalculator.resolveEffectiveElectricityRate(tenantAfterChange, "2026-10", ledgers, 8.0),
            0.001
        )
        // Unrecorded earlier month (before effective month) uses previous rate ₹9.5, NOT ₹11.0
        assertEquals(
            9.5,
            BillingCalculator.resolveEffectiveElectricityRate(tenantAfterChange, "2026-11", ledgers, 8.0),
            0.001
        )
        // Effective month and future months use new rate ₹11.0
        assertEquals(
            11.0,
            BillingCalculator.resolveEffectiveElectricityRate(tenantAfterChange, "2026-12", ledgers, 8.0),
            0.001
        )
        assertEquals(
            11.0,
            BillingCalculator.resolveEffectiveElectricityRate(tenantAfterChange, "2027-01", ledgers, 8.0),
            0.001
        )

        // Updating another field without changing rate preserves rateEffectiveFromMonth & previous rate
        assertTrue(
            repo.saveTenant(
                tenantId = "t_rate",
                name = "Anil Verma",
                roomNumber = "Room 204",
                phone = "9999988888",
                monthlyRent = 10500.0,
                meterTrackingMode = MeterTrackingMode.SUB_METER,
                initialMeterReading = 500.0,
                electricityRatePerUnit = 11.0,
                rateEffectiveMonth = "2027-02",
                openingBalance = 0.0,
                active = true
            ).isSuccess
        )
        val tenantAfterRentEdit = repo.observeTenants().first().first { it.id == "t_rate" }
        assertEquals("2026-12", tenantAfterRentEdit.rateEffectiveFromMonth)
        assertEquals(9.5, tenantAfterRentEdit.previousElectricityRatePerUnit, 0.001)
        }
    }

    @Test
    fun `payment date persists when paid and resets to zero when amount paid is zero`() {
        runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = LocalLedgerRepository(context)

        assertTrue(
            repo.saveTenant(
                tenantId = "t_paid_date",
                name = "Priya Nair",
                roomNumber = "Flat 3A",
                phone = "9876500001",
                monthlyRent = 12000.0,
                initialMeterReading = 100.0,
                electricityRatePerUnit = 8.5,
                openingBalance = 0.0,
                active = true
            ).isSuccess
        )

        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, 2026)
            set(Calendar.MONTH, Calendar.NOVEMBER)
            set(Calendar.DAY_OF_MONTH, 7)
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val expectedPaidDateMs = cal.timeInMillis

        assertTrue(
            repo.saveMonthlyLedger(
                tenantId = "t_paid_date",
                monthKey = "2026-11",
                rentAmount = 12000.0,
                prevReading = 100.0,
                currReading = 150.0,
                unitsConsumed = 50.0,
                electricityMode = ElectricityMode.FIXED_RATE,
                ratePerUnit = 8.5,
                electricityAmount = 425.0,
                previousBalance = 0.0,
                amountPaid = 12425.0,
                paidDateMs = expectedPaidDateMs,
                paymentMode = PaymentMode.UPI,
                paymentNote = "Paid on time",
                closingBalance = 0.0
            ).isSuccess
        )

        val savedPaid = repo.observeMonthlyLedgers().first().first { it.id == "t_paid_date_2026-11" }
        assertEquals(expectedPaidDateMs, savedPaid.paidDateMs)
        assertEquals("07 Nov 2026", BillingCalculator.formatPaymentDate(savedPaid.paidDateMs))
        assertEquals("07 Nov", BillingCalculator.formatPaymentDateShort(savedPaid.paidDateMs))

        // Saving unpaid month clears paidDateMs even if a non-zero timestamp is provided
        assertTrue(
            repo.saveMonthlyLedger(
                tenantId = "t_paid_date",
                monthKey = "2026-12",
                rentAmount = 12000.0,
                prevReading = 150.0,
                currReading = 200.0,
                unitsConsumed = 50.0,
                electricityMode = ElectricityMode.FIXED_RATE,
                ratePerUnit = 8.5,
                electricityAmount = 425.0,
                previousBalance = 0.0,
                amountPaid = 0.0,
                paidDateMs = expectedPaidDateMs,
                paymentMode = PaymentMode.CASH,
                paymentNote = "",
                closingBalance = 12425.0
            ).isSuccess
        )

        val savedUnpaid = repo.observeMonthlyLedgers().first().first { it.id == "t_paid_date_2026-12" }
        assertEquals(0L, savedUnpaid.paidDateMs)
        assertEquals("", BillingCalculator.formatPaymentDate(savedUnpaid.paidDateMs))
        }
    }

    @Test
    fun `restore v2 sqlite backup migrates cleanly to v3 preserving settings rate and ledgers`() {
        runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = LocalLedgerRepository(context)
        val v2File = File(context.cacheDir, "legacy_v2_backup.db")
        if (v2File.exists()) v2File.delete()

        val sqlDb = SQLiteDatabase.openOrCreateDatabase(v2File, null)
        try {
            sqlDb.execSQL(
                "CREATE TABLE properties (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, address TEXT NOT NULL, trackMainMeter INTEGER NOT NULL, consumerNumber TEXT NOT NULL, discomId TEXT NOT NULL, createdAtMs INTEGER NOT NULL, updatedAtMs INTEGER NOT NULL)"
            )
            sqlDb.execSQL(
                "CREATE TABLE tenants (id TEXT NOT NULL PRIMARY KEY, propertyId TEXT NOT NULL, name TEXT NOT NULL, roomNumber TEXT NOT NULL, phone TEXT NOT NULL, monthlyRent REAL NOT NULL, defaultMaintenance REAL NOT NULL, meterTrackingMode TEXT NOT NULL, initialMeterReading REAL NOT NULL, openingBalance REAL NOT NULL, active INTEGER NOT NULL, createdAtMs INTEGER NOT NULL, updatedAtMs INTEGER NOT NULL)"
            )
            sqlDb.execSQL(
                "CREATE TABLE monthly_ledgers (id TEXT NOT NULL PRIMARY KEY, tenantId TEXT NOT NULL, monthKey TEXT NOT NULL, rentAmount REAL NOT NULL, prevReading REAL NOT NULL, currReading REAL NOT NULL, unitsConsumed REAL NOT NULL, electricityMode TEXT NOT NULL, ratePerUnit REAL NOT NULL, electricityAmount REAL NOT NULL, maintenanceAmount REAL NOT NULL, extraChargesAmount REAL NOT NULL, extraChargesNote TEXT NOT NULL, previousBalance REAL NOT NULL, amountPaid REAL NOT NULL, paymentMode TEXT NOT NULL, paymentNote TEXT NOT NULL, closingBalance REAL NOT NULL, createdAtMs INTEGER NOT NULL, updatedAtMs INTEGER NOT NULL, FOREIGN KEY(tenantId) REFERENCES tenants(id) ON DELETE CASCADE)"
            )
            sqlDb.execSQL("CREATE INDEX IF NOT EXISTS index_monthly_ledgers_tenantId ON monthly_ledgers(tenantId)")
            sqlDb.execSQL("CREATE INDEX IF NOT EXISTS index_monthly_ledgers_monthKey ON monthly_ledgers(monthKey)")
            sqlDb.execSQL(
                "CREATE TABLE main_meter_logs (id TEXT NOT NULL PRIMARY KEY, propertyId TEXT NOT NULL, monthKey TEXT NOT NULL, consumerNumber TEXT NOT NULL, prevReading REAL NOT NULL, currReading REAL NOT NULL, unitsConsumed REAL NOT NULL, govtBillAmount REAL NOT NULL, billPaid INTEGER NOT NULL, notes TEXT NOT NULL, createdAtMs INTEGER NOT NULL, updatedAtMs INTEGER NOT NULL)"
            )
            sqlDb.execSQL(
                "CREATE TABLE billing_settings (id TEXT NOT NULL PRIMARY KEY, defaultRatePerUnit REAL NOT NULL, defaultElectricityMode TEXT NOT NULL, consumerNumber TEXT NOT NULL, updatedAtMs INTEGER NOT NULL)"
            )
            sqlDb.execSQL(
                "INSERT INTO properties VALUES ('$DEFAULT_PROPERTY_ID', 'Main House', '', 1, '', '', 1000, 1000)"
            )
            sqlDb.execSQL(
                "INSERT INTO billing_settings VALUES ('preferences', 10.25, 'FIXED_RATE', 'CA99', 1000)"
            )
            sqlDb.execSQL(
                "INSERT INTO tenants VALUES ('t_v2', '$DEFAULT_PROPERTY_ID', 'Sunita Devi', 'Room 2', '9000011111', 7500.0, 200.0, 'SUB_METER', 400.0, 0.0, 1, 1000, 1000)"
            )
            sqlDb.execSQL(
                "INSERT INTO monthly_ledgers VALUES ('t_v2_2026-09', 't_v2', '2026-09', 7500.0, 400.0, 480.0, 80.0, 'FIXED_RATE', 10.25, 820.0, 200.0, 0.0, '', 0.0, 8520.0, 'CASH', 'Paid', 0.0, 1000, 1000)"
            )
            sqlDb.execSQL("PRAGMA user_version = 2")
        } finally {
            sqlDb.close()
        }

        KirayaDatabaseHolder.replaceDatabaseFromFile(context, v2File)
        val tenants = repo.observeTenants().first()
        val ledgers = repo.observeMonthlyLedgers().first()

        assertEquals(1, tenants.size)
        val migratedTenant = tenants.first()
        assertEquals("Sunita Devi", migratedTenant.name)
        assertEquals(10.25, migratedTenant.electricityRatePerUnit, 0.001)
        assertEquals(10.25, migratedTenant.previousElectricityRatePerUnit, 0.001)
        assertEquals("", migratedTenant.rateEffectiveFromMonth)

        assertEquals(1, ledgers.size)
        assertEquals(0L, ledgers.first().paidDateMs)
        assertEquals(8520.0, ledgers.first().amountPaid, 0.01)

        v2File.delete()
        }
    }

    @Test
    @Config(shadows = [FakePdfDocumentShadow::class])
    fun `pdf generator exports valid multi page statement and cleans stale cache files`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val tenant = Tenant(
            id = "t_pdf",
            name = "Rohan Kulkarni",
            roomNumber = "Flat 102",
            phone = "9820012345",
            monthlyRent = 11000.0,
            defaultMaintenance = 500.0,
            meterTrackingMode = MeterTrackingMode.SUB_METER.name,
            initialMeterReading = 1000.0,
            electricityRatePerUnit = 9.0,
            openingBalance = 0.0,
            active = true
        )

        // Generate 24 months to guarantee multi-page PDF rendering
        val sampleLedgers = (0 until 24).map { idx ->
            val monthKey = BillingCalculator.shiftMonthKey("2025-01", idx)
            val prev = 1000.0 + idx * 80.0
            val curr = prev + 80.0
            MonthlyLedger(
                id = "${tenant.id}_$monthKey",
                tenantId = tenant.id,
                monthKey = monthKey,
                rentAmount = 11000.0,
                prevReading = prev,
                currReading = curr,
                unitsConsumed = 80.0,
                electricityMode = ElectricityMode.FIXED_RATE.name,
                ratePerUnit = 9.0,
                electricityAmount = 720.0,
                maintenanceAmount = 500.0,
                extraChargesAmount = if (idx % 3 == 0) 150.0 else 0.0,
                extraChargesNote = if (idx % 3 == 0) "Water tank" else "",
                previousBalance = 0.0,
                amountPaid = 12220.0,
                paidDateMs = 1_735_689_600_000L + (idx * 2_592_000_000L),
                paymentMode = PaymentMode.UPI.name,
                paymentNote = "Monthly rent",
                closingBalance = if (idx % 3 == 0) 150.0 else 0.0
            )
        }

        // Verify pagination splits 24 rows cleanly across 2 A4 pages (15 + 9)
        val pages = TenantStatementPdfGenerator.paginateLedgers(sampleLedgers)
        assertEquals(2, pages.size)
        assertEquals(15, pages[0].size)
        assertEquals(9, pages[1].size)
        assertEquals(1, TenantStatementPdfGenerator.paginateLedgers(emptyList()).size)

        // Stream export (Save PDF path)
        FakePdfDocumentShadow.lastFinishedPageCount = 0
        val out = ByteArrayOutputStream()
        TenantStatementPdfGenerator.writePdfToOutputStream(
            tenant = tenant,
            propertyName = "Main House",
            showPropertyName = false,
            ledgers = sampleLedgers,
            outputStream = out
        )
        assertEquals(2, FakePdfDocumentShadow.lastFinishedPageCount)
        val bytes = out.toByteArray()
        assertTrue("Expected non-empty multi-page PDF bytes", bytes.isNotEmpty())
        val header = String(bytes, 0, 5, Charsets.US_ASCII)
        assertEquals("%PDF-", header)

        // Also verify empty history renders 1 valid page without crashing
        FakePdfDocumentShadow.lastFinishedPageCount = 0
        val emptyOut = ByteArrayOutputStream()
        TenantStatementPdfGenerator.writePdfToOutputStream(
            tenant = tenant,
            propertyName = "Main House",
            showPropertyName = true,
            ledgers = emptyList(),
            outputStream = emptyOut
        )
        assertEquals(1, FakePdfDocumentShadow.lastFinishedPageCount)
        val emptyBytes = emptyOut.toByteArray()
        assertEquals("%PDF-", String(emptyBytes, 0, 5, Charsets.US_ASCII))

        // Shareable file export (FileProvider + stale PDF cleanup)
        val pdfDir = File(context.cacheDir, "pdfs").apply { mkdirs() }
        val stalePdf = File(pdfDir, "old_statement.pdf").apply { writeText("stale") }
        assertTrue(stalePdf.exists())

        val shared = TenantStatementPdfGenerator.generateShareablePdfFile(
            context = context,
            tenant = tenant,
            propertyName = "Main House",
            showPropertyName = false,
            ledgers = sampleLedgers
        )
        assertTrue(shared.fileName.startsWith("KirayaKhata_Rohan_Kulkarni_History_"))
        assertTrue(shared.fileName.endsWith(".pdf"))
        assertTrue(shared.sizeBytes > 0L)
        assertEquals("content", shared.uri.scheme)
        assertFalse("Stale cached PDF should be removed", stalePdf.exists())

        val generatedFile = File(pdfDir, shared.fileName)
        assertTrue(generatedFile.exists())
        generatedFile.delete()
    }
}

@org.robolectric.annotation.Implements(android.graphics.pdf.PdfDocument::class)
class FakePdfDocumentShadow {
    private val finishedPages = mutableListOf<android.graphics.pdf.PdfDocument.PageInfo>()
    private var currentPage: android.graphics.pdf.PdfDocument.Page? = null
    private var closed = false

    @org.robolectric.annotation.Implementation
    fun __constructor__() {
        closed = false
        finishedPages.clear()
        currentPage = null
    }

    @org.robolectric.annotation.Implementation
    fun startPage(pageInfo: android.graphics.pdf.PdfDocument.PageInfo): android.graphics.pdf.PdfDocument.Page {
        check(!closed) { "document is closed!" }
        check(currentPage == null) { "current page not finished" }
        val bitmap = android.graphics.Bitmap.createBitmap(
            pageInfo.pageWidth,
            pageInfo.pageHeight,
            android.graphics.Bitmap.Config.ARGB_8888
        )
        val canvas = android.graphics.Canvas(bitmap)
        val ctor = android.graphics.pdf.PdfDocument.Page::class.java.getDeclaredConstructor(
            android.graphics.Canvas::class.java,
            android.graphics.pdf.PdfDocument.PageInfo::class.java
        )
        ctor.isAccessible = true
        val page = ctor.newInstance(canvas, pageInfo)
        currentPage = page
        return page
    }

    @org.robolectric.annotation.Implementation
    fun finishPage(page: android.graphics.pdf.PdfDocument.Page) {
        check(!closed) { "document is closed!" }
        check(currentPage === page) { "page mismatch" }
        finishedPages.add(page.info)
        currentPage = null
        lastFinishedPageCount = finishedPages.size
    }

    @org.robolectric.annotation.Implementation
    fun writeTo(out: java.io.OutputStream) {
        check(!closed) { "document is closed!" }
        check(currentPage == null) { "cannot write with open page" }
        out.write("%PDF-1.4\n% KirayaKhata test pages=${finishedPages.size}\n%%EOF\n".toByteArray(Charsets.US_ASCII))
    }

    @org.robolectric.annotation.Implementation
    fun close() {
        closed = true
    }

    companion object {
        var lastFinishedPageCount: Int = 0
    }
}
