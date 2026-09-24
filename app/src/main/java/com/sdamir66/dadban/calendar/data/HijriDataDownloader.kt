private fun parseJson(jsonText: String, jalaliYear: Int): List<HijriCache> {
    val root = JSONObject(jsonText)
    val yearKey = root.keys().next()
    val monthsArray = root.getJSONArray(yearKey)

    val result = mutableListOf<HijriCache>()

    for (monthIndex in 0 until monthsArray.length()) {
        val monthData = monthsArray.getJSONObject(monthIndex)
        val header = monthData.getJSONObject("header")
        val qamariHeader = header.getString("qamari")

        val parts = qamariHeader.split("-").map { it.trim() }
        val hijriYear = parts.last().toIntOrNull() ?: 0
        val hijriMonths = parts.dropLast(1)

        val jMonth = monthIndex + 1

        // ✅ متغیرهای محلی برای هر ماه
        var lastHijriMonth: String? = null
        var hijriMonthIndex = 0

        // Parse رویدادها
        val eventsArray = monthData.optJSONArray("events") ?: JSONArray()
        val eventsByDay = mutableMapOf<Int, MutableList<JSONObject>>()
        for (i in 0 until eventsArray.length()) {
            val event = eventsArray.getJSONObject(i)
            val jDate = event.optString("jDate", "")
            val day = jDate.substringAfterLast("/").toIntOrNull() ?: continue
            eventsByDay.getOrPut(day) { mutableListOf() }.add(event)
        }

        // Parse روزها
        val weeksArray = monthData.optJSONArray("weeks") ?: JSONArray()

        for (i in 0 until weeksArray.length()) {
            val week = weeksArray.getJSONObject(i)
            if (week.optBoolean("disabled", false)) continue

            val dayObj = week.getJSONObject("day")
            val jDay = dayObj.optString("j", "").toIntOrNull() ?: continue
            val qDay = dayObj.optString("q", "").toIntOrNull() ?: continue

            // ✅ اگه qDay == 1 و ماه قبلی ست شده → ماه بعدی
            if (qDay == 1 && lastHijriMonth != null) {
                hijriMonthIndex = (hijriMonthIndex + 1).coerceAtMost(hijriMonths.size - 1)
            }

            val hijriMonth = hijriMonths.getOrNull(hijriMonthIndex) ?: hijriMonths.lastOrNull() ?: ""
            lastHijriMonth = hijriMonth

            val dayEvents = eventsByDay[jDay] ?: mutableListOf()
            val eventsJson = JSONArray(dayEvents).toString()
            val isHoliday = week.optBoolean("holiday", false)

            result.add(HijriCache(
                jalaliDate = "%04d/%02d/%02d".format(jalaliYear, jMonth, jDay),
                jalaliYear = jalaliYear,
                jalaliMonth = jMonth,
                jalaliDay = jDay,
                hijriDay = qDay,
                hijriMonth = hijriMonth,
                hijriYear = hijriYear,
                isHoliday = isHoliday,
                eventsJson = eventsJson
            ))
        }
    }

    return result
}
