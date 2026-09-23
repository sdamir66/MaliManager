package com.sdamir66.dadban

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import com.sdamir66.dadban.calendar.data.CalendarSettings
import com.sdamir66.dadban.calendar.data.CalendarType
import com.sdamir66.dadban.calendar.data.LocationMode
import com.sdamir66.dadban.calendar.prayer.LocationHelper
import com.sdamir66.dadban.data.AppDb
import com.sdamir66.dadban.ui.theme.BgLight
import com.sdamir66.dadban.ui.theme.CreditGreen
import com.sdamir66.dadban.ui.theme.DebitRed
import com.sdamir66.dadban.ui.theme.HeaderBlue
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

    // ═══ تنظیمات تقویم ═══
    var calendarSettings by remember { mutableStateOf<CalendarSettings?>(null) }
    var showCityPicker by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        calendarSettings = db.calendarSettingsDao().getNow() ?: CalendarSettings()
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

    if (calendarSettings == null) return

    Column(Modifier.fillMaxSize().background(BgLight)) {
        PageHeader(title = "تنظیمات", subtitle = "مدیریت برنامه و تقویم", onBackClick = onBack)

        Spacer(Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // ═══════════════════════════════════════════════
            // تنظیمات تقویم
            // ═══════════════════════════════════════════════
            item {
                Text("تنظیمات تقویم", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF1B1B1F), modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
            }

            item {
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        // تقویم پیش‌فرض
                        Text("تقویم پیش‌فرض", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Row {
                            CalendarTypeOption(
                                "جلالی",
                                calendarSettings!!.defaultCalendar == CalendarType.JALALI
                            ) {
                                scope.launch {
                                    calendarSettings = calendarSettings!!.copy(defaultCalendar = CalendarType.JALALI)
                                    db.calendarSettingsDao().insert(calendarSettings!!)
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            CalendarTypeOption(
                                "قمری",
                                calendarSettings!!.defaultCalendar == CalendarType.HIJRI
                            ) {
                                scope.launch {
                                    calendarSettings = calendarSettings!!.copy(defaultCalendar = CalendarType.HIJRI)
                                    db.calendarSettingsDao().insert(calendarSettings!!)
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            CalendarTypeOption(
                                "میلادی",
                                calendarSettings!!.defaultCalendar == CalendarType.GREGORIAN
                            ) {
                                scope.launch {
                                    calendarSettings = calendarSettings!!.copy(defaultCalendar = CalendarType.GREGORIAN)
                                    db.calendarSettingsDao().insert(calendarSettings!!)
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        // نمایش میلادی کوچیک
                        SettingSwitch(
                            title = "نمایش تاریخ میلادی کوچیک",
                            checked = calendarSettings!!.showGregorianSmall
                        ) {
                            scope.launch {
                                calendarSettings = calendarSettings!!.copy(showGregorianSmall = it)
                                db.calendarSettingsDao().insert(calendarSettings!!)
                            }
                        }

                        // نمایش قمری کوچیک
                        SettingSwitch(
                            title = "نمایش تاریخ قمری کوچیک",
                            checked = calendarSettings!!.showHijriSmall
                        ) {
                            scope.launch {
                                calendarSettings = calendarSettings!!.copy(showHijriSmall = it)
                                db.calendarSettingsDao().insert(calendarSettings!!)
                            }
                        }
                    }
                }
            }

            // ═══════════════════════════════════════════════
            // اصلاح عید فطر
            // ═══════════════════════════════════════════════
            item {
                Text("اصلاح عید فطر", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF1B1B1F), modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
            }

            item {
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "اگه عید فطر طبق اعلام رسمی با تقویم ما فرق داره، اینجا تنظیم کن. " +
                            "رویدادهای بعدی خودکار جابه‌جا می‌شن.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                        Spacer(Modifier.height(12.dp))

                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = {
                                scope.launch {
                                    val newOffset = (calendarSettings!!.eidFitrOffset - 1).coerceAtLeast(-2)
                                    calendarSettings = calendarSettings!!.copy(eidFitrOffset = newOffset)
                                    db.calendarSettingsDao().insert(calendarSettings!!)
                                }
                            }) {
                                Text("−", fontSize = 24.sp, color = HeaderBlue, fontWeight = FontWeight.Bold)
                            }
                            Text(
                                "${if (calendarSettings!!.eidFitrOffset > 0) "+" else ""}${calendarSettings!!.eidFitrOffset} روز",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (calendarSettings!!.eidFitrOffset == 0) Color.Gray else DebitRed
                            )
                            IconButton(onClick = {
                                scope.launch {
                                    val newOffset = (calendarSettings!!.eidFitrOffset + 1).coerceAtMost(2)
                                    calendarSettings = calendarSettings!!.copy(eidFitrOffset = newOffset)
                                    db.calendarSettingsDao().insert(calendarSettings!!)
                                }
                            }) {
                                Text("+", fontSize = 24.sp, color = HeaderBlue, fontWeight = FontWeight.Bold)
                            }
                        }

                        if (calendarSettings!!.eidFitrOffset != 0) {
                            Spacer(Modifier.height(8.dp))
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        calendarSettings = calendarSettings!!.copy(eidFitrOffset = 0, eidFitrHijriYear = null)
                                        db.calendarSettingsDao().insert(calendarSettings!!)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("برگشت به تقویم محاسباتی", color = HeaderBlue)
                            }
                        }
                    }
                }
            }

            // ═══════════════════════════════════════════════
            // اوقات شرعی
            // ═══════════════════════════════════════════════
            item {
                Text("اوقات شرعی", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF1B1B1F), modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
            }

            item {
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        SettingSwitch(
                            title = "نمایش اوقات شرعی",
                            checked = calendarSettings!!.showPrayerTimes
                        ) {
                            scope.launch {
                                calendarSettings = calendarSettings!!.copy(showPrayerTimes = it)
                                db.calendarSettingsDao().insert(calendarSettings!!)
                            }
                        }

                        HorizontalDivider(color = Color(0xFFEEEEEE), modifier = Modifier.padding(vertical = 8.dp))

                        // موقعیت مکانی
                        Text("موقعیت مکانی", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Row {
                            CalendarTypeOption(
                                "دستی",
                                calendarSettings!!.locationMode == LocationMode.MANUAL
                            ) {
                                scope.launch {
                                    calendarSettings = calendarSettings!!.copy(locationMode = LocationMode.MANUAL)
                                    db.calendarSettingsDao().insert(calendarSettings!!)
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            CalendarTypeOption(
                                "GPS",
                                calendarSettings!!.locationMode == LocationMode.GPS
                            ) {
                                scope.launch {
                                    calendarSettings = calendarSettings!!.copy(locationMode = LocationMode.GPS)
                                    db.calendarSettingsDao().insert(calendarSettings!!)
                                    val loc = LocationHelper.getCurrentLocation(context)
                                    if (loc != null) {
                                        calendarSettings = calendarSettings!!.copy(
                                            latitude = loc.first,
                                            longitude = loc.second,
                                            cityName = "موقعیت فعلی"
                                        )
                                        db.calendarSettingsDao().insert(calendarSettings!!)
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        // انتخاب شهر
                        if (calendarSettings!!.locationMode == LocationMode.MANUAL) {
                            OutlinedButton(
                                onClick = { showCityPicker = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = HeaderBlue)
                            ) {
                                Text("شهر فعلی: ${calendarSettings!!.cityName}", color = HeaderBlue)
                            }
                        }
                    }
                }
            }

            // ═══════════════════════════════════════════════
            // پشتیبان و بازیابی
            // ═══════════════════════════════════════════════
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
                        SettingRow("گرفتن بکاپ", "ذخیره در حافظه یا Google Drive", "⬇", onClick = { create.launch("Dadban-backup.json") })
                        HorizontalDivider(color = Color(0xFFEEEEEE))
                        SettingRow("بازیابی بکاپ", "از فایل JSON", "⬆", onClick = { open.launch(arrayOf("application/json", "text/*")) })
                    }
                }
            }

            // ═══════════════════════════════════════════════
            // بکاپ خودکار
            // ═══════════════════════════════════════════════
            item {
                Spacer(Modifier.height(8.dp))
                Text("بکاپ خودکار", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF1B1B1F), modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
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
                            Text("هنوز بکاپ خودکاری ذخیره نشده", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                        }
                    }
                }
            }

            // ═══════════════════════════════════════════════
            // محل ذخیره
            // ═══════════════════════════════════════════════
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

            // ═══════════════════════════════════════════════
            // درباره
            // ═══════════════════════════════════════════════
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
                                Text("📅", fontSize = 24.sp)
                            }
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text("دادبان", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("نسخه ۲.۰", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
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

    // ═══ دیالوگ انتخاب شهر ═══
    if (showCityPicker && calendarSettings != null) {
        CityPickerDialog(
            currentCity = calendarSettings!!.cityName,
            onCitySelected = { city ->
                scope.launch {
                    calendarSettings = calendarSettings!!.copy(
                        cityName = city.name,
                        latitude = city.latitude,
                        longitude = city.longitude
                    )
                    db.calendarSettingsDao().insert(calendarSettings!!)
                }
                showCityPicker = false
            },
            onDismiss = { showCityPicker = false }
        )
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
// کامپوننت‌های کمکی
// ═══════════════════════════════════════════════════════

@Composable
private fun CalendarTypeOption(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        color = if (isSelected) HeaderBlue else Color(0xFFF0F1F7),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) Color.White else Color(0xFF5C5D72)
        )
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = HeaderBlue
            )
        )
    }
}

