package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface KvDao {
    @Query("SELECT * FROM kv_store WHERE key = :key LIMIT 1")
    fun observe(key: String): Flow<KvEntity?>

    @Query("SELECT * FROM kv_store WHERE key = :key LIMIT 1")
    suspend fun get(key: String): KvEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entity: KvEntity)

    @Query("DELETE FROM kv_store")
    suspend fun clearAll()
}

@Dao
interface ItemDao {
    @Query("SELECT * FROM items ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<ItemEntity>>

    @Query("SELECT * FROM items")
    suspend fun getAll(): List<ItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<ItemEntity>)

    @Query("DELETE FROM items WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM items")
    suspend fun clearAll()
}

@Dao
interface InvoiceDao {
    @Query("SELECT * FROM invoices ORDER BY date DESC, createdAt DESC")
    fun observeAll(): Flow<List<InvoiceEntity>>

    @Query("SELECT * FROM invoices")
    suspend fun getAll(): List<InvoiceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: InvoiceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<InvoiceEntity>)

    @Query("DELETE FROM invoices WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM invoices")
    suspend fun clearAll()
}

@Dao
interface PurchaseDao {
    @Query("SELECT * FROM purchases ORDER BY date DESC, createdAt DESC")
    fun observeAll(): Flow<List<PurchaseEntity>>

    @Query("SELECT * FROM purchases")
    suspend fun getAll(): List<PurchaseEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PurchaseEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<PurchaseEntity>)

    @Query("DELETE FROM purchases WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM purchases")
    suspend fun clearAll()
}

@Dao
interface PartyDao {
    @Query("SELECT * FROM parties ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<PartyEntity>>

    @Query("SELECT * FROM parties")
    suspend fun getAll(): List<PartyEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PartyEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<PartyEntity>)

    @Query("DELETE FROM parties WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM parties")
    suspend fun clearAll()
}

@Dao
interface PaymentDao {
    @Query("SELECT * FROM payments ORDER BY date DESC, createdAt DESC")
    fun observeAll(): Flow<List<PaymentEntity>>

    @Query("SELECT * FROM payments")
    suspend fun getAll(): List<PaymentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PaymentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<PaymentEntity>)

    @Query("DELETE FROM payments WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM payments")
    suspend fun clearAll()
}

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses ORDER BY date DESC, createdAt DESC")
    fun observeAll(): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses")
    suspend fun getAll(): List<ExpenseEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ExpenseEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<ExpenseEntity>)

    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM expenses")
    suspend fun clearAll()
}

@Dao
interface RepairDao {
    @Query("SELECT * FROM repairs ORDER BY date DESC, createdAt DESC")
    fun observeAll(): Flow<List<RepairEntity>>

    @Query("SELECT * FROM repairs")
    suspend fun getAll(): List<RepairEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: RepairEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<RepairEntity>)

    @Query("DELETE FROM repairs WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM repairs")
    suspend fun clearAll()
}

@Dao
interface BankAccountDao {
    @Query("SELECT * FROM bank_accounts ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<BankAccountEntity>>

    @Query("SELECT * FROM bank_accounts")
    suspend fun getAll(): List<BankAccountEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: BankAccountEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<BankAccountEntity>)

    @Query("DELETE FROM bank_accounts WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM bank_accounts")
    suspend fun clearAll()
}

@Dao
interface CashBankTxnDao {
    @Query("SELECT * FROM cash_bank_txns ORDER BY date DESC, createdAt DESC")
    fun observeAll(): Flow<List<CashBankTxnEntity>>

    @Query("SELECT * FROM cash_bank_txns")
    suspend fun getAll(): List<CashBankTxnEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CashBankTxnEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<CashBankTxnEntity>)

    @Query("DELETE FROM cash_bank_txns WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM cash_bank_txns")
    suspend fun clearAll()
}

@Dao
interface ShopDayDao {
    @Query("SELECT * FROM shop_days ORDER BY date DESC")
    fun observeAll(): Flow<List<ShopDayEntity>>

    @Query("SELECT * FROM shop_days")
    suspend fun getAll(): List<ShopDayEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ShopDayEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<ShopDayEntity>)

    @Query("DELETE FROM shop_days")
    suspend fun clearAll()
}

@Dao
interface AutoBackupDao {
    @Query("SELECT * FROM auto_backups ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<AutoBackupEntity>>

    @Query("SELECT * FROM auto_backups ORDER BY createdAt DESC")
    suspend fun getAll(): List<AutoBackupEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: AutoBackupEntity)

    @Query("DELETE FROM auto_backups WHERE id = :id")
    suspend fun deleteById(id: String)
}
