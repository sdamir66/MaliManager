package com.sdamir66.dadban.calendar.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.sdamir66.dadban.ui.theme.HeaderBlue
import com.sdamir66.dadban.util.Jalali
import java.util.Calendar
import java.util.Locale

@Composable
fun CalendarDateConverter(selectedDateMillis: Long) {
    val selected = Jalali.toJalaliPublic(selectedDateMillis)
    var jalali by remember { mutableStateOf("%04d/%02d/%02d".format(Locale.US, selected[0], selected[1], selected[2])) }
    val converted = remember(jalali) {
        val millis = Jalali.parse(jalali)
        if (millis == null) null else Calendar.getInstance().apply { timeInMillis = millis }
    }
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("تبدیل تاریخ", style = MaterialTheme.typography.titleMedium, color = HeaderBlue)
            Text("تاریخ جلالی را وارد کن تا معادل میلادی آن نمایش داده شود.", style = MaterialTheme.typography.bodySmall, color = Color(0xFF5C5D72))
            OutlinedTextField(
                value = jalali,
                onValueChange = { jalali = it.filter { c -> c.isDigit() || c == '/' }.take(10) },
                label = { Text("جلالی (۱۴۰۵/۰۱/۰۱)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            if (converted != null) {
                Text(
                    "میلادی: %04d/%02d/%02d".format(Locale.US, converted.get(Calendar.YEAR), converted.get(Calendar.MONTH) + 1, converted.get(Calendar.DAY_OF_MONTH)),
                    color = Color(0xFF1B1B1F)
                )
                Text("تاریخ انتخاب‌شده: ${Jalali.format(selectedDateMillis).substringBefore(" ")}", style = MaterialTheme.typography.bodySmall, color = HeaderBlue)
            } else {
                Text("تاریخ جلالی نامعتبر است.", color = Color(0xFFC62828), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
