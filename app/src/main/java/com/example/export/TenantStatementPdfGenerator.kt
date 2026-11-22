package com.example.export

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.domain.BillingCalculator
import com.example.model.MonthlyLedger
import com.example.model.Tenant
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

data class SharePdfFile(
    val uri: Uri,
    val fileName: String,
    val sizeBytes: Long
)

object TenantStatementPdfGenerator {

    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN_X = 36f
    private const val CONTENT_WIDTH = PAGE_WIDTH - (MARGIN_X * 2f)
    private const val FOOTER_Y = PAGE_HEIGHT - 28f
    private const val MAX_CONTENT_Y = PAGE_HEIGHT - 54f

    // Column anchors inside [36..559]
    private const val COL_MONTH_X = 42f
    private const val COL_RENT_R = 166f
    private const val COL_ELEC_R = 258f
    private const val COL_MAINT_R = 324f
    private const val COL_PAID_R = 398f
    private const val COL_DATE_X = 408f
    private const val COL_BAL_R = 553f

    fun buildSuggestedFileName(tenant: Tenant): String {
        val safeName = tenant.name
            .trim()
            .replace(Regex("[^A-Za-z0-9]+"), "_")
            .trim('_')
            .ifBlank { "Tenant" }
        val stamp = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        return "KirayaKhata_${safeName}_History_$stamp.pdf"
    }

    fun generateShareablePdfFile(
        context: Context,
        tenant: Tenant,
        propertyName: String,
        showPropertyName: Boolean,
        ledgers: List<MonthlyLedger>
    ): SharePdfFile {
        val appContext = context.applicationContext
        val pdfDir = File(appContext.cacheDir, "pdfs").apply { mkdirs() }
        pdfDir.listFiles()?.forEach { old ->
            if (old.isFile && old.name.endsWith(".pdf")) {
                old.delete()
            }
        }
        val fileName = buildSuggestedFileName(tenant)
        val outFile = File(pdfDir, fileName)
        outFile.outputStream().use { stream ->
            writePdfToOutputStream(
                tenant = tenant,
                propertyName = propertyName,
                showPropertyName = showPropertyName,
                ledgers = ledgers,
                outputStream = stream
            )
        }
        val uri = FileProvider.getUriForFile(
            appContext,
            "${appContext.packageName}.fileprovider",
            outFile
        )
        return SharePdfFile(
            uri = uri,
            fileName = fileName,
            sizeBytes = outFile.length()
        )
    }

    fun writePdfToOutputStream(
        tenant: Tenant,
        propertyName: String,
        showPropertyName: Boolean,
        ledgers: List<MonthlyLedger>,
        outputStream: OutputStream
    ) {
        val sortedLedgers = ledgers.sortedBy { it.monthKey }
        val generatedOn = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.forLanguageTag("en-IN")).format(Date())

        val totalBilled = BillingCalculator.roundToTwoDecimals(
            sortedLedgers.sumOf {
                it.rentAmount + it.electricityAmount + it.maintenanceAmount + it.extraChargesAmount
            }
        )
        val totalPaid = BillingCalculator.roundToTwoDecimals(sortedLedgers.sumOf { it.amountPaid })
        val currentNetBalance = sortedLedgers.lastOrNull()?.closingBalance ?: tenant.openingBalance

        val rowHeight = 32f
        val firstPageTableTop = 278f
        val nextPageTableTop = 94f
        val chunks = paginateLedgers(sortedLedgers, firstPageTableTop, nextPageTableTop, rowHeight)
        val totalPages = chunks.size

