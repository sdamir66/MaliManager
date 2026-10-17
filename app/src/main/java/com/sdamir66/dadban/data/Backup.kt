package com.sdamir66.dadban.data

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import com.sdamir66.dadban.calendar.data.CalendarSettings
import com.sdamir66.dadban.calendar.data.Event

object Backup {
    const val CURRENT_VERSION = 3

    suspend fun exportToJson(db: AppDb): String {
        val root = JSONObject()
        root.put("version", CURRENT_VERSION)

        val pp = JSONArray()
        for (p in db.persons().allNow()) {
            val o = JSONObject()
            o.put("id", p.id); o.put("name", p.name); o.put("note", p.note)
            o.put("displayOrder", p.displayOrder)
            o.put("displayedCurrencies", p.displayedCurrencies)
            o.put("isTreasury", p.isTreasury)
            pp.put(o)
        }
        root.put("persons", pp)

        val aa = JSONArray()
        for (a in db.accounts().allNow()) {
            val o = JSONObject()
            o.put("id", a.id); o.put("personId", a.personId); o.put("name", a.name)
            o.put("note", a.note); o.put("currency", a.currency)
            o.put("customUnit", a.customUnit); o.put("displayOrder", a.displayOrder)
            o.put("rateEnabled", a.rateEnabled)
            o.put("rateMode", a.rateMode)
            o.put("manualRate", a.manualRate)
            o.put("liveKey", a.liveKey)
            o.put("nature", a.nature)
            aa.put(o)
        }
        root.put("accounts", aa)

        val tt = JSONArray()
        for (a in db.accounts().allNow()) for (t in db.tx().byAccountNow(a.id)) {
            val o = JSONObject()
            o.put("accountId", t.accountId); o.put("dateMillis", t.dateMillis)
            o.put("type", t.type); o.put("amount", t.amount)
            o.put("note", t.note); o.put("isAutoProfit", t.isAutoProfit)
            o.put("profitKey", t.profitKey)
            tt.put(o)
        }
        root.put("transactions", tt)

        val pp2 = JSONArray()
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

        val eventsArr = JSONArray()
        for (event in db.eventDao().allNow()) {
            val o = JSONObject()
            o.put("title", event.title)
            o.put("description", event.description)
            o.put("calendarType", event.calendarType.name)
            o.put("month", event.month)
            o.put("day", event.day)
            o.put("year", event.year ?: JSONObject.NULL)
            o.put("isHoliday", event.isHoliday)
            o.put("category", event.category.name)
            o.put("color", event.color)
            o.put("isUserCreated", event.isUserCreated)
            o.put("reminderMinutesBefore", event.reminderMinutesBefore ?: JSONObject.NULL)
            eventsArr.put(o)
        }
        root.put("userEvents", eventsArr)

        val settingsObj = db.calendarSettingsDao().getNow()
        if (settingsObj != null) {
            val o = JSONObject()
            o.put("eidFitrOffset", settingsObj.eidFitrOffset)
            o.put("eidFitrHijriYear", settingsObj.eidFitrHijriYear ?: JSONObject.NULL)
            o.put("defaultCalendar", settingsObj.defaultCalendar.name)
            o.put("showGregorianSmall", settingsObj.showGregorianSmall)
            o.put("showHijriSmall", settingsObj.showHijriSmall)
            o.put("showHolidays", settingsObj.showHolidays)
            o.put("showReligiousNonHoliday", settingsObj.showReligiousNonHoliday)
            o.put("showNationalNonHoliday", settingsObj.showNationalNonHoliday)
            o.put("showGlobalEvents", settingsObj.showGlobalEvents)
            o.put("showUserEvents", settingsObj.showUserEvents)
            o.put("locationMode", settingsObj.locationMode.name)
            o.put("cityName", settingsObj.cityName)
            o.put("latitude", settingsObj.latitude)
            o.put("longitude", settingsObj.longitude)
            o.put("showPrayerTimes", settingsObj.showPrayerTimes)
            root.put("calendarSettings", o)
        }

        return root.toString(2)
    }

