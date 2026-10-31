package `in`.ignishers.milkmanager2.ui.sales

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.FileOutputStream
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SalesAnalyticsPDFGenerator(
    private val context: Context,
    private val filterLabel: String,
    private val overview: SalesOverview,
    private val monthlySales: List<MonthlySalesItem>,
    private val routeSales: List<RouteSalesItem>,
    private val topDue: List<TopCustomerDueItem>
) {
    companion object {
        const val PAGE_WIDTH = 595 // A4 standard
        const val PAGE_HEIGHT = 842
        const val MARGIN = 36f
    }

    private val colorPrimary = Color.parseColor("#1E3A8A") // Deep Royal Navy
    private val colorRevenue = Color.parseColor("#4F46E5") // Indigo
    private val colorCollected = Color.parseColor("#059669") // Emerald
    private val colorMilk = Color.parseColor("#D97706") // Amber
    private val colorDue = Color.parseColor("#DC2626") // Rose Red
    private val colorTextDark = Color.parseColor("#1E293B")
    private val colorTextMuted = Color.parseColor("#64748B")
    private val colorBgRowAlt = Color.parseColor("#F8FAFC")
    private val colorBorder = Color.parseColor("#E2E8F0")

    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorPrimary
        textSize = 18f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val subTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorTextMuted
        textSize = 9f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    }

    private val sectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorPrimary
        textSize = 12f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val cardTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 8.5f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val cardValuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 14f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val tableHeaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 8.5f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorTextDark
        textSize = 8f
    }

    private val textBoldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorTextDark
        textSize = 8f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val rightAlignPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorTextDark
        textSize = 8f
        textAlign = Paint.Align.RIGHT
    }

    private val rightAlignBoldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorTextDark
        textSize = 8f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.RIGHT
    }

    private val rectPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
        color = colorBorder
    }

    fun generatePDF(): File? {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas

        drawWatermark(canvas)

        var y = 40f

        // 1. Header Banner
        canvas.drawText("MILK MANAGER - BUSINESS AUDIT & SALES REPORT", MARGIN, y, titlePaint)
        y += 14f

        val genDate = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US).format(Date())
        canvas.drawText("Scope: $filterLabel  •  Generated on: $genDate", MARGIN, y, subTitlePaint)
        y += 18f

        // 2. 4 Metric KPI Cards (2 rows of 2 cards)
        val cardWidth = (PAGE_WIDTH - (MARGIN * 2) - 12f) / 2f
        val cardHeight = 44f

        // Row 1: Total Revenue & Total Collected
        drawKpiCard(canvas, MARGIN, y, cardWidth, cardHeight, "TOTAL REVENUE (SALES)", "₹${formatCurrency(overview.totalRevenue)}", colorRevenue, Color.parseColor("#EEF2FF"))
        drawKpiCard(canvas, MARGIN + cardWidth + 12f, y, cardWidth, cardHeight, "TOTAL COLLECTED (RECEIVED)", "₹${formatCurrency(overview.totalCollected)}", colorCollected, Color.parseColor("#ECFDF5"))
        y += cardHeight + 8f

        // Row 2: Total Milk & Pending Dues
        drawKpiCard(canvas, MARGIN, y, cardWidth, cardHeight, "TOTAL MILK SOLD", "${formatQuantity(overview.totalMilk)} L", colorMilk, Color.parseColor("#FFFBEB"))
        drawKpiCard(canvas, MARGIN + cardWidth + 12f, y, cardWidth, cardHeight, "TOTAL OUTSTANDING DUES", "₹${formatCurrency(overview.totalPendingDue)}", colorDue, Color.parseColor("#FEF2F2"))
        y += cardHeight + 20f

        // 3. Monthly Breakdown Table
        canvas.drawText("MONTHLY SALES & COLLECTION BREAKDOWN", MARGIN, y, sectionPaint)
        y += 10f

        val tableWidth = PAGE_WIDTH - (MARGIN * 2)
        val colW1 = tableWidth * 0.22f // Month
        val colW2 = tableWidth * 0.18f // Milk
        val colW3 = tableWidth * 0.20f // Revenue
        val colW4 = tableWidth * 0.20f // Collected
        val colW5 = tableWidth * 0.20f // Gap

        // Table Header
        rectPaint.color = colorPrimary
        canvas.drawRoundRect(RectF(MARGIN, y, MARGIN + tableWidth, y + 18f), 4f, 4f, rectPaint)

        var colX = MARGIN + 8f
        canvas.drawText("MONTH", colX, y + 12f, tableHeaderPaint)
        colX += colW1
        canvas.drawText("MILK SOLD (L)", colX, y + 12f, tableHeaderPaint)
        colX += colW2
        canvas.drawText("SALES (₹)", colX, y + 12f, tableHeaderPaint)
        colX += colW3
        canvas.drawText("COLLECTED (₹)", colX, y + 12f, tableHeaderPaint)
        colX += colW4
        canvas.drawText("NET GAP (₹)", colX, y + 12f, tableHeaderPaint)
        y += 18f

        val monthsToShow = monthlySales.take(10)
        var isAlt = false
        var sumMilk = BigDecimal.ZERO
        var sumRev = BigDecimal.ZERO
        var sumCol = BigDecimal.ZERO

        for (item in monthsToShow) {
            sumMilk = sumMilk.add(item.milkSold)
            sumRev = sumRev.add(item.revenue)
            sumCol = sumCol.add(item.collected)

            if (isAlt) {
                rectPaint.color = colorBgRowAlt
                canvas.drawRect(RectF(MARGIN, y, MARGIN + tableWidth, y + 16f), rectPaint)
            }
            canvas.drawLine(MARGIN, y + 16f, MARGIN + tableWidth, y + 16f, strokePaint)

            colX = MARGIN + 8f
            canvas.drawText(item.monthDisplayName, colX, y + 11f, textBoldPaint)
            colX += colW1
            canvas.drawText(formatQuantity(item.milkSold), colX + colW2 - 16f, y + 11f, rightAlignPaint)
            colX += colW2
            canvas.drawText(formatCurrency(item.revenue), colX + colW3 - 16f, y + 11f, rightAlignPaint)
            colX += colW3
            canvas.drawText(formatCurrency(item.collected), colX + colW4 - 16f, y + 11f, rightAlignPaint)
            colX += colW4

            val gapColor = if (item.gap <= BigDecimal.ZERO) colorCollected else colorDue
            rightAlignBoldPaint.color = gapColor
            canvas.drawText(formatCurrency(item.gap), colX + colW5 - 16f, y + 11f, rightAlignBoldPaint)

            y += 16f
            isAlt = !isAlt
        }

        // Monthly Summary Row
        rectPaint.color = Color.parseColor("#F1F5F9")
        canvas.drawRect(RectF(MARGIN, y, MARGIN + tableWidth, y + 18f), rectPaint)
        canvas.drawLine(MARGIN, y + 18f, MARGIN + tableWidth, y + 18f, strokePaint)

        colX = MARGIN + 8f
        canvas.drawText("TOTAL (${monthsToShow.size} Months)", colX, y + 12f, textBoldPaint)
        colX += colW1
        rightAlignBoldPaint.color = colorTextDark
        canvas.drawText(formatQuantity(sumMilk), colX + colW2 - 16f, y + 12f, rightAlignBoldPaint)
        colX += colW2
        canvas.drawText(formatCurrency(sumRev), colX + colW3 - 16f, y + 12f, rightAlignBoldPaint)
        colX += colW3
        canvas.drawText(formatCurrency(sumCol), colX + colW4 - 16f, y + 12f, rightAlignBoldPaint)
        colX += colW4
        val totGap = sumRev.subtract(sumCol)
        rightAlignBoldPaint.color = if (totGap <= BigDecimal.ZERO) colorCollected else colorDue
        canvas.drawText(formatCurrency(totGap), colX + colW5 - 16f, y + 12f, rightAlignBoldPaint)
        y += 26f

        // 4. Route / Area Sales Breakdown
        if (routeSales.isNotEmpty() && y < PAGE_HEIGHT - 160f) {
            canvas.drawText("ROUTE & COLONY DISTRIBUTION", MARGIN, y, sectionPaint)
            y += 10f

            rectPaint.color = colorRevenue
            canvas.drawRoundRect(RectF(MARGIN, y, MARGIN + tableWidth, y + 18f), 4f, 4f, rectPaint)

            val rCol1 = tableWidth * 0.32f
            val rCol2 = tableWidth * 0.16f
            val rCol3 = tableWidth * 0.18f
            val rCol4 = tableWidth * 0.18f
            val rCol5 = tableWidth * 0.16f

            colX = MARGIN + 8f
            canvas.drawText("ROUTE NAME", colX, y + 12f, tableHeaderPaint)
            colX += rCol1
            canvas.drawText("CUSTOMERS", colX, y + 12f, tableHeaderPaint)
            colX += rCol2
            canvas.drawText("MILK (L)", colX, y + 12f, tableHeaderPaint)
            colX += rCol3
            canvas.drawText("REVENUE (₹)", colX, y + 12f, tableHeaderPaint)
            colX += rCol4
            canvas.drawText("DUE (₹)", colX, y + 12f, tableHeaderPaint)
            y += 18f

            isAlt = false
            for (r in routeSales.take(6)) {
                if (isAlt) {
                    rectPaint.color = colorBgRowAlt
                    canvas.drawRect(RectF(MARGIN, y, MARGIN + tableWidth, y + 15f), rectPaint)
                }
                canvas.drawLine(MARGIN, y + 15f, MARGIN + tableWidth, y + 15f, strokePaint)

                colX = MARGIN + 8f
                canvas.drawText(r.routeName, colX, y + 11f, textBoldPaint)
                colX += rCol1
                canvas.drawText("${r.customerCount}", colX + rCol2 - 16f, y + 11f, rightAlignPaint)
                colX += rCol2
                canvas.drawText(formatQuantity(r.milkSold), colX + rCol3 - 16f, y + 11f, rightAlignPaint)
                colX += rCol3
                canvas.drawText(formatCurrency(r.revenue), colX + rCol4 - 16f, y + 11f, rightAlignPaint)
                colX += rCol4
                rightAlignBoldPaint.color = if (r.pendingDue > BigDecimal.ZERO) colorDue else colorCollected
                canvas.drawText(formatCurrency(r.pendingDue), colX + rCol5 - 16f, y + 11f, rightAlignBoldPaint)

                y += 15f
                isAlt = !isAlt
            }
            y += 16f
        }

        // 5. Top Due Debtors Section
        if (topDue.isNotEmpty() && y < PAGE_HEIGHT - 90f) {
            canvas.drawText("TOP OUTSTANDING DUES (ACTIONABLE RECOVERY LIST)", MARGIN, y, sectionPaint)
            y += 10f

            for ((idx, cust) in topDue.take(4).withIndex()) {
                val debtorBox = RectF(MARGIN, y, MARGIN + tableWidth, y + 16f)
                rectPaint.color = Color.parseColor("#FFF1F2")
                canvas.drawRoundRect(debtorBox, 3f, 3f, rectPaint)

                textBoldPaint.color = colorDue
                canvas.drawText("#${idx + 1}  ${cust.customerName} (${cust.routeName ?: "Direct"})", MARGIN + 8f, y + 11f, textBoldPaint)

                rightAlignBoldPaint.color = colorDue
                canvas.drawText("Pending: ₹${formatCurrency(cust.currentDue)}", MARGIN + tableWidth - 8f, y + 11f, rightAlignBoldPaint)

                y += 18f
            }
        }

        // Footer
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorTextMuted
            textSize = 7.5f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("Generated automatically by MilkManager 2.0 • Advanced Dairy Operations System", PAGE_WIDTH / 2f, PAGE_HEIGHT - 16f, footerPaint)

        document.finishPage(page)

        val fileName = "Sales_Audit_Report_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.pdf"
        val outDir = File(context.cacheDir, "reports").apply { if (!exists()) mkdirs() }
        val file = File(outDir, fileName)

        return try {
            document.writeTo(FileOutputStream(file))
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            document.close()
        }
    }

    private fun drawKpiCard(
        canvas: Canvas,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        label: String,
        value: String,
        accentColor: Int,
        bgColor: Int
    ) {
        val rect = RectF(x, y, x + width, y + height)
        rectPaint.color = bgColor
        canvas.drawRoundRect(rect, 6f, 6f, rectPaint)

        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1f
            color = accentColor
            alpha = 70
        }
        canvas.drawRoundRect(rect, 6f, 6f, border)

        // Accent indicator bar on left
        val barRect = RectF(x, y + 4f, x + 3.5f, y + height - 4f)
        rectPaint.color = accentColor
        canvas.drawRoundRect(barRect, 2f, 2f, rectPaint)

        cardTitlePaint.color = accentColor
        canvas.drawText(label, x + 10f, y + 14f, cardTitlePaint)

        cardValuePaint.color = colorTextDark
        canvas.drawText(value, x + 10f, y + 33f, cardValuePaint)
    }

    private fun drawWatermark(canvas: Canvas) {
        val watermarkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1E3A8A")
            alpha = 15 // ~6% subtle opacity
            textSize = 52f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.08f
        }
        canvas.save()
        canvas.rotate(-35f, PAGE_WIDTH / 2f, PAGE_HEIGHT / 2f)
        canvas.drawText("MILK MANAGER", PAGE_WIDTH / 2f, PAGE_HEIGHT / 2f, watermarkPaint)
        canvas.restore()
    }

    private fun formatCurrency(amount: BigDecimal?): String {
        val amt = amount ?: BigDecimal.ZERO
        return if (amt.remainder(BigDecimal.ONE).compareTo(BigDecimal.ZERO) == 0) {
            amt.setScale(0, RoundingMode.HALF_UP).toPlainString()
        } else {
            amt.setScale(2, RoundingMode.HALF_UP).toPlainString()
        }
    }

    private fun formatQuantity(quantity: BigDecimal?): String {
        val q = quantity ?: BigDecimal.ZERO
        return if (q.remainder(BigDecimal.ONE).compareTo(BigDecimal.ZERO) == 0) {
            q.setScale(0, RoundingMode.HALF_UP).toPlainString()
        } else {
            q.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
        }
    }
}
