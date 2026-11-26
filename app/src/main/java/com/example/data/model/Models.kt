package com.example.data.model

import java.util.UUID

const val PRIMARY_OWNER_EMAIL = "tokiyotokiyo143@gmail.com"
const val FIRESTORE_DATABASE_ID = "ai-studio-annoormobileserv-79e2d0fb-f5a5-466e-ba26-e8e1b8bb698a"

enum class NavSection(
    val key: String,
    val labelEn: String,
    val labelTa: String,
    val isMain: Boolean = false
) {
    HOME("home", "Home", "முகப்பு", isMain = true),
    BILLING("billing", "Sales", "பில்லிங்", isMain = true),
    ITEMS("items", "Stock", "பொருட்கள்", isMain = true),
    SEARCH("search", "Search", "தேடல்"),
    REPAIRS("repairs", "Service", "ரிப்பேர்", isMain = true),
    SERVICE_STATUS("service_status", "Service Status", "சர்வீஸ் நிலை"),
    PURCHASES("purchases", "Purchases", "கொள்முதல்"),
    PARTIES("parties", "Parties", "வாடிக்கையாளர்"),
    CASH_BANK("cash_bank", "Cash & Bank", "பணம் & வங்கி"),
    EXPENSES("expenses", "Expenses", "செலவுகள்"),
    REPORTS("reports", "Reports", "அறிக்கைகள்", isMain = true),
    SETTINGS("settings", "Settings", "அமைப்புகள்", isMain = true);

    fun title(lang: String): String = if (lang == "ta") labelTa else labelEn
}

data class ThemePresetSpec(
    val id: String,
    val label: String,
    val accentHex: String,
    val hoverHex: String,
    val bgHex: String,
    val cardHex: String
)

val THEME_PRESETS = listOf(
    ThemePresetSpec("amoled_pink", "AMOLED Pink", "#FF1F8E", "#C20A72", "#000000", "#0E0E12"),
    ThemePresetSpec("midnight_cyan", "Midnight Cyan", "#06B6D4", "#0891B2", "#090D12", "#131A22"),
    ThemePresetSpec("emerald_ledger", "Emerald Ledger", "#10B981", "#059669", "#080C0A", "#121A16"),
    ThemePresetSpec("obsidian_gold", "Obsidian Gold", "#F59E0B", "#D97706", "#0A0908", "#171512")
)

val DEFAULT_CATEGORIES = listOf(
    "Silicon Cover",
    "Printed Cover",
    "Copy Cover",
    "Ipak Cover",
    "Tempered Glass",
    "Chargers",
    "Cables",
    "Batteries",
    "Displays",
    "Accessories",
    "Spares",
    "Phones / IMEI",
    "Repair / Service",
    "back cover"
)

data class UserPermissions(
    val salesCreate: Boolean = false,
    val salesEditReturnCancel: Boolean = false,
    val purchasesCreate: Boolean = false,
    val purchasesEditDelete: Boolean = false,
    val serviceCreate: Boolean = false,
    val serviceUpdateBill: Boolean = false,
    val stockView: Boolean = false,
    val stockEditBulkRates: Boolean = false,
    val partiesDues: Boolean = false,
    val expenses: Boolean = false,
    val reports: Boolean = false,
    val searchModels: Boolean = false,
    val settings: Boolean = false
) {
    companion object {
        fun fullOwner(): UserPermissions = UserPermissions(
            salesCreate = true,
            salesEditReturnCancel = true,
            purchasesCreate = true,
            purchasesEditDelete = true,
            serviceCreate = true,
            serviceUpdateBill = true,
            stockView = true,
            stockEditBulkRates = true,
            partiesDues = true,
            expenses = true,
            reports = true,
            searchModels = true,
            settings = true
        )

        fun preset(name: String): UserPermissions = when (name) {
            "Sales Counter" -> UserPermissions(
                salesCreate = true,
                salesEditReturnCancel = true,
                stockView = true,
                partiesDues = true,
                searchModels = true
            )
            "Purchase & Stock" -> UserPermissions(
                purchasesCreate = true,
                purchasesEditDelete = true,
                stockView = true,
                stockEditBulkRates = true,
                partiesDues = true,
                searchModels = true
            )
            "Service Technician" -> UserPermissions(
                serviceCreate = true,
                serviceUpdateBill = true,
                stockView = true,
                searchModels = true,
                partiesDues = true
            )
            "Cash & Bank" -> UserPermissions(
                partiesDues = true,
                expenses = true,
                reports = true
            )
            "Full Staff" -> UserPermissions(
                salesCreate = true,
                salesEditReturnCancel = true,
                purchasesCreate = true,
                purchasesEditDelete = true,
                serviceCreate = true,
                serviceUpdateBill = true,
                stockView = true,
                stockEditBulkRates = true,
                partiesDues = true,
                expenses = true,
                reports = true,
                searchModels = true,
                settings = false
            )
            else -> UserPermissions()
        }
    }
}