    suspend fun restoreOrExport(context: Context, db: AppDb, uri: Uri, restore: Boolean) {
        if (restore) {
            val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: return
            val root = JSONObject(text)
            val backupVersion = root.optInt("version", 1)

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
                    displayedCurrencies = o.optString("displayedCurrencies", ""),
                    isTreasury = o.optBoolean("isTreasury", false)
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
                    displayOrder = o.optInt("displayOrder", 0),
                    rateEnabled = o.optBoolean("rateEnabled", false),
                    rateMode = o.optString("rateMode", "manual"),
                    manualRate = o.optDouble("manualRate", 0.0),
                    liveKey = o.optString("liveKey", ""),
                    // ✅ اگه nature نبود (بکاپ قدیمی): پیش‌فرض "credit"
                    nature = o.optString("nature", "credit")
                ))
                accountIdMap[old] = id
            }

            val txArr = root.optJSONArray("transactions") ?: JSONArray()
            for (i in 0 until txArr.length()) {
                val o = txArr.getJSONObject(i)
                val aid = accountIdMap[o.optLong("accountId")] ?: continue
                val amount = if (backupVersion >= 2) o.getDouble("amount")
                             else o.getLong("amount").toDouble()
                db.tx().insert(Transaction(
                    accountId = aid, dateMillis = o.getLong("dateMillis"),
                    type = o.getString("type"), amount = amount,
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

            if (backupVersion >= 2) {
                db.eventDao().deleteAllUserEvents()

                val eventsArr = root.optJSONArray("userEvents") ?: JSONArray()
                for (i in 0 until eventsArr.length()) {
                    val o = eventsArr.getJSONObject(i)
                    if (!o.optBoolean("isUserCreated", false)) continue
                    db.eventDao().insert(Event(
                        title = o.getString("title"),
                        description = o.optString("description", ""),
                        calendarType = com.sdamir66.dadban.calendar.data.CalendarType.valueOf(o.getString("calendarType")),
                        month = o.getInt("month"),
                        day = o.getInt("day"),
                        year = if (o.isNull("year")) null else o.getInt("year"),
                        isHoliday = o.optBoolean("isHoliday", false),
                        category = com.sdamir66.dadban.calendar.data.EventCategory.valueOf(o.getString("category")),
                        color = o.optString("color", "#4C5FD7"),
                        isUserCreated = true,
                        reminderMinutesBefore = if (o.isNull("reminderMinutesBefore")) null else o.getInt("reminderMinutesBefore")
                    ))
                }

                val settingsObj = root.optJSONObject("calendarSettings")
                if (settingsObj != null) {
                    db.calendarSettingsDao().insert(CalendarSettings(
                        eidFitrOffset = settingsObj.optInt("eidFitrOffset", 0),
                        eidFitrHijriYear = if (settingsObj.isNull("eidFitrHijriYear")) null
                                          else settingsObj.getInt("eidFitrHijriYear"),
                        defaultCalendar = com.sdamir66.dadban.calendar.data.CalendarType.valueOf(
                            settingsObj.optString("defaultCalendar", "JALALI")
                        ),
                        showGregorianSmall = settingsObj.optBoolean("showGregorianSmall", true),
                        showHijriSmall = settingsObj.optBoolean("showHijriSmall", true),
                        showHolidays = settingsObj.optBoolean("showHolidays", true),
                        showReligiousNonHoliday = settingsObj.optBoolean("showReligiousNonHoliday", false),
                        showNationalNonHoliday = settingsObj.optBoolean("showNationalNonHoliday", false),
                        showGlobalEvents = settingsObj.optBoolean("showGlobalEvents", false),
                        showUserEvents = settingsObj.optBoolean("showUserEvents", true),
                        locationMode = com.sdamir66.dadban.calendar.data.LocationMode.valueOf(
                            settingsObj.optString("locationMode", "MANUAL")
                        ),
                        cityName = settingsObj.optString("cityName", "تهران"),
                        latitude = settingsObj.optDouble("latitude", 35.6892),
                        longitude = settingsObj.optDouble("longitude", 51.3890),
                        showPrayerTimes = settingsObj.optBoolean("showPrayerTimes", true)
                    ))
                }
            }
        } else {
            val json = exportToJson(db)
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(json) }
        }
    }
}
