package com.sdamir66.dadban.calendar.prayer

import com.batoulapps.adhan.CalculationMethod
import com.batoulapps.adhan.CalculationParameters
import com.batoulapps.adhan.Coordinates
import com.batoulapps.adhan.Madhab
import com.batoulapps.adhan.data.DateComponents
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object PrayerTimesCalculator {

    fun calculate(
        latitude: Double,
        longitude: Double,
        date: Date = Date()
    ): PrayerTimesData {
        
        val coordinates = Coordinates(latitude, longitude)
        val dateComponents = DateComponents.from(date)
        
        // ✅ روش شیعه اثناعشری (Jafari)
        val params = CalculationParameters(16.0, 14.0).apply {
            method = CalculationMethod.OTHER
            madhab = Madhab.SHAFI  // مطابق فقه جعفری
        }
        
        val times = com.batoulapps.adhan.PrayerTimes(
            coordinates,
            dateComponents,
            params
        )
        
        val formatter = SimpleDateFormat("HH:mm", Locale.US).apply {
            timeZone = TimeZone.getDefault()
        }
        
        return PrayerTimesData(
            fajr = formatter.format(times.fajr),
            sunrise = formatter.format(times.sunrise),
            dhuhr = formatter.format(times.dhuhr),
            asr = formatter.format(times.asr),
            maghrib = formatter.format(times.maghrib),
            isha = formatter.format(times.isha),
            dateMillis = date.time,
            latitude = latitude,
            longitude = longitude
        )
    }
}
