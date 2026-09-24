package com.sdamir66.dadban.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sdamir66.dadban.calendar.components.*
import com.sdamir66.dadban.calendar.data.*
import com.sdamir66.dadban.calendar.prayer.PrayerTimesCalculator
import com.sdamir66.dadban.data.AppDb
import com.sdamir66.dadban.ui.theme.BgLight
import com.sdamir66.dadban.util.Jalali
import java.util.Calendar
import java.util.Date

@Composable
fun CalendarScreen(db: AppDb) {
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
    val prayerTimes = remember(settings, currentDate, selectedDay) {
        if (settings != null) {
            PrayerTimesCalculator.calculate(
                latitude = settings!!.latitude,
                longitude = settings!!.longitude,
                date = selectedDay ?: currentDate,
                cityName = settings!!.cityName
            )
        } else null
    }

    // ═══ رویدادها ═══
    val allEvents by db.eventDao().all().collectAsState(emptyList())

    val visibleEvents = allEvents.filter { event ->
        when {
            event.isHoliday && (settings?.showHolidays ?: true) -> true
            event.category == EventCategory.RELIGIOUS && !event.isHoliday && (settings?.showReligiousNonHoliday ?: false) -> true
            event.category == EventCategory.NATIONAL && !event.isHoliday && (settings?.showNationalNonHoliday ?: false) -> true
            event.category == EventCategory.GLOBAL && (settings?.showGlobalEvents ?: false) -> true
            event.isUserCreated && (settings?.showUserEvents ?: true) -> true
            else -> false
        }
    }

    // ═══ رویدادهای روز انتخاب‌شده ═══
    val selectedDayEvents = remember(selectedDay, visibleEvents) {
        if (selectedDay == null) emptyList()
        else filterEventsForDay(visibleEvents, selectedDay!!)
    }

    if (settings == null) {
        Box(
            Modifier.fillMaxSize().background(BgLight),
            contentAlignment = Alignment.Center
        ) {
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
                onDateChange = { currentDate = it }
            )
        }

        // ═══ تقویم ماهانه (روی هدر) ═══
        item {
            Box(Modifier.offset(y = (-30).dp)) {
                MonthCalendarView(
                    currentDate = currentDate,
                    primaryCalendar = primaryCalendar,
                    events = visibleEvents,
                    selectedDay = selectedDay,
                    onDayClick = { date ->
                        selectedDay = date
                        currentDate = date
                    },
                    onDateChange = { newDate ->
                        currentDate = newDate
                    },
                    showGregorianSmall = settings!!.showGregorianSmall,
                    showHijriSmall = settings!!.showHijriSmall,
                    eidFitrOffset = settings!!.eidFitrOffset,
                    eidFitrHijriYear = settings!!.eidFitrHijriYear
                )
            }
        }

        // ═══ اوقات شرعی ═══
        if (settings!!.showPrayerTimes && prayerTimes != null) {
            item {
                Spacer(Modifier.height(4.dp))
                PrayerTimesSection(prayerTimes = prayerTimes)
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

private fun filterEventsForDay(events: List<Event>, date: Date): List<Event> {
    val cal = Calendar.getInstance().apply { time = date }
    val gregorianMonth = cal.get(Calendar.MONTH) + 1
    val gregorianDay = cal.get(Calendar.DAY_OF_MONTH)

    val jalali = Jalali.toJalaliPublic(date.time)
    val jalaliMonth = jalali[1]
    val jalaliDay = jalali[2]

    val hijri = getHijriDate(date)
    val hijriMonth = hijri[1]
    val hijriDay = hijri[2]

    return events.filter { event ->
        when (event.calendarType) {
            CalendarType.JALALI -> event.month == jalaliMonth && event.day == jalaliDay
            CalendarType.GREGORIAN -> event.month == gregorianMonth && event.day == gregorianDay
            CalendarType.HIJRI -> event.month == hijriMonth && event.day == hijriDay
        }
    }
}
