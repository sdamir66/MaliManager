import com.example.maliplus.ui.theme.MaliManagerTheme
@file:OptIn(ExperimentalMaterial3Api::class)
package com.example.maliplus

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.room.Room
import com.example.maliplus.data.*
import com.example.maliplus.util.Jalali
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.NumberFormat
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val db = Room.databaseBuilder(applicationContext, AppDb::class.java, "finance.db")
            .fallbackToDestructiveMigration()
            .build()
        setContent { 
    MaliManagerTheme { 
        FinanceApp(db)
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

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        when {
            account != null -> AccountScreen(db, account!!) { account = null }
            good != null -> GoodScreen(db, good!!) { good = null }
            else -> Scaffold(
                topBar = { TopAppBar(title = { Text("مدیریت مالی") }) },
                bottomBar = {
                    NavigationBar {
                        NavigationBarItem(
                            selected = section == 0,
                            onClick = { section = 0 },
                            icon = { Text("👤") },
                            label = { Text("حساب‌ها") }
                        )
                        NavigationBarItem(
                            selected = section == 1,
                            onClick = { section = 1 },
                            icon = { Text("📦") },
                            label = { Text("کالاها") }
                        )
                        NavigationBarItem(
                            selected = section == 2,
                            onClick = { section = 2 },
                            icon = { Text("☁") },
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
            Text("حساب‌ها", style = MaterialTheme.typography.headlineSmall)
            Button({ add = true }) { Text("+ حساب") }
        }
        LazyColumn {
            items(list) { a ->
                val tx by db.tx().byAccount(a.id).collectAsState(emptyList())
                val bal = tx.sumOf { if (it.type == "بستانکار") it.amount else -it.amount }
                Card(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { onOpen(a) }
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(a.name, style = MaterialTheme.typography.titleLarge)
                        Text("مانده: ${money(kotlin.math.abs(bal))} تومان  ${if (bal >= 0) "بستانکار" else "بدهکار"}")
                        Row {
                            TextButton({ onOpen(a) }) { Text("گردش") }
                            TextButton({ edit = a }) { Text("ویرایش") }
                            TextButton({
                                scope.launch {
                                    db.tx().deleteAutoByPrefix(a.id.toString())
                                    db.accounts().delete(a)
                                }
                            }) { Text("حذف") }
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
                OutlinedTextField(name, { name = it }, label = { Text("نام حساب") })
                OutlinedTextField(note, { note = it }, label = { Text("توضیحات") })
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onBack) { Text("بازگشت") }
            Text(a.name, style = MaterialTheme.typography.headlineSmall)
        }
        Text(
            "مانده: ${money(kotlin.math.abs(bal))} تومان  ${if (bal >= 0) "بستانکار" else "بدهکار"}",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(8.dp))
        Row {
            Button({ add = true }) { Text("+ تراکنش") }
            Spacer(Modifier.width(8.dp))
            OutlinedButton({ profit = true }) { Text("تنظیم سود") }
        }
        LazyColumn {
            items(tx) { t ->
                ListItem(
                    headlineContent = { Text("${t.type}: ${money(t.amount)} تومان") },
                    supportingContent = { Text("${Jalali.format(t.dateMillis)}  ${t.note}") },
                    trailingContent = {
                        if (!t.isAutoProfit) Row {
                            TextButton({ editor = t }) { Text("ویرایش") }
                            TextButton({
                                scope.launch {
                                    db.tx().delete(t)
                                    ProfitEngine.recalculateAll(db)
                                }
                            }) { Text("حذف") }
                        } else Text("خودکار")
                    }
                )
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
                OutlinedTextField(
                    amount, { amount = it },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    label = { Text("مبلغ تومان") }
                )
                OutlinedTextField(date, { date = it }, label = { Text("تاریخ شمسی 1405/02/31") })
                OutlinedTextField(note, { note = it }, label = { Text("شرح") })
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
                Text("نوع سود")
                Row {
                    FilterChip(mode == "DAILY_ANNUAL", { mode = "DAILY_ANNUAL" }, label = { Text("روزشمار سالانه") })
                    Spacer(Modifier.width(6.dp))
                    FilterChip(mode == "MONTHLY", { mode = "MONTHLY" }, label = { Text("ماهانه") })
                }
                if (mode == "DAILY_ANNUAL")
                    OutlinedTextField(
                        annual, { annual = it },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        label = { Text("نرخ سالانه ٪") }
                    )
                if (mode == "MONTHLY")
                    OutlinedButton({ ratesOpen = true }) { Text("تنظیم نرخ ماه‌های سال") }
                OutlinedTextField(
                    day, { day = it },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    label = { Text("روز واریز ماه") }
                )
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
                    Text(year.toString(), style = MaterialTheme.typography.titleMedium)
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
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("حساب‌های کالایی", style = MaterialTheme.typography.headlineSmall)
            Button({ add = true }) { Text("+ کالا") }
        }
        LazyColumn {
            items(list) { g ->
                val tx by db.goodTx().byGood(g.id).collectAsState(emptyList())
                val bal = tx.sumOf { if (it.type == "دریافت") it.quantity else -it.quantity }
                Card(
                    Modifier
                        .fillMaxWidth()
                        .padding(4.dp)
                        .clickable { onOpen(g) }
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(g.name, style = MaterialTheme.typography.titleLarge)
                        Text("نوع: ${g.type}  |  واحد: ${g.unit}")
                        Text("موجودی: ${qty(bal)} ${g.unit}")
                        Row {
                            TextButton({ onOpen(g) }) { Text("گردش") }
                            TextButton({ edit = g }) { Text("ویرایش") }
                            TextButton({ scope.launch { db.goods().delete(g) } }) { Text("حذف") }
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
                OutlinedTextField(n, { n = it }, label = { Text("نام کالا") })
                OutlinedTextField(t, { t = it }, label = { Text("نوع کالا") })
                OutlinedTextField(u, { u = it }, label = { Text("واحد") })
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
        Row {
            TextButton(onBack) { Text("بازگشت") }
            Text(g.name, style = MaterialTheme.typography.headlineSmall)
        }
        Text("موجودی: ${qty(bal)} ${g.unit}")
        Button({ add = true }) { Text("+ گردش کالا") }
        LazyColumn {
            items(tx) { x ->
                ListItem(
                    headlineContent = { Text("${x.type}: ${qty(x.quantity)} ${g.unit}") },
                    supportingContent = { Text("${Jalali.format(x.dateMillis)}  ${x.note}") },
                    trailingContent = {
                        Row {
                            TextButton({ edit = x }) { Text("ویرایش") }
                            TextButton({ scope.launch { db.goodTx().delete(x) } }) { Text("حذف") }
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
                OutlinedTextField(q, { q = it }, label = { Text("مقدار") })
                OutlinedTextField(date, { date = it }, label = { Text("تاریخ شمسی") })
                OutlinedTextField(note, { note = it }, label = { Text("شرح") })
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
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("پشتیبان و بازیابی", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        Text("فایل JSON را می‌توان در حافظه یا Google Drive ذخیره کرد.")
        Spacer(Modifier.height(12.dp))
        Button({ create.launch("MaliManager-backup.json") }) { Text("گرفتن بکاپ") }
        Button({ open.launch(arrayOf("application/json", "text/*")) }) { Text("بازیابی بکاپ") }
        Text(
            "برای Google Drive، هنگام انتخاب محل فایل، Google Drive را انتخاب کن.",
            style = MaterialTheme.typography.bodySmall
        )
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
                val id = db.accounts().insert(Account(0, o.getString("name"), o.optString("note")))
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
