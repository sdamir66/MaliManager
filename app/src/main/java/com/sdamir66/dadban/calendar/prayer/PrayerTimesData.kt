package com.sdamir66.dadban.calendar.prayer

data class PrayerTimesData(
    val fajr: String,
    val sunrise: String,
    val dhuhr: String,
    val asr: String,
    val maghrib: String,
    val isha: String,
    val dateMillis: Long,
    val latitude: Double,
    val longitude: Double
)
