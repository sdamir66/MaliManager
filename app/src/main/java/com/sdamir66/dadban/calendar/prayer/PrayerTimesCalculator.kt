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

    /**
     * محاسبه‌ی اوقات شرعی به روش مؤسسه ژئوفیزیک دانشگاه تهران
     *
     * - زاویه فجر: 17.7 درجه
     * - زاویه عشا: 14 درجه
     * - اذان مغرب: غروب + 15 دقیقه (معادل تقریبی زاویه 4.5 درجه)
     * - اسر: شافعی (ضریب سایه = 1، مطابق فقه جعفری)
     */
    fun calculate(
        latitude: Double,
        longitude: Double,
        date: Date = Date()
    ): PrayerTimesData {

        val coordinates = Coordinates(latitude, longitude)
        val dateComponents = DateComponents.from(date)

        // ═══ پارامترهای روش ژئوفیزیک تهران ═══
        val params = CalculationParameters(
            /* fajrAngle = */ 17.7,
            /* ishaAngle = */ 14.0
        ).apply {
            method = CalculationMethod.OTHER
            madhab = Madhab.SHAFI  // اسر جعفری
        }

        val times = com.batoulapps.adhan.PrayerTimes(
            coordinates,
            dateComponents,
            params
        )

        val formatter = SimpleDateFormat("HH:mm", Locale.US).apply {
            timeZone = TimeZone.getDefault()
        }

        // ═══ اذان مغرب = غروب + 15 دقیقه ═══
        // (معادل تقریبی زاویه 4.5 درجه در روش ژئوفیزیک)
        val maghribMillis = times.maghrib.time + (15 * 60 * 1000L)
        val maghribTime = formatter.format(Date(maghribMillis))

        return PrayerTimesData(
            fajr = formatter.format(times.fajr),
            sunrise = formatter.format(times.sunrise),
            dhuhr = formatter.format(times.dhuhr),
            asr = formatter.format(times.asr),
            maghrib = maghribTime,
            isha = formatter.format(times.isha),
            dateMillis = date.time,
            latitude = latitude,
            longitude = longitude
        )
    }
}
