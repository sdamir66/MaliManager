package com.sdamir66.dadban.calendar.data

import android.content.Context

object HijriOfficialData {

    private const val ASSET_FILE = "hijri_official.txt"

    // ═══ حداکثر سال جلالی که asset پشتیبانی می‌کنه ═══
    const val MAX_ASSET_YEAR = 1405

    // ═══ cache در حافظه ═══
    @Volatile
    private var loaded = false

    // jalaliDate ("1385/04/06") → HijriCache (فقط روز اول ماه)
    private val byJalaliDate = mutableMapOf<String, HijriCache>()

    // "hijriYear/hijriMonth" ("1427/1") → jalaliDate ("1385/04/06")
    private val startsByHijriKey = mutableMapOf<String, String>()

    // سال‌های قمری موجود
    private val availableHijriYears = mutableSetOf<Int>()

    // سال‌های جلالی موجود
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
    //  پارس فایل
    // ═══════════════════════════════════════════════════════════
    private fun parseAndFill(text: String) {
        for (rawLine in text.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) continue

            // فرمت: "1427/1 1385/04/06"
            val parts = line.split(Regex("\\s+"))
            if (parts.size < 2) continue

            val hijriKey = parts[0]
            val jalaliDate = parts[1]

            val hijriParts = hijriKey.split("/")
            if (hijriParts.size != 2) continue
            val hijriYear = hijriParts[0].toIntOrNull() ?: continue
            val hijriMonth = hijriParts[1].toIntOrNull() ?: continue

            val jalaliParts = jalaliDate.split("/")
            if (jalaliParts.size != 3) continue
            val jy = jalaliParts[0].toIntOrNull() ?: continue
            val jm = jalaliParts[1].toIntOrNull() ?: continue
            val jd = jalaliParts[2].toIntOrNull() ?: continue

            // فقط سال‌های جلالی قبل از 1405
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

    fun hasJalaliYear(jalaliYear: Int): Boolean =
        availableJalaliYears.contains(jalaliYear)

    fun hasHijriYear(hijriYear: Int): Boolean =
        availableHijriYears.contains(hijriYear)

    fun hasJalaliDate(jalaliDate: String): Boolean =
        byJalaliDate.containsKey(jalaliDate)

    fun getHijriMonthStart(hijriYear: Int, hijriMonth: Int): String? =
        startsByHijriKey["$hijriYear/$hijriMonth"]

    fun findByJalaliDate(jalaliDate: String): HijriCache? =
        byJalaliDate[jalaliDate]

    fun getAllStarts(): Map<String, String> = startsByHijriKey.toMap()

    /**
     * همه‌ی HijriCache ها (برای seed به دیتابیس)
     */
    fun getAllCaches(): List<HijriCache> = byJalaliDate.values.toList()

    /**
     * آخرین تاریخ جلالی موجود توی asset
     */
    fun getLastJalaliDate(): String? {
        return byJalaliDate.keys.maxOrNull()
    }

    // ═══════════════════════════════════════════════════════════
    //  کمکی
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
