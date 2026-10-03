package com.sdamir66.dadban.tools

import android.content.Context

object ToolsPreferences {

    private const val PREFS_NAME = "tools_prefs"
    private const val KEY_SELECTED = "selected_keys"

    // ═══ دریافت لیست تاپ ═══
    fun getSelectedKeys(context: Context): List<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_SELECTED, null)
        if (saved == null) return PriceCatalog.DEFAULT_SELECTED

        val list = saved.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        // اگه خالی بود، پیش‌فرض برگردون
        return list.ifEmpty { PriceCatalog.DEFAULT_SELECTED }
    }

    // ═══ ذخیره‌ی لیست تاپ ═══
    fun saveSelectedKeys(context: Context, keys: List<String>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_SELECTED, keys.joinToString(",")).apply()
    }
}
