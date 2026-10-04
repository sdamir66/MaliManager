package com.sdamir66.dadban.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sdamir66.dadban.data.AppDb
import com.sdamir66.dadban.data.ProfitEngine
import com.sdamir66.dadban.data.ProfitPeriod
import com.sdamir66.dadban.displayUnit
import com.sdamir66.dadban.ui.theme.DebitRed
import com.sdamir66.dadban.ui.theme.HeaderBlue
import com.sdamir66.dadban.util.Jalali
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun ProfitPeriodsScreen(db: AppDb, accountId: Long, accountName: String, close: () -> Unit) {
    val periodsRaw by db.profitPeriod().byAccount(accountId).collectAsState(emptyList())
    val periods = periodsRaw.sortedByDescending { it.id }
    var addPeriod by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<ProfitPeriod?>(null) }
    var deleteTarget by remember { mutableStateOf<ProfitPeriod?>(null) }
    val scope = rememberCoroutineScope()

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
        ProfitPeriodEditor(null, accountId, db, { newPeriod ->
            scope.launch {
                db.profitPeriod().insert(newPeriod)
                val updatedPeriods = db.profitPeriod().byAccountNow(accountId)
                ProfitEngine.recalculateForAccount(db, accountId, updatedPeriods)
                addPeriod = false
            }
        }, { addPeriod = false })
    }

    editTarget?.let { target ->
        ProfitPeriodEditor(target, accountId, db, { updated ->
            scope.launch {
                db.profitPeriod().update(updated)
                val updatedPeriods = db.profitPeriod().byAccountNow(accountId)
                ProfitEngine.recalculateForAccount(db, accountId, updatedPeriods)
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
                    val updatedPeriods = db.profitPeriod().byAccountNow(accountId)
                    ProfitEngine.recalculateForAccount(db, accountId, updatedPeriods)
                    deleteTarget = null
                }
            },
            onCancel = { deleteTarget = null }
        )
    }
}

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
    var conflictError by remember { mutableStateOf<String?>(null) }
    var isChecking by remember { mutableStateOf(false) }

    var expandedTab by remember { mutableStateOf<String?>(null) }

    val allAccounts by db.accounts().all().collectAsState(emptyList())
    val allPersons by db.persons().all().collectAsState(emptyList())
    val currentAccount = allAccounts.find { it.id == accountId }
    val currentUnit = currentAccount?.displayUnit() ?: ""

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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {

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
                                    val prevY: Int
                                    val prevM: Int
                                    if (today[1] == 1) {
                                        prevY = today[0] - 1
                                        prevM = 12
                                    } else {
                                        prevY = today[0]
                                        prevM = today[1] - 1
                                    }
                                    startY = prevY; startM = prevM; startD = 1
                                    endY = prevY; endM = prevM
                                    endD = Jalali.daysInMonth(prevY, prevM)
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

                if (accountId != 0L && currentAccount != null) {
                    Column {
                        Text(
                            "حساب مقصد سود",
                            style = MaterialTheme.typography.labelLarge,
                            color = labelColor,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.height(6.dp))

                        val samePersonAccounts = allAccounts.filter {
                            it.id != accountId &&
                            it.personId == currentAccount.personId &&
                            it.displayUnit() == currentUnit
                        }

                        Card(
                            Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F1F7)),
                            border = BorderStroke(1.dp, HeaderBlue.copy(alpha = 0.3f))
                        ) {
                            Column {
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable { expandedTab = if (expandedTab == "self") null else "self" }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        if (expandedTab == "self") "▲ حساب شخص" else "▼ حساب شخص",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = HeaderBlue,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        "${samePersonAccounts.size + 1} حساب",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = labelColor
                                    )
                                }

                                if (expandedTab == "self") {
                                    HorizontalDivider(color = HeaderBlue.copy(alpha = 0.2f))
                                    Column(
                                        Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 180.dp)
                                            .verticalScroll(rememberScrollState())
                                            .padding(horizontal = 8.dp, vertical = 6.dp)
                                    ) {
                                        Row(
                                            Modifier
                                                .fillMaxWidth()
                                                .clickable { destinationAccountId = null }
                                                .padding(vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
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

                                        samePersonAccounts.forEach { acc ->
                                            Row(
                                                Modifier
                                                    .fillMaxWidth()
                                                    .clickable { destinationAccountId = acc.id }
                                                    .padding(vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
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
                            }
                        }

                        Spacer(Modifier.height(6.dp))

                        val otherPersonAccounts = allAccounts.filter {
                            it.id != accountId &&
                            it.personId != currentAccount.personId &&
                            it.displayUnit() == currentUnit
                        }

                        if (otherPersonAccounts.isNotEmpty()) {
                            val groupedByPerson = otherPersonAccounts.groupBy { it.personId }

                            Card(
                                Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F1F7)),
                                border = BorderStroke(1.dp, HeaderBlue.copy(alpha = 0.3f))
                            ) {
                                Column {
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .clickable { expandedTab = if (expandedTab == "others") null else "others" }
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            if (expandedTab == "others") "▲ حساب دیگران" else "▼ حساب دیگران",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = HeaderBlue,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            "${groupedByPerson.size} شخص",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = labelColor
                                        )
                                    }

                                    if (expandedTab == "others") {
                                        HorizontalDivider(color = HeaderBlue.copy(alpha = 0.2f))
                                        Column(
                                            Modifier
                                                .fillMaxWidth()
                                                .heightIn(max = 220.dp)
                                                .verticalScroll(rememberScrollState())
                                                .padding(horizontal = 8.dp, vertical = 6.dp)
                                        ) {
                                            groupedByPerson.forEach { (personId, accounts) ->
                                                val personName = allPersons.find { it.id == personId }?.name ?: "?"

                                                Text(
                                                    "👤 $personName",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = HeaderBlue,
                                                    modifier = Modifier.padding(
                                                        start = 4.dp,
                                                        top = 6.dp,
                                                        bottom = 2.dp
                                                    )
                                                )

                                                accounts.forEach { acc ->
                                                    Row(
                                                        Modifier
                                                            .fillMaxWidth()
                                                            .clickable { destinationAccountId = acc.id }
                                                            .padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
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
                                    }
                                }
                            }
                        }
                    }
                }

                if (error.isNotBlank()) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            error,
                            color = DebitRed,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(10.dp),
                            fontWeight = FontWeight.Medium
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
                        val startMs = Jalali.parse("%04d/%02d/%02d".format(Locale.US, startY, startM, startD)) ?: 0L
                        val endMs = Jalali.parse("%04d/%02d/%02d".format(Locale.US, endY!!, endM!!, endD!!)) ?: 0L
                        if (endMs <= startMs) { error = "تاریخ پایان باید بعد از شروع باشه"; return@Button }
                    }
                    if (!isLastDayOfMonth && payoutDay <= 0) {
                        error = "روز واریز رو مشخص کن یا آخر ماه رو انتخاب کن"
                        return@Button
                    }

                    if (destinationAccountId != null) {
                        val destAcc = allAccounts.find { it.id == destinationAccountId }
                        if (destAcc != null && currentAccount != null &&
                            destAcc.displayUnit() != currentAccount.displayUnit()) {
                            error = "حساب مقصد باید هم‌ارز با حساب اصلی باشه (${currentAccount.displayUnit()})"
                            return@Button
                        }
                    }

                    error = ""
                    isChecking = true

                    scope.launch {
                        val existingPeriods = db.profitPeriod().byAccountNow(accountId)
                        val newStartMs = Jalali.parse("%04d/%02d/%02d".format(Locale.US, startY, startM, startD)) ?: 0L
                        val newEndMs = if (endY != null) Jalali.parse("%04d/%02d/%02d".format(Locale.US, endY!!, endM!!, endD!!)) ?: 0L else Long.MAX_VALUE

                        var conflictMsg: String? = null
                        for (existing in existingPeriods) {
                            if (existing.id == old?.id) continue

                            val existingStartMs = Jalali.parse("%04d/%02d/%02d".format(Locale.US, existing.startYear, existing.startMonth, existing.startDay)) ?: 0L
                            val existingEndMs = if (existing.endYear != null && existing.endMonth != null && existing.endDay != null) {
                                Jalali.parse("%04d/%02d/%02d".format(Locale.US, existing.endYear, existing.endMonth, existing.endDay)) ?: 0L
                            } else {
                                Long.MAX_VALUE
                            }

                            if (newStartMs <= existingEndMs && existingStartMs <= newEndMs) {
                                conflictMsg = "این بازه با بازه‌ی موجود تداخل داره"
                                break
                            }
                        }

                        isChecking = false

                        if (conflictMsg != null) {
                            conflictError = conflictMsg
                            return@launch
                        }

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
                enabled = !isChecking,
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
            endY ?: today[0],
            endM ?: today[1],
            endD ?: today[2],
            { y, m, d -> endY = y; endM = m; endD = d; showEndCalendar = false },
            { showEndCalendar = false }
        )
    }

    if (conflictError != null) {
        AlertDialog(
            onDismissRequest = { conflictError = null },
            containerColor = Color.White,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.border(2.dp, DebitRed, RoundedCornerShape(20.dp)),
            title = { Text("خطا", fontWeight = FontWeight.Bold, color = DebitRed) },
            text = { Text(conflictError!!, color = Color(0xFF1B1B1F)) },
            confirmButton = {
                Button(
                    onClick = { conflictError = null },
                    colors = ButtonDefaults.buttonColors(containerColor = DebitRed)
                ) { Text("باشه") }
            }
        )
    }
}
