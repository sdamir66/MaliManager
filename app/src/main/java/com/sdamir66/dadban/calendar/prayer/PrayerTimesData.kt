package com.sdamir66.dadban.calendar.prayer

/**
 * اوقات شرعی یک روز
 */
data class PrayerTimesData(
    val fajr: String,        // اذان صبح
    val sunrise: String,     // طلوع آفتاب
    val dhuhr: String,       // اذان ظهر
    val asr: String,         // عصر
    val maghrib: String,     // اذان مغرب (با زاویه 4.5 درجه)
    val isha: String,        // اذان عشا
    val dateMillis: Long,
    val latitude: Double,
    val longitude: Double
)
