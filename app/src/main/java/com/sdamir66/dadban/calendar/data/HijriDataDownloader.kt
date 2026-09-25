package com.sdamir66.dadban.calendar.data

import android.content.Context
import com.sdamir66.dadban.data.AppDb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

// ═══════════════════════════════════════════════════════════════
//  HijriDataDownloader
//  دانلود داده‌های تقویم قمری از API pipe2time.ir (GitHub Pages)
//  منبع: https://github.com/HMarzban/pipe2time.ir
//  بازه‌ی پشتیبانی: 1390 تا 1410 خورشیدی
// ═══════════════════════════════════════════════════════════════

object HijriDataDownloader {

    // ═══ آدرس API ═══
    private const val BASE_URL = "https://hmarzban.github.io/pipe2time.ir/api"
    private const val TIMEOUT_MS = 20_000

    // ═══ بازه‌ی سال‌های پشتیبانی‌شده توسط API ═══
    const val MIN_API_YEAR = 1405

    suspend fun downloadAndSave(
        db: AppDb,
        jalaliYear: Int
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            if (jalaliYear < MIN_API_YEAR) {
                return@withContext Result.failure(
                    IllegalArgumentException("سال $jalaliYear پشتیبانی نمی‌شود")
                )
            }

            val dao: HijriCacheDao = db.hijriCacheDao()
            val list = downloadYear(jalaliYear)
            if (list.isNotEmpty()) {
                dao.insertAll(list)
            }
            Result.success(list.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  دانلود داده‌ی یک سال جلالی و تبدیل به لیست HijriCache
    // ═══════════════════════════════════════════════════════════
    private fun downloadYear(jalaliYear: Int): List<HijriCache> {
        val url = "$BASE_URL/$jalaliYear/index.json"
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
    //  ساختار API:
    //  {
    //    "1405": [
    //      { "header": {...}, "events": [...], "weeks": [...] },
    //      ...
    //    ]
    //  }
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

            var lastHijriMonth: String? = null
            var hijriMonthIndex = 0

            // Parse رویدادها
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
