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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sdamir66.dadban.calendar.data.CalendarSettings
import com.sdamir66.dadban.calendar.data.CalendarType
import com.sdamir66.dadban.calendar.data.HijriCache
import com.sdamir66.dadban.ui.theme.HeaderBlue
import com.sdamir66.dadban.util.Jalali
import java.util.Calendar
import java.util.Date

@Composable
fun CalendarHeader(
    currentDate: Date,
    primaryCalendar: CalendarType,
    settings: CalendarSettings?,
    hijriCacheMap: Map<String, HijriCache>,
    onDateChange: (Date) -> Unit,
    onCalendarTypeChange: (CalendarType) -> Unit
) {
    var showYearPicker by remember { mutableStateOf(false) }

    // ✅ ماه و سال به‌صورت state (با تغییر currentDate آپدیت می‌شن)
    val monthName = remember(currentDate, primaryCalendar) {
        getMonthName(currentDate, primaryCalendar, hijriCacheMap)
    }
    val yearNumber = remember(currentDate, primaryCalendar) {
        getYear(currentDate, primaryCalendar, hijriCacheMap)
    }

    Box(
        Modifier
            .fillMaxWidth()
            .background(
                color = HeaderBlue,
                shape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)
            )
            .statusBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 0.dp)
    ) {
        Column {
            // ═══ ماه و سال ═══
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    monthName,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 22.sp
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    yearNumber.toString(),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 22.sp,
                    modifier = Modifier.clickable { showYearPicker = true }
                )
            }

            Spacer(Modifier.height(10.dp))

            // ═══ ترتیب LTR: میلادی (چپ) - جلالی (وسط) - قمری (راست) ═══
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    // ─── میلادی (چپ) ───
                    CalendarTypeChip(
                        label = getChipLabel(currentDate, CalendarType.GREGORIAN, hijriCacheMap, primaryCalendar),
                        isSelected = primaryCalendar == CalendarType.GREGORIAN,
                        onClick = { onCalendarTypeChange(CalendarType.GREGORIAN) },
                        modifier = Modifier.weight(1f)
                    )

                    // ─── جلالی (وسط) ───
                    CalendarTypeChip(
                        label = getChipLabel(currentDate, CalendarType.JALALI, hijriCacheMap, primaryCalendar),
                        isSelected = primaryCalendar == CalendarType.JALALI,
                        onClick = { onCalendarTypeChange(CalendarType.JALALI) },
                        modifier = Modifier.weight(1f)
                    )

                    // ─── قمری (راست) ───
                    CalendarTypeChip(
                        label = getChipLabel(currentDate, CalendarType.HIJRI, hijriCacheMap, primaryCalendar),
                        isSelected = primaryCalendar == CalendarType.HIJRI,
                        onClick = { onCalendarTypeChange(CalendarType.HIJRI) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }

    if (showYearPicker) {
        YearPickerDialog(
            currentYear = yearNumber,
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
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = if (isSelected) Color.White else Color.White.copy(alpha = 0.2f),
        shape = if (isSelected) {
            RoundedCornerShape(
                topStart = 14.dp,
                topEnd = 14.dp,
                bottomStart = 0.dp,
                bottomEnd = 0.dp
            )
        } else {
            RoundedCornerShape(14.dp)
        },
        modifier = modifier.clickable { onClick() }
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) HeaderBlue else Color.White,
            textAlign = TextAlign.Center,
            lineHeight = 16.sp,
            fontSize = 11.sp
        )
    }
}

// ═══════════════════════════════════════════════════════
// توابع کمکی
// ═══════════════════════════════════════════════════════

private fun getHijriFromCache(date: Date, cache: Map<String, HijriCache>): IntArray? {
    val j = Jalali.toJalaliPublic(date.time)
    val key = "%04d/%02d/%02d".format(j[0], j[1], j[2])
    val item = cache[key] ?: return null
    return intArrayOf(item.hijriYear, hijriMonthNameToNumber(item.hijriMonth), item.hijriDay)
}

