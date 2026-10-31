package `in`.ignishers.milkmanager2.utils

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Environment
import android.widget.Toast
import androidx.core.content.FileProvider
import `in`.ignishers.milkmanager2.R
import `in`.ignishers.milkmanager2.models.Customer
import `in`.ignishers.milkmanager2.models.DailyTransaction
import `in`.ignishers.milkmanager2.models.Payment
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class PDFGenerator(
    private val context: Context?,
    private val customer: Customer,
    private val transactions: List<DailyTransaction>,
    private val payments: List<Payment> = emptyList(),
    private val initialOpeningBalance: Double = 0.0,
    private val postMonthPayments: List<Payment> = emptyList()
) {
    // Colors
    private val colorPrimary = Color.parseColor("#0288D1")
    private val colorPrimaryDark = Color.parseColor("#01579B")
    private val colorBgAlt = Color.parseColor("#F5F9FC")
    private val colorTextDark = Color.parseColor("#212121")
    private val colorTextLight = Color.parseColor("#757575")
    private val colorBorder = Color.parseColor("#BDBDBD")
    private val colorWhite = Color.WHITE
    private val colorGreen = Color.parseColor("#10B981")
    private val colorRed = Color.parseColor("#EF4444")
    private val colorExtra = Color.parseColor("#C2410C")
    private val colorRegBadge = Color.parseColor("#0288D1")
    private val colorExtraBadge = Color.parseColor("#EA580C")

    private val badgeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 5.2f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorPrimaryDark
        textSize = 17f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    private val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorWhite
        textSize = 8f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorTextDark
        textSize = 7.5f
    }

    private val boldTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorTextDark
        textSize = 7.5f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val amountPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorPrimaryDark
        textSize = 7.5f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val smallTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorTextLight
        textSize = 7f
    }

    private val regSmallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorPrimaryDark
        textSize = 6.8f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val extraSmallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorExtra
        textSize = 6.8f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val extraPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorExtra
        textSize = 7.5f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val centerSmallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorTextLight
        textSize = 8f
        textAlign = Paint.Align.CENTER
    }

    private val centerDatePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorTextDark
        textSize = 8f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    private val centerPayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorGreen
        textSize = 7.8f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorBorder
        style = Paint.Style.STROKE
        strokeWidth = 0.8f
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val monthHeaderFormatter = DateTimeFormatter.ofPattern("MMMM yyyy")

    class SessionData {
        var regQ = 0.0
        var regA = 0.0
        var extraQ = 0.0
        var extraA = 0.0

        val totalQ: Double get() = regQ + extraQ
        val totalA: Double get() = regA + extraA
        val hasEntries: Boolean get() = totalQ > 0 || totalA > 0
        val hasBoth: Boolean get() = (regQ > 0 || regA > 0) && (extraQ > 0 || extraA > 0)
    }

    class DayData {
        val morn = SessionData()
        val eve = SessionData()
        var payA = 0.0
    }

    fun generate(fileName: String): File? {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()

        // 1. Group transactions & payments by Month (YYYY-MM)
        val allMonthKeys = mutableSetOf<String>()
        transactions.forEach { t ->
            t.date?.takeIf { it.length >= 7 }?.let { allMonthKeys.add(it.substring(0, 7)) }
        }
        payments.forEach { p ->
            p.date?.takeIf { it.length >= 7 }?.let { allMonthKeys.add(it.substring(0, 7)) }
        }

        val sortedMonths = allMonthKeys.sorted()
        if (sortedMonths.isEmpty()) {
            val currentMonthKey = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"))
            sortedMonths.plus(currentMonthKey)
        }

        val monthStartBalances = mutableMapOf<String, Double>()
        var runningBal = initialOpeningBalance

        for (monthKey in sortedMonths) {
            monthStartBalances[monthKey] = runningBal
            val monthTrans = transactions.filter { it.date?.startsWith(monthKey) == true }
            val monthPays = payments.filter { it.date?.startsWith(monthKey) == true }
            val bill = monthTrans.sumOf { it.amount?.toDouble() ?: 0.0 }
            val pay = monthPays.sumOf { it.amount.toDouble() }
            runningBal += (bill - pay)
        }

        // 2. Iterate Months (1 page per month)
        val monthsToRender = if (sortedMonths.isEmpty()) listOf(LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"))) else sortedMonths
        for (monthKey in monthsToRender) {
            val page = document.startPage(pageInfo)
            val canvas = page.canvas

            val monthTrans = transactions.filter { it.date?.startsWith(monthKey) == true }
            val monthPays = payments.filter { it.date?.startsWith(monthKey) == true }

            drawPageContent(
                canvas = canvas,
                monthKey = monthKey,
                monthTrans = monthTrans,
                monthPays = monthPays,
                startBal = monthStartBalances[monthKey] ?: initialOpeningBalance
            )

            document.finishPage(page)
        }

        // Save to Downloads or App External Storage folder safely
        val targetDir = try {
            val pub = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!pub.exists()) pub.mkdirs()
            if (pub.canWrite()) pub else (context?.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context?.cacheDir)
        } catch (e: Exception) {
            context?.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context?.cacheDir
        } ?: File("/sdcard/Download")

        if (!targetDir.exists()) targetDir.mkdirs()

        val safeFileName = if (fileName.endsWith(".pdf")) fileName else "$fileName.pdf"
        var file = File(targetDir, safeFileName)
        var counter = 1
        val baseName = safeFileName.substring(0, safeFileName.length - 4)

        while (file.exists()) {
            file = File(targetDir, "$baseName($counter).pdf")
            counter++
        }

        try {
            document.writeTo(FileOutputStream(file))
            return file
        } catch (e: IOException) {
            e.printStackTrace()
            // Fallback to internal cache dir if permission failed
            try {
                if (context != null) {
                    val cacheFile = File(context.cacheDir, safeFileName)
                    document.writeTo(FileOutputStream(cacheFile))
                    return cacheFile
                }
            } catch (ce: Exception) {
                ce.printStackTrace()
            }
            return null
        } finally {
            document.close()
        }
    }

    private fun drawWatermark(canvas: Canvas) {
        val watermarkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0288D1")
            alpha = 30 // ~12% opacity
            textSize = 48f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.10f
        }
        val subWatermarkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0288D1")
            alpha = 24 // ~9% opacity
            textSize = 15f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.15f
        }
        canvas.save()
        canvas.rotate(-35f, PAGE_WIDTH / 2f, PAGE_HEIGHT / 2f)
        canvas.drawText("IGNISHERS", PAGE_WIDTH / 2f, PAGE_HEIGHT / 2f - 10f, watermarkPaint)
        canvas.drawText("ignishers.in", PAGE_WIDTH / 2f, PAGE_HEIGHT / 2f + 18f, subWatermarkPaint)
        canvas.restore()
    }

    private fun drawPageContent(
        canvas: Canvas,
        monthKey: String,
        monthTrans: List<DailyTransaction>,
        monthPays: List<Payment>,
        startBal: Double
    ) {
        // --- 10-20% Visibility Watermark ---
        drawWatermark(canvas)

        // --- HEADER ---
        var y = 35f

        // App/Shop Title inside a rounded box
        fillPaint.color = colorBgAlt
        canvas.drawRoundRect(MARGIN, 14f, PAGE_WIDTH - MARGIN, 48f, 6f, 6f, fillPaint)
        canvas.drawRoundRect(MARGIN, 14f, PAGE_WIDTH - MARGIN, 48f, 6f, 6f, borderPaint)
        canvas.drawText("MILK BILL STATEMENT", PAGE_WIDTH / 2f, 36f, titlePaint)

        y = 66f
        // Customer Info (Left)
        val rawCustName = customer.name ?: "N/A"
        val custName = if (rawCustName.length > 22) rawCustName.take(20) + "..." else rawCustName
        canvas.drawText("Customer: $custName", MARGIN + 4f, y, boldTextPaint)
        canvas.drawText("Mobile: ${customer.mobile?.takeIf { it.isNotBlank() } ?: "-"}", MARGIN + 4f, y + 13f, textPaint)
        val rawAddr = customer.address?.takeIf { it.isNotBlank() } ?: "-"
        val addr = if (rawAddr.length > 24) rawAddr.take(22) + "..." else rawAddr
        canvas.drawText("Address: $addr", MARGIN + 4f, y + 26f, textPaint)

        // Month Info (Right)
        val monthDate = try { LocalDate.parse("$monthKey-01") } catch (_: Exception) { LocalDate.now() }
        val monthTitle = monthDate.format(monthHeaderFormatter)

        val rightAlign = Paint(boldTextPaint).apply {
            textAlign = Paint.Align.RIGHT
            textSize = 10f
            color = colorPrimaryDark
        }
        canvas.drawText("Month: $monthTitle", PAGE_WIDTH - MARGIN - 4f, y + 13f, rightAlign)

        val totalDueRight = Paint(amountPaint).apply {
            textAlign = Paint.Align.RIGHT
            textSize = 9.5f
            color = if ((customer.currentDue ?: java.math.BigDecimal.ZERO) > java.math.BigDecimal.ZERO) colorRed else colorGreen
        }
        canvas.drawText("Current Due: ₹${customer.currentDue ?: 0}", PAGE_WIDTH - MARGIN - 4f, y + 26f, totalDueRight)

        // --- TABLE ---
        y = 108f
        val tableTop = y
        val rowHeight = 25f
        val headerHeight = 18f

        // 4 Columns per side: Date (24), Morning (60), Evening (60), Payment (48) = 192f
        val colWidths = floatArrayOf(24f, 60f, 60f, 48f)
        val halfWidth = (PAGE_WIDTH - 2 * MARGIN) / 2f

        // Draw Header Bg
        fillPaint.color = colorPrimary
        canvas.drawRect(MARGIN, y, PAGE_WIDTH - MARGIN, y + headerHeight, fillPaint)

        // Draw Header Text
        val headerY = y + 12.5f
        drawRowHeaders(canvas, headerY, MARGIN, colWidths)
        drawRowHeaders(canvas, headerY, MARGIN + halfWidth, colWidths)

        y += headerHeight

        // Prepare Day Data for up to 31 days
        val daysInMonth = monthDate.lengthOfMonth()
        val days = Array(32) { DayData() }

        // Fill milk transactions with separation of Regular and Extra
        for (t in monthTrans) {
            val dateStr = t.date ?: continue
            if (dateStr.length < 10) continue
            val dayOfMonth = dateStr.substring(8, 10).toIntOrNull() ?: continue
            if (dayOfMonth in 1..31) {
                val d = days[dayOfMonth]
                val amt = t.amount?.toDouble() ?: 0.0
                val qty = t.quantity?.toDouble() ?: 0.0
                val s = if (t.session.equals("Evening", ignoreCase = true)) d.eve else d.morn
                if (t.milkType.equals("Extra", ignoreCase = true)) {
                    s.extraQ += qty
                    s.extraA += amt
                } else {
                    s.regQ += qty
                    s.regA += amt
                }
            }
        }

        // Fill payments in month
        for (p in monthPays) {
            val dateStr = p.date ?: continue
            if (dateStr.length < 10) continue
            val dayOfMonth = dateStr.substring(8, 10).toIntOrNull() ?: continue
            if (dayOfMonth in 1..31) {
                days[dayOfMonth].payA += p.amount.toDouble()
            }
        }

        // Draw Rows (1..16 on Left, 17..31 on Right)
        val maxRows = 16
        for (i in 1..maxRows) {
            val rowY = y + (i - 1) * rowHeight

            // Alternate row background
            if (i % 2 == 0) {
                fillPaint.color = colorBgAlt
                canvas.drawRect(MARGIN, rowY, PAGE_WIDTH - MARGIN, rowY + rowHeight, fillPaint)
            }

            // Left side: Day i
            drawDayCell(canvas, days[i], i, rowY, MARGIN, colWidths)

            // Right side: Day i + 16
            val rightDay = i + 16
            if (rightDay <= daysInMonth) {
                drawDayCell(canvas, days[rightDay], rightDay, rowY, MARGIN + halfWidth, colWidths)
            } else {
                drawEmptyDayCell(canvas, rowY, MARGIN + halfWidth, colWidths)
            }

            // Row separator
            canvas.drawLine(MARGIN, rowY + rowHeight, PAGE_WIDTH - MARGIN, rowY + rowHeight, borderPaint)
        }

        val tableBottom = y + maxRows * rowHeight

        // Outer border & Center split divider
        canvas.drawRect(MARGIN, tableTop, PAGE_WIDTH - MARGIN, tableBottom, borderPaint)
        val midX = MARGIN + halfWidth
        canvas.drawLine(midX, tableTop, midX, tableBottom, borderPaint)

        // Draw column vertical dividers
        drawColumnDividers(canvas, tableTop, tableBottom, MARGIN, colWidths)
        drawColumnDividers(canvas, tableTop, tableBottom, midX, colWidths)

        // --- SUMMARY SECTION ---
        y = tableBottom + 12f

        var totalMilk = 0.0
        var totalBill = 0.0
        var totalPayInMonth = 0.0
        for (i in 1..daysInMonth) {
            val d = days[i]
            totalMilk += d.morn.totalQ + d.eve.totalQ
            totalBill += d.morn.totalA + d.eve.totalA
            totalPayInMonth += d.payA
        }

        val monthBalance = startBal + totalBill - totalPayInMonth
        val postMonthTotal = postMonthPayments.sumOf { it.amount.toDouble() }
        val finalDue = monthBalance - postMonthTotal

        val sumBoxTop = y
        val sumBoxHeight = if (postMonthTotal > 0) 116f else 100f
        fillPaint.color = colorBgAlt
        canvas.drawRoundRect(MARGIN, sumBoxTop, PAGE_WIDTH - MARGIN, sumBoxTop + sumBoxHeight, 6f, 6f, fillPaint)
        canvas.drawRoundRect(MARGIN, sumBoxTop, PAGE_WIDTH - MARGIN, sumBoxTop + sumBoxHeight, 6f, 6f, borderPaint)

        // Summary Header Strip
        fillPaint.color = colorPrimary
        canvas.drawRoundRect(MARGIN, sumBoxTop, PAGE_WIDTH - MARGIN, sumBoxTop + 20f, 6f, 6f, fillPaint)
        canvas.drawRect(MARGIN, sumBoxTop + 12f, PAGE_WIDTH - MARGIN, sumBoxTop + 20f, fillPaint)

        val summaryTitlePaint = Paint(headerPaint).apply { textSize = 8.5f; textAlign = Paint.Align.CENTER }
        canvas.drawText("MONTHLY SUMMARY & SETTLEMENT", PAGE_WIDTH / 2f, sumBoxTop + 13.5f, summaryTitlePaint)

        y = sumBoxTop + 34f
        val col1X = MARGIN + 8f
        val col2X = midX + 8f

        canvas.drawText("Previous Due:", col1X, y, boldTextPaint)
        canvas.drawText(String.format(java.util.Locale.US, "₹ %.2f", startBal), col1X + 70f, y, textPaint)

        canvas.drawText("Total Milk:", col2X, y, boldTextPaint)
        canvas.drawText(formatMilk(totalMilk), col2X + 65f, y, textPaint)

        y += 16f
        canvas.drawText("This Month Bill:", col1X, y, boldTextPaint)
        canvas.drawText(String.format(java.util.Locale.US, "+ ₹ %.2f", totalBill), col1X + 70f, y, amountPaint)

        canvas.drawText("Paid in Month:", col2X, y, boldTextPaint)
        canvas.drawText(String.format(java.util.Locale.US, "- ₹ %.2f", totalPayInMonth), col2X + 65f, y, textPaint)

        if (postMonthTotal > 0) {
            y += 16f
            val lastPay = postMonthPayments.lastOrNull()
            val payDateStr = lastPay?.date ?: "Next Month"
            canvas.drawText("Next Month Pay:", col1X, y, boldTextPaint)
            val postPayPaint = Paint(amountPaint).apply { color = colorGreen }
            canvas.drawText(String.format(java.util.Locale.US, "- ₹ %.2f (%s)", postMonthTotal, payDateStr), col1X + 80f, y, postPayPaint)
        }

        y += 20f
        canvas.drawLine(MARGIN + 6f, y - 6f, PAGE_WIDTH - MARGIN - 6f, y - 6f, borderPaint)

        val finalPaint = Paint(boldTextPaint).apply { textSize = 10f; color = colorPrimaryDark }
        canvas.drawText("FINAL DUE:", col1X, y + 4f, finalPaint)

        val finalAmtPaint = Paint(amountPaint).apply {
            textSize = 12f
            color = if (finalDue > 0) colorRed else colorGreen
        }
        canvas.drawText(String.format(java.util.Locale.US, "₹ %.2f", finalDue), col1X + 60f, y + 4f, finalAmtPaint)

        // Status Stamp: [ PAID ] or [ PENDING ]
        val stampText = if (finalDue <= 0.0) "PAID" else "PENDING"
        val stampColor = if (finalDue <= 0.0) colorGreen else colorRed

        val stampBoxPaint = Paint(borderPaint).apply {
            color = stampColor
            strokeWidth = 1.2f
        }
        val stampTextPaint = Paint(boldTextPaint).apply {
            color = stampColor
            textSize = 9.5f
            textAlign = Paint.Align.CENTER
        }
        val stampRight = PAGE_WIDTH - MARGIN - 8f
        val stampLeft = stampRight - 62f
        val stampTop = y - 8f
        canvas.drawRoundRect(stampLeft, stampTop, stampRight, stampTop + 16f, 3f, 3f, stampBoxPaint)
        canvas.drawText(stampText, (stampLeft + stampRight) / 2f, stampTop + 11.5f, stampTextPaint)

        // Watermark Footer
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorTextLight
            textSize = 8f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("Generated by MilkManager • Powered by Ignishers (ignishers.in)", PAGE_WIDTH / 2f, PAGE_HEIGHT - 12f, footerPaint)
    }

    private fun drawRowHeaders(canvas: Canvas, y: Float, startX: Float, colWidths: FloatArray) {
        var curX = startX
        val headers = arrayOf("Date", "Morning", "Evening", "Payment")
        val headerCenterPaint = Paint(headerPaint).apply { textAlign = Paint.Align.CENTER }
        for (i in headers.indices) {
            val centerX = curX + colWidths[i] / 2f
            canvas.drawText(headers[i], centerX, y, headerCenterPaint)
            curX += colWidths[i]
        }
    }

    private fun drawColumnDividers(canvas: Canvas, topY: Float, bottomY: Float, startX: Float, colWidths: FloatArray) {
        var curX = startX
        for (i in 0 until colWidths.size - 1) {
            curX += colWidths[i]
            canvas.drawLine(curX, topY, curX, bottomY, borderPaint)
        }
    }

    private fun drawDayCell(
        canvas: Canvas, d: DayData, day: Int, rowY: Float, startX: Float, colWidths: FloatArray
    ) {
        var curX = startX

        // 1. Date (e.g. 01, 02)
        val dateDay = String.format(java.util.Locale.US, "%02d", day)
        canvas.drawText(dateDay, curX + colWidths[0] / 2f, rowY + 15.5f, centerDatePaint)
        curX += colWidths[0]

        // 2. Morning Milk
        drawSessionCell(canvas, d.morn, rowY, curX, colWidths[1])
        curX += colWidths[1]

        // 3. Evening Milk
        drawSessionCell(canvas, d.eve, rowY, curX, colWidths[2])
        curX += colWidths[2]

        // 4. Payment
        if (d.payA > 0) {
            canvas.drawText("₹${formatAmt(d.payA)}", curX + colWidths[3] / 2f, rowY + 15.5f, centerPayPaint)
        } else {
            canvas.drawText("-", curX + colWidths[3] / 2f, rowY + 15.5f, centerSmallPaint)
        }
    }

    private fun drawSessionCell(canvas: Canvas, s: SessionData, rowY: Float, curX: Float, colWidth: Float) {
        if (!s.hasEntries) {
            canvas.drawText("-", curX + colWidth / 2f, rowY + 15.5f, centerSmallPaint)
            return
        }

        val badgeCx = curX + 6.5f
        val textStartX = curX + 13f

        if (s.hasBoth) {
            // Both Regular and Extra entries exist - draw colored circle badge R and E first!
            val line1CenterY = rowY + 8f
            drawTypeBadge(canvas, "R", badgeCx, line1CenterY, 4f)
            val rText = "${formatQty(s.regQ)} (₹${formatAmt(s.regA)})"
            canvas.drawText(rText, textStartX, rowY + 10.5f, regSmallPaint)

            val line2CenterY = rowY + 18f
            drawTypeBadge(canvas, "E", badgeCx, line2CenterY, 4f)
            val eText = "${formatQty(s.extraQ)} (₹${formatAmt(s.extraA)})"
            canvas.drawText(eText, textStartX, rowY + 20.5f, extraSmallPaint)
        } else if (s.regQ > 0 || s.regA > 0) {
            // Only Regular entry exists - Badge 'R' first
            val line1CenterY = rowY + 8f
            drawTypeBadge(canvas, "R", badgeCx, line1CenterY, 4f)
            canvas.drawText(formatQty(s.regQ), textStartX, rowY + 10.5f, boldTextPaint)
            canvas.drawText("₹${formatAmt(s.regA)}", textStartX, rowY + 20.5f, amountPaint)
        } else {
            // Only Extra entry exists - Badge 'E' first
            val line1CenterY = rowY + 8f
            drawTypeBadge(canvas, "E", badgeCx, line1CenterY, 4f)
            canvas.drawText(formatQty(s.extraQ), textStartX, rowY + 10.5f, extraPaint)
            canvas.drawText("₹${formatAmt(s.extraA)}", textStartX, rowY + 20.5f, amountPaint)
        }
    }

    private fun drawTypeBadge(canvas: Canvas, type: String, cx: Float, cy: Float, radius: Float = 4f) {
        val isExtra = type.equals("E", ignoreCase = true)
        badgeBgPaint.color = if (isExtra) colorExtraBadge else colorRegBadge
        canvas.drawCircle(cx, cy, radius, badgeBgPaint)
        val textY = cy - ((badgeTextPaint.descent() + badgeTextPaint.ascent()) / 2f)
        canvas.drawText(type, cx, textY, badgeTextPaint)
    }

    private fun drawEmptyDayCell(canvas: Canvas, rowY: Float, startX: Float, colWidths: FloatArray) {
        var curX = startX
        for (i in colWidths.indices) {
            val centerX = curX + colWidths[i] / 2f
            canvas.drawText("-", centerX, rowY + 15.5f, centerSmallPaint)
            curX += colWidths[i]
        }
    }

    private fun formatQty(qty: Double): String {
        val s = if (qty % 1.0 == 0.0) {
            qty.toInt().toString()
        } else {
            String.format(java.util.Locale.US, "%.2f", qty).trimEnd('0').trimEnd('.')
        }
        return "${s}L"
    }

    private fun formatAmt(amt: Double): String {
        return if (amt % 1.0 == 0.0) {
            amt.toInt().toString()
        } else {
            String.format(java.util.Locale.US, "%.1f", amt).trimEnd('0').trimEnd('.')
        }
    }

    private fun formatMilk(qty: Double): String {
        val s = if (qty % 1.0 == 0.0) {
            qty.toInt().toString()
        } else {
            String.format(java.util.Locale.US, "%.1f", qty).trimEnd('0').trimEnd('.')
        }
        return "$s L"
    }

    companion object {
        const val PAGE_WIDTH = 412
        const val PAGE_HEIGHT = 680
        const val MARGIN = 14f

        fun generateCustomerMonthlyPDF(context: Context, reportData: `in`.ignishers.milkmanager2.ui.pos.BillReportData): File {
            val custName = (reportData.customer.name ?: "Customer").replace("[^a-zA-Z0-9]".toRegex(), "_")
            val titleClean = reportData.title.replace("[^a-zA-Z0-9]".toRegex(), "_")
            val fileName = "MilkBill_${custName}_$titleClean.pdf"
            val generator = PDFGenerator(
                context = context,
                customer = reportData.customer,
                transactions = reportData.allTransactions,
                payments = reportData.allPayments,
                initialOpeningBalance = reportData.months.firstOrNull()?.openingBalance?.toDouble() ?: 0.0,
                postMonthPayments = reportData.postMonthPayments
            )
            val file = generator.generate(fileName)
            return file ?: File(context.cacheDir, fileName)
        }

        fun shareFile(context: Context, file: File) {
            try {
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, context.getString(R.string.share_bill_pdf_title)))
            } catch (e: Exception) {
                Toast.makeText(context, context.getString(R.string.error_sharing_pdf, e.localizedMessage ?: ""), Toast.LENGTH_SHORT).show()
            }
        }

        fun openFile(context: Context, file: File) {
            try {
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/pdf")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(context, context.getString(R.string.no_pdf_viewer), Toast.LENGTH_SHORT).show()
            }
        }
    }
}