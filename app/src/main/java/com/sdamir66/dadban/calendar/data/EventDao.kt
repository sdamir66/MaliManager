package com.sdamir66.dadban.calendar.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {
    
    @Query("SELECT * FROM events ORDER BY month, day")
    fun all(): Flow<List<Event>>
    
    @Query("SELECT * FROM events ORDER BY month, day")
    suspend fun allNow(): List<Event>
    
    @Query("SELECT * FROM events WHERE isUserCreated = 1 ORDER BY month, day")
    fun userEvents(): Flow<List<Event>>
    
    @Query("SELECT * FROM events WHERE isUserCreated = 1 ORDER BY month, day")
    suspend fun userEventsNow(): List<Event>
    
    @Query("SELECT * FROM events WHERE calendarType = :type AND month = :month AND day = :day")
    suspend fun byDate(type: CalendarType, month: Int, day: Int): List<Event>
    
    @Insert
    suspend fun insert(event: Event): Long
    
    @Insert
    suspend fun insertAll(events: List<Event>)
    
    @Update
    suspend fun update(event: Event)
    
    @Delete
    suspend fun delete(event: Event)
    
    @Query("DELETE FROM events WHERE isUserCreated = 1")
    suspend fun deleteAllUserEvents()
    
    @Query("DELETE FROM events")
    suspend fun clear()
}
