package com.sdamir66.dadban.tools

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

object PriceCache {

    private const val TAG = "PriceCache"
    private const val PREFS_NAME = "price_cache_prefs"
    private const val KEY_CACHE = "price_cache_json"
    private const val KEY_SAVED_AT = "price_cache_saved_at"

    fun save(context: Context, prices: List<TgjuPrice>) {
        if (prices.isEmpty()) return
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
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_CACHE, arr.toString())
                .putLong(KEY_SAVED_AT, System.currentTimeMillis())
                .apply()
            Log.d(TAG, "saved ${prices.size} prices")
        } catch (e: Exception) {
            Log.e(TAG, "save failed", e)
        }
    }

    fun load(context: Context): List<TgjuPrice> {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val json = prefs.getString(KEY_CACHE, null) ?: return emptyList()
            val arr = JSONArray(json)
            val result = mutableListOf<TgjuPrice>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val key = o.optString("key", "")
                if (key.isBlank()) continue
                result.add(
                    TgjuPrice(
                        key = key,
                        title = o.optString("title", key),
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
            Log.d(TAG, "loaded ${result.size} prices")
            result
        } catch (e: Exception) {
            Log.e(TAG, "load failed", e)
            emptyList()
        }
    }

    fun getSavedAt(context: Context): Long {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_SAVED_AT, 0L)
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().clear().apply()
    }
}
