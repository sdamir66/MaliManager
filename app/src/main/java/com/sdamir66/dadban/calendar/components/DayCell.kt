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
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize().padding(2.dp)
        ) {
            // ═══ روز اصلی (بزرگ) ═══
            Text(
                toPersianDigits(day.toString()),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = textColor,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(2.dp))

            // ═══ روزهای فرعی (فقط روز، چپ/راست) ═══
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // ✅ میلادی (سمت چپ)
                Text(
                    if (primaryCalendar != CalendarType.GREGORIAN && showGregorianSmall) {
                        val cal = Calendar.getInstance().apply { time = date }
                        toPersianDigits(cal.get(Calendar.DAY_OF_MONTH).toString())
                    } else "",
                    fontSize = 9.sp,
                    color = if (isSelected) Color.White.copy(alpha = 0.85f) else Color.Gray,
                    textAlign = TextAlign.Start,
                    maxLines = 1
                )

                // ✅ قمری (سمت راست)
                Text(
                    if (primaryCalendar != CalendarType.HIJRI && showHijriSmall) {
                        val h = getHijriDate(date)
                        toPersianDigits(h[2].toString())
                    } else "",
                    fontSize = 9.sp,
                    color = if (isSelected) Color.White.copy(alpha = 0.85f) else Color.Gray,
                    textAlign = TextAlign.End,
                    maxLines = 1
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
