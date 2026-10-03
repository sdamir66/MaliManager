package com.sdamir66.dadban.tools

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object ToolsRepository {

    private const val TAG = "ToolsRepository"
    private const val TGJU_URL = "https://api.tgju.org/v1/widget/tmp"
    private const val NOBITEX_URL = "https://apiv2.nobitex.ir/market/stats"
    private const val TIMEOUT_MS = 15_000

    suspend fun fetchPrices(keys: List<String>): Result<List<TgjuPrice>> =
        withContext(Dispatchers.IO) {
            try {
                val result = mutableListOf<TgjuPrice>()

                val cryptoKeys = keys.filter { it in PriceCatalog.CRYPTO_KEYS }
                val fiatKeys = keys.filter { it !in PriceCatalog.CRYPTO_KEYS }

                // ═══ ۱. ارز، طلا، سکه (tgju) ═══
                if (fiatKeys.isNotEmpty()) {
                    try {
                        val tgjuUrl = "$TGJU_URL?keys=${fiatKeys.joinToString(",")}"
                        val tgjuJson = fetchUrl(tgjuUrl)
                        val parsed = parseTgjuJson(tgjuJson, fiatKeys)
                        Log.d(TAG, "tgju: requested=${fiatKeys.size}, parsed=${parsed.size}")
                        result.addAll(parsed)

                        val returnedKeys = parsed.map { it.key }.toSet()
                        val missing = fiatKeys.filter { it !in returnedKeys }
                        if (missing.isNotEmpty()) {
                            Log.w(TAG, "tgju missing keys: $missing")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "tgju fetch failed", e)
                    }
                }

                // ═══ ۲. رمزارزها (نوبیتکس) ═══
                if (cryptoKeys.isNotEmpty()) {
                    try {
                        val cryptoSymbols = cryptoKeys.mapNotNull { itemId ->
                            PriceCatalog.CRYPTO_SYMBOLS[itemId]
                        }
                        if (cryptoSymbols.isNotEmpty()) {
                            val symbolsParam = cryptoSymbols.joinToString(",")
                            val nobitexUrl = "$NOBITEX_URL?srcCurrency=$symbolsParam&dstCurrency=rls"
                            val nobitexJson = fetchUrl(nobitexUrl)
                            val parsed = parseNobitexJson(nobitexJson, cryptoKeys)
                            Log.d(TAG, "nobitex: requested=${cryptoKeys.size}, parsed=${parsed.size}")
                            result.addAll(parsed)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "nobitex fetch failed", e)
                    }
                }

                Log.d(TAG, "fetchPrices total=${result.size}")
                Result.success(result)
            } catch (e: Exception) {
                Log.e(TAG, "fetchPrices fatal", e)
                Result.failure(e)
            }
        }

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

    /**
     * tgju فیلدهای مهمی که برمی‌گردونه:
     *   name      → اسم واقعی (مثل "sekee", "nim", "price_dollar_rl")
     *   item_id   → عدد داخلی tgju (قابل اعتماد نیست! ممکنه با رمزارزها قاطی شه)
     *   title     → عنوان فارسی
     *   p, h, l, d, dp, dt, t, updated_at
     *
     * پس ما از `name` استفاده می‌کنیم به عنوان key، نه item_id.
     * اگه `name` خالی بود، fallback به item_id.
     */
    private fun parseTgjuJson(
        jsonText: String,
        requestedKeys: List<String>
    ): List<TgjuPrice> {
        val root = JSONObject(jsonText)
        val response = root.optJSONObject("response") ?: return emptyList()
        val indicators = response.optJSONArray("indicators") ?: return emptyList()

        val result = mutableListOf<TgjuPrice>()

        for (i in 0 until indicators.length()) {
            val obj = indicators.optJSONObject(i) ?: continue

            // ─── اولویت: name → بعد item_id ───
            val name = obj.optString("name", "").trim()
            val itemIdRaw = obj.opt("item_id")
            val itemId = when (itemIdRaw) {
                is Number -> itemIdRaw.toLong().toString()
                is String -> itemIdRaw
                else -> ""
            }

            // ─── key نهایی: name اگه توی requestedKeys بود، وگرنه item_id ───
            val key = when {
                name.isNotBlank() && name in requestedKeys -> name
                itemId.isNotBlank() && itemId in requestedKeys -> itemId
                else -> {
                    // شاید اسم با یه پیشوندی اومده باشه (مثل "price_dollar_rl_2")
                    val matchedName = requestedKeys.find {
                        name.equals(it, ignoreCase = true) ||
                        name.startsWith("${it}_") ||
                        name.startsWith("${it}-")
                    }
                    matchedName ?: continue
                }
            }

            val title = obj.optString("title", "").trim().ifBlank {
                PriceCatalog.DEFAULT_TITLES[key] ?: key
            }

            result.add(
                TgjuPrice(
                    key = key,
                    title = title,
                    price = parseDouble(obj.opt("p")),
                    change = parseDouble(obj.opt("d")),
                    changePercent = parseDouble(obj.opt("dp")),
                    direction = obj.optString("dt", ""),
                    low = parseDouble(obj.opt("l")),
                    high = parseDouble(obj.opt("h")),
                    time = obj.optString("updated_at", "").ifBlank {
                        obj.optString("t", "")
                    }
                )
            )
        }

        return result
    }

    private fun parseNobitexJson(
        jsonText: String,
        requestedKeys: List<String>
    ): List<TgjuPrice> {
        val root = JSONObject(jsonText)
        val stats = root.optJSONObject("stats") ?: return emptyList()

        Log.d(TAG, "nobitex stats keys: ${stats.keys().asSequence().toList()}")

        // ─── map معکوس: "btc" / "btc-rls" → "398096" ───
        val symbolToKey = mutableMapOf<String, String>()
        PriceCatalog.CRYPTO_SYMBOLS.forEach { (key, sym) ->
            val lower = sym.lowercase()
            symbolToKey[lower] = key
            symbolToKey["$lower-rls"] = key
            symbolToKey["$lower-irt"] = key
        }

        val result = mutableListOf<TgjuPrice>()

        stats.keys().forEach { symbol ->
            val stat = stats.optJSONObject(symbol) ?: return@forEach
            val lowerSymbol = symbol.lowercase()

            val itemId = symbolToKey[lowerSymbol]
                ?: PriceCatalog.CRYPTO_SYMBOLS.entries
                    .find { lowerSymbol.startsWith("${it.value.lowercase()}-") }
                    ?.key
                ?: run {
                    Log.w(TAG, "nobitex unknown symbol: $symbol")
                    return@forEach
                }

            if (itemId !in requestedKeys) return@forEach

            val title = PriceCatalog.CRYPTO_TITLES[itemId] ?: symbol

            val price = parseDouble(stat.opt("latest"))
            val changePercent = parseDouble(stat.opt("dayChange"))
            val change = price * changePercent / 100.0

            result.add(
                TgjuPrice(
                    key = itemId,
                    title = title,
                    price = price,
                    change = change,
                    changePercent = changePercent,
                    direction = when {
                        changePercent > 0 -> "high"
                        changePercent < 0 -> "low"
                        else -> ""
                    },
                    low = parseDouble(stat.opt("dayLow")),
                    high = parseDouble(stat.opt("dayHigh")),
                    time = ""
                )
            )
        }

        return result
    }

    private fun parseDouble(value: Any?): Double {
        return when (value) {
            null -> 0.0
            is Number -> value.toDouble()
            is String -> value.replace(",", "").trim().toDoubleOrNull() ?: 0.0
            else -> 0.0
        }
    }
}
