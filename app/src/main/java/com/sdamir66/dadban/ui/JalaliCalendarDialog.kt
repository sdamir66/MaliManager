package com.sdamir66.dadban.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sdamir66.dadban.ui.theme.HeaderBlue
import com.sdamir66.dadban.util.Jalali
import java.util.Locale

@Composable
fun JalaliCalendarDialog(
    initialYear: Int,
    initialMonth: Int,
    initialDay: Int,
    onSelect: (year: Int, month: Int, day: Int) -> Unit,
    onCancel: () -> Unit
) {
    var year by remember { mutableIntStateOf(initialYear) }
    var month by remember { mutableIntStateOf(initialMonth) }
    var day by remember { mutableIntStateOf(initialDay) }

    val daysInMonth = Jalali.daysInMonth(year, month)
    val firstDayMillis = Jalali.parse("%04d/%02d/%02d".format(Locale.US, year, month, 1)) ?: 0L

    val firstDayOfWeek = remember(firstDayMillis) {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = firstDayMillis }
        when (cal.get(java.util.Calendar.DAY_OF_WEEK)) {
            java.util.Calendar.SATURDAY -> 0
            java.util.Calendar.SUNDAY -> 1
            java.util.Calendar.MONDAY -> 2
            java.util.Calendar.TUESDAY -> 3
            java.util.Calendar.WEDNESDAY -> 4
            java.util.Calendar.THURSDAY -> 5
            java.util.Calendar.FRIDAY -> 6
            else -> 0
        }
    }

    val monthNames = listOf(
        "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
        "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"
    )

    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = HeaderBlue,
        shape = RoundedCornerShape(20.dp),
        title = {
            Column {
                Text("انتخاب تاریخ", fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(Modifier.height(4.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        if (month == 1) { month = 12; year-- } else month--
                        day = 1
                    }) {
                        Text("‹", fontSize = 24.sp, color = Color.White)
                    }
                    Text(
                        "${monthNames[month - 1]} $year",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    IconButton(onClick = {
                        if (month == 12) { month = 1; year++ } else month++
                        day = 1
                    }) {
                        Text("›", fontSize = 24.sp, color = Color.White)
                    }
                }
            }
        },
        text = {
            Column {
                Row(Modifier.fillMaxWidth()) {
                    listOf("ش", "ی", "د", "س", "چ", "پ", "ج").forEach { d ->
                        Text(
                            d,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))

                val totalCells = firstDayOfWeek + daysInMonth
                val rows = (totalCells + 6) / 7

                for (r in 0 until rows) {
                    Row(Modifier.fillMaxWidth()) {
                        for (c in 0 until 7) {
                            val cellIndex = r * 7 + c
                            val dayNumber = cellIndex - firstDayOfWeek + 1
                            if (dayNumber in 1..daysInMonth) {
                                val isSelected = dayNumber == day
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .padding(2.dp)
                                        .clip(CircleShape)
                                        .background(if (isSelected) Color.White else Color.Transparent)
                                        .clickable { day = dayNumber },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        dayNumber.toString(),
                                        color = if (isSelected) HeaderBlue else Color.White,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 13.sp
                                    )
                                }
                            } else {
                                Box(Modifier.weight(1f).aspectRatio(1f))
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Text("سال:", style = MaterialTheme.typography.bodySmall, color = Color.White)
                    Spacer(Modifier.width(4.dp))
                    IconButton(onClick = { year -= 1 }, modifier = Modifier.size(32.dp)) {
                        Text("−", fontSize = 20.sp, color = Color.White)
                    }
                    Text(year.toString(), fontWeight = FontWeight.Bold, color = Color.White)
                    IconButton(onClick = { year += 1 }, modifier = Modifier.size(32.dp)) {
                        Text("+", fontSize = 20.sp, color = Color.White)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSelect(year, month, day) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = HeaderBlue
                )
            ) { Text("تأیید", fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text("انصراف", color = Color.White) }
        }
    )
}
