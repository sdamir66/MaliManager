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
import androidx.room.Room
import com.example.maliplus.data.*
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

/**
 * بکاپ خودکار به حافظه داخلی برنامه.
 * فایل توی پوشه‌ی filesDir ذخیره می‌شه و نیازی به دسترسی خاصی نداره.
 */
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

        // ✅ بکاپ خودکار هر ۱ دقیقه
        backupScope.launch {
            while (true) {
                delay(60_000L) // ۱ دقیقه
                autoBackupToInternal(applicationContext, db)
            }
        }

        setContent {
            MaliManagerTheme {
                FinanceApp(db)
            }
        }
    }

    // ✅ بکاپ خودکار وقتی برنامه به پس‌زمینه می‌ره
    override fun onStop() {
        super.onStop()
        backupScope.launch {
            autoBackupToInternal(applicationContext, db)
        }
    }

    // ✅ بکاپ خودکار موقع خروج کامل
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
                            icon = { Text("☁", style = MaterialTheme.typography.titleLarge) },
                            label = { Text("پشتیبان") }
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
fun SwipeableTransactionItem(
    transaction: Transaction,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    onEdit()
                    false
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    onDelete()
                    false
                }
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
                SwipeToDismissBoxValue.StartToEnd -> Triple(
                    Color(0xFF2E7D6B),
                    Icons.Default.Edit,
                    Alignment.CenterStart
                )
                SwipeToDismissBoxValue.EndToStart -> Triple(
                    Color(0xFFBA1A1A),
                    Icons.Default.Delete,
                    Alignment.CenterEnd
                )
                else -> Triple(
                    Color.Transparent,
                    Icons.Default.Edit,
                    Alignment.Center
                )
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                contentAlignment = alignment
            ) {
                if (color != Color.Transparent) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(color, shape = CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    ) {
        ListItem(
            headlineContent = {
                Text(
                    "${transaction.type}: ${money(transaction.amount)} تومان",
                    fontWeight = FontWeight.SemiBold
                )
            },
            supportingContent = {
                Text(
                    "${Jalali.format(transaction.dateMillis)}  ${transaction.note}",
                    style = MaterialTheme.typography.bodySmall
                )
            },
            colors = ListItemDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        )
    }
}

@Composable
fun Accounts(db: AppDb, onOpen: (Account) -> Unit) {
    val list by db.accounts().all().collectAsState(emptyList())
    var edit by remember { mutableStateOf<Account?>(null) }
    var add by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().padding(16.dp)) {
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
        LazyColumn {
            items(list) { a ->
                val tx by db.tx().byAccount(a.id).collectAsState(emptyList())
                val bal = tx.sumOf { if (it.type == "بستانکار") it.amount else -it.amount }
                Card(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .clickable { onOpen(a) },
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            a.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                        val isCredit = bal >= 0
                        Text(
                            "مانده: ${money(kotlin.math.abs(bal))} تومان  ${if (isCredit) "بستانکار" else "بدهکار"}",
                            color = if (isCredit) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.height(8.dp))
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
            }
        },
        confirmButton = {
            Button({ if (name.isNotBlank()) onSave(Account(old?.id ?: 0, name, note)) }) { Text("ذخیره") }
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

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            BackButton(onBack)
            Spacer(Modifier.width(12.dp))
            Text(
                a.name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(12.dp))
        val isCredit = bal >= 0
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("مانده حساب", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(4.dp))
                Text(
                    "${money(kotlin.math.abs(bal))} تومان",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    if (isCredit) "بستانکار" else "بدهکار",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row {
            Button({ add = true }, modifier = Modifier.weight(1f)) { Text("+ تراکنش") }
            Spacer(Modifier.width(8.dp))
            OutlinedButton({ profit = true }, modifier = Modifier.weight(1f)) { Text("تنظیم سود") }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "برای ویرایش به راست، برای حذف به چپ بکش",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 4.dp)
        )
        LazyColumn {
            items(tx, key = { it.id }) { t ->
                if (!t.isAutoProfit) {
                    SwipeableTransactionItem(
                        transaction = t,
                        onEdit = { editor = t },
                        onDelete = {
                            scope.launch {
                                db.tx().delete(t)
                                ProfitEngine.recalculateAll(db)
                            }
                        }
                    )
                } else {
                    ListItem(
                        headlineContent = {
                            Text(
                                "${t.type}: ${money(t.amount)} تومان",
                                fontWeight = FontWeight.SemiBold
                            )
                        },
                        supportingContent = { Text("${Jalali.format(t.dateMillis)}  ${t.note}") },
                        trailingContent = {
                            Surface(
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                shape = MaterialTheme.shapes.small
                            ) {
                                Text(
                                    "خودکار",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    )
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
    var date by remember(old) { mutableStateOf(Jalali.format(old?.dateMillis ?: System.currentTimeMillis())) }

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
                    label = { Text("مبلغ تومان") },
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
                val d = Jalali.parse(date)
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

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "حساب‌های کالایی",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Button({ add = true }) { Text("+ کالا") }
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn {
            items(list) { g ->
                val tx by db.goodTx().byGood(g.id).collectAsState(emptyList())
                val bal = tx.sumOf { if (it.type == "دریافت") it.quantity else -it.quantity }
                Card(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .clickable { onOpen(g) },
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            g.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text("نوع: ${g.type}  |  واحد: ${g.unit}",
                            style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "موجودی: ${qty(bal)} ${g.unit}",
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
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
        title = { Text(if (old == null) "حساب کالایی جدید" else "ویرایش کالا") },
        text = {
            Column {
                OutlinedTextField(n, { n = it }, label = { Text("نام کالا") },
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

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            BackButton(onBack)
            Spacer(Modifier.width(12.dp))
            Text(
                g.name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(12.dp))
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("موجودی", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(4.dp))
                Text(
                    "${qty(bal)} ${g.unit}",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Button({ add = true }, modifier = Modifier.fillMaxWidth()) { Text("+ گردش کالا") }
        Spacer(Modifier.height(8.dp))
        LazyColumn {
            items(tx) { x ->
                ListItem(
                    headlineContent = {
                        Text(
                            "${x.type}: ${qty(x.quantity)} ${g.unit}",
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    supportingContent = { Text("${Jalali.format(x.dateMillis)}  ${x.note}") },
                    trailingContent = {
                        Row {
                            TextButton({ edit = x }) { Text("ویرایش") }
                            TextButton({ scope.launch { db.goodTx().delete(x) } }) {
                                Text("حذف", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                )
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
    var date by remember(old) { mutableStateOf(Jalali.format(old?.dateMillis ?: System.currentTimeMillis())) }
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
                val d = Jalali.parse(date)
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

    // ✅ وضعیت بکاپ خودکار
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

    Column(Modifier.fillMaxSize().padding(
