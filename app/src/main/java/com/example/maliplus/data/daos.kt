package com.example.maliplus.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonDao {
    @Query("SELECT * FROM persons ORDER BY displayOrder ASC, id ASC")
    fun all(): Flow<List<Person>>

    @Query("SELECT * FROM persons ORDER BY displayOrder ASC, id ASC")
    suspend fun allNow(): List<Person>

    @Query("SELECT * FROM persons WHERE id = :id LIMIT 1")
    suspend fun byId(id: Long): Person?

    @Query("SELECT * FROM persons WHERE name = :name LIMIT 1")
    suspend fun byName(name: String): Person?

    @Insert
    suspend fun insert(p: Person): Long

    @Update
    suspend fun update(p: Person)

    @Delete
    suspend fun delete(p: Person)

    @Query("UPDATE persons SET displayOrder = :order WHERE id = :id")
    suspend fun updateOrder(id: Long, order: Int)

    @Query("DELETE FROM persons")
    suspend fun clear()
}

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY displayOrder ASC, id ASC")
    fun all(): Flow<List<Account>>

    @Query("SELECT * FROM accounts ORDER BY displayOrder ASC, id ASC")
    suspend fun allNow(): List<Account>

    @Query("SELECT * FROM accounts WHERE personId = :personId ORDER BY displayOrder ASC, id ASC")
    fun byPerson(personId: Long): Flow<List<Account>>

    @Query("SELECT * FROM accounts WHERE personId = :personId ORDER BY displayOrder ASC, id ASC")
    suspend fun byPersonNow(personId: Long): List<Account>

    @Query("SELECT * FROM accounts WHERE id = :id LIMIT 1")
    suspend fun byId(id: Long): Account?

    @Insert
    suspend fun insert(a: Account): Long

    @Update
    suspend fun update(a: Account)

    @Delete
    suspend fun delete(a: Account)

    @Query("UPDATE accounts SET displayOrder = :order WHERE id = :id")
    suspend fun updateOrder(id: Long, order: Int)

    @Query("DELETE FROM accounts")
    suspend fun clear()
}

@Dao
interface TxDao {
    @Query("SELECT * FROM transactions WHERE accountId = :id ORDER BY dateMillis DESC, id DESC")
    fun byAccount(id: Long): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE accountId = :id ORDER BY dateMillis DESC, id DESC")
    suspend fun byAccountNow(id: Long): List<Transaction>

    @Query("SELECT * FROM transactions WHERE accountId IN (:accountIds) ORDER BY dateMillis DESC, id DESC")
    suspend fun byAccountsNow(accountIds: List<Long>): List<Transaction>

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

// برای مرحله ۳ — فعلاً استفاده نمی‌شه ولی آماده‌ست
@Dao
interface GlobalProfitDao {
    @Query("SELECT * FROM global_profit_settings WHERE id = 1 LIMIT 1")
    fun get(): Flow<GlobalProfitSettings?>

    @Query("SELECT * FROM global_profit_settings WHERE id = 1 LIMIT 1")
    suspend fun getNow(): GlobalProfitSettings?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(s: GlobalProfitSettings)
}
