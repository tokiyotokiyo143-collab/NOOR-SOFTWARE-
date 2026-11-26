package com.example.data.repository

import android.content.Context
import com.example.data.backup.BackupJsonCodec
import com.example.data.backup.FullShopBackup
import com.example.data.local.*
import com.example.data.model.*
import com.example.data.remote.FirestoreSyncEngine
import com.example.data.remote.SyncStatusState
import com.example.util.Formatters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class ItemSoldSummary(
    val soldQty: Int = 0,
    val lastInvoiceNumber: String = "",
    val lastSaleDate: String = ""
)

data class ShopAnalyticsSnapshot(
    val cashInHand: Double = 0.0,
    val openingCashToday: Double = 0.0,
    val openingCashDate: String = "",
    val bankBalance: Double = 0.0,
    val partySummaries: List<ShopRepository.PartyBalanceSummary> = emptyList(),
    val partySummaryById: Map<String, ShopRepository.PartyBalanceSummary> = emptyMap(),
    val totalReceivables: Double = 0.0,
    val totalPayables: Double = 0.0,
    val soldStatsByItemId: Map<String, ItemSoldSummary> = emptyMap(),
    val soldItemIds: Set<String> = emptySet()
)

private class CachedPayloadDecoder<T : Any>(
    private val decode: (JSONObject) -> T
) {
    private val cache = ConcurrentHashMap<String, Pair<String, T>>()

    fun decodeAll(rows: List<Pair<String, String>>): List<T> {
        val activeIds = HashSet<String>(rows.size * 2)
        val result = ArrayList<T>(rows.size)
        for ((id, payload) in rows) {
            activeIds.add(id)
            val cached = cache[id]
            if (cached != null && cached.first == payload) {
                result.add(cached.second)
            } else {
                val parsed = runCatching { decode(JSONObject(payload)) }.getOrNull()
                if (parsed != null) {
                    cache[id] = payload to parsed
                    result.add(parsed)
                }
            }
        }
        if (cache.size > activeIds.size) {
            cache.keys.retainAll(activeIds)
        }
        return result
    }

    fun clear() {
        cache.clear()
    }
}

