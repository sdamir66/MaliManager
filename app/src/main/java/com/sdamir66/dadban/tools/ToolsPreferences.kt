package com.sdamir66.dadban.tools

import android.content.Context

object ToolsPreferences {

    private const val PREFS_NAME = "tools_prefs"
    private const val KEY_SELECTED = "selected_keys_v2"  // ← نسخه جدید که کلیدهای قدیمی رو نادیده می‌گیره

    // ═══ دریافت لیست تاپ ═══
    fun getSelectedKeys(context: Context): List<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_SELECTED, null)
        if (saved == null) return PriceCatalog.DEFAULT_SELECTED

        val list = saved.split(",").map { it.trim() }.filter { it.isNotEmpty() }

        // ─── فیلتر کلیدهای نامعتبر (قدیمی که دیگه وجود ندارن) ───
        val validKeys = list.filter { it in PriceCatalog.ALL_ORDERED }

        return validKeys.ifEmpty { PriceCatalog.DEFAULT_SELECTED }
    }

    // ═══ ذخیره‌ی لیست تاپ ═══
    fun saveSelectedKeys(context: Context, keys: List<String>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        // ─── فقط کلیدهای معتبر رو ذخیره کن ───
        val validKeys = keys.filter { it in PriceCatalog.ALL_ORDERED }
        prefs.edit().putString(KEY_SELECTED, validKeys.joinToString(",")).apply()
    }

    // ═══ ریست کامل (اگه لازم شد) ═══
    fun resetToDefaults(context: Context) {
        saveSelectedKeys(context, PriceCatalog.DEFAULT_SELECTED)
    }
}
