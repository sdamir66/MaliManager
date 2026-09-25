package com.sdamir66.dadban.calendar.data

import com.sdamir66.dadban.data.AppDb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

// ═══════════════════════════════════════════════════════════════
//  HijriCalendarUpdater
//  دانلود و اعمال فایل calendar.json از GitHub
//  یا از فایل محلی (آپلود کاربر)
// ═══════════════════════════════════════════════════════════════

object HijriCalendarUpdater {

    // ═══ آدرس فایل JSON در GitHub ═══
    private const val GITHUB_URL =
        "https://raw.githubusercontent.com/sdamir66/MaliManager/main/app/src/main/assets/calendar.json"

    private const val TIMEOUT_MS = 20_000

    // ═══════════════════════════════════════════════════════════
    //  دانلود از GitHub و اعمال
    // ═══════════════════════════════════════════════════════════
    suspend fun downloadAndApply(
        db: AppDb,
        jalaliYear: Int
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val jsonText = fetchUrl(GITHUB_URL)
            applyFromJson(db, jsonText, jalaliYear)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  اعمال از فایل محلی (آپلود کاربر)
    // ═══════════════════════════════════════════════════════════
    suspend fun applyFromFile(
        db: AppDb,
        jsonText: String,
        jalaliYear: Int
    ): Result<Int> = withContext(Dispatchers.IO) {
        applyFromJson(db, jsonText, jalaliYear)
    }

    // ═══════════════════════════════════════════════════════════
    //  منطق مشترک: پارس و ذخیره
    // ═══════════════════════════════════════════════════════════
    private suspend fun applyFromJson(
        db: AppDb,
        jsonText: String,
        jalaliYear: Int
    ): Result<Int> {
        return try {
            val root = JSONObject(jsonText)
            val yearsObj = root.getJSONObject("years")

            val yearKey = jalaliYear.toString()
            if (!yearsObj.has(yearKey)) {
                return Result.failure(
                    Exception("سال $jalaliYear توی فایل JSON نیست")
                )
            }

            val yearObj = yearsObj.getJSONObject(yearKey)
            val dao = db.hijriCacheDao()

            // ═══ پاک کردن دیتای قبلی این سال ═══
            dao.deleteForYear(jalaliYear)

            // ═══ ذخیره‌ی تاریخ‌های قمری ═══
            val monthsArray = yearObj.getJSONArray("hijriMonths")
            val newCaches = mutableListOf<HijriCache>()

            for (i in 0 until monthsArray.length()) {
                val month = monthsArray.getJSONObject(i)
                val hijriYear = month.getInt("hijriYear")
                val hijriMonth = month.getInt("hijriMonth")
                val startJalali = month.getString("startJalali")

                val startMillis = com.sdamir66.dadban.util.Jalali.parse(startJalali)
                    ?: continue

                // محاسبه‌ی تعداد روز: تا شروع ماه بعدی
                val nextStartMillis = if (i + 1 < monthsArray.length()) {
                    val nextMonth = monthsArray.getJSONObject(i + 1)
                    val nextStartJalali = nextMonth.getString("startJalali")
                    com.sdamir66.dadban.util.Jalali.parse(nextStartJalali)
                } else {
                    null
                }

                val daysInMonth = if (nextStartMillis != null) {
                    ((nextStartMillis - startMillis) / 86_400_000L).toInt()
                } else {
                    30  // fallback
                }

                // تولید همه‌ی روزهای این ماه قمری
                for (d in 1..daysInMonth) {
                    val currentMillis = startMillis + (d - 1).toLong() * 86_400_000L
                    val j = com.sdamir66.dadban.util.Jalali.toJalaliPublic(currentMillis)

                    newCaches.add(
                        HijriCache(
                            jalaliDate = String.format(
                                Locale.US, "%04d/%02d/%02d", j[0], j[1], j[2]
                            ),
                            jalaliYear = j[0],
                            jalaliMonth = j[1],
                            jalaliDay = j[2],
                            hijriDay = d,
                            hijriMonth = hijriMonthName(hijriMonth),
                            hijriYear = hijriYear,
                            isHoliday = false,
                            eventsJson = "[]"
                        )
                    )
                }
            }

            if (newCaches.isNotEmpty()) {
                dao.insertAll(newCaches)
            }

            Result.success(newCaches.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ═══ دریافت متن JSON از URL ═══
    private fun fetchUrl(urlStr: String): String {
        val url = URL(urlStr)
        val conn = url.openConnection() as HttpURLConnection
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
