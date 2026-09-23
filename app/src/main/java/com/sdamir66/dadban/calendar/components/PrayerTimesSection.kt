package com.sdamir66.dadban.calendar.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sdamir66.dadban.calendar.prayer.PrayerTimesData
import com.sdamir66.dadban.ui.theme.HeaderBlue
import com.sdamir66.dadban.util.Jalali
import java.util.Date

@Composable
fun PrayerTimesSection(
    prayerTimes: PrayerTimesData,
    selectedDate: Date
) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        // ═══ عنوان ═══
        Text(
            "اوقات شرعی",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1B1B1F),
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )
        
        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                // ═══ تاریخ ═══
                Text(
                    Jalali.format(selectedDate.time).substringBefore(" "),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                
                Spacer(Modifier.height(12.dp))
                
                // ═══ ردیف اول: طلوع و غروب ═══
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    PrayerTimeItem("🌅 طلوع", prayerTimes.sunrise)
                    PrayerTimeItem("🌇 غروب", prayerTimes.maghrib)
                }
                
                Spacer(Modifier.height(12.dp))
                
                // ═══ خط جداکننده ═══
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color(0xFFEEEEEE))
                )
                
                Spacer(Modifier.height(12.dp))
                
                // ═══ ردیف دوم: اذان‌ها ═══
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    PrayerTimeItem("☀️ صبح", prayerTimes.fajr)
                    PrayerTimeItem("🕛 ظهر", prayerTimes.dhuhr)
                    PrayerTimeItem("🌆 مغرب", prayerTimes.maghrib)
                    PrayerTimeItem("🌙 عشا", prayerTimes.isha)
                }
            }
        }
    }
}

@Composable
private fun PrayerTimeItem(label: String, time: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(4.dp))
        Text(
            toPersianDigits(time),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = HeaderBlue
        )
    }
}

private fun toPersianDigits(input: String): String {
    return input.map { c ->
        if (c.isDigit()) ('۰' + (c - '0')) else c
    }.joinToString("")
}
