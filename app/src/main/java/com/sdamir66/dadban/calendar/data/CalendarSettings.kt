package com.sdamir66.dadban.calendar.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class LocationMode {
    GPS,
    MANUAL
}

@Entity(tableName = "calendar_settings")
data class CalendarSettings(
    @PrimaryKey val id: Int = 1,

    // ═══ اصلاح عید فطر ═══
    val eidFitrOffset: Int = 0,
    val eidFitrHijriYear: Int? = null,

    // ═══ نمایش تقویم ═══
    val defaultCalendar: CalendarType = CalendarType.JALALI,
    val showGregorianSmall: Boolean = true,
    val showHijriSmall: Boolean = true,

    // ═══ نمایش رویدادها (پیش‌فرض همه روشن) ═══
    val showHolidays: Boolean = true,
    val showReligiousNonHoliday: Boolean = true,      // ← روشن
    val showNationalNonHoliday: Boolean = true,       // ← روشن
    val showGlobalEvents: Boolean = true,             // ← روشن
    val showUserEvents: Boolean = true,

    // ═══ رنگ رویدادها ═══
    val colorHoliday: String = "#E53935",
    val colorReligious: String = "#4CAF50",
    val colorNational: String = "#2196F3",
    val colorGlobal: String = "#9C27B0",
    val colorUser: String = "#9E9E9E",

    // ═══ موقعیت مکانی ═══
    val locationMode: LocationMode = LocationMode.MANUAL,
    val cityName: String = "تهران",
    val latitude: Double = 35.6892,
    val longitude: Double = 51.3890,

    // ═══ اوقات شرعی ═══
    val showPrayerTimes: Boolean = true
)
