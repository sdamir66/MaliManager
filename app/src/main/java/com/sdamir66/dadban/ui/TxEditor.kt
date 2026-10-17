package com.sdamir66.dadban.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.sdamir66.dadban.data.Transaction
import com.sdamir66.dadban.ui.theme.HeaderBlue
import com.sdamir66.dadban.util.Jalali
import com.sdamir66.dadban.util.money
import java.util.Locale

private fun formatDecimalTextFieldValue(input: TextFieldValue): TextFieldValue {
    val text = input.text.replace(",", "")
    if (text.isEmpty()) return input.copy(text = "")

    if (text.count { it == '.' } > 1) return input

    return try {
        val parts = text.split(".")
        val intPart = parts[0].filter { it.isDigit() }
        val formattedInt = if (intPart.isEmpty()) "0"
                           else java.text.NumberFormat.getNumberInstance(Locale.US).format(intPart.toLong())

        val newText = if (parts.size > 1) {
            "$formattedInt.${parts[1].filter { it.isDigit() }.take(2)}"
        } else {
            formattedInt
        }

        input.copy(
            text = newText,
            selection = androidx.compose.ui.text.TextRange(newText.length)
        )
    } catch (e: Exception) {
        input
    }
}

private fun parseDecimalAmount(input: String): Double? {
    val cleaned = input.replace(",", "")
    return if (cleaned.isEmpty()) null else cleaned.toDoubleOrNull()
}

@Composable
fun TxEditor(old: Transaction?, accountId: Long, onSave: (Transaction) -> Unit, onCancel: () -> Unit) {
    var type by remember(old) { mutableStateOf(old?.type ?: "بدهکار") }
    var amountValue by remember(old) {
        mutableStateOf(TextFieldValue(text = old?.amount?.let { money(it) } ?: ""))
    }
    var note by remember(old) { mutableStateOf(old?.note ?: "") }
    var dateMillis by remember(old) {
        mutableStateOf(old?.dateMillis ?: System.currentTimeMillis())
    }
    var showCalendar by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    val textColor = Color(0xFF1B1B1F)
    val labelColor = Color(0xFF5C5D72)
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = textColor,
        unfocusedTextColor = textColor,
        focusedBorderColor = HeaderBlue,
        unfocusedBorderColor = Color(0xFFCCCCCC),
        focusedLabelColor = HeaderBlue,
        unfocusedLabelColor = labelColor,
        cursorColor = HeaderBlue
    )

    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = Color.White,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.border(2.dp, HeaderBlue, RoundedCornerShape(20.dp)),
        title = { Text(if (old == null) "ثبت تراکنش" else "ویرایش تراکنش", fontWeight = FontWeight.Bold, color = HeaderBlue) },
        text = {
            Column {
                Row {
                    FilterChip(
                        selected = type == "بدهکار",
                        onClick = { type = "بدهکار" },
                        label = { Text("بدهکار") },
                        colors = FilterChipDefaults.filterChipColors(
                            labelColor = labelColor,
                            selectedLabelColor = Color.White,
                            selectedContainerColor = HeaderBlue,
                            containerColor = Color(0xFFF0F1F7)
                        )
                    )
                    Spacer(Modifier.width(8.dp))
                    FilterChip(
                        selected = type == "بستانکار",
                        onClick = { type = "بستانکار" },
                        label = { Text("بستانکار") },
                        colors = FilterChipDefaults.filterChipColors(
                            labelColor = labelColor,
                            selectedLabelColor = Color.White,
                            selectedContainerColor = HeaderBlue,
                            containerColor = Color(0xFFF0F1F7)
                        )
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    amountValue,
                    { amountValue = formatDecimalTextFieldValue(it) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    label = { Text("مبلغ") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors
                )
                Spacer(Modifier.height(8.dp))

                OutlinedButton(
                    onClick = { showCalendar = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = HeaderBlue)
                ) {
                    Icon(Icons.Default.DateRange, null, modifier = Modifier.size(18.dp), tint = HeaderBlue)
                    Spacer(Modifier.width(8.dp))
                    Text(Jalali.format(dateMillis).substringBefore(" "), color = HeaderBlue)
                }
                Spacer(Modifier.height(8.dp))

                OutlinedButton(
                    onClick = { showTimePicker = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = HeaderBlue)
                ) {
                    val cal = java.util.Calendar.getInstance().apply { timeInMillis = dateMillis }
                    Text(
                        "⏰  %02d:%02d".format(
                            cal.get(java.util.Calendar.HOUR_OF_DAY),
                            cal.get(java.util.Calendar.MINUTE)
                        ),
                        color = HeaderBlue
                    )
                }

                Spacer(Modifier.height(8.dp))
                OutlinedTextField(note, { note = it }, label = { Text("شرح") }, modifier = Modifier.fillMaxWidth(), colors = fieldColors)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val n = parseDecimalAmount(amountValue.text)
                    if (n != null && n > 0)
                        onSave(Transaction(old?.id ?: 0, accountId, dateMillis, type, n, note, false, null))
                },
                colors = ButtonDefaults.buttonColors(containerColor = HeaderBlue, contentColor = Color.White)
            ) { Text("ذخیره", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("انصراف", color = HeaderBlue) } }
    )

    if (showCalendar) {
        val j = Jalali.toJalaliPublic(dateMillis)
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = dateMillis }
        JalaliCalendarDialog(
            initialYear = j[0],
            initialMonth = j[1],
            initialDay = j[2],
            onSelect = { y, m, d ->
                val newCal = java.util.Calendar.getInstance().apply {
                    timeInMillis = Jalali.parse("%04d/%02d/%02d".format(Locale.US, y, m, d)) ?: 0L
                    set(java.util.Calendar.HOUR_OF_DAY, cal.get(java.util.Calendar.HOUR_OF_DAY))
                    set(java.util.Calendar.MINUTE, cal.get(java.util.Calendar.MINUTE))
                    set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }
                dateMillis = newCal.timeInMillis
                showCalendar = false
            },
            onCancel = { showCalendar = false }
        )
    }

    if (showTimePicker) {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = dateMillis }
        android.app.TimePickerDialog(
            LocalContext.current,
            { _, hour, minute ->
                val newCal = java.util.Calendar.getInstance().apply {
                    timeInMillis = dateMillis
                    set(java.util.Calendar.HOUR_OF_DAY, hour)
                    set(java.util.Calendar.MINUTE, minute)
                    set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }
                dateMillis = newCal.timeInMillis
                showTimePicker = false
            },
            cal.get(java.util.Calendar.HOUR_OF_DAY),
            cal.get(java.util.Calendar.MINUTE),
            true
        ).show()
    }
}
