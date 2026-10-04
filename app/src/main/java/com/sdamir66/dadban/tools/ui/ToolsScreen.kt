package com.sdamir66.dadban.tools.ui

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sdamir66.dadban.tools.PriceCatalog
import com.sdamir66.dadban.tools.TgjuPrice
import com.sdamir66.dadban.tools.ToolsPreferences
import com.sdamir66.dadban.tools.ToolsRepository
import com.sdamir66.dadban.ui.theme.BgLight
import com.sdamir66.dadban.ui.theme.CreditGreen
import com.sdamir66.dadban.ui.theme.DebitRed
import com.sdamir66.dadban.ui.theme.HeaderBlue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "ToolsScreen"

@Composable
fun ToolsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var allPrices by remember { mutableStateOf<List<TgjuPrice>>(emptyList()) }
    var selectedKeys by remember { mutableStateOf(ToolsPreferences.getSelectedKeys(context)) }
    var isExpanded by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    suspend fun refreshPrices() {
        isLoading = true
        errorMessage = null
        val result = ToolsRepository.fetchPrices(context, PriceCatalog.ALL_ORDERED)
        isLoading = false
        result.fold(
            onSuccess = { prices ->
                allPrices = prices
                val returned = prices.map { it.key }.toSet()
                val missing = PriceCatalog.ALL_ORDERED.filter { it !in returned }
                Log.d(TAG, "returned=${prices.size}, missing=${missing.size}")
                if (missing.isNotEmpty()) {
                    Log.w(TAG, "missing keys: $missing")
                }
            },
            onFailure = { errorMessage = it.message ?: "خطا در دریافت" }
        )
    }

    LaunchedEffect(Unit) {
        val saved = ToolsPreferences.getSelectedKeys(context)
        val validKeys = saved.filter { it in PriceCatalog.ALL_ORDERED }
        if (validKeys != saved || saved.isEmpty()) {
            Log.d(TAG, "resetting preferences. old=$saved")
            ToolsPreferences.saveSelectedKeys(context, PriceCatalog.DEFAULT_SELECTED)
            selectedKeys = PriceCatalog.DEFAULT_SELECTED
        } else {
            selectedKeys = validKeys
        }

        refreshPrices()
        while (true) {
            delay(5 * 60 * 1000L)
            refreshPrices()
        }
    }

    fun addToTop(key: String) {
        if (key !in selectedKeys) {
            selectedKeys = selectedKeys + key
            ToolsPreferences.saveSelectedKeys(context, selectedKeys)
        }
    }

    fun removeFromTop(key: String) {
        selectedKeys = selectedKeys - key
        ToolsPreferences.saveSelectedKeys(context, selectedKeys)
    }

    val topList = PriceCatalog.sortForTopList(selectedKeys).mapNotNull { key ->
        allPrices.find { it.key == key }
    }

    val allList = PriceCatalog.sortForAllList(
        PriceCatalog.ALL_ORDERED.filter { it !in selectedKeys }
    ).mapNotNull { key ->
        allPrices.find { it.key == key }
    }

    Column(
        Modifier.fillMaxSize().background(BgLight)
    ) {
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
            item {
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column {
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

                        if (topList.isEmpty()) {
                            Text(
                                "هنوز چیزی انتخاب نکردی",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF5C5D72),
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        } else {
                            topList.forEach { price ->
                                key(price.key) {
                                    SwipeableTopItem(
                                        price = price,
                                        onRemove = { removeFromTop(price.key) }
                                    )
                                }
                                HorizontalDivider(color = Color(0xFFEEEEEE))
                            }
                        }

                        Box(
                            Modifier
                                .fillMaxWidth()
                                .clickable { isExpanded = !isExpanded }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                if (isExpanded) "▲ بستن لیست کامل"
                                else "▼ لیست کامل (${allList.size} آیتم)",
                                style = MaterialTheme.typography.bodySmall,
                                color = HeaderBlue,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        if (isExpanded) {
                            HorizontalDivider(color = Color(0xFFEEEEEE))
                            allList.forEach { price ->
                                key(price.key) {
                                    SwipeableAllItem(
                                        price = price,
                                        onAdd = { addToTop(price.key) }
                                    )
                                }
                                HorizontalDivider(color = Color(0xFFEEEEEE))
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableTopItem(
    price: TgjuPrice,
    onRemove: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onRemove()
                true
            } else false
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(DebitRed.copy(alpha = 0.15f))
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "حذف از تاپ",
                        tint = DebitRed,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "حذف از تاپ",
                        color = DebitRed,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }
    ) {
        PriceItem(price)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableAllItem(
    price: TgjuPrice,
    onAdd: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onAdd()
                true
            } else false
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(CreditGreen.copy(alpha = 0.15f))
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "افزودن به تاپ",
                        tint = CreditGreen,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "افزودن به تاپ",
                        color = CreditGreen,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }
    ) {
        PriceItem(price)
    }
}
