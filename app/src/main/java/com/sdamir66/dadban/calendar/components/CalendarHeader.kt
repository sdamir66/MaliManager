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

    // تغییر سال با سواپ
    LaunchedEffect(pagerState.currentPage) {
        val newYear = pageToYear(pagerState.currentPage)
        val currentYear = getYear(currentDate, primaryCalendar, settings)
        if (newYear != currentYear) {
            onDateChange(setYear(currentDate, newYear, primaryCalendar))
        }
    }

    // همگام‌سازی با تغییر از بیرون
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
                        fontSize = 24.sp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        year.toString(),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 24.sp,
                        modifier = Modifier.clickable { showYearPicker = true }
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // ═══ ردیف دوم: سه تقویم (قابل کلیک) ═══
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CalendarTypeChip(
                    label = getChipLabel(currentDate, CalendarType.JALALI, settings),
                    isSelected = primaryCalendar == CalendarType.JALALI,
                    onClick = { onCalendarTypeChange(CalendarType.JALALI) }
                )
                Spacer(Modifier.width(6.dp))
                CalendarTypeChip(
                    label = getChipLabel(currentDate, CalendarType.HIJRI, settings),
                    isSelected = primaryCalendar == CalendarType.HIJRI,
                    onClick = { onCalendarTypeChange(CalendarType.HIJRI) }
                )
                Spacer(Modifier.width(6.dp))
                CalendarTypeChip(
                    label = getChipLabel(currentDate, CalendarType.GREGORIAN, settings),
                    isSelected = primaryCalendar == CalendarType.GREGORIAN,
                    onClick = { onCalendarTypeChange(CalendarType.GREGORIAN) }
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
    onClick: () -> Unit
) {
    Surface(
        color = if (isSelected) Color.White else Color.White.copy(alpha = 0.2f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
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
 * فرمت دو خطی: تاریخ کامل + فقط نام ماه جاری (نه دو ماه)
 */
private fun getChipLabel(date: Date, type: CalendarType, settings: CalendarSettings?): String {
    return when (type) {
        CalendarType.JALALI -> {
            val j = Jalali.toJalaliPublic(date.time)
            "${j[0]}/${j[1].toString().padStart(2, '0')}/${j[2].toString().padStart(2, '0')}\n" +
            "${Jalali.monthName(j[1])} ${j[0]}"
        }
        CalendarType.GREGORIAN -> {
            val cal = Calendar.getInstance().apply { time = date }
            "${cal.get(Calendar.YEAR)}/${(cal.get(Calendar.MONTH) + 1).toString().padStart(2, '0')}/${cal.get(Calendar.DAY_OF_MONTH).toString().padStart(2, '0')}\n" +
            "${gregorianMonthName(cal.get(Calendar.MONTH) + 1)} ${cal.get(Calendar.YEAR)}"
        }
        CalendarType.HIJRI -> {
            val h = getHijriDate(date, settings)
            "${h[0]}/${h[1].toString().padStart(2, '0')}/${h[2].toString().padStart(2, '0')}\n" +
            "${hijriMonthName(h[1])} ${h[0]}"
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
            // برای قمری، سال رو با اضافه کردن اختلاف تغییر بده
            val diff = year - getHijriDate(date, null)[0]
            Calendar.getInstance().apply {
                time = date
                add(Calendar.YEAR, diff)
            }.time
        }
    }
}

// ═══════════════════════════════════════════════════════
// تبدیل قمری (با UmmalquraCalendar + اصلاح -1 روز)
// ═══════════════════════════════════════════════════════

fun getHijriDate(date: Date, settings: CalendarSettings?): IntArray {
    val cal = com.github.msarhan.ummalqura.calendar.UmmalquraCalendar()
    cal.time = date

    // ✅ اصلاح ۱ روز جلو بودن (کم کردن ۱ روز)
    cal.add(Calendar.DAY_OF_MONTH, -1)

    var year = cal.get(Calendar.YEAR)
    var month = cal.get(Calendar.MONTH) + 1
    var day = cal.get(Calendar.DAY_OF_MONTH)

    // اعمال اصلاح eidFitrOffset
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
