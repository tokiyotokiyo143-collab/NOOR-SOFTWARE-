package com.example

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ai.FloatingAiBubbleOverlay
import com.example.ai.ScreenCaptureService
import com.example.data.model.*
import com.example.ui.MainViewModel
import com.example.ui.auth.*
import com.example.ui.components.*
import com.example.ui.screens.*
import com.example.ui.theme.LocalNoorColors
import com.example.ui.theme.NoorTheme
import com.example.ui.theme.noorCard
import com.example.util.Formatters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class MainActivity : ComponentActivity() {
    private var tts: TextToSpeech? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("ta", "IN")
            }
        }

        setContent {
            val vm: MainViewModel = viewModel()
            NoorAppRoot(vm = vm, tts = tts)
        }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}

@Composable
fun NoorAppRoot(vm: MainViewModel, tts: TextToSpeech?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    val settings by vm.repo.settings.collectAsState()
    val authState by vm.authState.collectAsState()
    val authError by vm.authError.collectAsState()
    val authLoading by vm.authLoading.collectAsState()
    val isPinLocked by vm.isPinLocked.collectAsState()

    val items by vm.repo.items.collectAsState()
    val invoices by vm.repo.invoices.collectAsState()
    val purchases by vm.repo.purchases.collectAsState()
    val parties by vm.repo.parties.collectAsState()
    val payments by vm.repo.payments.collectAsState()
    val expenses by vm.repo.expenses.collectAsState()
    val repairs by vm.repo.repairs.collectAsState()
    val bankAccounts by vm.repo.bankAccounts.collectAsState()
    val cashBankTxns by vm.repo.cashBankTxns.collectAsState()
    val shopDays by vm.repo.shopDays.collectAsState()
    val syncStatus by vm.repo.syncStatus.collectAsState()
    val companies by vm.repo.companies.collectAsState()
    val activeCompany by vm.repo.activeCompany.collectAsState()

    val currentSection by vm.currentSection.collectAsState()
    val stockSearchQuery by vm.stockSearchQuery.collectAsState()
    val showShopDaySheet by vm.showShopDaySheet.collectAsState()
    val showPendingDuesSheet by vm.showPendingDuesSheet.collectAsState()
    val showCompaniesSheet by vm.showCompaniesSheet.collectAsState()
    val showQuickCreateSheet by vm.showQuickCreateSheet.collectAsState()
    val showFirstLaunchBackupDialog by vm.showFirstLaunchBackupDialog.collectAsState()
    val showWeeklyBackupReminder by vm.showWeeklyBackupReminder.collectAsState()
    val pendingDuesToast by vm.pendingDuesToast.collectAsState()
    val autoBackupToast by vm.autoBackupToast.collectAsState()
    val globalBanner by vm.globalBannerMessage.collectAsState()
    val isModalOpen by vm.isModalOpen.collectAsState()

    var topSearchActive by remember { mutableStateOf(false) }
    var showLongPressActions by remember { mutableStateOf(false) }

    val importBackupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val text = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
                }
                vm.importBackupJsonText(text) { _, msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val exportBackupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            scope.launch {
                val json = vm.repo.exportBackupJson()
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                }
                vm.repo.updateSettings { it.copy(lastBackupAt = System.currentTimeMillis()) }
                Toast.makeText(context, "Backup exported!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val screenCaptureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK && res.data != null) {
            val svcIntent = Intent(context, ScreenCaptureService::class.java).apply {
                action = ScreenCaptureService.ACTION_START
                putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, res.resultCode)
                putExtra(ScreenCaptureService.EXTRA_DATA, res.data)
            }
            ContextCompat.startForegroundService(context, svcIntent)
        } else {
            Toast.makeText(context, "Screen share cancelled.", Toast.LENGTH_SHORT).show()
        }
    }

    fun handleQuickAction(key: String) {
        when (key) {
            "sale" -> {
                vm.resetSaleDraft("product")
                vm.navigateTo(NavSection.BILLING)
            }
            "service" -> {
                vm.resetRepairDraft()
                vm.navigateTo(NavSection.REPAIRS)
            }
            "purchase" -> {
                vm.resetPurchaseDraft()
                vm.navigateTo(NavSection.PURCHASES)
            }
            "expense" -> vm.navigateTo(NavSection.EXPENSES)
            "stock" -> vm.navigateTo(NavSection.ITEMS)
            "shop_day" -> vm.setShowShopDaySheet(true)
        }
    }

    NoorTheme(settings = settings) {
        val palette = LocalNoorColors.current

        when (val gate = authState) {
            is AuthGateState.Loading -> {
                Box(
                    Modifier.fillMaxSize().background(palette.background),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = palette.accent)
                }
            }
            is AuthGateState.SignedOut -> {
                SignInScreen(
                    isLoading = authLoading,
                    errorMessage = authError,
                    onSignIn = { e, p -> vm.signInWithEmailPassword(e, p) },
                    onSendReset = { e, cb -> vm.sendPasswordReset(e, cb) }
                )
            }
            is AuthGateState.VerifyOwnerEmail -> {
                VerifyOwnerEmailScreen(
                    email = gate.email,
                    onSendVerification = { cb -> vm.sendOwnerVerificationEmail(cb) },
                    onCheckVerified = { cb -> vm.recheckOwnerVerification(cb) },
                    onSignOut = { vm.signOut() }
                )
            }
            is AuthGateState.AccessBlocked -> {
                AccessBlockedScreen(
                    email = gate.email,
                    reason = gate.reason,
                    onSignOut = { vm.signOut() }
                )
            }
            is AuthGateState.Ready -> {
                if (isPinLocked) {
                    PinLockOverlay(
                        onUnlock = { pin -> vm.unlockWithPin(pin) },
                        onSignOut = { vm.signOut() }
                    )
                } else {
                    val user = gate.user
                    val isOwner = user.isOwnerOrCoOwner
                    val anySheetOpen = showShopDaySheet || showPendingDuesSheet || showCompaniesSheet || showQuickCreateSheet || showLongPressActions || isModalOpen

                    BackHandler(enabled = topSearchActive || currentSection != NavSection.HOME) {
                        if (topSearchActive) topSearchActive = false else vm.navigateBack()
                    }

                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(palette.background)
                            .windowInsetsPadding(WindowInsets.safeDrawing)
                    ) {
                        val widthDp = maxWidth
                        val detectedMode = when {
                            widthDp >= 1000.dp -> "computer"
                            widthDp >= 600.dp -> "tab"
                            else -> "mobile"
                        }
                        val activeScreenMode = if (settings.screenMode == "auto") detectedMode else settings.screenMode
                        val isTabletOrDesktop = activeScreenMode == "tab" || activeScreenMode == "computer"

                        val edgeIgnorePx = with(density) { 28.dp.toPx() }
                        val swipeThresholdPx = with(density) { 64.dp.toPx() }

                        Row(modifier = Modifier.fillMaxSize()) {
                            if (isTabletOrDesktop) {
                                TabletSidebar(
                                    settings = settings,
                                    currentSection = currentSection,
                                    stockCount = items.size,
                                    repairsInShopCount = repairs.count { it.status != "Delivered" },
                                    partiesCount = parties.size,
                                    onNavigate = { vm.navigateTo(it) }
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .pointerInput(isTabletOrDesktop, anySheetOpen) {
                                        if (isTabletOrDesktop || anySheetOpen) return@pointerInput
                                        var dragX = 0f
                                        var dragY = 0f
                                        var startedFromEdge = false
                                        detectHorizontalDragGestures(
                                            onDragStart = { startOffset ->
                                                dragX = 0f
                                                dragY = 0f
                                                startedFromEdge = startOffset.x < edgeIgnorePx || startOffset.x > (size.width - edgeIgnorePx)
                                            },
                                            onHorizontalDrag = { change, amount ->
                                                dragX += amount
                                                dragY += kotlin.math.abs(change.position.y - change.previousPosition.y)
                                            },
                                            onDragEnd = {
                                                if (!startedFromEdge && kotlin.math.abs(dragX) > swipeThresholdPx && kotlin.math.abs(dragX) > dragY * 1.4f) {
                                                    vm.navigateMainStep(if (dragX < 0) 1 else -1)
                                                }
                                            }
                                        )
                                    }
                            ) {
                                Column(modifier = Modifier.fillMaxSize()) {
                                    if (!isTabletOrDesktop) {
                                        MobileTopBar(
                                            settings = settings,
                                            currentSection = currentSection,
                                            items = items,
                                            isSearchActive = topSearchActive,
                                            onSearchActiveChange = { topSearchActive = it },
                                            onSelectStockItem = { item ->
                                                vm.setStockSearchFilter(item.model.ifBlank { item.displayName })
                                                vm.navigateTo(NavSection.ITEMS)
                                            },
                                            onNavigate = { vm.navigateTo(it) },
                                            onQuickAction = ::handleQuickAction
                                        )
                                    }

                                    // Offline Banner
                                    if (!syncStatus.isOnline) {
                                        Text(
                                            text = "Offline Mode — Billing, stock, repairs, and ledger work 100% offline. Cloud changes will sync automatically when internet returns.",
                                            color = Color.Black,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 11.5.sp,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(palette.statusAmber)
                                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                        )
                                    }

                                    // Cloud Sync Error & Storage Warning Banners
                                    syncStatus.cloudError?.let { err ->
                                        Text(
                                            text = "Cloud Sync Error: $err",
                                            color = Color.White,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(palette.statusRed)
                                                .padding(horizontal = 12.dp, vertical = 5.dp)
                                        )
                                    }
                                    syncStatus.storageWarning?.let { warn ->
                                        Text(
                                            text = "Storage Warning: $warn",
                                            color = Color.Black,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(palette.statusAmber)
                                                .padding(horizontal = 12.dp, vertical = 5.dp)
                                        )
                                    }
                                    globalBanner?.let { banner ->
                                        Text(
                                            text = banner,
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(palette.accent)
                                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                        )
                                    }

                                    // Active Section Body
                                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                        when (currentSection) {
                                            NavSection.HOME -> HomeScreen(
                                                vm = vm,
                                                settings = settings,
                                                items = items,
                                                invoices = invoices,
                                                purchases = purchases,
                                                parties = parties,
                                                expenses = expenses,
                                                repairs = repairs,
                                                shopDays = shopDays,
                                                isOwner = isOwner
                                            )
                                            NavSection.BILLING -> SalesScreen(
                                                vm = vm,
                                                settings = settings,
                                                items = items,
                                                invoices = invoices,
                                                isOwner = isOwner,
                                                canEditReturnCancel = user.hasPermission { it.salesEditReturnCancel }
                                            )
                                            NavSection.ITEMS -> StockScreen(
                                                vm = vm,
                                                settings = settings,
                                                items = items,
                                                invoices = invoices,
                                                initialFilter = stockSearchQuery,
                                                isOwner = isOwner,
                                                canEditStock = user.hasPermission { it.stockEditBulkRates }
                                            )
                                            NavSection.SEARCH -> SearchModelsScreen(
                                                vm = vm,
                                                settings = settings,
                                                items = items,
                                                invoices = invoices,
                                                isOwner = isOwner
                                            )
                                            NavSection.REPAIRS -> RepairsScreen(
                                                vm = vm,
                                                settings = settings,
                                                items = items,
                                                repairs = repairs,
                                                isOwner = isOwner,
                                                canUpdateAndBill = user.hasPermission { it.serviceUpdateBill },
                                                userEmail = user.email
                                            )
                                            NavSection.SERVICE_STATUS -> ServiceStatusScreen(
                                                vm = vm,
                                                settings = settings,
                                                repairs = repairs,
                                                isOwner = isOwner,
                                                canUpdateAndBill = user.hasPermission { it.serviceUpdateBill },
                                                userEmail = user.email
                                            )
                                            NavSection.PURCHASES -> PurchasesScreen(
                                                vm = vm,
                                                settings = settings,
                                                items = items,
                                                purchases = purchases,
                                                parties = parties,
                                                isOwner = isOwner,
                                                canEditDelete = user.hasPermission { it.purchasesEditDelete }
                                            )
                                            NavSection.PARTIES -> PartiesScreen(
                                                vm = vm,
                                                settings = settings,
                                                parties = parties,
                                                invoices = invoices,
                                                purchases = purchases,
                                                repairs = repairs,
                                                payments = payments
                                            )
                                            NavSection.CASH_BANK -> CashBankScreen(
                                                vm = vm,
                                                settings = settings,
                                                bankAccounts = bankAccounts,
                                                cashBankTxns = cashBankTxns,
                                                parties = parties,
                                                invoices = invoices,
                                                purchases = purchases,
                                                payments = payments,
                                                expenses = expenses,
                                                repairs = repairs
                                            )
                                            NavSection.EXPENSES -> ExpensesScreen(
                                                vm = vm,
                                                expenses = expenses,
                                                bankAccounts = bankAccounts
                                            )
                                            NavSection.REPORTS -> ReportsScreen(
                                                vm = vm,
                                                settings = settings,
                                                items = items,
                                                invoices = invoices,
                                                purchases = purchases,
                                                parties = parties,
                                                payments = payments,
                                                expenses = expenses,
                                                repairs = repairs,
                                                bankAccounts = bankAccounts,
                                                cashBankTxns = cashBankTxns,
                                                isOwner = isOwner
                                            )
                                            NavSection.SETTINGS -> SettingsScreen(
                                                vm = vm,
                                                settings = settings,
                                                activeCompany = activeCompany,
                                                currentUser = user,
                                                isOwner = isOwner,
                                                detectedScreenMode = detectedMode
                                            )
                                        }
                                    }

                                    // Optional bottom navigation bar on mobile when Tab Navigation toggle is ON
                                    if (!isTabletOrDesktop && settings.useTabNavigation) {
                                        val mainSections = NavSection.entries.filter { it.isMain && vm.hasSectionPermission(it) }
                                        NavigationBar(containerColor = palette.card) {
                                            mainSections.forEach { sec ->
                                                NavigationBarItem(
                                                    selected = currentSection == sec,
                                                    onClick = { vm.navigateTo(sec) },
                                                    icon = { Icon(navSectionIcon(sec), contentDescription = sec.title(settings.language)) },
                                                    label = {
                                                        Text(
                                                            sec.title(settings.language),
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis,
                                                            fontSize = 10.sp
                                                        )
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }

                                // Edge < and > step arrows + Floating + Quick Create button
                                if (!anySheetOpen) {
                                    if (!isTabletOrDesktop) {
                                        EdgeStepArrowButton(
                                            isNext = false,
                                            onClick = { vm.navigateMainStep(-1) },
                                            modifier = Modifier
                                                .align(Alignment.CenterStart)
                                                .padding(start = 4.dp)
                                        )
                                        EdgeStepArrowButton(
                                            isNext = true,
                                            onClick = { vm.navigateMainStep(1) },
                                            modifier = Modifier
                                                .align(Alignment.CenterEnd)
                                                .padding(end = 4.dp)
                                        )
                                    }

                                    FloatingQuickCreatePlusButton(
                                        onTap = { vm.setShowQuickCreateSheet(true) },
                                        onLongPress = { showLongPressActions = true },
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(end = 16.dp, bottom = if (!isTabletOrDesktop && settings.useTabNavigation) 86.dp else 18.dp)
                                    )
                                }

                                // Floating Toasts (Pending Dues & Auto Backup)
                                Column(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(horizontal = 16.dp, vertical = 90.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    pendingDuesToast?.let { msg ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(14.dp))
                                                .background(palette.card)
                                                .border(1.dp, palette.accent, RoundedCornerShape(14.dp))
                                                .clickable {
                                                    vm.dismissDuesToast()
                                                    vm.setShowPendingDuesSheet(true)
                                                }
                                                .padding(horizontal = 14.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(msg, color = palette.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, modifier = Modifier.weight(1f))
                                            IconButton(onClick = { vm.dismissDuesToast() }, modifier = Modifier.size(24.dp)) {
                                                Icon(Icons.Outlined.Close, contentDescription = "Dismiss", tint = palette.textMuted, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                    autoBackupToast?.let { msg ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(14.dp))
                                                .background(palette.card)
                                                .border(1.dp, palette.statusGreen, RoundedCornerShape(14.dp))
                                                .clickable {
                                                    vm.dismissAutoBackupToast()
                                                    exportBackupLauncher.launch("Noor-Billing-Backup-${Formatters.todayIso()}.json")
                                                }
                                                .padding(horizontal = 14.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(msg, color = palette.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, modifier = Modifier.weight(1f))
                                            IconButton(onClick = { vm.dismissAutoBackupToast() }, modifier = Modifier.size(24.dp)) {
                                                Icon(Icons.Outlined.Close, contentDescription = "Dismiss", tint = palette.textMuted, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                }

                                // Section 18: Floating AI Assistant Bubble (only when enabled & no modal covering screen)
                                if (!anySheetOpen) {
                                    FloatingAiBubbleOverlay(
                                        vm = vm,
                                        tts = tts,
                                        onRequestScreenShare = {
                                            val mpm = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                                            screenCaptureLauncher.launch(mpm.createScreenCaptureIntent())
                                        },
                                        onStopScreenShare = {
                                            ScreenCaptureService.stop(context)
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Bottom Sheets & Dialogs
                    if (showShopDaySheet) {
                        val today = Formatters.todayIso()
                        ShopDayBottomSheet(
                            todayRecord = shopDays.firstOrNull { it.date == today },
                            allDays = shopDays,
                            todayInvoices = invoices.filter { it.date == today },
                            todayExpenses = expenses.filter { it.date == today },
                            onSaveDay = { rec -> scope.launch { vm.repo.saveShopDay(rec) } },
                            onDismiss = { vm.setShowShopDaySheet(false) }
                        )
                    }

                    if (showPendingDuesSheet) {
                        val dueSummaries = parties.map { vm.repo.computePartySummary(it) }.filter { it.receivableBalance > 0.0 }
                        PendingDuesBottomSheet(
                            settings = settings,
                            dueSummaries = dueSummaries,
                            onChangeFreq = { freq -> scope.launch { vm.repo.updateSettings { it.copy(duesReminderFreq = freq) } } },
                            onDismiss = { vm.setShowPendingDuesSheet(false) }
                        )
                    }

                    if (showQuickCreateSheet) {
                        QuickCreateSheet(
                            onAction = ::handleQuickAction,
                            onDismiss = { vm.setShowQuickCreateSheet(false) }
                        )
                    }

                    if (showLongPressActions) {
                        QuickActionsLongPressDialog(
                            onAction = ::handleQuickAction,
                            onDismiss = { showLongPressActions = false }
                        )
                    }

                    if (showCompaniesSheet) {
                        CompaniesBottomSheet(
                            companies = companies,
                            activeCompany = activeCompany,
                            onSwitch = { target -> scope.launch { vm.repo.switchCompany(target.id) } },
                            onCreate = { name -> scope.launch { vm.repo.createCompany(name) } },
                            onRename = { id, newName -> vm.repo.renameCompany(id, newName) },
                            onDelete = { id -> scope.launch { vm.repo.deleteCompany(id) } },
                            onDismiss = { vm.setShowCompaniesSheet(false) }
                        )
                    }

                    if (showFirstLaunchBackupDialog) {
                        AlertDialog(
                            onDismissRequest = { vm.dismissFirstLaunchBackupDialog() },
                            containerColor = palette.card,
                            title = { Text("Import Existing Data?", color = palette.textPrimary, fontWeight = FontWeight.Black) },
                            text = {
                                Text(
                                    "Export a backup from your old app first. New app data does not come automatically.",
                                    color = palette.textSecondary
                                )
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    vm.dismissFirstLaunchBackupDialog()
                                    importBackupLauncher.launch(arrayOf("application/json", "*/*"))
                                }) { Text("Import my existing data", color = palette.accent, fontWeight = FontWeight.Bold) }
                            },
                            dismissButton = {
                                TextButton(onClick = { vm.dismissFirstLaunchBackupDialog() }) {
                                    Text("Start Fresh", color = palette.textSecondary)
                                }
                            }
                        )
                    }

                    if (showWeeklyBackupReminder) {
                        AlertDialog(
                            onDismissRequest = { vm.dismissWeeklyBackupReminder() },
                            containerColor = palette.card,
                            title = { Text("Weekly Backup Reminder:", color = palette.textPrimary, fontWeight = FontWeight.Bold) },
                            text = { Text("Save a fresh JSON backup of your bills, stock, repairs, and customer dues.", color = palette.textSecondary) },
                            confirmButton = {
                                TextButton(onClick = {
                                    vm.dismissWeeklyBackupReminder()
                                    exportBackupLauncher.launch("Noor-Billing-Backup-${Formatters.todayIso()}.json")
                                }) { Text("Export Backup", color = palette.accent, fontWeight = FontWeight.Bold) }
                            },
                            dismissButton = {
                                TextButton(onClick = { vm.dismissWeeklyBackupReminder() }) {
                                    Text("Later", color = palette.textSecondary)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
