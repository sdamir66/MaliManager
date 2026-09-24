package com.sdamir66.dadban.calendar.prayer

/**
 * اوقات شرعی یک روز (به روش مؤسسه ژئوفیزیک دانشگاه تهران)
 */
data class PrayerTimesData(
    val fajr: String,          // اذان صبح (زاویه 17.7)
    val sunrise: String,       // طلوع آفتاب
    val dhuhr: String,         // اذان ظهر
    val asr: String,           // عصر (شافعی/جعفری)
    val maghrib: String,       // اذان مغرب (زاویه 4.5 - ذهاب حمره مشرقیه)
    val isha: String,          // اذان عشا (زاویه 14)
    val midnight: String,      // نیمه‌شب شرعی (جعفری)
    val dateMillis: Long,
    val latitude: Double,
    val longitude: Double,
    val cityName: String = ""
)
