package com.sdamir66.dadban.calendar.prayer

import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object PrayerTimesCalculator {

    /**
     * محاسبه اوقات شرعی به روش رسمی مؤسسه ژئوفیزیک دانشگاه تهران
     *
     * پارامترهای روش Tehran:
     * - Fajr Angle: 17.7 درجه
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

        // ═══ ۴. تنظیم روش محاسبه به تهران ═══
        prayTimes.setMethod(Method.TEHRAN)

        // ═══ ۵. تنظیم اسر به شافعی (مطابق فقه جعفری) ═══
        prayTimes.setAsrJuristic(Constants.JURISTIC_STANDARD)

        // ═══ ۶. تنظیم نیمه‌شب جعفری ═══
        prayTimes.setMidnightMode(Constants.MIDNIGHT_JAFARI)

        // ═══ ۷. تنظیم منطقه زمانی ایران ═══
        prayTimes.setTimezone(TimeZone.getTimeZone("Asia/Tehran"))

        // ═══ ۸. تنظیم عرض‌های بالا ═══
        prayTimes.setHighLatsAdjustment(Constants.HIGHLAT_ANGLEBASED)

        // ═══ ۹. استخراج اوقات ═══
        val fajr = prayTimes.getTime(Constants.TIMES_FAJR)
        val sunrise = prayTimes.getTime(Constants.TIMES_SUNRISE)
        val dhuhr = prayTimes.getTime(Constants.TIMES_DHUHR)
        val asr = prayTimes.getTime(Constants.TIMES_ASR)
        val maghrib = prayTimes.getTime(Constants.TIMES_MAGHRIB)  // ← اذان مغرب با زاویه 4.5
        val isha = prayTimes.getTime(Constants.TIMES_ISHA)
        val midnight = prayTimes.getTime(Constants.TIMES_MIDNIGHT) // ← نیمه‌شب جعفری

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
