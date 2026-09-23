package com.sdamir66.dadban.calendar.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CalendarSettingsDao {
    
    @Query("SELECT * FROM calendar_settings WHERE id = 1")
    fun get(): Flow<CalendarSettings?>
    
    @Query("SELECT * FROM calendar_settings WHERE id = 1")
    suspend fun getNow(): CalendarSettings?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(settings: CalendarSettings)
    
    @Query("DELETE FROM calendar_settings")
    suspend fun clear()
}
