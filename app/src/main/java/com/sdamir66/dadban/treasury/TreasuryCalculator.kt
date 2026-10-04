package com.sdamir66.dadban.treasury

import com.sdamir66.dadban.data.Account
import com.sdamir66.dadban.tools.TgjuPrice

/**
 * محاسبه‌ی «معادل ریالی» هر حساب.
 *
 * فرمول:
 *   معادل ریالی = مانده × نرخ
 *
 * که نرخ از یکی از این دو منبع میاد:
 *   - rateMode = "manual" → manualRate (ریال به ازای هر واحد)
 *   - rateMode = "live"   → قیمت آیتم liveKey از PriceCatalog (ریال به ازای هر واحد)
 *
 * اگه rateEnabled = false یا آیتم liveKey پیدا نشه → null برمی‌گردونه.
 */
object TreasuryCalculator {

    /**
     * معادل ریالی رو حساب می‌کنه.
     *
     * @param account حساب موردنظر
     * @param balance مانده‌ی حساب (مثبت یا منفی)
     * @param livePrices لیست قیمت‌های لحظه‌ای (از ToolsRepository)
     * @return معادل ریالی یا null اگه قابل محاسبه نبود
     */
    fun calculateEquivalent(
        account: Account,
        balance: Double,
        livePrices: List<TgjuPrice>
    ): Double? {
        if (!account.rateEnabled) return null

        val rate: Double = when (account.rateMode) {
            "manual" -> {
                if (account.manualRate <= 0.0) return null
                account.manualRate
            }
            "live" -> {
                if (account.liveKey.isBlank()) return null
                val price = livePrices.find { it.key == account.liveKey } ?: return null
                if (price.price <= 0.0) return null
                price.price
            }
            else -> return null
        }

        return balance * rate
    }

    /**
     * عنوان آیتم live برای نمایش (مثلاً «دلار» یا «سکه امامی»)
     */
    fun liveKeyTitle(account: Account, livePrices: List<TgjuPrice>): String? {
        if (!account.rateEnabled || account.rateMode != "live") return null
        if (account.liveKey.isBlank()) return null
        return livePrices.find { it.key == account.liveKey }?.title ?: account.liveKey
    }
}
