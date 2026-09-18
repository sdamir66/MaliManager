package com.example.maliplus.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "persons")
data class Person(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val note: String = "",
    val displayOrder: Int = 0,
    // ارزهایی که توی کارت شخص نمایش داده می‌شن (حداکثر ۳ تا)
    // به صورت comma-separated، مثلاً "ریال,دلار,یورو"
    val displayedCurrencies: String = ""
)

@Entity(tableName = "accounts")
data class Account(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val personId: Long,
    val name: String,
    val note: String = "",
    val currency: String = "تومان",
    val customUnit: String = "",     // اگه پر باشه، جایگزین ارز می‌شه
    val displayOrder: Int = 0,
    val useGlobalProfit: Boolean = true  // از تنظیم سود کلی استفاده کنه یا اختصاصی
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

@Entity(tableName = "profit_settings")
data class ProfitSettings(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    val enabled: Boolean = false,
    val mode: String = "DAILY_ANNUAL",
    val annualRate: Double = 0.0,
    val payoutDay: Int = 30,
    val destinationAccountId: Long? = null
)

@Entity(tableName = "monthly_rates", primaryKeys = ["accountId", "year", "month"])
data class MonthlyRate(
    val accountId: Long,
    val year: Int,
    val month: Int,
    val ratePercent: Double
)

// تنظیم سود کلی برنامه (برای مرحله ۳)
@Entity(tableName = "global_profit_settings")
data class GlobalProfitSettings(
    @PrimaryKey val id: Long = 1,
    val enabled: Boolean = false,
    val mode: String = "DAILY_ANNUAL",
    val annualRate: Double = 20.0,
    val payoutDay: Int = 30
)
