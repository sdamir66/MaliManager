package com.sdamir66.dadban.calendar.data

import android.content.Context

// ═══════════════════════════════════════════════════════════════
//  HijriOfficialData
//  داده‌های رسمی تقویم قمری ایران (مؤسسه ژئوفیزیک دانشگاه تهران)
//  منبع: فایل hijri_official.txt در assets
// ═══════════════════════════════════════════════════════════════

object HijriOfficialData {

    private const val ASSET_FILE = "hijri_official.txt"

    // ═══ cache در حافظه (بعد از اولین بارگذاری) ═══
    @Volatile
    private var loaded = false

    // jalaliDate ("1404/04/06") → HijriCache
    private val byJalaliDate = mutableMapOf<String, HijriCache>()

    // "hijriYear/hijriMonth" ("1447/1") → jalaliDate ("1404/04/06")
    private val startsByHijriKey = mutableMapOf<String, String>()

    // سال‌های قمری که توی فایل رسمی هستن
    private val availableYears = mutableSetOf<Int>()

    // ═══════════════════════════════════════════════════════════
    //  بارگذاری داده‌ها از asset (فقط یک بار)
    // ═══════════════════════════════════════════════════════════
    @Synchronized
    fun ensureLoaded(context: Context) {
        if (loaded) return
        try {
            val text = context.assets.open(ASSET_FILE)
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }
            parseAndFill(text)
            loaded = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  پارس فایل و پر کردن map ها
    // ═══════════════════════════════════════════════════════════
    private fun parseAndFill(text: String) {
        for (rawLine in text.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) continue

            // فرمت هر خط: "1447/1 1404/04/06"
            val parts = line.split(Regex("\\s+"))
            if (parts.size < 2) continue

            val hijriKey = parts[0]              // "1447/1"
            val jalaliDate = parts[1]            // "1404/04/06"

            val hijriParts = hijriKey.split("/")
            if (hijriParts.size != 2) continue
            val hijriYear = hijriParts[0].toIntOrNull() ?: continue
            val hijriMonth = hijriParts[1].toIntOrNull() ?: continue

            val jalaliParts = jalaliDate.split("/")
            if (jalaliParts.size != 3) continue
            val jy = jalaliParts[0].toIntOrNull() ?: continue
            val jm = jalaliParts[1].toIntOrNull() ?: continue
            val jd = jalaliParts[2].toIntOrNull() ?: continue

            // ثبت شروع ماه قمری
            startsByHijriKey[hijriKey] = jalaliDate
            availableYears.add(hijriYear)

            // ساخت HijriCache برای این روز (فقط روز اول ماه)
            // بقیه‌ی روزهای ماه توی findByJalaliDate محاسبه می‌شن
            val monthName = hijriMonthName(hijriMonth)

            val cache = HijriCache(
                id = 0,
                jalaliDate = jalaliDate,
                jalaliYear = jy,
                jalaliMonth = jm,
                jalaliDay = jd,
                hijriDay = 1,
                hijriMonth = monthName,
                hijriYear = hijriYear,
                isHoliday = false,
                eventsJson = "[]"
            )
            byJalaliDate[jalaliDate] = cache
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  API عمومی
    // ═══════════════════════════════════════════════════════════

    /**
     * آیا دیتای رسمی برای این سال قمری داریم؟
     */
    fun hasHijriYear(hijriYear: Int): Boolean = availableYears.contains(hijriYear)

    /**
     * آیا دیتای رسمی برای این تاریخ جلالی داریم؟
     */
    fun hasJalaliDate(jalaliDate: String): Boolean = byJalaliDate.containsKey(jalaliDate)

    /**
     * شروع ماه قمری رو برمی‌گردونه (تاریخ جلالی)
     */
    fun getHijriMonthStart(hijriYear: Int, hijriMonth: Int): String? {
        return startsByHijriKey["$hijriYear/$hijriMonth"]
    }

    /**
     * از روی تاریخ جلالی، تاریخ قمری رو پیدا می‌کنه.
     * اگه این تاریخ دقیقاً شروع یه ماه قمری باشه، HijriCache برمی‌گردونه.
     * وگرنه null.
     */
    fun findByJalaliDate(jalaliDate: String): HijriCache? {
        return byJalaliDate[jalaliDate]
    }

    /**
     * تاریخ قمری یه روز رو بر اساس نزدیک‌ترین شروع ماه قمری محاسبه می‌کنه.
     * این تابع برای هر روز جلالی کار می‌کنه، نه فقط اول ماه‌ها.
     */
    fun calculateHijriForJalali(
        jalaliYear: Int, jalaliMonth: Int, jalaliDay: Int
    ): HijriCache? {
        // جستجوی بین شروع ماه‌های قمری برای پیدا کردن ماهی که این روز توش قرار می‌گیره
        // (این تابع بعداً کامل می‌شه - الان فقط skeleton)
        return null
    }

    // ═══════════════════════════════════════════════════════════
    //  کمکی: اسم ماه قمری از شماره
    // ═══════════════════════════════════════════════════════════
    private fun hijriMonthName(month: Int): String = when (month) {
        1 -> "محرم"
        2 -> "صفر"
        3 -> "ربیع‌الاول"
        4 -> "ربیع‌الثانی"
        5 -> "جمادی‌الاول"
        6 -> "جمادی‌الثانی"
        7 -> "رجب"
        8 -> "شعبان"
        9 -> "رمضان"
        10 -> "شوال"
        11 -> "ذی‌القعده"
        12 -> "ذی‌الحجه"
        else -> ""
    }
}
