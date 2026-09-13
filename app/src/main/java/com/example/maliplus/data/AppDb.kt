package com.example.maliplus.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        Account::class,
        Transaction::class,
        Good::class,
        GoodTransaction::class,
        ProfitSettings::class,
        MonthlyRate::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDb : RoomDatabase() {
    abstract fun accounts(): AccountDao
    abstract fun tx(): TxDao
    abstract fun goods(): GoodDao
    abstract fun goodTx(): GoodTxDao
    abstract fun profit(): ProfitDao
}
