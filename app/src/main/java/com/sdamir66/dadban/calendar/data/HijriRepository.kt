package com.sdamir66.dadban.calendar.data

import android.content.Context
import com.sdamir66.dadban.data.AppDb
import com.sdamir66.dadban.util.Jalali
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

// ═══════════════════════════════════════════════════════════════
//  HijriRepository
//  لایه‌ی اصلی دسترسی به تاریخ قمری
//
//  منابع به ترتیب اولویت:
//  ۱. asset رسمی (قبل از ۱۴۰۵) — یک بار seed می‌شه
//  ۲. API pipe2time.ir (۱۳۹۰ تا ۱۴۱۰) — با دکمه‌ی به‌روزرسانی
//  ۳. حالت قراردادی (بعد از ۱۴۱۰) — محاسبه‌ی درجا
// ═══════════════════════════════════════════════════════════════

object HijriRepository {

    // ═══ بازه‌ی سال‌های API ═══
    private const val API_MIN_YEAR = 1390
    private const val API_MAX_YEAR = 1410

    // ═══ بازه‌ی asset ═══
    private const val ASSET_MAX_YEAR = 1404

    // ═══ نقطه‌ی شروع حالت قراردادی ═══
    // این مقدار توی initialize محاسبه و ذخیره می‌شه
    @Volatile
    private var contractualStartJalaliMillis: Long? = null
    @Volatile
    private var contractualStartHijriYear: Int? = null
    @Volatile
    private var contractualStartHijriMonth: Int? = null
    @Volatile
    private var contractualStartHijriDay: Int? = null

    // ═══════════════════════════════════════════════════════════
    //  init — بارگذاری asset و محاسبه‌ی نقطه‌ی شروع قراردادی
    //  توی MainActivity.onCreate صدا زده می‌شه
    // ═══════════════════════════════════════════════════════════
    suspend fun init(context: Context, db: AppDb) = withContext(Dispatchers.IO) {
        // ۱. بارگذاری asset
        HijriOfficialData.ensureLoaded(context)

        // ۲. seed asset به دیتابیس (اگه خالی باشه)
        seedAssetIfNeeded(db)

        // ۳. محاسبه‌ی نقطه‌ی شروع قراردادی
        computeContractualStart(db)
    }

    // ═══════════════════════════════════════════════════════════
    //  seed asset به دیتابیس
    // ═══════════════════════════════════════════════════════════
    private suspend fun seedAssetIfNeeded(db: AppDb) {
        val dao = db.hijriCacheDao()

        // اگه هیچ دیتایی از asset توی دیتابیس نیست، اضافه کن
        val allCaches = HijriOfficialData.getAllCaches()
        if (allCaches.isEmpty()) return

        // چک کن که آیا قبلاً seed شده
        val minYear = allCaches.minOfOrNull { it.jalaliYear } ?: return
        if (dao.countForYear(minYear) > 0) return

        dao.insertAll(allCaches)
    }