fun hijriMonthNameToNumber(name: String): Int {
    val clean = name.trim()
        .replace("‌", "")
        .replace(" ", "")
        .replace("ي", "ی")
        .replace("ك", "ک")

    return when {
        clean.contains("محرم") -> 1
        clean.contains("صفر") -> 2
        clean.contains("ربیعالاول") || clean.contains("ربیعاول") -> 3
        clean.contains("ربیعالثانی") || clean.contains("ربیعثانی") -> 4
        clean.contains("جمادیالاول") || clean.contains("جمادیاول") || clean.contains("جماديالاولي") -> 5
        clean.contains("جمادیالثانی") || clean.contains("جمادیثانی") || clean.contains("جماديالثانيه") -> 6
        clean.contains("رجب") -> 7
        clean.contains("شعبان") -> 8
        clean.contains("رمضان") -> 9
        clean.contains("شوال") -> 10
        clean.contains("ذیالقعده") || clean.contains("ذوالقعده") || clean.contains("ذیقعده") -> 11
        clean.contains("ذیالحجه") || clean.contains("ذوالحجه") || clean.contains("ذیحجه") -> 12
        else -> 0
    }
}

private fun getMonthName(date: Date, type: CalendarType, cache: Map<String, HijriCache>): String {
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
            val h = getHijriFromCache(date, cache) ?: getHijriDate(date, null)
            hijriMonthName(h[1])
        }
    }
}

private fun getYear(date: Date, type: CalendarType, cache: Map<String, HijriCache>): Int {
    return when (type) {
        CalendarType.JALALI -> Jalali.toJalaliPublic(date.time)[0]
        CalendarType.GREGORIAN -> Calendar.getInstance().apply { time = date }.get(Calendar.YEAR)
        CalendarType.HIJRI -> (getHijriFromCache(date, cache) ?: getHijriDate(date, null))[0]
    }
}

private fun getChipLabel(
    date: Date,
    type: CalendarType,
    cache: Map<String, HijriCache>,
    primaryCalendar: CalendarType
): String {
    return when (type) {
        CalendarType.JALALI -> {
            val j = Jalali.toJalaliPublic(date.time)
            "${j[0]}/${j[1].toString().padStart(2, '0')}/${j[2].toString().padStart(2, '0')}\n" +
            getMonthRangeForPrimary(date, primaryCalendar, CalendarType.JALALI, cache)
        }
        CalendarType.GREGORIAN -> {
            val cal = Calendar.getInstance().apply { time = date }
            "${cal.get(Calendar.YEAR)}/${(cal.get(Calendar.MONTH) + 1).toString().padStart(2, '0')}/${cal.get(Calendar.DAY_OF_MONTH).toString().padStart(2, '0')}\n" +
            getMonthRangeForPrimary(date, primaryCalendar, CalendarType.GREGORIAN, cache)
        }
        CalendarType.HIJRI -> {
            val h = getHijriFromCache(date, cache) ?: getHijriDate(date, null)
            "${h[0]}/${h[1].toString().padStart(2, '0')}/${h[2].toString().padStart(2, '0')}\n" +
            getMonthRangeForPrimary(date, primaryCalendar, CalendarType.HIJRI, cache)
        }
    }
}

private fun getMonthRangeForPrimary(
    date: Date,
    primaryCalendar: CalendarType,
    targetCalendar: CalendarType,
    cache: Map<String, HijriCache>
): String {
    val (firstDay, lastDay) = getFirstAndLastDayOfMonth(date, primaryCalendar)

    return when (targetCalendar) {
        CalendarType.JALALI -> {
            val firstJ = Jalali.toJalaliPublic(firstDay.time)
            val lastJ = Jalali.toJalaliPublic(lastDay.time)
            if (firstJ[1] == lastJ[1]) {
                Jalali.monthName(firstJ[1])
            } else {
                "${Jalali.monthName(firstJ[1])} - ${Jalali.monthName(lastJ[1])}"
            }
        }
        CalendarType.GREGORIAN -> {
            val firstCal = Calendar.getInstance().apply { time = firstDay }
            val lastCal = Calendar.getInstance().apply { time = lastDay }
            val firstMonth = firstCal.get(Calendar.MONTH) + 1
            val lastMonth = lastCal.get(Calendar.MONTH) + 1
            if (firstMonth == lastMonth) {
                gregorianMonthName(firstMonth)
            } else {
                "${gregorianMonthName(firstMonth)} - ${gregorianMonthName(lastMonth)}"
            }
        }
        CalendarType.HIJRI -> {
            val firstH = getHijriFromCache(firstDay, cache) ?: getHijriDate(firstDay, null)
            val lastH = getHijriFromCache(lastDay, cache) ?: getHijriDate(lastDay, null)
            if (firstH[1] == lastH[1]) {
                hijriMonthName(firstH[1])
            } else {
                "${hijriMonthName(firstH[1])} - ${hijriMonthName(lastH[1])}"
            }
        }
    }
}

