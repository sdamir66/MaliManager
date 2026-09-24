package com.sdamir66.dadban.calendar.prayer

import net.alhazmy13.PrayerTimes.PrayerTime
import java.util.Calendar
import java.util.Date
import java.util.Locale

object PrayerTimesCalculator {

    /**
     * محاسبه اوقات شرعی به روش رسمی مؤسسه ژئوفیزیک دانشگاه تهران
     *
     * پارامترهای روش Tehran در این کتابخانه:
     * - Fajr Angle: 17.7 درجه
     * - Isha Angle: 14 درجه
     * - Maghrib Angle: 4.5 درجه (ذهاب حمره مشرقیه)
     * - Midnight: Jafari (وسط مغرب تا فجر)
     * - Asr: Shafii (ضریب سایه = 1، مطابق فقه جعفری)
     */
    fun calculate(
        latitude: Double,
        longitude: Double,
        date: Date = Date(),
        cityName: String = ""
    ): PrayerTimesData {

        // ═══ ۱. ساخت نمونه PrayerTime ═══
        val prayers = PrayerTime()

        // ═══ ۲. تنظیم روش محاسبه به Tehran (مؤسسه ژئوفیزیک) ═══
        prayers.setCalcMethod(PrayerTime.Calculation.Tehran)

        // ═══ ۳. تنظیم روش اسر به Shafii (مطابق فقه جعفری) ═══
        prayers.setAsrJuristic(PrayerTime.Juristic.Shafii)

        // ═══ ۴. تنظیم روش عرض‌های بالا ═══
        prayers.setAdjustHighLats(PrayerTime.Adjusting.AngleBased)

        // ═══ ۵. آفست‌ها (همه صفر) ═══
        prayers.setOffsets(intArrayOf(0, 0, 0, 0, 0, 0, 0))

        // ═══ ۶. فرمت ۲۴ ساعته ═══
        prayers.setTimeFormat(PrayerTime.TimeFormat.Time24)

        // ═══ ۷. منطقه زمانی ایران (+3.5) ═══
        val timezone = 3.5

        // ═══ ۸. تاریخ میلادی به Calendar ═══
        val cal = Calendar.getInstance().apply {
            time = date
        }

        // ═══ ۹. محاسبه اوقات ═══
        val prayerTimes: ArrayList<String> = prayers.getPrayerTimes(
            cal,
            latitude,
            longitude,
            timezone
        )

        // ═══ ۱۰. استخراج اوقات ═══
        // ترتیب خروجی: Fajr, Sunrise, Dhuhr, Asr, Sunset, Maghrib, Isha
        val fajr = prayerTimes.getOrNull(0) ?: "--:--"
        val sunrise = prayerTimes.getOrNull(1) ?: "--:--"
        val dhuhr = prayerTimes.getOrNull(2) ?: "--:--"
        val asr = prayerTimes.getOrNull(3) ?: "--:--"
        val maghrib = prayerTimes.getOrNull(5) ?: "--:--"  // ← اذان مغرب با زاویه 4.5
        val isha = prayerTimes.getOrNull(6) ?: "--:--"

        // ═══ ۱۱. محاسبه نیمه‌شب شرعی (جعفری) ═══
        // نیمه‌شب = وسط بین اذان مغرب و اذان صبح
        val midnight = calculateMidnight(maghrib, fajr)

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

    /**
     * محاسبه نیمه‌شب شرعی (جعفری)
     * نیمه‌شب = وسط بین اذان مغرب و اذان صبح
     */
    private fun calculateMidnight(maghrib: String, fajr: String): String {
        return try {
            val maghribMinutes = timeToMinutes(maghrib)
            var fajrMinutes = timeToMinutes(fajr)

            // اگر اذان صبح فردا بعد از مغرب امروز است
            if (fajrMinutes < maghribMinutes) {
                fajrMinutes += 24 * 60
            }

            val midnightMinutes = (maghribMinutes + fajrMinutes) / 2
            minutesToTime(midnightMinutes % (24 * 60))
        } catch (e: Exception) {
            "--:--"
        }
    }

    private fun timeToMinutes(time: String): Int {
        val parts = time.split(":")
        val hours = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val minutes = parts.getOrNull(1)?.toIntOrNull() ?: 0
        return hours * 60 + minutes
    }

    private fun minutesToTime(totalMinutes: Int): String {
        val hours = (totalMinutes / 60) % 24
        val minutes = totalMinutes % 60
        return String.format(Locale.US, "%02d:%02d", hours, minutes)
    }
}
