package com.sdamir66.dadban.treasury

import android.content.Context

/**
 * ذخیره‌سازی تنظیمات مربوط به «دارایی‌های من»:
 *  - رمز ورود
 *  - زمان آخرین به‌روزرسانی نرخ‌ها
 */
object TreasuryPreferences {

    private const val PREFS_NAME = "treasury_prefs"
    private const val KEY_PASSWORD = "password"
    private const val KEY_LAST_UPDATE = "last_update"

    // ═══ رمز ═══

    fun getPassword(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val pwd = prefs.getString(KEY_PASSWORD, null)
        return pwd?.takeIf { it.isNotBlank() }
    }

    fun setPassword(context: Context, password: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_PASSWORD, password).apply()
    }

    fun clearPassword(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_PASSWORD).apply()
    }

    fun hasPassword(context: Context): Boolean = getPassword(context) != null

    // ═══ زمان آخرین به‌روزرسانی نرخ‌ها ═══

    fun getLastUpdate(context: Context): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getLong(KEY_LAST_UPDATE, 0L)
    }

    fun setLastUpdate(context: Context, millis: Long) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong(KEY_LAST_UPDATE, millis).apply()
    }

    // ═══ ابزار: آیا نرخ‌ها قدیمی شدن؟ ═══

    /**
     * آستانه‌ی هشدار: ۳۰ دقیقه
     */
    fun isStale(context: Context): Boolean {
        val last = getLastUpdate(context)
        if (last == 0L) return true
        return (System.currentTimeMillis() - last) > 30 * 60 * 1000L
    }
}
