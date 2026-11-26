package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import java.util.concurrent.ConcurrentHashMap

@Database(
    entities = [
        KvEntity::class,
        ItemEntity::class,
        InvoiceEntity::class,
        PurchaseEntity::class,
        PartyEntity::class,
        PaymentEntity::class,
        ExpenseEntity::class,
        RepairEntity::class,
        BankAccountEntity::class,
        CashBankTxnEntity::class,
        ShopDayEntity::class,
        AutoBackupEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun kvDao(): KvDao
    abstract fun itemDao(): ItemDao
    abstract fun invoiceDao(): InvoiceDao
    abstract fun purchaseDao(): PurchaseDao
    abstract fun partyDao(): PartyDao
    abstract fun paymentDao(): PaymentDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun repairDao(): RepairDao
    abstract fun bankAccountDao(): BankAccountDao
    abstract fun cashBankTxnDao(): CashBankTxnDao
    abstract fun shopDayDao(): ShopDayDao
    abstract fun autoBackupDao(): AutoBackupDao
}

object DatabaseProvider {
    private val instances = ConcurrentHashMap<String, AppDatabase>()

    fun get(context: Context, dbName: String): AppDatabase {
        return instances.getOrPut(dbName) {
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, dbName)
                .fallbackToDestructiveMigration(dropAllTables = false)
                .build()
        }
    }

    fun closeAndDelete(context: Context, dbName: String) {
        instances.remove(dbName)?.close()
        context.applicationContext.deleteDatabase(dbName)
    }
}
