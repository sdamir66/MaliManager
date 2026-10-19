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
        rows += Row("جمع", "", rows.sumOf { it.credit }, rows.sumOf { it.debit }, running)
        return rows
    }

    private fun toHtml(rows: List<Row>): String = buildString {
        append("<html><meta charset='utf-8'><body dir='ltr'><table border='1'><tr><th>تاریخ</th><th>شرح</th><th>بستانکار</th><th>بدهکار</th><th>مانده</th></tr>")
        rows.forEach { append("<tr><td>${esc(it.date)}</td><td>${esc(it.note)}</td><td>${it.credit}</td><td>${it.debit}</td><td>${it.balance}</td></tr>") }
        append("</table></body></html>")
    }

    private fun toPdf(rows: List<Row>): ByteArray {
        val document = PdfDocument()
        val paint = Paint().apply { textSize = 9f; color = android.graphics.Color.BLACK; typeface = android.graphics.Typeface.DEFAULT }
        var pageNumber = 1
        var page = document.startPage(PdfDocument.PageInfo.Builder(842, 595, pageNumber).create())
        var y = 32f
        page.canvas.drawText("تاریخ | شرح | بستانکار | بدهکار | مانده", 24f, y, paint)
        y += 24f
        rows.forEach { row ->
            if (y > 560f) {
                document.finishPage(page)
                page = document.startPage(PdfDocument.PageInfo.Builder(842, 595, ++pageNumber).create())
                y = 32f
            }
            val line = "${row.date} | ${row.note} | ${num(row.credit)} | ${num(row.debit)} | ${num(row.balance)}"
            page.canvas.drawText(line.take(130), 24f, y, paint)
            y += 19f
        }
        document.finishPage(page)
        return java.io.ByteArrayOutputStream().use { out -> document.writeTo(out); document.close(); out.toByteArray() }
    }

    private fun num(value: Double) = NumberFormat.getNumberInstance(Locale.US).format(value)
    private fun esc(value: String) = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
