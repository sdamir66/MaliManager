@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.maliplus

import android.content.Context
import android.content.Intent
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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ═══════════════════════════════════════════════════════
// بکاپ خودکار
// ═══════════════════════════════════════════════════════

private const val PREFS_NAME = "mali_prefs"
private const val KEY_AUTO_BACKUP_URI = "auto_backup_uri"

fun getSavedBackupFolderUri(context: Context): Uri? {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val uriString = prefs.getString(KEY_AUTO_BACKUP_URI, null) ?: return null
    return try { Uri.parse(uriString) } catch (e: Exception) { null }
}

fun saveBackupFolderUri(context: Context, uri: Uri) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putString(KEY_AUTO_BACKUP_URI, uri.toString()).apply()
}

fun clearBackupFolderUri(context: Context) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().remove(KEY_AUTO_BACKUP_URI).apply()
}

suspend fun autoBackupToInternal(context: Context, db: AppDb): Boolean {
    return try {
        val customFolderUri = getSavedBackupFolderUri(context)
        if (customFolderUri != null) {
            val folder = DocumentFile.fromTreeUri(context, customFolderUri)
            if (folder == null || !folder.exists() || !folder.canWrite()) {
                clearBackupFolderUri(context)
                return autoBackupToDefault(context, db)
            }
            val existing = folder.findFile("auto_backup.json")
            existing?.delete()
            val newFile = folder.createFile("application/json", "auto_backup")
                ?: return autoBackupToDefault(context, db)
            context.contentResolver.openOutputStream(newFile.uri)?.bufferedWriter()?.use {
                it.write(Backup.exportToJson(db))
            }
            true
        } else {
            autoBackupToDefault(context, db)
        }
    } catch (e: Exception) { e.printStackTrace(); false }
}

private suspend fun autoBackupToDefault(context: Context, db: AppDb): Boolean {
    return try {
        val file = java.io.File(context.filesDir, "auto_backup.json")
        val uri = Uri.fromFile(file)
        Backup.restoreOrExport(context, db, uri, false)
        true
    } catch (e: Exception) { e.printStackTrace(); false }
}

class MainActivity : ComponentActivity() {
    private lateinit var db: AppDb
    private val backupScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        db = Room.databaseBuilder(applicationContext, AppDb::class.java, "finance.db")
            .fallbackToDestructiveMigration()
            .build()

        window.statusBarColor = android.graphics.Color.parseColor("#4C5FD7")
        window.navigationBarColor = android.graphics.Color.parseColor("#1E1F25")

        backupScope.launch {
            while (true) {
                delay(5_000L)
                autoBackupToInternal(applicationContext, db)
            }
        }

