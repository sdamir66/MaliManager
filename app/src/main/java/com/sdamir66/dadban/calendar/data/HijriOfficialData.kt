package com.sdamir66.dadban.calendar.data

import android.content.Context
import com.sdamir66.dadban.util.AppTimeZone
import com.sdamir66.dadban.util.Jalali
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.Locale

object HijriOfficialData {

    private const val ASSET_FILE = "hijri_official.txt"
    const val MAX_ASSET_YEAR = 1404

    @Volatile
    private var loaded = false

    private val byJalaliDate = mutableMapOf<String, HijriCache>()
    private val startsByHijriKey = mutableMapOf<String, String>()
    private val availableHijriYears = mutableSetOf<Int>()
    private val availableJalaliYears = mutableSetOf<Int>()

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

    private fun parseAndFill(text: String) {
        for (rawLine in text.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) continue

            val parts = line.split(Regex("\\s+"))
            if (parts.size < 2) continue

            val hijriKey = parts[0]
            val miladiDate = parts[1]

            val hijriParts = hijriKey.split("/")
            if (hijriParts.size != 2) continue
            val hijriYear = hijriParts[0].toIntOrNull() ?: continue
            val hijriMonth = hijriParts[1].toIntOrNull() ?: continue

            val jalaliDate = convertMiladiToJalali(miladiDate) ?: continue

            val jalaliParts = jalaliDate.split("/")
            if (jalaliParts.size != 3) continue
            val jy = jalaliParts[0].toIntOrNull() ?: continue
            val jm = jalaliParts[1].toIntOrNull() ?: continue
            val jd = jalaliParts[2].toIntOrNull() ?: continue

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

    // ✅ از همون GregorianCalendar + TimeZone مشترک استفاده کن
    private fun convertMiladiToJalali(miladi: String): String? {
        return try {
            val parts = miladi.split("-")
            if (parts.size != 3) return null
            val gy = parts[0].toInt()
            val gm = parts[1].toInt()
            val gd = parts[2].toInt()

            // ✅ از همون GregorianCalendar و TimeZone مشترک
            val cal = GregorianCalendar(AppTimeZone.instance).apply {
                set(gy, gm - 1, gd, 0, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val millis = cal.timeInMillis

            val j = Jalali.toJalaliPublic(millis)
            String.format(Locale.US, "%04d/%02d/%02d", j[0], j[1], j[2])
        } catch (e: Exception) {
            null
        }
    }

    fun hasJalaliYear(jalaliYear: Int): Boolean = availableJalaliYears.contains(jalaliYear)
    fun hasHijriYear(hijriYear: Int): Boolean = availableHijriYears.contains(hijriYear)
    fun hasJalaliDate(jalaliDate: String): Boolean = byJalaliDate.containsKey(jalaliDate)
    fun getHijriMonthStart(hijriYear: Int, hijriMonth: Int): String? = startsByHijriKey["$hijriYear/$hijriMonth"]
    fun findByJalaliDate(jalaliDate: String): HijriCache? = byJalaliDate[jalaliDate]
    fun getAllStarts(): Map<String, String> = startsByHijriKey.toMap()
    fun getAllCaches(): List<HijriCache> = byJalaliDate.values.toList()
    fun getLastJalaliDate(): String? = byJalaliDate.keys.maxOrNull()

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
