@file:OptIn(ExperimentalMaterial3Api::class)

package com.sdamir66.dadban

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sdamir66.dadban.calendar.data.CalendarSettings
import com.sdamir66.dadban.calendar.data.HijriRepository
import com.sdamir66.dadban.data.*
import com.sdamir66.dadban.tools.PriceCatalog
import com.sdamir66.dadban.tools.TgjuPrice
import com.sdamir66.dadban.tools.ToolsRepository
import com.sdamir66.dadban.treasury.PasswordDialog
import com.sdamir66.dadban.treasury.TreasuryCalculator
import com.sdamir66.dadban.treasury.TreasuryPreferences
import com.sdamir66.dadban.treasury.TreasuryScreen
import com.sdamir66.dadban.ui.*
import com.sdamir66.dadban.ui.theme.BgLight
import com.sdamir66.dadban.ui.theme.CreditGreen
import com.sdamir66.dadban.ui.theme.DebitRed
import com.sdamir66.dadban.ui.theme.HeaderBlue
import com.sdamir66.dadban.ui.theme.MaliManagerTheme
import com.sdamir66.dadban.util.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

// ═══════════════════════════════════════════════════════
// MainActivity
// ═══════════════════════════════════════════════════════

class MainActivity : ComponentActivity() {
    private lateinit var db: AppDb
    private val backupScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        db = AppDb.build(applicationContext)

        window.statusBarColor = android.graphics.Color.parseColor("#4C5FD7")
        window.navigationBarColor = android.graphics.Color.parseColor("#1E1F25")

        // راه‌اندازی تقویم قمری + تنظیمات پیش‌فرض
        backupScope.launch {
            delay(500L)

            HijriRepository.init(applicationContext, db)

            val current = db.calendarSettingsDao().getNow()
            if (current == null) {
                db.calendarSettingsDao().insert(CalendarSettings())
            } else {
                if (!current.showReligiousNonHoliday &&
                    !current.showNationalNonHoliday &&
                    !current.showGlobalEvents) {
                    db.calendarSettingsDao().insert(
                        current.copy(
                            showReligiousNonHoliday = true,
                            showNationalNonHoliday = true,
                            showGlobalEvents = true
                        )
                    )
                }
            }
        }

        // بکاپ خودکار
        backupScope.launch {
            while (true) {
                delay(5_000L)
                autoBackupToInternal(applicationContext, db)
            }
        }

        setContent {
            MaliManagerTheme { DadbanApp(db) }
        }
    }

    override fun onStop() {
        super.onStop()
        backupScope.launch { autoBackupToInternal(applicationContext, db) }
    }

    override fun onDestroy() {
        backupScope.launch { autoBackupToInternal(applicationContext, db) }
        super.onDestroy()
    }
}

// ═══════════════════════════════════════════════════════
// توابع کمکی مشترک (فقط این‌هایی که هنوز توی MainActivity لازمه)
// ═══════════════════════════════════════════════════════

fun calculateRunningBalances(transactions: List<Transaction>): Map<Long, Double> {
    val sorted = transactions.sortedBy { it.dateMillis }
    val balances = mutableMapOf<Long, Double>()
    var running = 0.0
    for (t in sorted) {
        running += if (t.type == "بستانکار") t.amount else -t.amount
        balances[t.id] = running
    }
    return balances
}

fun Account.displayUnit(): String = if (customUnit.isNotBlank()) customUnit else currency

private fun toMillis(y: Int, m: Int, d: Int): Long =
    Jalali.parse("%04d/%02d/%02d".format(Locale.US, y, m, d)) ?: 0L

// ═══════════════════════════════════════════════════════
// FinanceApp
// ═══════════════════════════════════════════════════════

