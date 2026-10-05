package com.sdamir66.dadban.tools

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

object ToolsRepository {

    private const val TAG = "ToolsRepository"
    private const val TGJU_URL = "https://api.tgju.org/v1/widget/tmp"
    private const val NOBITEX_URL = "https://apiv2.nobitex.ir/market/stats"
    private const val TIMEOUT_MS = 8_000
    private const val TGJU_CHUNK_SIZE = 6
    private const val TGJU_CHUNK_DELAY = 400L

    suspend fun fetchPrices(context: Context, keys: List<String>): Result<List<TgjuPrice>> =
        withContext(Dispatchers.IO) {
            val cryptoKeys = keys.filter { it in PriceCatalog.CRYPTO_KEYS }
            val fiatKeys = keys.filter { it !in PriceCatalog.CRYPTO_KEYS }

            Log.d(TAG, "══════ fetchPrices START ══════")
            Log.d(TAG, "total=${keys.size}, fiat=${fiatKeys.size}, crypto=${cryptoKeys.size}")

            // ═══════════════════════════════════════════════
            // ۱. tgju → cache جداگانه
            // ═══════════════════════════════════════════════
            val fiatResult = mutableListOf<TgjuPrice>()
            if (fiatKeys.isNotEmpty()) {
                val chunks = fiatKeys.chunked(TGJU_CHUNK_SIZE)
                chunks.forEachIndexed { idx, chunk ->
                    try {
                        val url = "$TGJU_URL?keys=${chunk.joinToString(",")}"
                        Log.d(TAG, "tgju[$idx] URL: $url")
                        val json = fetchUrl(url, isNobitex = false)
                        val parsed = parseTgjuJson(json)
                        fiatResult.addAll(parsed)
                        Log.d(TAG, "tgju[$idx] OK: ${parsed.size}")
                    } catch (e: Exception) {
                        Log.e(TAG, "tgju[$idx] FAILED: ${e.message}")
                    }
                    if (idx < chunks.size - 1) delay(TGJU_CHUNK_DELAY)
                }
            }

            // اگه tgju موفق بود → cache فیات
            if (fiatResult.isNotEmpty()) {
                PriceCache.saveFiat(context, fiatResult)
                Log.d(TAG, "fiat cached: ${fiatResult.size}")
            } else {
                Log.w(TAG, "tgju کاملاً fail داد، فیات از cache قبلی استفاده می‌شه")
            }

            // ═══════════════════════════════════════════════
            // ۲. نوبیتکس → cache جداگانه
            // ═══════════════════════════════════════════════
            val cryptoResult = mutableListOf<TgjuPrice>()
            if (cryptoKeys.isNotEmpty()) {
                try {
                    val symbols = cryptoKeys.mapNotNull { PriceCatalog.CRYPTO_SYMBOLS[it] }
                    if (symbols.isNotEmpty()) {
                        val url = "$NOBITEX_URL?srcCurrency=${symbols.joinToString(",")}&dstCurrency=rls"
                        Log.d(TAG, "nobitex URL: $url")
                        val json = fetchUrl(url, isNobitex = true)
                        val parsed = parseNobitexJson(json, cryptoKeys)
                        cryptoResult.addAll(parsed)
                        Log.d(TAG, "nobitex OK: ${parsed.size}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "nobitex FAILED: ${e.message}", e)
                }
            }

            // اگه نوبیتکس موفق بود → cache رمزارز
            if (cryptoResult.isNotEmpty()) {
                PriceCache.saveCrypto(context, cryptoResult)
                Log.d(TAG, "crypto cached: ${cryptoResult.size}")
            } else {
                Log.w(TAG, "نوبیتکس fail داد، رمزارز از cache قبلی استفاده می‌شه")
            }

            // ═══════════════════════════════════════════════
            // ۳. ترکیب: نتیجه‌ی جدید + cache قبلی برای بخش‌های fail
            // ═══════════════════════════════════════════════
            val finalResult = mutableListOf<TgjuPrice>()
            finalResult.addAll(fiatResult)
            finalResult.addAll(cryptoResult)

            // اگه فیلدهای fail کردن، از cache استفاده کن
            if (fiatResult.isEmpty() || cryptoResult.isEmpty()) {
                val cached = PriceCache.load(context)
                if (fiatResult.isEmpty()) {
                    finalResult.addAll(cached.filter { it.key !in PriceCatalog.CRYPTO_KEYS })
                    Log.d(TAG, "added fiat from cache")
                }
                if (cryptoResult.isEmpty()) {
                    finalResult.addAll(cached.filter { it.key in PriceCatalog.CRYPTO_KEYS })
                    Log.d(TAG, "added crypto from cache")
                }
            }

            Log.d(TAG, "══════ fetchPrices END: ${finalResult.size} (fiat=${fiatResult.size}, crypto=${cryptoResult.size}) ══════")

            // ═══ FIX: اگه هیچی نیومد، failure بده ═══
            if (finalResult.isEmpty()) {
                Result.failure(IOException("هیچ قیمتی دریافت نشد (شبکه/API)"))
            } else {
                Result.success(finalResult)
            }
        }

    private fun fetchUrl(urlStr: String, isNobitex: Boolean = false): String {
        val url = URL(urlStr)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = TIMEOUT_MS
        conn.readTimeout = TIMEOUT_MS
        conn.setRequestProperty("Accept", "application/json")
        conn.setRequestProperty("Content-Type", "application/json")     // ← FIX
        conn.setRequestProperty("Cache-Control", "no-cache")
        conn.setRequestProperty("Pragma", "no-cache")
        conn.setRequestProperty(
            "User-Agent",
            if (isNobitex) "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
            else "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Dadban/1.0"
        )

        return try {
            val code = conn.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")
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
        val stats = root.optJSONObject("stats") ?: return emptyList()

        val symbolToKey = mutableMapOf<String, String>()
        PriceCatalog.CRYPTO_SYMBOLS.forEach { (itemId, sym) ->
            val s = sym.lowercase()
            symbolToKey[s] = itemId
            symbolToKey["$s-rls"] = itemId
            symbolToKey["$s-irt"] = itemId
            symbolToKey["$s-usdt"] = itemId
        }

        val result = mutableListOf<TgjuPrice>()
        stats.keys().forEach { rawSymbol ->
            val stat = stats.optJSONObject(rawSymbol) ?: return@forEach
            val lower = rawSymbol.lowercase()

            var itemId: String? = symbolToKey[lower]
            if (itemId == null) {
                itemId = PriceCatalog.CRYPTO_SYMBOLS.entries
                    .firstOrNull {
                        lower.startsWith("${it.value.lowercase()}-") ||
                        lower == it.value.lowercase()
                    }?.key
            }
            if (itemId == null) return@forEach
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
