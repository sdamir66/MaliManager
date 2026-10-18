package com.sdamir66.dadban

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sdamir66.dadban.ui.theme.HeaderBlue

@Composable
fun ReportPreviewDialog(
    title: String,
    rows: List<String>,
    onShare: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = HeaderBlue) },
        text = {
            Column {
                Text("پیش‌نمایش گزارش (${rows.size} ردیف)")
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 320.dp).padding(top = 8.dp)) {
                    items(rows.take(100)) { Text(it, modifier = Modifier.padding(vertical = 4.dp)) }
                }
            }
        },
        confirmButton = { Button(onClick = onShare) { Text("خروجی PDF و اکسل") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("بستن") } }
    )
}
