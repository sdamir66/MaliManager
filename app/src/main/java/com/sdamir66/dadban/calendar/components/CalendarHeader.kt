package com.sdamir66.dadban.calendar.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.msarhan.ummalqura.calendar.UmmalquraCalendar
import com.sdamir66.dadban.calendar.data.CalendarSettings
import com.sdamir66.dadban.calendar.data.CalendarType
import com.sdamir66.dadban.ui.theme.HeaderBlue
import com.sdamir66.dadban.util.Jalali
import java.util.Calendar
import java.util.Date

@Composable
fun CalendarHeader(
    currentDate: Date,
    primaryCalendar: CalendarType,
    settings: CalendarSettings?,
    onDateChange: (Date) -> Unit
) {
    var showYearPicker by remember { mutableStateOf(false) }

    Box(
        Modifier
            .fillMaxWidth()
            .background(
                color = HeaderBlue,
                shape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)
            )
            .statusBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 36.dp)
    ) {
        Column {
            // ═══ ردیف اول: ماه + سال (کلیک روی سال) ═══
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    getMonthName(currentDate, primaryCalendar, settings),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    getYear(currentDate, primaryCalendar, settings).toString(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.clickable { showYearPicker = true }
                )
            }

            Spacer(Modifier.height(8.dp))

            // ═══ ردیف دوم: سه تقویم (قابل کلیک) ═══
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CalendarTypeChip(
                    label = getChipLabel(currentDate, CalendarType.JALALI, settings),
                    isSelected = primaryCalendar == CalendarType.JALALI,
                    onClick = { onDateChange(currentDate) }
                )
                Spacer(Modifier.width(6.dp))
                CalendarTypeChip(
                    label = getChipLabel(currentDate, CalendarType.HIJRI, settings),
                    isSelected = primaryCalendar == CalendarType.HIJRI,
                    onClick = { onDateChange(currentDate) }
                )
                Spacer(Modifier.width(6.dp))
                CalendarTypeChip(
                    label = getChipLabel(currentDate, CalendarType.GREGORIAN, settings),
                    isSelected = primaryCalendar == CalendarType.GREGORIAN,
                    onClick = { onDateChange(currentDate) }
                )
            }
        }
    }

    // ═══ Dialog انتخاب سال ═══
    if (showYearPicker) {
        YearPickerDialog(
            currentYear = getYear(currentDate, primaryCalendar, settings),
            primaryCalendar = primaryCalendar,
            onYearSelected = { year ->
                onDateChange(setYear(currentDate, year, primaryCalendar))
                showYearPicker = false
            },
            onDismiss = { showYearPicker = false }
        )
    }
}

@Composable
private fun CalendarTypeChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        color = if (isSelected) Color.White else Color.White.copy(alpha = 0.2f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) HeaderBlue else Color.White,
            textAlign = TextAlign.Center,
            lineHeight = 14.sp,
            fontSize = 10.sp
        )
    }
}

// ═══════════════════════════════════════════════════════
// توابع کمکی
// ═══════════════════════════════════════════════════════

private fun getMonthName(date: Date, type: CalendarType, settings: CalendarSettings?): String {
    return when (type) {
        CalendarType.JALALI -> {
            val j = Jalali.toJalaliPublic(date.time)
            Jalali.monthName(j[1])
        }
        CalendarType.GREGORIAN -> {
            val cal = Calendar.getInstance().apply { time = date }
            gregorianMonthName(cal.get(Calendar.MONTH) + 1)
        }
        CalendarType.HIJRI -> {
            val h = getHijriDate(date, settings)
            hijriMonthName(h[1])
        }
    }
}

private fun getYear(date: Date, type: CalendarType, settings: CalendarSettings?): Int {
    return when (type) {
        CalendarType.JALALI -> Jalali.toJalaliPublic(date.time)[0]
        CalendarType.GREGORIAN -> Calendar.getInstance().apply { time = date }.get(Calendar.YEAR)
        CalendarType.HIJRI -> getHijriDate(date, settings)[0]
    }
}

