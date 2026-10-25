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

    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorPrimaryDark
        textSize = 22f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    private val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorWhite
        textSize = 9f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorTextDark
        textSize = 8.5f
    }

    private val boldTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorTextDark
        textSize = 8.5f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val amountPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorPrimaryDark
        textSize = 8.5f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val smallTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorTextLight
        textSize = 8f
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorBorder
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val monthHeaderFormatter = DateTimeFormatter.ofPattern("MMMM yyyy")

    class DayData {
        var mornQ = 0.0
        var mornA = 0.0
        var eveQ = 0.0
        var eveA = 0.0
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
            alpha = 35 // ~14% opacity (10 to 20 percent visibility)
            textSize = 66f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.12f
        }
        val subWatermarkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0288D1")
            alpha = 28 // ~11% opacity
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.18f
        }
        canvas.save()
        canvas.rotate(-35f, PAGE_WIDTH / 2f, PAGE_HEIGHT / 2f)
        canvas.drawText("IGNISHERS", PAGE_WIDTH / 2f, PAGE_HEIGHT / 2f - 12f, watermarkPaint)
        canvas.drawText("ignishers.in", PAGE_WIDTH / 2f, PAGE_HEIGHT / 2f + 24f, subWatermarkPaint)
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
        var y = 40f

        // App/Shop Title inside a rounded box
        fillPaint.color = colorBgAlt
        canvas.drawRoundRect(MARGIN, 18f, PAGE_WIDTH - MARGIN, 58f, 8f, 8f, fillPaint)
        canvas.drawRoundRect(MARGIN, 18f, PAGE_WIDTH - MARGIN, 58f, 8f, 8f, borderPaint)
        canvas.drawText("MILK BILL STATEMENT", PAGE_WIDTH / 2f, 44f, titlePaint)

        y = 78f
        // Customer Info (Left)
        canvas.drawText("Customer: ${customer.name ?: "N/A"}", MARGIN + 5, y, boldTextPaint)
        canvas.drawText("Mobile: ${customer.mobile?.takeIf { it.isNotBlank() } ?: "-"}", MARGIN + 5, y + 14, textPaint)
        val addr = if (customer.address.isNullOrEmpty()) "-" else customer.address
        canvas.drawText("Address: $addr", MARGIN + 5, y + 28, textPaint)

        // Month Info (Right)
        val monthDate = try { LocalDate.parse("$monthKey-01") } catch (_: Exception) { LocalDate.now() }
        val monthTitle = monthDate.format(monthHeaderFormatter)

        val rightAlign = Paint(boldTextPaint).apply {
            textAlign = Paint.Align.RIGHT
            textSize = 12f
            color = colorPrimaryDark
        }
        canvas.drawText("Bill Month: $monthTitle", PAGE_WIDTH - MARGIN - 5, y + 14, rightAlign)

        val totalDueRight = Paint(amountPaint).apply {
            textAlign = Paint.Align.RIGHT
            textSize = 10f
            color = if ((customer.currentDue ?: java.math.BigDecimal.ZERO) > java.math.BigDecimal.ZERO) colorRed else colorGreen
        }
        canvas.drawText("Total Current Due: ₹${customer.currentDue ?: 0}", PAGE_WIDTH - MARGIN - 5, y + 28, totalDueRight)

        // --- TABLE ---
        y = 125f
        val tableTop = y
        val rowHeight = 25f
        val headerHeight = 20f

        // 4 Columns per side: Date (30), Morning (80), Evening (80), Payment (87.5) = 277.5
        val colWidths = floatArrayOf(32f, 80f, 80f, 85.5f)
        val halfWidth = (PAGE_WIDTH - 2 * MARGIN) / 2f

        // Draw Header Bg
        fillPaint.color = colorPrimary
        canvas.drawRect(MARGIN, y, PAGE_WIDTH - MARGIN, y + headerHeight, fillPaint)

        // Draw Header Text
        val headerY = y + 14f
        drawRowHeaders(canvas, headerY, MARGIN, colWidths)
        drawRowHeaders(canvas, headerY, MARGIN + halfWidth, colWidths)

        y += headerHeight

        // Prepare Day Data for up to 31 days
        val daysInMonth = monthDate.lengthOfMonth()
        val days = Array(32) { DayData() }

        // Fill milk transactions
        for (t in monthTrans) {
            val dateStr = t.date ?: continue
            if (dateStr.length < 10) continue
            val dayOfMonth = dateStr.substring(8, 10).toIntOrNull() ?: continue
            if (dayOfMonth in 1..31) {
                val d = days[dayOfMonth]
                val amt = t.amount?.toDouble() ?: 0.0
                val qty = t.quantity?.toDouble() ?: 0.0
                if (t.session.equals("Evening", ignoreCase = true)) {
                    d.eveQ += qty
                    d.eveA += amt
                } else {
                    d.mornQ += qty
                    d.mornA += amt
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
        y = tableBottom + 16f

        var totalMilk = 0.0
        var totalBill = 0.0
        var totalPayInMonth = 0.0
        for (i in 1..daysInMonth) {
            val d = days[i]
            totalMilk += d.mornQ + d.eveQ
            totalBill += d.mornA + d.eveA
            totalPayInMonth += d.payA
        }

        val monthBalance = startBal + totalBill - totalPayInMonth
        val postMonthTotal = postMonthPayments.sumOf { it.amount.toDouble() }
        val finalDue = monthBalance - postMonthTotal

        val sumBoxTop = y
        val sumBoxHeight = if (postMonthTotal > 0) 130f else 115f
        fillPaint.color = colorBgAlt
        canvas.drawRoundRect(MARGIN, sumBoxTop, PAGE_WIDTH - MARGIN, sumBoxTop + sumBoxHeight, 8f, 8f, fillPaint)
        canvas.drawRoundRect(MARGIN, sumBoxTop, PAGE_WIDTH - MARGIN, sumBoxTop + sumBoxHeight, 8f, 8f, borderPaint)

        // Summary Header Strip
        fillPaint.color = colorPrimary
        canvas.drawRoundRect(MARGIN, sumBoxTop, PAGE_WIDTH - MARGIN, sumBoxTop + 22f, 8f, 8f, fillPaint)
        canvas.drawRect(MARGIN, sumBoxTop + 14f, PAGE_WIDTH - MARGIN, sumBoxTop + 22f, fillPaint)

        val summaryTitlePaint = Paint(headerPaint).apply { textSize = 10f; textAlign = Paint.Align.CENTER }
        canvas.drawText("MONTHLY SUMMARY & SETTLEMENT", PAGE_WIDTH / 2f, sumBoxTop + 15f, summaryTitlePaint)

        y = sumBoxTop + 40f
        val col1X = MARGIN + 12f
        val col2X = midX + 12f

        canvas.drawText("Previous Due:", col1X, y, boldTextPaint)
        canvas.drawText(String.format("₹ %.2f", startBal), col1X + 90f, y, textPaint)

        canvas.drawText("Total Milk:", col2X, y, boldTextPaint)
        canvas.drawText(formatMilk(totalMilk), col2X + 95f, y, textPaint)

        y += 18f
        canvas.drawText("This Month Bill:", col1X, y, boldTextPaint)
        canvas.drawText(String.format("+ ₹ %.2f", totalBill), col1X + 90f, y, amountPaint)

        canvas.drawText("Paid in Month:", col2X, y, boldTextPaint)
        canvas.drawText(String.format("- ₹ %.2f", totalPayInMonth), col2X + 95f, y, textPaint)

        if (postMonthTotal > 0) {
            y += 18f
            val lastPay = postMonthPayments.lastOrNull()
            val payDateStr = lastPay?.date ?: "Next Month"
            canvas.drawText("Next Month Payment:", col1X, y, boldTextPaint)
            val postPayPaint = Paint(amountPaint).apply { color = colorGreen }
            canvas.drawText(String.format("- ₹ %.2f (%s)", postMonthTotal, payDateStr), col1X + 115f, y, postPayPaint)
        }

        y += 22f
        canvas.drawLine(MARGIN + 10f, y - 8f, PAGE_WIDTH - MARGIN - 10f, y - 8f, borderPaint)

        val finalPaint = Paint(boldTextPaint).apply { textSize = 12f; color = colorPrimaryDark }
        canvas.drawText("FINAL DUE AMOUNT:", col1X, y + 5f, finalPaint)

        val finalAmtPaint = Paint(amountPaint).apply {
            textSize = 14f
            textAlign = Paint.Align.RIGHT
            color = if (finalDue > 0) colorRed else colorGreen
        }
        canvas.drawText(String.format("₹ %.2f", finalDue), PAGE_WIDTH - MARGIN - 110f, y + 5f, finalAmtPaint)

        // Status Stamp: [ PAID ] or [ DUE ]
        val stampText = if (finalDue <= 0.0) "PAID" else "PENDING"
        val stampColor = if (finalDue <= 0.0) colorGreen else colorRed

        val stampBoxPaint = Paint(borderPaint).apply {
            color = stampColor
            strokeWidth = 1.5f
        }
        val stampTextPaint = Paint(boldTextPaint).apply {
            color = stampColor
            textSize = 11f
            textAlign = Paint.Align.CENTER
        }
        val stampLeft = PAGE_WIDTH - MARGIN - 90f
        val stampTop = y - 8f
        canvas.drawRoundRect(stampLeft, stampTop, PAGE_WIDTH - MARGIN - 10f, stampTop + 20f, 4f, 4f, stampBoxPaint)
        canvas.drawText(stampText, stampLeft + 40f, stampTop + 14f, stampTextPaint)

        // Watermark Footer
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorTextLight
            textSize = 9f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("Generated by MilkManager • Powered by Ignishers (ignishers.in)", PAGE_WIDTH / 2f, PAGE_HEIGHT - 20f, footerPaint)
    }

    private fun drawRowHeaders(canvas: Canvas, y: Float, startX: Float, colWidths: FloatArray) {
        var curX = startX
        val headers = arrayOf("Date", "Morning", "Evening", "Payment")
        for (i in headers.indices) {
            canvas.drawText(headers[i], curX + 6f, y, headerPaint)
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
        val dateDay = String.format("%02d", day)
        canvas.drawText(dateDay, curX + 8f, rowY + 16f, boldTextPaint)
        curX += colWidths[0]

        // 2. Morning Milk
        if (d.mornQ > 0 || d.mornA > 0) {
            canvas.drawText(formatMilk(d.mornQ), curX + 6f, rowY + 11f, textPaint)
            canvas.drawText(String.format("₹%.0f", d.mornA), curX + 6f, rowY + 21f, amountPaint)
        } else {
            canvas.drawText("-", curX + 24f, rowY + 16f, smallTextPaint)
        }
        curX += colWidths[1]

        // 3. Evening Milk
        if (d.eveQ > 0 || d.eveA > 0) {
            canvas.drawText(formatMilk(d.eveQ), curX + 6f, rowY + 11f, textPaint)
            canvas.drawText(String.format("₹%.0f", d.eveA), curX + 6f, rowY + 21f, amountPaint)
        } else {
            canvas.drawText("-", curX + 24f, rowY + 16f, smallTextPaint)
        }
        curX += colWidths[2]

        // 4. Payment
        if (d.payA > 0) {
            val payPaint = Paint(amountPaint).apply { color = colorGreen }
            canvas.drawText(String.format("₹%.0f", d.payA), curX + 6f, rowY + 16f, payPaint)
        } else {
            canvas.drawText("-", curX + 28f, rowY + 16f, smallTextPaint)
        }
    }

    private fun drawEmptyDayCell(canvas: Canvas, rowY: Float, startX: Float, colWidths: FloatArray) {
        var curX = startX
        for (i in colWidths.indices) {
            canvas.drawText("-", curX + 12f, rowY + 16f, smallTextPaint)
            curX += colWidths[i]
        }
    }

    private fun formatMilk(qty: Double): String {
        return if (qty >= 1.0) String.format("%.1f L", qty)
        else String.format("%d ml", (qty * 1000).toInt())
    }

    companion object {
        const val PAGE_WIDTH = 595
        const val PAGE_HEIGHT = 842
        const val MARGIN = 18f

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