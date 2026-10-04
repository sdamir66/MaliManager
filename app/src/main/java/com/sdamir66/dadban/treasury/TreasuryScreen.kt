package com.sdamir66.dadban.treasury

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sdamir66.dadban.AccountEditor
import com.sdamir66.dadban.AccountScreen
import com.sdamir66.dadban.SwipeableAccountCard
import com.sdamir66.dadban.data.Account
import com.sdamir66.dadban.data.AppDb
import com.sdamir66.dadban.data.Person
import com.sdamir66.dadban.tools.PriceCatalog
import com.sdamir66.dadban.tools.TgjuPrice
import com.sdamir66.dadban.tools.ToolsRepository
import com.sdamir66.dadban.ui.ConfirmDeleteDialog
import com.sdamir66.dadban.ui.theme.BgLight
import com.sdamir66.dadban.ui.theme.DebitRed
import com.sdamir66.dadban.ui.theme.HeaderBlue
import com.sdamir66.dadban.util.Jalali
import com.sdamir66.dadban.util.balanceDisplay
import com.sdamir66.dadban.util.computeBalance
import com.sdamir66.dadban.util.money
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun TreasuryScreen(db: AppDb, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var treasuryPerson by remember { mutableStateOf<Person?>(null) }
    var accounts by remember { mutableStateOf<List<Account>>(emptyList()) }
    var balances by remember { mutableStateOf<Map<Long, Double>>(emptyMap()) }
    var livePrices by remember { mutableStateOf<List<TgjuPrice>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var lastUpdate by remember { mutableStateOf(TreasuryPreferences.getLastUpdate(context)) }

    var add by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<Account?>(null) }
    var deleteTarget by remember { mutableStateOf<Account?>(null) }
    var openAccount by remember { mutableStateOf<Account?>(null) }

    // ═══ BackHandler: اگه توی AccountScreen بودیم، برگرد به TreasuryScreen ═══
    BackHandler(enabled = true) {
        if (openAccount != null) {
            openAccount = null
        } else {
            onBack()
        }
    }

    // ═══ پیدا کردن/ساختن شخص مخفی ═══
    LaunchedEffect(Unit) {
        var p = db.persons().allNow().find { it.isTreasury }
        if (p == null) {
            val id = db.persons().insert(
                Person(
                    name = "دارایی‌های من",
                    note = "",
                    displayOrder = -1,
                    displayedCurrencies = "",
                    isTreasury = true
                )
            )
            p = db.persons().byId(id)
        }
        treasuryPerson = p
    }

    suspend fun loadAccounts() {
        val p = treasuryPerson ?: return
        val accs = db.accounts().byPersonNow(p.id)
        accounts = accs
        val newBalances = mutableMapOf<Long, Double>()
        for (acc in accs) {
            val txs = db.tx().byAccountNow(acc.id)
            val creditSum = txs.filter { it.type == "بستانکار" }.sumOf { it.amount }
            val debitSum = txs.filter { it.type == "بدهکار" }.sumOf { it.amount }
            newBalances[acc.id] = computeBalance(creditSum, debitSum, acc.nature)
        }
        balances = newBalances
    }

    suspend fun refreshPrices() {
        isLoading = true
        try {
            val result = ToolsRepository.fetchPrices(PriceCatalog.ALL_ORDERED)
            result.fold(
                onSuccess = { prices ->
                    livePrices = prices
                    val now = System.currentTimeMillis()
                    TreasuryPreferences.setLastUpdate(context, now)
                    lastUpdate = now
                },
                onFailure = { /* از cache استفاده کن */ }
            )
        } catch (e: Exception) {
            // fallback به cache (توی مرحله ۶ اضافه می‌شه)
        }
        isLoading = false
    }

    LaunchedEffect(treasuryPerson) {
        if (treasuryPerson != null) {
            loadAccounts()
            refreshPrices()
            while (true) {
                delay(5 * 60 * 1000L)
                loadAccounts()
                refreshPrices()
            }
        }
    }

    // ═══ اگه AccountScreen بازه، همون رو نشون بده ═══
    if (openAccount != null) {
        AccountScreen(db, openAccount!!) {
            openAccount = null
            scope.launch { loadAccounts() }
        }
        return
    }

    // ═══ محاسبه‌ی جمع کل معادل ریالی ═══
    val totalEquivalent: Double = accounts.sumOf { acc ->
        val bal = balances[acc.id] ?: 0.0
        TreasuryCalculator.calculateEquivalent(acc, bal, livePrices) ?: 0.0
    }

    val isStale = lastUpdate == 0L || (System.currentTimeMillis() - lastUpdate) > 30 * 60 * 1000L

    Box(Modifier.fillMaxSize().background(BgLight)) {
        Column(Modifier.fillMaxSize()) {

            // هدر
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
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack, modifier = Modifier.size(44.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "بازگشت", tint = Color.White, modifier = Modifier.size(24.dp))
                    }
                    Spacer(Modifier.width(4.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "دارایی‌های من",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "${accounts.size} دارایی",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                    if (!isLoading) {
                        Box(
                            Modifier
                                .size(44.dp)
                                .background(Color.White, shape = CircleShape)
                                .clickable { add = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("+", color = HeaderBlue, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // کارت هدر
            Card(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(
                        "جمع کل معادل ریالی",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF5C5D72)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${money(totalEquivalent)} ریال",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = HeaderBlue
                    )

                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider(color = Color(0xFFEEEEEE))
                    Spacer(Modifier.height(10.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isStale) {
                            Text("⚠️", fontSize = 14.sp)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (lastUpdate == 0L) "هنوز به‌روزرسانی نشده"
                                else "آخرین: ${formatDateTime(lastUpdate)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = DebitRed,
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            Text("✅", fontSize = 14.sp)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "آخرین: ${formatDateTime(lastUpdate)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF5C5D72),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        IconButton(
                            onClick = {
                                scope.launch {
                                    loadAccounts()
                                    refreshPrices()
                                }
                            },
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
                }
            }

            Spacer(Modifier.height(16.dp))

            // لیست دارایی‌ها
            if (accounts.isEmpty()) {
                Box(
                    Modifier.fillMaxSize().padding(top = 40.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🏦", fontSize = 64.sp)
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "هنوز دارایی‌ای ثبت نکردی",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.Gray
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "با دکمه + شروع کن",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Text(
                            "دارایی‌ها",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1B1B1F),
                            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                        )
                    }

                    items(accounts.size, key = { accounts[it].id }) { index ->
                        val acc = accounts[index]
                        val bal = balances[acc.id] ?: 0.0
                        val equiv = TreasuryCalculator.calculateEquivalent(acc, bal, livePrices)

                        SwipeableAccountCard(
                            account = acc,
                            balance = bal,
                            onClick = { openAccount = acc },
                            onEdit = { editTarget = acc },
                            onDelete = { deleteTarget = acc },
                            equivalentRial = equiv
                        )
                    }
                }
            }
        }
    }

    // ═══ افزودن دارایی (nature = debit) ═══
    if (add && treasuryPerson != null) {
        AccountEditor(
            old = null,
            personId = treasuryPerson!!.id,
            onSave = {
                scope.launch {
                    val order = (accounts.maxOfOrNull { it.displayOrder } ?: 0) + 1
                    db.accounts().insert(it.copy(displayOrder = order))
                    add = false
                    loadAccounts()
                }
            },
            onCancel = { add = false },
            defaultNature = "debit"   // ← مهم
        )
    }

    // ═══ ویرایش دارایی ═══
    editTarget?.let { target ->
        AccountEditor(
            old = target,
            personId = target.personId,
            onSave = {
                scope.launch {
                    db.accounts().update(it)
                    editTarget = null
                    loadAccounts()
                }
            },
            onCancel = { editTarget = null }
        )
    }

    // ═══ حذف دارایی ═══
    deleteTarget?.let { target ->
        ConfirmDeleteDialog(
            title = "حذف دارایی",
            message = "آیا «${target.name}» و تراکنش‌هایش حذف شوند؟",
            onConfirm = {
                scope.launch {
                    db.tx().byAccountNow(target.id).forEach { db.tx().delete(it) }
                    db.profitPeriod().deleteByAccount(target.id)
                    db.accounts().delete(target)
                    deleteTarget = null
                    loadAccounts()
                }
            },
            onCancel = { deleteTarget = null }
        )
    }
}

private fun formatDateTime(millis: Long): String {
    return try {
        val j = Jalali.toJalaliPublic(millis)
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = millis }
        "%04d/%02d/%02d %02d:%02d".format(
            Locale.US,
            j[0], j[1], j[2],
            cal.get(java.util.Calendar.HOUR_OF_DAY),
            cal.get(java.util.Calendar.MINUTE)
        )
    } catch (e: Exception) {
        ""
    }
}
