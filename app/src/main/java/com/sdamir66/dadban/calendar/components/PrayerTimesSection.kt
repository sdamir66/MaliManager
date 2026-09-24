package com.sdamir66.dadban.calendar.components

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

@Composable
fun PrayerTimesSection(
    prayerTimes: PrayerTimesData
) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        // ═══ عنوان ═══
        Text(
            if (prayerTimes.cityName.isNotBlank())
                "اوقات شرعی به وقت ${prayerTimes.cityName}"
            else
                "اوقات شرعی",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1B1B1F),
            fontSize = 16.sp,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )

        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                PrayerTimeItem("صبح", prayerTimes.fajr)
                PrayerTimeItem("طلوع", prayerTimes.sunrise)
                PrayerTimeItem("ظهر", prayerTimes.dhuhr)
                PrayerTimeItem("غروب", prayerTimes.sunset)
                PrayerTimeItem("مغرب", prayerTimes.maghrib)
                PrayerTimeItem("نیمه‌شب", prayerTimes.midnight)
            }
        }
    }
}

@Composable
private fun PrayerTimeItem(label: String, time: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 2.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = Color(0xFF5C5D72),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
        Spacer(Modifier.height(3.dp))
        Text(
            toPersianDigits(time),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = HeaderBlue,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

private fun toPersianDigits(input: String): String {
    return input.map { c ->
        if (c.isDigit()) ('۰' + (c - '0')) else c
    }.joinToString("")
}
