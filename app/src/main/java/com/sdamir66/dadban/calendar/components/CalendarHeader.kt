package com.sdamir66.dadban.calendar.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sdamir66.dadban.calendar.data.CalendarType
import com.sdamir66.dadban.ui.theme.HeaderBlue
import java.util.Calendar
import java.util.Date

@Composable
fun CalendarHeader(
    currentDate: Date,
    primaryCalendar: CalendarType,
    onPrevYear: () -> Unit,
    onNextYear: () -> Unit,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onCalendarTypeChange: (CalendarType) -> Unit,
    onDateChange: (Date) -> Unit
) {
    var showYearPicker by remember { mutableStateOf(false) }
    
    Box(
        Modifier
            .fillMaxWidth()
            .background(
                color = HeaderBlue,
                shape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)
            )
            .statusBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 36.dp)
    ) {
        Column {
            // ═══ ردیف اول: فلش‌های سال + ماه ═══
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // ◄◄ سال قبل
                IconButton(onClick = onPrevYear) {
                    Text("◄◄", color = Color.White, fontSize = 14.sp)
                }
                
                // نام ماه + سال
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { showYearPicker = true }
                ) {
                    Text(
                        getMonthName(currentDate, primaryCalendar),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        getYear(currentDate, primaryCalendar).toString(),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                }
                
                // ►► سال بعد
                IconButton(onClick = onNextYear) {
                    Text("►►", color = Color.White, fontSize = 14.sp)
                }
            }
            
            Spacer(Modifier.height(4.dp))
            
            // ═══ ردیف دوم: سه تقویم (قابل کلیک) ═══
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CalendarTypeChip(
                    label = getSecondaryLabel(currentDate, CalendarType.JALALI),
                    isSelected = primaryCalendar == CalendarType.JALALI,
                    onClick = { onCalendarTypeChange(CalendarType.JALALI) }
                )
                Spacer(Modifier.width(8.dp))
                CalendarTypeChip(
                    label = getSecondaryLabel(currentDate, CalendarType.HIJRI),
                    isSelected = primaryCalendar == CalendarType.HIJRI,
                    onClick = { onCalendarTypeChange(CalendarType.HIJRI) }
                )
                Spacer(Modifier.width(8.dp))
                CalendarTypeChip(
                    label = getSecondaryLabel(currentDate, CalendarType.GREGORIAN),
                    isSelected = primaryCalendar == CalendarType.GREGORIAN,
                    onClick = { onCalendarTypeChange(CalendarType.GREGORIAN) }
                )
            }
        }
    }
    
    // ═══ Dialog انتخاب سال ═══
    if (showYearPicker) {
        YearPickerDialog(
            currentYear = getYear(currentDate, primaryCalendar),
            primaryCalendar = primaryCalendar,
            onYearSelected = { year ->
                onDateChange(setYear(currentDate, year, primaryCalendar))
                showYearPicker = false
            },
            onDismiss = { showYearPicker = false }
        )
    }
}

@Composable
private fun CalendarTypeChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        color = if (isSelected) Color.White else Color.White.copy(alpha = 0.2f),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) HeaderBlue else Color.White
        )
    }
}

// ═══════════════════════════════════════════════════════
// کمک‌تابع‌ها
// ═══════════════════════════════════════════════════════

private fun getMonthName(date: Date, type: CalendarType): String {
    return when (type) {
        CalendarType.JALALI -> {
            val j = com.sdamir66.dadban.util.Jalali.toJalaliPublic(date.time)
            com.sdamir66.dadban.util.Jalali.monthName(j[1])
        }
        CalendarType.GREGORIAN -> {
            val cal = Calendar.getInstance().apply { time = date }
            listOf(
                "ژانویه", "فوریه", "مارس", "آوریل", "مه", "ژوئن",
                "ژوئیه", "اوت", "سپتامبر", "اکتبر", "نوامبر", "دسامبر"
            )[cal.get(Calendar.MONTH)]
        }
        CalendarType.HIJRI -> {
            // TODO: تبدیل به قمری
            "ماه قمری"
        }
    }
}

private fun getYear(date: Date, type: CalendarType): Int {
    return when (type) {
        CalendarType.JALALI -> com.sdamir66.dadban.util.Jalali.toJalaliPublic(date.time)[0]
        CalendarType.GREGORIAN -> Calendar.getInstance().apply { time = date }.get(Calendar.YEAR)
        CalendarType.HIJRI -> {
            // TODO: تبدیل به قمری
            Calendar.getInstance().apply { time = date }.get(Calendar.YEAR) - 622
        }
    }
}

private fun getSecondaryLabel(date: Date, type: CalendarType): String {
    return when (type) {
        CalendarType.JALALI -> {
            val j = com.sdamir66.dadban.util.Jalali.toJalaliPublic(date.time)
            "${j[0]}/${j[1].toString().padStart(2, '0')}"
        }
        CalendarType.GREGORIAN -> {
            val cal = Calendar.getInstance().apply { time = date }
            "${cal.get(Calendar.YEAR)}/${(cal.get(Calendar.MONTH) + 1).toString().padStart(2, '0')}"
        }
        CalendarType.HIJRI -> {
            // TODO
            "قمری"
        }
    }
}

private fun setYear(date: Date, year: Int, type: CalendarType): Date {
    return when (type) {
        CalendarType.JALALI -> {
            val j = com.sdamir66.dadban.util.Jalali.toJalaliPublic(date.time)
            val millis = com.sdamir66.dadban.util.Jalali.parse(
                "%04d/%02d/%02d".format(year, j[1], j[2])
            )
            Date(millis ?: date.time)
        }
        CalendarType.GREGORIAN -> {
            Calendar.getInstance().apply {
                time = date
                set(Calendar.YEAR, year)
            }.time
        }
        CalendarType.HIJRI -> date // TODO
    }
}
