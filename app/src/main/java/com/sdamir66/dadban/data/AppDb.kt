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
    version = 2,
    exportSchema = false
)
abstract class AppDb : RoomDatabase() {
    
    abstract fun persons(): PersonDao
    abstract fun accounts(): AccountDao
    abstract fun tx(): TxDao
    abstract fun profitPeriod(): ProfitPeriodDao
    
    // ✅ جدید
    abstract fun eventDao(): EventDao
    abstract fun calendarSettingsDao(): CalendarSettingsDao
    
    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // جدول رویدادها
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
                
                // جدول تنظیمات تقویم
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
        
        fun build(context: Context): AppDb {
            return Room.databaseBuilder(
                context.applicationContext,
                AppDb::class.java,
                "finance.db"
            )
                .addMigrations(MIGRATION_1_2)  // ✅ مهاجرت امن
                .build()
        }
    }
}