        setContent {
            MaliManagerTheme { FinanceApp(db) }
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
// کمک‌تابع‌ها
// ═══════════════════════════════════════════════════════

private fun money(v: Long) = NumberFormat.getNumberInstance(Locale.US).format(v)

private fun formatTextFieldValue(input: TextFieldValue): TextFieldValue {
    val digits = input.text.filter { it.isDigit() }
    if (digits.isEmpty()) return input.copy(text = "")
    return try {
        val formatted = NumberFormat.getNumberInstance(Locale.US).format(digits.toLong())
        input.copy(
            text = formatted,
            selection = androidx.compose.ui.text.TextRange(formatted.length)
        )
    } catch (e: Exception) {
        input.copy(text = digits, selection = androidx.compose.ui.text.TextRange(digits.length))
    }
}

private fun parseSeparatedAmount(input: String): Long? {
    val digits = input.replace(",", "").filter { it.isDigit() }
    return if (digits.isEmpty()) null else digits.toLongOrNull()
}

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

fun Account.displayUnit(): String = if (customUnit.isNotBlank()) customUnit else currency

/** تبدیل تاریخ شمسی به millis (روز اول) */
private fun toMillis(y: Int, m: Int, d: Int): Long =
    Jalali.parse("%04d/%02d/%02d".format(Locale.US, y, m, d)) ?: 0L

/** مقایسه‌ی دو تاریخ شمسی */
private fun compareJalali(y1: Int, m1: Int, d1: Int, y2: Int, m2: Int, d2: Int): Int {
    if (y1 != y2) return y1.compareTo(y2)
    if (m1 != m2) return m1.compareTo(m2)
    return d1.compareTo(d2)
}

// ═══════════════════════════════════════════════════════
// FinanceApp
// ═══════════════════════════════════════════════════════

@Composable
fun FinanceApp(db: AppDb) {
    var person by remember { mutableStateOf<Person?>(null) }
    var account by remember { mutableStateOf<Account?>(null) }
    var showSettings by remember { mutableStateOf(false) }

    BackHandler(enabled = account != null || person != null || showSettings) {
        when {
            account != null -> account = null
            person != null -> person = null
            showSettings -> showSettings = false
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        when {
            account != null -> AccountScreen(db, account!!) { account = null }
            person != null -> PersonScreen(
                db = db,
                person = person!!,
                onBack = { person = null },
                onOpenAccount = { account = it }
            )
            showSettings -> SettingsScreen(db) { showSettings = false }
            else -> PersonsScreen(
                db = db,
                onOpenPerson = { person = it },
                onOpenSettings = { showSettings = true }
            )
        }
    }
}

// ═══════════════════════════════════════════════════════
// PageHeader
// ═══════════════════════════════════════════════════════

@Composable
fun PageHeader(
    title: String,
    subtitle: String,
    onBackClick: (() -> Unit)? = null,
    extraActions: (@Composable () -> Unit)? = null
) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(
                color = HeaderBlue,
                shape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)
            )
            .statusBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBackClick != null) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "بازگشت",
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }
                Spacer(Modifier.width(4.dp))
            }

            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }

            if (extraActions != null) {
                Box(Modifier.offset(y = (-15).dp)) {
                    extraActions()
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════
// PersonsScreen
// ═══════════════════════════════════════════════════════

@Composable
fun PersonsScreen(
    db: AppDb,
    onOpenPerson: (Person) -> Unit,
    onOpenSettings: () -> Unit
) {
    val persons by db.persons().all().collectAsState(emptyList())
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
                .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (editMode) "مرتب‌سازی" else "مدیریت مالی",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        if (editMode) "با دکمه‌های ▲▼ جابه‌جا کن"
                        else "${persons.size} شخص  •  ${allAccounts.size} حساب",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }

                IconButton(
                    onClick = { editMode = !editMode },
                    modifier = Modifier.size(48.dp)
                ) {
                    Text(if (editMode) "✓" else "⇅", color = Color.White, fontSize = 24.sp)
                }

                if (!editMode) {
                    IconButton(onClick = onOpenSettings, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.Settings, "تنظیمات", tint = Color.White, modifier = Modifier.size(26.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier
                            .size(52.dp)
                            .background(Color.White, shape = CircleShape)
                            .clickable { add = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("+", color = HeaderBlue, fontSize = 30.sp, fontWeight = FontWeight.Bold)
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
                    Box(
                        Modifier.fillMaxWidth().padding(top = 60.dp),
                        contentAlignment = Alignment.Center
                    ) {
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
                        Column(Modifier.padding(end = 6.dp), verticalArrangement = Arrangement.Center) {
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
                                modifier = Modifier.size(36.dp)
                            ) {
                                Text("▲", fontSize = 16.sp, color = if (index > 0) HeaderBlue else Color.LightGray)
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
                                modifier = Modifier.size(36.dp)
                            ) {
                                Text("▼", fontSize = 16.sp, color = if (index < persons.size - 1) HeaderBlue else Color.LightGray)
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
        val bal = txs.value.sumOf { if (it.type == "بستانکار") it.amount else -it.amount }
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
                    Box(
                        modifier = Modifier.size(44.dp).background(color, shape = CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
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
                    Text(person.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("${accounts.size} حساب", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                }

                Spacer(Modifier.height(6.dp))

                if (filteredAccounts.isEmpty()) {
                    Text("حسابی برای نمایش نیست", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                } else {
                    filteredAccounts.forEach { (acc, bal) ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(acc.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                            Text(money(kotlin.math.abs(bal)), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = if (bal >= 0) CreditGreen else DebitRed)
                            Spacer(Modifier.width(6.dp))
                            Text(acc.displayUnit(), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                            Spacer(Modifier.width(6.dp))
                            Text(if (bal >= 0) "بستانکار" else "بدهکار", style = MaterialTheme.typography.labelSmall, color = if (bal >= 0) CreditGreen else DebitRed, fontWeight = FontWeight.Medium)
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
    var editPerson by remember { mutableStateOf(false) }
    var editMode by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val accountBalances = accounts.map { acc ->
        val txs = remember(acc.id) { mutableStateOf<List<Transaction>>(emptyList()) }
        LaunchedEffect(acc.id) { txs.value = db.tx().byAccountNow(acc.id) }
        val bal = txs.value.sumOf { if (it.type == "بستانکار") it.amount else -it.amount }
        acc to bal
    }

    Box(Modifier.fillMaxSize().background(BgLight)) {
        Column(Modifier.fillMaxSize()) {
            PageHeader(
                title = if (editMode) "مرتب‌سازی" else person.name,
                subtitle = if (editMode) "با دکمه‌های ▲▼ جابه‌جا کن" else "${accounts.size} حساب",
                onBackClick = onBack,
                extraActions = {
                    IconButton(onClick = { editMode = !editMode }, modifier = Modifier.size(48.dp)) {
                        Text(if (editMode) "✓" else "⇅", color = Color.White, fontSize = 24.sp)
                    }
                    if (!editMode) {
                        IconButton(onClick = { editPerson = true }, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Default.Edit, "ویرایش شخص", tint = Color.White, modifier = Modifier.size(24.dp))
                        }
                        Box(
                            Modifier
                                .size(48.dp)
                                .background(Color.White, shape = CircleShape)
                                .clickable { add = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("+", color = HeaderBlue, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            )

            Spacer(Modifier.height(16.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text(
                        "حساب‌ها",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1B1B1F),
                        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                    )
                }

                items(accounts.size, key = { accounts[it].id }) { index ->
                    val acc = accounts[index]
                    val bal = accountBalances.find { it.first.id == acc.id }?.second ?: 0L

                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        if (editMode) {
                            Column(Modifier.padding(end = 6.dp), verticalArrangement = Arrangement.Center) {
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
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Text("▲", fontSize = 16.sp, color = if (index > 0) HeaderBlue else Color.LightGray)
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
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Text("▼", fontSize = 16.sp, color = if (index < accounts.size - 1) HeaderBlue else Color.LightGray)
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

    if (editPerson) {
        PersonEditor(person, db, {
            scope.launch { db.persons().update(it); editPerson = false }
        }, { editPerson = false })
    }
}

// ═══════════════════════════════════════════════════════
// SwipeableAccountCard
// ═══════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeableAccountCard(
    account: Account,
    balance: Long,
    onClick: () -> Unit,
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
                    Box(
                        modifier = Modifier.size(44.dp).background(color, shape = CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icon, null, tint = Color.White, modifier = Modifier.size(24.dp))
                    }
                }
            }
        }
    ) {
        val isCredit = balance >= 0
        Card(
            Modifier.fillMaxWidth().clickable { onClick() },
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(46.dp)
                        .background(
                            color = if (isCredit) CreditGreen else DebitRed,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        account.displayUnit(),
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(account.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(money(kotlin.math.abs(balance)), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge, color = if (isCredit) CreditGreen else DebitRed)
                    Spacer(Modifier.height(2.dp))
                    Text(if (isCredit) "بستانکار" else "بدهکار", style = MaterialTheme.typography.labelSmall, color = if (isCredit) CreditGreen else DebitRed)
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
    val scope = rememberCoroutineScope()
    val bal = tx.sumOf { if (it.type == "بستانکار") it.amount else -it.amount }
    val isCredit = bal >= 0

    Box(Modifier.fillMaxSize().background(BgLight)) {
        Column(Modifier.fillMaxSize()) {
            PageHeader(
                title = a.name,
                subtitle = a.displayUnit(),
                onBackClick = onBack,
                extraActions = {
                    IconButton(onClick = { profit = true }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.Settings, "تنظیم سود", tint = Color.White, modifier = Modifier.size(24.dp))
                    }
                }
            )

            Card(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .offset(y = (-40).dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(money(kotlin.math.abs(bal)), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color(0xFF1B1B1F))
                        Spacer(Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(if (isCredit) "بستانکار" else "بدهکار", style = MaterialTheme.typography.bodyMedium, color = if (isCredit) CreditGreen else DebitRed, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.width(4.dp))
                            Text(a.displayUnit(), style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                        }
                    }
                    Box(
                        Modifier.size(56.dp).background(HeaderBlue, shape = CircleShape).clickable { add = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("+", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Light)
                    }
                }
            }

            val runningBalances = remember(tx) { calculateRunningBalances(tx) }

            LazyColumn(
                modifier = Modifier.fillMaxSize().offset(y = (-25).dp).padding(horizontal = 16.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text("تراکنش‌ها", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF1B1B1F), modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
                }
                items(tx, key = { it.id }) { t ->
                    val running = runningBalances[t.id] ?: 0L
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
    runningBalance: Long,
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
                    Text(Jalali.format(transaction.dateMillis), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    if (transaction.note.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(transaction.note, style = MaterialTheme.typography.bodySmall, color = Color(0xFF45464F))
                    }
                }
                Spacer(Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text("${if (isTxCredit) "+" else "−"}${money(transaction.amount)}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = if (isTxCredit) CreditGreen else DebitRed)
                    Spacer(Modifier.height(2.dp))
                    Text("مانده: ${money(kotlin.math.abs(runningBalance))}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                }
            }
        }
    }
}

@Composable
fun AutoTransactionCard(transaction: Transaction, currency: String, runningBalance: Long) {
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
                Text(Jalali.format(transaction.dateMillis), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                if (transaction.note.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(transaction.note, style = MaterialTheme.typography.bodySmall, color = Color(0xFF45464F))
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("+${money(transaction.amount)}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = CreditGreen)
                Spacer(Modifier.height(2.dp))
                Text("مانده: ${money(kotlin.math.abs(runningBalance))}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
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
        mutableStateOf(
            old?.displayedCurrencies?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet() ?: emptySet()
        )
    }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(if (old == null) "شخص جدید" else "ویرایش شخص") },
        text = {
            Column {
                OutlinedTextField(name, { name = it }, label = { Text("نام شخص") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(note, { note = it }, label = { Text("توضیحات (اختیاری)") }, modifier = Modifier.fillMaxWidth())

                if (old != null && personAccounts.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Text("حساب‌های نمایشی (حداکثر ۳ تا)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
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
                                }
                            )
                            Text(acc.name, style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.weight(1f))
                            Text(acc.displayUnit(), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (name.isNotBlank())
                    onSave(Person(old?.id ?: 0, name, note, old?.displayOrder ?: 0, selectedAccounts.joinToString(",")))
            }) { Text("ذخیره") }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("انصراف") } }
    )
}

// ═══════════════════════════════════════════════════════
// AccountEditor
// ═══════════════════════════════════════════════════════

@Composable
fun AccountEditor(old: Account?, personId: Long, onSave: (Account) -> Unit, onCancel: () -> Unit) {
    var name by remember(old) { mutableStateOf(old?.name ?: "") }
    var note by remember(old) { mutableStateOf(old?.note ?: "") }
    var currency by remember(old) { mutableStateOf(old?.currency ?: "تومان") }
    var customUnit by remember(old) { mutableStateOf(old?.customUnit ?: "") }
    var currencyExpanded by remember { mutableStateOf(false) }
    val currencies = listOf("ریال", "تومان", "دلار", "یورو", "پوند", "درهم")
    val hasCustomUnit = customUnit.isNotBlank()

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(if (old == null) "حساب جدید" else "ویرایش حساب") },
        text = {
            Column {
                OutlinedTextField(name, { name = it }, label = { Text("عنوان حساب") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(note, { note = it }, label = { Text("توضیحات (اختیاری)") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    customUnit, { customUnit = it },
                    label = { Text("واحد دلخواه (اختیاری)") },
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = { Text("اگه پر بشه، جایگزین ارز می‌شه") }
                )
                Spacer(Modifier.height(12.dp))
                Text("ارز", style = MaterialTheme.typography.titleSmall, color = if (hasCustomUnit) Color.Gray else Color(0xFF1B1B1F))
                Spacer(Modifier.height(4.dp))
                Box(Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { if (!hasCustomUnit) currencyExpanded = true },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !hasCustomUnit
                    ) {
                        Text(currency, modifier = Modifier.weight(1f))
                        Text("▼")
                    }
                    DropdownMenu(expanded = currencyExpanded, onDismissRequest = { currencyExpanded = false }) {
                        currencies.forEach { c ->
                            DropdownMenuItem(text = { Text(c) }, onClick = { currency = c; currencyExpanded = false })
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (name.isNotBlank())
                    onSave(Account(old?.id ?: 0, personId, name, note, currency, customUnit, old?.displayOrder ?: 0))
            }) { Text("ذخیره") }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("انصراف") } }
    )
}

// ═══════════════════════════════════════════════════════
// TxEditor
// ═══════════════════════════════════════════════════════

@Composable
fun TxEditor(old: Transaction?, accountId: Long, onSave: (Transaction) -> Unit, onCancel: () -> Unit) {
    var type by remember(old) { mutableStateOf(old?.type ?: "بدهکار") }
    var amountValue by remember(old) { mutableStateOf(TextFieldValue(text = old?.amount?.let { money(it) } ?: "")) }
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
                    amountValue, { amountValue = formatTextFieldValue(it) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    label = { Text("مبلغ") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(date, { date = it }, label = { Text("تاریخ شمسی 1405/02/31") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(note, { note = it }, label = { Text("شرح") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(onClick = {
                val n = parseSeparatedAmount(amountValue.text)
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
                } else Jalali.parseWithCurrentTime(date)
                if (n != null && n > 0 && d != null)
                    onSave(Transaction(old?.id ?: 0, accountId, d, type, n, note, false, null))
            }) { Text("ذخیره") }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("انصراف") } }
    )
}

// ═══════════════════════════════════════════════════════
// ProfitPeriodsScreen — لیست بازه‌های سود
// ═══════════════════════════════════════════════════════

@Composable
fun ProfitPeriodsScreen(
    db: AppDb,
    accountId: Long,  // 0 = کلی
    accountName: String,  // برای عنوان
    close: () -> Unit
) {
    val periods by db.profitPeriod().byAccount(accountId).collectAsState(emptyList())
    var addPeriod by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<ProfitPeriod?>(null) }
    var deleteTarget by remember { mutableStateOf<ProfitPeriod?>(null) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = close,
        title = {
            Text(
                if (accountId == 0L) "بازه‌های سود کلی" else "بازه‌های سود اختصاصی",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    if (accountId == 0L) "برای همه‌ی حساب‌هایی که سود کلی دارن اعمال می‌شه"
                    else "فقط برای حساب «$accountName» اعمال می‌شه",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                Spacer(Modifier.height(12.dp))

                Button(
                    onClick = { addPeriod = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("+ تعریف بازه‌ی سود جدید")
                }

                Spacer(Modifier.height(12.dp))

                if (periods.isEmpty()) {
                    Text(
                        "هنوز بازه‌ای تعریف نشده",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray,
                        modifier = Modifier.padding(vertical = 20.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 400.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(periods, key = { it.id }) { period ->
                            Card(
                                Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))
                            ) {
                                Row(
                                    Modifier.fillMaxWidth().padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            "از ${period.startYear}/${period.startMonth.toString().padStart(2, '0')}/${period.startDay.toString().padStart(2, '0')}" +
                                            if (period.endYear != null)
                                                " تا ${period.endYear}/${period.endMonth.toString().padStart(2, '0')}/${period.endDay.toString().padStart(2, '0')}"
                                            else " تا بی‌نهایت",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            "نوع: ${if (period.type == "ANNUAL") "سالانه" else "ماهانه"} | " +
                                            "نرخ: ${period.rate}% | " +
                                            "روز واریز: ${period.payoutDay}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.Gray
                                        )
                                    }
                                    IconButton(
                                        onClick = { editTarget = period },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.Edit, "ویرایش", modifier = Modifier.size(18.dp), tint = HeaderBlue)
                                    }
                                    IconButton(
                                        onClick = { deleteTarget = period },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, "حذف", modifier = Modifier.size(18.dp), tint = DebitRed)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = close) { Text("بستن") }
        }
    )

    if (addPeriod) {
        ProfitPeriodEditor(null, accountId, db, {
            scope.launch {
                db.profitPeriod().insert(it)
                ProfitEngine.recalculateAll(db)
                addPeriod = false
            }
        }, { addPeriod = false })
    }

    editTarget?.let { target ->
        ProfitPeriodEditor(target, accountId, db, {
            scope.launch {
                db.profitPeriod().update(it)
                ProfitEngine.recalculateAll(db)
                editTarget = null
            }
        }, { editTarget = null })
    }

    deleteTarget?.let { target ->
        ConfirmDeleteDialog(
            title = "حذف بازه",
            message = "آیا این بازه حذف شود؟",
            onConfirm = {
                scope.launch {
                    db.profitPeriod().delete(target)
                    ProfitEngine.recalculateAll(db)
                    deleteTarget = null
                }
            },
            onCancel = { deleteTarget = null }
        )
    }
}

// ═══════════════════════════════════════════════════════
// ProfitPeriodEditor — ویرایشگر یک بازه
// ═══════════════════════════════════════════════════════

@Composable
fun ProfitPeriodEditor(
    old: ProfitPeriod?,
    accountId: Long,
    db: AppDb,
    onSave: (ProfitPeriod) -> Unit,
    onCancel: () -> Unit
) {
    var type by remember(old) { mutableStateOf(old?.type ?: "ANNUAL") }
    var rate by remember(old) { mutableStateOf(old?.rate?.toString() ?: "20") }
    var startYear by remember(old) { mutableStateOf((old?.startYear ?: Jalali.nowJalali()[0]).toString()) }
    var startMonth by remember(old) { mutableStateOf((old?.startMonth ?: 1).toString()) }
    var startDay by remember(old) { mutableStateOf((old?.startDay ?: 1).toString()) }
    var hasEnd by remember(old) { mutableStateOf(old?.endYear != null) }
    var endYear by remember(old) { mutableStateOf(old?.endYear?.toString() ?: "") }
    var endMonth by remember(old) { mutableStateOf(old?.endMonth?.toString() ?: "") }
    var endDay by remember(old) { mutableStateOf(old?.endDay?.toString() ?: "") }
    var payoutDay by remember(old) { mutableStateOf((old?.payoutDay ?: 30).toString()) }

    var error by remember { mutableStateOf("") }

    val allAccounts by db.accounts().all().collectAsState(emptyList())
    var destAccountId by remember(old) { mutableStateOf(old?.destinationAccountId) }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(if (old == null) "بازه‌ی جدید" else "ویرایش بازه") },
        text = {
            Column {
                // نوع سود
                Text("نوع سود", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Row {
                    FilterChip(type == "ANNUAL", { type = "ANNUAL" }, label = { Text("سالانه") })
                    Spacer(Modifier.width(6.dp))
                    FilterChip(type == "MONTHLY", { type = "MONTHLY" }, label = { Text("ماهانه") })
                }
                Spacer(Modifier.height(8.dp))

                // نرخ
                OutlinedTextField(
                    rate, { input ->
                        val cleaned = input.filter { it.isDigit() || it == '.' }
                        if (cleaned.count { it == '.' } <= 1) rate = cleaned
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    label = { Text(if (type == "ANNUAL") "نرخ سالانه ٪" else "نرخ ماهانه ٪") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))

                // تاریخ شروع
                Text("از تاریخ", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedTextField(startYear, { startYear = it.filter { c -> c.isDigit() } },
                        label = { Text("سال") }, modifier = Modifier.weight(1.2f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    OutlinedTextField(startMonth, { startMonth = it.filter { c -> c.isDigit() } },
                        label = { Text("ماه") }, modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    OutlinedTextField(startDay, { startDay = it.filter { c -> c.isDigit() } },
                        label = { Text("روز") }, modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                }
                Spacer(Modifier.height(8.dp))

                // تاریخ پایان
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = hasEnd, onCheckedChange = { hasEnd = !hasEnd })
                    Text("تاریخ پایان دارد")
                }
                if (hasEnd) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedTextField(endYear, { endYear = it.filter { c -> c.isDigit() } },
                            label = { Text("سال") }, modifier = Modifier.weight(1.2f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        OutlinedTextField(endMonth, { endMonth = it.filter { c -> c.isDigit() } },
                            label = { Text("ماه") }, modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        OutlinedTextField(endDay, { endDay = it.filter { c -> c.isDigit() } },
                            label = { Text("روز") }, modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    }
                }
                Spacer(Modifier.height(8.dp))

                // روز واریز
                OutlinedTextField(
                    payoutDay, { payoutDay = it.filter { c -> c.isDigit() } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    label = { Text("روز واریز ماه") },
                    modifier = Modifier.fillMaxWidth()
                )

                // حساب مقصد (فقط برای سود اختصاصی)
                if (accountId != 0L) {
                    Spacer(Modifier.height(8.dp))
                    Text("حساب مقصد سود", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = destAccountId == null, onClick = { destAccountId = null })
                        Text("همین حساب")
                    }
                    allAccounts.filter { it.id != accountId }.forEach { acc ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = destAccountId == acc.id, onClick = { destAccountId = acc.id })
                            Text(acc.name)
                        }
                    }
                }

                if (error.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(error, color = DebitRed, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val r = rate.toDoubleOrNull() ?: 0.0
                val sy = startYear.toIntOrNull() ?: 0
                val sm = startMonth.toIntOrNull() ?: 0
                val sd = startDay.toIntOrNull() ?: 0
                val pd = payoutDay.toIntOrNull()?.coerceIn(1, 31) ?: 30

                if (r <= 0) { error = "نرخ باید بزرگتر از صفر باشد"; return@Button }
                if (sm !in 1..12) { error = "ماه باید بین ۱ تا ۱۲ باشد"; return@Button }
                if (sd !in 1..31) { error = "روز باید بین ۱ تا ۳۱ باشد"; return@Button }

                var ey: Int? = null
                var em: Int? = null
                var ed: Int? = null
                if (hasEnd) {
                    ey = endYear.toIntOrNull() ?: 0
                    em = endMonth.toIntOrNull() ?: 0
                    ed = endDay.toIntOrNull() ?: 0
                    if (em !in 1..12) { error = "ماه پایان باید بین ۱ تا ۱۲ باشد"; return@Button }
                    if (ed !in 1..31) { error = "روز پایان باید بین ۱ تا ۳۱ باشد"; return@Button }
                    if (compareJalali(ey, em, ed, sy, sm, sd) <= 0) {
                        error = "تاریخ پایان باید بعد از تاریخ شروع باشد"
                        return@Button
                    }
                }

                onSave(ProfitPeriod(
                    id = old?.id ?: 0,
                    accountId = accountId,
                    type = type,
                    rate = r,
                    startYear = sy, startMonth = sm, startDay = sd,
                    endYear = ey, endMonth = em, endDay = ed,
                    payoutDay = pd,
                    destinationAccountId = destAccountId
                ))
            }) { Text("ذخیره") }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("انصراف") } }
    )
}

// ═══════════════════════════════════════════════════════
// SettingsScreen
// ═══════════════════════════════════════════════════════

@Composable
fun SettingsScreen(db: AppDb, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch { Backup.restoreOrExport(context, db, uri, false) }
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch { Backup.restoreOrExport(context, db, uri, true) }
    }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            } catch (e: Exception) { e.printStackTrace() }
            saveBackupFolderUri(context, uri)
        }
    }

    var autoBackupExists by remember { mutableStateOf(false) }
    var autoBackupInfo by remember { mutableStateOf("") }
    var confirmRestore by remember { mutableStateOf(false) }
    var confirmRemoveFolder by remember { mutableStateOf(false) }
    var customFolderName by remember { mutableStateOf<String?>(null) }
    var globalProfitOpen by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val file = java.io.File(context.filesDir, "auto_backup.json")
        autoBackupExists = file.exists()
        if (file.exists()) {
            val dateFormat = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US)
            autoBackupInfo = dateFormat.format(Date(file.lastModified()))
        }
        val customUri = getSavedBackupFolderUri(context)
        if (customUri != null) {
            val folder = DocumentFile.fromTreeUri(context, customUri)
            customFolderName = folder?.name ?: "پوشه انتخاب شده"
        }
    }

    Column(Modifier.fillMaxSize().background(BgLight)) {
        PageHeader(title = "تنظیمات", subtitle = "مدیریت سود و بکاپ", onBackClick = onBack)

        Spacer(Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text("سود کلی برنامه", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF1B1B1F), modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
            }

            item {
                Card(
                    Modifier.fillMaxWidth().clickable { globalProfitOpen = true },
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(40.dp).background(HeaderBlue.copy(alpha = 0.1f), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                                Text("💰", fontSize = 18.sp, color = HeaderBlue)
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text("مدیریت بازه‌های سود کلی", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                                Text("تعریف، ویرایش و حذف بازه‌ها", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            }
                            Text("‹", fontSize = 20.sp, color = Color.Gray)
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(8.dp))
                Text("پشتیبان و بازیابی", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF1B1B1F), modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
            }

            item {
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        SettingRow("گرفتن بکاپ", "ذخیره در حافظه یا Google Drive", "⬇", onClick = { create.launch("MaliManager-backup.json") })
                        HorizontalDivider(color = Color(0xFFEEEEEE))
                        SettingRow("بازیابی بکاپ", "از فایل JSON", "⬆", onClick = { open.launch(arrayOf("application/json", "text/*")) })
                    }
                }
            }

            item {
                Spacer(Modifier.height(8.dp))
                Text("بکاپ خودکار (هر ۵ ثانیه)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF1B1B1F), modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
            }

            item {
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        if (autoBackupExists) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(36.dp).background(CreditGreen.copy(alpha = 0.15f), CircleShape), contentAlignment = Alignment.Center) {
                                    Text("✓", color = CreditGreen, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("بکاپ خودکار فعال", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                                    Text("آخرین: $autoBackupInfo", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            OutlinedButton(onClick = { confirmRestore = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                                Text("بازیابی از بکاپ خودکار")
                            }
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(36.dp).background(Color(0xFFFFE0B2), CircleShape), contentAlignment = Alignment.Center) {
                                    Text("!", color = Color(0xFFE65100), fontWeight = FontWeight.Bold, fontSize = 20.sp)
                                }
                                Spacer(Modifier.width(12.dp))
                                Text("هنوز بکاپ خودکاری ذخیره نشده", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                            }
                        }
                    }
                }
            }

            item {
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("محل ذخیره بکاپ خودکار", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (customFolderName != null) "پوشه فعلی: $customFolderName" else "پیش‌فرض: حافظه داخلی برنامه",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (customFolderName != null) CreditGreen else Color.Gray
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { pickFolder.launch(null) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                            Text(if (customFolderName != null) "تغییر پوشه" else "انتخاب پوشه")
                        }
                        if (customFolderName != null) {
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = { confirmRemoveFolder = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = DebitRed)
                            ) { Text("حذف پوشه و برگشت به پیش‌فرض") }
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(8.dp))
                Text("درباره", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF1B1B1F), modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
            }

            item {
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(48.dp).background(HeaderBlue, shape = RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                                Text("💰", fontSize = 24.sp)
                            }
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text("مدیریت مالی", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("نسخه ۱.۰", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        HorizontalDivider(color = Color(0xFFEEEEEE))
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("👤", fontSize = 20.sp)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("سازنده", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                Text("sdamir66", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }
        }
    }

    if (globalProfitOpen) {
        ProfitPeriodsScreen(db, 0L, "سود کلی") { globalProfitOpen = false }
    }

    if (confirmRestore) {
        ConfirmDeleteDialog(
            title = "بازیابی از بکاپ خودکار",
            message = "تمام داده‌های فعلی با بکاپ جایگزین می‌شوند. مطمئنی؟",
            onConfirm = {
                scope.launch {
                    val file = java.io.File(context.filesDir, "auto_backup.json")
                    if (file.exists()) Backup.restoreOrExport(context, db, Uri.fromFile(file), true)
                    confirmRestore = false
                }
            },
            onCancel = { confirmRestore = false }
        )
    }

    if (confirmRemoveFolder) {
        AlertDialog(
            onDismissRequest = { confirmRemoveFolder = false },
            title = { Text("حذف پوشه بکاپ", fontWeight = FontWeight.Bold) },
            text = { Text("بکاپ‌های بعدی در حافظه داخلی برنامه ذخیره می‌شوند.") },
            confirmButton = {
                Button(
                    onClick = { clearBackupFolderUri(context); customFolderName = null; confirmRemoveFolder = false },
                    colors = ButtonDefaults.buttonColors(containerColor = DebitRed)
                ) { Text("حذف") }
            },
            dismissButton = { TextButton(onClick = { confirmRemoveFolder = false }) { Text("انصراف") } }
        )
    }
}

// ═══════════════════════════════════════════════════════
// کامپوننت‌های کمکی
// ═══════════════════════════════════════════════════════

@Composable
fun ConfirmDeleteDialog(title: String, message: String, onConfirm: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = { Text(message) },
        confirmButton = {
            Button(onClick = onConfirm, colors = ButtonDefaults.buttonColors(containerColor = DebitRed)) { Text("حذف") }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("انصراف") } }
    )
}

@Composable
fun SettingRow(title: String, subtitle: String, icon: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).background(HeaderBlue.copy(alpha = 0.1f), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
            Text(icon, fontSize = 18.sp, color = HeaderBlue)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
        Text("‹", fontSize = 20.sp, color = Color.Gray)
    }
}

// ═══════════════════════════════════════════════════════
// ProfitEngine — محاسبه‌ی سود بر اساس بازه‌ها
// ═══════════════════════════════════════════════════════

object ProfitEngine {
    suspend fun recalculateAll(db: AppDb) {
        db.tx().deleteAllAuto()

        for (account in db.accounts().allNow()) {
            // بازه‌های سود کلی
            val globalPeriods = db.profitPeriod().byAccountNow(0L)
            // بازه‌های سود اختصاصی
            val customPeriods = db.profitPeriod().byAccountNow(account.id)

            // ترکیب: اختصاصی ارجحیت داره (اگه وجود داشت)
            val periodsToUse = if (customPeriods.isNotEmpty()) customPeriods else globalPeriods

            if (periodsToUse.isEmpty()) continue

            recalculateForAccount(db, account.id, periodsToUse)
        }
    }

    suspend fun recalculateForAccount(db: AppDb, accountId: Long, periods: List<ProfitPeriod>) {
        val base = db.tx().byAccountNow(accountId).filter { !it.isAutoProfit }
        val today = Jalali.nowJalali()
        val todayMillis = System.currentTimeMillis()

        // ✅ پاک کردن سودهای خودکار قبلی این حساب
        db.tx().deleteAutoByPrefix(accountId.toString())

        val out = mutableListOf<Transaction>()

        // ✅ برای هر بازه، روزهای داخل بازه رو محاسبه کن
        for (period in periods) {
            // تاریخ شروع و پایان (میلی‌ثانیه)
            val periodStart = toMillis(period.startYear, period.startMonth, period.startDay)

            val periodEnd = if (period.endYear != null && period.endMonth != null && period.endDay != null) {
                toMillis(period.endYear, period.endMonth, period.endDay)
            } else {
                // بی‌نهایت = امروز
                todayMillis
            }

            // ✅ تاریخ پرداخت (روز واریز ماه) برای هر ماه
            // از ماه شروع تا ماه امروز
            var y = period.startYear
            var m = period.startMonth

            while (true) {
                // چک کن که تاریخ پرداخت این ماه بعد از شروع بازه باشه
                val daysInMonth = Jalali.daysInMonth(y, m)
                val payoutDayClamped = period.payoutDay.coerceIn(1, daysInMonth)
                val payoutMillis = toMillis(y, m, payoutDayClamped)

                // اگه از بازه بیرون باشه، رد کن
                if (payoutMillis > periodEnd) break
                if (payoutMillis > todayMillis) break

                // اگه قبل از شروع بازه باشه، برو ماه بعد
                if (payoutMillis < periodStart) {
                    // برو ماه بعد
                    m++
                    if (m > 12) { m = 1; y++ }
                    continue
                }

                // ✅ محاسبه‌ی سود برای این ماه
                val amount = calculateMonthlyProfit(
                    base = base,
                    periodStart = periodStart,
                    payoutMillis = payoutMillis,
                    y = y,
                    m = m,
                    type = period.type,
                    rate = period.rate
                )

                if (amount > 0) {
                    val dest = period.destinationAccountId ?: accountId
                    val text = if (period.type == "ANNUAL")
                        "سود سالانه ${Jalali.monthName(m)} $y — نرخ ${period.rate}%"
                    else
                        "سود ماهانه ${Jalali.monthName(m)} $y — نرخ ${period.rate}%"

                    out.add(Transaction(0, dest, payoutMillis, "بستانکار", amount, text, true, "$accountId:$y:$m"))
                }

                // برو ماه بعد
                m++
                if (m > 12) { m = 1; y++ }

                // چک کن از امروز رد نشده باشه
                if (y > today[0] || (y == today[0] && m > today[1])) break
            }
        }

        db.tx().insertAll(out)
    }

    /**
     * محاسبه‌ی سود یک ماه
     * @param periodStart تاریخ شروعی که کاربر تعیین کرده (میلی‌ثانیه)
     * @param payoutMillis تاریخ پرداخت (میلی‌ثانیه)
     * @param y سال شمسی
     * @param m ماه شمسی
     * @param type ANNUAL یا MONTHLY
     * @param rate نرخ سود
     */
    private fun calculateMonthlyProfit(
        base: List<Transaction>,
        periodStart: Long,
        payoutMillis: Long,
        y: Int,
        m: Int,
        type: String,
        rate: Double
    ): Long {
        val monthStart = Jalali.startOfJalaliMonth(y, m)
        val monthEnd = payoutMillis

        // اگه ماه کاملاً قبل از periodStart باشه → سود صفر
        if (monthEnd <= periodStart) return 0L

        // روز شروع محاسبه (بیشترین از ماه یا periodStart)
        val effectiveStart = if (monthStart > periodStart) monthStart else periodStart

        // تعداد روز محاسبه
        val daysToCalc = ((monthEnd - effectiveStart) / 86400000L).toInt() + 1
        if (daysToCalc <= 0) return 0L

        // روزهای ماه (برای تقسیم)
        val daysInMonth = if (type == "MONTHLY") {
            Jalali.daysInMonth(y, m)
        } else {
            365
        }

        // ✅ برای هر روز، مانده حساب رو در اون روز محاسبه کن
        var total = 0.0
        for (d in 0 until daysToCalc) {
            val dayMillis = effectiveStart + (d * 86400000L)
            val bal = base.filter { it.dateMillis <= dayMillis }
                .sumOf { if (it.type == "بستانکار") it.amount else -it.amount }
            total += bal * (rate / 100.0) / daysInMonth
        }

        return kotlin.math.round(total).toLong()
    }
}

// ═══════════════════════════════════════════════════════
// Backup — با بازه‌های سود
// ═══════════════════════════════════════════════════════

object Backup {

    suspend fun exportToJson(db: AppDb): String {
        val root = JSONObject()
        fun arr() = JSONArray()

        val pp = arr()
        for (p in db.persons().allNow()) {
            val o = JSONObject()
            o.put("id", p.id); o.put("name", p.name); o.put("note", p.note)
            o.put("displayOrder", p.displayOrder)
            o.put("displayedCurrencies", p.displayedCurrencies)
            pp.put(o)
        }
        root.put("persons", pp)

        val aa = arr()
        for (a in db.accounts().allNow()) {
            val o = JSONObject()
            o.put("id", a.id); o.put("personId", a.personId); o.put("name", a.name)
            o.put("note", a.note); o.put("currency", a.currency)
            o.put("customUnit", a.customUnit); o.put("displayOrder", a.displayOrder)
            aa.put(o)
        }
        root.put("accounts", aa)

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

        val pp2 = arr()
        for (p in db.profitPeriod().allNow()) {
            val o = JSONObject()
            o.put("id", p.id); o.put("accountId", p.accountId)
            o.put("type", p.type); o.put("rate", p.rate)
            o.put("startYear", p.startYear); o.put("startMonth", p.startMonth); o.put("startDay", p.startDay)
            o.put("endYear", p.endYear ?: JSONObject.NULL)
            o.put("endMonth", p.endMonth ?: JSONObject.NULL)
            o.put("endDay", p.endDay ?: JSONObject.NULL)
            o.put("payoutDay", p.payoutDay)
            o.put("destinationAccountId", p.destinationAccountId ?: JSONObject.NULL)
            pp2.put(o)
        }
        root.put("profitPeriods", pp2)

        return root.toString(2)
    }

    suspend fun restoreOrExport(context: Context, db: AppDb, uri: Uri, restore: Boolean) {
        if (restore) {
            val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: return
            val root = JSONObject(text)
            db.tx().clear(); db.accounts().clear(); db.persons().clear(); db.profitPeriod().clear()

            val persons = root.optJSONArray("persons") ?: JSONArray()
            val personIdMap = mutableMapOf<Long, Long>()
            for (i in 0 until persons.length()) {
                val o = persons.getJSONObject(i)
                val old = o.optLong("id", 0)
                val id = db.persons().insert(Person(
                    name = o.getString("name"),
                    note = o.optString("note"),
                    displayOrder = o.optInt("displayOrder", 0),
                    displayedCurrencies = o.optString("displayedCurrencies", "")
                ))
                personIdMap[old] = id
            }

            val accounts = root.optJSONArray("accounts") ?: JSONArray()
            val accountIdMap = mutableMapOf<Long, Long>()
            for (i in 0 until accounts.length()) {
                val o = accounts.getJSONObject(i)
                val old = o.optLong("id", 0)
                val newPid = personIdMap[o.optLong("personId")] ?: continue
                val id = db.accounts().insert(Account(
                    personId = newPid,
                    name = o.getString("name"),
                    note = o.optString("note"),
                    currency = o.optString("currency", "تومان"),
                    customUnit = o.optString("customUnit", ""),
                    displayOrder = o.optInt("displayOrder", 0)
                ))
                accountIdMap[old] = id
            }

            val tx = root.optJSONArray("transactions") ?: JSONArray()
            for (i in 0 until tx.length()) {
                val o = tx.getJSONObject(i)
                val aid = accountIdMap[o.optLong("accountId")] ?: continue
                db.tx().insert(Transaction(
                    accountId = aid, dateMillis = o.getLong("dateMillis"),
                    type = o.getString("type"), amount = o.getLong("amount"),
                    note = o.optString("note"),
                    isAutoProfit = o.optBoolean("isAutoProfit", false),
                    profitKey = o.optString("profitKey").ifBlank { null }
                ))
            }

            val periods = root.optJSONArray("profitPeriods") ?: JSONArray()
            for (i in 0 until periods.length()) {
                val o = periods.getJSONObject(i)
                val aid = o.optLong("accountId", 0)
                val newAid = if (aid == 0L) 0L else accountIdMap[aid] ?: continue
                val destRaw = o.optLong("destinationAccountId", 0)
                val newDest = if (o.isNull("destinationAccountId")) null
                else accountIdMap[destRaw]
                db.profitPeriod().insert(ProfitPeriod(
                    accountId = newAid,
                    type = o.optString("type", "ANNUAL"),
                    rate = o.optDouble("rate"),
                    startYear = o.getInt("startYear"),
                    startMonth = o.getInt("startMonth"),
                    startDay = o.getInt("startDay"),
                    endYear = if (o.isNull("endYear")) null else o.getInt("endYear"),
                    endMonth = if (o.isNull("endMonth")) null else o.getInt("endMonth"),
                    endDay = if (o.isNull("endDay")) null else o.getInt("endDay"),
                    payoutDay = o.optInt("payoutDay", 30),
                    destinationAccountId = newDest
                ))
            }
        } else {
            val json = exportToJson(db)
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(json) }
        }
    }
}
