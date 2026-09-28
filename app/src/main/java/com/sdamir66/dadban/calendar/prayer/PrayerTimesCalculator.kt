package com.sdamir66.dadban.calendar.prayer

import java.util.Calendar
import java.util.Date
import java.util.TimeZone

object PrayerTimesCalculator {

    /**
     * محاسبه اوقات شرعی به روش رسمی مؤسسه ژئوفیزیک دانشگاه تهران
     *
     * پارامترهای Method.TEHRAN (از Parameters.java):
     * - Fajr Angle: 17.7 درجه
     * - Isha Angle: 14 درجه
     * - Maghrib Angle: 4.5 درجه (ذهاب حمره مشرقیه)
     * - Midnight: Jafari
     * - Asr: Shafii (ضریب سایه = 1)
     *
     * ⚠️ هیچ‌کدوم از این پارامترها نباید دستی override بشن،
     * چون Parameters.setMethod خودش تنظیمشون می‌کنه.
     */
    fun calculate(
        latitude: Double,
        longitude: Double,
        date: Date = Date(),
        cityName: String = ""
    ): PrayerTimesData {

        // ═══ ۱. ساخت PrayTimes (constructor خودش Method.TEHRAN رو اعمال می‌کنه) ═══
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

        // ═══ ۴. روش محاسبه: Tehran (مؤسسه ژئوفیزیک) ═══
        // ✅ این خط تکراریه (چون constructor خودش TEHRAN داره)
        //    ولی برای اطمینان می‌ذاریمش.
        prayTimes.setMethod(Method.TEHRAN)

        // ═══ ۵. ✅ منطقه‌ی زمانی دستگاه کاربر ═══
        prayTimes.setTimezone(TimeZone.getDefault())

        // ═══ ۶. تنظیم عرض‌های بالا (برای شهرهای شمالی) ═══
        prayTimes.setHighLatsAdjustment(Constants.HIGHLAT_ANGLEBASED)

        // ═══ ۷. استخراج اوقات ═══
        val fajr = prayTimes.getTime(Constants.TIMES_FAJR)
        val sunrise = prayTimes.getTime(Constants.TIMES_SUNRISE)
        val dhuhr = prayTimes.getTime(Constants.TIMES_DHUHR)
        val asr = prayTimes.getTime(Constants.TIMES_ASR)
        val sunset = prayTimes.getTime(Constants.TIMES_SUNSET)
        val maghrib = prayTimes.getTime(Constants.TIMES_MAGHRIB)
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
