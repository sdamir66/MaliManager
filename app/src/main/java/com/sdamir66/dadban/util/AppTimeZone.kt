package com.sdamir66.dadban.util

import java.util.TimeZone

/**
 * TimeZone مشترک برای همه‌ی تبدیل‌های تاریخ
 * - همیشه TimeZone گوشی رو برمی‌گردونه
 * - همه‌ی فایل‌ها از این استفاده می‌کنن تا هماهنگ باشن
 */
object AppTimeZone {
    val instance: TimeZone
        get() = TimeZone.getDefault()
}
