package com.sdamir66.dadban.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    var refreshTrigger by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        settings = db.calendarSettingsDao().getNow() ?: CalendarSettings()
    }

    // ✅ بارگذاری cache تقویم قمری — اولویت: دیتابیس → asset
    LaunchedEffect(settings, refreshTrigger) {
        if (settings != null) {
            withContext(Dispatchers.IO) {
                val year = Jalali.nowJalali()[0]

                // ۱. دیتابیس (شامل همه‌ی روزها از API)
                val dbItems = db.hijriCacheDao().getAllForYear(year)

                // ═══ DEBUG ═══
                android.util.Log.d("HijriDebug", "=== Year $year ===")
                android.util.Log.d("HijriDebug", "dbItems.size = ${dbItems.size}")
                if (dbItems.isNotEmpty()) {
                    android.util.Log.d("HijriDebug", "First: ${dbItems.first().jalaliDate} → ${dbItems.first().hijriDay} ${dbItems.first().hijriMonth} ${dbItems.first().hijriYear}")
                    android.util.Log.d("HijriDebug", "Last: ${dbItems.last().jalaliDate} → ${dbItems.last().hijriDay} ${dbItems.last().hijriMonth} ${dbItems.last().hijriYear}")
                }
                // ═══════════

                // ۲. اگه دیتابیس خالی بود، از asset
                val finalItems = if (dbItems.isNotEmpty()) {
                    dbItems
                } else {
                    HijriOfficialData.ensureLoaded(context)
                    HijriOfficialData.getAllCaches()
                        .filter { it.jalaliYear == year }
                }

                hijriCacheMap = finalItems.associateBy { it.jalaliDate }
                android.util.Log.d("HijriDebug", "hijriCacheMap.size = ${hijriCacheMap.size}")
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

    // ═══════════════════════════════════════════════════
    // ═══ DEBUG: نمایش وضعیت hijriCacheMap ═══
    // ═══════════════════════════════════════════════════
    val debugInfo = remember(hijriCacheMap) {
        val nowJalali = Jalali.nowJalali()
        val year = nowJalali[0]
        val month = nowJalali[1]
        val day = nowJalali[2]

        val yearItems = hijriCacheMap.filterKeys { it.startsWith("$year/") }

        val todayKey = String.format("%04d/%02d/%02d", year, month, day)

        val todayCache = hijriCacheMap[todayKey]

        """
        🔍 Debug Info:
        - hijriCacheMap size: ${hijriCacheMap.size}
        - year $year items: ${yearItems.size}
        - today key: $todayKey
        - today cache: ${todayCache?.let { "${it.hijriDay} ${it.hijriMonth} ${it.hijriYear}" } ?: "NULL"}
        - sample key: ${hijriCacheMap.keys.firstOrNull() ?: "empty"}
        - sample value: ${hijriCacheMap.values.firstOrNull()?.let { "${it.hijriDay} ${it.hijriMonth} ${it.hijriYear}" } ?: "empty"}
        """.trimIndent()
    }

    LazyColumn(Modifier.fillMaxSize().background(BgLight)) {
        // ═══ DEBUG ═══
        item {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFFFF3E0))
                    .padding(8.dp)
            ) {
                Text(
                    debugInfo,
                    fontSize = 10.sp,
                    color = Color.Red,
                    lineHeight = 14.sp
                )
            }
        }

        // هدر
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

        // تقویم ماهانه
        item {
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

        // اوقات شرعی
        if (settings!!.showPrayerTimes && prayerTimes != null) {
            item {
                Box(Modifier.offset(y = 8.dp)) {
                    PrayerTimesSection(prayerTimes = prayerTimes)
                }
            }
        }

        // رویدادهای روز
        item {
            Box(Modifier.offset(y = if (settings!!.showPrayerTimes) 18.dp else 8.dp)) {
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