@Composable
fun FinanceApp(db: AppDb) {
    val context = LocalContext.current
    var person by remember { mutableStateOf<Person?>(null) }
    var account by remember { mutableStateOf<Account?>(null) }
    var treasury by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var noPasswordMessage by remember { mutableStateOf(false) }

    BackHandler(enabled = account != null || person != null) {
        when {
            account != null -> account = null
            person != null -> person = null
        }
    }

    CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Rtl) {
        when {
            treasury -> TreasuryScreen(db) { treasury = false }
            account != null -> AccountScreen(db, account!!) { account = null }
            person != null -> PersonScreen(
                db = db,
                person = person!!,
                onBack = { person = null },
                onOpenAccount = { account = it }
            )
            else -> PersonsScreen(
                db = db,
                onOpenPerson = { person = it },
                onOpenTreasury = {
                    if (TreasuryPreferences.hasPassword(context)) {
                        showPasswordDialog = true
                    } else {
                        noPasswordMessage = true
                    }
                }
            )
        }
    }

    // ═══ دیالوگ رمز ═══
    if (showPasswordDialog) {
        val pwd = TreasuryPreferences.getPassword(context)
        if (pwd != null) {
            PasswordDialog(
                correctPassword = pwd,
                onDismiss = { showPasswordDialog = false },
                onCorrect = {
                    showPasswordDialog = false
                    treasury = true
                }
            )
        } else {
            showPasswordDialog = false
        }
    }

    // ═══ پیام «رمز تنظیم نشده» ═══
    if (noPasswordMessage) {
        AlertDialog(
            onDismissRequest = { noPasswordMessage = false },
            containerColor = Color.White,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.border(2.dp, HeaderBlue, RoundedCornerShape(20.dp)),
            title = {
                Text("رمز تنظیم نشده",
                    fontWeight = FontWeight.Bold, color = HeaderBlue)
            },
            text = {
                Text("برای ورود به «دارایی‌های من»، اول از بخش تنظیمات یه رمز تعیین کن.",
                    color = Color(0xFF1B1B1F))
            },
            confirmButton = {
                Button(
                    onClick = { noPasswordMessage = false },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = HeaderBlue,
                        contentColor = Color.White
                    )
                ) { Text("باشه") }
            }
        )
    }
}

// ═══════════════════════════════════════════════════════
// PersonsScreen
// ═══════════════════════════════════════════════════════

@Composable
fun PersonsScreen(
    db: AppDb,
    onOpenPerson: (Person) -> Unit,
    onOpenTreasury: () -> Unit
) {
    val personsAll by db.persons().all().collectAsState(emptyList())
    val persons = personsAll.filter { !it.isTreasury }
    val allAccounts by db.accounts().all().collectAsState(emptyList())
    var add by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<Person?>(null) }
    var deleteTarget by remember { mutableStateOf<Person?>(null) }
    var editMode by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().background(BgLight)) {
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

                // ═══ آیکون خزانه (قبل از عنوان) ═══
                if (!editMode) {
                    Box(
                        Modifier
                            .size(44.dp)
                            .background(Color.White, shape = CircleShape)
                            .clickable { onOpenTreasury() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("🏦", fontSize = 22.sp)
                    }
                    Spacer(Modifier.width(10.dp))
                }

                Column(Modifier.weight(1f)) {
                    Text(
                        if (editMode) "مرتب‌سازی" else "مدیریت مالی",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        if (editMode) "با ▲▼ جابه‌جا کن"
                        else "${persons.size} شخص  •  ${allAccounts.size} حساب",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }

                IconButton(onClick = { editMode = !editMode }, modifier = Modifier.size(44.dp)) {
                    Text(if (editMode) "✓" else "⇅", color = Color.White, fontSize = 22.sp)
                }

                if (!editMode) {
                    Spacer(Modifier.width(6.dp))
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

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (persons.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("👥", fontSize = 64.sp)
                            Spacer(Modifier.height(16.dp))
                            Text("هنوز شخصی ثبت نکردی", style = MaterialTheme.typography.titleLarge, color = Color.Gray)
                        }
                    }
                }
            }

            items(persons.size, key = { persons[it].id }) { index ->
                val p = persons[index]
                val personAccounts = allAccounts.filter { it.personId == p.id }

                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (editMode) {
                        Column(Modifier.padding(end = 4.dp)) {
                            IconButton(
                                onClick = {
                                    if (index > 0) {
                                        scope.launch {
                                            val p1 = persons[index]
                                            val p2 = persons[index - 1]
                                            db.persons().updateOrder(p1.id, p2.displayOrder)
                                            db.persons().updateOrder(p2.id, p1.displayOrder)
                                        }
                                    }
                                },
                                enabled = index > 0,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Text("▲", fontSize = 14.sp, color = if (index > 0) HeaderBlue else Color.LightGray)
                            }
                            IconButton(
                                onClick = {
                                    if (index < persons.size - 1) {
                                        scope.launch {
                                            val p1 = persons[index]
                                            val p2 = persons[index + 1]
                                            db.persons().updateOrder(p1.id, p2.displayOrder)
                                            db.persons().updateOrder(p2.id, p1.displayOrder)
                                        }
                                    }
                                },
                                enabled = index < persons.size - 1,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Text("▼", fontSize = 14.sp, color = if (index < persons.size - 1) HeaderBlue else Color.LightGray)
                            }
                        }
                    }

                    Box(Modifier.weight(1f)) {
                        if (editMode) {
                            PersonCard(p, personAccounts, { }, { }, { }, db)
                        } else {
                            PersonCard(p, personAccounts, { onOpenPerson(p) }, { editTarget = p }, { deleteTarget = p }, db)
                        }
                    }
                }
            }
        }
    }

    if (add) {
        PersonEditor(null, db, {
            scope.launch {
                val order = (persons.maxOfOrNull { it.displayOrder } ?: 0) + 1
                db.persons().insert(it.copy(displayOrder = order))
                add = false
            }
        }, { add = false })
    }

    editTarget?.let { target ->
        PersonEditor(target, db, {
            scope.launch { db.persons().update(it); editTarget = null }
        }, { editTarget = null })
    }

    deleteTarget?.let { target ->
        ConfirmDeleteDialog(
            title = "حذف شخص",
            message = "آیا «${target.name}» و همه‌ی حساب‌هایش حذف شوند؟",
            onConfirm = {
                scope.launch {
                    val accs = db.accounts().byPersonNow(target.id)
                    accs.forEach { acc ->
                        db.tx().byAccountNow(acc.id).forEach { db.tx().delete(it) }
                        db.profitPeriod().deleteByAccount(acc.id)
                        db.accounts().delete(acc)
                    }
                    db.persons().delete(target)
                    deleteTarget = null
                }
            },
            onCancel = { deleteTarget = null }
        )
    }
}