        val document = PdfDocument()
        try {
            val headerBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#0F5132") }
            val surfacePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#F5F8F6") }
            val tableHeaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#E6F2EC") }
            val zebraPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FAFDFB") }
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#D5E1DB")
                style = Paint.Style.STROKE
                strokeWidth = 1f
            }
            val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#E2EAE6")
                strokeWidth = 0.8f
            }

            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 18f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            }
            val subtitleWhitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#D7F3E5")
                textSize = 9.5f
            }
            val rightWhitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#D7F3E5")
                textSize = 8.5f
                textAlign = Paint.Align.RIGHT
            }
            val sectionHeadingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#11261E")
                textSize = 11.5f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            }
            val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#54665E")
                textSize = 8.5f
            }
            val valueBoldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#11261E")
                textSize = 10.5f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            }
            val thLeftPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#0F5132")
                textSize = 8.5f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            }
            val thRightPaint = Paint(thLeftPaint).apply { textAlign = Paint.Align.RIGHT }
            val cellLeftPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#1A2621")
                textSize = 8.8f
            }
            val cellBoldLeftPaint = Paint(cellLeftPaint).apply {
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            }
            val cellSubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#5E7068")
                textSize = 7.4f
            }
            val cellRightPaint = Paint(cellLeftPaint).apply { textAlign = Paint.Align.RIGHT }
            val cellSubRightPaint = Paint(cellSubPaint).apply { textAlign = Paint.Align.RIGHT }
            val paidRightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#0E6E46")
                textSize = 8.8f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                textAlign = Paint.Align.RIGHT
            }
            val dueRightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#B42318")
                textSize = 8.8f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                textAlign = Paint.Align.RIGHT
            }
            val footerLeftPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#6A7B73")
                textSize = 8f
            }
            val footerRightPaint = Paint(footerLeftPaint).apply { textAlign = Paint.Align.RIGHT }

            chunks.forEachIndexed { pageIndex, pageLedgers ->
                val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageIndex + 1).create()
                val page = document.startPage(pageInfo)
                val canvas = page.canvas

                // Top banner
                canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), 66f, headerBgPaint)
                canvas.drawText("KirayaKhata", MARGIN_X, 30f, titlePaint)
                canvas.drawText("Tenant Payment History Statement", MARGIN_X, 48f, subtitleWhitePaint)
                canvas.drawText("Generated: $generatedOn", PAGE_WIDTH - MARGIN_X, 34f, rightWhitePaint)
                if (pageIndex > 0) {
                    canvas.drawText(
                        "${tenant.name} (${tenant.roomNumber}) — Continued",
                        PAGE_WIDTH - MARGIN_X,
                        49f,
                        rightWhitePaint
                    )
                }

                var y = if (pageIndex == 0) {
                    // Tenant details card
                    val infoRect = RectF(MARGIN_X, 82f, PAGE_WIDTH - MARGIN_X, 174f)
                    canvas.drawRoundRect(infoRect, 8f, 8f, surfacePaint)
                    canvas.drawRoundRect(infoRect, 8f, 8f, borderPaint)

                    canvas.drawText("TENANT DETAILS", MARGIN_X + 14f, 100f, thLeftPaint)
                    canvas.drawText(
                        truncateText("${tenant.name}  •  ${tenant.roomNumber}", 44),
                        MARGIN_X + 14f,
                        118f,
                        sectionHeadingPaint
                    )

                    val metaLeft = buildString {
                        if (showPropertyName && propertyName.isNotBlank()) {
                            append("House: $propertyName")
                        }
                        if (tenant.phone.isNotBlank()) {
                            if (isNotEmpty()) append("   |   ")
                            append("Phone: ${tenant.phone}")
                        }
                        if (isEmpty()) {
                            append(if (tenant.active) "Status: Active Tenant" else "Status: Vacated")
                        }
                    }
                    canvas.drawText(truncateText(metaLeft, 60), MARGIN_X + 14f, 134f, labelPaint)

                    val rentSummary = buildString {
                        append("Monthly Rent: ${BillingCalculator.formatInr(tenant.monthlyRent)}")
                        if (tenant.defaultMaintenance > 0.005) {
                            append("   |   Default Maint: ${BillingCalculator.formatInr(tenant.defaultMaintenance)}")
                        }
                        if (tenant.tracksSubMeter) {
                            append("   |   Sub-Meter Rate: ${BillingCalculator.formatInr(tenant.electricityRatePerUnit)}/kWh")
                        } else {
                            append("   |   Meter: Direct Govt (DISCOM)")
                        }
                    }
                    canvas.drawText(truncateText(rentSummary, 76), MARGIN_X + 14f, 150f, labelPaint)

                    val openingLine = when {
                        tenant.openingBalance > 0.005 -> "Opening Balance: Due ${BillingCalculator.formatInr(tenant.openingBalance)}"
                        tenant.openingBalance < -0.005 -> "Opening Balance: Advance ${BillingCalculator.formatInr(-tenant.openingBalance)}"
                        else -> "Opening Balance: ₹0 (Settled)"
                    }
                    canvas.drawText(openingLine, MARGIN_X + 14f, 165f, labelPaint)

                    // 3-card Summary Strip
                    val gap = 10f
                    val boxWidth = (CONTENT_WIDTH - gap * 2f) / 3f
                    val boxTop = 188f
                    val boxBottom = 248f

                    drawSummaryBox(
                        canvas = canvas,
                        left = MARGIN_X,
                        top = boxTop,
                        right = MARGIN_X + boxWidth,
                        bottom = boxBottom,
                        label = "TOTAL BILLED (${sortedLedgers.size} MO)",
                        value = BillingCalculator.formatInr(totalBilled),
                        valueColor = Color.parseColor("#11261E"),
                        bgPaint = surfacePaint,
                        borderPaint = borderPaint,
                        labelPaint = labelPaint,
                        valuePaint = valueBoldPaint
                    )
                    drawSummaryBox(
                        canvas = canvas,
                        left = MARGIN_X + boxWidth + gap,
                        top = boxTop,
                        right = MARGIN_X + boxWidth * 2f + gap,
                        bottom = boxBottom,
                        label = "TOTAL PAID",
                        value = BillingCalculator.formatInr(totalPaid),
                        valueColor = Color.parseColor("#0E6E46"),
                        bgPaint = surfacePaint,
                        borderPaint = borderPaint,
                        labelPaint = labelPaint,
                        valuePaint = valueBoldPaint
                    )
                    val balanceText = when {
                        currentNetBalance > 0.005 -> "Due ${BillingCalculator.formatInr(currentNetBalance)}"
                        currentNetBalance < -0.005 -> "Adv ${BillingCalculator.formatInr(-currentNetBalance)}"
                        else -> "Settled (₹0)"
                    }
                    val balanceColor = when {
                        currentNetBalance > 0.005 -> Color.parseColor("#B42318")
                        else -> Color.parseColor("#0E6E46")
                    }
                    drawSummaryBox(
                        canvas = canvas,
                        left = MARGIN_X + (boxWidth + gap) * 2f,
                        top = boxTop,
                        right = PAGE_WIDTH - MARGIN_X,
                        bottom = boxBottom,
                        label = "CURRENT BALANCE",
                        value = balanceText,
                        valueColor = balanceColor,
                        bgPaint = surfacePaint,
                        borderPaint = borderPaint,
                        labelPaint = labelPaint,
                        valuePaint = valueBoldPaint
                    )

                    canvas.drawText("Monthly Payment Breakdown", MARGIN_X, 270f, sectionHeadingPaint)
                    firstPageTableTop
                } else {
                    canvas.drawText("Monthly Payment Breakdown (Continued)", MARGIN_X, 86f, sectionHeadingPaint)
                    nextPageTableTop
                }

                // Table header bar
                val headerRect = RectF(MARGIN_X, y, PAGE_WIDTH - MARGIN_X, y + 24f)
                canvas.drawRoundRect(headerRect, 4f, 4f, tableHeaderPaint)
                val headerBaseline = y + 15.5f
                canvas.drawText("Month", COL_MONTH_X, headerBaseline, thLeftPaint)
                canvas.drawText("Rent", COL_RENT_R, headerBaseline, thRightPaint)
                canvas.drawText("Electricity", COL_ELEC_R, headerBaseline, thRightPaint)
                canvas.drawText("Maint/Ext", COL_MAINT_R, headerBaseline, thRightPaint)
                canvas.drawText("Paid", COL_PAID_R, headerBaseline, thRightPaint)
                canvas.drawText("Paid Date", COL_DATE_X, headerBaseline, thLeftPaint)
                canvas.drawText("Balance", COL_BAL_R, headerBaseline, thRightPaint)
                y += 26f

                if (pageLedgers.isEmpty()) {
                    canvas.drawText(
                        "No monthly rent or payment entries recorded yet.",
                        COL_MONTH_X,
                        y + 24f,
                        labelPaint
                    )
                } else {
                    pageLedgers.forEachIndexed { rowIdx, entry ->
                        if (rowIdx % 2 == 1) {
                            canvas.drawRect(MARGIN_X, y, PAGE_WIDTH - MARGIN_X, y + rowHeight, zebraPaint)
                        }
                        val topLineY = y + 13f
                        val subLineY = y + 24.5f

                        // Month
                        canvas.drawText(
                            BillingCalculator.formatMonthShort(entry.monthKey),
                            COL_MONTH_X,
                            topLineY,
                            cellBoldLeftPaint
                        )
                        if (abs(entry.previousBalance) > 0.005) {
                            val prevTag = if (entry.previousBalance > 0) {
                                "Prev +${BillingCalculator.formatInr(entry.previousBalance)}"
                            } else {
                                "Adv ${BillingCalculator.formatInr(-entry.previousBalance)}"
                            }
                            canvas.drawText(truncateText(prevTag, 16), COL_MONTH_X, subLineY, cellSubPaint)
                        }

                        // Rent
                        canvas.drawText(
                            BillingCalculator.formatInr(entry.rentAmount),
                            COL_RENT_R,
                            topLineY,
                            cellRightPaint
                        )

                        // Electricity
                        if (tenant.tracksSubMeter) {
                            canvas.drawText(
                                BillingCalculator.formatInr(entry.electricityAmount),
                                COL_ELEC_R,
                                topLineY,
                                cellRightPaint
                            )
                            val elecSub = "${BillingCalculator.formatReading(entry.unitsConsumed)}u @ ${BillingCalculator.formatInr(entry.ratePerUnit)}"
                            canvas.drawText(elecSub, COL_ELEC_R, subLineY, cellSubRightPaint)
                        } else {
                            canvas.drawText("₹0", COL_ELEC_R, topLineY, cellRightPaint)
                            canvas.drawText("Direct Govt", COL_ELEC_R, subLineY, cellSubRightPaint)
                        }

                        // Maint / Extra
                        val maintExtra = entry.maintenanceAmount + entry.extraChargesAmount
                        canvas.drawText(
                            BillingCalculator.formatInr(maintExtra),
                            COL_MAINT_R,
                            topLineY,
                            cellRightPaint
                        )
                        if (entry.extraChargesNote.isNotBlank()) {
                            canvas.drawText(
                                truncateText(entry.extraChargesNote, 12),
                                COL_MAINT_R,
                                subLineY,
                                cellSubRightPaint
                            )
                        }

                        // Amount Paid
                        canvas.drawText(
                            BillingCalculator.formatInr(entry.amountPaid),
                            COL_PAID_R,
                            topLineY,
                            if (entry.amountPaid > 0.005) paidRightPaint else cellRightPaint
                        )
                        if (entry.amountPaid > 0.005) {
                            canvas.drawText(entry.resolvedPaymentMode.label, COL_PAID_R, subLineY, cellSubRightPaint)
                        }

                        // Payment Date
                        val paidDateStr = when {
                            entry.amountPaid <= 0.005 -> "Unpaid"
                            entry.paidDateMs > 0L -> BillingCalculator.formatPaymentDate(entry.paidDateMs)
                            else -> "Date not set"
                        }
                        canvas.drawText(paidDateStr, COL_DATE_X, topLineY, cellLeftPaint)
                        if (entry.paymentNote.isNotBlank()) {
                            canvas.drawText(
                                truncateText(entry.paymentNote, 15),
                                COL_DATE_X,
                                subLineY,
                                cellSubPaint
                            )
                        }

                        // Closing Balance
                        val balStr = when {
                            entry.closingBalance > 0.005 -> BillingCalculator.formatInr(entry.closingBalance)
                            entry.closingBalance < -0.005 -> BillingCalculator.formatInr(-entry.closingBalance)
                            else -> "₹0"
                        }
                        val balSub = when {
                            entry.closingBalance > 0.005 -> "Due"
                            entry.closingBalance < -0.005 -> "Advance"
                            else -> "Settled"
                        }
                        val balPaint = if (entry.closingBalance > 0.005) dueRightPaint else paidRightPaint
                        canvas.drawText(balStr, COL_BAL_R, topLineY, balPaint)
                        canvas.drawText(balSub, COL_BAL_R, subLineY, cellSubRightPaint)

                        y += rowHeight
                        canvas.drawLine(MARGIN_X, y, PAGE_WIDTH - MARGIN_X, y, dividerPaint)
                    }
                }

                // Footer
                canvas.drawLine(MARGIN_X, FOOTER_Y - 14f, PAGE_WIDTH - MARGIN_X, FOOTER_Y - 14f, dividerPaint)
                canvas.drawText(
                    "KirayaKhata • Rent & Sub-Meter Passbook Statement",
                    MARGIN_X,
                    FOOTER_Y,
                    footerLeftPaint
                )
                canvas.drawText(
                    "Page ${pageIndex + 1} of $totalPages",
                    PAGE_WIDTH - MARGIN_X,
                    FOOTER_Y,
                    footerRightPaint
                )

                document.finishPage(page)
            }

            document.writeTo(outputStream)
            outputStream.flush()
        } finally {
            document.close()
        }
    }

    private fun drawSummaryBox(
        canvas: Canvas,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        label: String,
        value: String,
        valueColor: Int,
        bgPaint: Paint,
        borderPaint: Paint,
        labelPaint: Paint,
        valuePaint: Paint
    ) {
        val rect = RectF(left, top, right, bottom)
        canvas.drawRoundRect(rect, 8f, 8f, bgPaint)
        canvas.drawRoundRect(rect, 8f, 8f, borderPaint)
        canvas.drawText(label, left + 10f, top + 20f, labelPaint)
        val coloredValPaint = Paint(valuePaint).apply {
            color = valueColor
            textSize = 12f
        }
        canvas.drawText(truncateText(value, 18), left + 10f, top + 42f, coloredValPaint)
    }

    internal fun paginateLedgers(
        sortedLedgers: List<MonthlyLedger>,
        firstPageTableTop: Float = 278f,
        nextPageTableTop: Float = 94f,
        rowHeight: Float = 32f
    ): List<List<MonthlyLedger>> {
        val firstPageRows = ((MAX_CONTENT_Y - (firstPageTableTop + 26f)) / rowHeight).toInt().coerceAtLeast(1)
        val nextPageRows = ((MAX_CONTENT_Y - (nextPageTableTop + 26f)) / rowHeight).toInt().coerceAtLeast(1)
        val chunks = mutableListOf<List<MonthlyLedger>>()
        if (sortedLedgers.isEmpty()) {
            chunks.add(emptyList())
        } else {
            var idx = 0
            val firstCount = firstPageRows.coerceAtMost(sortedLedgers.size)
            chunks.add(sortedLedgers.subList(0, firstCount))
            idx += firstCount
            while (idx < sortedLedgers.size) {
                val end = (idx + nextPageRows).coerceAtMost(sortedLedgers.size)
                chunks.add(sortedLedgers.subList(idx, end))
                idx = end
            }
        }
        return chunks
    }

    private fun truncateText(text: String, maxChars: Int): String {
        return if (text.length <= maxChars) text else text.take(maxChars - 1) + "…"
    }
}
