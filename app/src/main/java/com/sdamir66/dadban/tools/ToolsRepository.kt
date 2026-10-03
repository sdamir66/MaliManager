package com.sdamir66.dadban.tools

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object ToolsRepository {

    private const val TAG = "ToolsRepository"
    private const val TGJU_URL = "https://api.tgju.org/v1/widget/tmp"
    private const val NOBITEX_URL = "https://apiv2.nobitex.ir/market/stats"
    private const val TIMEOUT_MS = 20_000

    suspend fun fetchPrices(keys: List<String>): Result<List<TgjuPrice>> =
        withContext(Dispatchers.IO) {
            val result = mutableListOf<TgjuPrice>()

            val cryptoKeys = keys.filter { it in PriceCatalog.CRYPTO_KEYS }
            val fiatKeys = keys.filter { it !in PriceCatalog.CRYPTO_KEYS }

            Log.d(TAG, "fetchPrices: total=${keys.size}, fiat=${fiatKeys.size}, crypto=${cryptoKeys.size}")
            Log.d(TAG, "cryptoKeys=$cryptoKeys")
            val symbols2 = cryptoKeys.mapNotNull { PriceCatalog.CRYPTO_SYMBOLS[it] }
            Log.d(TAG, "mapped symbols=$symbols2")

            // ═══ ۱. tgju ═══
            if (fiatKeys.isNotEmpty()) {
                try {
                    val url = "$TGJU_URL?keys=${fiatKeys.joinToString(",")}"
                    Log.d(TAG, "tgju URL: $url")
                    val json = fetchUrlWithRetry(url, isNobitex = false)
                    val parsed = parseTgjuJson(json)
                    Log.d(TAG, "tgju parsed=${parsed.size}, keys=${parsed.map { it.key }}")
                    result.addAll(parsed)
                } catch (e: Exception) {
                    Log.e(TAG, "tgju failed", e)
                }
            }

            // ═══ ۲. نوبیتکس ═══
            if (cryptoKeys.isNotEmpty()) {
                val symbols = cryptoKeys.mapNotNull { PriceCatalog.CRYPTO_SYMBOLS[it] }
                if (symbols.isNotEmpty()) {
                    val url = "$NOBITEX_URL?srcCurrency=${symbols.joinToString(",")}&dstCurrency=rls"
                    Log.d(TAG, "nobitex URL: $url")
                    try {
                        val json = fetchUrlWithRetry(url, isNobitex = true)
                        Log.d(TAG, "nobitex raw response (first 300): ${json.take(300)}")
                        val parsed = parseNobitexJson(json, cryptoKeys)
                        Log.d(TAG, "nobitex parsed=${parsed.size}, keys=${parsed.map { it.key }}")
                        result.addAll(parsed)
                    } catch (e: Exception) {
                        Log.e(TAG, "nobitex failed", e)
                    }
                }
            }

            Log.d(TAG, "fetchPrices TOTAL=${result.size}")
            Result.success(result)
        }

    private suspend fun fetchUrlWithRetry(
        urlStr: String,
        isNobitex: Boolean = false,
        maxRetries: Int = 3
    ): String {
        var lastException: Exception? = null
        repeat(maxRetries) { attempt ->
            try {
                return fetchUrl(urlStr, isNobitex)
            } catch (e: Exception) {
                lastException = e
                Log.w(TAG, "fetch attempt ${attempt + 1} failed: ${e.message}")
                if (attempt < maxRetries - 1) delay(1500L * (attempt + 1))
            }
        }
        throw lastException ?: java.io.IOException("fetch failed: $urlStr")
    }

    private fun fetchUrl(urlStr: String, isNobitex: Boolean = false): String {
        val url = URL(urlStr)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = TIMEOUT_MS
        conn.readTimeout = TIMEOUT_MS
        conn.setRequestProperty("Accept", "application/json")
        conn.setRequestProperty(
            "User-Agent",
            if (isNobitex) "Mozilla/5.0 (Linux; Android 13; SM-G991B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
            else "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Dadban/1.0"
        )

        return try {
            val code = conn.responseCode
            Log.d(TAG, "fetchUrl -> HTTP $code")
            if (code !in 200..299) throw java.io.IOException("HTTP $code")
            conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun parseTgjuJson(jsonText: String): List<TgjuPrice> {
        val root = JSONObject(jsonText)
        val response = root.optJSONObject("response") ?: return emptyList()
        val indicators = response.optJSONArray("indicators") ?: return emptyList()

        val result = mutableListOf<TgjuPrice>()
        for (i in 0 until indicators.length()) {
            val obj = indicators.optJSONObject(i) ?: continue
            val itemIdRaw = obj.opt("item_id")
            val itemId = when (itemIdRaw) {
                is Number -> itemIdRaw.toLong().toString()
                is String -> itemIdRaw
                else -> ""
            }
            if (itemId.isBlank()) continue

            val title = obj.optString("title", "").ifBlank {
                PriceCatalog.DEFAULT_TITLES[itemId] ?: itemId
            }

            result.add(
                TgjuPrice(
                    key = itemId,
                    title = title,
                    price = parseDouble(obj.opt("p")),
                    change = parseDouble(obj.opt("d")),
                    changePercent = parseDouble(obj.opt("dp")),
                    direction = obj.optString("dt", ""),
                    low = parseDouble(obj.opt("l")),
                    high = parseDouble(obj.opt("h")),
                    time = obj.optString("updated_at", "").ifBlank { obj.optString("t", "") }
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
        val stats = root.optJSONObject("stats")
        if (stats == null) {
            Log.e(TAG, "nobitex: no 'stats'. Response: ${jsonText.take(300)}")
            return emptyList()
        }

        val statKeys = stats.keys().asSequence().toList()
        Log.d(TAG, "nobitex stats keys (raw): $statKeys")

        val symbolToKey = mutableMapOf<String, String>()
        PriceCatalog.CRYPTO_SYMBOLS.forEach { (itemId, sym) ->
            val s = sym.lowercase()
            symbolToKey[s] = itemId
            symbolToKey["$s-rls"] = itemId
            symbolToKey["$s-irt"] = itemId
            symbolToKey["$s-usdt"] = itemId
        }
        Log.d(TAG, "symbolToKey=$symbolToKey")

        val result = mutableListOf<TgjuPrice>()

        statKeys.forEach { rawSymbol ->
            val stat = stats.optJSONObject(rawSymbol) ?: return@forEach
            val lower = rawSymbol.lowercase()

            var itemId: String? = symbolToKey[lower]
            if (itemId == null) {
                itemId = PriceCatalog.CRYPTO_SYMBOLS.entries
                    .firstOrNull {
                        lower.startsWith("${it.value.lowercase()}-") ||
                        lower == it.value.lowercase()
                    }
                    ?.key
            }

            if (itemId == null) {
                Log.w(TAG, "nobitex UNKNOWN: $rawSymbol")
                return@forEach
            }
            if (itemId !in requestedKeys) {
                Log.d(TAG, "nobitex skip $rawSymbol (itemId=$itemId not in requested)")
                return@forEach
            }

            val title = PriceCatalog.CRYPTO_TITLES[itemId] ?: rawSymbol
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

    private fun parseDouble(value: Any?): Double = when (value) {
        null -> 0.0
        is Number -> value.toDouble()
        is String -> value.replace(",", "").trim().toDoubleOrNull() ?: 0.0
        else -> 0.0
    }
}
