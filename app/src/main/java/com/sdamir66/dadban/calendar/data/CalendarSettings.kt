package com.sdamir66.dadban.calendar.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * حالت موقعیت مکانی
 */
enum class LocationMode {
    GPS,
    MANUAL
}

/**
 * تنظیمات تقویم و اوقات شرعی
 */
@Entity(tableName = "calendar_settings")
data class CalendarSettings(
    @PrimaryKey val id: Int = 1,
    
    // ═══ اصلاح عید فطر ═══
    // 0 = بدون تغییر، +1 = یک روز جلو، -1 = یک روز عقب
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
    
    // ═══ موقعیت مکانی ═══
    val locationMode: LocationMode = LocationMode.MANUAL,
    val cityName: String = "تهران",
    val latitude: Double = 35.6892,
    val longitude: Double = 51.3890,
    
    // ═══ اوقات شرعی ═══
    val showPrayerTimes: Boolean = true
)