private fun getFirstAndLastDayOfMonth(date: Date, calendarType: CalendarType): Pair<Date, Date> {
    return when (calendarType) {
        CalendarType.JALALI -> {
            val j = Jalali.toJalaliPublic(date.time)
            val firstMillis = Jalali.parse("%04d/%02d/%02d".format(j[0], j[1], 1)) ?: 0L
            val lastMillis = Jalali.parse("%04d/%02d/%02d".format(
                j[0], j[1], Jalali.daysInMonth(j[0], j[1])
            )) ?: 0L
            Date(firstMillis) to Date(lastMillis)
        }
        CalendarType.GREGORIAN -> {
            val cal = Calendar.getInstance().apply { time = date }
            val year = cal.get(Calendar.YEAR)
            val month = cal.get(Calendar.MONTH)
            val firstCal = Calendar.getInstance().apply {
                set(year, month, 1, 0, 0, 0); set(Calendar.MILLISECOND, 0)
            }
            val lastCal = Calendar.getInstance().apply {
                set(year, month, cal.getActualMaximum(Calendar.DAY_OF_MONTH), 0, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }
            firstCal.time to lastCal.time
        }
        CalendarType.HIJRI -> {
            val h = getHijriDate(date, null)
            val daysInHijriMonth = if (h[1] % 2 == 1) 30 else 29
            val firstCal = Calendar.getInstance().apply {
                time = date; add(Calendar.DAY_OF_MONTH, -(h[2] - 1))
            }
            val lastCal = Calendar.getInstance().apply {
                time = date; add(Calendar.DAY_OF_MONTH, daysInHijriMonth - h[2])
            }
            firstCal.time to lastCal.time
        }
    }
}

private fun setYear(date: Date, year: Int, type: CalendarType): Date {
    return when (type) {
        CalendarType.JALALI -> {
            val j = Jalali.toJalaliPublic(date.time)
            Date(Jalali.parse("%04d/%02d/%02d".format(year, j[1], j[2])) ?: date.time)
        }
        CalendarType.GREGORIAN -> {
            Calendar.getInstance().apply { time = date; set(Calendar.YEAR, year) }.time
        }
        CalendarType.HIJRI -> {
            val diff = year - getHijriDate(date, null)[0]
            Calendar.getInstance().apply { time = date; add(Calendar.YEAR, diff) }.time
        }
    }
}

// ═══════════════════════════════════════════════════════
// تبدیل قمری (fallback)
// ═══════════════════════════════════════════════════════

fun getHijriDate(date: Date, settings: CalendarSettings?): IntArray {
    val cal = com.github.msarhan.ummalqura.calendar.UmmalquraCalendar()
    cal.time = date
    var year = cal.get(Calendar.YEAR)
    var month = cal.get(Calendar.MONTH) + 1
    var day = cal.get(Calendar.DAY_OF_MONTH)
    if (settings != null && settings.eidFitrOffset != 0) {
        if (month >= 10) {
            if (settings.eidFitrHijriYear == null || year == settings.eidFitrHijriYear) {
                cal.add(Calendar.DAY_OF_MONTH, settings.eidFitrOffset)
                year = cal.get(Calendar.YEAR)
                month = cal.get(Calendar.MONTH) + 1
                day = cal.get(Calendar.DAY_OF_MONTH)
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
