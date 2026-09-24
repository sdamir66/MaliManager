package com.sdamir66.dadban.calendar.prayer

/**
 * اوقات شرعی یک روز (به روش مؤسسه ژئوفیزیک دانشگاه تهران)
 */
data class PrayerTimesData(
    val fajr: String,          // اذان صبح (زاویه 18)
    val sunrise: String,       // طلوع آفتاب
    val dhuhr: String,         // اذان ظهر
    val asr: String,           // عصر (شافعی/جعفری)
    val sunset: String,        // غروب آفتاب (زاویه 0.833)
    val maghrib: String,       // اذان مغرب (زاویه 4.5)
    val isha: String,          // اذان عشا (زاویه 14)
    val midnight: String,      // نیمه‌شب شرعی (جعفری)
    val dateMillis: Long,
    val latitude: Double,
    val longitude: Double,
    val cityName: String = ""
)
