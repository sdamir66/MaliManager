package com.sdamir66.dadban.calendar.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * نوع تقویم برای تکرار رویداد
 */
enum class CalendarType {
    JALALI,      // شمسی
    HIJRI,       // قمری
    GREGORIAN    // میلادی
}

/**
 * دسته‌بندی رویداد
 */
enum class EventCategory {
    RELIGIOUS,   // مذهبی
    NATIONAL,    // ملی
    GLOBAL,      // جهانی
    PERSONAL     // کاربر
}

/**
 * رویداد تقویم
 */
@Entity(tableName = "events")
data class Event(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    
    val title: String,
    val description: String = "",
    
    // نوع تقویم مبدأ (تاریخ رویداد بر اساس این تقویم تکرار میشه)
    val calendarType: CalendarType,
    
    // ماه و روز در تقویم مبدأ (۱ تا ۱۲، ۱ تا ۳۱)
    val month: Int,
    val day: Int,
    
    // سال (فقط برای رویدادهای یک‌باره، null = هر سال)
    val year: Int? = null,
    
    // تعطیل رسمی؟
    val isHoliday: Boolean = false,
    
    // دسته‌بندی
    val category: EventCategory,
    
    // رنگ (برای رویدادهای کاربر)
    val color: String = "#4C5FD7",
    
    // رویداد ساخته‌شده توسط کاربر؟
    val isUserCreated: Boolean = false,
    
    // یادآور (دقیقه قبل از رویداد، null = بدون یادآور)
    val reminderMinutesBefore: Int? = null
)
