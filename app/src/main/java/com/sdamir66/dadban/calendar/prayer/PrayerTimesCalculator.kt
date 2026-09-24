package com.sdamir66.dadban.calendar.prayer

import io.saeid.oghat.PrayTime
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object PrayerTimesCalculator {

    /**
     * محاسبه اوقات شرعی به روش رسمی مؤسسه ژئوفیزیک دانشگاه تهران
     * 
     * کتابخانه Oghat به طور پیش‌فرض از روش Tehran استفاده می‌کند.
     */
    fun calculate(
        latitude: Double,
        longitude: Double,
        date: Date = Date(),
        cityName: String = ""
    ): PrayerTimesData {

        // ═══ ۱. ساخت نمونه PrayTime ═══
        val prayerTime = PrayTime.getInstance()

        // ═══ ۲. تنظیم تاریخ ═══
        val cal = Calendar.getInstance().apply { time = date }
        prayerTime.setDate(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))

        // ═══ ۳. تنظیم مختصات جغرافیایی ═══
        prayerTime.setLatLong(latitude, longitude)

        // ═══ ۴. تنظیم منطقه زمانی ایران (+3.5) ═══
        val timezone = 3.5
        prayerTime.setTimeZone(timezone)

        // ═══ ۵. تنظیم روش محاسبه (به طور پیش‌فرض Tehran است) ═══
        prayerTime.setCalculationType(PrayTime.CalculationType.TEHRAN)

        // ═══ ۶. تنظیم روش اسر به Standard (شافعی/جعفری) ═══
        prayerTime.setJuristicType(PrayTime.JuristicType.SHAFII)

        // ═══ ۷. دریافت اوقات ═══
        val times = prayerTime.getPrayerTimes()

        // ═══ ۸. استخراج اوقات ═══
        return PrayerTimesData(
            fajr = times.fajr,
            sunrise = times.sunrise,
            dhuhr = times.dhuhr,
            asr = times.asr,
            maghrib = times.maghrib,     // ← اذان مغرب با زاویه 4.5
            isha = times.isha,
            midnight = times.midnight,    // ← نیمه‌شب جعفری
            dateMillis = date.time,
            latitude = latitude,
            longitude = longitude,
            cityName = cityName
        )
    }
}
