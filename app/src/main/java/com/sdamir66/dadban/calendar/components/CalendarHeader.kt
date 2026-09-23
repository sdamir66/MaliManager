package com.sdamir66.dadban.calendar.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sdamir66.dadban.calendar.data.CalendarType
import com.sdamir66.dadban.ui.theme.HeaderBlue
import com.sdamir66.dadban.util.Jalali
import java.util.Calendar
import java.util.Date

@Composable
fun CalendarHeader(
    currentDate: Date,
    primaryCalendar: CalendarType,
    onPrevYear: () -> Unit,
    onNextYear: () -> Unit,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onCalendarTypeChange: (CalendarType) -> Unit,
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
            // ═══ ردیف اول: فلش‌های سال + ماه/سال ═══
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // ◄◄ سال قبل
                IconButton(onClick = onPrevYear) {
                    Text("◄◄", color = Color.White, fontSize = 14.sp)
                }

                // نام ماه + سال (کلیک روی سال)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { showYearPicker = true }
                ) {
                    Text(
                        getMonthName(currentDate, primaryCalendar),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        getYear(currentDate, primaryCalendar).toString(),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                }

                // ►► سال بعد
                IconButton(onClick = onNextYear) {
                    Text("►►", color = Color.White, fontSize = 14.sp)
                }
            }

            Spacer(Modifier.height(8.dp))

            // ═══ ردیف دوم: سه تقویم (قابل کلیک) ═══
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CalendarTypeChip(
                    label = getChipLabel(currentDate, CalendarType.JALALI),
                    isSelected = primaryCalendar == CalendarType.JALALI,
                    onClick = { onCalendarTypeChange(CalendarType.JALALI) }
                )
                Spacer(Modifier.width(8.dp))
                CalendarTypeChip(
                    label = getChipLabel(currentDate, CalendarType.HIJRI),
                    isSelected = primaryCalendar == CalendarType.HIJRI,
                    onClick = { onCalendarTypeChange(CalendarType.HIJRI) }
                )
                Spacer(Modifier.width(8.dp))
                CalendarTypeChip(
                    label = getChipLabel(currentDate, CalendarType.GREGORIAN),
                    isSelected = primaryCalendar == CalendarType.GREGORIAN,
                    onClick = { onCalendarTypeChange(CalendarType.GREGORIAN) }
                )
            }
        }
    }

    // ═══ Dialog انتخاب سال ═══
    if (showYearPicker) {
        YearPickerDialog(
            currentYear = getYear(currentDate, primaryCalendar),
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
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) HeaderBlue else Color.White,
            textAlign = TextAlign.Center
        )
    }
}

// ═══════════════════════════════════════════════════════
// توابع کمکی
// ═══════════════════════════════════════════════════════

private fun getMonthName(date: Date, type: CalendarType): String {
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
            val h = getHijriDate(date)
            hijriMonthName(h[1])
        }
    }
}

private fun getYear(date: Date, type: CalendarType): Int {
    return when (type) {
        CalendarType.JALALI -> Jalali.toJalaliPublic(date.time)[0]
        CalendarType.GREGORIAN -> Calendar.getInstance().apply { time = date }.get(Calendar.YEAR)
        CalendarType.HIJRI -> getHijriDate(date)[0]
    }
}

/**
 * فرمت کوتاه برچسب: مثلاً 2026/10/29
 */
private fun getChipLabel(date: Date, type: CalendarType): String {
    return when (type) {
        CalendarType.JALALI -> {
            val j = Jalali.toJalaliPublic(date.time)
            "${j[0]}/${j[1].toString().padStart(2, '0')}/${j[2].toString().padStart(2, '0')}"
        }
        CalendarType.GREGORIAN -> {
            val cal = Calendar.getInstance().apply { time = date }
            "${cal.get(Calendar.YEAR)}/${(cal.get(Calendar.MONTH) + 1).toString().padStart(2, '0')}/${cal.get(Calendar.DAY_OF_MONTH).toString().padStart(2, '0')}"
        }
        CalendarType.HIJRI -> {
            val h = getHijriDate(date)
            "${h[0]}/${h[1].toString().padStart(2, '0')}/${h[2].toString().padStart(2, '0')}"
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
        CalendarType.HIJRI -> date  // TODO
    }
}

// ═══════════════════════════════════════════════════════
// تبدیل قمری
// ═══════════════════════════════════════════════════════

/**
 * تبدیل میلادی به قمری (محاسباتی)
 */
fun getHijriDate(date: Date): IntArray {
    val cal = Calendar.getInstance().apply { time = date }
    val gy = cal.get(Calendar.YEAR)
    val gm = cal.get(Calendar.MONTH) + 1
    val gd = cal.get(Calendar.DAY_OF_MONTH)

    // تبدیل میلادی به Julian Day
    var a = (14 - gm) / 12
    var y = gy + 4800 - a
    var m = gm + 12 * a - 3
    var jdn = gd + (153 * m + 2) / 5 + 365 * y + y / 4 - y / 100 + y / 400 - 32045

    // تبدیل Julian Day به قمری
    var l = jdn - 1948440 + 10632
    val n = (l - 1) / 10631
    l = l - 10631 * n + 354
    val j = ((10985 - l) / 5316) * ((50 * l) / 17719) + (l / 5670) * ((43 * l) / 15238)
    l = l - ((30 - j) / 15) * ((17719 * j) / 50) - (j / 16) * ((15238 * j) / 43) + 29

    val hMonth = (24 * l) / 709
    val hDay = l - (709 * hMonth) / 24
    val hYear = 30 * n + j - 30

    return intArrayOf(hYear, hMonth, hDay)
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
