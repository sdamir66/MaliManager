package com.example.maliplus.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "accounts")
data class Account(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String, val note: String = "")

@Entity(tableName = "transactions", foreignKeys = [ForeignKey(entity = Account::class, parentColumns = ["id"], childColumns = ["accountId"], onDelete = ForeignKey.CASCADE)], indices = [Index("accountId")])
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

@Entity(tableName = "goods")
data class Good(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String, val type: String, val unit: String)

@Entity(tableName = "goods_transactions", foreignKeys = [ForeignKey(entity = Good::class, parentColumns = ["id"], childColumns = ["goodId"], onDelete = ForeignKey.CASCADE)], indices = [Index("goodId")])
data class GoodTransaction(@PrimaryKey(autoGenerate = true) val id: Long = 0, val goodId: Long, val dateMillis: Long, val type: String, val quantity: Double, val note: String = "")

@Entity(tableName = "profit_settings", foreignKeys = [ForeignKey(entity = Account::class, parentColumns = ["id"], childColumns = ["accountId"], onDelete = ForeignKey.CASCADE)], indices = [Index(value = ["accountId"], unique = true)])
data class ProfitSettings(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    val enabled: Boolean = false,
    val mode: String = "DAILY_ANNUAL",
    val annualRate: Double = 0.0,
    val payoutDay: Int = 30,
    val destinationAccountId: Long? = null
)

@Entity(tableName = "monthly_rates", primaryKeys = ["accountId", "year", "month"], foreignKeys = [ForeignKey(entity = Account::class, parentColumns = ["id"], childColumns = ["accountId"], onDelete = ForeignKey.CASCADE)], indices = [Index("accountId")])
data class MonthlyRate(val accountId: Long, val year: Int, val month: Int, val ratePercent: Double)

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY name") fun all(): Flow<List<Account>>
    @Query("SELECT * FROM accounts ORDER BY name") suspend fun allNow(): List<Account>
    @Query("SELECT * FROM accounts WHERE id=:id LIMIT 1") suspend fun byId(id: Long): Account?
    @Insert suspend fun insert(a: Account): Long
    @Update suspend fun update(a: Account)
    @Delete suspend fun delete(a: Account)
}

@Dao
interface TxDao {
    @Query("SELECT * FROM transactions WHERE accountId=:id ORDER BY dateMillis DESC, id DESC") fun byAccount(id: Long): Flow<List<Transaction>>
    @Query("SELECT * FROM transactions WHERE accountId=:id ORDER BY dateMillis, id") suspend fun byAccountNow(id: Long): List<Transaction>
    @Query("SELECT * FROM transactions WHERE isAutoProfit=1 AND accountId=:id") suspend fun autoForAccount(id: Long): List<Transaction>
    @Query("SELECT * FROM transactions WHERE isAutoProfit=1 AND profitKey LIKE :prefix || '%' ") suspend fun autoByPrefix(prefix: String): List<Transaction>
    @Insert suspend fun insert(t: Transaction): Long
    @Insert suspend fun insertAll(t: List<Transaction>)
    @Update suspend fun update(t: Transaction)
    @Delete suspend fun delete(t: Transaction)
    @Query("DELETE FROM transactions WHERE isAutoProfit=1 AND accountId=:accountId") suspend fun deleteAutoForAccount(accountId: Long)
    @Query("DELETE FROM transactions WHERE isAutoProfit=1") suspend fun deleteAllAuto()
    @Query("DELETE FROM transactions WHERE isAutoProfit=1 AND profitKey LIKE :prefix || '%'") suspend fun deleteAutoByPrefix(prefix: String)
    @Query("DELETE FROM transactions") suspend fun clear()
}

@Dao
interface GoodDao {
    @Query("SELECT * FROM goods ORDER BY name") fun all(): Flow<List<Good>>
    @Query("SELECT * FROM goods ORDER BY name") suspend fun allNow(): List<Good>
    @Insert suspend fun insert(g: Good): Long
    @Update suspend fun update(g: Good)
    @Delete suspend fun delete(g: Good)
    @Query("DELETE FROM goods") suspend fun clear()
}

@Dao
interface GoodTxDao {
    @Query("SELECT * FROM goods_transactions WHERE goodId=:id ORDER BY dateMillis DESC, id DESC") fun byGood(id: Long): Flow<List<GoodTransaction>>
    @Insert suspend fun insert(t: GoodTransaction): Long
    @Update suspend fun update(t: GoodTransaction)
    @Delete suspend fun delete(t: GoodTransaction)
    @Query("DELETE FROM goods_transactions") suspend fun clear()
}

@Dao
interface ProfitDao {
    @Query("SELECT * FROM profit_settings WHERE accountId=:id LIMIT 1") fun byAccount(id: Long): Flow<ProfitSettings?>
    @Query("SELECT * FROM profit_settings WHERE accountId=:id LIMIT 1") suspend fun byAccountNow(id: Long): ProfitSettings?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(s: ProfitSettings)
    @Delete suspend fun delete(s: ProfitSettings)
    @Query("SELECT * FROM monthly_rates WHERE accountId=:id ORDER BY year, month") fun rates(id: Long): Flow<List<MonthlyRate>>
    @Query("SELECT * FROM monthly_rates WHERE accountId=:id") suspend fun ratesNow(id: Long): List<MonthlyRate>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertRate(r: MonthlyRate)
    @Query("DELETE FROM monthly_rates WHERE accountId=:id") suspend fun deleteRates(id: Long)
    @Query("DELETE FROM profit_settings") suspend fun clearSettings()
    @Query("DELETE FROM monthly_rates") suspend fun clearRates()
}

@Database(entities = [Account::class, Transaction::class, Good::class, GoodTransaction::class, ProfitSettings::class, MonthlyRate::class], version = 3, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun accounts(): AccountDao
    abstract fun tx(): TxDao
    abstract fun goods(): GoodDao
    abstract fun goodTx(): GoodTxDao
    abstract fun profit(): ProfitDao
}
