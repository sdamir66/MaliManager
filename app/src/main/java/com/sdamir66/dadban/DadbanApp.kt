package com.sdamir66.dadban

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sdamir66.dadban.calendar.CalendarScreen
import com.sdamir66.dadban.data.AppDb
import com.sdamir66.dadban.ui.theme.HeaderBlue

enum class DadbanTab(
    val title: String,
    val icon: ImageVector
) {
    CALENDAR("تقویم", Icons.Default.DateRange),
    FINANCE("مالی", Icons.Default.AccountBalance),
    SETTINGS("تنظیمات", Icons.Default.Settings)
}

@Composable
fun DadbanApp(db: AppDb) {
    var currentTab by remember { mutableStateOf(DadbanTab.CALENDAR) }

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = Color.White,
                tonalElevation = 8.dp
            ) {
                DadbanTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = currentTab == tab,
                        onClick = { currentTab = tab },
                        icon = {
                            Icon(
                                tab.icon,
                                contentDescription = tab.title,
                                modifier = Modifier.size(24.dp)
                            )
                        },
                        label = {
                            Text(
                                tab.title,
                                fontSize = 11.sp,
                                fontWeight = if (currentTab == tab) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = HeaderBlue,
                            selectedTextColor = HeaderBlue,
                            indicatorColor = HeaderBlue.copy(alpha = 0.15f),
                            unselectedIconColor = Color.Gray,
                            unselectedTextColor = Color.Gray
                        )
                    )
                }
            }
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (currentTab) {
                DadbanTab.CALENDAR -> CalendarScreen(db)
                DadbanTab.FINANCE -> FinanceApp(db)
                DadbanTab.SETTINGS -> SettingsScreen(db) { currentTab = DadbanTab.CALENDAR }
            }
        }
    }
}
