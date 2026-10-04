package com.sdamir66.dadban.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sdamir66.dadban.ui.theme.DebitRed
import com.sdamir66.dadban.ui.theme.HeaderBlue

@Composable
fun ConfirmDeleteDialog(title: String, message: String, onConfirm: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = Color.White,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.border(2.dp, HeaderBlue, RoundedCornerShape(20.dp)),
        title = { Text(title, fontWeight = FontWeight.Bold, color = HeaderBlue) },
        text = { Text(message, color = Color(0xFF1B1B1F)) },
        confirmButton = {
            Button(onClick = onConfirm, colors = ButtonDefaults.buttonColors(containerColor = DebitRed)) { Text("حذف") }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("انصراف", color = HeaderBlue) } }
    )
}
