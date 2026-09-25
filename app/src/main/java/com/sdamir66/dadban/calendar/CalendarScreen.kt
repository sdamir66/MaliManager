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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.sdamir66.dadban.calendar.components.*
import com.sdamir66.dadban.calendar.data.*
import com.sdamir66.dadban.calendar.prayer.PrayerTimesCalculator
import com.sdamir66.dadban.data.AppDb
import com.sdamir66.dadban.ui.theme.BgLight
import com.sdamir66.dadban.util.Jalali
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Date

@Composable
fun CalendarScreen(db: AppDb) {
    val context = LocalContext.current

    var settings by remember { mutableStateOf<CalendarSettings?>(null) }
    var hijriCacheMap by remember { mutableStateOf<Map<String, HijriCache>>(emptyMap()) }

    LaunchedEffect(Unit) {
        settings = db.calendarSettingsDao().getNow() ?: CalendarSettings()
    }

    // ✅ بارگذاری cache تقویم قمری
    // اولویت: دیتابیس → asset
    LaunchedEffect(settings) {
        if (settings != null) {
            withContext(Dispatchers.IO) {
                val year = Jalali.nowJalali()[0]

                // ۱. دیتابیس (شامل همه‌ی روزها از API)
                val dbItems = db.hijriCacheDao().getAllForYear(year)

                // ۲. اگه دیتابیس خالی بود، از asset
                val finalItems = if (dbItems.isNotEmpty()) {
                    dbItems
                } else {
                    HijriOfficialData.ensureLoaded(context)
                    HijriOfficialData.getAllCaches()
                        .filter { it.jalaliYear == year }
                }

                hijriCacheMap = finalItems.associateBy { it.jalaliDate }
            }
        }
    }

    var currentDate by remember { mutableStateOf(Date()) }
    var primaryCalendar by remember { mutableStateOf(CalendarType.JALALI) }
    var selectedDay by remember { mutableStateOf<Date?>(null) }

    LaunchedEffect(settings) {
        if (settings != null) primaryCalendar = settings!!.defaultCalendar
    }

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

    val allEvents by db.eventDao().all().collectAsState(emptyList())

    val visibleEvents = allEvents.filter { event ->
        when {
            event.isHoliday -> settings?.showHolidays ?: true
            event.category == EventCategory.RELIGIOUS -> settings?.showReligiousNonHoliday ?: true
            event.category == EventCategory.NATIONAL -> settings?.showNationalNonHoliday ?: true
            event.category == EventCategory.GLOBAL -> settings?.showGlobalEvents ?: false
            event.isUserCreated -> settings?.showUserEvents ?: true
            else -> false
        }
    }

    if (settings == null) {
        Box(Modifier.fillMaxSize().background(BgLight), contentAlignment = Alignment.Center) {
            Text("در حال بارگذاری...", color = Color.Gray)
        }
        return
    }

    LazyColumn(Modifier.fillMaxSize().background(BgLight)) {
        item {
            CalendarHeader(
                currentDate = currentDate,
                primaryCalendar = primaryCalendar,
                settings = settings,
                hijriCacheMap = hijriCacheMap,
                onDateChange = { currentDate = it },
                onCalendarTypeChange = { primaryCalendar = it }
            )
        }

        item {
            Box(Modifier.offset(y = (-30).dp)) {
                MonthCalendarView(
                    currentDate = currentDate,
                    primaryCalendar = primaryCalendar,
                    settings = settings,
                    hijriCacheMap = hijriCacheMap,
                    events = visibleEvents,
                    selectedDay = selectedDay,
                    onDayClick = { date ->
                        selectedDay = date
                        currentDate = date
                    },
                    onDateChange = { newDate -> currentDate = newDate }
                )
            }
        }

        if (settings!!.showPrayerTimes && prayerTimes != null) {
            item {
                Box(Modifier.offset(y = (-25).dp)) {
                    PrayerTimesSection(prayerTimes = prayerTimes)
                }
            }
        }

        item {
            Box(Modifier.offset(y = if (settings!!.showPrayerTimes) (-15).dp else 0.dp)) {
                EventsSection(
                    events = visibleEvents,
                    selectedDate = selectedDay ?: currentDate,
                    primaryCalendar = primaryCalendar,
                    settings = settings,
                    hijriCacheMap = hijriCacheMap
                )
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}
