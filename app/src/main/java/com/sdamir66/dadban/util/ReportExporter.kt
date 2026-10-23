package com.sdamir66.dadban.util

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.sdamir66.dadban.data.Account
import com.sdamir66.dadban.data.Transaction
import java.io.File
import java.text.NumberFormat
import java.util.Locale

object ReportExporter {
    enum class Format { PDF, EXCEL }

    fun shareTransactions(context: Context, account: Account, transactions: List<Transaction>, format: Format) {
        val rows = buildRows(account, transactions)
        val dir = File(context.cacheDir, "reports").apply { mkdirs() }
        val file = when (format) {
            Format.EXCEL -> File(dir, "transactions.xls").apply { writeText(toHtml(rows), Charsets.UTF_8) }
            Format.PDF -> File(dir, "transactions.pdf").apply { writeBytes(toPdf(rows)) }
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val mime = if (format == Format.PDF) "application/pdf" else "application/vnd.ms-excel"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_SUBJECT, "گزارش تراکنش‌ها")
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "اشتراک گزارش"))
    }

    private data class Row(val date: String, val note: String, val credit: Double, val debit: Double, val balance: Double)

    private fun buildRows(account: Account, transactions: List<Transaction>): List<Row> {
        var running = 0.0
        val sorted = transactions.sortedWith(compareBy<Transaction> { it.dateMillis }.thenBy { it.id })
        val rows = sorted.map {
            val credit = if (it.type == "بستانکار") it.amount else 0.0
            val debit = if (it.type == "بدهکار") it.amount else 0.0
            running += if (account.nature == "debit") debit - credit else credit - debit
            Row(Jalali.format(it.dateMillis), it.note, credit, debit, running)
        }.toMutableList()
        rows += Row("", "جمع", rows.sumOf { it.credit }, rows.sumOf { it.debit }, running)
        return rows
    }

    private fun toHtml(rows: List<Row>): String = buildString {
        append("<html><head><meta charset='utf-8'><style>")
        append("body{font-family:Arial;direction:ltr}table{border-collapse:collapse;width:100%}th,td{border:1px solid #777;padding:6px;text-align:left}th{background:#F0F0F0;font-weight:bold}tr.total{background:#E0E0E0;font-weight:bold}")
        append("</style></head><body><table><tr><th>تاریخ</th><th>شرح</th><th>بستانکار</th><th>بدهکار</th><th>مانده</th></tr>")
        rows.forEachIndexed { index, row ->
            val cls = if (index == rows.lastIndex) " class='total'" else ""
            append("<tr$cls><td>${esc(row.date)}</td><td>${esc(row.note)}</td><td>${num(row.credit)}</td><td>${num(row.debit)}</td><td>${num(row.balance)}</td></tr>")
        }
        append("</table></body></html>")
    }

    private fun toPdf(rows: List<Row>): ByteArray {
        val document = PdfDocument()
        val pageWidth = 595f
        val pageHeight = 842f
        val left = 40f
        val right = 555f
        val widths = floatArrayOf(80f, 180f, 80f, 80f, 80f)
        val headers = arrayOf("تاریخ", "شرح", "بستانکار", "بدهکار", "مانده")
        val border = Paint().apply { isAntiAlias = true; color = android.graphics.Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 1f }
        val fill = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
        val text = Paint().apply { isAntiAlias = true; color = android.graphics.Color.BLACK; textSize = 11f; textAlign = Paint.Align.RIGHT }
        val title = Paint(text).apply { textSize = 20f; isFakeBoldText = true; textAlign = Paint.Align.LEFT }
        var pageNo = 1
        var page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth.toInt(), pageHeight.toInt(), pageNo).create())
        var y = 40f
        page.canvas.drawText("گزارش تراکنش‌ها", left, y, title)
        y += 40f
        page.canvas.drawText("تعداد: ${rows.size - 1} تراکنش", left, y, Paint(text).apply { textAlign = Paint.Align.LEFT; textSize = 12f })
        y += 24f
        fun drawTableHeader() {
            fill.color = android.graphics.Color.rgb(240, 240, 240)
            page.canvas.drawRect(left, y, right, y + 28f, fill)
            var x = left
            headers.forEachIndexed { i, h ->
                page.canvas.drawRect(x, y, x + widths[i], y + 28f, border)
                page.canvas.drawText(h, x + widths[i] - 5f, y + 18f, text)
                x += widths[i]
            }
            y += 28f
        }
        drawTableHeader()
        rows.forEachIndexed { index, row ->
            if (y + 28f > pageHeight - 40f) {
                document.finishPage(page)
                page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth.toInt(), pageHeight.toInt(), ++pageNo).create())
                y = 40f
                drawTableHeader()
            }
            fill.color = if (index == rows.lastIndex) android.graphics.Color.rgb(224, 224, 224) else android.graphics.Color.WHITE
            val values = arrayOf(row.date, row.note, num(row.credit), num(row.debit), num(row.balance))
            var x = left
            val rowText = Paint(text).apply { isFakeBoldText = index == rows.lastIndex }
            values.forEachIndexed { i, value ->
                page.canvas.drawRect(x, y, x + widths[i], y + 28f, fill)
                page.canvas.drawRect(x, y, x + widths[i], y + 28f, border)
                page.canvas.drawText(value.take(32), x + widths[i] - 5f, y + 18f, rowText)
                x += widths[i]
            }
            y += 28f
        }
        document.finishPage(page)
        return java.io.ByteArrayOutputStream().use { out -> document.writeTo(out); document.close(); out.toByteArray() }
    }

    private fun num(value: Double) = NumberFormat.getNumberInstance(Locale.US).format(value)
    private fun esc(value: String) = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
