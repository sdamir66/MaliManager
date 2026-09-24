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

    // ═══ نمایش رویدادها ═══
    val showHolidays: Boolean = true,
    val showReligiousNonHoliday: Boolean = false,
    val showNationalNonHoliday: Boolean = false,
    val showGlobalEvents: Boolean = false,
    val showUserEvents: Boolean = true,

    // ═══ رنگ رویدادها (قابل تنظیم) ═══
    val colorHoliday: String = "#E53935",      // قرمز (تعطیلات رسمی)
    val colorReligious: String = "#9E9E9E",    // خاکستری (مذهبی غیرتعطیل)
    val colorNational: String = "#9E9E9E",     // خاکستری (ملی غیرتعطیل)
    val colorGlobal: String = "#9E9E9E",       // خاکستری (جهانی)
    val colorUser: String = "#9E9E9E",         // خاکستری (کاربر)

    // ═══ موقعیت مکانی ═══
    val locationMode: LocationMode = LocationMode.MANUAL,
    val cityName: String = "تهران",
    val latitude: Double = 35.6892,
    val longitude: Double = 51.3890,

    // ═══ اوقات شرعی ═══
    val showPrayerTimes: Boolean = true
)
