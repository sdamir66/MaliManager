package com.sdamir66.dadban.treasury

import com.sdamir66.dadban.data.Account
import com.sdamir66.dadban.tools.TgjuPrice

object TreasuryCalculator {

    fun calculateEquivalent(
        account: Account,
        balance: Double,
        livePrices: List<TgjuPrice>
    ): Double? {
        // ═══ حالت ۱: معادل ریالی فعال نیست ═══
        if (!account.rateEnabled) {
            // اگه واحد حساب ریال یا تومان باشه، معادل = خود موجودی
            val unit = if (account.customUnit.isNotBlank()) account.customUnit else account.currency
            return when (unit) {
                "ریال" -> balance
                "تومان" -> balance * 10.0
                else -> null
            }
        }

        // ═══ حالت ۲: معادل فعال ═══
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

    fun liveKeyTitle(account: Account, livePrices: List<TgjuPrice>): String? {
        if (!account.rateEnabled || account.rateMode != "live") return null
        if (account.liveKey.isBlank()) return null
        return livePrices.find { it.key == account.liveKey }?.title ?: account.liveKey
    }
}
