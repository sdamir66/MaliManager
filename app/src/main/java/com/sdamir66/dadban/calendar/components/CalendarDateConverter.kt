package com.sdamir66.dadban.calendar.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import com.sdamir66.dadban.calendar.data.HijriCache
import com.sdamir66.dadban.ui.theme.HeaderBlue
import com.sdamir66.dadban.util.Jalali
import java.util.Calendar
import java.util.Locale

private val converterText = Color(0xFF1B1B1F)
private val converterLabel = Color(0xFF4B4D61)
private val converterBorder = Color(0xFF9AA3B2)

@Composable
fun CalendarDateConverter(
    selectedDateMillis: Long,
    hijriCacheMap: Map<String, HijriCache>,
    hijriCacheByHijriDate: Map<String, HijriCache>
) {
    val selectedJ = Jalali.toJalaliPublic(selectedDateMillis)
    val selectedG = Calendar.getInstance().apply { timeInMillis = selectedDateMillis }
    val selectedHijri = hijriCacheMap["%04d/%02d/%02d".format(Locale.US, selectedJ[0], selectedJ[1], selectedJ[2])]

    var jalali by remember(selectedDateMillis) {
        mutableStateOf("%04d/%02d/%02d".format(Locale.US, selectedJ[0], selectedJ[1], selectedJ[2]))
    }
    var gregorian by remember(selectedDateMillis) {
        mutableStateOf("%04d/%02d/%02d".format(Locale.US, selectedG.get(Calendar.YEAR), selectedG.get(Calendar.MONTH) + 1, selectedG.get(Calendar.DAY_OF_MONTH)))
    }
    var hijri by remember(selectedDateMillis, selectedHijri) {
        mutableStateOf(selectedHijri?.let { "%04d/%02d/%02d".format(Locale.US, it.hijriYear, hijriMonthNameToNumber(it.hijriMonth), it.hijriDay) } ?: "")
    }
    var error by remember { mutableStateOf("") }

    fun applyMillis(millis: Long?) {
        if (millis == null) {
            error = "تاریخ واردشده معتبر نیست یا در داده‌های تقویم موجود نیست."
            return
        }
        error = ""
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        val j = Jalali.toJalaliPublic(millis)
        val h = hijriCacheMap["%04d/%02d/%02d".format(Locale.US, j[0], j[1], j[2])]
        jalali = "%04d/%02d/%02d".format(Locale.US, j[0], j[1], j[2])
        gregorian = "%04d/%02d/%02d".format(Locale.US, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
        hijri = h?.let { "%04d/%02d/%02d".format(Locale.US, it.hijriYear, hijriMonthNameToNumber(it.hijriMonth), it.hijriDay) } ?: ""
    }

    fun parseGregorian(value: String): Long? = try {
        val parts = value.split("/").map { it.toInt() }
        if (parts.size != 3) null else Calendar.getInstance().apply {
            isLenient = false
            set(parts[0], parts[1] - 1, parts[2], 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    } catch (_: Exception) { null }

    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text("تبدیل تاریخ", style = MaterialTheme.typography.titleMedium, color = HeaderBlue)
            Text("هر ردیف را تغییر بده؛ دو ردیف دیگر هم‌زمان تبدیل می‌شوند.", style = MaterialTheme.typography.bodySmall, color = converterLabel)
            ConverterField("جلالی", jalali, { value ->
                jalali = value
                applyMillis(Jalali.parse(value))
            })
            ConverterField("میلادی", gregorian, { value ->
                gregorian = value
                applyMillis(parseGregorian(value))
            })
            ConverterField("قمری", hijri, { value ->
                hijri = value
                val parts = value.split("/")
                val cache = if (parts.size == 3) hijriCacheByHijriDate["${parts[0].toIntOrNull()}/${parts[1].toIntOrNull()}/${parts[2].toIntOrNull()}"] else null
                applyMillis(cache?.let { Jalali.parse(it.jalaliDate) })
            })
            if (error.isNotBlank()) Text(error, color = Color(0xFFC62828), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ConverterField(label: String, value: String, onValueChange: (String) -> Unit) {
    var fieldValue by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    LaunchedEffect(value) {
        if (fieldValue.text != value) fieldValue = TextFieldValue(value, TextRange(value.length))
    }
    OutlinedTextField(
        value = fieldValue,
        onValueChange = {
            val formatted = formatDateDigits(it)
            fieldValue = TextFieldValue(formatted.first, TextRange(formatted.second))
            onValueChange(formatted.first)
        },
        label = { Text(label, color = converterLabel) },
        placeholder = { Text("سال/ماه/روز", color = converterLabel.copy(alpha = 0.65f)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = converterText,
            unfocusedTextColor = converterText,
            focusedLabelColor = HeaderBlue,
            unfocusedLabelColor = converterLabel,
            focusedBorderColor = HeaderBlue,
            unfocusedBorderColor = converterBorder,
            cursorColor = HeaderBlue
        )
    )
}

private fun formatDateDigits(input: TextFieldValue): Pair<String, Int> {
    val digitsBeforeCursor = input.text.take(input.selection.start).count { it.isDigit() }
    val normalized = input.text.map { ch ->
        when (ch) {
            in '۰'..'۹' -> ('0'.code + (ch.code - '۰'.code)).toChar()
            in '٠'..'٩' -> ('0'.code + (ch.code - '٠'.code)).toChar()
            else -> ch
        }
    }.filter { it.isDigit() }.take(8)
    val result = buildString {
        normalized.forEachIndexed { index, ch ->
            if (index == 4 || index == 6) append('/')
            append(ch)
        }
    }
    if (digitsBeforeCursor >= normalized.size) return result to result.length
    var seen = 0
    val cursor = result.indexOfFirst { ch ->
        if (ch.isDigit()) seen++
        seen == digitsBeforeCursor
    }
    return result to if (cursor >= 0) cursor + 1 else result.length
}
