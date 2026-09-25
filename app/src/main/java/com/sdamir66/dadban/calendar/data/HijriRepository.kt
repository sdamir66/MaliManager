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
//  ۱. دیتابیس (شامل asset seed شده + API cache)
//  ۲. محاسبه از روی شروع ماه قمری (توی دیتابیس)
//  ۳. حالت قراردادی (بعد از ۱۴۱۰)
// ═══════════════════════════════════════════════════════════════

object HijriRepository {

    // ═══ حداقل سالی که API پشتیبانی می‌کنه ═══
    // (چون asset تا 1404 رو پوشش می‌ده)
    private const val MIN_API_YEAR = 1405

    // ═══ نقطه‌ی شروع حالت قراردادی ═══
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
        val lastDayOf1410 = "1410/12/29"  // فرض: 1410 کبیسه نیست
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
    //  به‌روزرسانی از API
    //  از سال ۱۴۰۵ شروع می‌کنه و تا هر سالی که API جواب بده ادامه می‌ده
    // ═══════════════════════════════════════════════════════════
    suspend fun refreshFromApi(
        db: AppDb,
        onProgress: (current: Int, year: Int) -> Unit = { _, _ -> }
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            // ═══ همیشه از 1405 شروع کن (چون asset تا 1404 رو پوشش می‌ده) ═══
            val startYear = MIN_API_YEAR

            // ═══ حداکثر 20 سال جلوتر (فقط برای امنیت) ═══
            val maxAttemptYear = startYear + 20

            var totalSaved = 0
            var successfulYears = 0

            for (year in startYear..maxAttemptYear) {
                onProgress(successfulYears + 1, year)

                // ═══ اگه سال توی دیتابیس هست، رد شو ═══
                val existingCount = db.hijriCacheDao().countForYear(year)
                if (existingCount > 0) {
                    successfulYears++
                    continue
                }

                // ═══ تلاش برای دانلود ═══
                val result = HijriDataDownloader.downloadAndSave(db, year)

                if (result.isSuccess) {
                    totalSaved += result.getOrNull() ?: 0
                    successfulYears++
                    delay(7_000L)  // rate limit
                } else {
                    // ═══ خطا داد (احتمالاً 404)، متوقف شو ═══
                    break
                }
            }

            if (successfulYears == 0) {
                return@withContext Result.failure(
                    Exception("هیچ سالی دانلود نشد. اتصال اینترنت رو چک کن.")
                )
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
