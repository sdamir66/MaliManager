package com.sdamir66.dadban.calendar.data

import com.sdamir66.dadban.data.AppDb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object HijriDataDownloader {

    private const val BASE_URL = "https://hmarzban.github.io/pipe2time.ir/api"

    suspend fun downloadAndSave(
        db: AppDb,
        jalaliYear: Int
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            // ۱. دانلود JSON
            val url = URL("$BASE_URL/$jalaliYear/index.json")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 15000
            connection.readTimeout = 15000

            if (connection.responseCode != 200) {
                return@withContext Result.failure(Exception("خطا در دانلود: ${connection.responseCode}"))
            }

            val jsonText = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()

            // ۲. Parse
            val items = parseJson(jsonText, jalaliYear)

            if (items.isEmpty()) {
                return@withContext Result.failure(Exception("دیتای خالی"))
            }

            // ۳. ذخیره در دیتابیس
            db.hijriCacheDao().deleteForYear(jalaliYear)
            db.hijriCacheDao().insertAll(items)

            Result.success(items.size)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    private fun parseJson(jsonText: String, jalaliYear: Int): List<HijriCache> {
        val root = JSONObject(jsonText)
        val yearKey = root.keys().next()
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

            // Parse رویدادها
            val eventsArray = monthData.optJSONArray("events") ?: JSONArray()
            val eventsByDay = mutableMapOf<Int, MutableList<JSONObject>>()
            for (i in 0 until eventsArray.length()) {
                val event = eventsArray.getJSONObject(i)
                val jDate = event.optString("jDate", "")
                val day = jDate.substringAfterLast("/").toIntOrNull() ?: continue
                eventsByDay.getOrPut(day) { mutableListOf() }.add(event)
            }

            // Parse روزها
            val weeksArray = monthData.optJSONArray("weeks") ?: JSONArray()
            var hijriMonthIndex = 0

            for (i in 0 until weeksArray.length()) {
                val week = weeksArray.getJSONObject(i)
                if (week.optBoolean("disabled", false)) continue

                val dayObj = week.getJSONObject("day")
                val jDay = dayObj.optString("j", "").toIntOrNull() ?: continue
                val qDay = dayObj.optString("q", "").toIntOrNull() ?: continue

                if (qDay == 1 && lastHijriMonth != null) {
                    hijriMonthIndex = (hijriMonthIndex + 1).coerceAtMost(hijriMonths.size - 1)
                }

                val hijriMonth = hijriMonths.getOrNull(hijriMonthIndex) ?: hijriMonths.lastOrNull() ?: ""
                lastHijriMonth = hijriMonth

                val dayEvents = eventsByDay[jDay] ?: mutableListOf()
                val eventsJson = JSONArray(dayEvents).toString()
                val isHoliday = week.optBoolean("holiday", false)

                result.add(HijriCache(
                    jalaliDate = "%04d/%02d/%02d".format(jalaliYear, jMonth, jDay),
                    jalaliYear = jalaliYear,
                    jalaliMonth = jMonth,
                    jalaliDay = jDay,
                    hijriDay = qDay,
                    hijriMonth = hijriMonth,
                    hijriYear = hijriYear,
                    isHoliday = isHoliday,
                    eventsJson = eventsJson
                ))
            }
        }

        return result
    }
}
