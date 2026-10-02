package com.sdamir66.dadban.tools

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

// ═══════════════════════════════════════════════════════════════
//  ToolsRepository
//  دریافت قیمت‌ها از API tgju.org
// ═══════════════════════════════════════════════════════════════

object ToolsRepository {

    private const val BASE_URL = "https://api.tgju.org/v1/widget/tmp"
    private const val TIMEOUT_MS = 15_000

    // ═══════════════════════════════════════════════════════════
    //  دریافت قیمت‌های چند key
    // ═══════════════════════════════════════════════════════════
    suspend fun fetchPrices(keys: List<String>): Result<List<TgjuPrice>> =
        withContext(Dispatchers.IO) {
            try {
                if (keys.isEmpty()) return@withContext Result.success(emptyList())

                val urlStr = "$BASE_URL?keys=${keys.joinToString(",")}"
                val jsonText = fetchUrl(urlStr)
                val list = parseJson(jsonText)
                Result.success(list)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    // ═══════════════════════════════════════════════════════════
    //  HTTP GET
    // ═══════════════════════════════════════════════════════════
    private fun fetchUrl(urlStr: String): String {
        val url = URL(urlStr)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = TIMEOUT_MS
        conn.readTimeout = TIMEOUT_MS
        conn.setRequestProperty("Accept", "application/json")
        conn.setRequestProperty("User-Agent", "Dadban/1.0")

        return try {
            val code = conn.responseCode
            if (code !in 200..299) {
                throw java.io.IOException("HTTP $code")
            }
            conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  پارس JSON
    // ═══════════════════════════════════════════════════════════
    private fun parseJson(jsonText: String): List<TgjuPrice> {
        val root = JSONObject(jsonText)
        val response = root.optJSONObject("response") ?: return emptyList()
        val indicators = response.optJSONArray("indicators") ?: return emptyList()

        val result = mutableListOf<TgjuPrice>()

        for (i in 0 until indicators.length()) {
            val obj = indicators.optJSONObject(i) ?: continue

            val itemId = obj.optString("item_id", "")
            val title = obj.optString("title", "")
            val priceStr = obj.optString("p", "0").replace(",", "")
            val price = priceStr.toDoubleOrNull() ?: 0.0

            // تغییر (مقدار)
            val dStr = obj.optString("d", "0").replace(",", "")
            val change = dStr.toDoubleOrNull() ?: 0.0

            // درصد تغییر
            val dpStr = obj.optString("dp", "0").replace(",", "")
            val changePercent = dpStr.toDoubleOrNull() ?: 0.0

            // جهت تغییر
            val direction = obj.optString("dt", "")

            // کمترین / بیشترین
            val lowStr = obj.optString("l", "0").replace(",", "")
            val highStr = obj.optString("h", "0").replace(",", "")
            val low = lowStr.toDoubleOrNull() ?: 0.0
            val high = highStr.toDoubleOrNull() ?: 0.0

            // زمان
            val time = obj.optString("t", "")

            if (itemId.isNotBlank() && title.isNotBlank()) {
                result.add(
                    TgjuPrice(
                        key = itemId,
                        title = title,
                        price = price,
                        change = change,
                        changePercent = changePercent,
                        direction = direction,
                        low = low,
                        high = high,
                        time = time
                    )
                )
            }
        }

        return result
    }
}