@Composable
private fun SettingRow(title: String, subtitle: String, icon: String, onClick: () -> Unit) {
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

@Composable
private fun CityPickerDialog(
    currentCity: String,
    onCitySelected: (com.sdamir66.dadban.calendar.prayer.CityLocation) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val allCities = LocationHelper.iranianCities
    val filteredCities = remember(searchQuery) {
        if (searchQuery.isBlank()) allCities
        else allCities.filter { it.name.contains(searchQuery) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.border(2.dp, HeaderBlue, RoundedCornerShape(20.dp)),
        title = { Text("انتخاب شهر", fontWeight = FontWeight.Bold, color = HeaderBlue) },
        text = {
            Column {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("جستجو...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = HeaderBlue,
                        focusedLabelColor = HeaderBlue,
                        cursorColor = HeaderBlue
                    )
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(
                    modifier = Modifier.heightIn(max = 350.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(filteredCities.size) { index ->
                        val city = filteredCities[index]
                        Card(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onCitySelected(city) },
                            colors = CardDefaults.cardColors(
                                containerColor = if (city.name == currentCity)
                                    HeaderBlue.copy(alpha = 0.15f) else Color(0xFFF8F9FC)
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                city.name,
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (city.name == currentCity) FontWeight.Bold else FontWeight.Normal,
                                color = if (city.name == currentCity) HeaderBlue else Color(0xFF1B1B1F)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("بستن", color = HeaderBlue, fontWeight = FontWeight.Bold)
            }
        }
    )
}
