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
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.snapshotFlow
import java.util.Calendar
import java.util.Date
import java.util.Locale

private const val PAGE_COUNT = 2400
private const val START_PAGE = PAGE_COUNT / 2

@OptIn(FlowPreview::class)
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
    val anchorMonth = remember(primaryCalendar) {
        normalizeToMonthStart(currentDate, primaryCalendar, hijriCacheMap)
    }

    var dayOfMonth by remember(primaryCalendar) {
        mutableIntStateOf(getDayOfMonth(currentDate, primaryCalendar, hijriCacheMap))
    }

    val pagerState = key(primaryCalendar) {
        rememberPagerState(
            initialPage = START_PAGE,
            pageCount = { PAGE_COUNT }
        )
    }

    val pageDates = remember(primaryCalendar) {
        val m = mutableMapOf<Int, Date>()
        m[START_PAGE] = anchorMonth
        m
    }

    fun getPageDate(page: Int): Date {
        pageDates[page]?.let { return it }
        val offset = page - START_PAGE

        val result: Date = if (primaryCalendar == CalendarType.HIJRI) {
            val nearest = pageDates.keys.minByOrNull { kotlin.math.abs(it - page) }!!
            val nearestDate = pageDates[nearest]!!
            val direction = if (page > nearest) 1 else -1
            var r = nearestDate
            var i = nearest
            while (i != page) {
                val h = getHijriFromCacheOrFallback(r, null, hijriCacheMap)
                val days = getHijriMonthDays(r, h, hijriCacheMap)
                r = Calendar.getInstance().apply {
                    time = r
                    add(Calendar.DAY_OF_MONTH, direction * days)
                }.time
                i += direction
                pageDates[i] = r
            }
            r
        } else {
            Calendar.getInstance().apply {
                time = anchorMonth
                add(Calendar.MONTH, offset)
            }.time
        }

        pageDates[page] = result
        return result
    }

    var isInternalChange by remember { mutableStateOf(false) }

    // ✅ فقط بعد از توقف کامل swipe، currentDate رو آپدیت کن
    LaunchedEffect(pagerState, primaryCalendar) {
        snapshotFlow { pagerState.settledPage to dayOfMonth }
            .distinctUntilChanged()
            .debounce(150L)
            .collect { (page, day) ->
                val monthStart = getPageDate(page)
                val newDate = applyDay(monthStart, day, primaryCalendar, hijriCacheMap)
                if (!isSameDay(newDate, currentDate)) {
                    isInternalChange = true
                    onDateChange(newDate)
                }
            }
    }

    // ✅ وقتی currentDate از بیرون عوض می‌شه
    LaunchedEffect(currentDate, primaryCalendar) {
        if (isInternalChange) {
            isInternalChange = false
            return@LaunchedEffect
        }
        val targetOffset = monthsBetween(
            anchorMonth,
            normalizeToMonthStart(currentDate, primaryCalendar, hijriCacheMap),
            primaryCalendar
        )
        val targetPage = START_PAGE + targetOffset
        if (targetPage != pagerState.currentPage && targetPage in 0 until PAGE_COUNT) {
            pagerState.scrollToPage(targetPage)
        }
    }

    // ✅ وقتی currentDate از بیرون عوض شد، dayOfMonth رو آپدیت کن
    LaunchedEffect(currentDate, primaryCalendar) {
        val newDay = getDayOfMonth(currentDate, primaryCalendar, hijriCacheMap)
        if (newDay != dayOfMonth) {
            dayOfMonth = newDay
        }
    }

    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxWidth(),
        pageSpacing = 0.dp
    ) { page ->
        val monthDate = getPageDate(page)

        Card(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(380.dp),
            shape = RoundedCornerShape(
                topStart = 0.dp, topEnd = 0.dp,
                bottomStart = 20.dp, bottomEnd = 20.dp
            ),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            CalendarMonthContent(
                monthDate = monthDate,
                primaryCalendar = primaryCalendar,
                settings = settings,
                hijriCacheMap = hijriCacheMap,
                events = events,
                selectedDay = selectedDay,
                onDayClick = onDayClick
            )
        }
    }
}