data class ShopUser(
    val id: String = UUID.randomUUID().toString(),
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val role: String = "staff", // "owner" | "co_owner" | "staff"
    val active: Boolean = true,
    val permissions: UserPermissions = UserPermissions()
) {
    val isOwnerOrCoOwner: Boolean
        get() = role.equals("owner", true) || role.equals("co_owner", true) ||
            email.equals(PRIMARY_OWNER_EMAIL, true)

    fun hasPermission(check: (UserPermissions) -> Boolean): Boolean {
        if (!active) return false
        if (isOwnerOrCoOwner) return true
        return check(permissions)
    }
}

data class ShopDayClosedSnapshot(
    val billsCount: Int = 0,
    val salesTotal: Double = 0.0,
    val collectedTotal: Double = 0.0,
    val dueTotal: Double = 0.0,
    val expensesTotal: Double = 0.0
)

data class ShopDayRecord(
    val date: String = "", // YYYY-MM-DD
    val openedAt: Long? = null,
    val closedAt: Long? = null,
    val isLeave: Boolean = false,
    val closedSnapshot: ShopDayClosedSnapshot? = null
)

data class ShopSettings(
    val shopName: String = "AN-NOOR Mobile Service Point",
    val address: String = "Annur, Tamil Nadu",
    val instagram: String = "",
    val phone: String = "",
    val website: String = "",
    val gstNumber: String = "",
    val invoiceTerms: String = "Goods once sold will not be taken back without original bill.",
    val logoDataUrl: String = "",
    val upiQrDataUrl: String = "",
    val upiId: String = "",
    val colorMode: String = "dark", // "dark" | "light"
    val themePreset: String = "amoled_pink",
    val accentColor: String = "#FF1F8E",
    val bgColor: String = "#000000",
    val cardColor: String = "#0E0E12",
    val lowStockDefault: Int = 3,
    val invoicePrefix: String = "AN-",
    val nextInvoiceSeq: Int = 1,
    val nextPurchaseSeq: Int = 1,
    val nextRepairSeq: Int = 1,
    val language: String = "en", // "en" | "ta"
    val openingCashBalance: Double = 0.0,
    val screenMode: String = "auto", // "auto" | "mobile" | "tab" | "computer"
    val useTabNavigation: Boolean = false,
    val whatsappAutoSend: Boolean = false,
    val metaWhatsappPhoneId: String = "",
    val metaWhatsappToken: String = "",
    val backupSchedule: String = "monthly", // "monthly" | "7" | "15" | "30" | "custom" | "off"
    val backupCustomDays: Int = 30,
    val lastBackupAt: Long = 0L,
    val duesReminderFreq: String = "every_3_days", // "off" | "daily" | "every_3_days" | "weekly"
    val customCategories: List<String> = DEFAULT_CATEGORIES,
    val users: List<ShopUser> = listOf(
        ShopUser(
            id = "primary_owner",
            name = "Primary Owner",
            email = PRIMARY_OWNER_EMAIL,
            role = "owner",
            active = true,
            permissions = UserPermissions.fullOwner()
        )
    ),
    val extraFields: Map<String, Any?> = emptyMap()
)

data class StockItem(
    val id: String = UUID.randomUUID().toString(),
    val category: String = "",
    val brand: String = "",
    val model: String = "",
    val color: String = "",
    val alsoFits: String = "",
    val barcode: String = "",
    val unit: String = "pcs", // box / set / pair / unit / service / pcs
    val imei: String = "",
    val costPrice: Double = 0.0,
    val sellingPrice: Double = 0.0,
    val pieces: Int = 0,
    val lowStockAlert: Int = 3,
    val isService: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis(),
    val extraFields: Map<String, Any?> = emptyMap()
) {
    val displayName: String
        get() = listOf(brand.trim(), model.trim()).filter { it.isNotEmpty() }.joinToString(" ")
            .ifEmpty { model.ifEmpty { category.ifEmpty { "Unnamed Item" } } }
}

