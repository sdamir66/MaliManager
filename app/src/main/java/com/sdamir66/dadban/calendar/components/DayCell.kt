package com.sdamir66.dadban.calendar.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sdamir66.dadban.calendar.data.CalendarType
import com.sdamir66.dadban.ui.theme.DebitRed
import com.sdamir66.dadban.ui.theme.HeaderBlue
import com.sdamir66.dadban.util.Jalali
import java.util.Calendar
import java.util.Date

@Composable
fun DayCell(
    day: Int,
    date: Date,
    isSelected: Boolean,
    isToday: Boolean,
    isFriday: Boolean,
    hasHoliday: Boolean,
    primaryCalendar: CalendarType,
    showGregorianSmall: Boolean,
    showHijriSmall: Boolean,
    eidFitrOffset: Int,
    eidFitrHijriYear: Int?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // ═══ رنگ‌بندی ═══
    val backgroundColor = when {
        isSelected -> HeaderBlue
        isToday -> HeaderBlue.copy(alpha = 0.15f)
        hasHoliday -> Color(0xFFFFEBEE)
        isFriday -> Color(0xFFFFF3E0)
        else -> Color(0xFFF8F9FC)
    }

    val textColor = when {
        isSelected -> Color.White
        hasHoliday -> DebitRed
        isFriday -> Color(0xFFE65100)
        else -> Color(0xFF1B1B1F)
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(backgroundColor)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // ═══ روز اصلی (بزرگ) ═══
            Text(
                toPersianDigits(day.toString()),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = textColor,
                textAlign = TextAlign.Center
            )

            // ═══ روزهای کوچیک (میلادی / قمری) ═══
            val subTexts = mutableListOf<String>()

            // میلادی (اگه تقویم اصلی جلالی یا قمری باشه)
            if (primaryCalendar != CalendarType.GREGORIAN && showGregorianSmall) {
                val cal = Calendar.getInstance().apply { time = date }
                val gDay = cal.get(Calendar.DAY_OF_MONTH)
                val gMonth = cal.get(Calendar.MONTH) + 1
                subTexts.add("$gMonth.$gDay")
            }

            // جلالی (اگه تقویم اصلی میلادی یا قمری باشه)
            if (primaryCalendar != CalendarType.JALALI && showGregorianSmall) {
                val j = Jalali.toJalaliPublic(date.time)
                subTexts.add("${j[1]}.${j[2]}")
            }

            // قمری (اگه تقویم اصلی جلالی یا میلادی باشه)
            if (primaryCalendar != CalendarType.HIJRI && showHijriSmall) {
                val h = getHijriDate(date)
                subTexts.add(toPersianDigits("${h[2]}"))
            }

            if (subTexts.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    subTexts.joinToString(" • "),
                    fontSize = 8.sp,
                    color = if (isSelected) Color.White.copy(alpha = 0.85f) else Color.Gray,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    lineHeight = 9.sp
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════
// کمک‌تابع‌ها
// ═══════════════════════════════════════════════════════

private fun toPersianDigits(input: String): String {
    return input.map { c ->
        if (c.isDigit()) ('۰' + (c - '0')) else c
    }.joinToString("")
}
