package com.sdamir66.dadban.calendar.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

// ═══════════════════════════════════════════════════════════════
//  HijriDataDownloader
//  دانلود و پارس داده‌های تقویم قمری از pipe2time.ir
// ═══════════════════════════════════════════════════════════════

object HijriDataDownloader {

    // ═══ آدرس‌های دانلود ═══
    private const val BASE_URL = "https://pipe2time.ir/api/calendar"
    private const val TIMEOUT_MS = 15_000

    // ═══════════════════════════════════════════════════════════
    //  دانلود + ذخیره در دیتابیس
    //  (همون تابعی که SettingsScreen صداش می‌زنه)
    // ═══════════════════════════════════════════════════════════
    suspend fun downloadAndSave(
        jalaliYear: Int,
        dao: HijriCacheDao
    ): Int {
        val list = withContext(Dispatchers.IO) {
            downloadYear(jalaliYear)
        }
        if (list.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                dao.insertAll(list)
            }
        }
        return list.size
    }

    // ═══════════════════════════════════════════════════════════
    //  دانلود داده‌ی یک سال جلالی و تبدیل به لیست HijriCache
    // ═══════════════════════════════════════════════════════════
    fun downloadYear(jalaliYear: Int): List<HijriCache> {
        val url = "$BASE_URL?year=$jalaliYear"
        val jsonText = fetchUrl(url)
        return parseJson(jsonText, jalaliYear)
    }

    // ═══════════════════════════════════════════════════════════
    //  دریافت متن JSON از URL
    // ═══════════════════════════════════════════════════════════
    private fun fetchUrl(urlStr: String): String {
        val url = java.net.URL(urlStr)
        val conn = url.openConnection() as java.net.HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = TIMEOUT_MS
        conn.readTimeout = TIMEOUT_MS
        conn.setRequestProperty("Accept", "application/json")
        conn.setRequestProperty("User-Agent", "Dadban/1.0")

        return try {
            val code = conn.responseCode
            if (code !in 200..299) {
                throw java.io.IOException("HTTP $code for $urlStr")
            }
            conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  پارس JSON و ساخت لیست HijriCache
    // ═══════════════════════════════════════════════════════════
    private fun parseJson(jsonText: String, jalaliYear: Int): List<HijriCache> {
        val root = JSONObject(jsonText)
        val yearKey = root.keys().asSequence().firstOrNull() ?: return emptyList()
        val monthsArray = root.getJSONArray(yearKey)

        val result = mutableListOf<HijriCache>()

        for (monthIndex in 0 until monthsArray.length()) {
            val monthData = monthsArray.getJSONObject(monthIndex)
            val header = monthData.getJSONObject("header")
            val qamariHeader = header.getString("qamari")

            val parts = qamariHeader.split("-").map { it.trim() }
            val hijriYear = parts.last().toIntOrNull() ?: 0
            val hijriMonths = parts.dropLast(1)

            val jMonth = monthIndex + 1

            // ✅ متغیرهای محلی برای هر ماه
            var lastHijriMonth: String? = null
            var hijriMonthIndex = 0

            // Parse رویدادها — مستقیم به JSONArray
            val eventsArray = monthData.optJSONArray("events") ?: JSONArray()
            val eventsByDay = mutableMapOf<Int, JSONArray>()
            for (i in 0 until eventsArray.length()) {
                val event = eventsArray.getJSONObject(i)
                val jDate = event.optString("jDate", "")
                val day = jDate.substringAfterLast("/").toIntOrNull() ?: continue
                eventsByDay.getOrPut(day) { JSONArray() }.put(event)
            }

            // Parse روزها
            val weeksArray = monthData.optJSONArray("weeks") ?: JSONArray()

            for (i in 0 until weeksArray.length()) {
                val week = weeksArray.getJSONObject(i)
                if (week.optBoolean("disabled", false)) continue

                val dayObj = week.getJSONObject("day")
                val jDay = dayObj.optString("j", "").toIntOrNull() ?: continue
                val qDay = dayObj.optString("q", "").toIntOrNull() ?: continue

                // ✅ اگه qDay == 1 و ماه قبلی ست شده → ماه بعدی
                if (qDay == 1 && lastHijriMonth != null) {
                    hijriMonthIndex += 1
                }

                val hijriMonth = hijriMonths.getOrNull(hijriMonthIndex)
                    ?: hijriMonths.lastOrNull()
                    ?: ""
                lastHijriMonth = hijriMonth

                val dayEvents = eventsByDay[jDay] ?: JSONArray()
                val isHoliday = week.optBoolean("holiday", false)

                result.add(
                    HijriCache(
                        jalaliDate = String.format(
                            Locale.US,
                            "%04d/%02d/%02d",
                            jalaliYear, jMonth, jDay
                        ),
                        jalaliYear = jalaliYear,
                        jalaliMonth = jMonth,
                        jalaliDay = jDay,
                        hijriDay = qDay,
                        hijriMonth = hijriMonth,
                        hijriYear = hijriYear,
                        isHoliday = isHoliday,
                        eventsJson = dayEvents.toString()
                    )
                )
            }
        }

        return result
    }
}
