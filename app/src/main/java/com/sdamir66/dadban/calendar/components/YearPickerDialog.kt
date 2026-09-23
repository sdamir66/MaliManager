package com.sdamir66.dadban.calendar.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sdamir66.dadban.calendar.data.CalendarType
import com.sdamir66.dadban.ui.theme.HeaderBlue

@Composable
fun YearPickerDialog(
    currentYear: Int,
    primaryCalendar: CalendarType,
    onYearSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedYear by remember { mutableIntStateOf(currentYear) }

    val years = remember(currentYear) {
        ((currentYear - 10)..(currentYear + 10)).toList()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.border(2.dp, HeaderBlue, RoundedCornerShape(20.dp)),
        title = {
            Text("انتخاب سال", fontWeight = FontWeight.Bold, color = HeaderBlue)
        },
        text = {
            Column {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { selectedYear-- }) {
                        Text("−", fontSize = 24.sp, color = HeaderBlue, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        selectedYear.toString(),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = HeaderBlue,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                    IconButton(onClick = { selectedYear++ }) {
                        Text("+", fontSize = 24.sp, color = HeaderBlue, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(Modifier.height(16.dp))

                LazyColumn(
                    modifier = Modifier.heightIn(max = 300.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(years) { year ->
                        Card(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onYearSelected(year)
                                    onDismiss()
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = if (year == selectedYear)
                                    HeaderBlue else Color(0xFFF0F1F7)
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                year.toString(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                textAlign = TextAlign.Center,
                                color = if (year == selectedYear)
                                    Color.White else HeaderBlue,
                                fontWeight = if (year == selectedYear)
                                    FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onYearSelected(selectedYear)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = HeaderBlue,
                    contentColor = Color.White
                )
            ) {
                Text("تایید", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("انصراف", color = HeaderBlue)
            }
        }
    )
}
