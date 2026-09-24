package com.sdamir66.dadban.calendar.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "hijri_cache",
    indices = [Index(value = ["jalaliDate"], unique = true)]
)
data class HijriCache(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    // ═══ تاریخ جلالی (کلید) ═══
    val jalaliDate: String,       // "1405/01/01"
    val jalaliYear: Int,
    val jalaliMonth: Int,
    val jalaliDay: Int,

    // ═══ تاریخ قمری ═══
    val hijriDay: Int,            // 1..30
    val hijriMonth: String,       // "شوال"
    val hijriYear: Int,           // 1447

    // ═══ تعطیلات و رویدادها ═══
    val isHoliday: Boolean,
    val eventsJson: String,       // JSON array از رویدادهای این روز

    // ═══ متادیتا ═══
    val downloadedAt: Long = System.currentTimeMillis()
)
