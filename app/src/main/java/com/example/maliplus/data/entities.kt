package com.example.maliplus.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "persons")
data class Person(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val note: String = "",
    val displayOrder: Int = 0,
    val displayedCurrencies: String = ""
)

@Entity(tableName = "accounts")
data class Account(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val personId: Long,
    val name: String,
    val note: String = "",
    val currency: String = "تومان",
    val customUnit: String = "",
    val displayOrder: Int = 0
)

@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    val dateMillis: Long,
    val type: String,
    val amount: Long,
    val note: String = "",
    val isAutoProfit: Boolean = false,
    val profitKey: String? = null
)

/**
 * بازه‌ی سود
 * - accountId = 0 → سود کلی برنامه
 * - accountId > 0 → سود اختصاصی برای اون حساب
 * - endYear = null → پایانی نداره (تا بی‌نهایت)
 */
@Entity(tableName = "profit_periods")
data class ProfitPeriod(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long = 0,
    val type: String = "ANNUAL",        // "ANNUAL" یا "MONTHLY"
    val rate: Double = 0.0,             // نرخ سود
    val startYear: Int,
    val startMonth: Int,
    val startDay: Int,
    val endYear: Int? = null,
    val endMonth: Int? = null,
    val endDay: Int? = null,
    val payoutDay: Int = 30,            // روز واریز ماه
    val destinationAccountId: Long? = null  // حساب مقصد (برای سود اختصاصی)
)
