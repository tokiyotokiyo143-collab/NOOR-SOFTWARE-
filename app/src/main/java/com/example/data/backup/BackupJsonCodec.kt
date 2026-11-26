package com.example.data.backup

import com.example.data.model.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class FullShopBackup(
    val settings: ShopSettings,
    val items: List<StockItem>,
    val invoices: List<Invoice>,
    val purchases: List<Purchase>,
    val parties: List<Party>,
    val payments: List<PaymentRecord>,
    val expenses: List<ExpenseRecord>,
    val repairs: List<RepairJob>,
    val bankAccounts: List<BankAccount>,
    val cashBankTxns: List<CashBankTxn>,
    val shopDays: Map<String, ShopDayRecord> = emptyMap(),
    val lastSavedAt: Long = System.currentTimeMillis()
)

object BackupJsonCodec {

    private fun jsonToValue(v: Any?): Any? = when (v) {
        null, JSONObject.NULL -> null
        is JSONObject -> jsonObjectToMap(v)
        is JSONArray -> (0 until v.length()).map { jsonToValue(v.get(it)) }
        else -> v
    }

    fun jsonObjectToMap(obj: JSONObject): Map<String, Any?> {
        val map = linkedMapOf<String, Any?>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            map[k] = jsonToValue(obj.opt(k))
        }
        return map
    }

    private fun valueToJson(v: Any?): Any = when (v) {
        null -> JSONObject.NULL
        is Map<*, *> -> JSONObject().apply {
            v.forEach { (k, value) ->
                if (k != null) put(k.toString(), valueToJson(value))
            }
        }
        is List<*> -> JSONArray().apply {
            v.forEach { put(valueToJson(it)) }
        }
        else -> v
    }

    private fun JSONObject.extractExtra(known: Set<String>): Map<String, Any?> {
        val extra = linkedMapOf<String, Any?>()
        val keys = keys()
        while (keys.hasNext()) {
            val k = keys.next()
            if (k !in known) extra[k] = jsonToValue(opt(k))
        }
        return extra
    }

    private fun JSONObject.putExtras(extras: Map<String, Any?>) {
        extras.forEach { (k, v) ->
            if (!has(k) && v != null) put(k, valueToJson(v))
        }
    }

    private fun optDoubleSafe(obj: JSONObject, key: String, fallback: Double = 0.0): Double {
        if (!obj.has(key) || obj.isNull(key)) return fallback
        val v = obj.opt(key)
        return when (v) {
            is Number -> v.toDouble()
            is String -> v.toDoubleOrNull() ?: fallback
            else -> fallback
        }
    }

    private fun optIntSafe(obj: JSONObject, key: String, fallback: Int = 0): Int {
        if (!obj.has(key) || obj.isNull(key)) return fallback
        val v = obj.opt(key)
        return when (v) {
            is Number -> v.toInt()
            is String -> v.toDoubleOrNull()?.toInt() ?: fallback
            else -> fallback
        }
    }

    private fun optLongSafe(obj: JSONObject, key: String, fallback: Long = 0L): Long {
        if (!obj.has(key) || obj.isNull(key)) return fallback
        val v = obj.opt(key)
        return when (v) {
            is Number -> v.toLong()
            is String -> v.toLongOrNull() ?: fallback
            else -> fallback
        }
    }

    private fun optStrSafe(obj: JSONObject, key: String, fallback: String = ""): String {
        if (!obj.has(key) || obj.isNull(key)) return fallback
        return obj.optString(key, fallback)
    }

    // ---------------- SETTINGS ----------------

    fun settingsToJson(s: ShopSettings): JSONObject = JSONObject().apply {
        put("shopName", s.shopName)
        put("address", s.address)
        put("instagram", s.instagram)
        put("phone", s.phone)
        put("website", s.website)
        put("gstNumber", s.gstNumber)
        put("invoiceTerms", s.invoiceTerms)
        put("logoDataUrl", s.logoDataUrl)
        put("upiQrDataUrl", s.upiQrDataUrl)
        put("upiId", s.upiId)
        put("colorMode", s.colorMode)
        put("themePreset", s.themePreset)
        put("accentColor", s.accentColor)
        put("bgColor", s.bgColor)
        put("cardColor", s.cardColor)
        put("lowStockDefault", s.lowStockDefault)
        put("invoicePrefix", s.invoicePrefix)
        put("nextInvoiceSeq", s.nextInvoiceSeq)
        put("nextPurchaseSeq", s.nextPurchaseSeq)
        put("nextRepairSeq", s.nextRepairSeq)
        put("language", s.language)
        put("openingCashBalance", s.openingCashBalance)
        put("screenMode", s.screenMode)
        put("useTabNavigation", s.useTabNavigation)
        put("whatsappAutoSend", s.whatsappAutoSend)
        put("metaWhatsappPhoneId", s.metaWhatsappPhoneId)
        put("metaWhatsappToken", s.metaWhatsappToken)
        put("backupSchedule", s.backupSchedule)
        put("backupCustomDays", s.backupCustomDays)
        put("lastBackupAt", s.lastBackupAt)
        put("duesReminderFreq", s.duesReminderFreq)
        put("customCategories", JSONArray(s.customCategories))
        put("users", JSONArray().apply { s.users.forEach { put(userToJson(it)) } })
        putExtras(s.extraFields)
    }

    fun settingsFromJson(obj: JSONObject?): ShopSettings {
        if (obj == null) return ShopSettings()
        val known = setOf(
            "shopName", "address", "instagram", "phone", "website", "gstNumber", "invoiceTerms",
            "logoDataUrl", "upiQrDataUrl", "upiId", "colorMode", "themePreset", "accentColor",
            "bgColor", "cardColor", "lowStockDefault", "invoicePrefix", "nextInvoiceSeq",
            "nextPurchaseSeq", "nextRepairSeq", "language", "openingCashBalance", "screenMode",
            "useTabNavigation", "whatsappAutoSend", "metaWhatsappPhoneId", "metaWhatsappToken",
            "backupSchedule", "backupCustomDays", "lastBackupAt", "duesReminderFreq",
            "customCategories", "users"
        )
        val catsArr = obj.optJSONArray("customCategories")
        val cats = if (catsArr != null && catsArr.length() > 0) {
            (0 until catsArr.length()).mapNotNull { catsArr.optString(it).takeIf { s -> s.isNotBlank() } }
        } else DEFAULT_CATEGORIES

        val usersArr = obj.optJSONArray("users")
        val parsedUsers = if (usersArr != null && usersArr.length() > 0) {
            (0 until usersArr.length()).mapNotNull { i ->
                usersArr.optJSONObject(i)?.let { userFromJson(it) }
            }
        } else ShopSettings().users

        val ensuredUsers = if (parsedUsers.any { it.email.equals(PRIMARY_OWNER_EMAIL, true) }) {
            parsedUsers
        } else {
            listOf(
                ShopUser(
                    id = "primary_owner",
                    name = "Primary Owner",
                    email = PRIMARY_OWNER_EMAIL,
                    role = "owner",
                    active = true,
                    permissions = UserPermissions.fullOwner()
                )
            ) + parsedUsers
        }

        return ShopSettings(
            shopName = optStrSafe(obj, "shopName", "AN-NOOR Mobile Service Point").ifBlank { "AN-NOOR Mobile Service Point" },
            address = optStrSafe(obj, "address", "Annur, Tamil Nadu"),
            instagram = optStrSafe(obj, "instagram"),
            phone = optStrSafe(obj, "phone"),
            website = optStrSafe(obj, "website"),
            gstNumber = optStrSafe(obj, "gstNumber"),
            invoiceTerms = optStrSafe(obj, "invoiceTerms", "Goods once sold will not be taken back without original bill."),
            logoDataUrl = optStrSafe(obj, "logoDataUrl"),
            upiQrDataUrl = optStrSafe(obj, "upiQrDataUrl"),
            upiId = optStrSafe(obj, "upiId"),
            colorMode = optStrSafe(obj, "colorMode", "dark").ifBlank { "dark" },
            themePreset = optStrSafe(obj, "themePreset", "amoled_pink").ifBlank { "amoled_pink" },
            accentColor = optStrSafe(obj, "accentColor", "#FF1F8E").ifBlank { "#FF1F8E" },
            bgColor = optStrSafe(obj, "bgColor", "#000000").ifBlank { "#000000" },
            cardColor = optStrSafe(obj, "cardColor", "#0E0E12").ifBlank { "#0E0E12" },
            lowStockDefault = optIntSafe(obj, "lowStockDefault", 3).coerceAtLeast(0),
            invoicePrefix = optStrSafe(obj, "invoicePrefix", "AN-").ifBlank { "AN-" },
            nextInvoiceSeq = optIntSafe(obj, "nextInvoiceSeq", 1).coerceAtLeast(1),
            nextPurchaseSeq = optIntSafe(obj, "nextPurchaseSeq", 1).coerceAtLeast(1),
            nextRepairSeq = optIntSafe(obj, "nextRepairSeq", 1).coerceAtLeast(1),
            language = optStrSafe(obj, "language", "en").let { if (it == "ta") "ta" else "en" },
            openingCashBalance = optDoubleSafe(obj, "openingCashBalance", 0.0),
            screenMode = optStrSafe(obj, "screenMode", "auto").ifBlank { "auto" },
            useTabNavigation = obj.optBoolean("useTabNavigation", false),
            whatsappAutoSend = obj.optBoolean("whatsappAutoSend", false),
            metaWhatsappPhoneId = optStrSafe(obj, "metaWhatsappPhoneId"),
            metaWhatsappToken = optStrSafe(obj, "metaWhatsappToken"),
            backupSchedule = optStrSafe(obj, "backupSchedule", "monthly").ifBlank { "monthly" },
            backupCustomDays = optIntSafe(obj, "backupCustomDays", 30).coerceIn(1, 365),
            lastBackupAt = optLongSafe(obj, "lastBackupAt", 0L),
            duesReminderFreq = optStrSafe(obj, "duesReminderFreq", "every_3_days").ifBlank { "every_3_days" },
            customCategories = cats,
            users = ensuredUsers,
            extraFields = obj.extractExtra(known)
        )
    }

    fun userToJson(u: ShopUser): JSONObject = JSONObject().apply {
        put("id", u.id)
        put("uid", u.uid)
        put("name", u.name)
        put("email", u.email)
        put("role", u.role)
        put("active", u.active)
        put("permissions", JSONObject().apply {
            put("salesCreate", u.permissions.salesCreate)
            put("salesEditReturnCancel", u.permissions.salesEditReturnCancel)
            put("purchasesCreate", u.permissions.purchasesCreate)
            put("purchasesEditDelete", u.permissions.purchasesEditDelete)
            put("serviceCreate", u.permissions.serviceCreate)
            put("serviceUpdateBill", u.permissions.serviceUpdateBill)
            put("stockView", u.permissions.stockView)
            put("stockEditBulkRates", u.permissions.stockEditBulkRates)
            put("partiesDues", u.permissions.partiesDues)
            put("expenses", u.permissions.expenses)
            put("reports", u.permissions.reports)
            put("searchModels", u.permissions.searchModels)
            put("settings", u.permissions.settings)
        })
    }

    fun userFromJson(obj: JSONObject): ShopUser {
        val p = obj.optJSONObject("permissions")
        val perms = if (p != null) {
            UserPermissions(
                salesCreate = p.optBoolean("salesCreate", false),
                salesEditReturnCancel = p.optBoolean("salesEditReturnCancel", false),
                purchasesCreate = p.optBoolean("purchasesCreate", false),
                purchasesEditDelete = p.optBoolean("purchasesEditDelete", false),
                serviceCreate = p.optBoolean("serviceCreate", false),
                serviceUpdateBill = p.optBoolean("serviceUpdateBill", false),
                stockView = p.optBoolean("stockView", false),
                stockEditBulkRates = p.optBoolean("stockEditBulkRates", false),
                partiesDues = p.optBoolean("partiesDues", false),
                expenses = p.optBoolean("expenses", false),
                reports = p.optBoolean("reports", false),
                searchModels = p.optBoolean("searchModels", false),
                settings = p.optBoolean("settings", false)
            )
        } else UserPermissions()
        return ShopUser(
            id = optStrSafe(obj, "id").ifBlank { UUID.randomUUID().toString() },
            uid = optStrSafe(obj, "uid"),
            name = optStrSafe(obj, "name"),
            email = optStrSafe(obj, "email").lowercase(),
            role = optStrSafe(obj, "role", "staff").ifBlank { "staff" },
            active = obj.optBoolean("active", true),
            permissions = perms
        )
    }

    // ---------------- ITEM ----------------

    fun itemToJson(i: StockItem): JSONObject = JSONObject().apply {
        put("id", i.id)
        put("category", i.category)
        put("brand", i.brand)
        put("model", i.model)
        put("color", i.color)
        put("alsoFits", i.alsoFits)
        put("barcode", i.barcode)
        put("unit", i.unit)
        put("imei", i.imei)
        put("costPrice", i.costPrice)
        put("sellingPrice", i.sellingPrice)
        put("pieces", i.pieces)
        put("lowStockAlert", i.lowStockAlert)
        put("isService", i.isService)
        put("updatedAt", i.updatedAt)
        putExtras(i.extraFields)
    }

    fun itemFromJson(obj: JSONObject): StockItem {
        val known = setOf(
            "id", "category", "brand", "model", "color", "alsoFits", "barcode", "unit",
            "imei", "costPrice", "sellingPrice", "pieces", "lowStockAlert", "isService", "updatedAt"
        )
        return StockItem(
            id = optStrSafe(obj, "id").ifBlank { UUID.randomUUID().toString() },
            category = optStrSafe(obj, "category"),
            brand = optStrSafe(obj, "brand"),
            model = optStrSafe(obj, "model"),
            color = optStrSafe(obj, "color"),
            alsoFits = optStrSafe(obj, "alsoFits"),
            barcode = optStrSafe(obj, "barcode"),
            unit = optStrSafe(obj, "unit", "pcs").ifBlank { "pcs" },
            imei = optStrSafe(obj, "imei"),
            costPrice = optDoubleSafe(obj, "costPrice", 0.0),
            sellingPrice = optDoubleSafe(obj, "sellingPrice", 0.0),
            pieces = optIntSafe(obj, "pieces", 0),
            lowStockAlert = optIntSafe(obj, "lowStockAlert", 3),
            isService = obj.optBoolean("isService", false),
            updatedAt = optLongSafe(obj, "updatedAt", System.currentTimeMillis()),
            extraFields = obj.extractExtra(known)
        )
    }

    // ---------------- INVOICE ----------------

    fun invoiceLineToJson(l: InvoiceLineItem): JSONObject = JSONObject().apply {
        put("id", l.id)
        if (l.itemId != null) put("itemId", l.itemId)
        put("name", l.name)
        put("category", l.category)
        put("brand", l.brand)
        put("model", l.model)
        put("color", l.color)
        put("qty", l.qty)
        put("rate", l.rate)
        put("costPrice", l.costPrice)
        put("isService", l.isService)
        put("partSource", l.partSource)
        putExtras(l.extraFields)
    }

    fun invoiceLineFromJson(obj: JSONObject): InvoiceLineItem {
        val known = setOf(
            "id", "itemId", "name", "category", "brand", "model", "color",
            "qty", "rate", "costPrice", "isService", "partSource", "total"
        )
        return InvoiceLineItem(
            id = optStrSafe(obj, "id").ifBlank { UUID.randomUUID().toString() },
            itemId = optStrSafe(obj, "itemId").takeIf { it.isNotBlank() },
            name = optStrSafe(obj, "name"),
            category = optStrSafe(obj, "category"),
            brand = optStrSafe(obj, "brand"),
            model = optStrSafe(obj, "model"),
            color = optStrSafe(obj, "color"),
            qty = optIntSafe(obj, "qty", 1).coerceAtLeast(1),
            rate = optDoubleSafe(obj, "rate", 0.0),
            costPrice = optDoubleSafe(obj, "costPrice", 0.0),
            isService = obj.optBoolean("isService", false),
            partSource = optStrSafe(obj, "partSource", "stock").ifBlank { "stock" },
            extraFields = obj.extractExtra(known)
        )
    }

    fun invoiceToJson(inv: Invoice): JSONObject = JSONObject().apply {
        put("id", inv.id)
        put("invoiceNumber", inv.invoiceNumber)
        put("docType", inv.docType)
        put("date", inv.date)
        if (inv.customerId != null) put("customerId", inv.customerId)
        put("customerName", inv.customerName)
        put("customerPhone", inv.customerPhone)
        put("billType", inv.billType)
        put("imei", inv.imei)
        put("items", JSONArray().apply { inv.items.forEach { put(invoiceLineToJson(it)) } })
        put("subtotal", inv.subtotal)
        put("discountType", inv.discountType)
        put("discountAmount", inv.discountAmount)
        put("gstRate", inv.gstRate)
        put("grandTotal", inv.grandTotal)
        put("paymentMode", inv.paymentMode)
        put("amountPaid", inv.amountPaid)
        put("balanceDue", inv.balanceDue)
        put("cashTender", inv.cashTender)
        put("status", inv.status)
        put("notes", inv.notes)
        put("partSourceMode", inv.partSourceMode)
        if (inv.linkedPartItemId != null) put("linkedPartItemId", inv.linkedPartItemId)
        put("outsideSupplierName", inv.outsideSupplierName)
        put("outsidePartName", inv.outsidePartName)
        put("outsidePartCategory", inv.outsidePartCategory)
        put("outsidePartCost", inv.outsidePartCost)
        put("outsidePartCharge", inv.outsidePartCharge)
        put("outsidePartPaymentMode", inv.outsidePartPaymentMode)
        if (inv.linkedPurchaseId != null) put("linkedPurchaseId", inv.linkedPurchaseId)
        put("labourCharge", inv.labourCharge)
        if (inv.linkedRepairId != null) put("linkedRepairId", inv.linkedRepairId)
        put("createdAt", inv.createdAt)
        put("updatedAt", inv.updatedAt)
        putExtras(inv.extraFields)
    }

    fun invoiceFromJson(obj: JSONObject): Invoice {
        val known = setOf(
            "id", "invoiceNumber", "docType", "date", "customerId", "customerName", "customerPhone",
            "billType", "imei", "items", "subtotal", "discountType", "discountAmount", "gstRate",
            "grandTotal", "paymentMode", "amountPaid", "balanceDue", "cashTender", "status", "notes",
            "partSourceMode", "linkedPartItemId", "outsideSupplierName", "outsidePartName",
            "outsidePartCategory", "outsidePartCost", "outsidePartCharge", "outsidePartPaymentMode",
            "linkedPurchaseId", "labourCharge", "linkedRepairId", "createdAt", "updatedAt"
        )
        val linesArr = obj.optJSONArray("items")
        val lines = if (linesArr != null) {
            (0 until linesArr.length()).mapNotNull { i ->
                linesArr.optJSONObject(i)?.let { invoiceLineFromJson(it) }
            }
        } else emptyList()

        return Invoice(
            id = optStrSafe(obj, "id").ifBlank { UUID.randomUUID().toString() },
            invoiceNumber = optStrSafe(obj, "invoiceNumber"),
            docType = optStrSafe(obj, "docType", "invoice").ifBlank { "invoice" },
            date = optStrSafe(obj, "date"),
            customerId = optStrSafe(obj, "customerId").takeIf { it.isNotBlank() },
            customerName = optStrSafe(obj, "customerName"),
            customerPhone = optStrSafe(obj, "customerPhone"),
            billType = optStrSafe(obj, "billType", "product").ifBlank { "product" },
            imei = optStrSafe(obj, "imei"),
            items = lines,
            subtotal = optDoubleSafe(obj, "subtotal", 0.0),
            discountType = optStrSafe(obj, "discountType", "flat").ifBlank { "flat" },
            discountAmount = optDoubleSafe(obj, "discountAmount", 0.0),
            gstRate = optDoubleSafe(obj, "gstRate", 0.0),
            grandTotal = optDoubleSafe(obj, "grandTotal", 0.0),
            paymentMode = optStrSafe(obj, "paymentMode", "Cash").ifBlank { "Cash" },
            amountPaid = optDoubleSafe(obj, "amountPaid", 0.0),
            balanceDue = optDoubleSafe(obj, "balanceDue", 0.0),
            cashTender = optDoubleSafe(obj, "cashTender", 0.0),
            status = optStrSafe(obj, "status", "active").ifBlank { "active" },
            notes = optStrSafe(obj, "notes"),
            partSourceMode = optStrSafe(obj, "partSourceMode", "labour").ifBlank { "labour" },
            linkedPartItemId = optStrSafe(obj, "linkedPartItemId").takeIf { it.isNotBlank() },
            outsideSupplierName = optStrSafe(obj, "outsideSupplierName"),
            outsidePartName = optStrSafe(obj, "outsidePartName"),
            outsidePartCategory = optStrSafe(obj, "outsidePartCategory", "Display").ifBlank { "Display" },
            outsidePartCost = optDoubleSafe(obj, "outsidePartCost", 0.0),
            outsidePartCharge = optDoubleSafe(obj, "outsidePartCharge", 0.0),
            outsidePartPaymentMode = optStrSafe(obj, "outsidePartPaymentMode", "Cash").ifBlank { "Cash" },
            linkedPurchaseId = optStrSafe(obj, "linkedPurchaseId").takeIf { it.isNotBlank() },
            labourCharge = optDoubleSafe(obj, "labourCharge", 0.0),
            linkedRepairId = optStrSafe(obj, "linkedRepairId").takeIf { it.isNotBlank() },
            createdAt = optLongSafe(obj, "createdAt", System.currentTimeMillis()),
            updatedAt = optLongSafe(obj, "updatedAt", System.currentTimeMillis()),
            extraFields = obj.extractExtra(known)
        )
    }

    // ---------------- PURCHASE ----------------

    fun purchaseLineToJson(l: PurchaseLineItem): JSONObject = JSONObject().apply {
        put("id", l.id)
        if (l.itemId != null) put("itemId", l.itemId)
        put("model", l.model)
        put("color", l.color)
        put("category", l.category)
        put("brand", l.brand)
        put("barcode", l.barcode)
        put("qty", l.qty)
        put("buyPrice", l.buyPrice)
        put("sellPrice", l.sellPrice)
        putExtras(l.extraFields)
    }

    fun purchaseLineFromJson(obj: JSONObject): PurchaseLineItem {
        val known = setOf("id", "itemId", "model", "color", "category", "brand", "barcode", "qty", "buyPrice", "sellPrice", "total")
        return PurchaseLineItem(
            id = optStrSafe(obj, "id").ifBlank { UUID.randomUUID().toString() },
            itemId = optStrSafe(obj, "itemId").takeIf { it.isNotBlank() },
            model = optStrSafe(obj, "model"),
            color = optStrSafe(obj, "color"),
            category = optStrSafe(obj, "category"),
            brand = optStrSafe(obj, "brand"),
            barcode = optStrSafe(obj, "barcode"),
            qty = optIntSafe(obj, "qty", 1).coerceAtLeast(1),
            buyPrice = optDoubleSafe(obj, "buyPrice", 0.0),
            sellPrice = optDoubleSafe(obj, "sellPrice", 0.0),
            extraFields = obj.extractExtra(known)
        )
    }

    fun purchaseToJson(p: Purchase): JSONObject = JSONObject().apply {
        put("id", p.id)
        put("billNumber", p.billNumber)
        put("docType", p.docType)
        put("supplierBillNo", p.supplierBillNo)
        put("date", p.date)
        if (p.supplierId != null) put("supplierId", p.supplierId)
        put("supplierName", p.supplierName)
        put("supplierPhone", p.supplierPhone)
        put("items", JSONArray().apply { p.items.forEach { put(purchaseLineToJson(it)) } })
        put("discount", p.discount)
        put("transportExtra", p.transportExtra)
        put("totalAmount", p.totalAmount)
        put("paymentMode", p.paymentMode)
        put("amountPaid", p.amountPaid)
        put("balanceDue", p.balanceDue)
        put("status", p.status)
        put("generateBarcodes", p.generateBarcodes)
        if (p.linkedSaleInvoiceId != null) put("linkedSaleInvoiceId", p.linkedSaleInvoiceId)
        if (p.linkedRepairId != null) put("linkedRepairId", p.linkedRepairId)
        put("notes", p.notes)
        put("createdAt", p.createdAt)
        put("updatedAt", p.updatedAt)
        putExtras(p.extraFields)
    }

    fun purchaseFromJson(obj: JSONObject): Purchase {
        val known = setOf(
            "id", "billNumber", "docType", "supplierBillNo", "date", "supplierId", "supplierName",
            "supplierPhone", "items", "discount", "transportExtra", "totalAmount", "paymentMode",
            "amountPaid", "balanceDue", "status", "generateBarcodes", "linkedSaleInvoiceId",
            "linkedRepairId", "notes", "createdAt", "updatedAt"
        )
        val linesArr = obj.optJSONArray("items")
        val lines = if (linesArr != null) {
            (0 until linesArr.length()).mapNotNull { i ->
                linesArr.optJSONObject(i)?.let { purchaseLineFromJson(it) }
            }
        } else emptyList()
        val computedTotal = (lines.sumOf { it.total } - optDoubleSafe(obj, "discount", 0.0) +
            optDoubleSafe(obj, "transportExtra", 0.0)).coerceAtLeast(0.0)
        val total = if (obj.has("totalAmount")) optDoubleSafe(obj, "totalAmount", computedTotal) else computedTotal
        val paid = optDoubleSafe(obj, "amountPaid", 0.0)
        val due = if (obj.has("balanceDue")) optDoubleSafe(obj, "balanceDue", (total - paid).coerceAtLeast(0.0)) else (total - paid).coerceAtLeast(0.0)
        return Purchase(
            id = optStrSafe(obj, "id").ifBlank { UUID.randomUUID().toString() },
            billNumber = optStrSafe(obj, "billNumber"),
            docType = optStrSafe(obj, "docType", "purchase").ifBlank { "purchase" },
            supplierBillNo = optStrSafe(obj, "supplierBillNo"),
            date = optStrSafe(obj, "date"),
            supplierId = optStrSafe(obj, "supplierId").takeIf { it.isNotBlank() },
            supplierName = optStrSafe(obj, "supplierName"),
            supplierPhone = optStrSafe(obj, "supplierPhone"),
            items = lines,
            discount = optDoubleSafe(obj, "discount", 0.0),
            transportExtra = optDoubleSafe(obj, "transportExtra", 0.0),
            totalAmount = total,
            paymentMode = optStrSafe(obj, "paymentMode", "Cash").ifBlank { "Cash" },
            amountPaid = paid,
            balanceDue = due,
            status = optStrSafe(obj, "status", "active").ifBlank { "active" },
            generateBarcodes = obj.optBoolean("generateBarcodes", false),
            linkedSaleInvoiceId = optStrSafe(obj, "linkedSaleInvoiceId").takeIf { it.isNotBlank() },
            linkedRepairId = optStrSafe(obj, "linkedRepairId").takeIf { it.isNotBlank() },
            notes = optStrSafe(obj, "notes"),
            createdAt = optLongSafe(obj, "createdAt", System.currentTimeMillis()),
            updatedAt = optLongSafe(obj, "updatedAt", System.currentTimeMillis()),
            extraFields = obj.extractExtra(known)
        )
    }

    // ---------------- PARTY ----------------

    fun partyToJson(p: Party): JSONObject = JSONObject().apply {
        put("id", p.id)
        put("name", p.name)
        put("phone", p.phone)
        put("address", p.address)
        put("type", p.type)
        put("openingReceivable", p.openingReceivable)
        put("openingPayable", p.openingPayable)
        put("notes", p.notes)
        put("createdAt", p.createdAt)
        put("updatedAt", p.updatedAt)
        putExtras(p.extraFields)
    }

    fun partyFromJson(obj: JSONObject): Party {
        val known = setOf(
            "id", "name", "phone", "address", "type", "openingReceivable", "openingPayable",
            "notes", "createdAt", "updatedAt"
        )
        return Party(
            id = optStrSafe(obj, "id").ifBlank { UUID.randomUUID().toString() },
            name = optStrSafe(obj, "name"),
            phone = optStrSafe(obj, "phone"),
            address = optStrSafe(obj, "address"),
            type = optStrSafe(obj, "type", "customer").ifBlank { "customer" },
            openingReceivable = optDoubleSafe(obj, "openingReceivable", 0.0),
            openingPayable = optDoubleSafe(obj, "openingPayable", 0.0),
            notes = optStrSafe(obj, "notes"),
            createdAt = optLongSafe(obj, "createdAt", System.currentTimeMillis()),
            updatedAt = optLongSafe(obj, "updatedAt", System.currentTimeMillis()),
            extraFields = obj.extractExtra(known)
        )
    }

    // ---------------- PAYMENT ----------------

    fun paymentToJson(p: PaymentRecord): JSONObject = JSONObject().apply {
        put("id", p.id)
        put("partyId", p.partyId)
        put("partyName", p.partyName)
        put("direction", p.direction)
        put("amount", p.amount)
        put("paymentMode", p.paymentMode)
        put("date", p.date)
        put("notes", p.notes)
        put("createdAt", p.createdAt)
        putExtras(p.extraFields)
    }

    fun paymentFromJson(obj: JSONObject): PaymentRecord {
        val known = setOf("id", "partyId", "partyName", "direction", "amount", "paymentMode", "date", "notes", "createdAt")
        return PaymentRecord(
            id = optStrSafe(obj, "id").ifBlank { UUID.randomUUID().toString() },
            partyId = optStrSafe(obj, "partyId"),
            partyName = optStrSafe(obj, "partyName"),
            direction = optStrSafe(obj, "direction", "in").ifBlank { "in" },
            amount = optDoubleSafe(obj, "amount", 0.0),
            paymentMode = optStrSafe(obj, "paymentMode", "Cash").ifBlank { "Cash" },
            date = optStrSafe(obj, "date"),
            notes = optStrSafe(obj, "notes"),
            createdAt = optLongSafe(obj, "createdAt", System.currentTimeMillis()),
            extraFields = obj.extractExtra(known)
        )
    }

    // ---------------- EXPENSE ----------------

    fun expenseToJson(e: ExpenseRecord): JSONObject = JSONObject().apply {
        put("id", e.id)
        put("category", e.category)
        put("reason", e.reason)
        put("amount", e.amount)
        put("date", e.date)
        put("paymentMode", e.paymentMode)
        put("notes", e.notes)
        put("createdAt", e.createdAt)
        put("updatedAt", e.updatedAt)
        putExtras(e.extraFields)
    }

    fun expenseFromJson(obj: JSONObject): ExpenseRecord {
        val known = setOf("id", "category", "reason", "amount", "date", "paymentMode", "notes", "createdAt", "updatedAt")
        return ExpenseRecord(
            id = optStrSafe(obj, "id").ifBlank { UUID.randomUUID().toString() },
            category = optStrSafe(obj, "category", "Expense").ifBlank { "Expense" },
            reason = optStrSafe(obj, "reason"),
            amount = optDoubleSafe(obj, "amount", 0.0),
            date = optStrSafe(obj, "date"),
            paymentMode = optStrSafe(obj, "paymentMode", "Cash").ifBlank { "Cash" },
            notes = optStrSafe(obj, "notes"),
            createdAt = optLongSafe(obj, "createdAt", System.currentTimeMillis()),
            updatedAt = optLongSafe(obj, "updatedAt", System.currentTimeMillis()),
            extraFields = obj.extractExtra(known)
        )
    }

    // ---------------- REPAIR ----------------

    fun repairToJson(r: RepairJob): JSONObject = JSONObject().apply {
        put("id", r.id)
        put("jobNumber", r.jobNumber)
        put("date", r.date)
        put("deliveryDate", r.deliveryDate)
        put("deliveredDate", r.deliveredDate)
        if (r.customerId != null) put("customerId", r.customerId)
        put("customerName", r.customerName)
        put("customerPhone", r.customerPhone)
        put("phoneModel", r.phoneModel)
        put("imei", r.imei)
        put("problem", r.problem)
        put("status", r.status)
        put("partSource", r.partSource)
        put("outsideSupplierName", r.outsideSupplierName)
        put("outsidePartName", r.outsidePartName)
        put("outsidePartCategory", r.outsidePartCategory)
        put("outsidePartPaymentMode", r.outsidePartPaymentMode)
        if (r.outsidePartPurchaseId != null) put("outsidePartPurchaseId", r.outsidePartPurchaseId)
        if (r.outsidePartItemId != null) put("outsidePartItemId", r.outsidePartItemId)
        put("partBuyingCost", r.partBuyingCost)
        put("partCharge", r.partCharge)
        put("serviceCharge", r.serviceCharge)
        put("advancePaid", r.advancePaid)
        put("postBillPaid", r.postBillPaid)
        if (r.billedInvoiceId != null) put("billedInvoiceId", r.billedInvoiceId)
        put("billingPaymentMode", r.billingPaymentMode)
        put("partStockDeducted", r.partStockDeducted)
        put("partsUsed", JSONArray(r.partsUsed))
        put("notes", r.notes)
        put("statusHistory", JSONArray().apply {
            r.statusHistory.forEach { h ->
                put(JSONObject().apply {
                    put("status", h.status)
                    put("note", h.note)
                    put("timestamp", h.timestamp)
                    put("updatedBy", h.updatedBy)
                })
            }
        })
        put("createdAt", r.createdAt)
        put("updatedAt", r.updatedAt)
        putExtras(r.extraFields)
    }

    fun repairFromJson(obj: JSONObject): RepairJob {
        val known = setOf(
            "id", "jobNumber", "date", "deliveryDate", "deliveredDate", "customerId", "customerName",
            "customerPhone", "phoneModel", "imei", "problem", "status", "partSource",
            "outsideSupplierName", "outsidePartName", "outsidePartCategory", "outsidePartPaymentMode",
            "outsidePartPurchaseId", "outsidePartItemId", "partBuyingCost", "partCharge",
            "serviceCharge", "advancePaid", "postBillPaid", "billedInvoiceId", "billingPaymentMode",
            "partStockDeducted", "partsUsed", "notes", "statusHistory", "createdAt", "updatedAt"
        )
        val partsArr = obj.optJSONArray("partsUsed")
        val partsUsed = if (partsArr != null) {
            (0 until partsArr.length()).mapNotNull { partsArr.optString(it).takeIf { s -> s.isNotBlank() } }
        } else emptyList()
        val histArr = obj.optJSONArray("statusHistory")
        val history = if (histArr != null) {
            (0 until histArr.length()).mapNotNull { i ->
                histArr.optJSONObject(i)?.let { h ->
                    RepairStatusEntry(
                        status = optStrSafe(h, "status", "Received"),
                        note = optStrSafe(h, "note"),
                        timestamp = optLongSafe(h, "timestamp", System.currentTimeMillis()),
                        updatedBy = optStrSafe(h, "updatedBy")
                    )
                }
            }
        } else emptyList()

        return RepairJob(
            id = optStrSafe(obj, "id").ifBlank { UUID.randomUUID().toString() },
            jobNumber = optStrSafe(obj, "jobNumber"),
            date = optStrSafe(obj, "date"),
            deliveryDate = optStrSafe(obj, "deliveryDate"),
            deliveredDate = optStrSafe(obj, "deliveredDate"),
            customerId = optStrSafe(obj, "customerId").takeIf { it.isNotBlank() },
            customerName = optStrSafe(obj, "customerName"),
            customerPhone = optStrSafe(obj, "customerPhone"),
            phoneModel = optStrSafe(obj, "phoneModel"),
            imei = optStrSafe(obj, "imei"),
            problem = optStrSafe(obj, "problem"),
            status = optStrSafe(obj, "status", "Received").ifBlank { "Received" },
            partSource = optStrSafe(obj, "partSource", "labour").ifBlank { "labour" },
            outsideSupplierName = optStrSafe(obj, "outsideSupplierName"),
            outsidePartName = optStrSafe(obj, "outsidePartName"),
            outsidePartCategory = optStrSafe(obj, "outsidePartCategory", "Display").ifBlank { "Display" },
            outsidePartPaymentMode = optStrSafe(obj, "outsidePartPaymentMode", "Cash").ifBlank { "Cash" },
            outsidePartPurchaseId = optStrSafe(obj, "outsidePartPurchaseId").takeIf { it.isNotBlank() },
            outsidePartItemId = optStrSafe(obj, "outsidePartItemId").takeIf { it.isNotBlank() },
            partBuyingCost = optDoubleSafe(obj, "partBuyingCost", 0.0),
            partCharge = optDoubleSafe(obj, "partCharge", 0.0),
            serviceCharge = optDoubleSafe(obj, "serviceCharge", 0.0),
            advancePaid = optDoubleSafe(obj, "advancePaid", 0.0),
            postBillPaid = optDoubleSafe(obj, "postBillPaid", 0.0),
            billedInvoiceId = optStrSafe(obj, "billedInvoiceId").takeIf { it.isNotBlank() },
            billingPaymentMode = optStrSafe(obj, "billingPaymentMode", "Cash").ifBlank { "Cash" },
            partStockDeducted = obj.optBoolean("partStockDeducted", false),
            partsUsed = partsUsed,
            notes = optStrSafe(obj, "notes"),
            statusHistory = history,
            createdAt = optLongSafe(obj, "createdAt", System.currentTimeMillis()),
            updatedAt = optLongSafe(obj, "updatedAt", System.currentTimeMillis()),
            extraFields = obj.extractExtra(known)
        )
    }

    // ---------------- BANK ACCOUNT & CASH BANK TXN ----------------

    fun bankAccountToJson(b: BankAccount): JSONObject = JSONObject().apply {
        put("id", b.id)
        put("name", b.name)
        put("last4", b.last4)
        put("openingBalance", b.openingBalance)
        put("upiHandle", b.upiHandle)
        put("createdAt", b.createdAt)
        putExtras(b.extraFields)
    }

    fun bankAccountFromJson(obj: JSONObject): BankAccount {
        val known = setOf("id", "name", "last4", "openingBalance", "upiHandle", "createdAt")
        return BankAccount(
            id = optStrSafe(obj, "id").ifBlank { UUID.randomUUID().toString() },
            name = optStrSafe(obj, "name"),
            last4 = optStrSafe(obj, "last4"),
            openingBalance = optDoubleSafe(obj, "openingBalance", 0.0),
            upiHandle = optStrSafe(obj, "upiHandle"),
            createdAt = optLongSafe(obj, "createdAt", System.currentTimeMillis()),
            extraFields = obj.extractExtra(known)
        )
    }

    fun cashBankTxnToJson(t: CashBankTxn): JSONObject = JSONObject().apply {
        put("id", t.id)
        put("type", t.type)
        put("amount", t.amount)
        put("date", t.date)
        if (t.accountId != null) put("accountId", t.accountId)
        put("chequeNumber", t.chequeNumber)
        put("chequeStatus", t.chequeStatus)
        put("notes", t.notes)
        put("createdAt", t.createdAt)
        putExtras(t.extraFields)
    }

    fun cashBankTxnFromJson(obj: JSONObject): CashBankTxn {
        val known = setOf("id", "type", "amount", "date", "accountId", "chequeNumber", "chequeStatus", "notes", "createdAt")
        return CashBankTxn(
            id = optStrSafe(obj, "id").ifBlank { UUID.randomUUID().toString() },
            type = optStrSafe(obj, "type", "cash_in").ifBlank { "cash_in" },
            amount = optDoubleSafe(obj, "amount", 0.0),
            date = optStrSafe(obj, "date"),
            accountId = optStrSafe(obj, "accountId").takeIf { it.isNotBlank() },
            chequeNumber = optStrSafe(obj, "chequeNumber"),
            chequeStatus = optStrSafe(obj, "chequeStatus", "Pending").ifBlank { "Pending" },
            notes = optStrSafe(obj, "notes"),
            createdAt = optLongSafe(obj, "createdAt", System.currentTimeMillis()),
            extraFields = obj.extractExtra(known)
        )
    }

    // ---------------- SHOP DAY ----------------

    fun shopDayToJson(d: ShopDayRecord): JSONObject = JSONObject().apply {
        put("date", d.date)
        if (d.openedAt != null) put("openedAt", d.openedAt)
        if (d.closedAt != null) put("closedAt", d.closedAt)
        put("isLeave", d.isLeave)
        if (d.closedSnapshot != null) {
            put("closedSnapshot", JSONObject().apply {
                put("billsCount", d.closedSnapshot.billsCount)
                put("salesTotal", d.closedSnapshot.salesTotal)
                put("collectedTotal", d.closedSnapshot.collectedTotal)
                put("dueTotal", d.closedSnapshot.dueTotal)
                put("expensesTotal", d.closedSnapshot.expensesTotal)
            })
        }
    }

    fun shopDayFromJson(obj: JSONObject): ShopDayRecord {
        val snapObj = obj.optJSONObject("closedSnapshot")
        val snap = snapObj?.let {
            ShopDayClosedSnapshot(
                billsCount = optIntSafe(it, "billsCount", 0),
                salesTotal = optDoubleSafe(it, "salesTotal", 0.0),
                collectedTotal = optDoubleSafe(it, "collectedTotal", 0.0),
                dueTotal = optDoubleSafe(it, "dueTotal", 0.0),
                expensesTotal = optDoubleSafe(it, "expensesTotal", 0.0)
            )
        }
        return ShopDayRecord(
            date = optStrSafe(obj, "date"),
            openedAt = if (obj.has("openedAt") && !obj.isNull("openedAt")) optLongSafe(obj, "openedAt") else null,
            closedAt = if (obj.has("closedAt") && !obj.isNull("closedAt")) optLongSafe(obj, "closedAt") else null,
            isLeave = obj.optBoolean("isLeave", false),
            closedSnapshot = snap
        )
    }

    // ---------------- FULL BACKUP ----------------

    fun exportToJsonString(backup: FullShopBackup): String {
        val root = JSONObject().apply {
            put("settings", settingsToJson(backup.settings))
            put("items", JSONArray().apply { backup.items.forEach { put(itemToJson(it)) } })
            put("invoices", JSONArray().apply { backup.invoices.forEach { put(invoiceToJson(it)) } })
            put("purchases", JSONArray().apply { backup.purchases.forEach { put(purchaseToJson(it)) } })
            put("parties", JSONArray().apply { backup.parties.forEach { put(partyToJson(it)) } })
            put("payments", JSONArray().apply { backup.payments.forEach { put(paymentToJson(it)) } })
            put("expenses", JSONArray().apply { backup.expenses.forEach { put(expenseToJson(it)) } })
            put("repairs", JSONArray().apply { backup.repairs.forEach { put(repairToJson(it)) } })
            put("bankAccounts", JSONArray().apply { backup.bankAccounts.forEach { put(bankAccountToJson(it)) } })
            put("cashBankTxns", JSONArray().apply { backup.cashBankTxns.forEach { put(cashBankTxnToJson(it)) } })
            put("shopDays", JSONObject().apply {
                backup.shopDays.forEach { (k, v) -> put(k, shopDayToJson(v)) }
            })
            put("lastSavedAt", backup.lastSavedAt)
        }
        return root.toString(2)
    }

    fun parseBackupJson(rawJson: String): Result<FullShopBackup> {
        return try {
            val root = JSONObject(rawJson)
            val hasRecognizedKey = listOf(
                "settings", "items", "invoices", "purchases", "parties",
                "payments", "expenses", "repairs", "bankAccounts", "cashBankTxns"
            ).any { root.has(it) }
            if (!hasRecognizedKey) {
                return Result.failure(IllegalArgumentException("Missing Noor backup keys."))
            }

            fun <T> parseList(key: String, mapper: (JSONObject) -> T): List<T> {
                val arr = root.optJSONArray(key) ?: return emptyList()
                return (0 until arr.length()).mapNotNull { i ->
                    arr.optJSONObject(i)?.let(mapper)
                }
            }

            val shopDaysMap = linkedMapOf<String, ShopDayRecord>()
            val sdObj = root.optJSONObject("shopDays")
            if (sdObj != null) {
                val keys = sdObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    sdObj.optJSONObject(k)?.let { shopDaysMap[k] = shopDayFromJson(it) }
                }
            }

            Result.success(
                FullShopBackup(
                    settings = settingsFromJson(root.optJSONObject("settings")),
                    items = parseList("items", ::itemFromJson),
                    invoices = parseList("invoices", ::invoiceFromJson),
                    purchases = parseList("purchases", ::purchaseFromJson),
                    parties = parseList("parties", ::partyFromJson),
                    payments = parseList("payments", ::paymentFromJson),
                    expenses = parseList("expenses", ::expenseFromJson),
                    repairs = parseList("repairs", ::repairFromJson),
                    bankAccounts = parseList("bankAccounts", ::bankAccountFromJson),
                    cashBankTxns = parseList("cashBankTxns", ::cashBankTxnFromJson),
                    shopDays = shopDaysMap,
                    lastSavedAt = optLongSafe(root, "lastSavedAt", System.currentTimeMillis())
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
