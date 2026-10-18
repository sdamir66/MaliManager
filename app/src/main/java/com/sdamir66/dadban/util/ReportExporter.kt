package com.sdamir66.dadban.util

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.sdamir66.dadban.displayUnit
import com.sdamir66.dadban.data.Account
import com.sdamir66.dadban.data.Transaction
import java.io.File

object ReportExporter {
    fun shareAccounts(context: Context, accounts: List<Account>, title: String = "گزارش حساب‌ها", balances: Map<Long, Double> = emptyMap()) {
        shareFiles(context, title, accountsHtml(accounts, balances), "accounts.xls", "application/vnd.ms-excel", accountsPdf(accounts, title, balances))
    }

    fun shareTransactions(context: Context, account: Account, transactions: List<Transaction>, title: String = "گزارش تراکنش‌ها") {
        shareFiles(context, title, transactionsHtml(account, transactions), "transactions.xls", "application/vnd.ms-excel", transactionsPdf(account, transactions, title))
    }

    private fun shareFiles(context: Context, title: String, excel: String, excelName: String, mime: String, pdf: ByteArray) {
        val dir = File(context.cacheDir, "reports").apply { mkdirs() }
        val excelFile = File(dir, excelName).apply { writeText(excel, Charsets.UTF_8) }
        val pdfFile = File(dir, excelName.removeSuffix(".xls") + ".pdf").apply { writeBytes(pdf) }
        val excelUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", excelFile)
        val pdfUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", pdfFile)
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, arrayListOf(excelUri, pdfUri))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "اشتراک‌گذاری گزارش"))
    }

    private fun accountsHtml(accounts: List<Account>, balances: Map<Long, Double>): String = buildString {
        append("<html><meta charset='utf-8'><table><tr><th>حساب</th><th>ارز</th><th>مانده</th><th>ماهیت</th><th>نرخ</th></tr>")
        accounts.forEach { append("<tr><td>${esc(it.name)}</td><td>${esc(it.displayUnit())}</td><td>${balances[it.id] ?: 0.0}</td><td>${if (it.nature == "debit") "بدهکار" else "بستانکار"}</td><td>${it.manualRate}</td></tr>") }
        append("</table></html>")
    }

    private fun transactionsHtml(account: Account, transactions: List<Transaction>): String = buildString {
        append("<html><meta charset='utf-8'><table><tr><th>تاریخ</th><th>نوع</th><th>مبلغ</th><th>یادداشت</th></tr>")
        transactions.forEach { append("<tr><td>${Jalali.format(it.dateMillis)}</td><td>${it.type}</td><td>${it.amount}</td><td>${esc(it.note)}</td></tr>") }
        append("</table></html>")
    }

    private fun accountsPdf(accounts: List<Account>, title: String, balances: Map<Long, Double>) = pdf(title, accounts.map { "${it.name} | ${money(balances[it.id] ?: 0.0)} ${it.displayUnit()} | ${if (it.nature == "debit") "بدهکار" else "بستانکار"}" })
    private fun transactionsPdf(account: Account, transactions: List<Transaction>, title: String) = pdf(title, transactions.map { "${Jalali.format(it.dateMillis)} | ${it.type} | ${money(it.amount)} | ${it.note}" })

    private fun pdf(title: String, rows: List<String>): ByteArray {
        val doc = PdfDocument()
        val paint = Paint().apply { textSize = 12f; color = android.graphics.Color.BLACK }
        val page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
        var y = 40f
        page.canvas.drawText(title, 40f, y, paint); y += 28f
        rows.take(28).forEach { row -> page.canvas.drawText(row.take(95), 40f, y, paint); y += 24f }
        doc.finishPage(page)
        return java.io.ByteArrayOutputStream().use { out -> doc.writeTo(out); doc.close(); out.toByteArray() }
    }

    private fun esc(value: String) = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
