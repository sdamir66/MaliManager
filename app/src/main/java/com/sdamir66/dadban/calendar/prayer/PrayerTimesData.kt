package com.sdamir66.dadban.calendar.prayer

/**
 * اوقات شرعی یک روز (به روش مؤسسه ژئوفیزیک دانشگاه تهران)
 */
data class PrayerTimesData(
    val fajr: String,          // اذان صبح
    val sunrise: String,       // طلوع آفتاب
    val dhuhr: String,         // اذان ظهر
    val asr: String,           // عصر
    val maghrib: String,       // اذان مغرب (با احتیاط)
    val isha: String,          // اذان عشا
    val midnight: String,      // نیمه‌شب شرعی
    val dateMillis: Long,
    val latitude: Double,
    val longitude: Double,
    val cityName: String = ""
)