class ShopRepository(
    private val context: Context,
    val secretStore: KeystoreSecretStore
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _activeCompany = MutableStateFlow(loadInitialCompany())
    val activeCompany: StateFlow<CompanyMeta> = _activeCompany.asStateFlow()

    private val _companies = MutableStateFlow(secretStore.getCompanies())
    val companies: StateFlow<List<CompanyMeta>> = _companies.asStateFlow()

    private val _debugLogs = MutableStateFlow<List<DebugLogEntry>>(emptyList())
    val debugLogs: StateFlow<List<DebugLogEntry>> = _debugLogs.asStateFlow()

    private var db: AppDatabase = DatabaseProvider.get(context, _activeCompany.value.dbName)

    private val _settings = MutableStateFlow(ShopSettings())
    val settings: StateFlow<ShopSettings> = _settings.asStateFlow()

    private val _items = MutableStateFlow<List<StockItem>>(emptyList())
    val items: StateFlow<List<StockItem>> = _items.asStateFlow()

    private val _invoices = MutableStateFlow<List<Invoice>>(emptyList())
    val invoices: StateFlow<List<Invoice>> = _invoices.asStateFlow()

    private val _purchases = MutableStateFlow<List<Purchase>>(emptyList())
    val purchases: StateFlow<List<Purchase>> = _purchases.asStateFlow()

    private val _parties = MutableStateFlow<List<Party>>(emptyList())
    val parties: StateFlow<List<Party>> = _parties.asStateFlow()

    private val _payments = MutableStateFlow<List<PaymentRecord>>(emptyList())
    val payments: StateFlow<List<PaymentRecord>> = _payments.asStateFlow()

    private val _expenses = MutableStateFlow<List<ExpenseRecord>>(emptyList())
    val expenses: StateFlow<List<ExpenseRecord>> = _expenses.asStateFlow()

    private val _repairs = MutableStateFlow<List<RepairJob>>(emptyList())
    val repairs: StateFlow<List<RepairJob>> = _repairs.asStateFlow()

    private val _bankAccounts = MutableStateFlow<List<BankAccount>>(emptyList())
    val bankAccounts: StateFlow<List<BankAccount>> = _bankAccounts.asStateFlow()

    private val _cashBankTxns = MutableStateFlow<List<CashBankTxn>>(emptyList())
    val cashBankTxns: StateFlow<List<CashBankTxn>> = _cashBankTxns.asStateFlow()

    private val _shopDays = MutableStateFlow<List<ShopDayRecord>>(emptyList())
    val shopDays: StateFlow<List<ShopDayRecord>> = _shopDays.asStateFlow()

    private val _autoBackups = MutableStateFlow<List<AutoBackupEntry>>(emptyList())
    val autoBackups: StateFlow<List<AutoBackupEntry>> = _autoBackups.asStateFlow()

    private val _ownerCosts = MutableStateFlow<Map<String, Double>>(emptyMap())

    private var isOwnerOrCoOwnerSession: Boolean = true
    private var dbCollectJob: kotlinx.coroutines.Job? = null

    val syncEngine = FirestoreSyncEngine(
        context = context,
        onRemoteSettings = { remote ->
            val merged = remote.copy(
                logoDataUrl = remote.logoDataUrl.ifBlank { _settings.value.logoDataUrl },
                upiQrDataUrl = remote.upiQrDataUrl.ifBlank { _settings.value.upiQrDataUrl }
            )
            saveSettingsLocalOnly(merged)
        },
        onRemoteOwnerCosts = { costs ->
            _ownerCosts.value = costs
            if (isOwnerOrCoOwnerSession && costs.isNotEmpty()) {
                val changed = mutableListOf<StockItem>()
                val updated = _items.value.map { item ->
                    val realCost = costs[item.id]
                    if (realCost != null && item.costPrice != realCost) {
                        val copy = item.copy(costPrice = realCost)
                        changed.add(copy)
                        copy
                    } else item
                }
                if (changed.isNotEmpty()) {
                    db.itemDao().upsertAll(changed.map { it.toEntity() })
                }
                _items.value = updated
            }
        },
        onRemoteUsers = { remoteUsers ->
            val current = _settings.value
            val mergedUsers = (remoteUsers + current.users.filter { local ->
                remoteUsers.none { it.email.equals(local.email, true) }
            }).distinctBy { it.email.lowercase() }
            saveSettingsLocalOnly(current.copy(users = mergedUsers))
        },
        onRemoteItems = { remoteItems ->
            val costs = _ownerCosts.value
            val entities = remoteItems.map { item ->
                val localExisting = _items.value.find { it.id == item.id }
                val finalCost = when {
                    !isOwnerOrCoOwnerSession -> 0.0
                    costs[item.id] != null -> costs[item.id]!!
                    item.costPrice > 0.0 -> item.costPrice
                    else -> localExisting?.costPrice ?: 0.0
                }
                item.copy(costPrice = finalCost).toEntity()
            }
            db.itemDao().upsertAll(entities)
        },
        onRemoteInvoices = { list -> db.invoiceDao().upsertAll(list.map { it.toEntity() }) },
        onRemotePurchases = { list -> db.purchaseDao().upsertAll(list.map { it.toEntity() }) },
        onRemoteParties = { list -> db.partyDao().upsertAll(list.map { it.toEntity() }) },
        onRemotePayments = { list -> db.paymentDao().upsertAll(list.map { it.toEntity() }) },
        onRemoteExpenses = { list -> db.expenseDao().upsertAll(list.map { it.toEntity() }) },
        onRemoteRepairs = { list -> db.repairDao().upsertAll(list.map { it.toEntity() }) },
        onRemoteBankAccounts = { list -> db.bankAccountDao().upsertAll(list.map { it.toEntity() }) },
        onRemoteCashBankTxns = { list -> db.cashBankTxnDao().upsertAll(list.map { it.toEntity() }) },
        onLog = { lvl, src, msg -> addDebugLog(lvl, src, msg) }
    )

    val syncStatus: StateFlow<SyncStatusState> = syncEngine.status

    private val itemDecoder = CachedPayloadDecoder { BackupJsonCodec.itemFromJson(it) }
    private val invoiceDecoder = CachedPayloadDecoder { BackupJsonCodec.invoiceFromJson(it) }
    private val purchaseDecoder = CachedPayloadDecoder { BackupJsonCodec.purchaseFromJson(it) }
    private val partyDecoder = CachedPayloadDecoder { BackupJsonCodec.partyFromJson(it) }
    private val paymentDecoder = CachedPayloadDecoder { BackupJsonCodec.paymentFromJson(it) }
    private val expenseDecoder = CachedPayloadDecoder { BackupJsonCodec.expenseFromJson(it) }
    private val repairDecoder = CachedPayloadDecoder { BackupJsonCodec.repairFromJson(it) }
    private val bankDecoder = CachedPayloadDecoder { BackupJsonCodec.bankAccountFromJson(it) }
    private val cashBankDecoder = CachedPayloadDecoder { BackupJsonCodec.cashBankTxnFromJson(it) }
    private val shopDayDecoder = CachedPayloadDecoder { BackupJsonCodec.shopDayFromJson(it) }

    private data class AnalyticsPartA(
        val settings: ShopSettings,
        val items: List<StockItem>,
        val invoices: List<Invoice>,
        val purchases: List<Purchase>,
        val parties: List<Party>
    )

    private data class AnalyticsPartB(
        val payments: List<PaymentRecord>,
        val expenses: List<ExpenseRecord>,
        val repairs: List<RepairJob>,
        val bankAccounts: List<BankAccount>,
        val cashBankTxns: List<CashBankTxn>
    )

    val analytics: StateFlow<ShopAnalyticsSnapshot> = combine(
        combine(_settings, _items, _invoices, _purchases, _parties, ::AnalyticsPartA),
        combine(_payments, _expenses, _repairs, _bankAccounts, _cashBankTxns, ::AnalyticsPartB)
    ) { a, b ->
        computeAnalyticsSnapshot(
            settings = a.settings,
            invoices = a.invoices,
            purchases = a.purchases,
            parties = a.parties,
            payments = b.payments,
            expenses = b.expenses,
            repairs = b.repairs,
            bankAccounts = b.bankAccounts,
            cashBankTxns = b.cashBankTxns
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(scope, SharingStarted.Eagerly, ShopAnalyticsSnapshot())

    init {
        attachDatabaseObservers()
    }

    private fun loadInitialCompany(): CompanyMeta {
        val list = secretStore.getCompanies()
        val activeId = secretStore.getActiveCompanyId()
        return list.firstOrNull { it.id == activeId } ?: list.first()
    }

    private fun attachDatabaseObservers() {
        dbCollectJob?.cancel()
        itemDecoder.clear()
        invoiceDecoder.clear()
        purchaseDecoder.clear()
        partyDecoder.clear()
        paymentDecoder.clear()
        expenseDecoder.clear()
        repairDecoder.clear()
        bankDecoder.clear()
        cashBankDecoder.clear()
        shopDayDecoder.clear()

        dbCollectJob = scope.launch {
            launch {
                db.kvDao().observe("settings").collect { kv ->
                    _settings.value = if (kv != null) {
                        runCatching { BackupJsonCodec.settingsFromJson(JSONObject(kv.jsonValue)) }
                            .getOrDefault(ShopSettings())
                    } else ShopSettings()
                }
            }
            launch {
                db.itemDao().observeAll().collect { rows ->
                    _items.value = itemDecoder.decodeAll(rows.map { it.id to it.jsonPayload })
                }
            }
            launch {
                db.invoiceDao().observeAll().collect { rows ->
                    _invoices.value = invoiceDecoder.decodeAll(rows.map { it.id to it.jsonPayload })
                }
            }
            launch {
                db.purchaseDao().observeAll().collect { rows ->
                    _purchases.value = purchaseDecoder.decodeAll(rows.map { it.id to it.jsonPayload })
                }
            }
            launch {
                db.partyDao().observeAll().collect { rows ->
                    _parties.value = partyDecoder.decodeAll(rows.map { it.id to it.jsonPayload })
                }
            }
            launch {
                db.paymentDao().observeAll().collect { rows ->
                    _payments.value = paymentDecoder.decodeAll(rows.map { it.id to it.jsonPayload })
                }
            }
            launch {
                db.expenseDao().observeAll().collect { rows ->
                    _expenses.value = expenseDecoder.decodeAll(rows.map { it.id to it.jsonPayload })
                }
            }
            launch {
                db.repairDao().observeAll().collect { rows ->
                    _repairs.value = repairDecoder.decodeAll(rows.map { it.id to it.jsonPayload })
                }
            }
            launch {
                db.bankAccountDao().observeAll().collect { rows ->
                    _bankAccounts.value = bankDecoder.decodeAll(rows.map { it.id to it.jsonPayload })
                }
            }
            launch {
                db.cashBankTxnDao().observeAll().collect { rows ->
                    _cashBankTxns.value = cashBankDecoder.decodeAll(rows.map { it.id to it.jsonPayload })
                }
            }
            launch {
                db.shopDayDao().observeAll().collect { rows ->
                    _shopDays.value = shopDayDecoder.decodeAll(rows.map { it.date to it.jsonPayload })
                }
            }
            launch {
                db.autoBackupDao().observeAll().collect { rows ->
                    _autoBackups.value = rows.map {
                        AutoBackupEntry(it.id, it.createdAt, it.label, it.itemsCount, it.invoicesCount, it.filePath)
                    }
                }
            }
        }
    }

    fun startCloudSync(session: OfflineUserSession, user: ShopUser) {
        isOwnerOrCoOwnerSession = user.isOwnerOrCoOwner
        syncEngine.startListening(
            userEmail = session.email,
            userUid = session.uid,
            ownerOrCoOwner = user.isOwnerOrCoOwner,
            scopeScopeCallback = { block -> scope.launch { block() } }
        )
    }

    fun stopCloudSync() {
        syncEngine.stopListening()
    }

    fun addDebugLog(level: String, source: String, message: String) {
        val entry = DebugLogEntry(System.currentTimeMillis(), level, source, message)
        _debugLogs.value = (listOf(entry) + _debugLogs.value).take(100)
    }

    // ---------------- SETTINGS ----------------

    private suspend fun saveSettingsLocalOnly(settings: ShopSettings) {
        val json = BackupJsonCodec.settingsToJson(settings).toString()
        db.kvDao().put(KvEntity("settings", json, System.currentTimeMillis()))
        _settings.value = settings
    }

    suspend fun updateSettings(transform: (ShopSettings) -> ShopSettings) {
        val updated = transform(_settings.value)
        saveSettingsLocalOnly(updated)
        syncEngine.pushSettings(updated)
    }

    suspend fun addOrUpdateUser(user: ShopUser) {
        updateSettings { s ->
            val list = s.users.toMutableList()
            val idx = list.indexOfFirst { it.id == user.id || it.email.equals(user.email, true) }
            if (idx >= 0) list[idx] = user else list.add(user)
            s.copy(users = list)
        }
        syncEngine.pushUser(user, _settings.value)
    }

    suspend fun removeUser(userId: String) {
        updateSettings { s ->
            s.copy(users = s.users.filterNot { it.id == userId })
        }
        syncEngine.deleteUserDoc(userId)
    }

    // ---------------- STOCK ITEMS ----------------

    private fun clampItem(item: StockItem): StockItem = item.copy(
        category = Formatters.clampName(item.category),
        brand = Formatters.clampName(item.brand),
        model = Formatters.clampName(item.model),
        color = Formatters.clampName(item.color),
        alsoFits = Formatters.clampNotes(item.alsoFits),
        barcode = Formatters.clampName(item.barcode),
        imei = if (item.imei.isBlank()) "" else Formatters.clampImei(item.imei),
        costPrice = Formatters.clampAmount(item.costPrice),
        sellingPrice = Formatters.clampAmount(item.sellingPrice),
        pieces = item.pieces.coerceAtLeast(0),
        updatedAt = System.currentTimeMillis()
    )

    suspend fun saveItem(item: StockItem, isOwner: Boolean) {
        val clamped = clampItem(item)
        db.itemDao().upsert(clamped.toEntity())
        syncEngine.pushItem(clamped, _settings.value, isOwner)
    }

    suspend fun saveItemsBatch(items: List<StockItem>, isOwner: Boolean) {
        if (items.isEmpty()) return
        val clamped = items.map { clampItem(it) }
        db.itemDao().upsertAll(clamped.map { it.toEntity() })
        val curSettings = _settings.value
        scope.launch {
            clamped.forEach { syncEngine.pushItem(it, curSettings, isOwner) }
        }
    }

    suspend fun adjustStock(itemId: String, mode: String, qty: Int, isOwner: Boolean) {
        val item = _items.value.find { it.id == itemId } ?: return
        if (item.isService) return
        val newPieces = when (mode) {
            "add" -> (item.pieces + qty).coerceAtLeast(0)
            "reduce" -> (item.pieces - qty).coerceAtLeast(0)
            "set" -> qty.coerceAtLeast(0)
            else -> item.pieces
        }
        saveItem(item.copy(pieces = newPieces), isOwner)
    }

    suspend fun deleteItem(itemId: String) {
        db.itemDao().deleteById(itemId)
        syncEngine.deleteDoc("items", itemId)
    }

    fun findDuplicateItem(category: String, brand: String, model: String, color: String): StockItem? {
        return _items.value.firstOrNull {
            it.category.equals(category.trim(), true) &&
                it.brand.equals(brand.trim(), true) &&
                it.model.equals(model.trim(), true) &&
                it.color.equals(color.trim(), true)
        }
    }

    // ---------------- PARTIES ----------------

    suspend fun ensureParty(name: String, phone: String, type: String): Party? {
        val cleanName = Formatters.clampName(name)
        val cleanPhone = Formatters.clampPhone(phone)
        if (cleanName.isBlank()) return null
        val existing = _parties.value.firstOrNull {
            (cleanPhone.isNotBlank() && it.phone == cleanPhone) ||
                it.name.equals(cleanName, ignoreCase = true)
        }
        if (existing != null) {
            val upgradedType = when {
                existing.type == "both" -> "both"
                existing.type != type -> "both"
                else -> existing.type
            }
            if (upgradedType != existing.type || (existing.phone.isBlank() && cleanPhone.isNotBlank())) {
                val updated = existing.copy(
                    type = upgradedType,
                    phone = existing.phone.ifBlank { cleanPhone },
                    updatedAt = System.currentTimeMillis()
                )
                saveParty(updated)
                return updated
            }
            return existing
        }
        val created = Party(
            id = UUID.randomUUID().toString(),
            name = cleanName,
            phone = cleanPhone,
            type = type
        )
        saveParty(created)
        return created
    }

    suspend fun saveParty(party: Party) {
        val clamped = party.copy(
            name = Formatters.clampName(party.name),
            phone = Formatters.clampPhone(party.phone),
            address = Formatters.clampAddress(party.address),
            notes = Formatters.clampNotes(party.notes),
            openingReceivable = Formatters.clampAmount(party.openingReceivable),
            openingPayable = Formatters.clampAmount(party.openingPayable),
            updatedAt = System.currentTimeMillis()
        )
        db.partyDao().upsert(clamped.toEntity())
        syncEngine.pushDoc("parties", clamped.id, BackupJsonCodec.partyToJson(clamped), _settings.value)
    }

    suspend fun deleteParty(partyId: String) {
        db.partyDao().deleteById(partyId)
        syncEngine.deleteDoc("parties", partyId)
    }

    // ---------------- PAYMENTS ----------------

    suspend fun savePayment(payment: PaymentRecord) {
        val clamped = payment.copy(
            partyName = Formatters.clampName(payment.partyName),
            amount = Formatters.clampAmount(payment.amount),
            notes = Formatters.clampNotes(payment.notes),
            date = payment.date.ifBlank { Formatters.todayIso() }
        )
        db.paymentDao().upsert(clamped.toEntity())
        syncEngine.pushDoc("payments", clamped.id, BackupJsonCodec.paymentToJson(clamped), _settings.value)
    }

    suspend fun deletePayment(paymentId: String) {
        db.paymentDao().deleteById(paymentId)
        syncEngine.deleteDoc("payments", paymentId)
    }

    // ---------------- INVOICES & v10 PART SOURCE SWITCH ----------------

    private fun nextDocNumber(docType: String, settings: ShopSettings, existingNumbers: Set<String>): Pair<String, Int?> {
        val prefix = when (docType) {
            "estimate" -> "EST-"
            "order" -> "SO-"
            "challan" -> "DC-"
            "return" -> "CN-"
            else -> settings.invoicePrefix.ifBlank { "AN-" }
        }
        var seq = if (docType == "invoice") settings.nextInvoiceSeq else {
            _invoices.value.count { it.docType == docType } + 1
        }
        var candidate = Formatters.formatSeqNumber(prefix, seq)
        if (candidate in existingNumbers) {
            var tries = 0
            while (candidate in existingNumbers && tries < 25) {
                seq++
                candidate = Formatters.formatSeqNumber(prefix, seq)
                tries++
            }
            if (candidate in existingNumbers) {
                val suffix = (10..99).random()
                candidate = "$candidate-$suffix"
            }
        }
        return candidate to (if (docType == "invoice") seq + 1 else null)
    }

    private fun nextPurchaseNumber(docType: String, settings: ShopSettings): Pair<String, Int> {
        val prefix = if (docType == "return") "DN-" else "PUR-"
        var seq = settings.nextPurchaseSeq.coerceAtLeast(1)
        val existing = _purchases.value.map { it.billNumber }.toSet()
        var candidate = Formatters.formatSeqNumber(prefix, seq)
        while (candidate in existing) {
            seq++
            candidate = Formatters.formatSeqNumber(prefix, seq)
        }
        return candidate to (seq + 1)
    }

    private fun nextRepairNumber(settings: ShopSettings): Pair<String, Int> {
        var seq = settings.nextRepairSeq.coerceAtLeast(1)
        val existing = _repairs.value.map { it.jobNumber }.toSet()
        var candidate = Formatters.formatSeqNumber("JOB-", seq)
        while (candidate in existing) {
            seq++
            candidate = Formatters.formatSeqNumber("JOB-", seq)
        }
        return candidate to (seq + 1)
    }

    suspend fun saveInvoice(rawInvoice: Invoice, isOwner: Boolean): Invoice {
        val existing = _invoices.value.find { it.id == rawInvoice.id }
        val existingNumbers = _invoices.value.filter { it.id != rawInvoice.id }.map { it.invoiceNumber }.toSet()

        // Reverse previous stock impact if editing an active posted invoice/return
        if (existing != null && existing.status == "active") {
            applyInvoiceStockDelta(existing, reverse = true, isOwner = isOwner)
        }

        val currentSettings = _settings.value
        val (invNo, newSeq) = if (rawInvoice.invoiceNumber.isBlank()) {
            nextDocNumber(rawInvoice.docType, currentSettings, existingNumbers)
        } else {
            rawInvoice.invoiceNumber to null
        }

        // Ensure customer party if name given
        val customerParty = if (rawInvoice.customerName.isNotBlank()) {
            ensureParty(rawInvoice.customerName, rawInvoice.customerPhone, "customer")
        } else null

        var invoice = rawInvoice.copy(
            invoiceNumber = invNo,
            date = rawInvoice.date.ifBlank { Formatters.todayIso() },
            customerId = rawInvoice.customerId ?: customerParty?.id,
            customerName = Formatters.clampName(rawInvoice.customerName),
            customerPhone = Formatters.clampPhone(rawInvoice.customerPhone),
            imei = Formatters.clampImei(rawInvoice.imei),
            notes = Formatters.clampNotes(rawInvoice.notes),
            subtotal = Formatters.clampAmount(rawInvoice.subtotal),
            discountAmount = Formatters.clampAmount(rawInvoice.discountAmount),
            grandTotal = Formatters.clampAmount(rawInvoice.grandTotal),
            amountPaid = Formatters.clampAmount(rawInvoice.amountPaid),
            balanceDue = Formatters.clampAmount(rawInvoice.balanceDue),
            updatedAt = System.currentTimeMillis()
        )

        // Handle v10 Part Source "Bought Outside" auto-PUR creation/update/cleanup
        val oldLinkedPurId = existing?.linkedPurchaseId
        if (invoice.status == "active" && invoice.partSourceMode == "outside" && invoice.outsidePartCost > 0.0) {
            val supplierName = invoice.outsideSupplierName.ifBlank { "Outside Spares" }
            val supplierParty = ensureParty(supplierName, "", "supplier")
            val oldPur = oldLinkedPurId?.let { id -> _purchases.value.find { it.id == id } }
            val purId = oldPur?.id ?: UUID.randomUUID().toString()
            val (purNo, nextPurSeq) = if (oldPur != null && oldPur.billNumber.isNotBlank()) {
                oldPur.billNumber to currentSettings.nextPurchaseSeq
            } else {
                nextPurchaseNumber("purchase", currentSettings)
            }
            val purLine = PurchaseLineItem(
                model = invoice.outsidePartName.ifBlank { "Spare Part" },
                category = invoice.outsidePartCategory.ifBlank { "Display" },
                qty = 1,
                buyPrice = Formatters.clampAmount(invoice.outsidePartCost),
                sellPrice = Formatters.clampAmount(invoice.outsidePartCharge)
            )
            val paidToShop = if (invoice.outsidePartPaymentMode.startsWith("Credit", true)) 0.0 else purLine.buyPrice
            val pur = Purchase(
                id = purId,
                billNumber = purNo,
                docType = "purchase",
                date = invoice.date,
                supplierId = supplierParty?.id,
                supplierName = Formatters.clampName(supplierName),
                items = listOf(purLine),
                totalAmount = purLine.buyPrice,
                paymentMode = invoice.outsidePartPaymentMode,
                amountPaid = paidToShop,
                balanceDue = (purLine.buyPrice - paidToShop).coerceAtLeast(0.0),
                status = "active",
                linkedSaleInvoiceId = invoice.id,
                notes = "Auto-logged outside part for ${invoice.invoiceNumber}"
            )
            // Save purchase record directly without double-adding outside part to stock (it's consumed on the bill)
            db.purchaseDao().upsert(pur.toEntity())
            syncEngine.pushDoc("purchases", pur.id, BackupJsonCodec.purchaseToJson(pur), _settings.value)
            invoice = invoice.copy(linkedPurchaseId = pur.id)
            if (nextPurSeq != currentSettings.nextPurchaseSeq) {
                updateSettings { it.copy(nextPurchaseSeq = nextPurSeq) }
            }
        } else if (oldLinkedPurId != null) {
            // Cleanly remove old linked PUR if switched away from outside or cancelled
            deletePurchase(oldLinkedPurId, reverseStock = false)
            invoice = invoice.copy(linkedPurchaseId = null)
        }

        // Apply new stock effect
        if (invoice.status == "active") {
            applyInvoiceStockDelta(invoice, reverse = false, isOwner = isOwner)
        }

        db.invoiceDao().upsert(invoice.toEntity())
        syncEngine.pushDoc("invoices", invoice.id, BackupJsonCodec.invoiceToJson(invoice), _settings.value)

        if (newSeq != null) {
            updateSettings { it.copy(nextInvoiceSeq = maxOf(it.nextInvoiceSeq, newSeq)) }
        }
        return invoice
    }

    private suspend fun applyInvoiceStockDelta(inv: Invoice, reverse: Boolean, isOwner: Boolean) {
        if (inv.docType != "invoice" && inv.docType != "return") return
        val sign = when {
            inv.docType == "invoice" && !reverse -> -1
            inv.docType == "invoice" && reverse -> +1
            inv.docType == "return" && !reverse -> +1
            inv.docType == "return" && reverse -> -1
            else -> 0
        }
        if (sign == 0) return

        val itemsToUpdate = mutableListOf<StockItem>()
        for (line in inv.items) {
            if (!line.isService && !line.itemId.isNullOrBlank()) {
                val stock = _items.value.find { it.id == line.itemId } ?: continue
                if (!stock.isService) {
                    val updatedPieces = (stock.pieces + sign * line.qty).coerceAtLeast(0)
                    itemsToUpdate.add(stock.copy(pieces = updatedPieces))
                }
            }
        }
        // Also deduct/restore v10 "From My Stock" linked part if not already in line items
        if (inv.partSourceMode == "stock" && !inv.linkedPartItemId.isNullOrBlank()) {
            val alreadyInLines = inv.items.any { it.itemId == inv.linkedPartItemId }
            if (!alreadyInLines) {
                val stock = _items.value.find { it.id == inv.linkedPartItemId }
                if (stock != null && !stock.isService) {
                    val updatedPieces = (stock.pieces + sign).coerceAtLeast(0)
                    itemsToUpdate.add(stock.copy(pieces = updatedPieces))
                }
            }
        }
        if (itemsToUpdate.isNotEmpty()) {
            saveItemsBatch(itemsToUpdate, isOwner)
        }
    }

    suspend fun cancelInvoice(invoiceId: String, isOwner: Boolean) {
        val inv = _invoices.value.find { it.id == invoiceId } ?: return
        if (inv.status == "cancelled") return
        applyInvoiceStockDelta(inv, reverse = true, isOwner = isOwner)
        if (inv.linkedPurchaseId != null) {
            deletePurchase(inv.linkedPurchaseId, reverseStock = false)
        }
        val cancelled = inv.copy(status = "cancelled", updatedAt = System.currentTimeMillis())
        db.invoiceDao().upsert(cancelled.toEntity())
        syncEngine.pushDoc("invoices", cancelled.id, BackupJsonCodec.invoiceToJson(cancelled), _settings.value)
    }

    suspend fun deleteInvoice(invoiceId: String, isOwner: Boolean) {
        val inv = _invoices.value.find { it.id == invoiceId } ?: return
        if (inv.status == "active") {
            applyInvoiceStockDelta(inv, reverse = true, isOwner = isOwner)
        }
        if (inv.linkedPurchaseId != null) {
            deletePurchase(inv.linkedPurchaseId, reverseStock = false)
        }
        db.invoiceDao().deleteById(invoiceId)
        syncEngine.deleteDoc("invoices", invoiceId)
    }

    // ---------------- PURCHASES ----------------

    suspend fun savePurchase(rawPurchase: Purchase, isOwner: Boolean): Purchase {
        val existing = _purchases.value.find { it.id == rawPurchase.id }
        if (existing != null && existing.status == "active" && existing.docType == "purchase" &&
            existing.linkedSaleInvoiceId == null && existing.linkedRepairId == null
        ) {
            applyPurchaseStockDelta(existing, reverse = true, isOwner = isOwner)
        }

        val currentSettings = _settings.value
        val (billNo, nextSeq) = if (rawPurchase.billNumber.isBlank()) {
            nextPurchaseNumber(rawPurchase.docType, currentSettings)
        } else {
            rawPurchase.billNumber to currentSettings.nextPurchaseSeq
        }

        val supplierParty = if (rawPurchase.supplierName.isNotBlank()) {
            ensureParty(rawPurchase.supplierName, rawPurchase.supplierPhone, "supplier")
        } else null

        val lines = rawPurchase.items.map { line ->
            if (rawPurchase.generateBarcodes && line.barcode.isBlank()) {
                line.copy(barcode = "NS" + (10000000..99999999).random())
            } else line
        }

        val total = Formatters.clampAmount(
            (lines.sumOf { it.total } - rawPurchase.discount + rawPurchase.transportExtra).coerceAtLeast(0.0)
        )
        val paid = Formatters.clampAmount(rawPurchase.amountPaid)
        val due = (total - paid).coerceAtLeast(0.0)

        val purchase = rawPurchase.copy(
            billNumber = billNo,
            date = rawPurchase.date.ifBlank { Formatters.todayIso() },
            supplierId = rawPurchase.supplierId ?: supplierParty?.id,
            supplierName = Formatters.clampName(rawPurchase.supplierName),
            supplierPhone = Formatters.clampPhone(rawPurchase.supplierPhone),
            items = lines,
            totalAmount = total,
            amountPaid = paid,
            balanceDue = due,
            notes = Formatters.clampNotes(rawPurchase.notes),
            updatedAt = System.currentTimeMillis()
        )

        if (purchase.status == "active" && purchase.docType == "purchase" &&
            purchase.linkedSaleInvoiceId == null && purchase.linkedRepairId == null
        ) {
            applyPurchaseStockDelta(purchase, reverse = false, isOwner = isOwner)
        } else if (purchase.status == "active" && purchase.docType == "return") {
            applyPurchaseStockDelta(purchase, reverse = true, isOwner = isOwner)
        }

        db.purchaseDao().upsert(purchase.toEntity())
        syncEngine.pushDoc("purchases", purchase.id, BackupJsonCodec.purchaseToJson(purchase), _settings.value)
        if (nextSeq > currentSettings.nextPurchaseSeq) {
            updateSettings { it.copy(nextPurchaseSeq = nextSeq) }
        }
        return purchase
    }

    private suspend fun applyPurchaseStockDelta(purchase: Purchase, reverse: Boolean, isOwner: Boolean) {
        val sign = if (reverse) -1 else +1
        val itemsToSave = mutableListOf<StockItem>()
        for (line in purchase.items) {
            if (line.model.isBlank()) continue
            val existing = line.itemId?.let { id -> _items.value.find { it.id == id } }
                ?: findDuplicateItem(line.category, line.brand, line.model, line.color)
            if (existing != null) {
                itemsToSave.add(
                    existing.copy(
                        pieces = (existing.pieces + sign * line.qty).coerceAtLeast(0),
                        costPrice = if (!reverse && line.buyPrice > 0) line.buyPrice else existing.costPrice,
                        sellingPrice = if (!reverse && line.sellPrice > 0) line.sellPrice else existing.sellingPrice,
                        barcode = existing.barcode.ifBlank { line.barcode }
                    )
                )
            } else if (!reverse) {
                itemsToSave.add(
                    StockItem(
                        category = line.category.ifBlank { "Accessories" },
                        brand = line.brand,
                        model = line.model,
                        color = line.color,
                        barcode = line.barcode,
                        costPrice = line.buyPrice,
                        sellingPrice = line.sellPrice,
                        pieces = line.qty,
                        lowStockAlert = _settings.value.lowStockDefault
                    )
                )
            }
        }
        if (itemsToSave.isNotEmpty()) {
            saveItemsBatch(itemsToSave, isOwner)
        }
    }

    suspend fun deletePurchase(purchaseId: String, reverseStock: Boolean = true, isOwner: Boolean = true) {
        val p = _purchases.value.find { it.id == purchaseId } ?: return
        if (reverseStock && p.status == "active" && p.docType == "purchase" &&
            p.linkedSaleInvoiceId == null && p.linkedRepairId == null
        ) {
            applyPurchaseStockDelta(p, reverse = true, isOwner = isOwner)
        }
        db.purchaseDao().deleteById(purchaseId)
        syncEngine.deleteDoc("purchases", purchaseId)
    }

    // ---------------- REPAIRS / SERVICE ----------------

    suspend fun saveRepair(
        rawRepair: RepairJob,
        createOrUpdateBill: Boolean,
        isOwner: Boolean,
        updatedByEmail: String
    ): RepairJob {
        val existing = _repairs.value.find { it.id == rawRepair.id }
        val currentSettings = _settings.value
        val (jobNo, nextSeq) = if (rawRepair.jobNumber.isBlank()) {
            nextRepairNumber(currentSettings)
        } else {
            rawRepair.jobNumber to currentSettings.nextRepairSeq
        }

        val customer = if (rawRepair.customerName.isNotBlank()) {
            ensureParty(rawRepair.customerName, rawRepair.customerPhone, "customer")
        } else null

        val history = rawRepair.statusHistory.toMutableList()
        if (history.isEmpty() || existing?.status != rawRepair.status) {
            history.add(
                RepairStatusEntry(
                    status = rawRepair.status,
                    note = rawRepair.notes,
                    timestamp = System.currentTimeMillis(),
                    updatedBy = updatedByEmail
                )
            )
        }

        val deliveredDate = if (rawRepair.status == "Delivered") {
            rawRepair.deliveredDate.ifBlank { Formatters.todayIso() }
        } else rawRepair.deliveredDate

        var repair = rawRepair.copy(
            jobNumber = jobNo,
            date = rawRepair.date.ifBlank { Formatters.todayIso() },
            deliveredDate = deliveredDate,
            customerId = rawRepair.customerId ?: customer?.id,
            customerName = Formatters.clampName(rawRepair.customerName),
            customerPhone = Formatters.clampPhone(rawRepair.customerPhone),
            phoneModel = Formatters.clampName(rawRepair.phoneModel),
            imei = Formatters.clampImei(rawRepair.imei),
            problem = Formatters.clampNotes(rawRepair.problem),
            notes = Formatters.clampNotes(rawRepair.notes),
            partBuyingCost = Formatters.clampAmount(rawRepair.partBuyingCost),
            partCharge = Formatters.clampAmount(rawRepair.partCharge),
            serviceCharge = Formatters.clampAmount(rawRepair.serviceCharge),
            advancePaid = Formatters.clampAmount(rawRepair.advancePaid),
            postBillPaid = Formatters.clampAmount(rawRepair.postBillPaid),
            statusHistory = history,
            updatedAt = System.currentTimeMillis()
        )

        // Handle stock part deduction (once) if partSource == "stock"
        if (repair.partSource == "stock" && !repair.outsidePartItemId.isNullOrBlank()) {
            if (!repair.partStockDeducted && (createOrUpdateBill || existing == null)) {
                val part = _items.value.find { it.id == repair.outsidePartItemId }
                if (part != null && !part.isService) {
                    saveItem(part.copy(pieces = (part.pieces - 1).coerceAtLeast(0)), isOwner)
                    repair = repair.copy(partStockDeducted = true)
                }
            }
        }

        // Handle outside part purchase auto-creation
        val oldPurId = existing?.outsidePartPurchaseId
        if (repair.partSource == "outside" && repair.partBuyingCost > 0.0) {
            val supplierName = repair.outsideSupplierName.ifBlank { "Outside Spares" }
            val supplierParty = ensureParty(supplierName, "", "supplier")
            val oldPur = oldPurId?.let { id -> _purchases.value.find { it.id == id } }
            val purId = oldPur?.id ?: UUID.randomUUID().toString()
            val (purNo, nextPurSeq) = if (oldPur != null && oldPur.billNumber.isNotBlank()) {
                oldPur.billNumber to currentSettings.nextPurchaseSeq
            } else {
                nextPurchaseNumber("purchase", currentSettings)
            }
            val purLine = PurchaseLineItem(
                model = repair.outsidePartName.ifBlank { repair.phoneModel.ifBlank { "Spare Part" } },
                category = repair.outsidePartCategory.ifBlank { "Display" },
                qty = 1,
                buyPrice = repair.partBuyingCost,
                sellPrice = repair.partCharge
            )
            val paidToShop = if (repair.outsidePartPaymentMode.startsWith("Credit", true)) 0.0 else repair.partBuyingCost
            val pur = Purchase(
                id = purId,
                billNumber = purNo,
                docType = "purchase",
                date = repair.date,
                supplierId = supplierParty?.id,
                supplierName = Formatters.clampName(supplierName),
                items = listOf(purLine),
                totalAmount = repair.partBuyingCost,
                paymentMode = repair.outsidePartPaymentMode,
                amountPaid = paidToShop,
                balanceDue = (repair.partBuyingCost - paidToShop).coerceAtLeast(0.0),
                status = "active",
                linkedRepairId = repair.id,
                notes = "Auto-logged outside part for ${repair.jobNumber}"
            )
            db.purchaseDao().upsert(pur.toEntity())
            syncEngine.pushDoc("purchases", pur.id, BackupJsonCodec.purchaseToJson(pur), _settings.value)
            repair = repair.copy(outsidePartPurchaseId = pur.id)
            if (nextPurSeq > currentSettings.nextPurchaseSeq) {
                updateSettings { it.copy(nextPurchaseSeq = nextPurSeq) }
            }
        } else if (oldPurId != null) {
            deletePurchase(oldPurId, reverseStock = false)
            repair = repair.copy(outsidePartPurchaseId = null)
        }

        // Create / update linked Service Bill (Sales Invoice)
        if (createOrUpdateBill && repair.totalCharged > 0.0) {
            val existingInv = repair.billedInvoiceId?.let { id -> _invoices.value.find { it.id == id } }
            val lines = buildList {
                if (repair.partCharge > 0.0) {
                    val partTitle = when (repair.partSource) {
                        "stock" -> _items.value.find { it.id == repair.outsidePartItemId }?.displayName ?: "Spare Part"
                        "outside" -> repair.outsidePartName.ifBlank { "Spare Part" }
                        else -> "Spare Part"
                    }
                    add(
                        InvoiceLineItem(
                            itemId = if (repair.partSource == "stock") repair.outsidePartItemId else null,
                            name = partTitle,
                            qty = 1,
                            rate = repair.partCharge,
                            costPrice = repair.partBuyingCost,
                            isService = true, // Stock already deducted by repair.partStockDeducted
                            partSource = repair.partSource
                        )
                    )
                }
                if (repair.serviceCharge > 0.0) {
                    add(
                        InvoiceLineItem(
                            name = "Service / Labour (${repair.phoneModel} - ${repair.problem})".take(100),
                            qty = 1,
                            rate = repair.serviceCharge,
                            costPrice = 0.0,
                            isService = true,
                            partSource = "labour"
                        )
                    )
                }
            }
            val total = repair.totalCharged
            val paid = repair.totalPaid.coerceAtMost(total)
            val inv = Invoice(
                id = existingInv?.id ?: UUID.randomUUID().toString(),
                invoiceNumber = existingInv?.invoiceNumber ?: "",
                docType = "invoice",
                date = repair.date,
                customerId = repair.customerId,
                customerName = repair.customerName,
                customerPhone = repair.customerPhone,
                billType = "repair",
                imei = repair.imei,
                items = lines,
                subtotal = total,
                grandTotal = total,
                paymentMode = repair.billingPaymentMode,
                amountPaid = paid,
                balanceDue = (total - paid).coerceAtLeast(0.0),
                status = "active",
                notes = "${repair.jobNumber}: ${repair.phoneModel} - ${repair.problem}",
                linkedRepairId = repair.id
            )
            val savedInv = saveInvoice(inv, isOwner)
            repair = repair.copy(billedInvoiceId = savedInv.id)
        }

        db.repairDao().upsert(repair.toEntity())
        syncEngine.pushDoc("repairs", repair.id, BackupJsonCodec.repairToJson(repair), _settings.value)
        if (nextSeq > currentSettings.nextRepairSeq) {
            updateSettings { it.copy(nextRepairSeq = nextSeq) }
        }
        return repair
    }

    suspend fun collectRepairPayment(repairId: String, amount: Double, paymentMode: String, notes: String, isOwner: Boolean) {
        val repair = _repairs.value.find { it.id == repairId } ?: return
        val newPostPaid = repair.postBillPaid + Formatters.clampAmount(amount)
        val updated = repair.copy(
            postBillPaid = newPostPaid,
            billingPaymentMode = paymentMode,
            notes = if (notes.isNotBlank()) "${repair.notes}\nPayment: $notes".trim() else repair.notes,
            updatedAt = System.currentTimeMillis()
        )
        saveRepair(updated, createOrUpdateBill = updated.billedInvoiceId != null, isOwner = isOwner, updatedByEmail = "")
    }

    suspend fun deleteRepair(repairId: String, isOwner: Boolean) {
        val r = _repairs.value.find { it.id == repairId } ?: return
        if (r.partStockDeducted && !r.outsidePartItemId.isNullOrBlank()) {
            val part = _items.value.find { it.id == r.outsidePartItemId }
            if (part != null && !part.isService) {
                saveItem(part.copy(pieces = part.pieces + 1), isOwner)
            }
        }
        if (!r.billedInvoiceId.isNullOrBlank()) {
            deleteInvoice(r.billedInvoiceId, isOwner)
        }
        if (!r.outsidePartPurchaseId.isNullOrBlank()) {
            deletePurchase(r.outsidePartPurchaseId, reverseStock = false)
        }
        db.repairDao().deleteById(repairId)
        syncEngine.deleteDoc("repairs", repairId)
    }

    // ---------------- EXPENSES & CASH / BANK ----------------

    suspend fun saveExpense(expense: ExpenseRecord) {
        val clamped = expense.copy(
            category = Formatters.clampName(expense.category.ifBlank { "Expense" }),
            reason = Formatters.clampName(expense.reason),
            amount = Formatters.clampAmount(expense.amount),
            date = expense.date.ifBlank { Formatters.todayIso() },
            notes = Formatters.clampNotes(expense.notes),
            updatedAt = System.currentTimeMillis()
        )
        db.expenseDao().upsert(clamped.toEntity())
        syncEngine.pushDoc("expenses", clamped.id, BackupJsonCodec.expenseToJson(clamped), _settings.value)
    }

    suspend fun deleteExpense(expenseId: String) {
        db.expenseDao().deleteById(expenseId)
        syncEngine.deleteDoc("expenses", expenseId)
    }

    suspend fun saveBankAccount(account: BankAccount) {
        val clamped = account.copy(
            name = Formatters.clampName(account.name),
            last4 = account.last4.filter { it.isDigit() }.take(4),
            openingBalance = Formatters.clampAmount(account.openingBalance),
            upiHandle = Formatters.clampName(account.upiHandle)
        )
        db.bankAccountDao().upsert(clamped.toEntity())
        syncEngine.pushDoc("bankAccounts", clamped.id, BackupJsonCodec.bankAccountToJson(clamped), _settings.value)
    }

    suspend fun deleteBankAccount(accountId: String) {
        db.bankAccountDao().deleteById(accountId)
        syncEngine.deleteDoc("bankAccounts", accountId)
    }

    suspend fun saveCashBankTxn(txn: CashBankTxn) {
        val clamped = txn.copy(
            amount = Formatters.clampAmount(txn.amount),
            date = txn.date.ifBlank { Formatters.todayIso() },
            notes = Formatters.clampNotes(txn.notes)
        )
        db.cashBankTxnDao().upsert(clamped.toEntity())
        syncEngine.pushDoc("cashBankTxns", clamped.id, BackupJsonCodec.cashBankTxnToJson(clamped), _settings.value)
    }

    suspend fun updateChequeStatus(txnId: String, newStatus: String) {
        val txn = _cashBankTxns.value.find { it.id == txnId } ?: return
        saveCashBankTxn(txn.copy(chequeStatus = newStatus))
    }

    suspend fun deleteCashBankTxn(txnId: String) {
        db.cashBankTxnDao().deleteById(txnId)
        syncEngine.deleteDoc("cashBankTxns", txnId)
    }

    // ---------------- SHOP DAY ----------------

    suspend fun saveShopDay(record: ShopDayRecord) {
        db.shopDayDao().upsert(ShopDayEntity(record.date, BackupJsonCodec.shopDayToJson(record).toString()))
    }

    // ---------------- LEDGER & BALANCE CALCULATIONS ----------------

    private fun computeAnalyticsSnapshot(
        settings: ShopSettings,
        invoices: List<Invoice>,
        purchases: List<Purchase>,
        parties: List<Party>,
        payments: List<PaymentRecord>,
        expenses: List<ExpenseRecord>,
        repairs: List<RepairJob>,
        bankAccounts: List<BankAccount>,
        cashBankTxns: List<CashBankTxn>
    ): ShopAnalyticsSnapshot {
        val today = Formatters.todayIso()
        val cashAll = computeCashInHandInternal(
            openingCashBalance = settings.openingCashBalance,
            invoices = invoices,
            purchases = purchases,
            payments = payments,
            expenses = expenses,
            repairs = repairs,
            cashBankTxns = cashBankTxns,
            beforeDateExclusive = null
        )
        val cashBeforeToday = computeCashInHandInternal(
            openingCashBalance = settings.openingCashBalance,
            invoices = invoices,
            purchases = purchases,
            payments = payments,
            expenses = expenses,
            repairs = repairs,
            cashBankTxns = cashBankTxns,
            beforeDateExclusive = today
        )
        val bankBal = computeBankBalanceInternal(
            bankAccounts = bankAccounts,
            invoices = invoices,
            purchases = purchases,
            payments = payments,
            expenses = expenses,
            cashBankTxns = cashBankTxns
        )

        // O(N) indexed Party ledger calculation
        val nParties = parties.size
        val partyIndexById = HashMap<String, Int>(nParties * 2)
        val partyIndicesByLowerName = HashMap<String, MutableList<Int>>(nParties * 2)
        for (i in 0 until nParties) {
            val p = parties[i]
            partyIndexById[p.id] = i
            val normName = p.name.trim().lowercase()
            if (normName.isNotEmpty()) {
                partyIndicesByLowerName.getOrPut(normName) { mutableListOf() }.add(i)
            }
        }

        val invDueArr = DoubleArray(nParties)
        val oldestDateArr = arrayOfNulls<String>(nParties)
        val unbilledRepDueArr = DoubleArray(nParties)
        val purDueArr = DoubleArray(nParties)
        val payInArr = DoubleArray(nParties)
        val payOutArr = DoubleArray(nParties)

        fun forMatchedParties(id: String?, name: String, block: (Int) -> Unit) {
            val directIdx = if (!id.isNullOrBlank()) partyIndexById[id] else null
            if (directIdx != null) block(directIdx)
            val byName = partyIndicesByLowerName[name.trim().lowercase()]
            if (byName != null) {
                for (idx in byName) {
                    if (idx != directIdx) block(idx)
                }
            }
        }

        // Sold stats by itemId in the same invoice pass
        val soldQtyMap = HashMap<String, Int>(128)
        val lastInvNumMap = HashMap<String, String>(128)
        val lastInvDateMap = HashMap<String, String>(128)

        for (inv in invoices) {
            if (inv.status != "active") continue
            val deltaDue = when (inv.docType) {
                "invoice" -> inv.balanceDue
                "return" -> -inv.balanceDue
                else -> 0.0
            }
            forMatchedParties(inv.customerId, inv.customerName) { idx ->
                invDueArr[idx] += deltaDue
                if (inv.docType == "invoice" && inv.balanceDue > 0.0) {
                    val cur = oldestDateArr[idx]
                    if (cur == null || inv.date < cur) oldestDateArr[idx] = inv.date
                }
            }
            if (inv.docType == "invoice") {
                for (line in inv.items) {
                    val itemId = line.itemId ?: continue
                    soldQtyMap[itemId] = (soldQtyMap[itemId] ?: 0) + line.qty
                    val prevDate = lastInvDateMap[itemId]
                    if (prevDate == null || inv.date > prevDate) {
                        lastInvDateMap[itemId] = inv.date
                        lastInvNumMap[itemId] = inv.invoiceNumber
                    }
                }
            }
        }

        for (rep in repairs) {
            if (rep.billedInvoiceId == null) {
                forMatchedParties(rep.customerId, rep.customerName) { idx ->
                    unbilledRepDueArr[idx] += rep.balanceDue
                }
            }
        }

        for (p in purchases) {
            if (p.status != "active") continue
            val deltaDue = when (p.docType) {
                "purchase" -> p.balanceDue
                "return" -> -p.balanceDue
                else -> 0.0
            }
            forMatchedParties(p.supplierId, p.supplierName) { idx ->
                purDueArr[idx] += deltaDue
            }
        }

        for (pay in payments) {
            forMatchedParties(pay.partyId, pay.partyName) { idx ->
                if (pay.direction == "in") payInArr[idx] += pay.amount else payOutArr[idx] += pay.amount
            }
        }

        val summaries = ArrayList<PartyBalanceSummary>(nParties)
        val summaryById = HashMap<String, PartyBalanceSummary>(nParties * 2)
        var totalRecSum = 0.0
        var totalPaySum = 0.0
        for (i in 0 until nParties) {
            val party = parties[i]
            val totalRec = party.openingReceivable + invDueArr[i] + unbilledRepDueArr[i] - payInArr[i]
            val totalPay = party.openingPayable + purDueArr[i] - payOutArr[i]
            val recClamped = totalRec.coerceAtLeast(0.0)
            val payClamped = totalPay.coerceAtLeast(0.0)
            val summary = PartyBalanceSummary(
                party = party,
                receivableBalance = recClamped,
                payableBalance = payClamped,
                netBalance = totalRec - totalPay,
                oldestDueDate = oldestDateArr[i] ?: today
            )
            summaries.add(summary)
            summaryById[party.id] = summary
            totalRecSum += recClamped
            totalPaySum += payClamped
        }

        val soldStats = HashMap<String, ItemSoldSummary>(soldQtyMap.size * 2)
        for ((itemId, qty) in soldQtyMap) {
            soldStats[itemId] = ItemSoldSummary(
                soldQty = qty,
                lastInvoiceNumber = lastInvNumMap[itemId].orEmpty(),
                lastSaleDate = lastInvDateMap[itemId].orEmpty()
            )
        }

        return ShopAnalyticsSnapshot(
            cashInHand = cashAll,
            openingCashToday = cashBeforeToday,
            openingCashDate = today,
            bankBalance = bankBal,
            partySummaries = summaries,
            partySummaryById = summaryById,
            totalReceivables = totalRecSum,
            totalPayables = totalPaySum,
            soldStatsByItemId = soldStats,
            soldItemIds = soldStats.keys
        )
    }

    private fun computeCashInHandInternal(
        openingCashBalance: Double,
        invoices: List<Invoice>,
        purchases: List<Purchase>,
        payments: List<PaymentRecord>,
        expenses: List<ExpenseRecord>,
        repairs: List<RepairJob>,
        cashBankTxns: List<CashBankTxn>,
        beforeDateExclusive: String?
    ): Double {
        var cash = openingCashBalance
        for (inv in invoices) {
            if (inv.status == "active" && (beforeDateExclusive == null || inv.date < beforeDateExclusive)) {
                if (inv.paymentMode.equals("Cash", true)) {
                    if (inv.docType == "invoice") cash += inv.amountPaid
                    else if (inv.docType == "return") cash -= inv.amountPaid
                }
            }
        }
        for (p in purchases) {
            if (p.status == "active" && (beforeDateExclusive == null || p.date < beforeDateExclusive)) {
                if (p.paymentMode.equals("Cash", true)) {
                    if (p.docType == "purchase") cash -= p.amountPaid
                    else if (p.docType == "return") cash += p.amountPaid
                }
            }
        }
        for (pay in payments) {
            if (beforeDateExclusive == null || pay.date < beforeDateExclusive) {
                if (pay.paymentMode.equals("Cash", true)) {
                    if (pay.direction == "in") cash += pay.amount else cash -= pay.amount
                }
            }
        }
        for (exp in expenses) {
            if (beforeDateExclusive == null || exp.date < beforeDateExclusive) {
                if (exp.paymentMode.equals("Cash", true)) {
                    cash -= exp.amount
                }
            }
        }
        for (rep in repairs) {
            if (rep.billedInvoiceId == null && (beforeDateExclusive == null || rep.date < beforeDateExclusive)) {
                if (rep.billingPaymentMode.equals("Cash", true)) {
                    cash += rep.advancePaid
                }
            }
        }
        for (txn in cashBankTxns) {
            if (beforeDateExclusive == null || txn.date < beforeDateExclusive) {
                when (txn.type) {
                    "cash_in", "bank_withdraw" -> cash += txn.amount
                    "cash_out", "bank_deposit" -> cash -= txn.amount
                }
            }
        }
        return cash
    }

    private fun computeBankBalanceInternal(
        bankAccounts: List<BankAccount>,
        invoices: List<Invoice>,
        purchases: List<Purchase>,
        payments: List<PaymentRecord>,
        expenses: List<ExpenseRecord>,
        cashBankTxns: List<CashBankTxn>
    ): Double {
        var bank = bankAccounts.sumOf { it.openingBalance }
        for (inv in invoices) {
            if (inv.status == "active" && !inv.paymentMode.equals("Cash", true) && !inv.paymentMode.startsWith("Credit", true)) {
                if (inv.docType == "invoice") bank += inv.amountPaid
                else if (inv.docType == "return") bank -= inv.amountPaid
            }
        }
        for (p in purchases) {
            if (p.status == "active" && !p.paymentMode.equals("Cash", true) && !p.paymentMode.startsWith("Credit", true)) {
                if (p.docType == "purchase") bank -= p.amountPaid
                else if (p.docType == "return") bank += p.amountPaid
            }
        }
        for (pay in payments) {
            if (!pay.paymentMode.equals("Cash", true)) {
                if (pay.direction == "in") bank += pay.amount else bank -= pay.amount
            }
        }
        for (exp in expenses) {
            if (!exp.paymentMode.equals("Cash", true)) {
                bank -= exp.amount
            }
        }
        for (txn in cashBankTxns) {
            when (txn.type) {
                "bank_deposit" -> bank += txn.amount
                "bank_withdraw" -> bank -= txn.amount
                "cheque" -> if (txn.chequeStatus.equals("Cleared", true)) bank += txn.amount
            }
        }
        return bank
    }

    fun computeCashInHand(beforeDateExclusive: String? = null): Double {
        return computeCashInHandInternal(
            openingCashBalance = _settings.value.openingCashBalance,
            invoices = _invoices.value,
            purchases = _purchases.value,
            payments = _payments.value,
            expenses = _expenses.value,
            repairs = _repairs.value,
            cashBankTxns = _cashBankTxns.value,
            beforeDateExclusive = beforeDateExclusive
        )
    }

    fun computeBankBalance(): Double {
        return computeBankBalanceInternal(
            bankAccounts = _bankAccounts.value,
            invoices = _invoices.value,
            purchases = _purchases.value,
            payments = _payments.value,
            expenses = _expenses.value,
            cashBankTxns = _cashBankTxns.value
        )
    }

    data class PartyBalanceSummary(
        val party: Party,
        val receivableBalance: Double, // positive = customer owes shop
        val payableBalance: Double,    // positive = shop owes supplier
        val netBalance: Double,        // +ve to collect, -ve to pay
        val oldestDueDate: String
    )

    fun computePartySummary(party: Party): PartyBalanceSummary {
        analytics.value.partySummaryById[party.id]?.let { return it }
        val invDue = _invoices.value.filter {
            it.status == "active" &&
                (it.customerId == party.id || it.customerName.equals(party.name, true))
        }.sumOf {
            when (it.docType) {
                "invoice" -> it.balanceDue
                "return" -> -it.balanceDue
                else -> 0.0
            }
        }
        val unbilledRepairsDue = _repairs.value.filter {
            it.billedInvoiceId == null &&
                (it.customerId == party.id || it.customerName.equals(party.name, true))
        }.sumOf { it.balanceDue }

        val purDue = _purchases.value.filter {
            it.status == "active" &&
                (it.supplierId == party.id || it.supplierName.equals(party.name, true))
        }.sumOf {
            when (it.docType) {
                "purchase" -> it.balanceDue
                "return" -> -it.balanceDue
                else -> 0.0
            }
        }

        val payIn = _payments.value.filter {
            it.partyId == party.id || it.partyName.equals(party.name, true)
        }.sumOf { if (it.direction == "in") it.amount else 0.0 }

        val payOut = _payments.value.filter {
            it.partyId == party.id || it.partyName.equals(party.name, true)
        }.sumOf { if (it.direction == "out") it.amount else 0.0 }

        val totalRec = party.openingReceivable + invDue + unbilledRepairsDue - payIn
        val totalPay = party.openingPayable + purDue - payOut
        val net = totalRec - totalPay

        val oldestDate = _invoices.value
            .filter { it.status == "active" && it.docType == "invoice" && it.balanceDue > 0 && (it.customerId == party.id || it.customerName.equals(party.name, true)) }
            .minOfOrNull { it.date } ?: Formatters.todayIso()

        return PartyBalanceSummary(
            party = party,
            receivableBalance = totalRec.coerceAtLeast(0.0),
            payableBalance = totalPay.coerceAtLeast(0.0),
            netBalance = net,
            oldestDueDate = oldestDate
        )
    }

    // ---------------- BACKUP EXPORT / IMPORT / AUTO-BACKUP ----------------

    suspend fun exportBackupJson(): String = withContext(Dispatchers.IO) {
        val daysMap = _shopDays.value.associateBy { it.date }
        BackupJsonCodec.exportToJsonString(
            FullShopBackup(
                settings = _settings.value,
                items = _items.value,
                invoices = _invoices.value,
                purchases = _purchases.value,
                parties = _parties.value,
                payments = _payments.value,
                expenses = _expenses.value,
                repairs = _repairs.value,
                bankAccounts = _bankAccounts.value,
                cashBankTxns = _cashBankTxns.value,
                shopDays = daysMap,
                lastSavedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun importBackup(backup: FullShopBackup): Pair<Int, Int> {
        val mergedUsers = (_settings.value.users + backup.settings.users)
            .distinctBy { it.email.lowercase() }
        val mergedSettings = backup.settings.copy(
            users = mergedUsers,
            nextInvoiceSeq = maxOf(_settings.value.nextInvoiceSeq, backup.settings.nextInvoiceSeq),
            nextPurchaseSeq = maxOf(_settings.value.nextPurchaseSeq, backup.settings.nextPurchaseSeq),
            nextRepairSeq = maxOf(_settings.value.nextRepairSeq, backup.settings.nextRepairSeq)
        )
        saveSettingsLocalOnly(mergedSettings)

        db.itemDao().upsertAll(backup.items.map { it.toEntity() })
        db.invoiceDao().upsertAll(backup.invoices.map { it.toEntity() })
        db.purchaseDao().upsertAll(backup.purchases.map { it.toEntity() })
        db.partyDao().upsertAll(backup.parties.map { it.toEntity() })
        db.paymentDao().upsertAll(backup.payments.map { it.toEntity() })
        db.expenseDao().upsertAll(backup.expenses.map { it.toEntity() })
        db.repairDao().upsertAll(backup.repairs.map { it.toEntity() })
        db.bankAccountDao().upsertAll(backup.bankAccounts.map { it.toEntity() })
        db.cashBankTxnDao().upsertAll(backup.cashBankTxns.map { it.toEntity() })
        db.shopDayDao().upsertAll(backup.shopDays.values.map {
            ShopDayEntity(it.date, BackupJsonCodec.shopDayToJson(it).toString())
        })

        // Push imported data to cloud in background
        scope.launch {
            syncEngine.pushSettings(mergedSettings)
            backup.items.forEach { syncEngine.pushItem(it, mergedSettings, isOwnerOrCoOwnerSession) }
            backup.invoices.forEach { syncEngine.pushDoc("invoices", it.id, BackupJsonCodec.invoiceToJson(it), mergedSettings) }
            backup.purchases.forEach { syncEngine.pushDoc("purchases", it.id, BackupJsonCodec.purchaseToJson(it), mergedSettings) }
            backup.parties.forEach { syncEngine.pushDoc("parties", it.id, BackupJsonCodec.partyToJson(it), mergedSettings) }
            backup.payments.forEach { syncEngine.pushDoc("payments", it.id, BackupJsonCodec.paymentToJson(it), mergedSettings) }
            backup.expenses.forEach { syncEngine.pushDoc("expenses", it.id, BackupJsonCodec.expenseToJson(it), mergedSettings) }
            backup.repairs.forEach { syncEngine.pushDoc("repairs", it.id, BackupJsonCodec.repairToJson(it), mergedSettings) }
            backup.bankAccounts.forEach { syncEngine.pushDoc("bankAccounts", it.id, BackupJsonCodec.bankAccountToJson(it), mergedSettings) }
            backup.cashBankTxns.forEach { syncEngine.pushDoc("cashBankTxns", it.id, BackupJsonCodec.cashBankTxnToJson(it), mergedSettings) }
        }
        return backup.items.size to backup.invoices.size
    }

    suspend fun saveAutoBackupSnapshot(): AutoBackupEntry? = withContext(Dispatchers.IO) {
        if (_items.value.isEmpty() && _invoices.value.isEmpty() && _repairs.value.isEmpty() && _parties.value.isEmpty()) {
            return@withContext null
        }
        val dir = File(context.filesDir, "backups").apply { mkdirs() }
        val fileName = "Noor-AutoBackup-${Formatters.todayIso()}-${System.currentTimeMillis()}.json"
        val file = File(dir, fileName)
        file.writeText(exportBackupJson())

        val entry = AutoBackupEntity(
            id = UUID.randomUUID().toString(),
            createdAt = System.currentTimeMillis(),
            label = "Noor-Billing-Backup-${Formatters.todayIso()}.json",
            itemsCount = _items.value.size,
            invoicesCount = _invoices.value.size,
            filePath = file.absolutePath
        )
        db.autoBackupDao().insert(entry)

        // Keep last 6 snapshots
        val all = db.autoBackupDao().getAll()
        if (all.size > 6) {
            all.drop(6).forEach { old ->
                runCatching { File(old.filePath).delete() }
                db.autoBackupDao().deleteById(old.id)
            }
        }
        updateSettings { it.copy(lastBackupAt = System.currentTimeMillis()) }
        AutoBackupEntry(entry.id, entry.createdAt, entry.label, entry.itemsCount, entry.invoicesCount, entry.filePath)
    }

    // ---------------- SAMPLE DATA & CLEAR ALL ----------------

    suspend fun loadSampleData(isOwner: Boolean) {
        val today = Formatters.todayIso()
        val item1 = StockItem(
            category = "Silicon Cover", brand = "Vivo", model = "Y20", color = "Black",
            alsoFits = "Y20i, Y20s, Y12s", barcode = "890123450001", costPrice = 45.0, sellingPrice = 150.0, pieces = 18, lowStockAlert = 3
        )
        val item2 = StockItem(
            category = "Tempered Glass", brand = "Redmi", model = "Note 12", color = "11D Clear",
            alsoFits = "Poco X5", barcode = "890123450002", costPrice = 20.0, sellingPrice = 100.0, pieces = 2, lowStockAlert = 5
        )
        val item3 = StockItem(
            category = "Displays", brand = "Samsung", model = "A14 5G", color = "OG Black",
            barcode = "890123450003", costPrice = 1450.0, sellingPrice = 2400.0, pieces = 3, lowStockAlert = 2
        )
        val item4 = StockItem(
            category = "Chargers", brand = "Boat", model = "Type-C 20W", color = "White",
            barcode = "890123450004", costPrice = 220.0, sellingPrice = 450.0, pieces = 8, lowStockAlert = 3
        )
        saveItemsBatch(listOf(item1, item2, item3, item4), isOwner)

        val bank = BankAccount(name = "Indian Bank Annur", last4 = "4821", openingBalance = 25000.0, upiHandle = "annoor@okaxis")
        saveBankAccount(bank)

        val cust = Party(name = "Karthik Raja", phone = "9876543210", address = "Annur", type = "customer")
        saveParty(cust)

        val inv = Invoice(
            invoiceNumber = "AN-0001", docType = "invoice", date = today,
            customerId = cust.id, customerName = cust.name, customerPhone = cust.phone,
            billType = "product",
            items = listOf(
                InvoiceLineItem(itemId = item1.id, name = "Vivo Y20 (Black)", category = item1.category, brand = item1.brand, model = item1.model, color = item1.color, qty = 1, rate = 150.0, costPrice = 45.0),
                InvoiceLineItem(itemId = item4.id, name = "Boat Type-C 20W", category = item4.category, brand = item4.brand, model = item4.model, color = item4.color, qty = 1, rate = 450.0, costPrice = 220.0)
            ),
            subtotal = 600.0, grandTotal = 600.0, paymentMode = "UPI", amountPaid = 600.0, balanceDue = 0.0
        )
        db.invoiceDao().upsert(inv.toEntity())
        syncEngine.pushDoc("invoices", inv.id, BackupJsonCodec.invoiceToJson(inv), _settings.value)

        val rep = RepairJob(
            jobNumber = "JOB-0001", date = today, deliveryDate = today,
            customerId = cust.id, customerName = cust.name, customerPhone = cust.phone,
            phoneModel = "Vivo Y20", problem = "Display replacement", status = "In-Progress",
            partSource = "stock", outsidePartItemId = item3.id, partBuyingCost = 1450.0,
            partCharge = 2200.0, serviceCharge = 400.0, advancePaid = 500.0,
            notes = "Tested touch & brightness"
        )
        saveRepair(rep, createOrUpdateBill = false, isOwner = isOwner, updatedByEmail = PRIMARY_OWNER_EMAIL)
        updateSettings { it.copy(nextInvoiceSeq = maxOf(it.nextInvoiceSeq, 2), nextRepairSeq = maxOf(it.nextRepairSeq, 2)) }
    }

    suspend fun clearAllData() {
        db.itemDao().clearAll()
        db.invoiceDao().clearAll()
        db.purchaseDao().clearAll()
        db.partyDao().clearAll()
        db.paymentDao().clearAll()
        db.expenseDao().clearAll()
        db.repairDao().clearAll()
        db.bankAccountDao().clearAll()
        db.cashBankTxnDao().clearAll()
        db.shopDayDao().clearAll()
        updateSettings {
            it.copy(nextInvoiceSeq = 1, nextPurchaseSeq = 1, nextRepairSeq = 1)
        }
    }

    // ---------------- MULTI-COMPANY MANAGEMENT ----------------

    suspend fun createCompany(name: String): CompanyMeta {
        val clean = Formatters.clampName(name).ifBlank { "New Shop" }
        val id = "shop_${UUID.randomUUID().toString().take(8)}"
        val meta = CompanyMeta(id = id, name = clean, dbName = "noor_$id.db")
        val updated = _companies.value + meta
        secretStore.saveCompanies(updated)
        _companies.value = updated
        switchCompany(meta.id)
        updateSettings { it.copy(shopName = clean) }
        return meta
    }

    suspend fun switchCompany(companyId: String) {
        val target = _companies.value.firstOrNull { it.id == companyId } ?: return
        secretStore.setActiveCompanyId(target.id)
        _activeCompany.value = target
        db = DatabaseProvider.get(context, target.dbName)
        attachDatabaseObservers()
    }

    fun renameCompany(companyId: String, newName: String) {
        val clean = Formatters.clampName(newName).ifBlank { return }
        val updated = _companies.value.map { if (it.id == companyId) it.copy(name = clean) else it }
        secretStore.saveCompanies(updated)
        _companies.value = updated
        if (_activeCompany.value.id == companyId) {
            _activeCompany.value = _activeCompany.value.copy(name = clean)
            scope.launch { updateSettings { it.copy(shopName = clean) } }
        }
    }

    suspend fun deleteCompany(companyId: String) {
        if (_companies.value.size <= 1) return
        val target = _companies.value.firstOrNull { it.id == companyId } ?: return
        val remaining = _companies.value.filterNot { it.id == companyId }
        secretStore.saveCompanies(remaining)
        _companies.value = remaining
        if (_activeCompany.value.id == companyId) {
            switchCompany(remaining.first().id)
        }
        DatabaseProvider.closeAndDelete(context, target.dbName)
    }

    // ---------------- ENTITY CONVERTERS ----------------

    private fun StockItem.toEntity() = ItemEntity(
        id, category, brand, model, barcode, imei, pieces, updatedAt,
        BackupJsonCodec.itemToJson(this).toString()
    )

    private fun Invoice.toEntity() = InvoiceEntity(
        id, invoiceNumber, docType, date, customerId, status, createdAt,
        BackupJsonCodec.invoiceToJson(this).toString()
    )

    private fun Purchase.toEntity() = PurchaseEntity(
        id, billNumber, docType, date, supplierId, status, createdAt,
        BackupJsonCodec.purchaseToJson(this).toString()
    )

    private fun Party.toEntity() = PartyEntity(
        id, name, phone, type, updatedAt,
        BackupJsonCodec.partyToJson(this).toString()
    )

    private fun PaymentRecord.toEntity() = PaymentEntity(
        id, partyId, direction, date, createdAt,
        BackupJsonCodec.paymentToJson(this).toString()
    )

    private fun ExpenseRecord.toEntity() = ExpenseEntity(
        id, category, date, createdAt,
        BackupJsonCodec.expenseToJson(this).toString()
    )

    private fun RepairJob.toEntity() = RepairEntity(
        id, jobNumber, status, date, customerId, createdAt,
        BackupJsonCodec.repairToJson(this).toString()
    )

    private fun BankAccount.toEntity() = BankAccountEntity(
        id, name, createdAt,
        BackupJsonCodec.bankAccountToJson(this).toString()
    )

    private fun CashBankTxn.toEntity() = CashBankTxnEntity(
        id, type, date, accountId, createdAt,
        BackupJsonCodec.cashBankTxnToJson(this).toString()
    )
}