data class InvoiceLineItem(
    val id: String = UUID.randomUUID().toString(),
    val itemId: String? = null,
    val name: String = "",
    val category: String = "",
    val brand: String = "",
    val model: String = "",
    val color: String = "",
    val qty: Int = 1,
    val rate: Double = 0.0,
    val costPrice: Double = 0.0,
    val isService: Boolean = false,
    val partSource: String = "stock", // "labour" | "stock" | "outside"
    val extraFields: Map<String, Any?> = emptyMap()
) {
    val total: Double get() = qty * rate
}

data class Invoice(
    val id: String = UUID.randomUUID().toString(),
    val invoiceNumber: String = "",
    val docType: String = "invoice", // invoice | estimate | order | challan | return
    val date: String = "", // YYYY-MM-DD
    val customerId: String? = null,
    val customerName: String = "",
    val customerPhone: String = "",
    val billType: String = "product", // product | repair
    val imei: String = "",
    val items: List<InvoiceLineItem> = emptyList(),
    val subtotal: Double = 0.0,
    val discountType: String = "flat", // flat | percent
    val discountAmount: Double = 0.0,
    val gstRate: Double = 0.0, // 0, 5, 12, 18, 28
    val grandTotal: Double = 0.0,
    val paymentMode: String = "Cash", // Cash | UPI | Card | Bank Transfer | Credit (Udhar)
    val amountPaid: Double = 0.0,
    val balanceDue: Double = 0.0,
    val cashTender: Double = 0.0,
    val status: String = "active", // active | cancelled
    val notes: String = "",
    val partSourceMode: String = "labour", // labour | stock | outside (v10 normal bill)
    val linkedPartItemId: String? = null,
    val outsideSupplierName: String = "",
    val outsidePartName: String = "",
    val outsidePartCategory: String = "Display",
    val outsidePartCost: Double = 0.0,
    val outsidePartCharge: Double = 0.0,
    val outsidePartPaymentMode: String = "Cash",
    val linkedPurchaseId: String? = null,
    val labourCharge: Double = 0.0,
    val linkedRepairId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val extraFields: Map<String, Any?> = emptyMap()
)

data class PurchaseLineItem(
    val id: String = UUID.randomUUID().toString(),
    val itemId: String? = null,
    val model: String = "",
    val color: String = "",
    val category: String = "",
    val brand: String = "",
    val barcode: String = "",
    val qty: Int = 1,
    val buyPrice: Double = 0.0,
    val sellPrice: Double = 0.0,
    val extraFields: Map<String, Any?> = emptyMap()
) {
    val total: Double get() = qty * buyPrice
}

data class Purchase(
    val id: String = UUID.randomUUID().toString(),
    val billNumber: String = "",
    val docType: String = "purchase", // purchase | order | return
    val supplierBillNo: String = "",
    val date: String = "",
    val supplierId: String? = null,
    val supplierName: String = "",
    val supplierPhone: String = "",
    val items: List<PurchaseLineItem> = emptyList(),
    val discount: Double = 0.0,
    val transportExtra: Double = 0.0,
    val totalAmount: Double = 0.0,
    val paymentMode: String = "Cash",
    val amountPaid: Double = 0.0,
    val balanceDue: Double = 0.0,
    val status: String = "active", // active | cancelled
    val generateBarcodes: Boolean = false,
    val linkedSaleInvoiceId: String? = null,
    val linkedRepairId: String? = null,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val extraFields: Map<String, Any?> = emptyMap()
)

data class Party(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val phone: String = "",
    val address: String = "",
    val type: String = "customer", // customer | supplier | both
    val openingReceivable: Double = 0.0,
    val openingPayable: Double = 0.0,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val extraFields: Map<String, Any?> = emptyMap()
)

data class PaymentRecord(
    val id: String = UUID.randomUUID().toString(),
    val partyId: String = "",
    val partyName: String = "",
    val direction: String = "in", // in | out
    val amount: Double = 0.0,
    val paymentMode: String = "Cash",
    val date: String = "",
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val extraFields: Map<String, Any?> = emptyMap()
)

