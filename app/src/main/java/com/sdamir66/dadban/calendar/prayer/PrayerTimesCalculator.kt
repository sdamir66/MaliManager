package com.sdamir66.dadban.calendar.prayer

import java.util.Calendar
import java.util.Date
import java.util.TimeZone

object PrayerTimesCalculator {

    /**
     * محاسبه اوقات شرعی به روش رسمی مؤسسه ژئوفیزیک دانشگاه تهران
     *
     * پارامترها (تنظیم‌شده برای هماهنگی کامل با time.ir):
     * - Fajr Angle: 18 درجه
     * - Isha Angle: 14 درجه
     * - Maghrib Angle: 4.5 درجه (ذهاب حمره مشرقیه)
     * - Midnight: Jafari
     * - Asr: Shafii (ضریب سایه = 1)
     */
    fun calculate(
        latitude: Double,
        longitude: Double,
        date: Date = Date(),
        cityName: String = ""
    ): PrayerTimesData {

        // ═══ ۱. ساخت نمونه PrayTimes ═══
        val prayTimes = PrayTimes()

        // ═══ ۲. تنظیم مختصات ═══
        prayTimes.setCoordinates(latitude, longitude, 0.0)

        // ═══ ۳. تنظیم تاریخ ═══
        val cal = Calendar.getInstance().apply { time = date }
        prayTimes.setDate(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH)
        )

        // ═══ ۴. روش محاسبه: Tehran ═══
        prayTimes.setMethod(Method.TEHRAN)

        // ═══ ۵. اسر شافعی (جعفری) ═══
        prayTimes.setAsrJuristic(Constants.JURISTIC_STANDARD)

        // ═══ ۶. نیمه‌شب جعفری ═══
        prayTimes.setMidnightMode(Constants.MIDNIGHT_JAFARI)

        // ═══ ۷. منطقه زمانی ایران ═══
        prayTimes.setTimezone(TimeZone.getTimeZone("Asia/Tehran"))

        // ═══ ۸. تنظیم عرض‌های بالا ═══
        prayTimes.setHighLatsAdjustment(Constants.HIGHLAT_ANGLEBASED)

        // ═══ ۹. ✅ تنظیم زاویه فجر به 18 درجه (هماهنگ با time.ir) ═══
        prayTimes.setFajrDegrees(18.0)

        // ═══ ۱۰. ✅ تنظیم اذان مغرب به 4.5 درجه (ذهاب حمره مشرقیه) ═══
        prayTimes.setMaghribTime(4.5, false)  // false = بر حسب درجه

        // ═══ ۱۱. استخراج اوقات ═══
        val fajr = prayTimes.getTime(Constants.TIMES_FAJR)
        val sunrise = prayTimes.getTime(Constants.TIMES_SUNRISE)
        val dhuhr = prayTimes.getTime(Constants.TIMES_DHUHR)
        val asr = prayTimes.getTime(Constants.TIMES_ASR)
        val sunset = prayTimes.getTime(Constants.TIMES_SUNSET)    // ← غروب واقعی آفتاب
        val maghrib = prayTimes.getTime(Constants.TIMES_MAGHRIB)  // ← اذان مغرب
        val isha = prayTimes.getTime(Constants.TIMES_ISHA)
        val midnight = prayTimes.getTime(Constants.TIMES_MIDNIGHT)

        return PrayerTimesData(
            fajr = fajr,
            sunrise = sunrise,
            dhuhr = dhuhr,
            asr = asr,
            sunset = sunset,
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
