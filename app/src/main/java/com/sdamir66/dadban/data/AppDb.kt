@Database(
    entities = [
        Person::class,
        Account::class,
        Transaction::class,
        ProfitPeriod::class,
        Event::class,
        CalendarSettings::class,
        HijriCache::class          // ✅ اضافه کن
    ],
    version = 5,                    // ✅ از ۴ به ۵
    exportSchema = false
)
abstract class AppDb : RoomDatabase() {

    // ... DAO های قبلی

    abstract fun hijriCacheDao(): HijriCacheDao   // ✅ اضافه کن

    companion object {
        // ... MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4

        // ✅ migration نسخه ۵
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS hijri_cache (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        jalaliDate TEXT NOT NULL,
                        jalaliYear INTEGER NOT NULL,
                        jalaliMonth INTEGER NOT NULL,
                        jalaliDay INTEGER NOT NULL,
                        hijriDay INTEGER NOT NULL,
                        hijriMonth TEXT NOT NULL,
                        hijriYear INTEGER NOT NULL,
                        isHoliday INTEGER NOT NULL DEFAULT 0,
                        eventsJson TEXT NOT NULL DEFAULT '[]',
                        downloadedAt INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_hijri_cache_jalaliDate ON hijri_cache(jalaliDate)")
            }
        }

        fun build(context: Context): AppDb {
            return Room.databaseBuilder(
                context.applicationContext,
                AppDb::class.java,
                "finance.db"
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .build()
        }
    }
}
