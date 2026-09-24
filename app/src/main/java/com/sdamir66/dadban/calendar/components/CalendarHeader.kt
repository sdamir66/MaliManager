package com.sdamir66.dadban.calendar.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
    onDateChange: (Date) -> Unit,
    onCalendarTypeChange: (CalendarType) -> Unit
) {
    var showYearPicker by remember { mutableStateOf(false) }

    // ═══ Pager برای سواپ سال ═══
    val baseYear = remember { getYear(currentDate, primaryCalendar, settings) }
    val pageCount = 200
    val startPage = pageCount / 2

    val pagerState = rememberPagerState(
        initialPage = startPage,
        pageCount = { pageCount }
    )

    fun pageToYear(page: Int): Int = baseYear + (page - startPage)

    LaunchedEffect(pagerState.currentPage) {
        val newYear = pageToYear(pagerState.currentPage)
        val currentYear = getYear(currentDate, primaryCalendar, settings)
        if (newYear != currentYear) {
            onDateChange(setYear(currentDate, newYear, primaryCalendar))
        }
    }

    LaunchedEffect(currentDate) {
        val year = getYear(currentDate, primaryCalendar, settings)
        val targetPage = startPage + (year - baseYear)
        if (targetPage != pagerState.currentPage && targetPage in 0 until pageCount) {
            pagerState.scrollToPage(targetPage)
        }
    }

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
            // ═══ ردیف اول: ماه + سال (با سواپ) ═══
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth(),
                pageSpacing = 0.dp
            ) { page ->
                val year = pageToYear(page)
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        getMonthName(currentDate, primaryCalendar, settings),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 22.sp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        year.toString(),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 22.sp,
                        modifier = Modifier.clickable { showYearPicker = true }
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // ═══ ردیف دوم: سه تقویم (با فاصله‌ی مساوی) ═══
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CalendarTypeChip(
                    label = getChipLabel(currentDate, CalendarType.JALALI, settings, primaryCalendar),
                    isSelected = primaryCalendar == CalendarType.JALALI,
                    onClick = { onCalendarTypeChange(CalendarType.JALALI) },
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                CalendarTypeChip(
                    label = getChipLabel(currentDate, CalendarType.HIJRI, settings, primaryCalendar),
                    isSelected = primaryCalendar == CalendarType.HIJRI,
                    onClick = { onCalendarTypeChange(CalendarType.HIJRI) },
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                CalendarTypeChip(
                    label = getChipLabel(currentDate, CalendarType.GREGORIAN, settings, primaryCalendar),
                    isSelected = primaryCalendar == CalendarType.GREGORIAN,
                    onClick = { onCalendarTypeChange(CalendarType.GREGORIAN) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

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
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = if (isSelected) Color.White else Color.White.copy(alpha = 0.2f),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.clickable { onClick() }
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
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

/**
 * فرمت دو خطی:
 * - خط اول: تاریخ عددی کامل (سال/ماه/روز)
 * - خط دوم: فقط نام ماه(ها) — بدون سال
 *
 * ⚠️ اول و آخر ماه از **تقویم اصلی (primaryCalendar)** حساب میشه،
 * و بعد توی تقویم هدف (type) نمایش داده میشه.
 */
private fun getChipLabel(
    date: Date,
    type: CalendarType,
    settings: CalendarSettings?,
    primaryCalendar: CalendarType
): String {
    return when (type) {
        CalendarType.JALALI -> {
            val j = Jalali.toJalaliPublic(date.time)
            "${j[0]}/${j[1].toString().padStart(2, '0')}/${j[2].toString().padStart(2, '0')}\n" +
            getMonthRangeForPrimary(date, primaryCalendar, CalendarType.JALALI, settings)
        }
        CalendarType.GREGORIAN -> {
            val cal = Calendar.getInstance().apply { time = date }
            "${cal.get(Calendar.YEAR)}/${(cal.get(Calendar.MONTH) + 1).toString().padStart(2, '0')}/${cal.get(Calendar.DAY_OF_MONTH).toString().padStart(2, '0')}\n" +
            getMonthRangeForPrimary(date, primaryCalendar, CalendarType.GREGORIAN, settings)
        }
        CalendarType.HIJRI -> {
            val h = getHijriDate(date, settings)
            "${h[0]}/${h[1].toString().padStart(2, '0')}/${h[2].toString().padStart(2, '0')}\n" +
            getMonthRangeForPrimary(date, primaryCalendar, CalendarType.HIJRI, settings)
        }
    }
}

/**
 * محدوده‌ی ماه تقویم هدف (targetCalendar) رو بر اساس ماه اصلی (primaryCalendar) برمی‌گردونه.
 *
 * مثلاً اگه ماه اصلی جلالی باشه، می‌خوایم ببینیم اون ماه جلالی
 * توی چه ماه‌هایی از تقویم میلادی/قمری قرار می‌گیره.
 */
private fun getMonthRangeForPrimary(
    date: Date,
    primaryCalendar: CalendarType,
    targetCalendar: CalendarType,
    settings: CalendarSettings?
): String {
    // ═══ ۱. اول و آخر ماه اصلی رو حساب کن ═══
    val (firstDay, lastDay) = getFirstAndLastDayOfMonth(date, primaryCalendar, settings)

    // ═══ ۲. تبدیل به تقویم هدف و نمایش محدوده ═══
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
            val firstH = getHijriDate(firstDay, settings)
            val lastH = getHijriDate(lastDay, settings)
            if (firstH[1] == lastH[1]) {
                hijriMonthName(firstH[1])
            } else {
                "${hijriMonthName(firstH[1])} - ${hijriMonthName(lastH[1])}"
            }
        }
    }
}

/**
 * اول و آخر ماه تقویم مورد نظر رو برمی‌گردونه.
 */
private fun getFirstAndLastDayOfMonth(
    date: Date,
    calendarType: CalendarType,
    settings: CalendarSettings?
): Pair<Date, Date> {
    return when (calendarType) {
        CalendarType.JALALI -> {
            val j = Jalali.toJalaliPublic(date.time)
            val firstMillis = Jalali.parse("%04d/%02d/%02d".format(j[0], j[1], 1)) ?: 0L
            val lastMillis = Jalali.parse("%04d/%02d/%02d".format(
                j[0], j[1],
                Jalali.daysInMonth(j[0], j[1])
            )) ?: 0L
            Date(firstMillis) to Date(lastMillis)
        }
        CalendarType.GREGORIAN -> {
            val cal = Calendar.getInstance().apply { time = date }
            val year = cal.get(Calendar.YEAR)
            val month = cal.get(Calendar.MONTH)
            val firstCal = Calendar.getInstance().apply {
                set(year, month, 1, 0, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val lastCal = Calendar.getInstance().apply {
                set(year, month, cal.getActualMaximum(Calendar.DAY_OF_MONTH), 0, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }
            firstCal.time to lastCal.time
        }
        CalendarType.HIJRI -> {
            val h = getHijriDate(date, settings)
            // برای قمری، ماه‌های فرد ۳۰ روز و زوج ۲۹ روز
            val daysInHijriMonth = if (h[1] % 2 == 1) 30 else 29
            val firstCal = Calendar.getInstance().apply {
                time = date
                add(Calendar.DAY_OF_MONTH, -(h[2] - 1))
            }
            val lastCal = Calendar.getInstance().apply {
                time = date
                add(Calendar.DAY_OF_MONTH, daysInHijriMonth - h[2])
            }
            firstCal.time to lastCal.time
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
        CalendarType.HIJRI -> {
            val diff = year - getHijriDate(date, null)[0]
            Calendar.getInstance().apply {
                time = date
                add(Calendar.YEAR, diff)
            }.time
        }
    }
}

// ═══════════════════════════════════════════════════════
// تبدیل قمری
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
