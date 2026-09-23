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
     * محاسبه‌ی اوقات شرعی به روش شیعه اثناعشری (Jafari)
     *
     * - زاویه فجر: 16 درجه
     * - زاویه عشا: 14 درجه
     * - اسر: شافعی (ضریب سایه = 1، مطابق فقه جعفری)
     *
     * @param latitude عرض جغرافیایی
     * @param longitude طول جغرافیایی
     * @param date تاریخ (پیش‌فرض: امروز)
     * @return PrayerTimesData
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

        // ═══ ۳. تنظیم پارامترها برای شیعه اثناعشری ═══
        val params = CalculationParameters(
            /* fajrAngle = */ 16.0,
            /* ishaAngle = */ 14.0
        ).apply {
            method = CalculationMethod.OTHER
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
            maghrib = formatter.format(times.maghrib),
            isha = formatter.format(times.isha),
            dateMillis = date.time,
            latitude = latitude,
            longitude = longitude
        )
    }
}
