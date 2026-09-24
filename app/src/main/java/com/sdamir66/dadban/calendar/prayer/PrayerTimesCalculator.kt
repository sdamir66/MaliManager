package com.sdamir66.dadban.calendar.prayer

import com.batoulapps.adhan.Coordinates
import com.batoulapps.adhan.data.DateComponents
import java.text.SimpleDateFormat
import java.util.*

// ⚠️ این import رو بعد از اضافه کردن PrayTime اضافه کن:
// import PrayTimes

object PrayerTimesCalculator {

    /**
     * محاسبه اوقات شرعی به روش رسمی مؤسسه ژئوفیزیک دانشگاه تهران
     * 
     * پارامترها:
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

        // ═══ ۱. استفاده از PrayTime با روش Tehran ═══
        val prayTime = PrayTimes()
        
        // ✅ تنظیم روش رسمی ژئوفیزیک تهران
        prayTime.setMethod("Tehran")
        
        // ✅ تنظیم نیمه‌شب به روش جعفری
        prayTime.adjust(mapOf(
            "midnight" to "Jafari",
            "asr" to "Standard"  // شافعی/جعفری (ضریب سایه = 1)
        ))

        // ═══ ۲. محاسبه اوقات ═══
        // آرایه مختصات: [عرض، طول، ارتفاع]
        val coordinates = doubleArrayOf(latitude, longitude, 0.0)
        
        // منطقه زمانی: برای ایران +3.5
        val timezone = 3.5
        
        // محاسبه (فرمت 24 ساعته)
        val times = prayTime.getTimes(
            date,
            coordinates,
            timezone,
            0,      // DST (ساعت تابستانی)
            "24h"   // فرمت
        )

        // ═══ ۳. استخراج اوقات ═══
        // PrayTime مقادیر رو به صورت map برمی‌گردونه:
        // "fajr", "sunrise", "dhuhr", "asr", "sunset", "maghrib", "isha", "midnight"
        
        return PrayerTimesData(
            fajr = times["fajr"] ?: "--:--",
            sunrise = times["sunrise"] ?: "--:--",
            dhuhr = times["dhuhr"] ?: "--:--",
            asr = times["asr"] ?: "--:--",
            maghrib = times["maghrib"] ?: "--:--",   // ← اذان مغرب با زاویه 4.5
            isha = times["isha"] ?: "--:--",
            midnight = times["midnight"] ?: "--:--", // ← نیمه‌شب جعفری
            dateMillis = date.time,
            latitude = latitude,
            longitude = longitude,
            cityName = cityName
        )
    }
}
