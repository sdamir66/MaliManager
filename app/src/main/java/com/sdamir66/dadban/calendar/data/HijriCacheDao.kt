package com.sdamir66.dadban.calendar.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface HijriCacheDao {

    @Query("SELECT * FROM hijri_cache WHERE jalaliDate = :date LIMIT 1")
    suspend fun getByJalaliDate(date: String): HijriCache?

    @Query("SELECT * FROM hijri_cache WHERE jalaliYear = :year ORDER BY jalaliMonth, jalaliDay")
    suspend fun getAllForYear(year: Int): List<HijriCache>

    @Query("SELECT * FROM hijri_cache WHERE jalaliYear = :year ORDER BY jalaliMonth, jalaliDay")
    fun getAllForYearFlow(year: Int): Flow<List<HijriCache>>

    @Query("SELECT COUNT(*) FROM hijri_cache WHERE jalaliYear = :year")
    suspend fun countForYear(year: Int): Int

    @Query("SELECT MIN(downloadedAt) FROM hijri_cache WHERE jalaliYear = :year")
    suspend fun getLastDownloadForYear(year: Int): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<HijriCache>)

    @Query("DELETE FROM hijri_cache WHERE jalaliYear = :year")
    suspend fun deleteForYear(year: Int)

    @Query("DELETE FROM hijri_cache")
    suspend fun clear()
}
