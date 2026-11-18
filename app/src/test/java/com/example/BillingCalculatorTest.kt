package com.example

import com.example.domain.BillingCalculator
import com.example.model.MainMeterLog
import com.example.model.MonthlyLedger
import com.example.model.Tenant
import org.junit.Assert.assertEquals
import org.junit.Test

class BillingCalculatorTest {

    @Test
    fun `formatInr formats Indian lakhs and negative amounts`() {
        assertEquals("₹1,25,000", BillingCalculator.formatInr(125000.0))
        assertEquals("₹12,50,000", BillingCalculator.formatInr(1250000.0))
        assertEquals("-₹500", BillingCalculator.formatInr(-500.0))
        assertEquals("₹850.5", BillingCalculator.formatInr(850.5))
    }

    @Test
    fun `formatUnits formats values with space before kWh`() {
        assertEquals("0 kWh", BillingCalculator.formatUnits(0.0))
        assertEquals("450 kWh", BillingCalculator.formatUnits(450.0))
        assertEquals("1,250.5 kWh", BillingCalculator.formatUnits(1250.5))
    }

    @Test
    fun `carry forward balance and sub meter reading propagate from prior month`() {
        val tenant = Tenant(
            id = "t1",
            name = "Suresh",
            roomNumber = "Room 101",
            monthlyRent = 8000.0,
            initialMeterReading = 1000.0,
            openingBalance = 500.0
        )
        val octLedger = MonthlyLedger(
            id = "t1_2026-10",
            tenantId = "t1",
            monthKey = "2026-10",
            rentAmount = 8000.0,
            prevReading = 1000.0,
            currReading = 1120.0,
            unitsConsumed = 120.0,
            electricityAmount = 960.0,
            previousBalance = 500.0,
            amountPaid = 10000.0,
            closingBalance = -540.0 // Paid 540 extra -> Advance!
        )

        val novSummary = BillingCalculator.buildTenantMonthSummary(
            tenant = tenant,
            monthKey = "2026-11",
            allLedgers = listOf(octLedger)
        )

        assertEquals(-540.0, novSummary.carriedPreviousBalance, 0.01)
        assertEquals(1120.0, novSummary.suggestedPrevReading, 0.01)
        assertEquals(7460.0, novSummary.totalPayable, 0.01)
    }

    @Test
    fun `reconciliation compares main meter units and bill with tenant sub meters`() {
        val mainLog = MainMeterLog(
            id = "2026-11",
            monthKey = "2026-11",
            prevReading = 5000.0,
            currReading = 5500.0,
            unitsConsumed = 500.0,
            govtBillAmount = 4000.0
        )
        val ledgers = listOf(
            MonthlyLedger(id = "t1_2026-11", monthKey = "2026-11", unitsConsumed = 200.0, electricityAmount = 1600.0),
            MonthlyLedger(id = "t2_2026-11", monthKey = "2026-11", unitsConsumed = 250.0, electricityAmount = 2000.0)
        )

        val rec = BillingCalculator.buildReconciliation("2026-11", mainLog, ledgers)
        assertEquals(500.0, rec.mainUnits, 0.01)
        assertEquals(450.0, rec.subMeterUnitsSum, 0.01)
        assertEquals(50.0, rec.differenceUnits, 0.01)
        assertEquals(8.0, rec.effectiveMainRate, 0.01)
        assertEquals(8.89, rec.effectiveSubSplitRate, 0.01)
    }

    @Test
    fun `maintenance and extra charges flow into total payable and dashboard summary`() {
        val directTenant = Tenant(
            id = "t_direct",
            name = "Vikram",
            roomNumber = "House 2 - Flat 1",
            monthlyRent = 12000.0,
            defaultMaintenance = 500.0,
            meterTrackingMode = com.example.model.MeterTrackingMode.DIRECT_GOVT_METER.name,
            openingBalance = 0.0
        )
        val unrecordedSummary = BillingCalculator.buildTenantMonthSummary(
            tenant = directTenant,
            monthKey = "2026-11",
            allLedgers = emptyList()
        )
        assertEquals(12500.0, unrecordedSummary.totalPayable, 0.01)

        val recordedLedger = MonthlyLedger(
            id = "t_direct_2026-11",
            tenantId = "t_direct",
            monthKey = "2026-11",
            rentAmount = 12000.0,
            electricityAmount = 0.0,
            maintenanceAmount = 500.0,
            extraChargesAmount = 300.0,
            extraChargesNote = "Plumbing repair",
            previousBalance = 0.0,
            amountPaid = 12000.0,
            closingBalance = 800.0
        )
        val recordedSummary = BillingCalculator.buildTenantMonthSummary(
            tenant = directTenant,
            monthKey = "2026-11",
            allLedgers = listOf(recordedLedger)
        )
        assertEquals(12800.0, recordedSummary.totalPayable, 0.01)
        assertEquals(800.0, recordedSummary.closingBalance, 0.01)

        val dash = BillingCalculator.buildMonthlyDashboard(listOf(recordedSummary))
        assertEquals(12800.0, dash.totalExpected, 0.01)
        assertEquals(12000.0, dash.totalCollected, 0.01)
        assertEquals(800.0, dash.totalDue, 0.01)
        assertEquals(0.0, dash.totalSubMeterUnits, 0.01)
    }
}
