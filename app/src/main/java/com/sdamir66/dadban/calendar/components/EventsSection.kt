package com.sdamir66.dadban.calendar.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sdamir66.dadban.calendar.data.CalendarSettings
import com.sdamir66.dadban.calendar.data.CalendarType
import com.sdamir66.dadban.calendar.data.Event
import com.sdamir66.dadban.calendar.data.EventCategory
import com.sdamir66.dadban.calendar.data.HijriCache
import com.sdamir66.dadban.ui.theme.DebitRed
import com.sdamir66.dadban.util.Jalali
import org.json.JSONArray
import java.util.Calendar
import java.util.Date

/**
 * داده‌ی یه رویداد برای نمایش (چه از cache، چه از دیتابیس)
 */
data class DisplayEvent(
    val title: String,
    val isHoliday: Boolean,
    val category: EventCategory,
    val color: String = ""
)

@Composable
fun EventsSection(
    events: List<Event>,
    selectedDate: Date,
    primaryCalendar: CalendarType,
    settings: CalendarSettings?,
    hijriCacheMap: Map<String, HijriCache>
) {
    // ═══ اول از cache چک کن ═══
    val j = Jalali.toJalaliPublic(selectedDate.time)
    val key = "%04d/%02d/%02d".format(j[0], j[1], j[2])
    val cached = hijriCacheMap[key]

    // ═══ استخراج رویدادها از cache (اگه بود) ═══
    val cachedEvents: List<DisplayEvent> = remember(cached) {
        if (cached == null) emptyList()
        else {
            try {
                val arr = JSONArray(cached.eventsJson)
                (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    DisplayEvent(
                        title = o.optString("text", ""),
                        isHoliday = o.optBoolean("isHoliday", false),
                        category = if (o.optBoolean("isHoliday", false))
                            EventCategory.NATIONAL
                        else EventCategory.RELIGIOUS
                    )
                }
            } catch (e: Exception) { emptyList() }
        }
    }

    // ═══ رویدادهای دیتابیس (fallback یا رویدادهای کاربر) ═══
    val dbEvents = remember(events, selectedDate, hijriCacheMap) {
        val cal = Calendar.getInstance().apply { time = selectedDate }
        val gM = cal.get(Calendar.MONTH) + 1
        val gD = cal.get(Calendar.DAY_OF_MONTH)
        val jj = Jalali.toJalaliPublic(selectedDate.time)
        val jM = jj[1]; val jD = jj[2]
        val hijri = getHijriFromCacheOrFallback(selectedDate, settings, hijriCacheMap)

        events.filter { e ->
            when (e.calendarType) {
                CalendarType.JALALI -> e.month == jM && e.day == jD
                CalendarType.GREGORIAN -> e.month == gM && e.day == gD
                CalendarType.HIJRI -> e.month == hijri[1] && e.day == hijri[2]
            }
        }.map {
            DisplayEvent(
                title = it.title,
                isHoliday = it.isHoliday,
                category = it.category,
                color = it.color
            )
        }
    }

    // ✅ اگه cache داشتیم، از cache استفاده کن؛ وگرنه از دیتابیس
    val finalEvents = if (cachedEvents.isNotEmpty()) cachedEvents
                      else if (cached != null) cachedEvents  // cache خالی = بدون رویداد
                      else dbEvents

    Column(Modifier.padding(horizontal = 16.dp)) {
        Text(
            "رویدادهای ${Jalali.format(selectedDate.time).substringBefore(" ")}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1B1B1F),
            fontSize = 16.sp,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )

        if (finalEvents.isEmpty()) {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
                Text("رویدادی برای این روز ثبت نشده",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF5C5D72))
            }
        } else {
            LazyColumn(Modifier.heightIn(max = 500.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(finalEvents.size) { idx ->
                    EventCard(finalEvents[idx], settings)
                }
            }
        }
    }
}

@Composable
private fun EventCard(event: DisplayEvent, settings: CalendarSettings?) {
    val color = if (event.isHoliday) {
        parseColor(settings?.colorHoliday ?: "#E53935", DebitRed)
    } else {
        val hex = when (event.category) {
            EventCategory.RELIGIOUS -> settings?.colorReligious ?: "#4CAF50"
            EventCategory.NATIONAL -> settings?.colorNational ?: "#2196F3"
            EventCategory.GLOBAL -> settings?.colorGlobal ?: "#9C27B0"
            EventCategory.PERSONAL -> settings?.colorUser ?: "#9E9E9E"
        }
        parseColor(hex, Color(0xFF9E9E9E))
    }

    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Row(Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(12.dp).background(color, CircleShape))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(event.title, style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (event.isHoliday) FontWeight.Bold else FontWeight.Medium,
                    color = if (event.isHoliday) color else Color(0xFF1B1B1F))
            }
            if (event.isHoliday) {
                Box(Modifier.background(color.copy(alpha = 0.1f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)) {
                    Text("تعطیل", style = MaterialTheme.typography.labelSmall,
                        color = color, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                }
            }
        }
    }
}

private fun parseColor(hex: String, default: Color): Color {
    return try { Color(android.graphics.Color.parseColor(hex)) } catch (e: Exception) { default }
}
