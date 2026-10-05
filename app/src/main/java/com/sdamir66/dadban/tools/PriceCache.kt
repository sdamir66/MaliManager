package com.sdamir66.dadban.tools

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

object PriceCache {

    private const val TAG = "PriceCache"
    private const val PREFS_NAME = "price_cache_prefs_v3"
    private const val KEY_FIAT = "cache_fiat"
    private const val KEY_CRYPTO = "cache_crypto"
    private const val KEY_SAVED_AT = "cache_saved_at"

    // ═══════════════════════════════════════════════════════
    // ذخیره‌ی جداگانه
    // ═══════════════════════════════════════════════════════

    fun saveFiat(context: Context, prices: List<TgjuPrice>) {
        saveToKey(context, KEY_FIAT, prices)
    }

    fun saveCrypto(context: Context, prices: List<TgjuPrice>) {
        saveToKey(context, KEY_CRYPTO, prices)
    }

    private fun saveToKey(context: Context, key: String, prices: List<TgjuPrice>) {
        if (prices.isEmpty()) {
            Log.d(TAG, "save $key skipped: empty")
            return
        }
        try {
            val arr = JSONArray()
            for (p in prices) {
                val o = JSONObject()
                o.put("key", p.key)
                o.put("title", p.title)
                o.put("price", p.price)
                o.put("change", p.change)
                o.put("changePercent", p.changePercent)
                o.put("direction", p.direction)
                o.put("low", p.low)
                o.put("high", p.high)
                o.put("time", p.time)
                arr.put(o)
            }
            val jsonStr = arr.toString()
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putString(key, jsonStr)
                .putLong(KEY_SAVED_AT, System.currentTimeMillis())
                .commit()
            Log.d(TAG, "save $key: ${prices.size} items")
        } catch (e: Exception) {
            Log.e(TAG, "save $key failed", e)
        }
    }

    // ═══════════════════════════════════════════════════════
    // لود ترکیبی
    // ═══════════════════════════════════════════════════════

    fun load(context: Context): List<TgjuPrice> {
        val fiat = loadFromKey(context, KEY_FIAT)
        val crypto = loadFromKey(context, KEY_CRYPTO)
        val result = fiat + crypto
        Log.d(TAG, "load total: ${result.size} (fiat=${fiat.size}, crypto=${crypto.size})")
        return result
    }

    private fun loadFromKey(context: Context, key: String): List<TgjuPrice> {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val json = prefs.getString(key, null) ?: return emptyList()
            val arr = JSONArray(json)
            val result = mutableListOf<TgjuPrice>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val k = o.optString("key", "")
                if (k.isBlank()) continue
                result.add(
                    TgjuPrice(
                        key = k,
                        title = o.optString("title", k),
                        price = o.optDouble("price", 0.0),
                        change = o.optDouble("change", 0.0),
                        changePercent = o.optDouble("changePercent", 0.0),
                        direction = o.optString("direction", ""),
                        low = o.optDouble("low", 0.0),
                        high = o.optDouble("high", 0.0),
                        time = o.optString("time", "")
                    )
                )
            }
            result
        } catch (e: Exception) {
            Log.e(TAG, "load $key failed", e)
            emptyList()
        }
    }

    fun getSavedAt(context: Context): Long {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_SAVED_AT, 0L)
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
    }
}
