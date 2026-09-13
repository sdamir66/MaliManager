@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.maliplus

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.room.Room
import com.example.maliplus.data.*
import com.example.maliplus.ui.theme.BgLight
import com.example.maliplus.ui.theme.CreditGreen
import com.example.maliplus.ui.theme.DebitRed
import com.example.maliplus.ui.theme.HeaderBlue
import com.example.maliplus.ui.theme.MaliManagerTheme
import com.example.maliplus.util.Jalali
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.NumberFormat
import java.util.Locale

suspend fun autoBackupToInternal(context: Context, db: AppDb): Boolean {
    return try {
        val file = java.io.File(context.filesDir, "auto_backup.json")
        val uri = Uri.fromFile(file)
        Backup.restoreOrExport(context, db, uri, false)
        true
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}

class MainActivity : ComponentActivity() {

    private lateinit var db: AppDb
    private val backupScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        db = Room.databaseBuilder(applicationContext, AppDb::class.java, "finance.db")
            .fallbackToDestructiveMigration()
            .build()

        backupScope.launch {
            while (true) {
                delay(60_000L)
                autoBackupToInternal(applicationContext, db)
            }
        }

        setContent {
            MaliManagerTheme {
                FinanceApp(db)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        backupScope.launch {
            autoBackupToInternal(applicationContext, db)
        }
    }

    override fun onDestroy() {
        backupScope.launch {
            autoBackupToInternal(applicationContext, db)
        }
        super.onDestroy()
    }
}

private fun money(v: Long) = NumberFormat.getNumberInstance(Locale.US).format(v)

private fun qty(v: Double): String =
    if (v % 1.0 == 0.0) v.toLong().toString() else "%.3f".format(Locale.US, v)

fun calculateRunningBalances(transactions: List<Transaction>): Map<Long, Long> {
    val sorted = transactions.sortedBy { it.dateMillis }
    val balances = mutableMapOf<Long, Long>()
    var running = 0L
    for (t in sorted) {
        running += if (t.type == "بستانکار") t.amount else -t.amount
        balances[t.id] = running
    }
    return balances
}

@Composable
fun FinanceApp(db: AppDb) {
    var account by remember { mutableStateOf<Account?>(null) }
    var good by remember { mutableStateOf<Good?>(null) }
    var section by remember { mutableIntStateOf(0) }

    BackHandler(enabled = account != null || good != null || section != 0) {
        when {
            account != null -> account = null
            good != null -> good = null
            section != 0 -> section = 0
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        when {
            account != null -> AccountScreen(db, account!!) { account = null }
            good != null -> GoodScreen(db, good!!) { good = null }
            else -> Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("مدیریت مالی", fontWeight = FontWeight.Bold) },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                },
                bottomBar = {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 3.dp
                    ) {
                        NavigationBarItem(
                            selected = section == 0,
                            onClick = { section = 0 },
                            icon = { Text("👤", style = MaterialTheme.typography.titleLarge) },
                            label = { Text("حساب‌ها") }
                        )
                        NavigationBarItem(
                            selected = section == 1,
                            onClick = { section = 1 },
                            icon = { Text("📦", style = MaterialTheme.typography.titleLarge) },
                            label = { Text("کالاها") }
                        )
                        NavigationBarItem(
                            selected = section == 2,
                            onClick = { section = 2 },
                            icon = { Text("⚙️", style = MaterialTheme.typography.titleLarge) },
                            label = { Text("تنظیمات") }
                        )
                    }
                }
            ) { p ->
                Box(Modifier.padding(p)) {
                    when (section) {
                        0 -> Accounts(db) { account = it }
                        1 -> Goods(db) { good = it }
                        else -> BackupScreen(db)
                    }
                }
            }
        }
    }
}