private fun getChipLabel(date: Date, type: CalendarType, settings: CalendarSettings?): String {
    return when (type) {
        CalendarType.JALALI -> {
            val j = Jalali.toJalaliPublic(date.time)
            val nextM = if (j[1] == 12) 1 else j[1] + 1
            "${j[0]}/${j[1].toString().padStart(2, '0')}/${j[2].toString().padStart(2, '0')}\n" +
            "${Jalali.monthName(j[1])} - ${Jalali.monthName(nextM)}"
        }
        CalendarType.GREGORIAN -> {
            val cal = Calendar.getInstance().apply { time = date }
            val nextMonth = (cal.get(Calendar.MONTH) + 1) % 12
            "${cal.get(Calendar.YEAR)}/${(cal.get(Calendar.MONTH) + 1).toString().padStart(2, '0')}/${cal.get(Calendar.DAY_OF_MONTH).toString().padStart(2, '0')}\n" +
            "${gregorianMonthName(cal.get(Calendar.MONTH) + 1)} - ${gregorianMonthName(nextMonth + 1)} ${cal.get(Calendar.YEAR)}"
        }
        CalendarType.HIJRI -> {
            val h = getHijriDate(date, settings)
            val nextM = if (h[1] == 12) 1 else h[1] + 1
            "${h[0]}/${h[1].toString().padStart(2, '0')}/${h[2].toString().padStart(2, '0')}\n" +
            "${hijriMonthName(h[1])} - ${hijriMonthName(nextM)} ${h[0]}"
        }
    }
}

private fun setYear(date: Date, year: Int, type: CalendarType): Date {
    return when (type) {
        CalendarType.JALALI -> {
            val j = Jalali.toJalaliPublic(date.time)
            val millis = Jalali.parse("%04d/%02d/%02d".format(year, j[1], j[2]))
            Date(millis ?: date.time)
        }
        CalendarType.GREGORIAN -> {
            Calendar.getInstance().apply {
                time = date
                set(Calendar.YEAR, year)
            }.time
        }
        CalendarType.HIJRI -> date
    }
}

// ═══════════════════════════════════════════════════════
// تبدیل قمری (با UmmalquraCalendar + اصلاح eidFitrOffset)
// ═══════════════════════════════════════════════════════

fun getHijriDate(date: Date, settings: CalendarSettings?): IntArray {
    // ═══ ۱. محاسبه‌ی قمری پایه با UmmalquraCalendar ═══
    val cal = UmmalquraCalendar()
    cal.time = date

    var year = cal.get(UmmalquraCalendar.YEAR)
    var month = cal.get(UmmalquraCalendar.MONTH) + 1
    var day = cal.get(UmmalquraCalendar.DAY_OF_MONTH)

    // ═══ ۲. اعمال اصلاح eidFitrOffset (اگه تنظیم شده) ═══
    if (settings != null && settings.eidFitrOffset != 0) {
        // فقط برای ماه‌های بعد از شوال (۱۰) اعمال کن
        if (month >= 10) {
            // اگه سال قمری با سال اصلاح فرق داره، اصلاح نکن
            if (settings.eidFitrHijriYear == null || year == settings.eidFitrHijriYear) {
                val adjusted = UmmalquraCalendar()
                adjusted.time = date
                adjusted.add(UmmalquraCalendar.DAY_OF_MONTH, settings.eidFitrOffset)
                year = adjusted.get(UmmalquraCalendar.YEAR)
                month = adjusted.get(UmmalquraCalendar.MONTH) + 1
                day = adjusted.get(UmmalquraCalendar.DAY_OF_MONTH)
            }
        }
    }

    return intArrayOf(year, month, day)
}

fun hijriMonthName(m: Int): String {
    return listOf(
        "محرم", "صفر", "ربیع‌الاول", "ربیع‌الثانی",
        "جمادی‌الاول", "جمادی‌الثانی", "رجب", "شعبان",
        "رمضان", "شوال", "ذی‌القعده", "ذی‌الحجه"
    )[(m - 1).coerceIn(0, 11)]
}

private fun gregorianMonthName(m: Int): String {
    return listOf(
        "ژانویه", "فوریه", "مارس", "آوریل", "مه", "ژوئن",
        "ژوئیه", "اوت", "سپتامبر", "اکتبر", "نوامبر", "دسامبر"
    )[(m - 1).coerceIn(0, 11)]
}
