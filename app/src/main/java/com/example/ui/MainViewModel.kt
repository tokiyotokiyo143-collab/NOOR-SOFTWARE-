package com.example.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.NoorApplication
import com.example.data.backup.BackupJsonCodec
import com.example.data.model.*
import com.example.data.remote.FirebaseManager
import com.example.data.repository.ShopRepository
import com.example.util.Formatters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.UUID

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as NoorApplication
    val repo: ShopRepository = app.repository
    val secretStore = app.secretStore

    // ---------------- AUTH & PIN ----------------

    private val _authState = MutableStateFlow<AuthGateState>(AuthGateState.Loading)
    val authState: StateFlow<AuthGateState> = _authState.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    private val _authLoading = MutableStateFlow(false)
    val authLoading: StateFlow<Boolean> = _authLoading.asStateFlow()

    private val _isPinLocked = MutableStateFlow(false)
    val isPinLocked: StateFlow<Boolean> = _isPinLocked.asStateFlow()

    // ---------------- NAVIGATION & OVERLAYS ----------------

    private val _currentSection = MutableStateFlow(NavSection.HOME)
    val currentSection: StateFlow<NavSection> = _currentSection.asStateFlow()

    private val _sectionHistory = MutableStateFlow<List<NavSection>>(emptyList())

    private val _stockSearchQuery = MutableStateFlow("")
    val stockSearchQuery: StateFlow<String> = _stockSearchQuery.asStateFlow()

    private val _selectedReportTab = MutableStateFlow("Profit & Loss (Private)")
    val selectedReportTab: StateFlow<String> = _selectedReportTab.asStateFlow()

    private val _showShopDaySheet = MutableStateFlow(false)
    val showShopDaySheet: StateFlow<Boolean> = _showShopDaySheet.asStateFlow()

    private val _showPendingDuesSheet = MutableStateFlow(false)
    val showPendingDuesSheet: StateFlow<Boolean> = _showPendingDuesSheet.asStateFlow()

    private val _showCompaniesSheet = MutableStateFlow(false)
    val showCompaniesSheet: StateFlow<Boolean> = _showCompaniesSheet.asStateFlow()

    private val _showQuickCreateSheet = MutableStateFlow(false)
    val showQuickCreateSheet: StateFlow<Boolean> = _showQuickCreateSheet.asStateFlow()

    private val _showFirstLaunchBackupDialog = MutableStateFlow(false)
    val showFirstLaunchBackupDialog: StateFlow<Boolean> = _showFirstLaunchBackupDialog.asStateFlow()

    private val _showWeeklyBackupReminder = MutableStateFlow(false)
    val showWeeklyBackupReminder: StateFlow<Boolean> = _showWeeklyBackupReminder.asStateFlow()

    private val _pendingDuesToast = MutableStateFlow<String?>(null)
    val pendingDuesToast: StateFlow<String?> = _pendingDuesToast.asStateFlow()

    private val _autoBackupToast = MutableStateFlow<String?>(null)
    val autoBackupToast: StateFlow<String?> = _autoBackupToast.asStateFlow()

    private val _globalBannerMessage = MutableStateFlow<String?>(null)
    val globalBannerMessage: StateFlow<String?> = _globalBannerMessage.asStateFlow()

    private val _isModalOpen = MutableStateFlow(false)
    val isModalOpen: StateFlow<Boolean> = _isModalOpen.asStateFlow()

    private val _isSidebarExpanded = MutableStateFlow(true)
    val isSidebarExpanded: StateFlow<Boolean> = _isSidebarExpanded.asStateFlow()

    // ---------------- LIVE DRAFTS ----------------

    private val _saleDraft = MutableStateFlow(SaleDraftState())
    val saleDraft: StateFlow<SaleDraftState> = _saleDraft.asStateFlow()

    private val _repairDraft = MutableStateFlow(RepairDraftState())
    val repairDraft: StateFlow<RepairDraftState> = _repairDraft.asStateFlow()

    private val _purchaseDraft = MutableStateFlow(PurchaseDraftState())
    val purchaseDraft: StateFlow<PurchaseDraftState> = _purchaseDraft.asStateFlow()

    // ---------------- AI ASSISTANT STATE ----------------

    private val _aiSettings = MutableStateFlow(secretStore.getAiSettings())
    val aiSettings: StateFlow<AiSettings> = _aiSettings.asStateFlow()

    private val _aiMessages = MutableStateFlow<List<AiChatMessage>>(emptyList())
    val aiMessages: StateFlow<List<AiChatMessage>> = _aiMessages.asStateFlow()

    private val _aiActions = MutableStateFlow<List<AiActionRecord>>(emptyList())
    val aiActions: StateFlow<List<AiActionRecord>> = _aiActions.asStateFlow()

    private val _pendingAiConfirmation = MutableStateFlow<PendingAiConfirmation?>(null)
    val pendingAiConfirmation: StateFlow<PendingAiConfirmation?> = _pendingAiConfirmation.asStateFlow()

    private val _isScreenSharing = MutableStateFlow(false)
    val isScreenSharing: StateFlow<Boolean> = _isScreenSharing.asStateFlow()

    private val _latestScreenFrame = MutableStateFlow<Bitmap?>(null)
    val latestScreenFrame: StateFlow<Bitmap?> = _latestScreenFrame.asStateFlow()

    val currentUser: ShopUser?
        get() = (_authState.value as? AuthGateState.Ready)?.user

    val isOwnerOrCoOwner: Boolean
        get() = currentUser?.isOwnerOrCoOwner == true

    init {
        evaluateSessionOnStartup()
        observeAccessRevocation()
        startDuesAndBackupSchedulers()
    }

    private fun evaluateSessionOnStartup() {
        viewModelScope.launch {
            val fbUser = withContext(Dispatchers.IO) {
                runCatching { FirebaseManager.auth(app).currentUser }.getOrNull()
            }
            if (fbUser != null && !fbUser.email.isNullOrBlank()) {
                val session = OfflineUserSession(
                    uid = fbUser.uid,
                    email = fbUser.email!!.lowercase(),
                    displayName = fbUser.displayName.orEmpty(),
                    emailVerified = fbUser.isEmailVerified
                )
                withContext(Dispatchers.IO) { secretStore.saveOfflineSession(session) }
                resolveUserAccess(session)
            } else {
                val offline = withContext(Dispatchers.IO) { secretStore.getOfflineSession() }
                if (offline != null && offline.email.isNotBlank()) {
                    resolveUserAccess(offline)
                } else {
                    _authState.value = AuthGateState.SignedOut
                }
            }
            val (pin, promptShown) = withContext(Dispatchers.IO) {
                secretStore.getDevicePin() to secretStore.isFirstLaunchPromptShown()
            }
            if (pin.isNotBlank() && _authState.value is AuthGateState.Ready) {
                _isPinLocked.value = true
            }
            if (!promptShown && repo.items.value.isEmpty() && repo.invoices.value.isEmpty()) {
                _showFirstLaunchBackupDialog.value = true
            }
        }
    }

    private fun observeAccessRevocation() {
        viewModelScope.launch {
            repo.syncStatus.collect { st ->
                val revoked = st.accessRevokedReason
                if (!revoked.isNullOrBlank()) {
                    val ready = _authState.value as? AuthGateState.Ready
                    if (ready != null && !ready.session.email.equals(PRIMARY_OWNER_EMAIL, true)) {
                        _authState.value = AuthGateState.AccessBlocked(ready.session.email, revoked)
                        repo.stopCloudSync()
                    }
                }
            }
        }
    }

    private fun startDuesAndBackupSchedulers() {
        // Pending dues reminder 4.5s after open, then every 30 minutes
        viewModelScope.launch {
            delay(4500L)
            while (true) {
                checkAndTriggerDuesReminder()
                delay(30 * 60 * 1000L)
            }
        }
        // Weekly backup reminder check
        viewModelScope.launch {
            delay(8000L)
            val lastRem = secretStore.getLastWeeklyReminder()
            val now = System.currentTimeMillis()
            if (repo.items.value.isNotEmpty() && (now - lastRem) > 7 * 86_400_000L) {
                _showWeeklyBackupReminder.value = true
            }
        }
    }

    private fun checkAndTriggerDuesReminder() {
        if (_authState.value !is AuthGateState.Ready) return
        val freq = repo.settings.value.duesReminderFreq
        if (freq == "off") return
        val dueParties = repo.analytics.value.partySummaries
            .filter { it.receivableBalance > 0.0 }
        if (dueParties.isEmpty()) return

        val totalDue = dueParties.sumOf { it.receivableBalance }
        _pendingDuesToast.value = "Pending dues ${Formatters.formatInr(totalDue)} - ${dueParties.size} customers · tap to view"
        viewModelScope.launch {
            delay(6000L)
            _pendingDuesToast.value = null
        }
    }

    fun dismissDuesToast() {
        _pendingDuesToast.value = null
    }

    fun dismissAutoBackupToast() {
        _autoBackupToast.value = null
    }

    fun dismissFirstLaunchBackupDialog() {
        secretStore.setFirstLaunchPromptShown()
        _showFirstLaunchBackupDialog.value = false
    }

    fun dismissWeeklyBackupReminder() {
        secretStore.setLastWeeklyReminder(System.currentTimeMillis())
        _showWeeklyBackupReminder.value = false
    }

    private fun resolveUserAccess(session: OfflineUserSession) {
        val email = session.email.lowercase()
        val isPrimaryOwner = email == PRIMARY_OWNER_EMAIL.lowercase()
        if (isPrimaryOwner && !session.emailVerified) {
            _authState.value = AuthGateState.VerifyOwnerEmail(email)
            return
        }

        val users = repo.settings.value.users
        val matched = users.firstOrNull { it.email.equals(email, true) }
        when {
            isPrimaryOwner -> {
                val ownerUser = (matched ?: ShopUser(
                    id = "primary_owner",
                    uid = session.uid,
                    name = session.displayName.ifBlank { "Primary Owner" },
                    email = email,
                    role = "owner",
                    active = true,
                    permissions = UserPermissions.fullOwner()
                )).copy(uid = session.uid, active = true, role = "owner", permissions = UserPermissions.fullOwner())
                _authState.value = AuthGateState.Ready(session, ownerUser)
                repo.startCloudSync(session, ownerUser)
            }
            matched == null -> {
                _authState.value = AuthGateState.AccessBlocked(
                    email,
                    "Account \"$email\" is not added in Shop Settings → Users. Ask the shop owner ($PRIMARY_OWNER_EMAIL) to create your staff login."
                )
            }
            !matched.active -> {
                _authState.value = AuthGateState.AccessBlocked(
                    email,
                    "Account \"$email\" has been deactivated by the shop owner. Ask the owner to reactivate your account in Settings → Users."
                )
            }
            else -> {
                val activeUser = matched.copy(uid = session.uid)
                _authState.value = AuthGateState.Ready(session, activeUser)
                repo.startCloudSync(session, activeUser)
            }
        }
    }

    fun signInWithEmailPassword(emailInput: String, passwordInput: String) {
        val email = emailInput.trim().lowercase()
        if (email.isBlank() || passwordInput.isBlank()) {
            _authError.value = "Incorrect email or password. Please try again."
            return
        }
        viewModelScope.launch {
            _authLoading.value = true
            _authError.value = null
            try {
                val result = FirebaseManager.auth(app)
                    .signInWithEmailAndPassword(email, passwordInput)
                    .await()
                val user = result.user ?: throw IllegalStateException("No signed-in user.")
                val session = OfflineUserSession(
                    uid = user.uid,
                    email = (user.email ?: email).lowercase(),
                    displayName = user.displayName.orEmpty(),
                    emailVerified = user.isEmailVerified
                )
                secretStore.saveOfflineSession(session)
                resolveUserAccess(session)
            } catch (e: Exception) {
                _authError.value = FirebaseManager.mapAuthErrorMessage(e)
            } finally {
                _authLoading.value = false
            }
        }
    }

    fun sendPasswordReset(emailInput: String, onResult: (Boolean, String) -> Unit) {
        val email = emailInput.trim().lowercase()
        if (email.isBlank()) {
            onResult(false, "Enter a valid email address.")
            return
        }
        viewModelScope.launch {
            try {
                FirebaseManager.auth(app).sendPasswordResetEmail(email).await()
                onResult(true, "Password reset link sent to $email.")
            } catch (e: Exception) {
                onResult(false, FirebaseManager.mapAuthErrorMessage(e))
            }
        }
    }

    fun sendOwnerVerificationEmail(onResult: (String) -> Unit) {
        viewModelScope.launch {
            val user = FirebaseManager.auth(app).currentUser
            if (user == null) {
                onResult("No signed-in user.")
                return@launch
            }
            try {
                user.sendEmailVerification().await()
                onResult("Verification email sent to ${user.email}. Check your inbox and tap \"I've Verified — Continue\".")
            } catch (e: Exception) {
                onResult(FirebaseManager.mapAuthErrorMessage(e))
            }
        }
    }

    fun recheckOwnerVerification(onUnverified: (String) -> Unit) {
        viewModelScope.launch {
            val user = FirebaseManager.auth(app).currentUser
            if (user == null) {
                _authState.value = AuthGateState.SignedOut
                return@launch
            }
            try {
                user.reload().await()
                if (user.isEmailVerified) {
                    val session = OfflineUserSession(
                        uid = user.uid,
                        email = (user.email ?: PRIMARY_OWNER_EMAIL).lowercase(),
                        displayName = user.displayName.orEmpty(),
                        emailVerified = true
                    )
                    secretStore.saveOfflineSession(session)
                    resolveUserAccess(session)
                } else {
                    onUnverified("Email is not verified yet. Please open the verification link in your inbox first.")
                }
            } catch (e: Exception) {
                onUnverified(FirebaseManager.mapAuthErrorMessage(e))
            }
        }
    }

    fun changePassword(
        currentPass: String,
        newPass: String,
        confirmPass: String,
        onResult: (Boolean, String) -> Unit
    ) {
        val fbUser = FirebaseManager.auth(app).currentUser
        val email = fbUser?.email
        if (fbUser == null || email.isNullOrBlank()) {
            onResult(false, "You must be signed in with an email and password account to change your password.")
            return
        }
        if (newPass.length < 6) {
            onResult(false, "Password must be at least 6 characters.")
            return
        }
        if (newPass != confirmPass) {
            onResult(false, "New passwords do not match.")
            return
        }
        viewModelScope.launch {
            try {
                val cred = com.google.firebase.auth.EmailAuthProvider.getCredential(email, currentPass)
                fbUser.reauthenticate(cred).await()
                fbUser.updatePassword(newPass).await()
                onResult(true, "Your password has been updated. Use the new password the next time you sign in on any device.")
            } catch (e: Exception) {
                onResult(false, FirebaseManager.mapAuthErrorMessage(e))
            }
        }
    }

    fun createStaffAccount(
        name: String,
        emailInput: String,
        tempPassword: String,
        role: String,
        permissions: UserPermissions,
        onResult: (Boolean, String) -> Unit
    ) {
        val cleanName = name.trim()
        val email = emailInput.trim().lowercase()
        if (cleanName.isBlank()) {
            onResult(false, "Enter the staff member name.")
            return
        }
        if (!email.contains("@") || !email.contains(".")) {
            onResult(false, "Enter a valid email address (e.g. staff@gmail.com).")
            return
        }
        if (tempPassword.length < 6) {
            onResult(false, "Temporary password must be at least 6 characters.")
            return
        }
        if (repo.settings.value.users.any { it.email.equals(email, true) }) {
            onResult(false, "User \"$email\" is already added.")
            return
        }

        viewModelScope.launch {
            var uid = ""
            try {
                val secAuth = FirebaseManager.secondaryAuthForStaffCreation(app)
                val res = secAuth.createUserWithEmailAndPassword(email, tempPassword).await()
                uid = res.user?.uid.orEmpty()
                secAuth.signOut()
            } catch (e: Exception) {
                // If user already exists in Firebase Auth, still allow adding to shop users list
                if (!e.message.orEmpty().contains("already in use", ignoreCase = true)) {
                    repo.addDebugLog("WARN", "StaffAuth", e.message ?: "Auth creation warning")
                }
            }
            val newUser = ShopUser(
                id = UUID.randomUUID().toString(),
                uid = uid,
                name = cleanName,
                email = email,
                role = role,
                active = true,
                permissions = if (role == "co_owner") UserPermissions.fullOwner() else permissions
            )
            repo.addOrUpdateUser(newUser)
            onResult(
                true,
                "Created account for $cleanName ($email). Share their email and temporary password so they can sign in and set their own password."
            )
        }
    }

    fun signOut() {
        repo.stopCloudSync()
        FirebaseManager.auth(app).signOut()
        secretStore.saveOfflineSession(null)
        _isPinLocked.value = false
        _authError.value = null
        _authState.value = AuthGateState.SignedOut
    }

    fun lockNowWithPin(): Boolean {
        if (secretStore.getDevicePin().length !in 4..6) return false
        _isPinLocked.value = true
        return true
    }

    fun unlockWithPin(pin: String): Boolean {
        val saved = secretStore.getDevicePin()
        return if (pin == saved) {
            _isPinLocked.value = false
            true
        } else false
    }

    // ---------------- NAVIGATION ----------------

    fun navigateTo(section: NavSection) {
        if (!hasSectionPermission(section)) {
            showBanner("Permission denied for ${section.labelEn}.")
            return
        }
        if (_currentSection.value != section) {
            _sectionHistory.value = (_sectionHistory.value + _currentSection.value).takeLast(20)
            _currentSection.value = section
        }
    }

    fun navigateBack(): Boolean {
        val hist = _sectionHistory.value
        return if (hist.isNotEmpty()) {
            _currentSection.value = hist.last()
            _sectionHistory.value = hist.dropLast(1)
            true
        } else if (_currentSection.value != NavSection.HOME) {
            _currentSection.value = NavSection.HOME
            true
        } else false
    }

    fun navigateMainStep(delta: Int) {
        val mainList = NavSection.entries.filter { it.isMain && hasSectionPermission(it) }
        if (mainList.isEmpty()) return
        val curIdx = mainList.indexOf(_currentSection.value).let { if (it < 0) 0 else it }
        val nextIdx = (curIdx + delta + mainList.size) % mainList.size
        navigateTo(mainList[nextIdx])
    }

    fun hasSectionPermission(section: NavSection): Boolean {
        val user = currentUser ?: return false
        if (user.isOwnerOrCoOwner) return true
        return when (section) {
            NavSection.HOME -> true
            NavSection.BILLING -> user.hasPermission { it.salesCreate || it.salesEditReturnCancel }
            NavSection.ITEMS -> user.hasPermission { it.stockView || it.stockEditBulkRates }
            NavSection.SEARCH -> user.hasPermission { it.searchModels || it.stockView }
            NavSection.REPAIRS, NavSection.SERVICE_STATUS -> user.hasPermission { it.serviceCreate || it.serviceUpdateBill }
            NavSection.PURCHASES -> user.hasPermission { it.purchasesCreate || it.purchasesEditDelete }
            NavSection.PARTIES -> user.hasPermission { it.partiesDues }
            NavSection.CASH_BANK -> user.hasPermission { it.reports || it.partiesDues }
            NavSection.EXPENSES -> user.hasPermission { it.expenses }
            NavSection.REPORTS -> user.hasPermission { it.reports }
            NavSection.SETTINGS -> user.hasPermission { it.settings }
        }
    }

    fun setStockSearchFilter(query: String) {
        _stockSearchQuery.value = query
    }

    fun openReportTab(tab: String) {
        _selectedReportTab.value = tab
        navigateTo(NavSection.REPORTS)
    }

    fun setShowShopDaySheet(show: Boolean) { _showShopDaySheet.value = show }
    fun setShowPendingDuesSheet(show: Boolean) { _showPendingDuesSheet.value = show }
    fun setShowCompaniesSheet(show: Boolean) { _showCompaniesSheet.value = show }
    fun setShowQuickCreateSheet(show: Boolean) { _showQuickCreateSheet.value = show }
    fun setModalOpen(open: Boolean) { _isModalOpen.value = open }
    fun setSidebarExpanded(expanded: Boolean) { _isSidebarExpanded.value = expanded }
    fun toggleSidebar() { _isSidebarExpanded.value = !_isSidebarExpanded.value }

    fun showBanner(msg: String) {
        _globalBannerMessage.value = msg
        viewModelScope.launch {
            delay(3500L)
            if (_globalBannerMessage.value == msg) _globalBannerMessage.value = null
        }
    }

    // ---------------- LIVE DRAFTS MUTATIONS ----------------

    fun updateSaleDraft(transform: (SaleDraftState) -> SaleDraftState) {
        _saleDraft.value = transform(_saleDraft.value)
    }

    fun resetSaleDraft(billType: String = "product") {
        _saleDraft.value = SaleDraftState(billType = billType)
    }

    fun loadInvoiceIntoDraft(invoice: Invoice) {
        _saleDraft.value = SaleDraftState(
            editingInvoiceId = invoice.id,
            docType = invoice.docType,
            billType = invoice.billType,
            date = invoice.date,
            customerId = invoice.customerId,
            customerName = invoice.customerName,
            customerPhone = invoice.customerPhone,
            notes = invoice.notes,
            imei = invoice.imei,
            lines = invoice.items,
            discountType = invoice.discountType,
            discountAmountText = if (invoice.discountAmount > 0) Formatters.formatDecimal(invoice.discountAmount) else "",
            gstRate = invoice.gstRate,
            paymentMode = invoice.paymentMode,
            amountPaidText = Formatters.formatDecimal(invoice.amountPaid),
            amountPaidTouched = true,
            cashTenderText = if (invoice.cashTender > 0) Formatters.formatDecimal(invoice.cashTender) else "",
            partSourceMode = invoice.partSourceMode,
            linkedPartItemId = invoice.linkedPartItemId,
            outsideSupplierName = invoice.outsideSupplierName,
            outsidePartName = invoice.outsidePartName,
            outsidePartCategory = invoice.outsidePartCategory,
            outsidePartCostText = if (invoice.outsidePartCost > 0) Formatters.formatDecimal(invoice.outsidePartCost) else "",
            outsidePartChargeText = if (invoice.outsidePartCharge > 0) Formatters.formatDecimal(invoice.outsidePartCharge) else "",
            outsidePartPaymentMode = invoice.outsidePartPaymentMode,
            labourChargeText = if (invoice.labourCharge > 0) Formatters.formatDecimal(invoice.labourCharge) else ""
        )
        navigateTo(NavSection.BILLING)
    }

    suspend fun saveCurrentSaleDraft(): Invoice {
        val d = _saleDraft.value
        val lines = d.lines.toMutableList()
        if (d.partSourceMode == "stock" && !d.linkedPartItemId.isNullOrBlank()) {
            val stock = repo.items.value.find { it.id == d.linkedPartItemId }
            if (stock != null && lines.none { it.itemId == stock.id }) {
                lines.add(
                    InvoiceLineItem(
                        itemId = stock.id,
                        name = stock.displayName,
                        category = stock.category,
                        brand = stock.brand,
                        model = stock.model,
                        color = stock.color,
                        qty = 1,
                        rate = stock.sellingPrice,
                        costPrice = if (isOwnerOrCoOwner) stock.costPrice else 0.0,
                        isService = stock.isService,
                        partSource = "stock"
                    )
                )
            }
        }
        val inv = Invoice(
            id = d.editingInvoiceId ?: UUID.randomUUID().toString(),
            invoiceNumber = d.editingInvoiceId?.let { id -> repo.invoices.value.find { it.id == id }?.invoiceNumber } ?: "",
            docType = d.docType,
            date = d.date.ifBlank { Formatters.todayIso() },
            customerId = d.customerId,
            customerName = d.customerName,
            customerPhone = d.customerPhone,
            billType = d.billType,
            imei = d.imei,
            items = lines,
            subtotal = d.subtotal,
            discountType = d.discountType,
            discountAmount = d.discountAmountText.toDoubleOrNull() ?: 0.0,
            gstRate = d.gstRate,
            grandTotal = d.grandTotal,
            paymentMode = d.paymentMode,
            amountPaid = d.effectivePaid,
            balanceDue = d.balanceDue,
            cashTender = d.cashTender,
            status = "active",
            notes = d.notes,
            partSourceMode = d.partSourceMode,
            linkedPartItemId = d.linkedPartItemId,
            outsideSupplierName = d.outsideSupplierName,
            outsidePartName = d.outsidePartName,
            outsidePartCategory = d.outsidePartCategory,
            outsidePartCost = d.outsideCost,
            outsidePartCharge = d.outsideCharge,
            outsidePartPaymentMode = d.outsidePartPaymentMode,
            labourCharge = d.labourCharge
        )
        val saved = repo.saveInvoice(inv, isOwnerOrCoOwner)
        resetSaleDraft(d.billType)
        return saved
    }

    fun updateRepairDraft(transform: (RepairDraftState) -> RepairDraftState) {
        _repairDraft.value = transform(_repairDraft.value)
    }

    fun resetRepairDraft() {
        _repairDraft.value = RepairDraftState()
    }

    fun loadRepairIntoDraft(r: RepairJob) {
        _repairDraft.value = RepairDraftState(
            editingRepairId = r.id,
            customerName = r.customerName,
            customerPhone = r.customerPhone,
            phoneModel = r.phoneModel,
            imei = r.imei,
            problem = r.problem,
            status = r.status,
            date = r.date,
            deliveryDate = r.deliveryDate,
            notes = r.notes,
            partSource = r.partSource,
            stockPartItemId = r.outsidePartItemId,
            outsideSupplierName = r.outsideSupplierName,
            outsidePartName = r.outsidePartName,
            outsidePartCategory = r.outsidePartCategory,
            outsidePartPaymentMode = r.outsidePartPaymentMode,
            partBuyingCostText = if (r.partBuyingCost > 0) Formatters.formatDecimal(r.partBuyingCost) else "",
            partChargeText = if (r.partCharge > 0) Formatters.formatDecimal(r.partCharge) else "",
            serviceChargeText = if (r.serviceCharge > 0) Formatters.formatDecimal(r.serviceCharge) else "",
            advancePaidText = if (r.advancePaid > 0) Formatters.formatDecimal(r.advancePaid) else "",
            billingPaymentMode = r.billingPaymentMode,
            createBillNow = r.billedInvoiceId != null
        )
        navigateTo(NavSection.REPAIRS)
    }

    fun updatePurchaseDraft(transform: (PurchaseDraftState) -> PurchaseDraftState) {
        _purchaseDraft.value = transform(_purchaseDraft.value)
    }

    fun resetPurchaseDraft() {
        _purchaseDraft.value = PurchaseDraftState()
    }

    fun loadPurchaseIntoDraft(p: Purchase) {
        _purchaseDraft.value = PurchaseDraftState(
            editingPurchaseId = p.id,
            docType = p.docType,
            supplierName = p.supplierName,
            supplierPhone = p.supplierPhone,
            date = p.date,
            supplierBillNo = p.supplierBillNo,
            notes = p.notes,
            rows = p.items.ifEmpty { List(3) { PurchaseLineItem() } },
            discountText = if (p.discount > 0) Formatters.formatDecimal(p.discount) else "",
            transportText = if (p.transportExtra > 0) Formatters.formatDecimal(p.transportExtra) else "",
            paymentMode = p.paymentMode,
            amountPaidText = Formatters.formatDecimal(p.amountPaid),
            amountPaidTouched = true,
            generateBarcodes = p.generateBarcodes
        )
        navigateTo(NavSection.PURCHASES)
    }

    // ---------------- AI SETTINGS & ACTIONS ----------------

    fun updateAiSettings(transform: (AiSettings) -> AiSettings) {
        val next = transform(_aiSettings.value)
        secretStore.saveAiSettings(next)
        _aiSettings.value = next
    }

    fun appendAiMessage(msg: AiChatMessage) {
        _aiMessages.value = (_aiMessages.value + msg).takeLast(40)
    }

    fun clearAiMessages() {
        _aiMessages.value = emptyList()
    }

    fun recordAiAction(description: String, undoBlock: (suspend () -> Unit)? = null) {
        _aiActions.value = (listOf(AiActionRecord(description = description, undoBlock = undoBlock)) + _aiActions.value).take(20)
    }

    fun undoLastAiAction() {
        val last = _aiActions.value.firstOrNull() ?: return
        val undo = last.undoBlock ?: return
        viewModelScope.launch {
            undo()
            _aiActions.value = _aiActions.value.drop(1)
            appendAiMessage(AiChatMessage(isUser = false, text = "Undid: ${last.description}"))
        }
    }

    fun setPendingAiConfirmation(conf: PendingAiConfirmation?) {
        _pendingAiConfirmation.value = conf
    }

    fun setScreenSharing(active: Boolean, latestFrame: Bitmap? = null) {
        _isScreenSharing.value = active
        if (!active) _latestScreenFrame.value = null else if (latestFrame != null) _latestScreenFrame.value = latestFrame
    }

    fun updateLatestScreenFrame(bmp: Bitmap?) {
        if (_isScreenSharing.value) _latestScreenFrame.value = bmp
    }

    // ---------------- SHOP DAY HELPERS ----------------

    fun triggerManualBackup() {
        viewModelScope.launch {
            val saved = repo.saveAutoBackupSnapshot()
            if (saved != null) {
                _autoBackupToast.value = "Automatic backup saved - Tap to also download a copy to this phone"
                delay(6000L)
                _autoBackupToast.value = null
            } else {
                showBanner("No data")
            }
        }
    }

    fun importBackupJsonText(raw: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val parsed = BackupJsonCodec.parseBackupJson(raw)
            parsed.fold(
                onSuccess = { b ->
                    val (itemsCnt, invCnt) = repo.importBackup(b)
                    onResult(true, "Backup restored! Loaded $itemsCnt items and $invCnt invoices.")
                },
                onFailure = { e ->
                    onResult(false, "Invalid backup file: ${e.message ?: "Unrecognized JSON"}")
                }
            )
        }
    }
}
