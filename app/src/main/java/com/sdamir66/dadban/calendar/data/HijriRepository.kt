package com.sdamir66.dadban.calendar.data

import android.content.Context
import com.sdamir66.dadban.data.AppDb
import com.sdamir66.dadban.util.Jalali
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object HijriRepository {

    @Volatile
    private var contractualStartJalaliMillis: Long? = null
    @Volatile
    private var contractualStartHijriYear: Int? = null
    @Volatile
    private var contractualStartHijriMonth: Int? = null

    // ═══════════════════════════════════════════════════════════
    //  init
    // ═══════════════════════════════════════════════════════════
    suspend fun init(context: Context, db: AppDb) = withContext(Dispatchers.IO) {
        // ۱. بارگذاری asset (hijri_official.txt)
        HijriOfficialData.ensureLoaded(context)

        // ۲. seed asset به دیتابیس
        seedAssetIfNeeded(db)

        // ۳. بارگذاری calendar.json از asset (برای رویدادها)
        seedCalendarJsonIfNeeded(context, db)

        // ۴. محاسبه‌ی نقطه‌ی شروع قراردادی
        computeContractualStart(db)
    }

    // ═══ seed دیتای hijri_official.txt ═══
    private suspend fun seedAssetIfNeeded(db: AppDb) {
        val dao = db.hijriCacheDao()
        val allCaches = HijriOfficialData.getAllCaches()
        if (allCaches.isEmpty()) return

        val minYear = allCaches.minOfOrNull { it.jalaliYear } ?: return
        if (dao.countForYear(minYear) > 0) return

        dao.insertAll(allCaches)
    }

    // ═══ seed calendar.json (فقط اگه دیتابیس خالیه) ═══
    private suspend fun seedCalendarJsonIfNeeded(context: Context, db: AppDb) {
        try {
            val jsonText = context.assets.open("calendar.json")
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }

            val currentYear = Jalali.nowJalali()[0]
            HijriCalendarUpdater.applyFromFile(db, jsonText, currentYear)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun computeContractualStart(db: AppDb) {
        if (contractualStartJalaliMillis != null) return

        val lastDayOf1410 = "1410/12/29"
        val lastDayMillis = Jalali.parse(lastDayOf1410) ?: return
        contractualStartJalaliMillis = lastDayMillis + 86_400_000L

        val lastHijri = db.hijriCacheDao().getByJalaliDate(lastDayOf1410)
        if (lastHijri != null) {
            var nextHijriDay = lastHijri.hijriDay + 1
            var nextHijriMonth = hijriMonthNumber(lastHijri.hijriMonth)
            var nextHijriYear = lastHijri.hijriYear

            if (nextHijriDay > 30) {
                nextHijriDay = 1
                nextHijriMonth += 1
                if (nextHijriMonth > 12) {
                    nextHijriMonth = 1
                    nextHijriYear += 1
                }
            }

            contractualStartHijriYear = nextHijriYear
            contractualStartHijriMonth = nextHijriMonth
        }
    }

    suspend fun getHijriDate(
        db: AppDb,
        jalaliYear: Int,
        jalaliMonth: Int,
        jalaliDay: Int
    ): HijriCache? {
        val jalaliDate = String.format("%04d/%02d/%02d", jalaliYear, jalaliMonth, jalaliDay)

        db.hijriCacheDao().getByJalaliDate(jalaliDate)?.let { return it }
        calculateFromDbStarts(db, jalaliYear, jalaliMonth, jalaliDay)?.let { return it }
        return calculateContractual(jalaliYear, jalaliMonth, jalaliDay)
    }

    private suspend fun calculateFromDbStarts(
        db: AppDb,
        jalaliYear: Int,
        jalaliMonth: Int,
        jalaliDay: Int
    ): HijriCache? {
        val targetDate = String.format("%04d/%02d/%02d", jalaliYear, jalaliMonth, jalaliDay)

        val allStarts = mutableListOf<HijriCache>()
        for (y in (jalaliYear - 1)..jalaliYear) {
            allStarts.addAll(db.hijriCacheDao().getAllForYear(y))
        }

        val bestStart = allStarts
            .filter { it.jalaliDate <= targetDate }
            .maxByOrNull { it.jalaliDate }
            ?: return null

        val startMillis = Jalali.parse(bestStart.jalaliDate) ?: return null
        val targetMillis = Jalali.parse(targetDate) ?: return null
        val daysDiff = ((targetMillis - startMillis) / 86_400_000L).toInt()

        val hijriDay = bestStart.hijriDay + daysDiff
        if (hijriDay < 1 || hijriDay > 30) return null

        return bestStart.copy(
            jalaliDate = targetDate,
            jalaliYear = jalaliYear,
            jalaliMonth = jalaliMonth,
            jalaliDay = jalaliDay,
            hijriDay = hijriDay,
            eventsJson = "[]"
        )
    }

    private fun calculateContractual(
        jalaliYear: Int,
        jalaliMonth: Int,
        jalaliDay: Int
    ): HijriCache? {
        val startMillis = contractualStartJalaliMillis ?: return null
        val startHijriYear = contractualStartHijriYear ?: return null
        val startHijriMonth = contractualStartHijriMonth ?: return null

        val targetDate = String.format("%04d/%02d/%02d", jalaliYear, jalaliMonth, jalaliDay)
        val targetMillis = Jalali.parse(targetDate) ?: return null

        if (targetMillis < startMillis) return null

        val daysFromStart = ((targetMillis - startMillis) / 86_400_000L).toInt()

        var hijriYear = startHijriYear
        var hijriMonth = startHijriMonth
        var hijriDay = 1

        var remaining = daysFromStart
        var currentMonth = hijriMonth

        while (true) {
            val daysInThisMonth = if (currentMonth % 2 == 1) 30 else 29
            if (remaining < daysInThisMonth) {
                hijriMonth = currentMonth
                hijriDay = remaining + 1
                break
            }
            remaining -= daysInThisMonth
            currentMonth += 1
            if (currentMonth > 12) {
                currentMonth = 1
                hijriYear += 1
            }
        }

        return HijriCache(
            id = 0,
            jalaliDate = targetDate,
            jalaliYear = jalaliYear,
            jalaliMonth = jalaliMonth,
            jalaliDay = jalaliDay,
            hijriDay = hijriDay,
            hijriMonth = hijriMonthName(hijriMonth),
            hijriYear = hijriYear,
            isHoliday = false,
            eventsJson = "[]"
        )
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

    private fun hijriMonthNumber(name: String): Int = when (name) {
        "محرم" -> 1
        "صفر" -> 2
        "ربیع‌الاول" -> 3
        "ربیع‌الثانی" -> 4
        "جمادی‌الاول" -> 5
        "جمادی‌الثانی" -> 6
        "رجب" -> 7
        "شعبان" -> 8
        "رمضان" -> 9
        "شوال" -> 10
        "ذی‌القعده" -> 11
        "ذی‌الحجه" -> 12
        else -> 1
    }
}
