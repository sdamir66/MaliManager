package com.sdamir66.dadban.calendar.prayer

import com.batoulapps.adhan.CalculationMethod
import com.batoulapps.adhan.CalculationParameters
import com.batoulapps.adhan.Coordinates
import com.batoulapps.adhan.Madhab
import com.batoulapps.adhan.data.DateComponents
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object PrayerTimesCalculator {

    /**
     * محاسبه‌ی اوقات شرعی به روش مؤسسه ژئوفیزیک دانشگاه تهران
     *
     * - زاویه فجر: 17.7 درجه
     * - زاویه عشا: 14 درجه
     * - اذان مغرب: غروب + 15 دقیقه (احتیاط برای ذهاب حمره مشرقیه)
     * - نیمه‌شب شرعی: وسط بین اذان مغرب و اذان صبح فردا
     * - اسر: شافعی (ضریب سایه = 1، مطابق فقه جعفری)
     */
    fun calculate(
        latitude: Double,
        longitude: Double,
        date: Date = Date(),
        cityName: String = ""
    ): PrayerTimesData {

        // ═══ ۱. مختصات ═══
        val coordinates = Coordinates(latitude, longitude)
        val dateComponents = DateComponents.from(date)

        // ═══ ۲. پارامترهای ژئوفیزیک تهران ═══
        val params = CalculationParameters(
            /* fajrAngle = */ 17.7,
            /* ishaAngle = */ 14.0
        ).apply {
            method = CalculationMethod.OTHER
            madhab = Madhab.SHAFI
        }

        // ═══ ۳. محاسبه اوقات امروز ═══
        val times = com.batoulapps.adhan.PrayerTimes(
            coordinates,
            dateComponents,
            params
        )

        // ═══ ۴. محاسبه اوقات فردا (برای نیمه‌شب شرعی) ═══
        val tomorrow = Calendar.getInstance().apply {
            time = date
            add(Calendar.DAY_OF_YEAR, 1)
        }.time

        val tomorrowTimes = com.batoulapps.adhan.PrayerTimes(
            coordinates,
            DateComponents.from(tomorrow),
            params
        )

        // ═══ ۵. فرمت‌کننده ═══
        val formatter = SimpleDateFormat("HH:mm", Locale.US).apply {
            timeZone = TimeZone.getDefault()
        }

        // ═══ ۶. اذان مغرب (غروب + 15 دقیقه احتیاط) ═══
        val maghribMillis = times.maghrib.time + (15 * 60 * 1000L)

        // ═══ ۷. نیمه‌شب شرعی ═══
        // نیمه‌شب = وسط بین اذان مغرب و اذان صبح روز بعد
        val fajrTomorrowMillis = tomorrowTimes.fajr.time
        val midnightMillis = maghribMillis + (fajrTomorrowMillis - maghribMillis) / 2

        return PrayerTimesData(
            fajr = formatter.format(times.fajr),
            sunrise = formatter.format(times.sunrise),
            dhuhr = formatter.format(times.dhuhr),
            asr = formatter.format(times.asr),
            maghrib = formatter.format(Date(maghribMillis)),
            isha = formatter.format(times.isha),
            midnight = formatter.format(Date(midnightMillis)),
            dateMillis = date.time,
            latitude = latitude,
            longitude = longitude,
            cityName = cityName
        )
    }
}
