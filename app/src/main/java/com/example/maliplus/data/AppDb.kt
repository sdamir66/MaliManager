package com.example.maliplus.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        Person::class,
        Account::class,
        Transaction::class,
        ProfitPeriod::class
    ],
    version = 5,
    exportSchema = false
)
abstract class AppDb : RoomDatabase() {
    abstract fun persons(): PersonDao
    abstract fun accounts(): AccountDao
    abstract fun tx(): TxDao
    abstract fun profitPeriod(): ProfitPeriodDao
}
