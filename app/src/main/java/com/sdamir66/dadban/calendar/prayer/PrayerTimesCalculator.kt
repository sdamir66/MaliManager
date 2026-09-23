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
     * - زاویه مغرب: 4.5 درجه (ذهاب حمره مشرقیه)
     * - اسر: شافعی (ضریب سایه = 1، مطابق فقه جعفری)
     */
    fun calculate(
        latitude: Double,
        longitude: Double,
        date: Date = Date()
    ): PrayerTimesData {

        // ═══ ۱. مختصات جغرافیایی ═══
        val coordinates = Coordinates(latitude, longitude)

        // ═══ ۲. تاریخ میلادی ═══
        val dateComponents = DateComponents.from(date)

        // ═══ ۳. تنظیم پارامترها برای روش ژئوفیزیک تهران ═══
        val params = CalculationParameters(
            /* fajrAngle = */ 17.7,
            /* ishaAngle = */ 14.0
        ).apply {
            method = CalculationMethod.OTHER
            maghribAngle = 4.5  // ← زاویه مغرب مؤسسه ژئوفیزیک
            madhab = Madhab.SHAFI  // مطابق فقه جعفری
        }

        // ═══ ۴. محاسبه ═══
        val times = com.batoulapps.adhan.PrayerTimes(
            coordinates,
            dateComponents,
            params
        )

        // ═══ ۵. قالب‌بندی ساعت به وقت محلی ═══
        val formatter = SimpleDateFormat("HH:mm", Locale.US).apply {
            timeZone = TimeZone.getDefault()
        }

        return PrayerTimesData(
            fajr = formatter.format(times.fajr),
            sunrise = formatter.format(times.sunrise),
            dhuhr = formatter.format(times.dhuhr),
            asr = formatter.format(times.asr),
            maghrib = formatter.format(times.maghrib),  // ← با maghribAngle محاسبه می‌شود
            isha = formatter.format(times.isha),
            dateMillis = date.time,
            latitude = latitude,
            longitude = longitude
        )
    }
}
