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
                        Log.d(TAG, "tgju request: $tgjuUrl")
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
                            Log.d(TAG, "nobitex request: $nobitexUrl")
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
     * tgju پاسخش ممکنه item_id عددی یا name متنی باشه.
     * برای سکه‌های امروزی: name = "sekee", "sekeb", "nim", "rob", "gerami"
     * برای سکه‌های قدیم: item_id = "137142", "137143", "137144"
     *
     * استراتژی: هر کدوم که توی requestedKeys بود رو به عنوان key بگیر.
     * اولویت با item_id هست چون دقیق‌تره.
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

            val name = obj.optString("name", "").trim()
            val itemIdRaw = obj.opt("item_id")
            val itemId = when (itemIdRaw) {
                is Number -> itemIdRaw.toLong().toString()
                is String -> itemIdRaw.trim()
                else -> ""
            }

            // ─── پیدا کردن key: هر کدوم که توی requestedKeys بود ───
            val key = when {
                itemId.isNotBlank() && itemId in requestedKeys -> itemId
                name.isNotBlank() && name in requestedKeys -> name
                else -> {
                    // ─── fallback: تطبیق نسبی ───
                    requestedKeys.find { req ->
                        name.equals(req, ignoreCase = true) ||
                        itemId.equals(req, ignoreCase = true) ||
                        name.startsWith("${req}_") ||
                        name.startsWith("${req}-")
                    }
                }
            } ?: run {
                Log.w(TAG, "tgju unmatched: name=$name, item_id=$itemId")
                continue
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
