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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
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
import androidx.compose.ui.text.style.TextAlign
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
        input.copy(text = formatted, selection = androidx.compose.ui.text.TextRange(formatted.length))
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

private fun toMillis(y: Int, m: Int, d: Int): Long =
    Jalali.parse("%04d/%02d/%02d".format(Locale.US, y, m, d)) ?: 0L

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
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 36.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBackClick != null) {
                IconButton(onClick = onBackClick, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Default.ArrowBack, "بازگشت", tint = Color.White, modifier = Modifier.size(24.dp))
                }
                Spacer(Modifier.width(4.dp))
            }

            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
                if (subtitle.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.85f))
                }
            }

            if (extraActions != null) extraActions()
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
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 36.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
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
                    IconButton(onClick = onOpenSettings, modifier = Modifier.size(44.dp)) {
                        Icon(Icons.Default.Settings, "تنظیمات", tint = Color.White, modifier = Modifier.size(24.dp))
                    }
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
                    Text(person.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("${accounts.size} حساب", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                }

                Spacer(Modifier.height(6.dp))

                if (filteredAccounts.isEmpty()) {
                    Text("حسابی برای نمایش نیست", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                } else {
                    filteredAccounts.forEach { (acc, bal) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
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
                        Icon(Icons.Default.ArrowBack, "بازگشت", tint = Color.White, modifier = Modifier.size(24.dp))
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
                        IconButton(onClick = { editPerson = true }, modifier = Modifier.size(44.dp)) {
                            Icon(Icons.Default.Edit, "ویرایش شخص", tint = Color.White, modifier = Modifier.size(22.dp))
                        }
                        Spacer(Modifier.width(4.dp))
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
                    val bal = accountBalances.find { it.first.id == acc.id }?.second ?: 0L

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
                    Box(modifier = Modifier.size(44.dp).background(color, shape = CircleShape), contentAlignment = Alignment.Center) {
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
            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(46.dp)
                        .background(color = if (isCredit) CreditGreen else DebitRed, shape = CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(account.displayUnit(), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                        Text(money(kotlin.math.abs(bal)), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color(0xFF1B1B1F))
                        Spacer(Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(if (isCredit) "بستانکار" else "بدهکار", style = MaterialTheme.typography.bodyMedium, color = if (isCredit) CreditGreen else DebitRed, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.width(4.dp))
                            Text(a.displayUnit(), style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
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
// JalaliCalendarDialog
// ═══════════════════════════════════════════════════════

@Composable
fun JalaliCalendarDialog(
    initialYear: Int,
    initialMonth: Int,
    initialDay: Int,
    onSelect: (year: Int, month: Int, day: Int) -> Unit,
    onCancel: () -> Unit
) {
    var year by remember { mutableIntStateOf(initialYear) }
    var month by remember { mutableIntStateOf(initialMonth) }
    var day by remember { mutableIntStateOf(initialDay) }

    val daysInMonth = Jalali.daysInMonth(year, month)
    val firstDayMillis = Jalali.parse("%04d/%02d/%02d".format(Locale.US, year, month, 1)) ?: 0L

    val firstDayOfWeek = remember(firstDayMillis) {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = firstDayMillis }
        when (cal.get(java.util.Calendar.DAY_OF_WEEK)) {
            java.util.Calendar.SATURDAY -> 0
            java.util.Calendar.SUNDAY -> 1
            java.util.Calendar.MONDAY -> 2
            java.util.Calendar.TUESDAY -> 3
            java.util.Calendar.WEDNESDAY -> 4
            java.util.Calendar.THURSDAY -> 5
            java.util.Calendar.FRIDAY -> 6
            else -> 0
        }
    }

    val monthNames = listOf(
        "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
        "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"
    )

    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = HeaderBlue,
        shape = RoundedCornerShape(20.dp),
        title = {
            Column {
                Text("انتخاب تاریخ", fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(Modifier.height(4.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        if (month == 1) { month = 12; year-- } else month--
                        day = 1
                    }) {
                        Text("‹", fontSize = 24.sp, color = Color.White)
                    }
                    Text(
                        "${monthNames[month - 1]} $year",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    IconButton(onClick = {
                        if (month == 12) { month = 1; year++ } else month++
                        day = 1
                    }) {
                        Text("›", fontSize = 24.sp, color = Color.White)
                    }
                }
            }
        },
        text = {
            Column {
                Row(Modifier.fillMaxWidth()) {
                    listOf("ش", "ی", "د", "س", "چ", "پ", "ج").forEach { d ->
                        Text(
                            d,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))

                val totalCells = firstDayOfWeek + daysInMonth
                val rows = (totalCells + 6) / 7

                for (r in 0 until rows) {
                    Row(Modifier.fillMaxWidth()) {
                        for (c in 0 until 7) {
                            val cellIndex = r * 7 + c
                            val dayNumber = cellIndex - firstDayOfWeek + 1
                            if (dayNumber in 1..daysInMonth) {
                                val isSelected = dayNumber == day
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .padding(2.dp)
                                        .clip(CircleShape)
                                        .background(if (isSelected) Color.White else Color.Transparent)
                                        .clickable { day = dayNumber },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        dayNumber.toString(),
                                        color = if (isSelected) HeaderBlue else Color.White,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 13.sp
                                    )
                                }
                            } else {
                                Box(Modifier.weight(1f).aspectRatio(1f))
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Text("سال:", style = MaterialTheme.typography.bodySmall, color = Color.White)
                    Spacer(Modifier.width(4.dp))
                    IconButton(onClick = { year -= 1 }, modifier = Modifier.size(32.dp)) {
                        Text("−", fontSize = 20.sp, color = Color.White)
                    }
                    Text(year.toString(), fontWeight = FontWeight.Bold, color = Color.White)
                    IconButton(onClick = { year += 1 }, modifier = Modifier.size(32.dp)) {
                        Text("+", fontSize = 20.sp, color = Color.White)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSelect(year, month, day) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = HeaderBlue
                )
            ) { Text("تأیید", fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text("انصراف", color = Color.White) }
        }
    )
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
                        onSave(Person(old?.id ?: 0, name, note, old?.displayOrder ?: 0, selectedAccounts.joinToString(",")))
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
    var currency by remember(old) { mutableStateOf(old?.currency ?: "تومان") }
    var customUnit by remember(old) { mutableStateOf(old?.customUnit ?: "") }
    var currencyExpanded by remember { mutableStateOf(false) }
    val currencies = listOf("ریال", "تومان", "دلار", "یورو", "پوند", "درهم")
    val hasCustomUnit = customUnit.isNotBlank()

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
            Column {
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
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank())
                        onSave(Account(old?.id ?: 0, personId, name, note, currency, customUnit, old?.displayOrder ?: 0))
                },
                colors = ButtonDefaults.buttonColors(containerColor = HeaderBlue, contentColor = Color.White)
            ) { Text("ذخیره", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("انصراف", color = HeaderBlue) } }
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
    var dateMillis by remember(old) {
        mutableStateOf(old?.dateMillis ?: System.currentTimeMillis())
    }
    var showCalendar by remember { mutableStateOf(false) }

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
        title = { Text(if (old == null) "ثبت تراکنش" else "ویرایش تراکنش", fontWeight = FontWeight.Bold, color = HeaderBlue) },
        text = {
            Column {
                Row {
                    FilterChip(
                        selected = type == "بدهکار",
                        onClick = { type = "بدهکار" },
                        label = { Text("بدهکار") },
                        colors = FilterChipDefaults.filterChipColors(
                            labelColor = labelColor,
                            selectedLabelColor = Color.White,
                            selectedContainerColor = HeaderBlue,
                            containerColor = Color(0xFFF0F1F7)
                        )
                    )
                    Spacer(Modifier.width(8.dp))
                    FilterChip(
                        selected = type == "بستانکار",
                        onClick = { type = "بستانکار" },
                        label = { Text("بستانکار") },
                        colors = FilterChipDefaults.filterChipColors(
                            labelColor = labelColor,
                            selectedLabelColor = Color.White,
                            selectedContainerColor = HeaderBlue,
                            containerColor = Color(0xFFF0F1F7)
                        )
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    amountValue, { amountValue = formatTextFieldValue(it) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    label = { Text("مبلغ") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { showCalendar = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = HeaderBlue)
                ) {
                    Icon(Icons.Default.DateRange, null, modifier = Modifier.size(18.dp), tint = HeaderBlue)
                    Spacer(Modifier.width(8.dp))
                    Text(Jalali.format(dateMillis), color = HeaderBlue)
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(note, { note = it }, label = { Text("شرح") }, modifier = Modifier.fillMaxWidth(), colors = fieldColors)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val n = parseSeparatedAmount(amountValue.text)
                    if (n != null && n > 0)
                        onSave(Transaction(old?.id ?: 0, accountId, dateMillis, type, n, note, false, null))
                },
                colors = ButtonDefaults.buttonColors(containerColor = HeaderBlue, contentColor = Color.White)
            ) { Text("ذخیره", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("انصراف", color = HeaderBlue) } }
    )

    if (showCalendar) {
        val j = Jalali.nowJalali()
        JalaliCalendarDialog(
            initialYear = j[0],
            initialMonth = j[1],
            initialDay = j[2],
            onSelect = { y, m, d ->
                dateMillis = toMillis(y, m, d)
                showCalendar = false
            },
            onCancel = { showCalendar = false }
        )
    }
}

// ═══════════════════════════════════════════════════════
// ProfitPeriodsScreen
// ═══════════════════════════════════════════════════════

@Composable
fun ProfitPeriodsScreen(db: AppDb, accountId: Long, accountName: String, close: () -> Unit) {
    val periods by db.profitPeriod().byAccount(accountId).collectAsState(emptyList())
    var addPeriod by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<ProfitPeriod?>(null) }
    var deleteTarget by remember { mutableStateOf<ProfitPeriod?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(periods) {
        if (periods.isNotEmpty()) {
            ProfitEngine.recalculateForAccount(db, accountId, periods)
        }
    }

    AlertDialog(
        onDismissRequest = close,
        containerColor = Color.White,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.border(2.dp, HeaderBlue, RoundedCornerShape(20.dp)),
        title = {
            Text(
                "بازه‌های سود — $accountName",
                fontWeight = FontWeight.Bold,
                color = HeaderBlue,
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column {
                Text(
                    "هر بازه یه نرخ سود برای یه دوره‌ست.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF5C5D72)
                )
                Spacer(Modifier.height(12.dp))

                Button(
                    onClick = { addPeriod = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = HeaderBlue, contentColor = Color.White)
                ) { Text("+ تعریف بازه‌ی سود", fontWeight = FontWeight.Bold) }

                Spacer(Modifier.height(12.dp))

                if (periods.isEmpty()) {
                    Text(
                        "هنوز بازه‌ای تعریف نشده",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF5C5D72),
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
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F1F7)),
                                border = BorderStroke(1.dp, HeaderBlue.copy(alpha = 0.3f))
                            ) {
                                Row(
                                    Modifier.fillMaxWidth().padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            "از ${period.startYear}/${period.startMonth.toString().padStart(2, '0')}/${period.startDay.toString().padStart(2, '0')}" +
                                            if (period.endYear != null)
                                                " تا ${period.endYear}/${period.endMonth?.toString()?.padStart(2, '0')}/${period.endDay?.toString()?.padStart(2, '0')}"
                                            else " تا بی‌نهایت",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = HeaderBlue
                                        )
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            "نوع: ${if (period.type == "ANNUAL") "سالانه" else "ماهانه"} | " +
                                            "نرخ: ${period.rate}% | " +
                                            "واریز: ${if (period.payoutDay == 0) "آخر ماه" else "روز ${period.payoutDay}"}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color(0xFF5C5D72)
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
            TextButton(onClick = close) {
                Text("بستن", color = HeaderBlue, fontWeight = FontWeight.Bold)
            }
        }
    )

    if (addPeriod) {
        ProfitPeriodEditor(null, accountId, db, {
            scope.launch {
                db.profitPeriod().insert(it)
                addPeriod = false
            }
        }, { addPeriod = false })
    }

    editTarget?.let { target ->
        ProfitPeriodEditor(target, accountId, db, {
            scope.launch {
                db.profitPeriod().update(it)
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
                    deleteTarget = null
                }
            },
            onCancel = { deleteTarget = null }
        )
    }
}

// ═══════════════════════════════════════════════════════
// ProfitPeriodEditor — با payoutDay خالی پیش‌فرض
// ═══════════════════════════════════════════════════════

@Composable
fun ProfitPeriodEditor(
    old: ProfitPeriod?,
    accountId: Long,
    db: AppDb,
    onSave: (ProfitPeriod) -> Unit,
    onCancel: () -> Unit
) {
    val today = Jalali.nowJalali()
    val scope = rememberCoroutineScope()

    var type by remember(old) { mutableStateOf(old?.type ?: "ANNUAL") }
    var rate by remember(old) { mutableStateOf(old?.rate?.toString() ?: "20") }
    var startY by remember(old) { mutableIntStateOf(old?.startYear ?: today[0]) }
    var startM by remember(old) { mutableIntStateOf(old?.startMonth ?: today[1]) }
    var startD by remember(old) { mutableIntStateOf(old?.startDay ?: today[2]) }
    var endY by remember(old) { mutableStateOf(old?.endYear) }
    var endM by remember(old) { mutableStateOf(old?.endMonth) }
    var endD by remember(old) { mutableStateOf(old?.endDay) }
    var isLastDayOfMonth by remember(old) { mutableStateOf(old?.payoutDay == 0 && old != null) }
    var payoutDay by remember(old) { mutableIntStateOf(old?.payoutDay ?: 0) }
    var payoutDayText by remember(old) { mutableStateOf(if (old?.payoutDay != null && old.payoutDay > 0) old.payoutDay.toString() else "") }
    var destinationAccountId by remember(old) { mutableStateOf(old?.destinationAccountId) }

    var showStartCalendar by remember { mutableStateOf(false) }
    var showEndCalendar by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    val allAccounts by db.accounts().all().collectAsState(emptyList())

    val textColor = Color(0xFF1B1B1F)
    val labelColor = Color(0xFF5C5D72)

    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = Color.White,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.border(2.dp, HeaderBlue, RoundedCornerShape(20.dp)),
        title = {
            Text(
                if (old == null) "بازه‌ی جدید" else "ویرایش بازه",
                fontWeight = FontWeight.Bold,
                color = HeaderBlue,
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

                Column {
                    Text("نوع سود", style = MaterialTheme.typography.labelLarge, color = labelColor, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(6.dp))
                    Row {
                        FilterChip(
                            selected = type == "ANNUAL",
                            onClick = {
                                type = "ANNUAL"
                                if (old == null) {
                                    startY = today[0]; startM = today[1]; startD = today[2]
                                    endY = null; endM = null; endD = null
                                }
                            },
                            label = { Text("سالانه") },
                            colors = FilterChipDefaults.filterChipColors(
                                labelColor = labelColor,
                                selectedLabelColor = Color.White,
                                selectedContainerColor = HeaderBlue,
                                containerColor = Color(0xFFF0F1F7)
                            )
                        )
                        Spacer(Modifier.width(8.dp))
                        FilterChip(
                            selected = type == "MONTHLY",
                            onClick = {
                                type = "MONTHLY"
                                if (old == null) {
                                    startY = today[0]; startM = today[1]; startD = 1
                                    endY = today[0]; endM = today[1]
                                    endD = Jalali.daysInMonth(today[0], today[1])
                                }
                            },
                            label = { Text("ماهانه") },
                            colors = FilterChipDefaults.filterChipColors(
                                labelColor = labelColor,
                                selectedLabelColor = Color.White,
                                selectedContainerColor = HeaderBlue,
                                containerColor = Color(0xFFF0F1F7)
                            )
                        )
                    }
                }

                Column {
                    Text(
                        if (type == "ANNUAL") "نرخ سالانه" else "نرخ ماهانه",
                        style = MaterialTheme.typography.labelLarge,
                        color = labelColor,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = rate,
                            onValueChange = { input ->
                                val cleaned = input.filter { it.isDigit() || it == '.' }
                                if (cleaned.count { it == '.' } <= 1) rate = cleaned
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.width(90.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = textColor,
                                unfocusedTextColor = textColor,
                                focusedBorderColor = HeaderBlue,
                                unfocusedBorderColor = Color(0xFFCCCCCC),
                                cursorColor = HeaderBlue
                            )
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("%", color = labelColor, fontWeight = FontWeight.Bold)
                    }
                }

                Column {
                    Text("دوره", style = MaterialTheme.typography.labelLarge, color = labelColor, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(6.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showStartCalendar = true },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = textColor),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.DateRange, null, modifier = Modifier.size(14.dp), tint = HeaderBlue)
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "$startY/${startM.toString().padStart(2, '0')}/${startD.toString().padStart(2, '0')}",
                                fontSize = 11.sp,
                                color = textColor,
                                maxLines = 1
                            )
                        }

                        Text("تا", color = labelColor, fontSize = 12.sp)

                        OutlinedButton(
                            onClick = { showEndCalendar = true },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = textColor),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.DateRange, null, modifier = Modifier.size(14.dp), tint = HeaderBlue)
                            Spacer(Modifier.width(4.dp))
                            Text(
                                if (endY != null)
                                    "$endY/${endM?.toString()?.padStart(2, '0')}/${endD?.toString()?.padStart(2, '0')}"
                                else "بی‌نهایت",
                                fontSize = 11.sp,
                                color = textColor,
                                maxLines = 1
                            )
                        }

                        if (endY != null) {
                            IconButton(
                                onClick = { endY = null; endM = null; endD = null },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    "حذف پایان",
                                    modifier = Modifier.size(18.dp),
                                    tint = DebitRed
                                )
                            }
                        }
                    }
                }

                Column {
                    Text("روز واریز سود", style = MaterialTheme.typography.labelLarge, color = labelColor, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = isLastDayOfMonth,
                            onCheckedChange = { isLastDayOfMonth = it },
                            colors = CheckboxDefaults.colors(
                                checkedColor = HeaderBlue,
                                checkmarkColor = Color.White,
                                uncheckedColor = labelColor
                            )
                        )
                        Text("آخر ماه", color = textColor, fontSize = 13.sp)
                        Spacer(Modifier.width(12.dp))
                        OutlinedTextField(
                            value = payoutDayText,
                            onValueChange = { input ->
                                val cleaned = input.filter { c -> c.isDigit() }.take(2)
                                payoutDayText = cleaned
                                payoutDay = cleaned.toIntOrNull()?.coerceIn(1, 31) ?: 0
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            enabled = !isLastDayOfMonth,
                            placeholder = { Text("16", color = labelColor.copy(alpha = 0.5f)) },
                            modifier = Modifier.width(80.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = textColor,
                                unfocusedTextColor = textColor,
                                disabledTextColor = labelColor.copy(alpha = 0.5f),
                                focusedBorderColor = HeaderBlue,
                                unfocusedBorderColor = Color(0xFFCCCCCC),
                                disabledBorderColor = Color(0xFFE0E0E0),
                                cursorColor = HeaderBlue
                            )
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("روز", color = labelColor, fontSize = 12.sp)
                    }
                }

                if (accountId != 0L) {
                    Column {
                        Text("حساب مقصد سود", style = MaterialTheme.typography.labelLarge, color = labelColor, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = destinationAccountId == null,
                                onClick = { destinationAccountId = null },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = HeaderBlue,
                                    unselectedColor = labelColor
                                )
                            )
                            Text("همین حساب", color = textColor, fontSize = 13.sp)
                        }
                        allAccounts.filter { it.id != accountId }.take(5).forEach { acc ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = destinationAccountId == acc.id,
                                    onClick = { destinationAccountId = acc.id },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = HeaderBlue,
                                        unselectedColor = labelColor
                                    )
                                )
                                Text(acc.name, color = textColor, fontSize = 13.sp)
                            }
                        }
                    }
                }

                if (error.isNotBlank()) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            error,
                            color = DebitRed,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val r = rate.toDoubleOrNull() ?: 0.0
                    if (r <= 0) { error = "نرخ باید بزرگتر از صفر باشه"; return@Button }
                    if (endY != null) {
                        val startMs = toMillis(startY, startM, startD)
                        val endMs = toMillis(endY!!, endM!!, endD!!)
                        if (endMs <= startMs) { error = "تاریخ پایان باید بعد از شروع باشه"; return@Button }
                    }
                    if (!isLastDayOfMonth && payoutDay <= 0) {
                        error = "روز واریز رو مشخص کن یا آخر ماه رو انتخاب کن"
                        return@Button
                    }

                    scope.launch {
                        val existingPeriods = db.profitPeriod().byAccountNow(accountId)
                        val newStartMs = toMillis(startY, startM, startD)
                        val newEndMs = if (endY != null) toMillis(endY!!, endM!!, endD!!) else Long.MAX_VALUE

                        for (existing in existingPeriods) {
                            if (existing.id == old?.id) continue

                            val existingStartMs = toMillis(existing.startYear, existing.startMonth, existing.startDay)
                            val existingEndMs = if (existing.endYear != null && existing.endMonth != null && existing.endDay != null) {
                                toMillis(existing.endYear, existing.endMonth, existing.endDay)
                            } else {
                                Long.MAX_VALUE
                            }

                            if (newStartMs <= existingEndMs && existingStartMs <= newEndMs) {
                                error = "این بازه با بازه‌ی موجود تداخل داره"
                                return@launch
                            }
                        }

                        error = ""
                        onSave(ProfitPeriod(
                            id = old?.id ?: 0,
                            accountId = accountId,
                            type = type,
                            rate = r,
                            startYear = startY, startMonth = startM, startDay = startD,
                            endYear = endY, endMonth = endM, endDay = endD,
                            payoutDay = if (isLastDayOfMonth) 0 else payoutDay,
                            destinationAccountId = destinationAccountId
                        ))
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = HeaderBlue, contentColor = Color.White),
                shape = RoundedCornerShape(12.dp)
            ) { Text("ذخیره", fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text("انصراف", color = HeaderBlue)
            }
        }
    )

    if (showStartCalendar) {
        JalaliCalendarDialog(startY, startM, startD, { y, m, d ->
            startY = y; startM = m; startD = d; showStartCalendar = false
        }, { showStartCalendar = false })
    }

    if (showEndCalendar) {
        JalaliCalendarDialog(
            endY ?: startY,
            endM ?: startM,
            endD ?: startD,
            { y, m, d -> endY = y; endM = m; endD = d; showEndCalendar = false },
            { showEndCalendar = false }
        )
    }
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
        PageHeader(title = "تنظیمات", subtitle = "مدیریت بکاپ و اطلاعات", onBackClick = onBack)

        Spacer(Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
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
                            OutlinedButton(onClick = { confirmRestore = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = HeaderBlue)) {
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
                        Button(
                            onClick = { pickFolder.launch(null) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = HeaderBlue, contentColor = Color.White)
                        ) { Text(if (customFolderName != null) "تغییر پوشه" else "انتخاب پوشه") }
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
            containerColor = Color.White,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.border(2.dp, HeaderBlue, RoundedCornerShape(20.dp)),
            title = { Text("حذف پوشه بکاپ", fontWeight = FontWeight.Bold, color = HeaderBlue) },
            text = { Text("بکاپ‌های بعدی در حافظه داخلی برنامه ذخیره می‌شوند.") },
            confirmButton = {
                Button(
                    onClick = { clearBackupFolderUri(context); customFolderName = null; confirmRemoveFolder = false },
                    colors = ButtonDefaults.buttonColors(containerColor = DebitRed)
                ) { Text("حذف") }
            },
            dismissButton = { TextButton(onClick = { confirmRemoveFolder = false }) { Text("انصراف", color = HeaderBlue) } }
        )
    }
}

// ═══════════════════════════════════════════════════════
// ConfirmDeleteDialog
// ═══════════════════════════════════════════════════════

@Composable
fun ConfirmDeleteDialog(title: String, message: String, onConfirm: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = Color.White,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.border(2.dp, HeaderBlue, RoundedCornerShape(20.dp)),
        title = { Text(title, fontWeight = FontWeight.Bold, color = HeaderBlue) },
        text = { Text(message) },
        confirmButton = {
            Button(onClick = onConfirm, colors = ButtonDefaults.buttonColors(containerColor = DebitRed)) { Text("حذف") }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("انصراف", color = HeaderBlue) } }
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
// ProfitEngine — با حداقل بالانس روز
// ═══════════════════════════════════════════════════════

object ProfitEngine {
    suspend fun recalculateAll(db: AppDb) {
        db.tx().deleteAllAuto()
        for (account in db.accounts().allNow()) {
            val periods = db.profitPeriod().byAccountNow(account.id)
            if (periods.isEmpty()) continue
            recalculateForAccount(db, account.id, periods)
        }
    }

    suspend fun recalculateForAccount(db: AppDb, accountId: Long, periods: List<ProfitPeriod>) {
        if (periods.isEmpty()) return

        val base = db.tx().byAccountNow(accountId)
            .filter { !it.isAutoProfit }
            .sortedBy { it.dateMillis }

        val today = Jalali.nowJalali()
        val todayMillis = System.currentTimeMillis()

        db.tx().deleteAutoByPrefix(accountId.toString())

        val sortedByStart = periods.sortedWith(
            compareBy({ it.startYear }, { it.startMonth }, { it.startDay })
        )
        val firstPeriod = sortedByStart.first()
        val overallStartMillis = toMillis(
            firstPeriod.startYear, firstPeriod.startMonth, firstPeriod.startDay
        )

        val out = mutableListOf<Transaction>()

        var cy = firstPeriod.startYear
        var cm = firstPeriod.startMonth

        while (true) {
            if (cy > today[0] || (cy == today[0] && cm > today[1])) break

            val daysInMonth = Jalali.daysInMonth(cy, cm)

            val activePeriod = periods.find { period ->
                val payoutDayForPeriod = if (period.payoutDay == 0) daysInMonth
                                         else period.payoutDay.coerceIn(1, daysInMonth)
                val monthStart = toMillis(cy, cm, 1)
                val monthEnd = toMillis(cy, cm, payoutDayForPeriod)

                val pStart = toMillis(period.startYear, period.startMonth, period.startDay)
                val pEnd = if (period.endYear != null && period.endMonth != null && period.endDay != null) {
                    toMillis(period.endYear, period.endMonth, period.endDay)
                } else {
                    Long.MAX_VALUE
                }

                monthStart <= pEnd && pStart <= monthEnd
            }

            if (activePeriod != null) {
                val payoutDayActual = if (activePeriod.payoutDay <= 0) daysInMonth
                                      else activePeriod.payoutDay.coerceIn(1, daysInMonth)
                val payoutMillis = toMillis(cy, cm, payoutDayActual)

                if (payoutMillis > todayMillis) {
                    cm++; if (cm > 12) { cm = 1; cy++ }
                    continue
                }

                val prevY: Int
                val prevM: Int
                if (cm == 1) { prevY = cy - 1; prevM = 12 } else { prevY = cy; prevM = cm - 1 }
                val prevMonthDays = Jalali.daysInMonth(prevY, prevM)
                val startDayInPrevMonth = payoutDayActual.coerceAtMost(prevMonthDays)
                val periodStartMillis = toMillis(prevY, prevM, startDayInPrevMonth)
                val periodEndMillis = payoutMillis - 86400000L
                val effectiveStart = maxOf(periodStartMillis, overallStartMillis)

                var totalProfit = 0.0
                var currentMillis = effectiveStart

                while (currentMillis <= periodEndMillis) {
                    val j = millisToJalali(currentMillis)
                    val dayStartMillis = currentMillis
                    val dayEndMillis = currentMillis + 86399000L

                    val minBalance = calculateMinBalanceInDay(
                        base = base,
                        dayStartMillis = dayStartMillis,
                        dayEndMillis = dayEndMillis
                    )

                    if (minBalance > 0L) {
                        val dailyRate = if (activePeriod.type == "ANNUAL") {
                            activePeriod.rate / 100.0 / 365.0
                        } else {
                            val daysInCurMonth = Jalali.daysInMonth(j[0], j[1])
                            activePeriod.rate / 100.0 / daysInCurMonth
                        }
                        totalProfit += minBalance * dailyRate
                    }

                    currentMillis += 86400000L
                }

                val roundedProfit = kotlin.math.round(totalProfit).toLong()
                if (roundedProfit > 0) {
                    val dest = activePeriod.destinationAccountId ?: accountId
                    val typeLabel = if (activePeriod.type == "ANNUAL") "سالانه" else "ماهانه"
                    val rateDisplay = if (activePeriod.rate % 1.0 == 0.0) activePeriod.rate.toLong().toString() else activePeriod.rate.toString()
                    val text = "سود ${Jalali.monthName(cm)} $cy — نرخ $rateDisplay% $typeLabel"

                    out.add(Transaction(
                        id = 0,
                        accountId = dest,
                        dateMillis = payoutMillis,
                        type = "بستانکار",
                        amount = roundedProfit,
                        note = text,
                        isAutoProfit = true,
                        profitKey = "$accountId:$cy:$cm"
                    ))
                }
            }

            cm++; if (cm > 12) { cm = 1; cy++ }
        }

        db.tx().insertAll(out)
    }

    /**
     * محاسبه‌ی حداقل بالانس توی یه روز
     * - بالانس قبل از اولین تراکنش روز
     * - بالانس بعد از هر تراکنش توی روز
     * - حداقل بین همه‌ی اینا
     */
    private fun calculateMinBalanceInDay(
        base: List<Transaction>,
        dayStartMillis: Long,
        dayEndMillis: Long
    ): Long {
        val balanceAtStart = base
            .filter { it.dateMillis < dayStartMillis }
            .sumOf { if (it.type == "بستانکار") it.amount else -it.amount }

        val dayTransactions = base
            .filter { it.dateMillis in dayStartMillis..dayEndMillis }
            .sortedBy { it.dateMillis }

        if (dayTransactions.isEmpty()) return balanceAtStart

        var minBal = balanceAtStart
        var runningBal = balanceAtStart
        for (t in dayTransactions) {
            runningBal += if (t.type == "بستانکار") t.amount else -t.amount
            if (runningBal < minBal) minBal = runningBal
        }

        return minBal
    }

    private fun millisToJalali(millis: Long): IntArray {
        val formatted = Jalali.format(millis)
        val datePart = formatted.substringBefore(" ")
        val parts = datePart.split("/")
        return intArrayOf(
            parts.getOrNull(0)?.toIntOrNull() ?: 0,
            parts.getOrNull(1)?.toIntOrNull() ?: 0,
            parts.getOrNull(2)?.toIntOrNull() ?: 0
        )
    }
}

// ═══════════════════════════════════════════════════════
// Backup
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
        for (a in db.accounts().allNow()) for (p in db.profitPeriod().byAccountNow(a.id)) {
            val o = JSONObject()
            o.put("accountId", p.accountId)
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
                val aid = accountIdMap[o.optLong("accountId")] ?: continue
                val dest = if (o.isNull("destinationAccountId")) null
                else accountIdMap[o.optLong("destinationAccountId")]
                db.profitPeriod().insert(ProfitPeriod(
                    accountId = aid,
                    type = o.optString("type", "ANNUAL"),
                    rate = o.optDouble("rate"),
                    startYear = o.getInt("startYear"),
                    startMonth = o.getInt("startMonth"),
                    startDay = o.getInt("startDay"),
                    endYear = if (o.isNull("endYear")) null else o.getInt("endYear"),
                    endMonth = if (o.isNull("endMonth")) null else o.getInt("endMonth"),
                    endDay = if (o.isNull("endDay")) null else o.getInt("endDay"),
                    payoutDay = o.optInt("payoutDay", 0),
                    destinationAccountId = dest
                ))
            }
        } else {
            val json = exportToJson(db)
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(json) }
        }
    }
}
