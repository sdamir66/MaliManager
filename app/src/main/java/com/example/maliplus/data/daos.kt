package com.example.maliplus.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY id")
    fun all(): Flow<List<Account>>

    @Query("SELECT * FROM accounts ORDER BY id")
    suspend fun allNow(): List<Account>

    @Query("SELECT * FROM accounts WHERE id = :id LIMIT 1")
    suspend fun byId(id: Long): Account?

    @Insert
    suspend fun insert(a: Account): Long

    @Update
    suspend fun update(a: Account)

    @Delete
    suspend fun delete(a: Account)

    @Query("DELETE FROM accounts")
    suspend fun clear()
}

@Dao
interface TxDao {
    @Query("SELECT * FROM transactions WHERE accountId = :id ORDER BY dateMillis DESC, id DESC")
    fun byAccount(id: Long): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE accountId = :id ORDER BY dateMillis DESC, id DESC")
    suspend fun byAccountNow(id: Long): List<Transaction>

    @Insert
    suspend fun insert(t: Transaction): Long

    @Insert
    suspend fun insertAll(list: List<Transaction>)

    @Update
    suspend fun update(t: Transaction)

    @Delete
    suspend fun delete(t: Transaction)

    @Query("DELETE FROM transactions")
    suspend fun clear()

    @Query("DELETE FROM transactions WHERE isAutoProfit = 1")
    suspend fun deleteAllAuto()

    @Query("DELETE FROM transactions WHERE isAutoProfit = 1 AND profitKey LIKE :prefix || ':%'")
    suspend fun deleteAutoByPrefix(prefix: String)
}

@Dao
interface GoodDao {
    @Query("SELECT * FROM goods ORDER BY id")
    fun all(): Flow<List<Good>>

    @Query("SELECT * FROM goods ORDER BY id")
    suspend fun allNow(): List<Good>

    @Query("SELECT * FROM goods WHERE id = :id LIMIT 1")
    suspend fun byId(id: Long): Good?

    @Insert
    suspend fun insert(g: Good): Long

    @Update
    suspend fun update(g: Good)

    @Delete
    suspend fun delete(g: Good)

    @Query("DELETE FROM goods")
    suspend fun clear()
}

@Dao
interface GoodTxDao {
    @Query("SELECT * FROM goods_transactions WHERE goodId = :id ORDER BY dateMillis DESC, id DESC")
    fun byGood(id: Long): Flow<List<GoodTransaction>>

    @Query("SELECT * FROM goods_transactions WHERE goodId = :id ORDER BY dateMillis DESC, id DESC")
    suspend fun byGoodNow(id: Long): List<GoodTransaction>

    @Insert
    suspend fun insert(t: GoodTransaction): Long

    @Update
    suspend fun update(t: GoodTransaction)

    @Delete
    suspend fun delete(t: GoodTransaction)

    @Query("DELETE FROM goods_transactions")
    suspend fun clear()
}

@Dao
interface ProfitDao {
    @Query("SELECT * FROM profit_settings WHERE accountId = :id LIMIT 1")
    fun byAccount(id: Long): Flow<ProfitSettings?>

    @Query("SELECT * FROM profit_settings WHERE accountId = :id LIMIT 1")
    suspend fun byAccountNow(id: Long): ProfitSettings?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(s: ProfitSettings): Long

    @Query("DELETE FROM profit_settings")
    suspend fun clearSettings()

    @Query("SELECT * FROM monthly_rates WHERE accountId = :id")
    fun rates(id: Long): Flow<List<MonthlyRate>>

    @Query("SELECT * FROM monthly_rates WHERE accountId = :id")
    suspend fun ratesNow(id: Long): List<MonthlyRate>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRate(r: MonthlyRate)

    @Query("DELETE FROM monthly_rates")
    suspend fun clearRates()
}
