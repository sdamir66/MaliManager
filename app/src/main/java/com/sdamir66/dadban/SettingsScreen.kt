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
import androidx.compose.foundation.lazy.items
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
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (e: Exception) { e.printStackTrace() }
            saveBackupFolderUri(context, uri)
        }
    }

    // ✅ Launcher برای مجوز GPS
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.values.all { it }
        if (granted) {
            scope.launch {
                calendarSettings?.let { current ->
                    calendarSettings = current.copy(locationMode = LocationMode.GPS)
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
    }

    var autoBackupExists by remember { mutableStateOf(false) }
    var autoBackupInfo by remember { mutableStateOf("") }
    var confirmRestore by remember { mutableStateOf(false) }
    var confirmRemoveFolder by remember { mutableStateOf(false) }
    var customFolderName by remember { mutableStateOf<String?>(null) }
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
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // ═══════════════════════════════════════════════
            // تنظیمات تقویم
            // ═══════════════════════════════════════════════
            item {
                Text(
                    "تنظیمات تقویم",
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
                        Text("تقویم پیش‌فرض", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = Color(0xFF1B1B1F))
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

                        HorizontalDivider(color = Color(0xFFEEEEEE), modifier = Modifier.padding(vertical = 12.dp))

                        SettingSwitch(
                            title = "نمایش تاریخ میلادی زیر روز",
                            checked = calendarSettings!!.showGregorianSmall
                        ) {
                            scope.launch {
                                calendarSettings = calendarSettings!!.copy(showGregorianSmall = it)
                                db.calendarSettingsDao().insert(calendarSettings!!)
                            }
                        }

                        SettingSwitch(
                            title = "نمایش تاریخ قمری زیر روز",
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
                Text(
                    "اصلاح عید فطر",
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
                        Text(
                            "اگه تقویم قمری با اعلام رسمی فرق داره، اینجا تنظیم کن. " +
                            "رویدادهای بعد از عید فطر خودکار جابه‌جا می‌شن.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF5C5D72)
                        )
                        Spacer(Modifier.height(12.dp))

                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = {
                                scope.launch {
                                    val newOffset = (calendarSettings!!.eidFitrOffset - 1).coerceAtLeast(-3)
                                    calendarSettings = calendarSettings!!.copy(
                                        eidFitrOffset = newOffset,
                                        eidFitrHijriYear = 1447
                                    )
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
                                    val newOffset = (calendarSettings!!.eidFitrOffset + 1).coerceAtMost(3)
                                    calendarSettings = calendarSettings!!.copy(
                                        eidFitrOffset = newOffset,
                                        eidFitrHijriYear = 1447
                                    )
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
                                Text("برگشت به تقویم پیش‌فرض", color = HeaderBlue)
                            }
                        }
                    }
                }
            }

            // ═══════════════════════════════════════════════
            // نمایش رویدادها
            // ═══════════════════════════════════════════════
            item {
                Text(
                    "نمایش رویدادها",
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
                        SettingSwitch(
                            title = "تعطیلات رسمی",
                            checked = calendarSettings!!.showHolidays
                        ) {
                            scope.launch {
                                calendarSettings = calendarSettings!!.copy(showHolidays = it)
                                db.calendarSettingsDao().insert(calendarSettings!!)
                            }
                        }
                        SettingSwitch(
                            title = "رویدادهای مذهبی غیرتعطیل",
                            checked = calendarSettings!!.showReligiousNonHoliday
                        ) {
                            scope.launch {
                                calendarSettings = calendarSettings!!.copy(showReligiousNonHoliday = it)
                                db.calendarSettingsDao().insert(calendarSettings!!)
                            }
                        }
                        SettingSwitch(
                            title = "رویدادهای ملی غیرتعطیل",
                            checked = calendarSettings!!.showNationalNonHoliday
                        ) {
                            scope.launch {
                                calendarSettings = calendarSettings!!.copy(showNationalNonHoliday = it)
                                db.calendarSettingsDao().insert(calendarSettings!!)
                            }
                        }
                        SettingSwitch(
                            title = "رویدادهای جهانی",
                            checked = calendarSettings!!.showGlobalEvents
                        ) {
                            scope.launch {
                                calendarSettings = calendarSettings!!.copy(showGlobalEvents = it)
                                db.calendarSettingsDao().insert(calendarSettings!!)
                            }
                        }
                    }
                }
            }

            // ═══════════════════════════════════════════════
            // رنگ رویدادها
            // ═══════════════════════════════════════════════
            item {
                Text(
                    "رنگ رویدادها",
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
                        ColorPickerRow(
                            title = "تعطیلات رسمی",
                            selectedColor = calendarSettings!!.colorHoliday
                        ) { newColor ->
                            scope.launch {
                                calendarSettings = calendarSettings!!.copy(colorHoliday = newColor)
                                db.calendarSettingsDao().insert(calendarSettings!!)
                            }
                        }

                        ColorPickerRow(
                            title = "مذهبی غیرتعطیل",
                            selectedColor = calendarSettings!!.colorReligious
                        ) { newColor ->
                            scope.launch {
                                calendarSettings = calendarSettings!!.copy(colorReligious = newColor)
                                db.calendarSettingsDao().insert(calendarSettings!!)
                            }
                        }

                        ColorPickerRow(
                            title = "ملی غیرتعطیل",
                            selectedColor = calendarSettings!!.colorNational
                        ) { newColor ->
                            scope.launch {
                                calendarSettings = calendarSettings!!.copy(colorNational = newColor)
                                db.calendarSettingsDao().insert(calendarSettings!!)
                            }
                        }

                        ColorPickerRow(
                            title = "جهانی",
                            selectedColor = calendarSettings!!.colorGlobal
                        ) { newColor ->
                            scope.launch {
                                calendarSettings = calendarSettings!!.copy(colorGlobal = newColor)
                                db.calendarSettingsDao().insert(calendarSettings!!)
                            }
                        }
                    }
                }
            }

            // ═══════════════════════════════════════════════
            // اوقات شرعی
            // ═══════════════════════════════════════════════
            item {
                Text(
                    "اوقات شرعی",
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
                        SettingSwitch(
                            title = "نمایش اوقات شرعی",
                            checked = calendarSettings!!.showPrayerTimes
                        ) {
                            scope.launch {
                                calendarSettings = calendarSettings!!.copy(showPrayerTimes = it)
                                db.calendarSettingsDao().insert(calendarSettings!!)
                            }
                        }

                        HorizontalDivider(color = Color(0xFFEEEEEE), modifier = Modifier.padding(vertical = 12.dp))

                        Text("موقعیت مکانی", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = Color(0xFF1B1B1F))
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
                                // ✅ درخواست مجوز GPS
                                if (!LocationHelper.hasLocationPermission(context)) {
                                    permissionLauncher.launch(
                                        arrayOf(
                                            android.Manifest.permission.ACCESS_FINE_LOCATION,
                                            android.Manifest.permission.ACCESS_COARSE_LOCATION
                                        )
                                    )
                                } else {
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
                        }

                        Spacer(Modifier.height(12.dp))

                        if (calendarSettings!!.locationMode == LocationMode.MANUAL) {
                            OutlinedButton(
                                onClick = { showCityPicker = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = HeaderBlue)
                            ) {
                                Text("شهر فعلی: ${calendarSettings!!.cityName}", color = HeaderBlue)
                            }
                        } else {
                            Text(
                                "موقعیت فعلی: ${calendarSettings!!.cityName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = CreditGreen,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            // ═══════════════════════════════════════════════
            // پشتیبان و بازیابی
            // ═══════════════════════════════════════════════
            item {
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
                Text(
                    "بکاپ خودکار",
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
                                    Text("بکاپ خودکار فعال", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = Color(0xFF1B1B1F))
                                    Text("آخرین: $autoBackupInfo", style = MaterialTheme.typography.bodySmall, color = Color(0xFF5C5D72))
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            OutlinedButton(
                                onClick = { confirmRestore = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = HeaderBlue)
                            ) {
                                Text("بازیابی از بکاپ خودکار")
                            }
                        } else {
                            Text(
                                "هنوز بکاپ خودکاری ذخیره نشده",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF5C5D72)
                            )
                        }
                    }
                }
            }

            // ═══════════════════════════════════════════════
            // محل ذخیره بکاپ
            // ═══════════════════════════════════════════════
            item {
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("محل ذخیره بکاپ خودکار", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = Color(0xFF1B1B1F))
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (customFolderName != null) "پوشه فعلی: $customFolderName" else "پیش‌فرض: حافظه داخلی برنامه",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (customFolderName != null) CreditGreen else Color(0xFF5C5D72)
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = { pickFolder.launch(null) },
                            modifier = Modifier.fillMaxWidth(),
                            shape =
