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
            modifier = Modifier.fillMaxSize().padding(1.dp)
        ) {
            // ═══ روز اصلی (بزرگ) ═══
            Text(
                toPersianDigits(day.toString()),
                fontSize = 20.sp,   // ← 18 → 20
                fontWeight = FontWeight.Bold,
                color = textColor,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(1.dp))  // ← 2 → 1

            // ═══ ردیف تاریخ‌های فرعی ═══
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // ─── فرعی چپ ───
                when {
                    // اگه تقویم اصلی میلادیه → جلالی با دایره
                    primaryCalendar == CalendarType.GREGORIAN -> {
                        Box(
                            Modifier
                                .background(
                                    color = if (isSelected) Color.White.copy(alpha = 0.25f)
                                            else HeaderBlue.copy(alpha = 0.15f),
                                    shape = CircleShape
                                )
                                .padding(horizontal = 4.dp, vertical = 1.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                toPersianDigits(
                                    Jalali.toJalaliPublic(date.time)[2].toString()
                                ),
                                fontSize = 12.sp,
                                color = if (isSelected) Color.White.copy(alpha = 0.9f)
                                        else HeaderBlue,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Start,
                                maxLines = 1
                            )
                        }
                    }
                    // وگرنه → میلادی بدون دایره
                    (settings?.showGregorianSmall ?: true) -> {
                        Text(
                            Calendar.getInstance().apply { time = date }
                                .get(Calendar.DAY_OF_MONTH).toString(),
                            fontSize = 13.sp,   // ← 12 → 13
                            color = if (isSelected) Color.White.copy(alpha = 0.9f)
                                    else Color(0xFF5C5D72),
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Start,
                            maxLines = 1
                        )
                    }
                    else -> Text("", fontSize = 13.sp)
                }

                // ─── فرعی راست ───
                when {
                    // اگه تقویم اصلی قمریه → جلالی با دایره
                    primaryCalendar == CalendarType.HIJRI -> {
                        if (settings?.showHijriSmall ?: true) {
                            Box(
                                Modifier
                                    .background(
                                        color = if (isSelected) Color.White.copy(alpha = 0.25f)
                                                else HeaderBlue.copy(alpha = 0.15f),
                                        shape = CircleShape
                                    )
                                    .padding(horizontal = 4.dp, vertical = 1.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    toPersianDigits(
                                        Jalali.toJalaliPublic(date.time)[2].toString()
                                    ),
                                    fontSize = 12.sp,
                                    color = if (isSelected) Color.White.copy(alpha = 0.9f)
                                            else HeaderBlue,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.End,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                    // وگرنه → قمری بدون دایره
                    (settings?.showHijriSmall ?: true) -> {
                        val h = getHijriFromCacheOrFallback(date, settings, hijriCacheMap)
                        Text(
                            toPersianDigits(h[2].toString()),
                            fontSize = 13.sp,   // ← 12 → 13
                            color = if (isSelected) Color.White.copy(alpha = 0.9f)
                                    else Color(0xFF5C5D72),
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.End,
                            maxLines = 1
                        )
                    }
                    else -> Text("", fontSize = 13.sp)
                }
            }
        }
    }
}

private fun toPersianDigits(input: String): String {
    return input.map { c -> if (c.isDigit()) ('۰' + (c - '0')) else c }.joinToString("")
}
