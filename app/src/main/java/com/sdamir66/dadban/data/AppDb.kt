package com.sdamir66.dadban.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.sdamir66.dadban.calendar.data.CalendarSettings
import com.sdamir66.dadban.calendar.data.CalendarSettingsDao
import com.sdamir66.dadban.calendar.data.Event
import com.sdamir66.dadban.calendar.data.EventDao

@Database(
    entities = [
        Person::class,
        Account::class,
        Transaction::class,
        ProfitPeriod::class,
        Event::class,
        CalendarSettings::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDb : RoomDatabase() {

    abstract fun persons(): PersonDao
    abstract fun accounts(): AccountDao
    abstract fun tx(): TxDao
    abstract fun profitPeriod(): ProfitPeriodDao
    abstract fun eventDao(): EventDao
    abstract fun calendarSettingsDao(): CalendarSettingsDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS events (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        title TEXT NOT NULL,
                        description TEXT NOT NULL DEFAULT '',
                        calendarType TEXT NOT NULL,
                        month INTEGER NOT NULL,
                        day INTEGER NOT NULL,
                        year INTEGER,
                        isHoliday INTEGER NOT NULL DEFAULT 0,
                        category TEXT NOT NULL,
                        color TEXT NOT NULL DEFAULT '#4C5FD7',
                        isUserCreated INTEGER NOT NULL DEFAULT 0,
                        reminderMinutesBefore INTEGER
                    )
                """.trimIndent())
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS calendar_settings (
                        id INTEGER PRIMARY KEY NOT NULL,
                        eidFitrOffset INTEGER NOT NULL DEFAULT 0,
                        eidFitrHijriYear INTEGER,
                        defaultCalendar TEXT NOT NULL DEFAULT 'JALALI',
                        showGregorianSmall INTEGER NOT NULL DEFAULT 1,
                        showHijriSmall INTEGER NOT NULL DEFAULT 1,
                        showHolidays INTEGER NOT NULL DEFAULT 1,
                        showReligiousNonHoliday INTEGER NOT NULL DEFAULT 0,
                        showNationalNonHoliday INTEGER NOT NULL DEFAULT 0,
                        showGlobalEvents INTEGER NOT NULL DEFAULT 0,
                        showUserEvents INTEGER NOT NULL DEFAULT 1,
                        locationMode TEXT NOT NULL DEFAULT 'MANUAL',
                        cityName TEXT NOT NULL DEFAULT 'تهران',
                        latitude REAL NOT NULL DEFAULT 35.6892,
                        longitude REAL NOT NULL DEFAULT 51.3890,
                        showPrayerTimes INTEGER NOT NULL DEFAULT 1
                    )
                """.trimIndent())
            }
        }

        // ✅ migration نسخه ۳: تغییر amount از Long به Double
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // ساخت جدول جدید با amount به صورت REAL
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS transactions_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        accountId INTEGER NOT NULL,
                        dateMillis INTEGER NOT NULL,
                        type TEXT NOT NULL,
                        amount REAL NOT NULL,
                        note TEXT NOT NULL DEFAULT '',
                        isAutoProfit INTEGER NOT NULL DEFAULT 0,
                        profitKey TEXT
                    )
                """.trimIndent())
                // کپی دیتا از جدول قدیمی
                db.execSQL("""
                    INSERT INTO transactions_new (id, accountId, dateMillis, type, amount, note, isAutoProfit, profitKey)
                    SELECT id, accountId, dateMillis, type, CAST(amount AS REAL), note, isAutoProfit, profitKey FROM transactions
                """.trimIndent())
                // حذف جدول قدیمی و تغییر نام
                db.execSQL("DROP TABLE transactions")
                db.execSQL("ALTER TABLE transactions_new RENAME TO transactions")
            }
        }

        fun build(context: Context): AppDb {
            return Room.databaseBuilder(
                context.applicationContext,
                AppDb::class.java,
                "finance.db"
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
        }
    }
}
