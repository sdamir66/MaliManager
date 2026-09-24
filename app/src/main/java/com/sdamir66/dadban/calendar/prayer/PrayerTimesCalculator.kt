package com.sdamir66.dadban.calendar.prayer

import com.github.persian.calendar.praytimes.PrayTime
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object PrayerTimesCalculator {

    /**
     * محاسبه اوقات شرعی به روش رسمی مؤسسه ژئوفیزیک دانشگاه تهران
     *
     * پارامترها (روش Tehran در PrayTime):
     * - Fajr Angle: 17.7 درجه
     * - Isha Angle: 14 درجه
     * - Maghrib Angle: 4.5 درجه (ذهاب حمره مشرقیه)
     * - Midnight: Jafari
     * - Asr: Standard (ضریب سایه = 1، مطابق فقه جعفری)
     */
    fun calculate(
        latitude: Double,
        longitude: Double,
        date: Date = Date(),
        cityName: String = ""
    ): PrayerTimesData {

        // ═══ ۱. ساخت نمونه PrayTime ═══
        val prayTime = PrayTime()

        // ═══ ۲. تنظیم روش محاسبه به Tehran (مؤسسه ژئوفیزیک) ═══
        prayTime.setCalcMethod(PrayTime.Tehran)

        // ═══ ۳. تنظیم روش اسر به Standard (شافعی/جعفری) ═══
        prayTime.setAsrJuristic(PrayTime.Standard)

        // ═══ ۴. تنظیم نیمه‌شب به Jafari ═══
        prayTime.setMidnight(PrayTime.Jafari)

        // ═══ ۵. فرمت ۲۴ ساعته ═══
        prayTime.setTimeFormat(PrayTime.Time24)

        // ═══ ۶. تنظیم منطقه زمانی ایران (+3.5) ═══
        val timezone = 3.5

        // ═══ ۷. تاریخ میلادی به Calendar ═══
        val cal = Calendar.getInstance().apply {
            time = date
        }

        // ═══ ۸. محاسبه اوقات ═══
        val prayerTimes: ArrayList<String> = prayTime.getPrayerTimes(
            cal,
            latitude,
            longitude,
            timezone
        )

        // ═══ ۹. استخراج اوقات ═══
        // ترتیب خروجی PrayTime:
        // 0: Fajr, 1: Sunrise, 2: Dhuhr, 3: Asr, 4: Sunset, 5: Maghrib, 6: Isha, 7: Midnight
        val fajr = prayerTimes.getOrNull(0) ?: "--:--"
        val sunrise = prayerTimes.getOrNull(1) ?: "--:--"
        val dhuhr = prayerTimes.getOrNull(2) ?: "--:--"
        val asr = prayerTimes.getOrNull(3) ?: "--:--"
        val maghrib = prayerTimes.getOrNull(5) ?: "--:--"  // ← اذان مغرب با زاویه 4.5
        val isha = prayerTimes.getOrNull(6) ?: "--:--"
        val midnight = prayerTimes.getOrNull(7) ?: "--:--"  // ← نیمه‌شب جعفری

        return PrayerTimesData(
            fajr = fajr,
            sunrise = sunrise,
            dhuhr = dhuhr,
            asr = asr,
            maghrib = maghrib,
            isha = isha,
            midnight = midnight,
            dateMillis = date.time,
            latitude = latitude,
            longitude = longitude,
            cityName = cityName
        )
    }
}
