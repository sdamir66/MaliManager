package com.sdamir66.dadban.calendar.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import com.sdamir66.dadban.calendar.data.CalendarSettings
import com.sdamir66.dadban.calendar.data.CalendarType
import com.sdamir66.dadban.calendar.data.HijriCache
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
    settings: CalendarSettings?,
    hijriCacheMap: Map<String, HijriCache>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
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
            .padding(1.dp)
            .clip(RoundedCornerShape(8.dp))
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

            // ═══ ردیف تاریخ‌های فرعی ═══
            // چپ: میلادی (همیشه بدون دایره)
            // راست: قمری یا جلالی (بسته به تقویم اصلی)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // ─── فرعی چپ: میلادی (همیشه) ───
                Text(
                    if (primaryCalendar != CalendarType.GREGORIAN &&
                        (settings?.showGregorianSmall ?: true)) {
                        Calendar.getInstance().apply { time = date }
                            .get(Calendar.DAY_OF_MONTH).toString()
                    } else "",
                    fontSize = 12.sp,
                    color = if (isSelected) Color.White.copy(alpha = 0.9f)
                            else Color(0xFF5C5D72),
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Start,
                    maxLines = 1
                )

                // ─── فرعی راست: قمری یا جلالی ───
                if (primaryCalendar == CalendarType.HIJRI) {
                    // اگه تقویم اصلی قمریه → جلالی با دایره
                    if (settings?.showGregorianSmall ?: true) {
                        Box(
                            Modifier
                                .border(
                                    width = 1.5.dp,
                                    color = if (isSelected) Color.White.copy(alpha = 0.9f)
                                            else HeaderBlue.copy(alpha = 0.6f),
                                    shape = CircleShape
                                )
                                .padding(horizontal = 3.dp, vertical = 1.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                toPersianDigits(
                                    Jalali.toJalaliPublic(date.time)[2].toString()
                                ),
                                fontSize = 11.sp,
                                color = if (isSelected) Color.White.copy(alpha = 0.9f)
                                        else HeaderBlue,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.End,
                                maxLines = 1
                            )
                        }
                    }
                } else {
                    // اگه تقویم اصلی جلالی یا میلادیه → قمری بدون دایره
                    if (primaryCalendar != CalendarType.HIJRI &&
                        (settings?.showHijriSmall ?: true)) {
                        val h = getHijriFromCacheOrFallback(date, settings, hijriCacheMap)
                        Text(
                            toPersianDigits(h[2].toString()),
                            fontSize = 12.sp,
                            color = if (isSelected) Color.White.copy(alpha = 0.9f)
                                    else Color(0xFF5C5D72),
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.End,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

private fun toPersianDigits(input: String): String {
    return input.map { c -> if (c.isDigit()) ('۰' + (c - '0')) else c }.joinToString("")
}
