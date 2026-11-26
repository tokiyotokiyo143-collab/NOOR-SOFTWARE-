package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "kv_store")
data class KvEntity(
    @PrimaryKey val key: String,
    val jsonValue: String,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "items")
data class ItemEntity(
    @PrimaryKey val id: String,
    val category: String,
    val brand: String,
    val model: String,
    val barcode: String,
    val imei: String,
    val pieces: Int,
    val updatedAt: Long,
    val jsonPayload: String
)

@Entity(tableName = "invoices")
data class InvoiceEntity(
    @PrimaryKey val id: String,
    val invoiceNumber: String,
    val docType: String,
    val date: String,
    val customerId: String?,
    val status: String,
    val createdAt: Long,
    val jsonPayload: String
)

@Entity(tableName = "purchases")
data class PurchaseEntity(
    @PrimaryKey val id: String,
    val billNumber: String,
    val docType: String,
    val date: String,
    val supplierId: String?,
    val status: String,
    val createdAt: Long,
    val jsonPayload: String
)

@Entity(tableName = "parties")
data class PartyEntity(
    @PrimaryKey val id: String,
    val name: String,
    val phone: String,
    val type: String,
    val updatedAt: Long,
    val jsonPayload: String
)

@Entity(tableName = "payments")
data class PaymentEntity(
    @PrimaryKey val id: String,
    val partyId: String,
    val direction: String,
    val date: String,
    val createdAt: Long,
    val jsonPayload: String
)

@Entity(tableName = "expenses")
data class ExpenseEntity(
    @PrimaryKey val id: String,
    val category: String,
    val date: String,
    val createdAt: Long,
    val jsonPayload: String
)

@Entity(tableName = "repairs")
data class RepairEntity(
    @PrimaryKey val id: String,
    val jobNumber: String,
    val status: String,
    val date: String,
    val customerId: String?,
    val createdAt: Long,
    val jsonPayload: String
)

@Entity(tableName = "bank_accounts")
data class BankAccountEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long,
    val jsonPayload: String
)

@Entity(tableName = "cash_bank_txns")
data class CashBankTxnEntity(
    @PrimaryKey val id: String,
    val type: String,
    val date: String,
    val accountId: String?,
    val createdAt: Long,
    val jsonPayload: String
)

@Entity(tableName = "shop_days")
data class ShopDayEntity(
    @PrimaryKey val date: String,
    val jsonPayload: String
)

@Entity(tableName = "auto_backups")
data class AutoBackupEntity(
    @PrimaryKey val id: String,
    val createdAt: Long,
    val label: String,
    val itemsCount: Int,
    val invoicesCount: Int,
    val filePath: String
)
