package com.sdamir66.dadban.calendar.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
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
import com.sdamir66.dadban.calendar.data.Event
import com.sdamir66.dadban.calendar.data.HijriCache
import com.sdamir66.dadban.util.Jalali
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun MonthCalendarView(
    currentDate: Date,
    primaryCalendar: CalendarType,
    settings: CalendarSettings?,
    hijriCacheMap: Map<String, HijriCache>,
    events: List<Event>,
    selectedDay: Date?,
    onDayClick: (Date) -> Unit,
    onDateChange: (Date) -> Unit
) {
    val baseMonth = remember { normalizeToMonthStart(currentDate, primaryCalendar) }
    val pageCount = 2400
    val startPage = pageCount / 2

    val pagerState = rememberPagerState(
        initialPage = startPage,
        pageCount = { pageCount }
    )

    fun pageToDate(page: Int): Date = addMonths(baseMonth, page - startPage)

    // ✅ اصلاح: برای HIJRI، onDateChange رو حتماً صدا بزن
    LaunchedEffect(pagerState.currentPage, primaryCalendar) {
        val newDate = pageToDate(pagerState.currentPage)
        
        if (primaryCalendar == CalendarType.HIJRI) {
            // برای قمری: اگه روز فرق کرد، onDateChange رو صدا بزن
            if (!isSameDay(newDate, currentDate)) {
                onDateChange(newDate)
            }
        } else {
            if (!isSameMonth(newDate, currentDate, primaryCalendar, settings)) {
                onDateChange(newDate)
            }
        }
    }

    LaunchedEffect(currentDate) {
        val offset = monthsBetween(baseMonth, normalizeToMonthStart(currentDate, primaryCalendar))
        val targetPage = startPage + offset
        if (targetPage != pagerState.currentPage && targetPage in 0 until pageCount) {
            pagerState.scrollToPage(targetPage)
        }
    }

    HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth(), pageSpacing = 0.dp) { page ->
        val monthDate = pageToDate(page)

        Card(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(380.dp),
            shape = RoundedCornerShape(
                topStart = 0.dp,
                topEnd = 0.dp,
                bottomStart = 20.dp,
                bottomEnd = 20.dp
            ),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Column(Modifier.padding(8.dp).fillMaxHeight()) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Row(Modifier.fillMaxWidth()) {
                        listOf("ش", "ی", "د", "س", "چ", "پ", "ج").forEachIndexed { index, day ->
                            Text(day, modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (index == 6) Color(0xFFE53935) else Color(0xFF5C5D72),
                                fontSize = 13.sp)
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))

                val daysInMonth = getDaysInMonth(monthDate, primaryCalendar)
                val firstDayOfWeek = getFirstDayOfWeek(monthDate, primaryCalendar)
                val rows = 6

                for (row in 0 until rows) {
                    Row(Modifier.fillMaxWidth().weight(1f)) {
                        for (col in 0 until 7) {
                            val cellIndex = row * 7 + col
                            val dayNumber = cellIndex - firstDayOfWeek + 1
                            if (dayNumber in 1..daysInMonth) {
                                val date = getDateForDay(monthDate, dayNumber, primaryCalendar)
                                val isSelected = selectedDay?.let { isSameDay(it, date) } ?: false
                                val isToday = isSameDay(Date(), date)
                                val isFriday = col == 6
                                val dayEvents = getEventsForDay(events, date, settings, hijriCacheMap)
                                val hasHoliday = dayEvents.any { it.isHoliday }

                                DayCell(
                                    day = dayNumber, date = date,
                                    isSelected = isSelected, isToday = isToday,
                                    isFriday = isFriday, hasHoliday = hasHoliday,
                                    primaryCalendar = primaryCalendar, settings = settings,
                                    hijriCacheMap = hijriCacheMap,
                                    onClick = { onDayClick(date) },
                                    modifier = Modifier.weight(1f).fillMaxHeight()
                                )
                            } else {
                                Box(Modifier.weight(1f).fillMaxHeight())
                            }
                        }
                    }
                }
            }
        }
    }
}