data class ExpenseRecord(
    val id: String = UUID.randomUUID().toString(),
    val category: String = "Expense",
    val reason: String = "",
    val amount: Double = 0.0,
    val date: String = "",
    val paymentMode: String = "Cash",
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val extraFields: Map<String, Any?> = emptyMap()
)

data class RepairStatusEntry(
    val status: String = "Received",
    val note: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val updatedBy: String = ""
)

data class RepairJob(
    val id: String = UUID.randomUUID().toString(),
    val jobNumber: String = "",
    val date: String = "",
    val deliveryDate: String = "",
    val deliveredDate: String = "",
    val customerId: String? = null,
    val customerName: String = "",
    val customerPhone: String = "",
    val phoneModel: String = "",
    val imei: String = "",
    val problem: String = "",
    val status: String = "Received", // Received | In-Progress | Ready for Pickup | Delivered
    val partSource: String = "labour", // labour | stock | outside
    val outsideSupplierName: String = "",
    val outsidePartName: String = "",
    val outsidePartCategory: String = "Display",
    val outsidePartPaymentMode: String = "Cash",
    val outsidePartPurchaseId: String? = null,
    val outsidePartItemId: String? = null,
    val partBuyingCost: Double = 0.0,
    val partCharge: Double = 0.0,
    val serviceCharge: Double = 0.0,
    val advancePaid: Double = 0.0,
    val postBillPaid: Double = 0.0,
    val billedInvoiceId: String? = null,
    val billingPaymentMode: String = "Cash",
    val partStockDeducted: Boolean = false,
    val partsUsed: List<String> = emptyList(),
    val notes: String = "",
    val statusHistory: List<RepairStatusEntry> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val extraFields: Map<String, Any?> = emptyMap()
) {
    val totalCharged: Double get() = partCharge + serviceCharge
    val totalPaid: Double get() = advancePaid + postBillPaid
    val balanceDue: Double get() = (totalCharged - totalPaid).coerceAtLeast(0.0)
    val profit: Double get() = totalCharged - partBuyingCost
}

data class BankAccount(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val last4: String = "",
    val openingBalance: Double = 0.0,
    val upiHandle: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val extraFields: Map<String, Any?> = emptyMap()
)

data class CashBankTxn(
    val id: String = UUID.randomUUID().toString(),
    val type: String = "cash_in", // cash_in | cash_out | bank_deposit | bank_withdraw | cheque
    val amount: Double = 0.0,
    val date: String = "",
    val accountId: String? = null,
    val chequeNumber: String = "",
    val chequeStatus: String = "Pending", // Pending | Cleared | Bounced
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val extraFields: Map<String, Any?> = emptyMap()
)

data class CompanyMeta(
    val id: String = "default",
    val name: String = "AN-NOOR Mobile Service Point",
    val dbName: String = "noor_shop_default.db",
    val createdAt: Long = System.currentTimeMillis()
)

data class AutoBackupEntry(
    val id: String = UUID.randomUUID().toString(),
    val createdAt: Long = System.currentTimeMillis(),
    val label: String = "",
    val itemsCount: Int = 0,
    val invoicesCount: Int = 0,
    val filePath: String = ""
)

data class DebugLogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val level: String = "INFO",
    val source: String = "App",
    val message: String = ""
)

data class AiSettings(
    val enabled: Boolean = false,
    val model: String = "gemini-3.5-flash-lite", // "gemini-3.5-flash-lite" | "gemini-3.5-flash"
    val speakRepliesAloud: Boolean = true,
    val replyLanguage: String = "ta", // "ta" | "en"
    val askConfirmationForDestructive: Boolean = true,
    val allowScreenShare: Boolean = true,
    val bubbleOffsetXFraction: Float = 0.92f,
    val bubbleOffsetYFraction: Float = 0.72f
)

data class OfflineUserSession(
    val uid: String = "",
    val email: String = "",
    val displayName: String = "",
    val emailVerified: Boolean = false
)

sealed class AuthGateState {
    data object Loading : AuthGateState()
    data object SignedOut : AuthGateState()
    data class VerifyOwnerEmail(val email: String) : AuthGateState()
    data class AccessBlocked(val email: String, val reason: String) : AuthGateState()
    data class Ready(val session: OfflineUserSession, val user: ShopUser) : AuthGateState()
}
