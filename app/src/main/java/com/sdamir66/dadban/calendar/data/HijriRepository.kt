object HijriRepository {

    // ═══ همگام‌سازی اولیه (فقط یک بار) ═══
    suspend fun initializeIfNeeded(context: Context, db: AppDb) {
        val dao = db.hijriCacheDao()
        
        // اگه دیتابیس خالیه، seed کن
        if (dao.countForYear(1400) == 0) {
            // ۱. از asset (قبل از ۱۳۹۰)
            seedFromAsset(context, dao)
            
            // ۲. از API (۱۳۹۰ تا ۱۴۱۰)
            seedFromApi(db)
        }
    }

    // ═══ گرفتن تاریخ قمری — فقط از دیتابیس ═══
    suspend fun getHijriDate(
        db: AppDb,
        jalaliYear: Int,
        jalaliMonth: Int,
        jalaliDay: Int
    ): HijriCache? {
        val jalaliDate = String.format("%04d/%02d/%02d", jalaliYear, jalaliMonth, jalaliDay)
        return db.hijriCacheDao().getByJalaliDate(jalaliDate)
    }

    // ═══ seed از asset ═══
    private suspend fun seedFromAsset(context: Context, dao: HijriCacheDao) {
        HijriOfficialData.ensureLoaded(context)
        val allCaches = HijriOfficialData.getAllCaches()
        dao.insertAll(allCaches)
    }

    // ═══ seed از API ═══
    private suspend fun seedFromApi(db: AppDb) {
        for (year in 1390..1410) {
            HijriDataDownloader.downloadAndSave(db, year)
            delay(500) // جلوگیری از rate limit
        }
    }
}
