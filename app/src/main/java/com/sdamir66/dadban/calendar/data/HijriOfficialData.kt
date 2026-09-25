package com.sdamir66.dadban.calendar.data

import android.content.Context

// ═══════════════════════════════════════════════════════════════
//  HijriOfficialData
//  داده‌های رسمی تقویم قمری ایران (مؤسسه ژئوفیزیک دانشگاه تهران)
//  منبع: فایل hijri_official.txt در assets
//  بازه‌ی پشتیبانی: قبل از سال ۱۳۹۰ خورشیدی
// ═══════════════════════════════════════════════════════════════

object HijriOfficialData {

    private const val ASSET_FILE = "hijri_official.txt"

    // ═══ حداکثر سال جلالی که asset پشتیبانی می‌کنه ═══
    // از سال 1390 به بعد، از API استفاده می‌کنیم
    const val MAX_ASSET_YEAR = 1389

    // ═══ cache در حافظه ═══
    @Volatile
    private var loaded = false

    // jalaliDate ("1385/04/06") → HijriCache
    private val byJalaliDate = mutableMapOf<String, HijriCache>()

    // "hijriYear/hijriMonth" ("1427/1") → jalaliDate ("1385/04/06")
    private val startsByHijriKey = mutableMapOf<String, String>()

    // سال‌های قمری موجود توی فایل
    private val availableHijriYears = mutableSetOf<Int>()

    // سال‌های جلالی موجود توی فایل
    private val availableJalaliYears = mutableSetOf<Int>()

    // ═══════════════════════════════════════════════════════════
    //  بارگذاری از asset (یک بار)
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

            // فرمت: "1427/1 1385/04/06"
            val parts = line.split(Regex("\\s+"))
            if (parts.size < 2) continue

            val hijriKey = parts[0]              // "1427/1"
            val jalaliDate = parts[1]            // "1385/04/06"

            val hijriParts = hijriKey.split("/")
            if (hijriParts.size != 2) continue
            val hijriYear = hijriParts[0].toIntOrNull() ?: continue
            val hijriMonth = hijriParts[1].toIntOrNull() ?: continue

            val jalaliParts = jalaliDate.split("/")
            if (jalaliParts.size != 3) continue
            val jy = jalaliParts[0].toIntOrNull() ?: continue
            val jm = jalaliParts[1].toIntOrNull() ?: continue
            val jd = jalaliParts[2].toIntOrNull() ?: continue

            // فقط سال‌های جلالی قبل از 1390 رو نگه دار
            // (چون از 1390 به بعد از API استفاده می‌کنیم)
            if (jy > MAX_ASSET_YEAR) continue

            startsByHijriKey[hijriKey] = jalaliDate
            availableHijriYears.add(hijriYear)
            availableJalaliYears.add(jy)

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
     * آیا دیتای asset برای این سال جلالی داریم؟
     * (فقط برای سال‌های قبل از 1390)
     */
    fun hasJalaliYear(jalaliYear: Int): Boolean {
        return availableJalaliYears.contains(jalaliYear)
    }

    /**
     * آیا دیتای asset برای این سال قمری داریم؟
     */
    fun hasHijriYear(hijriYear: Int): Boolean {
        return availableHijriYears.contains(hijriYear)
    }

    /**
     * آیا دیتای asset برای این تاریخ جلالی داریم؟
     */
    fun hasJalaliDate(jalaliDate: String): Boolean {
        return byJalaliDate.containsKey(jalaliDate)
    }

    /**
     * شروع ماه قمری رو برمی‌گردونه (تاریخ جلالی)
     */
    fun getHijriMonthStart(hijriYear: Int, hijriMonth: Int): String? {
        return startsByHijriKey["$hijriYear/$hijriMonth"]
    }

    /**
     * از روی تاریخ جلالی، HijriCache روز اول ماه قمری رو برمی‌گردونه.
     */
    fun findByJalaliDate(jalaliDate: String): HijriCache? {
        return byJalaliDate[jalaliDate]
    }

    /**
     * همه‌ی شروع‌های ماه قمری (برای محاسبه‌ی روزهای میانی).
     * کلید: "hijriYear/hijriMonth" | مقدار: "jalaliDate"
     */
    fun getAllStarts(): Map<String, String> {
        return startsByHijriKey.toMap()
    }

    /**
     * همه‌ی شروع‌های ماه قمری مرتب‌شده بر اساس تاریخ جلالی.
     * (برای پیدا کردن نزدیک‌ترین شروع قبل از یه تاریخ خاص)
     */
    fun getAllStartsSortedByJalali(): List<Pair<String, String>> {
        return startsByHijriKey.entries
            .sortedBy { it.value }
            .map { it.key to it.value }
    }

    // ═══════════════════════════════════════════════════════════
    //  کمکی: اسم ماه قمری
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
