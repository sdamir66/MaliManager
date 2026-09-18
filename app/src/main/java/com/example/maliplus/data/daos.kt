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
interface ProfitPeriodDao {
    @Query("SELECT * FROM profit_periods WHERE accountId = :accountId ORDER BY startYear ASC, startMonth ASC, startDay ASC")
    fun byAccount(accountId: Long): Flow<List<ProfitPeriod>>

    @Query("SELECT * FROM profit_periods WHERE accountId = :accountId ORDER BY startYear ASC, startMonth ASC, startDay ASC")
    suspend fun byAccountNow(accountId: Long): List<ProfitPeriod>

    @Insert
    suspend fun insert(p: ProfitPeriod): Long

    @Update
    suspend fun update(p: ProfitPeriod)

    @Delete
    suspend fun delete(p: ProfitPeriod)

    @Query("DELETE FROM profit_periods WHERE accountId = :accountId")
    suspend fun deleteByAccount(accountId: Long)

    @Query("DELETE FROM profit_periods")
    suspend fun clear()
}
