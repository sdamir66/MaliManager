package com.example.maliplus.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "accounts")
data class Account(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val note: String = ""
)

@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    val dateMillis: Long,
    val type: String,          // "بدهکار" یا "بستانکار"
    val amount: Long,
    val note: String = "",
    val isAutoProfit: Boolean = false,
    val profitKey: String? = null
)

@Entity(tableName = "goods")
data class Good(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: String = "",
    val unit: String
)

@Entity(tableName = "goods_transactions")
data class GoodTransaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val goodId: Long,
    val dateMillis: Long,
    val type: String,          // "دریافت" یا "پرداخت"
    val quantity: Double,
    val note: String = ""
)

@Entity(tableName = "profit_settings")
data class ProfitSettings(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    val enabled: Boolean = false,
    val mode: String = "DAILY_ANNUAL",  // "DAILY_ANNUAL" یا "MONTHLY"
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
