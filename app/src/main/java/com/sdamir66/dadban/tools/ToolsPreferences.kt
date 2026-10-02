package com.sdamir66.dadban.tools

import android.content.Context

// ═══════════════════════════════════════════════════════════════
//  ToolsPreferences
//  ذخیره‌ی key های انتخاب‌شده (تاپ‌لیست) در SharedPreferences
// ═══════════════════════════════════════════════════════════════

object ToolsPreferences {

    private const val PREFS_NAME = "tools_prefs"
    private const val KEY_SELECTED = "selected_keys"

    // ═══ دریافت لیست انتخاب‌شده‌ها ═══
    fun getSelectedKeys(context: Context): List<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_SELECTED, null) ?: return PriceCategory.DEFAULT_SELECTED
        return saved.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }

    // ═══ ذخیره‌ی لیست انتخاب‌شده‌ها ═══
    fun saveSelectedKeys(context: Context, keys: List<String>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_SELECTED, keys.joinToString(",")).apply()
    }
}
