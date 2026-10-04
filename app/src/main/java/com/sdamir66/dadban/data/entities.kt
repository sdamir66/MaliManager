package com.sdamir66.dadban.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "persons")
data class Person(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val note: String = "",
    val displayOrder: Int = 0,
    val displayedCurrencies: String = "",
    val isTreasury: Boolean = false
)

@Entity(tableName = "accounts")
data class Account(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val personId: Long,
    val name: String,
    val note: String = "",
    val currency: String = "تومان",
    val customUnit: String = "",
    val displayOrder: Int = 0,
    val rateEnabled: Boolean = false,
    val rateMode: String = "manual",
    val manualRate: Double = 0.0,
    val liveKey: String = "",
    // ═══ ماهیت حساب ═══
    // "credit" = بستانکار (حساب معمولی) — بستانکار مثبت، بدهکار منفی
    // "debit"  = بدهکار (دارایی‌ها) — بدهکار مثبت، بستانکار منفی
    val nature: String = "credit"
)

@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    val dateMillis: Long,
    val type: String,
    val amount: Double,
    val note: String = "",
    val isAutoProfit: Boolean = false,
    val profitKey: String? = null
)

@Entity(tableName = "profit_periods")
data class ProfitPeriod(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    val type: String,
    val rate: Double,
    val startYear: Int,
    val startMonth: Int,
    val startDay: Int,
    val endYear: Int? = null,
    val endMonth: Int? = null,
    val endDay: Int? = null,
    val payoutDay: Int = 0,
    val destinationAccountId: Long? = null
)