// ═══════════════════════════════════════════════════════
// PersonCard
// ═══════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonCard(
    person: Person,
    accounts: List<Account>,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    db: AppDb
) {
    val accountsWithBalance = accounts.map { acc ->
        val txs = remember(acc.id) { mutableStateOf<List<Transaction>>(emptyList()) }
        LaunchedEffect(acc.id) { txs.value = db.tx().byAccountNow(acc.id) }
        val creditSum = txs.value.filter { it.type == "بستانکار" }.sumOf { it.amount }
        val debitSum = txs.value.filter { it.type == "بدهکار" }.sumOf { it.amount }
        val bal = computeBalance(creditSum, debitSum, acc.nature)
        acc to bal
    }

    val selectedAccounts = remember(person) {
        person.displayedCurrencies.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }

    val filteredAccounts = if (selectedAccounts.isEmpty()) accountsWithBalance
    else accountsWithBalance.filter { (acc, _) -> acc.name in selectedAccounts }

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> { onEdit(); false }
                SwipeToDismissBoxValue.EndToStart -> { onDelete(); false }
                else -> false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            val value = dismissState.targetValue
            val (color, icon, alignment) = when (value) {
                SwipeToDismissBoxValue.StartToEnd -> Triple(CreditGreen, Icons.Default.Edit, Alignment.CenterStart)
                SwipeToDismissBoxValue.EndToStart -> Triple(DebitRed, Icons.Default.Delete, Alignment.CenterEnd)
                else -> Triple(Color.Transparent, Icons.Default.Edit, Alignment.Center)
            }
            Box(Modifier.fillMaxSize().padding(horizontal = 20.dp), contentAlignment = alignment) {
                if (color != Color.Transparent) {
                    Box(modifier = Modifier.size(44.dp).background(color, shape = CircleShape), contentAlignment = Alignment.Center) {
                        Icon(icon, null, tint = Color.White, modifier = Modifier.size(24.dp))
                    }
                }
            }
        }
    ) {
        Card(
            Modifier.fillMaxWidth().clickable { onClick() },
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
        ) {
            Column(Modifier.fillMaxWidth().padding(14.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(person.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color(0xFF1B1B1F))
                    Text("${accounts.size} حساب", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF5C5D72))
                }

                Spacer(Modifier.height(6.dp))

                if (filteredAccounts.isEmpty()) {
                    Text("حسابی برای نمایش نیست", style = MaterialTheme.typography.bodySmall, color = Color(0xFF5C5D72))
                } else {
                    filteredAccounts.forEach { (acc, bal) ->
                        val display = balanceDisplay(bal)
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(acc.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = Color(0xFF1B1B1F), modifier = Modifier.weight(1f))
                            Text(
                                display.text,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = display.color
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(acc.displayUnit(), style = MaterialTheme.typography.labelSmall, color = Color(0xFF5C5D72))
                            if (display.label.isNotEmpty()) {
                                Spacer(Modifier.width(6.dp))
                                Text(display.label, style = MaterialTheme.typography.labelSmall, color = display.color, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════
// PersonScreen
// ═══════════════════════════════════════════════════════

@Composable
fun PersonScreen(
    db: AppDb,
    person: Person,
    onBack: () -> Unit,
    onOpenAccount: (Account) -> Unit
) {
    val accounts by db.accounts().byPerson(person.id).collectAsState(emptyList())
    var add by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<Account?>(null) }
    var deleteTarget by remember { mutableStateOf<Account?>(null) }
    var editMode by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val accountBalances = accounts.map { acc ->
        val txs = remember(acc.id) { mutableStateOf<List<Transaction>>(emptyList()) }
        LaunchedEffect(acc.id) { txs.value = db.tx().byAccountNow(acc.id) }
        val creditSum = txs.value.filter { it.type == "بستانکار" }.sumOf { it.amount }
        val debitSum = txs.value.filter { it.type == "بدهکار" }.sumOf { it.amount }
        val bal = computeBalance(creditSum, debitSum, acc.nature)
        acc to bal
    }

    Box(Modifier.fillMaxSize().background(BgLight)) {
        Column(Modifier.fillMaxSize()) {
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
                            if (editMode) "مرتب‌سازی" else person.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            if (editMode) "با ▲▼ جابه‌جا کن" else "${accounts.size} حساب",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }

                    IconButton(onClick = { editMode = !editMode }, modifier = Modifier.size(44.dp)) {
                        Text(if (editMode) "✓" else "⇅", color = Color.White, fontSize = 22.sp)
                    }

                    if (!editMode) {
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

            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text("حساب‌ها", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF1B1B1F), modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
                }

                items(accounts.size, key = { accounts[it].id }) { index ->
                    val acc = accounts[index]
                    val bal = accountBalances.find { it.first.id == acc.id }?.second ?: 0.0

                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        if (editMode) {
                            Column(Modifier.padding(end = 4.dp)) {
                                IconButton(
                                    onClick = {
                                        if (index > 0) {
                                            scope.launch {
                                                val a1 = accounts[index]
                                                val a2 = accounts[index - 1]
                                                db.accounts().updateOrder(a1.id, a2.displayOrder)
                                                db.accounts().updateOrder(a2.id, a1.displayOrder)
                                            }
                                        }
                                    },
                                    enabled = index > 0,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Text("▲", fontSize = 14.sp, color = if (index > 0) HeaderBlue else Color.LightGray)
                                }
                                IconButton(
                                    onClick = {
                                        if (index < accounts.size - 1) {
                                            scope.launch {
                                                val a1 = accounts[index]
                                                val a2 = accounts[index + 1]
                                                db.accounts().updateOrder(a1.id, a2.displayOrder)
                                                db.accounts().updateOrder(a2.id, a1.displayOrder)
                                            }
                                        }
                                    },
                                    enabled = index < accounts.size - 1,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Text("▼", fontSize = 14.sp, color = if (index < accounts.size - 1) HeaderBlue else Color.LightGray)
                                }
                            }
                        }

                        Box(Modifier.weight(1f)) {
                            if (editMode) {
                                SwipeableAccountCard(acc, bal, { }, { }, { })
                            } else {
                                SwipeableAccountCard(acc, bal, { onOpenAccount(acc) }, { editTarget = acc }, { deleteTarget = acc })
                            }
                        }
                    }
                }
            }
        }
    }

    if (add) {
        AccountEditor(null, person.id, {
            scope.launch {
                val order = (accounts.maxOfOrNull { it.displayOrder } ?: 0) + 1
                db.accounts().insert(it.copy(displayOrder = order))
                add = false
            }
        }, { add = false })
    }

    editTarget?.let { target ->
        AccountEditor(target, person.id, {
            scope.launch { db.accounts().update(it); editTarget = null }
        }, { editTarget = null })
    }

    deleteTarget?.let { target ->
        ConfirmDeleteDialog(
            title = "حذف حساب",
            message = "آیا «${target.name}» و تراکنش‌هایش حذف شوند؟",
            onConfirm = {
                scope.launch {
                    db.tx().byAccountNow(target.id).forEach { db.tx().delete(it) }
                    db.profitPeriod().deleteByAccount(target.id)
                    db.accounts().delete(target)
                    deleteTarget = null
                }
            },
            onCancel = { deleteTarget = null }
        )
    }
}

// ═══════════════════════════════════════════════════════
// SwipeableAccountCard
// ═══════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeableAccountCard(
    account: Account,
    balance: Double,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    equivalentRial: Double? = null
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> { onEdit(); false }
                SwipeToDismissBoxValue.EndToStart -> { onDelete(); false }
                else -> false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            val value = dismissState.targetValue
            val (color, icon, alignment) = when (value) {
                SwipeToDismissBoxValue.StartToEnd -> Triple(CreditGreen, Icons.Default.Edit, Alignment.CenterStart)
                SwipeToDismissBoxValue.EndToStart -> Triple(DebitRed, Icons.Default.Delete, Alignment.CenterEnd)
                else -> Triple(Color.Transparent, Icons.Default.Edit, Alignment.Center)
            }
            Box(Modifier.fillMaxSize().padding(horizontal = 20.dp), contentAlignment = alignment) {
                if (color != Color.Transparent) {
                    Box(modifier = Modifier.size(44.dp).background(color, shape = CircleShape), contentAlignment = Alignment.Center) {
                        Icon(icon, null, tint = Color.White, modifier = Modifier.size(24.dp))
                    }
                }
            }
        }
    ) {
        val display = balanceDisplay(balance)
        Card(
            Modifier.fillMaxWidth().clickable { onClick() },
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
        ) {
            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(46.dp)
                        .background(color = display.color, shape = CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(account.displayUnit(), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(account.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = Color(0xFF1B1B1F))
                }
                Spacer(Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        display.text,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge,
                        color = display.color
                    )
                    if (display.label.isNotEmpty()) {
                        Spacer(Modifier.height(2.dp))
                        Text(display.label, style = MaterialTheme.typography.labelSmall, color = display.color)
                    }

                    if (equivalentRial != null) {
                        Spacer(Modifier.height(3.dp))
                        Text(
                            "معادل: ${money(equivalentRial)} ریال",
                            style = MaterialTheme.typography.labelSmall,
                            color = HeaderBlue,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════
// AccountScreen
// ═══════════════════════════════════════════════════════

@Composable
fun AccountScreen(db: AppDb, a: Account, onBack: () -> Unit) {
    val tx by db.tx().byAccount(a.id).collectAsState(emptyList())
    var editor by remember { mutableStateOf<Transaction?>(null) }
    var add by remember { mutableStateOf(false) }
    var profit by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<Transaction?>(null) }
    var livePrices by remember { mutableStateOf<List<TgjuPrice>>(emptyList()) }
    val scope = rememberCoroutineScope()
    val creditSum = tx.filter { it.type == "بستانکار" }.sumOf { it.amount }
    val debitSum = tx.filter { it.type == "بدهکار" }.sumOf { it.amount }
    val bal = computeBalance(creditSum, debitSum, a.nature)
    val display = balanceDisplay(bal)

    // لود قیمت‌های لحظه‌ای (اگه rateEnabled بود)
    LaunchedEffect(a.id) {
        if (a.rateEnabled) {
            try {
                val result = ToolsRepository.fetchPrices(PriceCatalog.ALL_ORDERED)
                result.fold(
                    onSuccess = { livePrices = it },
                    onFailure = { }
                )
            } catch (e: Exception) { }
        }
    }

    val equivalentRial = remember(a, bal, livePrices) {
        TreasuryCalculator.calculateEquivalent(a, bal, livePrices)
    }

    Box(Modifier.fillMaxSize().background(BgLight)) {
        Column(Modifier.fillMaxSize()) {
            PageHeader(
                title = a.name,
                subtitle = "",
                onBackClick = onBack,
                extraActions = {
                    IconButton(onClick = { profit = true }, modifier = Modifier.size(44.dp)) {
                        Icon(Icons.Default.Settings, "تنظیم سود", tint = Color.White, modifier = Modifier.size(24.dp))
                    }
                }
            )

            Card(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .offset(y = (-30).dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            display.text,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = display.color
                        )
                        Spacer(Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (display.label.isNotEmpty()) {
                                Text(
                                    display.label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = display.color,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(Modifier.width(4.dp))
                            }
                            Text(a.displayUnit(), style = MaterialTheme.typography.bodyMedium, color = Color(0xFF5C5D72))
                        }

                        // معادل ریالی
                        if (equivalentRial != null) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "معادل: ${money(equivalentRial)} ریال",
                                style = MaterialTheme.typography.bodySmall,
                                color = HeaderBlue,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Box(
                        Modifier.size(52.dp).background(HeaderBlue, shape = CircleShape).clickable { add = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("+", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Light)
                    }
                }
            }

            val runningBalances = remember(tx) { calculateRunningBalances(tx) }

            LazyColumn(
                modifier = Modifier.fillMaxSize().offset(y = (-20).dp).padding(horizontal = 16.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text("تراکنش‌ها", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF1B1B1F), modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
                }
                items(tx, key = { it.id }) { t ->
                    val running = runningBalances[t.id] ?: 0.0
                    if (!t.isAutoProfit) {
                        SwipeableTransactionCard(t, a.displayUnit(), running, { editor = t }, { deleteTarget = t })
                    } else {
                        AutoTransactionCard(t, a.displayUnit(), running)
                    }
                }
            }
        }
    }

    if (add) TxEditor(null, a.id, {
        scope.launch { db.tx().insert(it); ProfitEngine.recalculateAll(db); add = false }
    }, { add = false })
    editor?.let {
        TxEditor(it, a.id, {
            scope.launch { db.tx().update(it); ProfitEngine.recalculateAll(db); editor = null }
        }, { editor = null })
    }
    if (profit) ProfitPeriodsScreen(db, a.id, a.name) { profit = false }

    deleteTarget?.let { target ->
        ConfirmDeleteDialog(
            title = "حذف تراکنش",
            message = "آیا از حذف این تراکنش مطمئن هستید؟",
            onConfirm = {
                scope.launch {
                    db.tx().delete(target)
                    ProfitEngine.recalculateAll(db)
                    deleteTarget = null
                }
            },
            onCancel = { deleteTarget = null }
        )
    }
}

// ═══════════════════════════════════════════════════════
// SwipeableTransactionCard
// ═══════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeableTransactionCard(
    transaction: Transaction,
    currency: String,
    runningBalance: Double,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> { onEdit(); false }
                SwipeToDismissBoxValue.EndToStart -> { onDelete(); false }
                else -> false
            }
        }
    )
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            val value = dismissState.targetValue
            val (color, icon, alignment) = when (value) {
                SwipeToDismissBoxValue.StartToEnd -> Triple(CreditGreen, Icons.Default.Edit, Alignment.CenterStart)
                SwipeToDismissBoxValue.EndToStart -> Triple(DebitRed, Icons.Default.Delete, Alignment.CenterEnd)
                else -> Triple(Color.Transparent, Icons.Default.Edit, Alignment.Center)
            }
            Box(Modifier.fillMaxSize().padding(horizontal = 20.dp), contentAlignment = alignment) {
                if (color != Color.Transparent) {
                    Box(modifier = Modifier.size(44.dp).background(color, shape = CircleShape), contentAlignment = Alignment.Center) {
                        Icon(icon, null, tint = Color.White, modifier = Modifier.size(24.dp))
                    }
                }
            }
        }
    ) {
        val isTxCredit = transaction.type == "بستانکار"
        val runningDisplay = balanceDisplay(runningBalance)
        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(46.dp).background(color = if (isTxCredit) CreditGreen else DebitRed, shape = CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(if (isTxCredit) "↓" else "↑", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(transaction.type, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = if (isTxCredit) CreditGreen else DebitRed)
                    Spacer(Modifier.height(2.dp))
                    Text(Jalali.format(transaction.dateMillis), style = MaterialTheme.typography.bodySmall, color = Color(0xFF5C5D72))
                    if (transaction.note.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(transaction.note, style = MaterialTheme.typography.bodySmall, color = Color(0xFF45464F))
                    }
                }
                Spacer(Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "${if (isTxCredit) "+" else "−"}${money(transaction.amount)}",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (isTxCredit) CreditGreen else DebitRed
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "مانده: ${runningDisplay.text}",
                        style = MaterialTheme.typography.labelSmall,
                        color = runningDisplay.color,
                        fontWeight = if (runningDisplay.label.isEmpty()) FontWeight.Normal else FontWeight.Bold
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════
// AutoTransactionCard
// ═══════════════════════════════════════════════════════

@Composable
fun AutoTransactionCard(transaction: Transaction, currency: String, runningBalance: Double) {
    val runningDisplay = balanceDisplay(runningBalance)
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(46.dp).background(CreditGreen, shape = CircleShape), contentAlignment = Alignment.Center) {
                Text("↓", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(transaction.type, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = CreditGreen)
                    Spacer(Modifier.width(6.dp))
                    Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = RoundedCornerShape(6.dp)) {
                        Text("خودکار", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(Jalali.format(transaction.dateMillis), style = MaterialTheme.typography.bodySmall, color = Color(0xFF5C5D72))
                if (transaction.note.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(transaction.note, style = MaterialTheme.typography.bodySmall, color = Color(0xFF45464F))
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("+${money(transaction.amount)}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = CreditGreen)
                Spacer(Modifier.height(2.dp))
                Text(
                    "مانده: ${runningDisplay.text}",
                    style = MaterialTheme.typography.labelSmall,
                    color = runningDisplay.color,
                    fontWeight = if (runningDisplay.label.isEmpty()) FontWeight.Normal else FontWeight.Bold
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════
// PersonEditor
// ═══════════════════════════════════════════════════════

@Composable
fun PersonEditor(old: Person?, db: AppDb, onSave: (Person) -> Unit, onCancel: () -> Unit) {
    var name by remember(old) { mutableStateOf(old?.name ?: "") }
    var note by remember(old) { mutableStateOf(old?.note ?: "") }

    val personAccounts by if (old != null) {
        db.accounts().byPerson(old.id).collectAsState(emptyList())
    } else {
        remember { mutableStateOf(emptyList<Account>()) }
    }

    var selectedAccounts by remember(old) {
        mutableStateOf(old?.displayedCurrencies?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet() ?: emptySet())
    }

    val textColor = Color(0xFF1B1B1F)
    val labelColor = Color(0xFF5C5D72)
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = textColor,
        unfocusedTextColor = textColor,
        focusedBorderColor = HeaderBlue,
        unfocusedBorderColor = Color(0xFFCCCCCC),
        focusedLabelColor = HeaderBlue,
        unfocusedLabelColor = labelColor,
        cursorColor = HeaderBlue
    )

    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = Color.White,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.border(2.dp, HeaderBlue, RoundedCornerShape(20.dp)),
        title = { Text(if (old == null) "شخص جدید" else "ویرایش شخص", fontWeight = FontWeight.Bold, color = HeaderBlue) },
        text = {
            Column {
                OutlinedTextField(name, { name = it }, label = { Text("نام شخص") }, modifier = Modifier.fillMaxWidth(), colors = fieldColors)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(note, { note = it }, label = { Text("توضیحات (اختیاری)") }, modifier = Modifier.fillMaxWidth(), colors = fieldColors)

                if (old != null && personAccounts.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Text("حساب‌های نمایشی (حداکثر ۳ تا)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = HeaderBlue)
                    Spacer(Modifier.height(8.dp))
                    personAccounts.forEach { acc ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                selectedAccounts = if (acc.name in selectedAccounts) selectedAccounts - acc.name
                                else if (selectedAccounts.size < 3) selectedAccounts + acc.name
                                else selectedAccounts
                            }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = acc.name in selectedAccounts,
                                onCheckedChange = {
                                    selectedAccounts = if (acc.name in selectedAccounts) selectedAccounts - acc.name
                                    else if (selectedAccounts.size < 3) selectedAccounts + acc.name
                                    else selectedAccounts
                                },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = HeaderBlue,
                                    checkmarkColor = Color.White,
                                    uncheckedColor = labelColor
                                )
                            )
                            Text(acc.name, style = MaterialTheme.typography.bodyMedium, color = textColor)
                            Spacer(Modifier.weight(1f))
                            Text(acc.displayUnit(), style = MaterialTheme.typography.labelSmall, color = labelColor)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank())
                        onSave(Person(
                            id = old?.id ?: 0,
                            name = name,
                            note = note,
                            displayOrder = old?.displayOrder ?: 0,
                            displayedCurrencies = selectedAccounts.joinToString(","),
                            isTreasury = old?.isTreasury ?: false
                        ))
                },
                colors = ButtonDefaults.buttonColors(containerColor = HeaderBlue, contentColor = Color.White)
            ) { Text("ذخیره", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("انصراف", color = HeaderBlue) } }
    )
}

// ═══════════════════════════════════════════════════════
// AccountEditor
// ═══════════════════════════════════════════════════════

@Composable
fun AccountEditor(old: Account?, personId: Long, onSave: (Account) -> Unit, onCancel: () -> Unit) {
    var name by remember(old) { mutableStateOf(old?.name ?: "") }
    var note by remember(old) { mutableStateOf(old?.note ?: "") }
    var currency by remember(old) { mutableStateOf(old?.currency ?: "ریال") }
    var customUnit by remember(old) { mutableStateOf(old?.customUnit ?: "") }
    var currencyExpanded by remember { mutableStateOf(false) }
    val currencies = listOf("ریال", "تومان", "دلار", "یورو", "پوند", "درهم")
    val hasCustomUnit = customUnit.isNotBlank()

    // معادل ریالی
    var rateEnabled by remember(old) { mutableStateOf(old?.rateEnabled ?: false) }
    var rateMode by remember(old) { mutableStateOf(old?.rateMode ?: "manual") }
    var manualRateText by remember(old) {
        mutableStateOf(if (old != null && old.manualRate > 0.0) old.manualRate.toString() else "")
    }
    var liveKey by remember(old) { mutableStateOf(old?.liveKey ?: "") }
    var liveKeyExpanded by remember { mutableStateOf(false) }

    val textColor = Color(0xFF1B1B1F)
    val labelColor = Color(0xFF5C5D72)
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = textColor,
        unfocusedTextColor = textColor,
        focusedBorderColor = HeaderBlue,
        unfocusedBorderColor = Color(0xFFCCCCCC),
        focusedLabelColor = HeaderBlue,
        unfocusedLabelColor = labelColor,
        cursorColor = HeaderBlue
    )

    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = Color.White,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.border(2.dp, HeaderBlue, RoundedCornerShape(20.dp)),
        title = { Text(if (old == null) "حساب جدید" else "ویرایش حساب", fontWeight = FontWeight.Bold, color = HeaderBlue) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(name, { name = it }, label = { Text("عنوان حساب") }, modifier = Modifier.fillMaxWidth(), colors = fieldColors)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(note, { note = it }, label = { Text("توضیحات (اختیاری)") }, modifier = Modifier.fillMaxWidth(), colors = fieldColors)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    customUnit, { customUnit = it },
                    label = { Text("واحد دلخواه (اختیاری)") },
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = { Text("اگه پر بشه، جایگزین ارز می‌شه", color = labelColor) },
                    colors = fieldColors
                )
                Spacer(Modifier.height(12.dp))
                Text("ارز", style = MaterialTheme.typography.titleSmall, color = HeaderBlue, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Box(Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { if (!hasCustomUnit) currencyExpanded = true },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !hasCustomUnit,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = HeaderBlue)
                    ) {
                        Text(currency, modifier = Modifier.weight(1f), color = HeaderBlue)
                        Text("▼", color = HeaderBlue)
                    }
                    DropdownMenu(expanded = currencyExpanded, onDismissRequest = { currencyExpanded = false }) {
                        currencies.forEach { c ->
                            DropdownMenuItem(text = { Text(c) }, onClick = { currency = c; currencyExpanded = false })
                        }
                    }
                }

                // معادل ریالی
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = Color(0xFFEEEEEE))
                Spacer(Modifier.height(12.dp))

                Row(
                    Modifier.fillMaxWidth().clickable { rateEnabled = !rateEnabled },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = rateEnabled,
                        onCheckedChange = { rateEnabled = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = HeaderBlue,
                            checkmarkColor = Color.White,
                            uncheckedColor = labelColor
                        )
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "نمایش معادل ریالی",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                }

                if (rateEnabled) {
                    Spacer(Modifier.height(10.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = rateMode == "manual",
                            onClick = { rateMode = "manual" },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = HeaderBlue,
                                unselectedColor = labelColor
                            )
                        )
                        Text("دستی", color = textColor, fontSize = 13.sp)
                        Spacer(Modifier.width(16.dp))
                        RadioButton(
                            selected = rateMode == "live",
                            onClick = { rateMode = "live" },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = HeaderBlue,
                                unselectedColor = labelColor
                            )
                        )
                        Text("از قیمت لحظه‌ای", color = textColor, fontSize = 13.sp)
                    }

                    Spacer(Modifier.height(10.dp))

                    if (rateMode == "manual") {
                        OutlinedTextField(
                            value = manualRateText,
                            onValueChange = { input ->
                                manualRateText = input.filter { it.isDigit() || it == '.' }
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            label = { Text("نرخ هر واحد (ریال)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = fieldColors
                        )
                    } else {
                        Text("انتخاب از لیست قیمت‌ها", style = MaterialTheme.typography.labelMedium, color = labelColor)
                        Spacer(Modifier.height(4.dp))
                        Box(Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = { liveKeyExpanded = true },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = HeaderBlue)
                            ) {
                                val display = if (liveKey.isBlank()) "انتخاب کن..."
                                    else PriceCatalog.DEFAULT_TITLES[liveKey] ?: liveKey
                                Text(display, modifier = Modifier.weight(1f), color = HeaderBlue)
                                Text("▼", color = HeaderBlue)
                            }
                            DropdownMenu(
                                expanded = liveKeyExpanded,
                                onDismissRequest = { liveKeyExpanded = false },
                                modifier = Modifier.heightIn(max = 300.dp)
                            ) {
                                PriceCatalog.ALL_ORDERED.forEach { key ->
                                    if (key in PriceCatalog.CRYPTO_KEYS) return@forEach
                                    val title = PriceCatalog.DEFAULT_TITLES[key] ?: key
                                    DropdownMenuItem(
                                        text = { Text(title) },
                                        onClick = { liveKey = key; liveKeyExpanded = false }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        val mr = manualRateText.toDoubleOrNull() ?: 0.0
                        onSave(
                            Account(
                                id = old?.id ?: 0,
                                personId = personId,
                                name = name,
                                note = note,
                                currency = currency,
                                customUnit = customUnit,
                                displayOrder = old?.displayOrder ?: 0,
                                rateEnabled = rateEnabled,
                                rateMode = rateMode,
                                manualRate = mr,
                                liveKey = liveKey,
                                nature = old?.nature ?: "credit"
                            )
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = HeaderBlue, contentColor = Color.White)
            ) { Text("ذخیره", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("انصراف", color = HeaderBlue) } }
    )
}
