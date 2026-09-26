package com.sdamir66.dadban.calendar.data

import android.content.Context
import com.sdamir66.dadban.util.Jalali
import java.util.Locale

object HijriOfficialData {

    private const val ASSET_FILE = "hijri_official.txt"
    const val MAX_ASSET_YEAR = 1405

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

    private data class HijriStart(
        val hijriYear: Int,
        val hijriMonth: Int,
        val jalaliYear: Int,
        val jalaliMonth: Int,
        val jalaliDay: Int,
        val jalaliDate: String
    )

    private fun parseAndFill(text: String) {
        // ═══ مرحله ۱: جمع‌آوری شروع همه‌ی ماه‌ها ═══
        val starts = mutableListOf<HijriStart>()

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

            // ✅ تبدیل مستقیم میلادی به جلالی (بدون TimeZone)
            val j = convertMiladiToJalaliInts(miladiDate) ?: continue
            val jy = j[0]
            val jm = j[1]
            val jd = j[2]

            if (jy > MAX_ASSET_YEAR) continue

            val jalaliDate = String.format(Locale.US, "%04d/%02d/%02d", jy, jm, jd)

            starts.add(HijriStart(hijriYear, hijriMonth, jy, jm, jd, jalaliDate))
            startsByHijriKey["$hijriYear/$hijriMonth"] = jalaliDate
            availableHijriYears.add(hijriYear)
            availableJalaliYears.add(jy)
        }

        // ═══ مرحله ۲: تولید همه‌ی روزهای هر ماه (بدون millis) ═══
        for (i in starts.indices) {
            val start = starts[i]

            // محاسبه‌ی تعداد روزهای این ماه
            val daysInMonth = if (i + 1 < starts.size) {
                val next = starts[i + 1]
                daysBetweenJalali(
                    start.jalaliYear, start.jalaliMonth, start.jalaliDay,
                    next.jalaliYear, next.jalaliMonth, next.jalaliDay
                ).coerceIn(29, 30)
            } else {
                if (start.hijriMonth % 2 == 1) 30 else 29
            }

            // تولید روزها با increment کردن مستقیم تاریخ جلالی
            var jy = start.jalaliYear
            var jm = start.jalaliMonth
            var jd = start.jalaliDay

            for (d in 1..daysInMonth) {
                val date = String.format(Locale.US, "%04d/%02d/%02d", jy, jm, jd)

                byJalaliDate[date] = HijriCache(
                    id = 0,
                    jalaliDate = date,
                    jalaliYear = jy,
                    jalaliMonth = jm,
                    jalaliDay = jd,
                    hijriDay = d,
                    hijriMonth = hijriMonthName(start.hijriMonth),
                    hijriYear = start.hijriYear,
                    isHoliday = false,
                    eventsJson = "[]"
                )

                // روز بعد
                jd++
                if (jd > Jalali.daysInMonth(jy, jm)) {
                    jd = 1
                    jm++
                    if (jm > 12) {
                        jm = 1
                        jy++
                    }
                }
            }
        }
    }

    // ✅ تبدیل مستقیم میلادی به جلالی (بدون TimeZone)
    private fun convertMiladiToJalaliInts(miladi: String): IntArray? {
        return try {
            val parts = miladi.split("-")
            if (parts.size != 3) return null
            val gy = parts[0].toInt()
            val gm = parts[1].toInt()
            val gd = parts[2].toInt()

            Jalali.gregorianToJalaliDirect(gy, gm, gd)
        } catch (e: Exception) {
            null
        }
    }

    // ✅ محاسبه‌ی تعداد روز بین دو تاریخ جلالی (بدون millis)
    private fun daysBetweenJalali(
        y1: Int, m1: Int, d1: Int,
        y2: Int, m2: Int, d2: Int
    ): Int {
        val total1 = jalaliToDayNumber(y1, m1, d1)
        val total2 = jalaliToDayNumber(y2, m2, d2)
        return total2 - total1
    }

    // تبدیل تاریخ جلالی به تعداد روز (تقریبی، دقیق برای بازه‌های کوتاه)
    private fun jalaliToDayNumber(y: Int, m: Int, d: Int): Int {
        var days = 0
        days += (y - 1) * 365
        days += (y - 1) / 33 * 8 + ((y - 1) % 33 + 3) / 4
        days += if (m <= 7) (m - 1) * 31 else (6 * 31) + (m - 7) * 30
        days += d - 1
        return days
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
