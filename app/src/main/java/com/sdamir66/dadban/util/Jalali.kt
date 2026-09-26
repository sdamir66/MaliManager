package com.sdamir66.dadban.util

import java.util.Calendar
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone

object Jalali {
    private val monthNames = arrayOf(
        "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
        "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"
    )

    fun monthName(m: Int): String = monthNames[(m - 1).coerceIn(0, 11)]

    fun nowJalali(): IntArray = toJalali(System.currentTimeMillis())

    fun format(millis: Long): String {
        val j = toJalali(millis)
        val cal = Calendar.getInstance(AppTimeZone.instance).apply { timeInMillis = millis }
        val h = cal.get(Calendar.HOUR_OF_DAY)
        val min = cal.get(Calendar.MINUTE)
        return "%04d/%02d/%02d %02d:%02d".format(Locale.US, j[0], j[1], j[2], h, min)
    }

    fun parse(s: String): Long? {
        val parts = s.trim().split("/")
        if (parts.size != 3) return null
        val y = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        val d = parts[2].toIntOrNull() ?: return null
        if (m !in 1..12 || d !in 1..31) return null
        return toGregorian(y, m, d)
    }

    fun parseWithCurrentTime(s: String): Long? {
        val dateMillis = parse(s) ?: return null
        val now = Calendar.getInstance(AppTimeZone.instance)
        val dateCal = Calendar.getInstance(AppTimeZone.instance).apply { timeInMillis = dateMillis }
        dateCal.set(Calendar.HOUR_OF_DAY, now.get(Calendar.HOUR_OF_DAY))
        dateCal.set(Calendar.MINUTE, now.get(Calendar.MINUTE))
        dateCal.set(Calendar.SECOND, now.get(Calendar.SECOND))
        dateCal.set(Calendar.MILLISECOND, now.get(Calendar.MILLISECOND))
        return dateCal.timeInMillis
    }

    fun daysInMonth(y: Int, m: Int): Int = when {
        m in 1..6 -> 31
        m in 7..11 -> 30
        m == 12 -> if (isLeap(y)) 30 else 29
        else -> 30
    }

    fun startOfJalaliMonth(y: Int, m: Int): Long = toGregorian(y, m, 1)

    fun toJalaliPublic(millis: Long): IntArray = toJalali(millis)

    /**
     * ✅ تبدیل مستقیم میلادی به جلالی (بدون TimeZone)
     * برای تبدیل تاریخ‌های ثابت (مثل دیتای رسمی قمری)
     */
    fun gregorianToJalaliDirect(gy: Int, gm: Int, gd: Int): IntArray {
        return gregorianToJalali(gy, gm, gd)
    }

    private fun isLeap(y: Int): Boolean {
        val r = y % 33
        return r in intArrayOf(1, 5, 9, 13, 17, 22, 26, 30)
    }

    private fun toJalali(millis: Long): IntArray {
        val cal = GregorianCalendar(AppTimeZone.instance).apply { timeInMillis = millis }
        val gy = cal.get(Calendar.YEAR)
        val gm = cal.get(Calendar.MONTH) + 1
        val gd = cal.get(Calendar.DAY_OF_MONTH)
        return gregorianToJalali(gy, gm, gd)
    }

    private fun toGregorian(jy: Int, jm: Int, jd: Int): Long {
        val g = jalaliToGregorian(jy, jm, jd)
        val cal = GregorianCalendar(AppTimeZone.instance)
        cal.set(g[0], g[1] - 1, g[2], 0, 0, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

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