@Composable
fun BackButton(onClick: () -> Unit) {
    FilledTonalIconButton(
        onClick = onClick,
        modifier = Modifier.size(48.dp),
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    ) {
        Icon(
            imageVector = Icons.Default.ArrowBack,
            contentDescription = "بازگشت",
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
fun Accounts(db: AppDb, onOpen: (Account) -> Unit) {
    val list by db.accounts().all().collectAsState(emptyList())
    var edit by remember { mutableStateOf<Account?>(null) }
    var add by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().background(BgLight).padding(16.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "حساب‌ها",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Button({ add = true }) { Text("+ حساب") }
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(list) { a ->
                val tx by db.tx().byAccount(a.id).collectAsState(emptyList())
                val bal = tx.sumOf { if (it.type == "بستانکار") it.amount else -it.amount }
                val isCredit = bal >= 0
                Card(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onOpen(a) },
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(44.dp)
                                    .background(
                                        color = if (isCredit) CreditGreen else DebitRed,
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    a.name.take(1),
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    a.name,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "ارز: ${a.currency}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "مانده: ${money(kotlin.math.abs(bal))} ${a.currency}",
                            fontWeight = FontWeight.Bold,
                            color = if (isCredit) CreditGreen else DebitRed,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            if (isCredit) "بستانکار" else "بدهکار",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                        Spacer(Modifier.height(10.dp))
                        Row {
                            TextButton({ onOpen(a) }) { Text("گردش") }
                            TextButton({ edit = a }) { Text("ویرایش") }
                            TextButton({
                                scope.launch {
                                    db.tx().deleteAutoByPrefix(a.id.toString())
                                    db.accounts().delete(a)
                                }
                            }) {
                                Text("حذف", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
    if (add) AccountEditor(null, { scope.launch { db.accounts().insert(it); add = false } }, { add = false })
    edit?.let {
        AccountEditor(it, { scope.launch { db.accounts().update(it); edit = null } }, { edit = null })
    }
}

@Composable
fun AccountEditor(old: Account?, onSave: (Account) -> Unit, onCancel: () -> Unit) {
    var name by remember(old) { mutableStateOf(old?.name ?: "") }
    var note by remember(old) { mutableStateOf(old?.note ?: "") }
    var currency by remember(old) { mutableStateOf(old?.currency ?: "تومان") }
    var currencyExpanded by remember { mutableStateOf(false) }

    val currencies = listOf("ریال", "تومان", "دلار", "یورو", "پوند", "درهم")

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(if (old == null) "حساب عادی جدید" else "ویرایش حساب") },
        text = {
            Column {
                OutlinedTextField(name, { name = it }, label = { Text("نام حساب") },
                    modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(note, { note = it }, label = { Text("توضیحات") },
                    modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                Text("ارز", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                Box(Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { currencyExpanded = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(currency, modifier = Modifier.weight(1f))
                        Text("▼")
                    }
                    DropdownMenu(
                        expanded = currencyExpanded,
                        onDismissRequest = { currencyExpanded = false }
                    ) {
                        currencies.forEach { c ->
                            DropdownMenuItem(
                                text = { Text(c) },
                                onClick = {
                                    currency = c
                                    currencyExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button({
                if (name.isNotBlank())
                    onSave(Account(old?.id ?: 0, name, note, currency))
            }) { Text("ذخیره") }
        },
        dismissButton = { TextButton(onCancel) { Text("انصراف") } }
    )
}

@Composable
fun AccountScreen(db: AppDb, a: Account, onBack: () -> Unit) {
    val tx by db.tx().byAccount(a.id).collectAsState(emptyList())
    val setting by db.profit().byAccount(a.id).collectAsState(null)
    val accounts by db.accounts().all().collectAsState(emptyList())
    var editor by remember { mutableStateOf<Transaction?>(null) }
    var add by remember { mutableStateOf(false) }
    var profit by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val bal = tx.sumOf { if (it.type == "بستانکار") it.amount else -it.amount }
    val isCredit = bal >= 0

    Box(Modifier.fillMaxSize().background(BgLight)) {
        Column(Modifier.fillMaxSize()) {

            // ═══════════ هدر آبی با گوشه‌های گرد ═══════════
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .background(
                        color = HeaderBlue,
                        shape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)
                    )
            ) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "بازگشت",
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                        Text(
                            a.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { profit = true }) {
                            Text("⚙", color = Color.White, fontSize = 22.sp)
                        }
                    }
                }
            }

            // ═══════════ کارت سفید شناور روی هدر ═══════════
            Card(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .offset(y = (-80).dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "مانده کل",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Gray
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            money(kotlin.math.abs(bal)),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1B1B1F)
                        )
                        Spacer(Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (isCredit) "بستانکار" else "بدهکار",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isCredit) CreditGreen else DebitRed,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                a.currency,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.Gray
                            )
                        }
                    }
                    Box(
                        Modifier
                            .size(56.dp)
                            .background(HeaderBlue, shape = CircleShape)
                            .clickable { add = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "+",
                            color = Color.White,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Light
                        )
                    }
                }
            }

            // ═══════════ لیست تراکنش‌ها ═══════════
            val runningBalances = remember(tx) { calculateRunningBalances(tx) }

            LazyColumn(
                Modifier
                    .fillMaxSize()
                    .offset(y = (-60).dp)
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text(
                        "تراکنش‌ها",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1B1B1F),
                        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                    )
                }

                items(tx, key = { it.id }) { t ->
                    val running = runningBalances[t.id] ?: 0L
                    val isTxCredit = t.type == "بستانکار"

                    Card(
                        Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !t.isAutoProfit) { editor = t },
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier
                                    .size(46.dp)
                                    .background(
                                        color = if (isTxCredit) CreditGreen else DebitRed,
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    if (isTxCredit) "↓" else "↑",
                                    color = Color.White,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(Modifier.width(12.dp))

                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        t.type,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isTxCredit) CreditGreen else DebitRed
                                    )
                                    if (t.isAutoProfit) {
                                        Spacer(Modifier.width(6.dp))
                                        Surface(
                                            color = MaterialTheme.colorScheme.tertiaryContainer,
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                "خودکار",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    Jalali.format(t.dateMillis),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                                if (t.note.isNotBlank()) {
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        t.note,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF45464F)
                                    )
                                }
                            }

                            Spacer(Modifier.width(8.dp))

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    "${if (isTxCredit) "+" else "−"}${money(t.amount)}",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isTxCredit) CreditGreen else DebitRed
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    "مانده: ${money(kotlin.math.abs(running))}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.Gray
                                )
                            }
                        }
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
    if (profit) ProfitSettingsEditor(db, a, setting, accounts) { profit = false }
}

@Composable
fun TxEditor(old: Transaction?, accountId: Long, onSave: (Transaction) -> Unit, onCancel: () -> Unit) {
    var type by remember(old) { mutableStateOf(old?.type ?: "بدهکار") }
    var amount by remember(old) { mutableStateOf(old?.amount?.toString() ?: "") }
    var note by remember(old) { mutableStateOf(old?.note ?: "") }
    var date by remember(old) {
        mutableStateOf(
            if (old != null) Jalali.format(old.dateMillis).substringBefore(" ")
            else Jalali.format(System.currentTimeMillis()).substringBefore(" ")
        )
    }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(if (old == null) "ثبت تراکنش" else "ویرایش تراکنش") },
        text = {
            Column {
                Row {
                    FilterChip(type == "بدهکار", { type = "بدهکار" }, label = { Text("بدهکار") })
                    Spacer(Modifier.width(8.dp))
                    FilterChip(type == "بستانکار", { type = "بستانکار" }, label = { Text("بستانکار") })
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    amount, { amount = it },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    label = { Text("مبلغ") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(date, { date = it }, label = { Text("تاریخ شمسی 1405/02/31") },
                    modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(note, { note = it }, label = { Text("شرح") },
                    modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button({
                val n = amount.toLongOrNull()
                val d = if (old != null) {
                    val newDate = Jalali.parse(date)
                    if (newDate != null) {
                        val origCal = java.util.Calendar.getInstance().apply { timeInMillis = old.dateMillis }
                        val newCal = java.util.Calendar.getInstance().apply { timeInMillis = newDate }
                        newCal.set(java.util.Calendar.HOUR_OF_DAY, origCal.get(java.util.Calendar.HOUR_OF_DAY))
                        newCal.set(java.util.Calendar.MINUTE, origCal.get(java.util.Calendar.MINUTE))
                        newCal.set(java.util.Calendar.SECOND, origCal.get(java.util.Calendar.SECOND))
                        newCal.timeInMillis
                    } else null
                } else {
                    Jalali.parseWithCurrentTime(date)
                }
                if (n != null && n > 0 && d != null)
                    onSave(Transaction(old?.id ?: 0, accountId, d, type, n, note, false, null))
            }) { Text("ذخیره") }
        },
        dismissButton = { TextButton(onCancel) { Text("انصراف") } }
    )
}

@Composable
fun ProfitSettingsEditor(
    db: AppDb, a: Account, old: ProfitSettings?,
    accounts: List<Account>, close: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var enabled by remember(old) { mutableStateOf(old?.enabled ?: false) }
    var mode by remember(old) { mutableStateOf(old?.mode ?: "DAILY_ANNUAL") }
    var annual by remember(old) { mutableStateOf(old?.annualRate?.toString() ?: "20") }
    var day by remember(old) { mutableStateOf(old?.payoutDay?.toString() ?: "30") }
    var dest by remember(old) { mutableStateOf(old?.destinationAccountId) }
    var ratesOpen by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = close,
        title = { Text("تنظیم سود") },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(enabled, { enabled = !enabled })
                    Text("فعال باشد")
                }
                Spacer(Modifier.height(8.dp))
                Text("نوع سود", fontWeight = FontWeight.Medium)
                Row {
                    FilterChip(mode == "DAILY_ANNUAL", { mode = "DAILY_ANNUAL" }, label = { Text("روزشمار سالانه") })
                    Spacer(Modifier.width(6.dp))
                    FilterChip(mode == "MONTHLY", { mode = "MONTHLY" }, label = { Text("ماهانه") })
                }
                Spacer(Modifier.height(8.dp))
                if (mode == "DAILY_ANNUAL")
                    OutlinedTextField(
                        annual, { annual = it },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        label = { Text("نرخ سالانه ٪") },
                        modifier = Modifier.fillMaxWidth()
                    )
                if (mode == "MONTHLY")
                    OutlinedButton({ ratesOpen = true }) { Text("تنظیم نرخ ماه‌های سال") }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    day, { day = it },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    label = { Text("روز واریز ماه") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Text("حساب مقصد سود", style = MaterialTheme.typography.titleSmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(dest == null, { dest = null })
                    Text("همین حساب")
                }
                accounts.filter { it.id != a.id }.forEach { acc ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(dest == acc.id, { dest = acc.id })
                        Text(acc.name)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "با ویرایش تراکنش‌های گذشته، سودهای خودکار دوباره محاسبه می‌شوند.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            Button({
                scope.launch {
                    db.profit().upsert(
                        ProfitSettings(
                            old?.id ?: 0, a.id, enabled, mode,
                            annual.toDoubleOrNull() ?: 0.0,
                            day.toIntOrNull()?.coerceIn(1, 31) ?: 30,
                            dest
                        )
                    )
                    ProfitEngine.recalculateAll(db)
                    close()
                }
            }) { Text("ذخیره") }
        },
        dismissButton = { TextButton(close) { Text("انصراف") } }
    )
    if (ratesOpen) MonthlyRatesEditor(db, a.id) { ratesOpen = false }
}

@Composable
fun MonthlyRatesEditor(db: AppDb, accountId: Long, close: () -> Unit) {
    val rates by db.profit().rates(accountId).collectAsState(emptyList())
    var year by rememberSaveable { mutableIntStateOf(Jalali.nowJalali()[0]) }
    val values = remember(rates, year) {
        mutableStateMapOf<Int, String>().apply {
            for (m in 1..12) put(m, rates.find { it.year == year && it.month == m }?.ratePercent?.toString() ?: "")
        }
    }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = close,
        title = { Text("نرخ سود ماهانه $year") },
        text = {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextButton({ year-- }) { Text("سال قبل") }
                    Text(year.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    TextButton({ year++ }) { Text("سال بعد") }
                }
                LazyColumn {
                    items((1..12).toList()) { m ->
                        OutlinedTextField(
                            values[m] ?: "", { values[m] = it },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            label = { Text(Jalali.monthName(m)) },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button({
                scope.launch {
                    for (m in 1..12) {
                        val v = values[m]?.replace(",", "")?.toDoubleOrNull()
                        if (v != null) db.profit().upsertRate(MonthlyRate(accountId, year, m, v))
                    }
                    ProfitEngine.recalculateAll(db)
                    close()
                }
            }) { Text("ذخیره نرخ‌ها") }
        },
        dismissButton = { TextButton(close) { Text("انصراف") } }
    )
}

@Composable
fun Goods(db: AppDb, onOpen: (Good) -> Unit) {
    val list by db.goods().all().collectAsState(emptyList())
    var add by remember { mutableStateOf(false) }
    var edit by remember { mutableStateOf<Good?>(null) }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().background(BgLight).padding(16.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "کالاها",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Button({ add = true }) { Text("+ کالا") }
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(list) { g ->
                val tx by db.goodTx().byGood(g.id).collectAsState(emptyList())
                val bal = tx.sumOf { if (it.type == "دریافت") it.quantity else -it.quantity }
                Card(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onOpen(g) },
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            g.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text("نوع: ${g.type}  |  واحد: ${g.unit}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Gray)
                        Text(
                            "موجودی: ${qty(bal)} ${g.unit}",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.height(8.dp))
                        Row {
                            TextButton({ onOpen(g) }) { Text("گردش") }
                            TextButton({ edit = g }) { Text("ویرایش") }
                            TextButton({ scope.launch { db.goods().delete(g) } }) {
                                Text("حذف", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
    if (add) GoodEditor(null, { scope.launch { db.goods().insert(it); add = false } }, { add = false })
    edit?.let {
        GoodEditor(it, { scope.launch { db.goods().update(it); edit = null } }, { edit = null })
    }
}

@Composable
fun GoodEditor(old: Good?, onSave: (Good) -> Unit, onCancel: () -> Unit) {
    var n by remember(old) { mutableStateOf(old?.name ?: "") }
    var t by remember(old) { mutableStateOf(old?.type ?: "") }
    var u by remember(old) { mutableStateOf(old?.unit ?: "") }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(if (old == null) "کالای جدید" else "ویرایش کالا") },
        text = {
            Column {
                OutlinedTextField(n, { n = it }, label = { Text("نام") },
                    modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(t, { t = it }, label = { Text("نوع کالا") },
                    modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(u, { u = it }, label = { Text("واحد") },
                    modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button({ if (n.isNotBlank() && u.isNotBlank()) onSave(Good(old?.id ?: 0, n, t, u)) }) { Text("ذخیره") }
        },
        dismissButton = { TextButton(onCancel) { Text("انصراف") } }
    )
}

@Composable
fun GoodScreen(db: AppDb, g: Good, onBack: () -> Unit) {
    val tx by db.goodTx().byGood(g.id).collectAsState(emptyList())
    var edit by remember { mutableStateOf<GoodTransaction?>(null) }
    var add by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val bal = tx.sumOf { if (it.type == "دریافت") it.quantity else -it.quantity }

    Box(Modifier.fillMaxSize().background(BgLight)) {
        Column(Modifier.fillMaxSize()) {

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .background(
                        color = HeaderBlue,
                        shape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)
                    )
            ) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "بازگشت",
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                        Text(
                            g.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Card(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .offset(y = (-70).dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("موجودی", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${qty(bal)} ${g.unit}",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1B1B1F)
                        )
                    }
                    Box(
                        Modifier
                            .size(56.dp)
                            .background(HeaderBlue, shape = CircleShape)
                            .clickable { add = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("+", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Light)
                    }
                }
            }

            LazyColumn(
                Modifier
                    .fillMaxSize()
                    .offset(y = (-50).dp)
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text(
                        "گردش کالا",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1B1B1F),
                        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                    )
                }
                items(tx) { x ->
                    val isIn = x.type == "دریافت"
                    Card(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier
                                    .size(46.dp)
                                    .background(
                                        color = if (isIn) CreditGreen else DebitRed,
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    if (isIn) "↓" else "↑",
                                    color = Color.White,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    x.type,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isIn) CreditGreen else DebitRed
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    Jalali.format(x.dateMillis),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                                if (x.note.isNotBlank()) {
                                    Text(x.note, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            Text(
                                "${qty(x.quantity)} ${g.unit}",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (isIn) CreditGreen else DebitRed
                            )
                        }
                    }
                }
            }
        }
    }

    if (add) GoodTxEditor(null, g.id, { scope.launch { db.goodTx().insert(it); add = false } }, { add = false })
    edit?.let {
        GoodTxEditor(it, g.id, { scope.launch { db.goodTx().update(it); edit = null } }, { edit = null })
    }
}

@Composable
fun GoodTxEditor(
    old: GoodTransaction?, id: Long,
    onSave: (GoodTransaction) -> Unit, onCancel: () -> Unit
) {
    var type by remember(old) { mutableStateOf(old?.type ?: "دریافت") }
    var q by remember(old) { mutableStateOf(old?.quantity?.toString() ?: "") }
    var date by remember(old) {
        mutableStateOf(
            if (old != null) Jalali.format(old.dateMillis).substringBefore(" ")
            else Jalali.format(System.currentTimeMillis()).substringBefore(" ")
        )
    }
    var note by remember(old) { mutableStateOf(old?.note ?: "") }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("گردش کالا") },
        text = {
            Column {
                Row {
                    FilterChip(type == "دریافت", { type = "دریافت" }, label = { Text("دریافت") })
                    Spacer(Modifier.width(8.dp))
                    FilterChip(type == "پرداخت", { type = "پرداخت" }, label = { Text("پرداخت") })
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(q, { q = it }, label = { Text("مقدار") },
                    modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(date, { date = it }, label = { Text("تاریخ شمسی") },
                    modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(note, { note = it }, label = { Text("شرح") },
                    modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button({
                val n = q.toDoubleOrNull()
                val d = if (old != null) {
                    val newDate = Jalali.parse(date)
                    if (newDate != null) {
                        val origCal = java.util.Calendar.getInstance().apply { timeInMillis = old.dateMillis }
                        val newCal = java.util.Calendar.getInstance().apply { timeInMillis = newDate }
                        newCal.set(java.util.Calendar.HOUR_OF_DAY, origCal.get(java.util.Calendar.HOUR_OF_DAY))
                        newCal.set(java.util.Calendar.MINUTE, origCal.get(java.util.Calendar.MINUTE))
                        newCal.set(java.util.Calendar.SECOND, origCal.get(java.util.Calendar.SECOND))
                        newCal.timeInMillis
                    } else null
                } else {
                    Jalali.parseWithCurrentTime(date)
                }
                if (n != null && n > 0 && d != null)
                    onSave(GoodTransaction(old?.id ?: 0, id, d, type, n, note))
            }) { Text("ذخیره") }
        },
        dismissButton = { TextButton(onCancel) { Text("انصراف") } }
    )
}

@Composable
fun BackupScreen(db: AppDb) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch { Backup.restoreOrExport(context, db, uri, false) }
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch { Backup.restoreOrExport(context, db, uri, true) }
    }

    var autoBackupExists by remember { mutableStateOf(false) }
    var autoBackupInfo by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        val file = java.io.File(context.filesDir, "auto_backup.json")
        autoBackupExists = file.exists()
        if (file.exists()) {
            val dateFormat = java.text.SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US)
            autoBackupInfo = dateFormat.format(java.util.Date(file.lastModified()))
        }
    }

    Column(Modifier.fillMaxSize().background(BgLight).padding(16.dp)) {
        Text(
            "تنظیمات",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(16.dp))

        Text(
            "پشتیبان و بازیابی",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    "فایل JSON را می‌توان در حافظه یا Google Drive ذخیره کرد.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Button(
            { create.launch("MaliManager-backup.json") },
            modifier = Modifier.fillMaxWidth()
        ) { Text("گرفتن بکاپ") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            { open.launch(arrayOf("application/json", "text/*")) },
            modifier = Modifier.fillMaxWidth()
        ) { Text("بازیابی بکاپ") }

        Spacer(Modifier.height(20.dp))
        Text(
            "بکاپ خودکار",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                if (autoBackupExists) {
                    Text(
                        "✅ بکاپ خودکار موجود است",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "آخرین بکاپ: $autoBackupInfo",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        {
                            scope.launch {
                                val file = java.io.File(context.filesDir, "auto_backup.json")
                                if (file.exists()) {
                                    Backup.restoreOrExport(context, db, Uri.fromFile(file), true)
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("بازیابی از بکاپ خودکار") }
                } else {
                    Text(
                        "هنوز بکاپ خودکاری ذخیره نشده است.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "بکاپ خودکار هر ۱ دقیقه، هنگام خروج از برنامه و رفتن به پس‌زمینه ذخیره می‌شود.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(20.dp))
        Text(
            "درباره",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    "مدیریت مالی",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "نسخه ۱.۰",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "سازنده: sdamir66",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}

object ProfitEngine {
    suspend fun recalculateAll(db: AppDb) {
        db.tx().deleteAllAuto()
        for (a in db.accounts().allNow()) recalculateWithoutDeletingAuto(db, a.id)
    }

    suspend fun recalculate(db: AppDb, accountId: Long) {
        recalculateAll(db)
    }

    private suspend fun recalculateWithoutDeletingAuto(db: AppDb, accountId: Long) {
        val s = db.profit().byAccountNow(accountId) ?: return
        if (!s.enabled) return
        val base = db.tx().byAccountNow(accountId).filter { !it.isAutoProfit }
        val rates = db.profit().ratesNow(accountId)
        val today = Jalali.nowJalali()
        val out = mutableListOf<Transaction>()
        val months = rates.map { it.year * 12 + it.month }.toMutableSet()
        if (s.mode == "DAILY_ANNUAL") {
            for (y in (today[0] - 2)..today[0]) for (m in 1..12) months.add(y * 12 + m)
        }
        for (key in months.sorted()) {
            val y = key / 12
            val m = key % 12
            if (y > today[0] || (y == today[0] && m > today[1])) continue
            val days = Jalali.daysInMonth(y, m)
            val rate = if (s.mode == "MONTHLY") rates.find { it.year == y && it.month == m }?.ratePercent ?: continue
            else s.annualRate
            var total = 0.0
            for (d in 1..days) {
                val t = Jalali.startOfJalaliMonth(y, m) + ((d - 1) * 86400000L)
                val bal = balanceAt(base, t)
                total += if (s.mode == "MONTHLY") bal * (rate / 100.0) / days
                else bal * (rate / 100.0) / 365.0
            }
            val amount = kotlin.math.round(total).toLong()
            if (amount <= 0) continue
            val payout = jalaliPayout(y, m, s.payoutDay)
            if (payout > System.currentTimeMillis()) continue
            val destCandidate = s.destinationAccountId ?: accountId
            val dest = if (db.accounts().byId(destCandidate) != null) destCandidate else accountId
            val text = if (s.mode == "MONTHLY")
                "سود ماهانه ${Jalali.monthName(m)} $y — نرخ ${rate}%"
            else
                "سود روزشمار ${Jalali.monthName(m)} $y — نرخ ${s.annualRate}% سالانه"
            out.add(Transaction(0, dest, payout, "بستانکار", amount, text, true, "$accountId:$y:$m"))
        }
        db.tx().insertAll(out)
    }

    private fun balanceAt(base: List<Transaction>, t: Long): Long =
        base.filter { it.dateMillis <= t }.sumOf { if (it.type == "بستانکار") it.amount else -it.amount }
}

private fun jalaliPayout(y: Int, m: Int, day: Int): Long =
    Jalali.parse("%04d/%02d/%02d".format(Locale.US, y, m, day.coerceAtMost(Jalali.daysInMonth(y, m))))!!

object Backup {
    suspend fun restoreOrExport(context: Context, db: AppDb, uri: Uri, restore: Boolean) {
        if (restore) {
            val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: return
            val root = JSONObject(text)
            db.tx().clear(); db.goodTx().clear(); db.goods().clear()
            db.profit().clearRates(); db.profit().clearSettings(); db.accounts().clear()

            val accounts = root.optJSONArray("accounts") ?: JSONArray()
            val idMap = mutableMapOf<Long, Long>()
            for (i in 0 until accounts.length()) {
                val o = accounts.getJSONObject(i)
                val old = o.optLong("id", 0)
                val id = db.accounts().insert(
                    Account(0, o.getString("name"), o.optString("note"), o.optString("currency", "تومان"))
                )
                idMap[old] = id
            }
            val goods = root.optJSONArray("goods") ?: JSONArray()
            val goodMap = mutableMapOf<Long, Long>()
            for (i in 0 until goods.length()) {
                val o = goods.getJSONObject(i)
                val old = o.optLong("id", 0)
                val id = db.goods().insert(Good(0, o.getString("name"), o.optString("type"), o.getString("unit")))
                goodMap[old] = id
            }
            val tx = root.optJSONArray("transactions") ?: JSONArray()
            for (i in 0 until tx.length()) {
                val o = tx.getJSONObject(i)
                val aid = idMap[o.optLong("accountId")]
                if (aid != null) db.tx().insert(
                    Transaction(0, aid, o.getLong("dateMillis"), o.getString("type"),
                        o.getLong("amount"), o.optString("note"),
                        o.optBoolean("isAutoProfit", false),
                        o.optString("profitKey").ifBlank { null })
                )
            }
            val gtx = root.optJSONArray("goodsTransactions") ?: JSONArray()
            for (i in 0 until gtx.length()) {
                val o = gtx.getJSONObject(i)
                val gid = goodMap[o.optLong("goodId")]
                if (gid != null) db.goodTx().insert(
                    GoodTransaction(0, gid, o.getLong("dateMillis"), o.getString("type"),
                        o.getDouble("quantity"), o.optString("note"))
                )
            }
            val ps = root.optJSONArray("profitSettings") ?: JSONArray()
            for (i in 0 until ps.length()) {
                val o = ps.getJSONObject(i)
                val aid = idMap[o.optLong("accountId")]
                if (aid != null) {
                    val did = idMap[o.optLong("destinationAccountId")]
                    db.profit().upsert(
                        ProfitSettings(0, aid, o.optBoolean("enabled"),
                            o.optString("mode", "DAILY_ANNUAL"),
                            o.optDouble("annualRate"),
                            o.optInt("payoutDay", 30), did)
                    )
                }
            }
            val mr = root.optJSONArray("monthlyRates") ?: JSONArray()
            for (i in 0 until mr.length()) {
                val o = mr.getJSONObject(i)
                val aid = idMap[o.optLong("accountId")]
                if (aid != null) db.profit().upsertRate(
                    MonthlyRate(aid, o.getInt("year"), o.getInt("month"), o.getDouble("ratePercent"))
                )
            }
        } else {
            val root = JSONObject()
            fun arr() = JSONArray()

            val aa = arr()
            for (a in db.accounts().allNow()) {
                val o = JSONObject()
                o.put("id", a.id); o.put("name", a.name); o.put("note", a.note)
                o.put("currency", a.currency)
                aa.put(o)
            }
            root.put("accounts", aa)

            val gg = arr()
            for (g in db.goods().allNow()) {
                val o = JSONObject()
                o.put("id", g.id); o.put("name", g.name); o.put("type", g.type); o.put("unit", g.unit)
                gg.put(o)
            }
            root.put("goods", gg)

            val tt = arr()
            for (a in db.accounts().allNow()) for (t in db.tx().byAccountNow(a.id)) {
                val o = JSONObject()
                o.put("accountId", t.accountId); o.put("dateMillis", t.dateMillis)
                o.put("type", t.type); o.put("amount", t.amount)
                o.put("note", t.note); o.put("isAutoProfit", t.isAutoProfit)
                o.put("profitKey", t.profitKey)
                tt.put(o)
            }
            root.put("transactions", tt)

            val gt = arr()
            for (g in db.goods().allNow()) for (t in db.goodTx().byGoodNow(g.id)) {
                val o = JSONObject()
                o.put("goodId", t.goodId); o.put("dateMillis", t.dateMillis)
                o.put("type", t.type); o.put("quantity", t.quantity); o.put("note", t.note)
                gt.put(o)
            }
            root.put("goodsTransactions", gt)

            val ps = arr()
            for (a in db.accounts().allNow()) {
                val s = db.profit().byAccountNow(a.id)
                if (s != null) {
                    val o = JSONObject()
                    o.put("accountId", a.id); o.put("enabled", s.enabled)
                    o.put("mode", s.mode); o.put("annualRate", s.annualRate)
                    o.put("payoutDay", s.payoutDay)
                    o.put("destinationAccountId", s.destinationAccountId ?: JSONObject.NULL)
                    ps.put(o)
                }
            }
            root.put("profitSettings", ps)

            val mr = arr()
            for (a in db.accounts().allNow()) for (r in db.profit().ratesNow(a.id)) {
                val o = JSONObject()
                o.put("accountId", r.accountId); o.put("year", r.year)
                o.put("month", r.month); o.put("ratePercent", r.ratePercent)
                mr.put(o)
            }
            root.put("monthlyRates", mr)

            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(root.toString(2)) }
        }
    }
}