@Composable
private fun CalendarMonthContent(
    monthDate: Date,
    primaryCalendar: CalendarType,
    settings: CalendarSettings?,
    hijriCacheMap: Map<String, HijriCache>,
    events: List<Event>,
    selectedDay: Date?,
    onDayClick: (Date) -> Unit
) {
    val daysInMonth = remember(monthDate, primaryCalendar, hijriCacheMap) {
        getDaysInMonth(monthDate, primaryCalendar, hijriCacheMap)
    }
    val firstDayOfWeek = remember(monthDate, primaryCalendar) {
        getFirstDayOfWeek(monthDate, primaryCalendar)
    }

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

        for (row in 0 until 6) {
            Row(Modifier.fillMaxWidth().weight(1f)) {
                for (col in 0 until 7) {
                    val cellIndex = row * 7 + col
                    val dayNumber = cellIndex - firstDayOfWeek + 1
                    if (dayNumber in 1..daysInMonth) {
                        val date = getDateForDay(monthDate, dayNumber, primaryCalendar)
                        val isSelected = selectedDay?.let { isSameDay(it, date) } ?: false
                        val isToday = isSameDay(Date(), date)
                        val isFriday = col == 6
                        val hasHoliday = remember(date, events, hijriCacheMap) {
                            getEventsForDay(events, date, settings, hijriCacheMap).any { it.isHoliday }
                        }

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

// ═══ کمک‌تابع‌ها ═══

private fun getDayOfMonth(
    date: Date, type: CalendarType,
    hijriCacheMap: Map<String, HijriCache>
): Int {
    return when (type) {
        CalendarType.JALALI -> Jalali.toJalaliPublic(date.time)[2]
        CalendarType.GREGORIAN -> Calendar.getInstance().apply { time = date }.get(Calendar.DAY_OF_MONTH)
        CalendarType.HIJRI -> getHijriFromCacheOrFallback(date, null, hijriCacheMap)[2]
    }
}

private fun applyDay(
    monthStart: Date, day: Int,
    type: CalendarType,
    hijriCacheMap: Map<String, HijriCache>
): Date {
    return when (type) {
        CalendarType.JALALI -> {
            val j = Jalali.toJalaliPublic(monthStart.time)
            val actualDay = day.coerceAtMost(Jalali.daysInMonth(j[0], j[1]))
            Date(Jalali.parse(String.format(Locale.US, "%04d/%02d/%02d", j[0], j[1], actualDay)) ?: monthStart.time)
        }
        CalendarType.GREGORIAN -> {
            val cal = Calendar.getInstance().apply { time = monthStart }
            val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
            cal.set(Calendar.DAY_OF_MONTH, day.coerceAtMost(maxDay))
            cal.time
        }
        CalendarType.HIJRI -> {
            Calendar.getInstance().apply {
                time = monthStart
                add(Calendar.DAY_OF_MONTH, day - 1)
            }.time
        }
    }
}

private fun normalizeToMonthStart(
    date: Date, type: CalendarType,
    hijriCacheMap: Map<String, HijriCache>
): Date {
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
        CalendarType.HIJRI -> {
            val h = getHijriFromCacheOrFallback(date, null, hijriCacheMap)
            val daysFromStart = h[2] - 1
            Calendar.getInstance().apply {
                time = date
                add(Calendar.DAY_OF_MONTH, -daysFromStart)
            }.time
        }
    }
}

private fun getHijriMonthDays(
    date: Date, currentHijri: IntArray,
    hijriCacheMap: Map<String, HijriCache>
): Int {
    val cal = Calendar.getInstance().apply { time = date }
    for (i in 1..31) {
        cal.add(Calendar.DAY_OF_MONTH, 1)
        val h = getHijriFromCacheOrFallback(cal.time, null, hijriCacheMap)
        if (h[0] != currentHijri[0] || h[1] != currentHijri[1]) {
            return i
        }
    }
    return 30
}

private fun monthsBetween(from: Date, to: Date, type: CalendarType): Int {
    if (type == CalendarType.HIJRI) {
        val days = ((to.time - from.time) / 86_400_000L).toInt()
        return if (days >= 0) (days / 29.53).toInt() else -((-days / 29.53).toInt())
    }
    val c1 = Calendar.getInstance().apply { time = from }
    val c2 = Calendar.getInstance().apply { time = to }
    return (c2.get(Calendar.YEAR) - c1.get(Calendar.YEAR)) * 12 +
            (c2.get(Calendar.MONTH) - c1.get(Calendar.MONTH))
}

private fun isSameDay(d1: Date, d2: Date): Boolean {
    val c1 = Calendar.getInstance().apply { time = d1 }
    val c2 = Calendar.getInstance().apply { time = d2 }
    return c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR) &&
            c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR)
}

private fun getDaysInMonth(
    date: Date, type: CalendarType,
    hijriCacheMap: Map<String, HijriCache>
): Int = when (type) {
    CalendarType.JALALI -> {
        val j = Jalali.toJalaliPublic(date.time); Jalali.daysInMonth(j[0], j[1])
    }
    CalendarType.GREGORIAN -> Calendar.getInstance().apply { time = date }.getActualMaximum(Calendar.DAY_OF_MONTH)
    CalendarType.HIJRI -> {
        val h = getHijriFromCacheOrFallback(date, null, hijriCacheMap)
        getHijriMonthDays(date, h, hijriCacheMap)
    }
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
        CalendarType.HIJRI -> {
            Calendar.getInstance().apply {
                time = currentDate
                add(Calendar.DAY_OF_MONTH, dayNumber - 1)
            }.time
        }
    }
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
