package com.sdamir66.dadban.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sdamir66.dadban.calendar.components.*
import com.sdamir66.dadban.calendar.data.*
import com.sdamir66.dadban.calendar.prayer.PrayerTimesCalculator
import com.sdamir66.dadban.calendar.prayer.PrayerTimesData
import com.sdamir66.dadban.data.AppDb
import com.sdamir66.dadban.ui.theme.BgLight
import com.sdamir66.dadban.ui.theme.HeaderBlue
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date

@Composable
fun CalendarScreen(db: AppDb) {
    val scope = rememberCoroutineScope()
    
    // ═══ تنظیمات ═══
    var settings by remember { mutableStateOf<CalendarSettings?>(null) }
    LaunchedEffect(Unit) {
        settings = db.calendarSettingsDao().getNow() ?: CalendarSettings()
    }
    
    // ═══ ماه جاری ═══
    var currentDate by remember { mutableStateOf(Date()) }
    
    // ═══ تقویم اصلی ═══
    var primaryCalendar by remember { mutableStateOf(CalendarType.JALALI) }
    
    // ═══ روز انتخاب‌شده ═══
    var selectedDay by remember { mutableStateOf<Date?>(null) }
    
    // ═══ اوقات شرعی ═══
    val prayerTimes = remember(settings, currentDate) {
        if (settings != null) {
            PrayerTimesCalculator.calculate(
                latitude = settings!!.latitude,
                longitude = settings!!.longitude,
                date = selectedDay ?: currentDate
            )
        } else null
    }
    
    // ═══ رویدادهای ماه ═══
    val allEvents by db.eventDao().all().collectAsState(emptyList())
    
    // ═══ فیلتر رویدادها بر اساس تنظیمات ═══
    val visibleEvents = allEvents.filter { event ->
        when {
            event.isHoliday && settings?.showHolidays == true -> true
            event.category == EventCategory.RELIGIOUS && !event.isHoliday && settings?.showReligiousNonHoliday == true -> true
            event.category == EventCategory.NATIONAL && !event.isHoliday && settings?.showNationalNonHoliday == true -> true
            event.category == EventCategory.GLOBAL && settings?.showGlobalEvents == true -> true
            event.isUserCreated && settings?.showUserEvents == true -> true
            else -> false
        }
    }
    
    // ═══ رویدادهای ماه جاری ═══
    val monthEvents = remember(visibleEvents, currentDate, primaryCalendar) {
        filterEventsForMonth(visibleEvents, currentDate, primaryCalendar)
    }
    
    // ═══ رویدادهای روز انتخاب‌شده ═══
    val selectedDayEvents = remember(selectedDay, visibleEvents, primaryCalendar) {
        if (selectedDay == null) emptyList()
        else filterEventsForDay(visibleEvents, selectedDay!!, primaryCalendar)
    }
    
    if (settings == null) {
        Box(Modifier.fillMaxSize().background(BgLight), contentAlignment = androidx.compose.ui.Alignment.Center) {
            Text("در حال بارگذاری...", color = Color.Gray)
        }
        return
    }
    
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(BgLight)
    ) {
        // ═══ هدر تقویم ═══
        item {
            CalendarHeader(
    currentDate = currentDate,
    primaryCalendar = primaryCalendar,
    onPrevYear = { currentDate = addYears(currentDate, -1) },
    onNextYear = { currentDate = addYears(currentDate, 1) },
    onPrevMonth = { currentDate = addMonths(currentDate, -1) },
    onNextMonth = { currentDate = addMonths(currentDate, 1) },
    onCalendarTypeChange = { primaryCalendar = it },
    onDateChange = { newDate -> currentDate = newDate }   // ← اضافه کن
)
        }
        
        // ═══ تقویم ماهانه ═══
        item {
            MonthCalendarView(
                currentDate = currentDate,
                primaryCalendar = primaryCalendar,
                events = monthEvents,
                selectedDay = selectedDay,
                onDayClick = { date ->
                    onDateChange = { newDate -> currentDate = newDate },   // ← اضافه کن
    showGregorianSmall = settings!!.showGregorianSmall,
    showHijriSmall = settings!!.showHijriSmall,
    eidFitrOffset = settings!!.eidFitrOffset,
    eidFitrHijriYear = settings!!.eidFitrHijriYear
                },
                showGregorianSmall = settings!!.showGregorianSmall,
                showHijriSmall = settings!!.showHijriSmall,
                eidFitrOffset = settings!!.eidFitrOffset,
                eidFitrHijriYear = settings!!.eidFitrHijriYear
            )
        }
        
        // ═══ اوقات شرعی ═══
        if (settings!!.showPrayerTimes && prayerTimes != null) {
            item {
                Spacer(Modifier.height(16.dp))
                PrayerTimesSection(
                    prayerTimes = prayerTimes,
                    selectedDate = selectedDay ?: currentDate
                )
            }
        }
        
        // ═══ رویدادهای روز ═══
        item {
            Spacer(Modifier.height(16.dp))
            EventsSection(
                events = selectedDayEvents,
                date = selectedDay ?: currentDate,
                primaryCalendar = primaryCalendar
            )
        }
        
        item {
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ═══════════════════════════════════════════════════════
// کمک‌تابع‌ها
// ═══════════════════════════════════════════════════════

private fun addYears(date: Date, years: Int): Date {
    return Calendar.getInstance().apply {
        time = date
        add(Calendar.YEAR, years)
    }.time
}

private fun addMonths(date: Date, months: Int): Date {
    return Calendar.getInstance().apply {
        time = date
        add(Calendar.MONTH, months)
    }.time
}

/**
 * رویدادهای ماه جاری رو فیلتر می‌کنه.
 * بر اساس تقویم اصلی، ماه جاری رو از اون تقویم حساب می‌کنه.
 */
private fun filterEventsForMonth(
    events: List<Event>,
    currentDate: Date,
    primaryCalendar: CalendarType
): List<Event> {
    // برای سادگی، همه‌ی رویدادهای اون ماه رو برمی‌گردونیم
    // (فیلتر دقیق توی DayCell انجام میشه)
    return events
}

/**
 * رویدادهای یه روز خاص رو فیلتر می‌کنه.
 */
private fun filterEventsForDay(
    events: List<Event>,
    date: Date,
    primaryCalendar: CalendarType
): List<Event> {
    val cal = Calendar.getInstance().apply { time = date }
    val gregorianYear = cal.get(Calendar.YEAR)
    val gregorianMonth = cal.get(Calendar.MONTH) + 1
    val gregorianDay = cal.get(Calendar.DAY_OF_MONTH)
    
    // TODO: تبدیل به جلالی و قمری
    val jalali = com.sdamir66.dadban.util.Jalali.toJalaliPublic(date.time)
    val jalaliYear = jalali[0]
    val jalaliMonth = jalali[1]
    val jalaliDay = jalali[2]
    
    return events.filter { event ->
        when (event.calendarType) {
            CalendarType.JALALI ->
                event.month == jalaliMonth && event.day == jalaliDay
            CalendarType.GREGORIAN ->
                event.month == gregorianMonth && event.day == gregorianDay
            CalendarType.HIJRI -> {
                // TODO: تبدیل به قمری
                false
            }
        }
    }
}
