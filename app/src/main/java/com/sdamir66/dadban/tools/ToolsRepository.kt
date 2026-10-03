package com.sdamir66.dadban.tools

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object ToolsRepository {

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
                        result.addAll(parseTgjuJson(tgjuJson))
                    } catch (e: Exception) {
                        // اگه tgju خطا داد، ادامه بده
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
                            result.addAll(parseNobitexJson(nobitexJson, cryptoKeys))
                        }
                    } catch (e: Exception) {
                        // اگه نوبیتکس خطا داد، ادامه بده
                    }
                }

                Result.success(result)
            } catch (e: Exception) {
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

        val result = mutableListOf<TgjuPrice>()

        stats.keys().forEach { symbol ->
            val stat = stats.optJSONObject(symbol) ?: return@forEach

            val itemId = PriceCatalog.CRYPTO_SYMBOLS.entries
                .find { it.value.equals(symbol, ignoreCase = true) }
                ?.key
                ?: return@forEach

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
