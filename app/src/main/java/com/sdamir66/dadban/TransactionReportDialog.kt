package com.sdamir66.dadban

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.sdamir66.dadban.data.Account
import com.sdamir66.dadban.data.Transaction
import com.sdamir66.dadban.util.ReportExporter

@Composable
fun TransactionReportDialog(
    account: Account,
    transactions: List<Transaction>,
    fromDate: String,
    toDate: String,
    search: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("📊 گزارش تراکنش‌ها") },
        text = {
            Column {
                Text("بازه: ${fromDate.ifBlank { "ابتدا" }} تا ${toDate.ifBlank { "انتها" }}")
                Text("جستجو: ${search.ifBlank { "بدون جستجو" }}")
                Text("تعداد: ${transactions.size} تراکنش")
            }
        },
        confirmButton = {
            Row {
                Button(onClick = { ReportExporter.shareTransactions(context, account, transactions, ReportExporter.Format.PDF) }) { Text("📄 PDF") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { ReportExporter.shareTransactions(context, account, transactions, ReportExporter.Format.EXCEL) }) { Text("📊 Excel") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )
}