    // ═══════════════════════════════════════════════════════════
    //  محاسبه‌ی نقطه‌ی شروع حالت قراردادی
    //  (روز بعد از آخرین روز سال ۱۴۱۰)
    // ═══════════════════════════════════════════════════════════
    private suspend fun computeContractualStart(db: AppDb) {
        if (contractualStartJalaliMillis != null) return

        // آخرین روز 1410 توی دیتابیس
        val lastDayOf1410 = "1410/12/29" // فرض: 1410 کبیسه نیست
        val lastDayMillis = Jalali.parse(lastDayOf1410) ?: return

        // روز بعدش
        contractualStartJalaliMillis = lastDayMillis + 86_400_000L

        // تاریخ قمری متناظر با آخرین روز 1410
        val lastHijri = db.hijriCacheDao().getByJalaliDate(lastDayOf1410)
        if (lastHijri != null) {
            // روز بعد از آخرین روز، یعنی روز بعد از این تاریخ قمری
            var nextHijriDay = lastHijri.hijriDay + 1
            var nextHijriMonth = hijriMonthNumber(lastHijri.hijriMonth)
            var nextHijriYear = lastHijri.hijriYear

            // اگه از ۳۰ گذشت، ماه بعد
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
            contractualStartHijriDay = nextHijriDay
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  گرفتن تاریخ قمری برای یه روز خاص
    // ═══════════════════════════════════════════════════════════
    suspend fun getHijriDate(
        db: AppDb,
        jalaliYear: Int,
        jalaliMonth: Int,
        jalaliDay: Int
    ): HijriCache? {
        val jalaliDate = String.format("%04d/%02d/%02d", jalaliYear, jalaliMonth, jalaliDay)

        // ─── منبع ۱: دیتابیس (شامل asset seed شده + API cache) ───
        db.hijriCacheDao().getByJalaliDate(jalaliDate)?.let { return it }

        // ─── منبع ۲: محاسبه از روی شروع ماه قمری (توی دیتابیس) ───
        calculateFromDbStarts(db, jalaliYear, jalaliMonth, jalaliDay)?.let { return it }

        // ─── منبع ۳: حالت قراردادی (بعد از ۱۴۱۰) ───
        return calculateContractual(jalaliYear, jalaliMonth, jalaliDay)
    }

    // ═══════════════════════════════════════════════════════════
    //  محاسبه از روی شروع ماه‌های قمری توی دیتابیس
    //  (برای روزهای میانی که توی دیتابیس نیستن)
    // ═══════════════════════════════════════════════════════════
    private suspend fun calculateFromDbStarts(
        db: AppDb,
        jalaliYear: Int,
        jalaliMonth: Int,
        jalaliDay: Int
    ): HijriCache? {
        val targetDate = String.format("%04d/%02d/%02d", jalaliYear, jalaliMonth, jalaliDay)

        // همه‌ی شروع‌های ماه قمری توی دیتابیس برای این سال و سال قبل
        val allStarts = mutableListOf<HijriCache>()
        for (y in (jalaliYear - 1)..jalaliYear) {
            allStarts.addAll(db.hijriCacheDao().getAllForYear(y))
        }

        // نزدیک‌ترین شروع قبل از targetDate
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

    // ═══════════════════════════════════════════════════════════
    //  حالت قراردادی (بعد از ۱۴۱۰)
    //  محرم ۳۰، صفر ۲۹، ربیع‌الاول ۳۰ و...
    // ═══════════════════════════════════════════════════════════
    private fun calculateContractual(
        jalaliYear: Int,
        jalaliMonth: Int,
        jalaliDay: Int
    ): HijriCache? {
        val startMillis = contractualStartJalaliMillis ?: return null
        val startHijriYear = contractualStartHijriYear ?: return null
        val startHijriMonth = contractualStartHijriMonth ?: return null
        val startHijriDay = contractualStartHijriDay ?: return null

        val targetDate = String.format("%04d/%02d/%02d", jalaliYear, jalaliMonth, jalaliDay)
        val targetMillis = Jalali.parse(targetDate) ?: return null

        if (targetMillis < startMillis) return null

        var daysFromStart = ((targetMillis - startMillis) / 86_400_000L).toInt() + (startHijriDay - 1)

        var hijriYear = startHijriYear
        var hijriMonth = startHijriMonth
        var hijriDay = 1

        // از روز شروع، روزها رو می‌شمُریم تا به target برسیم
        // هر ماه: فرد ۳۰، زوج ۲۹
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

        val monthName = hijriMonthName(hijriMonth)

        return HijriCache(
            id = 0,
            jalaliDate = targetDate,
            jalaliYear = jalaliYear,
            jalaliMonth = jalaliMonth,
            jalaliDay = jalaliDay,
            hijriDay = hijriDay,
            hijriMonth = monthName,
            hijriYear = hijriYear,
            isHoliday = false,
            eventsJson = "[]"
        )
    }

    // ═══════════════════════════════════════════════════════════
    //  به‌روزرسانی از API (دکمه‌ی «به‌روزرسانی»)
    //  از ۱۳۹۰ تا ۱۴۱۰ همه رو یه بار دانلود می‌کنه
    // ═══════════════════════════════════════════════════════════
    suspend fun refreshFromApi(
        db: AppDb,
        onProgress: (current: Int, total: Int, year: Int) -> Unit = { _, _, _ -> }
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            var totalSaved = 0
            val years = (API_MIN_YEAR..API_MAX_YEAR).toList()

            for ((index, year) in years.withIndex()) {
                onProgress(index + 1, years.size, year)

                val result = HijriDataDownloader.downloadAndSave(db, year)
                if (result.isSuccess) {
                    totalSaved += result.getOrNull() ?: 0
                }

                // rate limit: 10/minute → 7 ثانیه بین درخواست‌ها
                if (index < years.size - 1) {
                    delay(7_000L)
                }
            }

            Result.success(totalSaved)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  کمک‌تابع‌ها
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