// ═══ کمک‌تابع‌ها ═══
private fun normalizeToMonthStart(date: Date, type: CalendarType): Date {
    return when (type) {
        CalendarType.JALALI -> {
            val j = Jalali.toJalaliPublic(date.time)
            Date(Jalali.parse(String.format(Locale.US, "%04d/%02d/%02d", j[0], j[1], 1)) ?: date.time)
        }
        CalendarType.GREGORIAN -> Calendar.getInstance().apply {
            time = date; set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.time
        CalendarType.HIJRI -> date
    }
}

private fun addMonths(date: Date, months: Int): Date =
    Calendar.getInstance().apply { time = date; add(Calendar.MONTH, months) }.time

private fun monthsBetween(from: Date, to: Date): Int {
    val c1 = Calendar.getInstance().apply { time = from }
    val c2 = Calendar.getInstance().apply { time = to }
    return (c2.get(Calendar.YEAR) - c1.get(Calendar.YEAR)) * 12 +
            (c2.get(Calendar.MONTH) - c1.get(Calendar.MONTH))
}

private fun isSameMonth(d1: Date, d2: Date, type: CalendarType, settings: CalendarSettings?): Boolean {
    return when (type) {
        CalendarType.JALALI -> {
            val j1 = Jalali.toJalaliPublic(d1.time); val j2 = Jalali.toJalaliPublic(d2.time)
            j1[0] == j2[0] && j1[1] == j2[1]
        }
        CalendarType.GREGORIAN -> {
            val c1 = Calendar.getInstance().apply { time = d1 }
            val c2 = Calendar.getInstance().apply { time = d2 }
            c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR) && c1.get(Calendar.MONTH) == c2.get(Calendar.MONTH)
        }
        CalendarType.HIJRI -> true
    }
}

private fun getDaysInMonth(date: Date, type: CalendarType): Int = when (type) {
    CalendarType.JALALI -> {
        val j = Jalali.toJalaliPublic(date.time); Jalali.daysInMonth(j[0], j[1])
    }
    CalendarType.GREGORIAN -> Calendar.getInstance().apply { time = date }.getActualMaximum(Calendar.DAY_OF_MONTH)
    CalendarType.HIJRI -> 30
}

private fun getFirstDayOfWeek(date: Date, type: CalendarType): Int {
    val firstOfMonth = when (type) {
        CalendarType.JALALI -> {
            val j = Jalali.toJalaliPublic(date.time)
            Date(Jalali.parse(String.format(Locale.US, "%04d/%02d/%02d", j[0], j[1], 1)) ?: 0L)
        }
        CalendarType.GREGORIAN -> Calendar.getInstance().apply {
            time = date; set(Calendar.DAY_OF_MONTH, 1)
        }.time
        CalendarType.HIJRI -> date
    }
    val cal = Calendar.getInstance().apply { time = firstOfMonth }
    return when (cal.get(Calendar.DAY_OF_WEEK)) {
        Calendar.SATURDAY -> 0; Calendar.SUNDAY -> 1; Calendar.MONDAY -> 2
        Calendar.TUESDAY -> 3; Calendar.WEDNESDAY -> 4; Calendar.THURSDAY -> 5
        Calendar.FRIDAY -> 6; else -> 0
    }
}

private fun getDateForDay(currentDate: Date, dayNumber: Int, type: CalendarType): Date {
    return when (type) {
        CalendarType.JALALI -> {
            val j = Jalali.toJalaliPublic(currentDate.time)
            Date(Jalali.parse(String.format(Locale.US, "%04d/%02d/%02d", j[0], j[1], dayNumber)) ?: 0L)
        }
        CalendarType.GREGORIAN -> Calendar.getInstance().apply {
            time = currentDate; set(Calendar.DAY_OF_MONTH, dayNumber)
        }.time
        CalendarType.HIJRI -> Calendar.getInstance().apply {
            time = currentDate; set(Calendar.DAY_OF_MONTH, dayNumber)
        }.time
    }
}

private fun isSameDay(d1: Date, d2: Date): Boolean {
    val c1 = Calendar.getInstance().apply { time = d1 }
    val c2 = Calendar.getInstance().apply { time = d2 }
    return c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR) &&
            c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR)
}

private fun getEventsForDay(
    events: List<Event>, date: Date, settings: CalendarSettings?,
    hijriCacheMap: Map<String, HijriCache>
): List<Event> {
    val cal = Calendar.getInstance().apply { time = date }
    val gM = cal.get(Calendar.MONTH) + 1
    val gD = cal.get(Calendar.DAY_OF_MONTH)
    val j = Jalali.toJalaliPublic(date.time)
    val jM = j[1]; val jD = j[2]

    val hijri = getHijriFromCacheOrFallback(date, settings, hijriCacheMap)

    return events.filter { event ->
        when (event.calendarType) {
            CalendarType.JALALI -> event.month == jM && event.day == jD
            CalendarType.GREGORIAN -> event.month == gM && event.day == gD
            CalendarType.HIJRI -> event.month == hijri[1] && event.day == hijri[2]
        }
    }
}

fun getHijriFromCacheOrFallback(
    date: Date, settings: CalendarSettings?, cache: Map<String, HijriCache>
): IntArray {
    val j = Jalali.toJalaliPublic(date.time)
    val key = String.format(Locale.US, "%04d/%02d/%02d", j[0], j[1], j[2])
    val cached = cache[key]
    if (cached != null) {
        return intArrayOf(cached.hijriYear, hijriMonthNameToNumber(cached.hijriMonth), cached.hijriDay)
    }
    return getHijriDate(date, settings)
}
