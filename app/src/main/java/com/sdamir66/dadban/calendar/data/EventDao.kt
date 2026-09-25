package com.sdamir66.dadban.calendar.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {

    @Query("SELECT * FROM events ORDER BY month, day")
    fun all(): Flow<List<Event>>

    @Query("SELECT * FROM events ORDER BY month, day")
    suspend fun allNow(): List<Event>

    @Query("SELECT * FROM events WHERE id = :id LIMIT 1")
    suspend fun byId(id: Long): Event?

    @Query("SELECT * FROM events WHERE calendarType = :type AND month = :month AND day = :day")
    suspend fun byDate(type: CalendarType, month: Int, day: Int): List<Event>

    @Query("SELECT * FROM events WHERE isUserCreated = 1 ORDER BY month, day")
    fun allUserEvents(): Flow<List<Event>>

    @Insert
    suspend fun insert(e: Event): Long

    @Insert
    suspend fun insertAll(events: List<Event>)

    @Update
    suspend fun update(e: Event)

    @Delete
    suspend fun delete(e: Event)

    @Query("DELETE FROM events")
    suspend fun clear()

    // ✅ حذف رویدادهای کاربر (برای بکاپ)
    @Query("DELETE FROM events WHERE isUserCreated = 1")
    suspend fun deleteAllUserEvents()

    // ✅ حذف رویدادهای خودکار (از calendar.json)
    @Query("DELETE FROM events WHERE isUserCreated = 0")
    suspend fun deleteAllAutoEvents()
}
