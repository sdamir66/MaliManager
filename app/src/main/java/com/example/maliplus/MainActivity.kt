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

private fun formatWithSeparator(input: String): String {
    if (input.isBlank()) return ""
    val digits = input.replace(",", "").filter { it.isDigit() }
    if (digits.isEmpty()) return ""
    return try {
        NumberFormat.getNumberInstance(Locale.US).format(digits.toLong())
    } catch (e: Exception) { digits }
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

// ═══════════════════════════════════════════════════════
// FinanceApp — ناوبری اصلی
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
            .padding(horizontal = 20.dp, vertical = 24.dp)
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

            extraActions?.invoke()
        }
    }
}

// ═══════════════════════════════════════════════════════
// صفحه اصلی — لیست اشخاص
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
                .padding(horizontal = 20.dp, vertical = 24.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "مدیریت مالی",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "${persons.size} شخص  •  ${allAccounts.size} حساب",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "تنظیمات",
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
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

        Spacer(Modifier.height(16.dp))

        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = 16.dp),
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
                            Text(
                                "هنوز شخصی ثبت نکردی",
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.Gray
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "روی + بزن و شروع کن",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.Gray
                            )
                        }
                    }
                }
            }

            items(persons, key = { it.id }) { p ->
                val personAccounts = allAccounts.filter { it.personId == p.id }
                PersonCard(
                    person = p,
                    accounts = personAccounts,
                    onClick = { onOpenPerson(p) },
                    onEdit = { editTarget = p },
                    onDelete = { deleteTarget = p },
                    db = db
                )
            }
        }
    }

    if (add) {
        PersonEditor(null, {
            scope.launch {
                val order = (persons.maxOfOrNull { it.displayOrder } ?: 0) + 1
                db.persons().insert(it.copy(displayOrder = order))
                add = false
            }
        }, { add = false })
    }

    editTarget?.let { target ->
        PersonEditor(target, {
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
                        db.tx().byAccountNow(acc.id).forEach { tx -> db.tx().delete(tx) }
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
        LaunchedEffect(acc.id) {
            txs.value = db.tx().byAccountNow(acc.id)
        }
        val bal = txs.value.sumOf { if (it.type == "بستانکار") it.amount else -it.amount }
        acc to bal
    }

    val displayCurrencies = remember(person, accountsWithBalance) {
        val saved = person.displayedCurrencies.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (saved.isNotEmpty()) saved
        else accountsWithBalance.map { it.first.displayUnit() }.distinct().take(3)
    }

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
            Box(
                Modifier.fillMaxSize().padding(horizontal = 20.dp),
                contentAlignment = alignment
            ) {
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
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(52.dp)
                            .background(HeaderBlue, shape = CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            accounts.size.toString(),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            person.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "${accounts.size} حساب",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                    Text("‹", fontSize = 22.sp, color = Color.Gray)
                }

                if (displayCurrencies.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = Color(0xFFEEEEEE))
                    Spacer(Modifier.height(12.dp))
                    displayCurrencies.take(3).forEach { cur ->
                        val sum = accountsWithBalance
                            .filter { it.first.displayUnit() == cur }
                            .sumOf { it.second }
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 3.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                cur,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.Gray
                            )
                            Text(
                                money(kotlin.math.abs(sum)),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (sum >= 0) CreditGreen else DebitRed
                            )
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
    val scope = rememberCoroutineScope()

    val accountBalances = accounts.map { acc ->
        val txs = remember(acc.id) { mutableStateOf<List<Transaction>>(emptyList()) }
        LaunchedEffect(acc.id) { txs.value = db.tx().byAccountNow(acc.id) }
        val bal = txs.value.sumOf { if (it.type == "بستانکار") it.amount else -it.amount }
        acc to bal
    }

    val currencyTotals = accountBalances
        .groupBy { it.first.displayUnit() }
        .mapValues { entry -> entry.value.sumOf { it.second } }

    Box(Modifier.fillMaxSize().background(BgLight)) {
        Column(Modifier.fillMaxSize()) {
            PageHeader(
                title = person.name,
                subtitle = "${accounts.size} حساب",
                onBackClick = onBack,
                extraActions = {
                    IconButton(onClick = { editPerson = true }, modifier = Modifier.size(48.dp)) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "ویرایش شخص",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
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
                Column(Modifier.fillMaxWidth().padding(18.dp)) {
                    Text("مانده‌های کل", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                    Spacer(Modifier.height(8.dp))
                    if (currencyTotals.isEmpty()) {
                        Text("—", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                    } else {
                        currencyTotals.forEach { (cur, total) ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    cur,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.Gray
                                )
                                Text(
                                    money(kotlin.math.abs(total)),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = if (total >= 0) CreditGreen else DebitRed
                                )
                            }
                        }
                    }
                }
            }

            LazyColumn(
                Modifier
                    .fillMaxSize()
                    .offset(y = (-25).dp)
                    .padding(horizontal = 16.dp),
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

                items(accounts, key = { it.id }) { acc ->
                    val bal = accountBalances.find { it.first.id == acc.id }?.second ?: 0L
                    SwipeableAccountCard(
                        account = acc,
                        balance = bal,
                        onClick = { onOpenAccount(acc) },
                        onEdit = { editTarget = acc },
                        onDelete = { deleteTarget = acc }
                    )
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
                    db.accounts().delete(target)
                    deleteTarget = null
                }
            },
            onCancel = { deleteTarget = null }
        )
    }

    if (editPerson) {
        PersonEditor(person, {
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
            Box(
                Modifier.fillMaxSize().padding(horizontal = 20.dp),
                contentAlignment = alignment
            ) {
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
                Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(52.dp)
                        .background(
                            color = if (isCredit) CreditGreen else DebitRed,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (isCredit) "بس" else "بد",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        account.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        account.displayUnit(),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        money(kotlin.math.abs(balance)),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isCredit) CreditGreen else DebitRed
                    )
                }
                Text("‹", fontSize = 22.sp, color = Color.Gray)
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
    val setting by db.profit().byAccount(a.id).collectAsState(null)
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
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "تنظیم سود",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
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
                Row(
                    Modifier.fillMaxWidth().padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("مانده کل", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
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
                                a.displayUnit(),
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
                        Text("+", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Light)
                    }
                }
            }

            val runningBalances = remember(tx) { calculateRunningBalances(tx) }

            LazyColumn(
                Modifier
                    .fillMaxSize()
                    .offset(y = (-25).dp)
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
                    if (!t.isAutoProfit) {
                        SwipeableTransactionCard(
                            transaction = t,
                            currency = a.displayUnit(),
                            runningBalance = running,
                            onEdit = { editor = t },
                            onDelete = { deleteTarget = t }
                        )
                    } else {
                        AutoTransactionCard(
                            transaction = t,
                            currency = a.displayUnit(),
                            runningBalance = running
                        )
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
    if (profit) ProfitSettingsEditor(db, a, setting) { profit = false }

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
            Box(
                Modifier.fillMaxSize().padding(horizontal = 20.dp),
                contentAlignment = alignment
            ) {
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
        val isTxCredit = transaction.type == "بستانکار"
        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(14.dp),
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
                    Text(
                        transaction.type,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isTxCredit) CreditGreen else DebitRed
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        Jalali.format(transaction.dateMillis),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                    if (transaction.note.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            transaction.note,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF45464F)
                        )
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
                        "مانده: ${money(kotlin.math.abs(runningBalance))}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )
                }
            }
        }
    }
}

@Composable
fun AutoTransactionCard(
    transaction: Transaction,
    currency: String,
    runningBalance: Long
) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(46.dp).background(CreditGreen, shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("↓", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        transaction.type,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = CreditGreen
                    )
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
                Spacer(Modifier.height(2.dp))
                Text(
                    Jalali.format(transaction.dateMillis),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                if (transaction.note.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        transaction.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF45464F)
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "+${money(transaction.amount)}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = CreditGreen
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "مانده: ${money(kotlin.math.abs(runningBalance))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════
// PersonEditor
// ═══════════════════════════════════════════════════════

@Composable
fun PersonEditor(old: Person?, onSave: (Person) -> Unit, onCancel: () -> Unit) {
    var name by remember(old) { mutableStateOf(old?.name ?: "") }
    var note by remember(old) { mutableStateOf(old?.note ?: "") }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(if (old == null) "شخص جدید" else "ویرایش شخص") },
        text = {
            Column {
                OutlinedTextField(
                    name, { name = it },
                    label = { Text("نام شخص") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    note, { note = it },
                    label = { Text("توضیحات (اختیاری)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button({
                if (name.isNotBlank())
                    onSave(Person(
                        id = old?.id ?: 0,
                        name = name,
                        note = note,
                        displayOrder = old?.displayOrder ?: 0,
                        displayedCurrencies = old?.displayedCurrencies ?: ""
                    ))
            }) { Text("ذخیره") }
        },
        dismissButton = { TextButton(onCancel) { Text("انصراف") } }
    )
}

// ═══════════════════════════════════════════════════════
// AccountEditor
// ═══════════════════════════════════════════════════════

@Composable
fun AccountEditor(
    old: Account?,
    personId: Long,
    onSave: (Account) -> Unit,
    onCancel: () -> Unit
) {
    var name by remember(old) { mutableStateOf(old?.name ?: "") }
    var note by remember(old) { mutableStateOf(old?.note ?: "") }
    var currency by remember(old) { mutableStateOf(old?.currency ?: "تومان") }
    var customUnit by remember(old) { mutableStateOf(old?.customUnit ?: "") }
    var currencyExpanded by remember { mutableStateOf(false) }
    var useGlobalProfit by remember(old) { mutableStateOf(old?.useGlobalProfit ?: true) }

    val currencies = listOf("ریال", "تومان", "دلار", "یورو", "پوند", "درهم")
    val hasCustomUnit = customUnit.isNotBlank()

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(if (old == null) "حساب جدید" else "ویرایش حساب") },
        text = {
            Column {
                OutlinedTextField(
                    name, { name = it },
                    label = { Text("عنوان حساب") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    note, { note = it },
                    label = { Text("توضیحات (اختیاری)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    customUnit,
                    { customUnit = it },
                    label = { Text("واحد دلخواه (اختیاری)") },
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = { Text("اگه پر بشه، جایگزین ارز می‌شه") }
                )
                Spacer(Modifier.height(12.dp))

                Text(
                    "ارز",
                    style = MaterialTheme.typography.titleSmall,
                    color = if (hasCustomUnit) Color.Gray else Color(0xFF1B1B1F)
                )
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

                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = Color(0xFFEEEEEE))
                Spacer(Modifier.height(8.dp))

                Text("نوع سود", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(useGlobalProfit, { useGlobalProfit = true })
                    Text("استفاده از تنظیم کلی برنامه")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(!useGlobalProfit, { useGlobalProfit = false })
                    Text("تنظیم سود اختصاصی")
                }
            }
        },
        confirmButton = {
            Button({
                if (name.isNotBlank())
                    onSave(Account(
                        id = old?.id ?: 0,
                        personId = personId,
                        name = name,
                        note = note,
                        currency = currency,
                        customUnit = customUnit,
                        displayOrder = old?.displayOrder ?: 0,
                        useGlobalProfit = useGlobalProfit
                    ))
            }) { Text("ذخیره") }
        },
        dismissButton = { TextButton(onCancel) { Text("انصراف") } }
    )
}

// ═══════════════════════════════════════════════════════
// TxEditor
// ═══════════════════════════════════════════════════════

@Composable
fun TxEditor(
    old: Transaction?,
    accountId: Long,
    onSave: (Transaction) -> Unit,
    onCancel: () -> Unit
) {
    var type by remember(old) { mutableStateOf(old?.type ?: "بدهکار") }
    var amount by remember(old) {
        mutableStateOf(old?.amount?.let { money(it) } ?: "")
    }
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
                    amount,
                    { input -> amount = formatWithSeparator(input) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    label = { Text("مبلغ") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    date, { date = it },
                    label = { Text("تاریخ شمسی 1405/02/31") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    note, { note = it },
                    label = { Text("شرح") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button({
                val n = parseSeparatedAmount(amount)
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

// ═══════════════════════════════════════════════════════
// ProfitSettingsEditor
// ═══════════════════════════════════════════════════════

@Composable
fun ProfitSettingsEditor(
    db: AppDb,
    a: Account,
    old: ProfitSettings?,
    close: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var enabled by remember(old) { mutableStateOf(old?.enabled ?: false) }
    var mode by remember(old) { mutableStateOf(old?.mode ?: "DAILY_ANNUAL") }
    var annual by remember(old) { mutableStateOf(old?.annualRate?.toString() ?: "20") }
    var day by remember(old) { mutableStateOf(old?.payoutDay?.toString() ?: "30") }
    var dest by remember(old) { mutableStateOf(old?.destinationAccountId) }
    var ratesOpen by remember { mutableStateOf(false) }
    val allAccounts by db.accounts().all().collectAsState(emptyList())
    val globalSettings by db.globalProfit().get().collectAsState(null)

    AlertDialog(
        onDismissRequest = close,
        title = { Text("تنظیم سود اختصاصی") },
        text = {
            Column {
                if (a.useGlobalProfit) {
                    Card(
                        Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFFFF3E0)
                        )
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                "⚠️ این حساب از تنظیم سود کلی برنامه استفاده می‌کند.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFE65100),
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(4.dp))
                            if (globalSettings != null && globalSettings.enabled) {
                                Text(
                                    "نرخ کلی: ${globalSettings.annualRate}% سالانه",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFFE65100)
                                )
                            } else {
                                Text(
                                    "سود کلی برنامه غیرفعال است.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFFE65100)
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "برای تنظیم اختصاصی، از «ویرایش حساب» گزینه‌ی «اختصاصی» را انتخاب کن.",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFE65100)
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(enabled, { enabled = !enabled }, enabled = !a.useGlobalProfit)
                    Text(
                        "فعال باشد",
                        color = if (a.useGlobalProfit) Color.Gray else Color(0xFF1B1B1F)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text("نوع سود", fontWeight = FontWeight.Medium)
                Row {
                    FilterChip(mode == "DAILY_ANNUAL", { mode = "DAILY_ANNUAL" },
                        label = { Text("روزشمار سالانه") }, enabled = !a.useGlobalProfit)
                    Spacer(Modifier.width(6.dp))
                    FilterChip(mode == "MONTHLY", { mode = "MONTHLY" },
                        label = { Text("ماهانه") }, enabled = !a.useGlobalProfit)
                }
                Spacer(Modifier.height(8.dp))
                if (mode == "DAILY_ANNUAL")
                    OutlinedTextField(
                        annual,
                        { input ->
                            val cleaned = input.filter { it.isDigit() || it == '.' }
                            if (cleaned.count { it == '.' } <= 1) annual = cleaned
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        label = { Text("نرخ سالانه ٪") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !a.useGlobalProfit
                    )
                if (mode == "MONTHLY")
                    OutlinedButton({ ratesOpen = true }, enabled = !a.useGlobalProfit) {
                        Text("تنظیم نرخ ماه‌های سال")
                    }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    day, { day = it.filter { c -> c.isDigit() } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    label = { Text("روز واریز ماه") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !a.useGlobalProfit
                )
                Spacer(Modifier.height(8.dp))
                Text("حساب مقصد سود", style = MaterialTheme.typography.titleSmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(dest == null, { dest = null }, enabled = !a.useGlobalProfit)
                    Text("همین حساب")
                }
                allAccounts.filter { it.id != a.id }.forEach { acc ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(dest == acc.id, { dest = acc.id }, enabled = !a.useGlobalProfit)
                        Text(acc.name)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                {
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
                },
                enabled = !a.useGlobalProfit
            ) { Text("ذخیره") }
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
                            values[m] ?: "",
                            { input ->
                                val cleaned = input.filter { it.isDigit() || it == '.' }
                                if (cleaned.count { it == '.' } <= 1) values[m] = cleaned
                            },
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

// ═══════════════════════════════════════════════════════
// GlobalProfitEditor
// ═══════════════════════════════════════════════════════

@Composable
fun GlobalProfitEditor(
    db: AppDb,
    old: GlobalProfitSettings?,
    close: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var enabled by remember(old) { mutableStateOf(old?.enabled ?: false) }
    var mode by remember(old) { mutableStateOf(old?.mode ?: "DAILY_ANNUAL") }
    var annual by remember(old) { mutableStateOf(old?.annualRate?.toString() ?: "20") }
    var day by remember(old) { mutableStateOf(old?.payoutDay?.toString() ?: "30") }

    AlertDialog(
        onDismissRequest = close,
        title = { Text("تنظیم سود کلی برنامه") },
        text = {
            Column {
                Text(
                    "این تنظیم روی همه‌ی حساب‌هایی که «سود کلی» را انتخاب کرده‌اند، اعمال می‌شود.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                Spacer(Modifier.height(12.dp))
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
                        annual,
                        { input ->
                            val cleaned = input.filter { it.isDigit() || it == '.' }
                            if (cleaned.count { it == '.' } <= 1) annual = cleaned
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        label = { Text("نرخ سالانه ٪") },
                        modifier = Modifier.fillMaxWidth()
                    )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    day, { day = it.filter { c -> c.isDigit() } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    label = { Text("روز واریز ماه") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "⚠️ برای حالت ماهانه، باید از توی هر حساب نرخ ماهانه تعریف کنی.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFE65100)
                )
            }
        },
        confirmButton = {
            Button({
                scope.launch {
                    db.globalProfit().upsert(
                        GlobalProfitSettings(
                            id = 1,
                            enabled = enabled,
                            mode = mode,
                            annualRate = annual.toDoubleOrNull() ?: 20.0,
                            payoutDay = day.toIntOrNull()?.coerceIn(1, 31) ?: 30
                        )
                    )
                    ProfitEngine.recalculateAll(db)
                    close()
                }
            }) { Text("ذخیره") }
        },
        dismissButton = { TextButton(close) { Text("انصراف") } }
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
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (e: Exception) { e.printStackTrace() }
            saveBackupFolderUri(context, uri)
        }
    }

    var autoBackupExists by remember { mutableStateOf(false) }
    var autoBackupInfo by remember { mutableStateOf("") }
    var confirmRestore by remember { mutableStateOf(false) }
    var confirmRemoveFolder by remember { mutableStateOf(false) }
    var customFolderName by remember { mutableStateOf<String?>(null) }
    var globalProfitEditorOpen by remember { mutableStateOf(false) }
    var globalSettings by remember { mutableStateOf<GlobalProfitSettings?>(null) }

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
        globalSettings = db.globalProfit().getNow()
    }

    Column(Modifier.fillMaxSize().background(BgLight)) {
        PageHeader(
            title = "تنظیمات",
            subtitle = "مدیریت بکاپ و اطلاعات",
            onBackClick = onBack
        )

        Spacer(Modifier.height(16.dp))

        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // سود کلی
            item {
                Text(
                    "سود کلی برنامه",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1B1B1F),
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                )
            }

            item {
                Card(
                    Modifier.fillMaxWidth().clickable { globalProfitEditorOpen = true },
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(40.dp).background(HeaderBlue.copy(alpha = 0.1f), RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("💰", fontSize = 18.sp, color = HeaderBlue)
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "تنظیم سود کلی",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    if (globalSettings?.enabled == true)
                                        "فعال — ${globalSettings?.annualRate}% سالانه"
                                    else "غیرفعال",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (globalSettings?.enabled == true) CreditGreen else Color.Gray
                                )
                            }
                            Text("‹", fontSize = 20.sp, color = Color.Gray)
                        }
                    }
                }
            }

            // بکاپ
            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    "پشتیبان و بازیابی",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1B1B1F),
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                )
            }

            item {
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        SettingRow("گرفتن بکاپ", "ذخیره در حافظه یا Google Drive", "⬇",
                            onClick = { create.launch("MaliManager-backup.json") })
                        HorizontalDivider(color = Color(0xFFEEEEEE))
                        SettingRow("بازیابی بکاپ", "از فایل JSON", "⬆",
                            onClick = { open.launch(arrayOf("application/json", "text/*")) })
                    }
                }
            }

            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    "بکاپ خودکار (هر ۵ ثانیه)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1B1B1F),
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                )
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
                                Box(
                                    Modifier.size(36.dp).background(CreditGreen.copy(alpha = 0.15f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("✓", color = CreditGreen, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("بکاپ خودکار فعال",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold)
                                    Text("آخرین: $autoBackupInfo",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.Gray)
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            OutlinedButton(
                                onClick = { confirmRestore = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) { Text("بازیابی از بکاپ خودکار") }
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier.size(36.dp).background(Color(0xFFFFE0B2), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("!", color = Color(0xFFE65100), fontWeight = FontWeight.Bold, fontSize = 20.sp)
                                }
                                Spacer(Modifier.width(12.dp))
                                Text("هنوز بکاپ خودکاری ذخیره نشده",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.Gray)
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
                        Text("محل ذخیره بکاپ خودکار",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (customFolderName != null) "پوشه فعلی: $customFolderName"
                            else "پیش‌فرض: حافظه داخلی برنامه",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (customFolderName != null) CreditGreen else Color.Gray
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = { pickFolder.launch(null) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
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
                Text(
                    "درباره",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1B1B1F),
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                )
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
                            Box(
                                Modifier.size(48.dp).background(HeaderBlue, shape = RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) { Text("💰", fontSize = 24.sp) }
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text("مدیریت مالی",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold)
                                Text("نسخه ۱.۰",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray)
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
                                Text("sdamir66",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }
        }
    }

    if (globalProfitEditorOpen) {
        GlobalProfitEditor(db, globalSettings) { globalProfitEditorOpen = false }
    }

    if (confirmRestore) {
        ConfirmDeleteDialog(
            title = "بازیابی از بکاپ خودکار",
            message = "تمام داده‌های فعلی با بکاپ جایگزین می‌شوند. مطمئنی؟",
            onConfirm = {
                scope.launch {
                    val file = java.io.File(context.filesDir, "auto_backup.json")
                    if (file.exists()) {
                        Backup.restoreOrExport(context, db, Uri.fromFile(file), true)
                    }
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
                    onClick = {
                        clearBackupFolderUri(context)
                        customFolderName = null
                        confirmRemoveFolder = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DebitRed)
                ) { Text("حذف") }
            },
            dismissButton = {
                TextButton({ confirmRemoveFolder = false }) { Text("انصراف") }
            }
        )
    }
}

// ═══════════════════════════════════════════════════════
// کامپوننت‌های کمکی
// ═══════════════════════════════════════════════════════

@Composable
fun ConfirmDeleteDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = { Text(message) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = DebitRed)
            ) { Text("حذف") }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text("انصراف") }
        }
    )
}

@Composable
fun SettingRow(
    title: String,
    subtitle: String,
    icon: String,
    onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(40.dp).background(HeaderBlue.copy(alpha = 0.1f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
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
// ProfitEngine
// ═══════════════════════════════════════════════════════

object ProfitEngine {
    suspend fun recalculateAll(db: AppDb) {
        db.tx().deleteAllAuto()
        val globalSettings = db.globalProfit().getNow()
        for (a in db.accounts().allNow()) {
            val accountSettings = db.profit().byAccountNow(a.id)
            if (a.useGlobalProfit) {
                if (globalSettings != null && globalSettings.enabled) {
                    val gs = globalSettings  // ← متغیر محلی
                    recalculateWithSettings(
                        db, a.id,
                        gs.enabled, gs.mode, gs.annualRate, gs.payoutDay,
                        null
                    )
                }
            } else {
                if (accountSettings != null && accountSettings.enabled) {
                    val as_ = accountSettings  // ← متغیر محلی
                    recalculateWithSettings(
                        db, a.id,
                        as_.enabled, as_.mode, as_.annualRate, as_.payoutDay,
                        as_.destinationAccountId
                    )
                }
            }
        }
    }

    suspend fun recalculateForAccount(db: AppDb, accountId: Long) {
        val account = db.accounts().byId(accountId) ?: return
        val globalSettings = db.globalProfit().getNow()
        val accountSettings = db.profit().byAccountNow(accountId)

        if (account.useGlobalProfit) {
            if (globalSettings != null && globalSettings.enabled) {
                val gs = globalSettings  // ← متغیر محلی
                recalculateWithSettings(
                    db, accountId,
                    gs.enabled, gs.mode, gs.annualRate, gs.payoutDay,
                    null
                )
            }
        } else {
            if (accountSettings != null && accountSettings.enabled) {
                val as_ = accountSettings  // ← متغیر محلی
                recalculateWithSettings(
                    db, accountId,
                    as_.enabled, as_.mode, as_.annualRate, as_.payoutDay,
                    as_.destinationAccountId
                )
            }
        }
    }

    private suspend fun recalculateWithSettings(
        db: AppDb,
        accountId: Long,
        enabled: Boolean,
        mode: String,
        annualRate: Double,
        payoutDay: Int,
        destinationAccountId: Long?
    ) {
        if (!enabled) return
        val base = db.tx().byAccountNow(accountId).filter { !it.isAutoProfit }
        val rates = db.profit().ratesNow(accountId)
        val today = Jalali.nowJalali()
        val out = mutableListOf<Transaction>()

        val months = rates.map { it.year * 12 + it.month }.toMutableSet()
        if (mode == "DAILY_ANNUAL") {
            for (y in (today[0] - 2)..today[0]) for (m in 1..12) months.add(y * 12 + m)
        }

        for (key in months.sorted()) {
            val y = key / 12
            val m = key % 12
            if (y > today[0] || (y == today[0] && m > today[1])) continue
            val days = Jalali.daysInMonth(y, m)

            val rate = if (mode == "MONTHLY") {
                rates.find { it.year == y && it.month == m }?.ratePercent ?: continue
            } else annualRate

            var total = 0.0
            for (d in 1..days) {
                val t = Jalali.startOfJalaliMonth(y, m) + ((d - 1) * 86400000L)
                val bal = base.filter { it.dateMillis <= t }
                    .sumOf { if (it.type == "بستانکار") it.amount else -it.amount }
                total += if (mode == "MONTHLY") bal * (rate / 100.0) / days
                else bal * (rate / 100.0) / 365.0
            }
            val amount = kotlin.math.round(total).toLong()
            if (amount <= 0) continue

            val payout = jalaliPayout(y, m, payoutDay)
            if (payout > System.currentTimeMillis()) continue

            val destCandidate = destinationAccountId ?: accountId
            val dest = if (db.accounts().byId(destCandidate) != null) destCandidate else accountId

            val text = if (mode == "MONTHLY")
                "سود ماهانه ${Jalali.monthName(m)} $y — نرخ ${rate}%"
            else
                "سود روزشمار ${Jalali.monthName(m)} $y — نرخ ${annualRate}% سالانه"

            out.add(Transaction(0, dest, payout, "بستانکار", amount, text, true, "$accountId:$y:$m"))
        }
        db.tx().insertAll(out)
    }
}

private fun jalaliPayout(y: Int, m: Int, day: Int): Long =
    Jalali.parse("%04d/%02d/%02d".format(Locale.US, y, m, day.coerceAtMost(Jalali.daysInMonth(y, m))))!!

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
            o.put("id", p.id)
            o.put("name", p.name)
            o.put("note", p.note)
            o.put("displayOrder", p.displayOrder)
            o.put("displayedCurrencies", p.displayedCurrencies)
            pp.put(o)
        }
        root.put("persons", pp)

        val aa = arr()
        for (a in db.accounts().allNow()) {
            val o = JSONObject()
            o.put("id", a.id)
            o.put("personId", a.personId)
            o.put("name", a.name)
            o.put("note", a.note)
            o.put("currency", a.currency)
            o.put("customUnit", a.customUnit)
            o.put("displayOrder", a.displayOrder)
            o.put("useGlobalProfit", a.useGlobalProfit)
            aa.put(o)
        }
        root.put("accounts", aa)

        val tt = arr()
        for (a in db.accounts().allNow()) for (t in db.tx().byAccountNow(a.id)) {
            val o = JSONObject()
            o.put("accountId", t.accountId)
            o.put("dateMillis", t.dateMillis)
            o.put("type", t.type)
            o.put("amount", t.amount)
            o.put("note", t.note)
            o.put("isAutoProfit", t.isAutoProfit)
            o.put("profitKey", t.profitKey)
            tt.put(o)
        }
        root.put("transactions", tt)

        val ps = arr()
        for (a in db.accounts().allNow()) {
            val s = db.profit().byAccountNow(a.id)
            if (s != null) {
                val o = JSONObject()
                o.put("accountId", a.id)
                o.put("enabled", s.enabled)
                o.put("mode", s.mode)
                o.put("annualRate", s.annualRate)
                o.put("payoutDay", s.payoutDay)
                o.put("destinationAccountId", s.destinationAccountId ?: JSONObject.NULL)
                ps.put(o)
            }
        }
        root.put("profitSettings", ps)

        val mr = arr()
        for (a in db.accounts().allNow()) for (r in db.profit().ratesNow(a.id)) {
            val o = JSONObject()
            o.put("accountId", r.accountId)
            o.put("year", r.year)
            o.put("month", r.month)
            o.put("ratePercent", r.ratePercent)
            mr.put(o)
        }
        root.put("monthlyRates", mr)

        val gs = db.globalProfit().getNow()
        if (gs != null) {
            val o = JSONObject()
            o.put("enabled", gs.enabled)
            o.put("mode", gs.mode)
            o.put("annualRate", gs.annualRate)
            o.put("payoutDay", gs.payoutDay)
            root.put("globalProfit", o)
        }

        return root.toString(2)
    }

    suspend fun restoreOrExport(context: Context, db: AppDb, uri: Uri, restore: Boolean) {
        if (restore) {
            val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: return
            val root = JSONObject(text)
            db.tx().clear()
            db.profit().clearRates()
            db.profit().clearSettings()
            db.accounts().clear()
            db.persons().clear()

            val persons = root.optJSONArray("persons") ?: JSONArray()
            val personIdMap = mutableMapOf<Long, Long>()
            for (i in 0 until persons.length()) {
                val o = persons.getJSONObject(i)
                val oldId = o.optLong("id", 0)
                val newId = db.persons().insert(Person(
                    name = o.getString("name"),
                    note = o.optString("note"),
                    displayOrder = o.optInt("displayOrder", 0),
                    displayedCurrencies = o.optString("displayedCurrencies", "")
                ))
                personIdMap[oldId] = newId
            }

            val accounts = root.optJSONArray("accounts") ?: JSONArray()
            val accountIdMap = mutableMapOf<Long, Long>()
            for (i in 0 until accounts.length()) {
                val o = accounts.getJSONObject(i)
                val oldId = o.optLong("id", 0)
                val newPersonId = personIdMap[o.optLong("personId")] ?: continue
                val newId = db.accounts().insert(Account(
                    personId = newPersonId,
                    name = o.getString("name"),
                    note = o.optString("note"),
                    currency = o.optString("currency", "تومان"),
                    customUnit = o.optString("customUnit", ""),
                    displayOrder = o.optInt("displayOrder", 0),
                    useGlobalProfit = o.optBoolean("useGlobalProfit", true)
                ))
                accountIdMap[oldId] = newId
            }

            val tx = root.optJSONArray("transactions") ?: JSONArray()
            for (i in 0 until tx.length()) {
                val o = tx.getJSONObject(i)
                val newAccountId = accountIdMap[o.optLong("accountId")] ?: continue
                db.tx().insert(Transaction(
                    accountId = newAccountId,
                    dateMillis = o.getLong("dateMillis"),
                    type = o.getString("type"),
                    amount = o.getLong("amount"),
                    note = o.optString("note"),
                    isAutoProfit = o.optBoolean("isAutoProfit", false),
                    profitKey = o.optString("profitKey").ifBlank { null }
                ))
            }

            val ps = root.optJSONArray("profitSettings") ?: JSONArray()
            for (i in 0 until ps.length()) {
                val o = ps.getJSONObject(i)
                val newAccountId = accountIdMap[o.optLong("accountId")] ?: continue
                val newDestId = if (o.has("destinationAccountId") && !o.isNull("destinationAccountId"))
                    accountIdMap[o.optLong("destinationAccountId")] else null
                db.profit().upsert(ProfitSettings(
                    accountId = newAccountId,
                    enabled = o.optBoolean("enabled"),
                    mode = o.optString("mode", "DAILY_ANNUAL"),
                    annualRate = o.optDouble("annualRate"),
                    payoutDay = o.optInt("payoutDay", 30),
                    destinationAccountId = newDestId
                ))
            }

            val mr = root.optJSONArray("monthlyRates") ?: JSONArray()
            for (i in 0 until mr.length()) {
                val o = mr.getJSONObject(i)
                val newAccountId = accountIdMap[o.optLong("accountId")] ?: continue
                db.profit().upsertRate(MonthlyRate(
                    accountId = newAccountId,
                    year = o.getInt("year"),
                    month = o.getInt("month"),
                    ratePercent = o.getDouble("ratePercent")
                ))
            }

            val gs = root.optJSONObject("globalProfit")
            if (gs != null) {
                db.globalProfit().upsert(GlobalProfitSettings(
                    id = 1,
                    enabled = gs.optBoolean("enabled", false),
                    mode = gs.optString("mode", "DAILY_ANNUAL"),
                    annualRate = gs.optDouble("annualRate", 20.0),
                    payoutDay = gs.optInt("payoutDay", 30)
                ))
            }
        } else {
            val json = exportToJson(db)
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(json) }
        }
    }
}