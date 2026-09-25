package com.sdamir66.dadban.calendar.data

import android.content.Context
import com.sdamir66.dadban.data.AppDb

// ═══════════════════════════════════════════════════════════════
//  HijriRepository
//  لایه‌ی اصلی دسترسی به تاریخ قمری
//  اولویت: asset رسمی → cache دیتابیس → محاسبه‌ی پیش‌فرض
// ═══════════════════════════════════════════════════════════════

object HijriRepository {

    // ═══════════════════════════════════════════════════════════
    //  بارگذاری اولیه (یک بار در MainActivity صدا زده می‌شه)
    // ═══════════════════════════════════════════════════════════
    fun init(context: Context) {
        HijriOfficialData.ensureLoaded(context.applicationContext)
    }

    // ═══════════════════════════════════════════════════════════
    //  گرفتن تاریخ قمری برای یه روز خاص
    //  ورودی: سال، ماه، روز جلالی
    //  خروجی: HijriCache (شامل روز، ماه، سال قمری، تعطیلات، رویدادها)
    // ═══════════════════════════════════════════════════════════
    suspend fun getHijriDate(
        db: AppDb,
        jalaliYear: Int,
        jalaliMonth: Int,
        jalaliDay: Int
    ): HijriCache? {
        val jalaliDate = String.format("%04d/%02d/%02d", jalaliYear, jalaliMonth, jalaliDay)

        // ─── منبع ۱: دیتای رسمی مؤسسه ژئوفیزیک (asset) ───
        HijriOfficialData.findByJalaliDate(jalaliDate)?.let { return it }

        // ─── منبع ۲: cache دیتابیس (از API) ───
        db.hijriCacheDao().getByJalaliDate(jalaliDate)?.let { return it }

        // ─── منبع ۳: محاسبه از روی شروع ماه قمری ───
        return calculateFromNearestMonthStart(jalaliYear, jalaliMonth, jalaliDay)
    }

    // ═══════════════════════════════════════════════════════════
    //  محاسبه‌ی تاریخ قمری از روی شروع نزدیک‌ترین ماه قمری
    //  این تابع از دیتای رسمی asset استفاده می‌کنه
    // ═══════════════════════════════════════════════════════════
    private fun calculateFromNearestMonthStart(
        jalaliYear: Int,
        jalaliMonth: Int,
        jalaliDay: Int
    ): HijriCache? {
        val targetJalaliDate = String.format("%04d/%02d/%02d", jalaliYear, jalaliMonth, jalaliDay)

        // پیدا کردن شروع ماه قمری که این روز توش قرار می‌گیره
        // یعنی بزرگ‌ترین start که <= targetJalaliDate باشه
        var bestStart: HijriCache? = null
        var bestStartDate: String? = null

        for ((hijriKey, startDate) in HijriOfficialData.getAllStarts()) {
            if (startDate <= targetJalaliDate) {
                if (bestStartDate == null || startDate > bestStartDate) {
                    bestStartDate = startDate
                    bestStart = HijriOfficialData.findByJalaliDate(startDate)
                }
            }
        }

        if (bestStart == null || bestStartDate == null) return null

        // محاسبه‌ی تعداد روز از شروع ماه قمری
        val daysDiff = daysBetween(bestStartDate, targetJalaliDate)
        if (daysDiff < 0) return null

        // تاریخ قمری این روز
        val hijriDay = bestStart.hijriDay + daysDiff.toInt()
        if (hijriDay < 1 || hijriDay > 30) return null

        return bestStart.copy(
            jalaliDate = targetJalaliDate,
            jalaliYear = jalaliYear,
            jalaliMonth = jalaliMonth,
            jalaliDay = jalaliDay,
            hijriDay = hijriDay
        )
    }

    // ═══════════════════════════════════════════════════════════
    //  محاسبه‌ی تعداد روز بین دو تاریخ جلالی
    // ═══════════════════════════════════════════════════════════
    private fun daysBetween(fromJalaliDate: String, toJalaliDate: String): Long {
        val fromMillis = com.sdamir66.dadban.util.Jalali.parse(fromJalaliDate) ?: return 0L
        val toMillis = com.sdamir66.dadban.util.Jalali.parse(toJalaliDate) ?: return 0L
        return (toMillis - fromMillis) / 86_400_000L
    }

    // ═══════════════════════════════════════════════════════════
    //  آیا برای این سال جلالی، دیتای قمری آماده داریم؟
    //  (یا توی asset هست یا توی cache دیتابیس)
    // ═══════════════════════════════════════════════════════════
    suspend fun isYearAvailable(db: AppDb, jalaliYear: Int): Boolean {
        // چک asset
        if (HijriOfficialData.hasJalaliYear(jalaliYear)) return true

        // چک دیتابیس
        val count = db.hijriCacheDao().countForYear(jalaliYear)
        return count > 0
    }

    // ═══════════════════════════════════════════════════════════
    //  به‌روزرسانی از API (برای سال‌هایی که توی asset نیستن)
    // ═══════════════════════════════════════════════════════════
    suspend fun refreshFromApi(db: AppDb, jalaliYear: Int): Result<Int> {
        return HijriDataDownloader.downloadAndSave(db, jalaliYear)
    }
}
