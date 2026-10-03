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
            val result = mutableListOf<TgjuPrice>()

            val cryptoKeys = keys.filter { it in PriceCatalog.CRYPTO_KEYS }
            val fiatKeys = keys.filter { it !in PriceCatalog.CRYPTO_KEYS }

            Log.d(TAG, "fetchPrices: total=${keys.size}, fiat=${fiatKeys.size}, crypto=${cryptoKeys.size}")

            // ═══ ۱. tgju — دسته‌ای (۵ تایی) برای جلوگیری از drop ═══
            fiatKeys.chunked(5).forEachIndexed { idx, chunk ->
                try {
                    val url = "$TGJU_URL?keys=${chunk.joinToString(",")}"
                    Log.d(TAG, "tgju[$idx] URL: $url")
                    val json = fetchUrl(url)
                    val parsed = parseTgjuJson(json)
                    Log.d(TAG, "tgju[$idx] parsed=${parsed.size}, keys=${parsed.map { it.key }}")
                    result.addAll(parsed)

                    val returned = parsed.map { it.key }.toSet()
                    val missing = chunk.filter { it !in returned }
                    if (missing.isNotEmpty()) {
                        Log.w(TAG, "tgju[$idx] MISSING: $missing")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "tgju[$idx] failed", e)
                }
            }

            // ═══ ۲. نوبیتکس — رمزارزها ═══
            if (cryptoKeys.isNotEmpty()) {
                try {
                    val symbols = cryptoKeys.mapNotNull { PriceCatalog.CRYPTO_SYMBOLS[it] }
                    Log.d(TAG, "crypto symbols to fetch: $symbols")
                    if (symbols.isNotEmpty()) {
                        val url = "$NOBITEX_URL?srcCurrency=${symbols.joinToString(",")}&dstCurrency=rls"
                        Log.d(TAG, "nobitex URL: $url")
                        val json = fetchUrl(url)
                        val parsed = parseNobitexJson(json, cryptoKeys)
                        Log.d(TAG, "nobitex parsed=${parsed.size}, keys=${parsed.map { it.key }}")
                        result.addAll(parsed)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "nobitex failed", e)
                }
            }

            Log.d(TAG, "fetchPrices TOTAL=${result.size}")
            Result.success(result)
        }

    private fun fetchUrl(urlStr: String): String {
        val url = URL(urlStr)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = TIMEOUT_MS
        conn.readTimeout = TIMEOUT_MS
        conn.setRequestProperty("Accept", "application/json")
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 Dadban/1.0")

        return try {
            val code = conn.responseCode
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

    /**
     * نوبیتکس برمی‌گردونه: "btc-rls": { "latest": "...", "dayChange": "...", ... }
     * پس کلید "btc-rls" باید به itemId "398096" map بشه.
     */
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

        // ─── map: "btc" و "btc-rls" و "btc-irt" → itemId ───
        val symbolToKey = mutableMapOf<String, String>()
        PriceCatalog.CRYPTO_SYMBOLS.forEach { (itemId, sym) ->
            val s = sym.lowercase()
            symbolToKey[s] = itemId
            symbolToKey["$s-rls"] = itemId
            symbolToKey["$s-irt"] = itemId
            symbolToKey["$s-usdt"] = itemId
        }

        val result = mutableListOf<TgjuPrice>()

        statKeys.forEach { rawSymbol ->
            val stat = stats.optJSONObject(rawSymbol) ?: return@forEach
            val lower = rawSymbol.lowercase()

            // ─── ۱. مچ دقیق ───
            var itemId: String? = symbolToKey[lower]

            // ─── ۲. fallback: مچ با پیشوند ───
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

            if (itemId !in requestedKeys) return@forEach

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
