package com.sdamir66.dadban.tools.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sdamir66.dadban.tools.PriceCategory
import com.sdamir66.dadban.tools.TgjuPrice
import com.sdamir66.dadban.tools.ToolsPreferences
import com.sdamir66.dadban.tools.ToolsRepository
import com.sdamir66.dadban.ui.theme.BgLight
import com.sdamir66.dadban.ui.theme.HeaderBlue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ToolsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // ═══ State ═══
    var allPrices by remember { mutableStateOf<List<TgjuPrice>>(emptyList()) }
    var selectedKeys by remember {
        mutableStateOf(ToolsPreferences.getSelectedKeys(context))
    }
    var isExpanded by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // ═══ تابع دریافت قیمت‌ها ═══
    suspend fun refreshPrices() {
        isLoading = true
        errorMessage = null
        val result = ToolsRepository.fetchPrices(PriceCategory.ALL_KEYS)
        isLoading = false
        result.fold(
            onSuccess = { allPrices = it },
            onFailure = { errorMessage = it.message ?: "خطا در دریافت" }
        )
    }

    // ═══ دریافت اولیه + هر ۵ دقیقه ═══
    LaunchedEffect(Unit) {
        refreshPrices()
        while (true) {
            delay(5 * 60 * 1000L)  // ۵ دقیقه
            refreshPrices()
        }
    }

    // ═══ ذخیره‌ی انتخاب‌شده‌ها ═══
    fun toggleKey(key: String) {
        val newList = if (key in selectedKeys) {
            selectedKeys - key
        } else {
            selectedKeys + key
        }
        selectedKeys = newList
        ToolsPreferences.saveSelectedKeys(context, newList)
    }

    // ═══ تاپ‌لیست ═══
    val topList = selectedKeys.mapNotNull { key ->
        allPrices.find { it.key == key }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(BgLight)
    ) {
        // ═══ هدر ═══
        Box(
            Modifier
                .fillMaxWidth()
                .background(
                    color = HeaderBlue,
                    shape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)
                )
                .statusBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp)
        ) {
            Column {
                Text(
                    "ابزارها",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "قیمت‌های لحظه‌ای",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.85f)
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // ═══ کارت اصلی ═══
            item {
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column {
                        // ─── هدر کارت ───
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "💰 قیمت‌های لحظه‌ای",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1B1B1F),
                                modifier = Modifier.weight(1f)
                            )

                            // دکمه‌ی به‌روزرسانی
                            IconButton(
                                onClick = { scope.launch { refreshPrices() } },
                                enabled = !isLoading,
                                modifier = Modifier.size(36.dp)
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        color = HeaderBlue,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Text("↻", fontSize = 20.sp, color = HeaderBlue)
                                }
                            }
                        }

                        // ─── خطا ───
                        errorMessage?.let { err ->
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFFFEBEE))
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    "❌ $err",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }

                        // ─── تاپ‌لیست ───
                        if (topList.isEmpty() && allPrices.isNotEmpty()) {
                            Text(
                                "هنوز چیزی انتخاب نکردی",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF5C5D72),
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        } else {
                            topList.forEach { price ->
                                PriceItem(
                                    price = price,
                                    isSelected = true,
                                    onToggle = { toggleKey(price.key) }
                                )
                            }
                        }

                        // ─── دکمه‌ی باز/بسته ───
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .clickable { isExpanded = !isExpanded }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    if (isExpanded) "▲ بستن لیست کامل"
                                    else "▼ لیست کامل (${allPrices.size} آیتم)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = HeaderBlue,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        // ─── لیست کامل ───
                        if (isExpanded) {
                            HorizontalDivider(color = Color(0xFFEEEEEE))

                            allPrices.forEach { price ->
                                PriceItem(
                                    price = price,
                                    isSelected = price.key in selectedKeys,
                                    onToggle = { toggleKey(price.key) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
