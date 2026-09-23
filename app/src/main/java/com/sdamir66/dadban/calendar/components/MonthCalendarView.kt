package com.sdamir66.dadban.calendar.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sdamir66.dadban.calendar.data.CalendarType
import com.sdamir66.dadban.calendar.data.Event
import java.util.Calendar
import java.util.Date

@Composable
fun MonthCalendarView(
    currentDate: Date,
    primaryCalendar: CalendarType,
    events: List<Event>,
    selectedDay: Date?,
    onDayClick: (Date) -> Unit,
    showGregorianSmall: Boolean,
    showHijriSmall: Boolean,
    eidFitrOffset: Int,
    eidFitrHijriYear: Int?
) {
    // ═══ محاسبه‌ی روزهای ماه ═══
    val daysInMonth = getDaysInMonth(currentDate, primaryCalendar)
    val firstDayOfWeek = getFirstDayOfWeek(currentDate, primaryCalendar)
    
    Card(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            // ═══ نام روزهای هفته ═══
            Row(Modifier.fillMaxWidth()) {
                listOf("ش", "ی", "د", "س", "چ", "پ", "ج").forEachIndexed { index, day ->
                    Text(
                        day,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = if (index == 6) Color(0xFFE53935) else Color(0xFF5C5D72)
                    )
                }
            }
            
            Spacer(Modifier.height(6.dp))
            
            // ═══ گرید روزها ═══
            val totalCells = firstDayOfWeek + daysInMonth
            val rows = (totalCells + 6) / 7
            
            for (row in 0 until rows) {
                Row(Modifier.fillMaxWidth()) {
                    for (col in 0 until 7) {
                        val cellIndex = row * 7 + col
                        val dayNumber = cellIndex - firstDayOfWeek + 1
                        
                        if (dayNumber in 1..daysInMonth) {
                            val date = getDateForDay(currentDate, dayNumber, primaryCalendar)
                            val isSelected = selectedDay?.let { isSameDay(it, date) } ?: false
                            val isToday = isSameDay(Date(), date)
                            val isFriday = col == 6
                            
                            // رویدادهای این روز
                            val dayEvents = getEventsForDay(events, date)
                            val hasHoliday = dayEvents.any { it.isHoliday }
                            
                            DayCell(
                                day = dayNumber,
                                date = date,
                                isSelected = isSelected,
                                isToday = isToday,
                                isFriday = isFriday,
                                hasHoliday = hasHoliday,
                                primaryCalendar = primaryCalendar,
                                showGregorianSmall = showGregorianSmall,
                                showHijriSmall = showHijriSmall,
                                onClick = { onDayClick(date) },
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            Box(Modifier.weight(1f).aspectRatio(1f))
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════
// کمک‌تابع‌ها
// ═══════════════════════════════════════════════════════

private fun getDaysInMonth(date: Date, type: CalendarType): Int {
    return when (type) {
        CalendarType.JALALI -> {
            val j = com.sdamir66.dadban.util.Jalali.toJalaliPublic(date.time)
            com.sdamir66.dadban.util.Jalali.daysInMonth(j[0], j[1])
        }
        CalendarType.GREGORIAN -> {
            val cal = Calendar.getInstance().apply { time = date }
            cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        }
        CalendarType.HIJRI -> 30 // تقریبی
    }
}

private fun getFirstDayOfWeek(date: Date, type: CalendarType): Int {
    return when (type) {
        CalendarType.JALALI -> {
            val j = com.sdamir66.dadban.util.Jalali.toJalaliPublic(date.time)
            val firstMillis = com.sdamir66.dadban.util.Jalali.parse(
                "%04d/%02d/%02d".format(j[0], j[1], 1)
            ) ?: 0L
            val cal = Calendar.getInstance().apply { timeInMillis = firstMillis }
            when (cal.get(Calendar.DAY_OF_WEEK)) {
                Calendar.SATURDAY -> 0
                Calendar.SUNDAY -> 1
                Calendar.MONDAY -> 2
                Calendar.TUESDAY -> 3
                Calendar.WEDNESDAY -> 4
                Calendar.THURSDAY -> 5
                Calendar.FRIDAY -> 6
                else -> 0
            }
        }
        CalendarType.GREGORIAN -> {
            val cal = Calendar.getInstance().apply {
                time = date
                set(Calendar.DAY_OF_MONTH, 1)
            }
            when (cal.get(Calendar.DAY_OF_WEEK)) {
                Calendar.SATURDAY -> 0
                Calendar.SUNDAY -> 1
                Calendar.MONDAY -> 2
                Calendar.TUESDAY -> 3
                Calendar.WEDNESDAY -> 4
                Calendar.THURSDAY -> 5
                Calendar.FRIDAY -> 6
                else -> 0
            }
        }
        CalendarType.HIJRI -> 0
    }
}

private fun getDateForDay(currentDate: Date, dayNumber: Int, type: CalendarType): Date {
    return when (type) {
        CalendarType.JALALI -> {
            val j = com.sdamir66.dadban.util.Jalali.toJalaliPublic(currentDate.time)
            val millis = com.sdamir66.dadban.util.Jalali.parse(
                "%04d/%02d/%02d".format(j[0], j[1], dayNumber)
            ) ?: 0L
            Date(millis)
        }
        CalendarType.GREGORIAN -> {
            val cal = Calendar.getInstance().apply {
                time = currentDate
                set(Calendar.DAY_OF_MONTH, dayNumber)
            }
            cal.time
        }
        CalendarType.HIJRI -> currentDate
    }
}

private fun isSameDay(d1: Date, d2: Date): Boolean {
    val c1 = Calendar.getInstance().apply { time = d1 }
    val c2 = Calendar.getInstance().apply { time = d2 }
    return c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR) &&
            c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR)
}

private fun getEventsForDay(events: List<Event>, date: Date): List<Event> {
    val cal = Calendar.getInstance().apply { time = date }
    val gregorianMonth = cal.get(Calendar.MONTH) + 1
    val gregorianDay = cal.get(Calendar.DAY_OF_MONTH)
    
    val jalali = com.sdamir66.dadban.util.Jalali.toJalaliPublic(date.time)
    val jalaliMonth = jalali[1]
    val jalaliDay = jalali[2]
    
    return events.filter { event ->
        when (event.calendarType) {
            CalendarType.JALALI ->
                event.month == jalaliMonth && event.day == jalaliDay
            CalendarType.GREGORIAN ->
                event.month == gregorianMonth && event.day == gregorianDay
            CalendarType.HIJRI -> false // TODO
        }
    }
}
