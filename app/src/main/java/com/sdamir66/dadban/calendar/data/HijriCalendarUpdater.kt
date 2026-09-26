package com.sdamir66.dadban.calendar.data

import com.sdamir66.dadban.data.AppDb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone

// ═══════════════════════════════════════════════════════════════
//  HijriCalendarUpdater
//  دانلود و اعمال calendar.json
//  ⚠️ از TimeZone «Asia/Tehran» استفاده می‌کنه (چون تقویم ایرانه)
// ═══════════════════════════════════════════════════════════════

object HijriCalendarUpdater {

    private const val GITHUB_URL =
        "https://raw.githubusercontent.com/sdamir66/MaliManager/main/app/src/main/assets/calendar.json"

    private const val TIMEOUT_MS = 20_000

    // ✅ TimeZone ثابت برای تقویم هجری (ایران)
    private val HIJRI_TZ: TimeZone get() = TimeZone.getTimeZone("Asia/Tehran")

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

    suspend fun applyFromFile(
        db: AppDb,
        jsonText: String,
        jalaliYear: Int
    ): Result<Int> = withContext(Dispatchers.IO) {
        applyFromJson(db, jsonText, jalaliYear)
    }

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
                return Result.failure(Exception("سال $jalaliYear توی فایل JSON نیست"))
            }

            val yearObj = yearsObj.getJSONObject(yearKey)
            val dao = db.hijriCacheDao()

            dao.deleteForYear(jalaliYear)

            val monthsArray = yearObj.getJSONArray("hijriMonths")
            val newCaches = mutableListOf<HijriCache>()

            for (i in 0 until monthsArray.length()) {
                val month = monthsArray.getJSONObject(i)
                val hijriYear = month.getInt("hijriYear")
                val hijriMonth = month.getInt("hijriMonth")
                val startJalali = month.getString("startJalali")

                // ✅ تبدیل جلالی به millis با TimeZone ایران
                val startMillis = jalaliToMillis(startJalali) ?: continue

                val daysFromJson = month.optInt("days", 0)
                val daysInMonth = if (daysFromJson > 0) {
                    daysFromJson
                } else if (i + 1 < monthsArray.length()) {
                    val nextMonth = monthsArray.getJSONObject(i + 1)
                    val nextStartJalali = nextMonth.getString("startJalali")
                    val nextStartMillis = jalaliToMillis(nextStartJalali)
                    if (nextStartMillis != null) {
                        ((nextStartMillis - startMillis) / 86_400_000L).toInt()
                    } else 30
                } else {
                    if (hijriMonth % 2 == 1) 30 else 29
                }

                for (d in 1..daysInMonth) {
                    val currentMillis = startMillis + (d - 1).toLong() * 86_400_000L
                    val j = millisToJalali(currentMillis)

                    newCaches.add(
                        HijriCache(
                            jalaliDate = String.format(Locale.US, "%04d/%02d/%02d", j[0], j[1], j[2]),
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

            saveEvents(db, root)

            Result.success(newCaches.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ✅ تبدیل جلالی به millis (با TimeZone ایران)
    private fun jalaliToMillis(jalali: String): Long? {
        return try {
            val parts = jalali.split("/")
            if (parts.size != 3) return null
            val jy = parts[0].toInt()
            val jm = parts[1].toInt()
            val jd = parts[2].toInt()

            // تبدیل جلالی به میلادی (بدون TimeZone)
            val g = jalaliToGregorian(jy, jm, jd)
            
            // ساخت millis با TimeZone ایران
            val cal = GregorianCalendar(HIJRI_TZ)
            cal.set(g[0], g[1] - 1, g[2], 0, 0, 0)
            cal.set(Calendar.MILLISECOND, 0)
            cal.timeInMillis
        } catch (e: Exception) {
            null
        }
    }

    // ✅ تبدیل millis به جلالی (با TimeZone ایران)
    private fun millisToJalali(millis: Long): IntArray {
        val cal = GregorianCalendar(HIJRI_TZ).apply { timeInMillis = millis }
        val gy = cal.get(Calendar.YEAR)
        val gm = cal.get(Calendar.MONTH) + 1
        val gd = cal.get(Calendar.DAY_OF_MONTH)
        return gregorianToJalali(gy, gm, gd)
    }

    private suspend fun saveEvents(db: AppDb, root: JSONObject) {
        val eventDao = db.eventDao()
        eventDao.deleteAllAutoEvents()

        val recurringArray = root.optJSONArray("recurringEvents")
        if (recurringArray != null) {
            for (i in 0 until recurringArray.length()) {
                val obj = recurringArray.getJSONObject(i)
                eventDao.insert(
                    Event(
                        title = obj.getString("text"),
                        description = "",
                        calendarType = CalendarType.valueOf(obj.optString("calendarType", "JALALI")),
                        month = obj.getInt("month"),
                        day = obj.getInt("day"),
                        year = null,
                        isHoliday = obj.optBoolean("isHoliday", false),
                        category = EventCategory.RELIGIOUS,
                        color = "#4C5FD7",
                        isUserCreated = false,
                        reminderMinutesBefore = null
                    )
                )
            }
        }

        val historicalArray = root.optJSONArray("historicalEvents")
        if (historicalArray != null) {
            for (i in 0 until historicalArray.length()) {
                val obj = historicalArray.getJSONObject(i)
                eventDao.insert(
                    Event(
                        title = obj.getString("text"),
                        description = "",
                        calendarType = CalendarType.valueOf(obj.optString("calendarType", "JALALI")),
                        month = obj.getInt("month"),
                        day = obj.getInt("day"),
                        year = null,
                        isHoliday = obj.optBoolean("isHoliday", false),
                        category = EventCategory.NATIONAL,
                        color = "#4C5FD7",
                        isUserCreated = false,
                        reminderMinutesBefore = null
                    )
                )
            }
        }
    }

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

    // ═══ تبدیل‌های پایه (کپی از Jalali.kt، بدون TimeZone) ═══
    private fun gregorianToJalali(gy: Int, gm: Int, gd: Int): IntArray {
        val g_d_m = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
        var jy = if (gy <= 1600) 0 else 979
        var gy2 = if (gy <= 1600) gy - 621 else gy - 1600
        var gm2 = gm
        var gd2 = gd
        var days = 365 * gy2 + ((gy2 + 3) / 4) - ((gy2 + 99) / 100) + ((gy2 + 399) / 400) - 80 + gd2 + g_d_m[gm2 - 1]
        jy += 33 * (days / 12053)
        days %= 12053
        jy += 4 * (days / 1461)
        days %= 1461
        if (days > 365) {
            jy += (days - 1) / 365
            days = (days - 1) % 365
        }
        val jm = if (days < 186) 1 + days / 31 else 7 + (days - 186) / 30
        val jd = if (days < 186) 1 + days % 31 else 1 + (days - 186) % 30
        return intArrayOf(jy, jm, jd)
    }

    private fun jalaliToGregorian(jy: Int, jm: Int, jd: Int): IntArray {
        var jy2 = jy + 1595
        var days = -355668 + 365 * jy2 + ((jy2 / 33) * 8) + (((jy2 % 33) + 3) / 4) + jd
        days += if (jm < 7) (jm - 1) * 31 else ((jm - 7) * 30) + 186
        var gy = 400 * (days / 146097)
        days %= 146097
        if (days > 36524) {
            gy += 100 * (--days / 36524)
            days %= 36524
            if (days >= 365) days++
        }
        gy += 4 * (days / 1461)
        days %= 1461
        if (days > 365) {
            gy += (days - 1) / 365
            days = (days - 1) % 365
        }
        var gd = days + 1
        val sal_a = intArrayOf(0, 31, if ((gy % 4 == 0 && gy % 100 != 0) || gy % 400 == 0) 29 else 28,
            31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        var gm = 0
        while (gm < 13 && gd > sal_a[gm]) {
            gd -= sal_a[gm]
            gm++
        }
        return intArrayOf(gy, gm, gd)
    }
}
