package com.example

import com.example.domain.BillingCalculator
import com.example.domain.DiscomCatalog
import com.example.domain.DiscomRegion
import com.example.model.ElectricityMode
import com.example.model.MainMeterLog
import com.example.model.MonthlyLedger
import com.example.model.PaymentMode
import com.example.model.Tenant
import com.example.ocr.IndianBillTextParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BillParserAndChartTest {

    @Test
    fun parseBsesBillText_extractsAllFields() {
        val sample = """
            BSES Rajdhani Power Limited
            CA No: 152940381
            Previous Reading: 4200
            Current Reading: 4650
            Units Consumed: 450 kWh
            Due Date: 28-Nov-2026
            Net Payable: Rs. 3,840.50
        """.trimIndent()

        val parsed = IndianBillTextParser.parse(sample)
        assertEquals("152940381", parsed.consumerNumber)
        assertEquals(4200.0, parsed.prevReading!!, 0.01)
        assertEquals(4650.0, parsed.currReading!!, 0.01)
        assertEquals(450.0, parsed.unitsConsumed!!, 0.01)
        assertEquals(3840.50, parsed.billAmount!!, 0.01)
        assertTrue(parsed.summaryNote.contains("28-Nov-2026"))
    }

    @Test
    fun parseMsedclAndBareMeterReading_handlesEdgeCases() {
        val msedclSample = """
            MAHAVITARAN MSEDCL
            Consumer Number: 170019283746
            Current Reading: 8120
            Billed Units: 310
            Amount Payable ₹ 2,950
        """.trimIndent()

        val parsed = IndianBillTextParser.parse(msedclSample)
        assertEquals("170019283746", parsed.consumerNumber)
        assertEquals(8120.0, parsed.currReading!!, 0.01)
        assertEquals(7810.0, parsed.prevReading!!, 0.01)
        assertEquals(310.0, parsed.unitsConsumed!!, 0.01)
        assertEquals(2950.0, parsed.billAmount!!, 0.01)

        val bareMeter = IndianBillTextParser.parse("001482 kWh")
        assertEquals(1482.0, bareMeter.unitsConsumed ?: bareMeter.currReading ?: 0.0, 0.01)
    }

    @Test
    fun buildConsumptionChartData_computesSharesAndMainVsTenantsDifference() {
        val month = "2026-11"
        val tenants = listOf(
            Tenant(id = "t1", name = "Amit", roomNumber = "101", monthlyRent = 8000.0),
            Tenant(id = "t2", name = "Priya", roomNumber = "102", monthlyRent = 9000.0)
        )
        val ledgers = listOf(
            MonthlyLedger(
                id = "l1",
                tenantId = "t1",
                monthKey = month,
                rentAmount = 8000.0,
                prevReading = 100.0,
                currReading = 250.0,
                unitsConsumed = 150.0,
                electricityMode = ElectricityMode.FIXED_RATE.name,
                ratePerUnit = 8.0,
                electricityAmount = 1200.0,
                previousBalance = 0.0,
                amountPaid = 9200.0,
                paymentMode = PaymentMode.UPI.name,
                closingBalance = 0.0
            ),
            MonthlyLedger(
                id = "l2",
                tenantId = "t2",
                monthKey = month,
                rentAmount = 9000.0,
                prevReading = 200.0,
                currReading = 250.0,
                unitsConsumed = 50.0,
                electricityMode = ElectricityMode.FIXED_RATE.name,
                ratePerUnit = 8.0,
                electricityAmount = 400.0,
                previousBalance = 0.0,
                amountPaid = 9400.0,
                paymentMode = PaymentMode.CASH.name,
                closingBalance = 0.0
            )
        )
        val mainLog = MainMeterLog(
            monthKey = month,
            consumerNumber = "152940381",
            prevReading = 5000.0,
            currReading = 5240.0,
            unitsConsumed = 240.0,
            govtBillAmount = 2100.0
        )

        val chart = BillingCalculator.buildConsumptionChartData(
            monthKey = month,
            tenants = tenants,
            mainLog = mainLog,
            allLedgers = ledgers
        )

        assertEquals(240.0, chart.mainUnits, 0.01)
        assertEquals(200.0, chart.subMeterUnitsSum, 0.01)
        assertEquals(40.0, chart.differenceUnits, 0.01)
        assertEquals(2, chart.tenantSegments.size)
        // Sorted highest consumption first
        assertEquals("Amit", chart.tenantSegments[0].tenantName)
        assertEquals(75.0, chart.tenantSegments[0].sharePercent, 0.01)
        assertEquals(25.0, chart.tenantSegments[1].sharePercent, 0.01)
    }

    @Test
    fun discomCatalog_coversAllRequestedRegionsAndIds() {
        val regionsPresent = DiscomCatalog.allDiscoms.map { it.region }.toSet()
        assertEquals(DiscomRegion.entries.toSet(), regionsPresent)
        assertNotNull(DiscomCatalog.findById("brpl"))
        assertNotNull(DiscomCatalog.findById("msedcl"))
        assertNotNull(DiscomCatalog.findById("uppcl"))
        assertNotNull(DiscomCatalog.findById("bescom"))
    }

    @Test
    fun buildConsumptionChartData_filtersByPropertyAndExcludesDirectGovtMeterTenants() {
        val month = "2026-11"
        val tenants = listOf(
            Tenant(
                id = "t1",
                propertyId = "house_a",
                name = "Amit",
                roomNumber = "A-101",
                meterTrackingMode = com.example.model.MeterTrackingMode.SUB_METER.name
            ),
            Tenant(
                id = "t2",
                propertyId = "house_a",
                name = "Neha",
                roomNumber = "A-102",
                meterTrackingMode = com.example.model.MeterTrackingMode.DIRECT_GOVT_METER.name
            ),
            Tenant(
                id = "t3",
                propertyId = "house_b",
                name = "Raju",
                roomNumber = "B-201",
                meterTrackingMode = com.example.model.MeterTrackingMode.SUB_METER.name
            )
        )
        val ledgers = listOf(
            MonthlyLedger(id = "l1", tenantId = "t1", monthKey = month, unitsConsumed = 120.0, electricityAmount = 960.0),
            MonthlyLedger(id = "l2", tenantId = "t2", monthKey = month, unitsConsumed = 999.0, electricityAmount = 0.0),
            MonthlyLedger(id = "l3", tenantId = "t3", monthKey = month, unitsConsumed = 200.0, electricityAmount = 1600.0)
        )
        val mainLog = MainMeterLog(id = "house_a_2026-11", propertyId = "house_a", monthKey = month, unitsConsumed = 150.0)

        val chart = BillingCalculator.buildConsumptionChartData(
            monthKey = month,
            tenants = tenants,
            mainLog = mainLog,
            allLedgers = ledgers,
            propertyIdFilter = "house_a"
        )

        assertEquals(150.0, chart.mainUnits, 0.01)
        assertEquals(120.0, chart.subMeterUnitsSum, 0.01)
        assertEquals(30.0, chart.differenceUnits, 0.01)
        assertEquals(1, chart.tenantSegments.size)
        assertEquals("Amit", chart.tenantSegments.first().tenantName)
    }
}
