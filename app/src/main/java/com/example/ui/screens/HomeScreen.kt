package com.example.ui.screens

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.*
import com.example.data.repository.ShopRepository
import com.example.ui.MainViewModel
import com.example.ui.components.NoorPrimaryButton
import com.example.ui.components.NoorSecondaryButton
import com.example.ui.components.NoorStatusDot
import com.example.ui.theme.LocalNoorColors
import com.example.ui.theme.noorCard
import com.example.util.Formatters
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    vm: MainViewModel,
    settings: ShopSettings,
    items: List<StockItem>,
    invoices: List<Invoice>,
    purchases: List<Purchase>,
    parties: List<Party>,
    expenses: List<ExpenseRecord>,
    repairs: List<RepairJob>,
    shopDays: List<ShopDayRecord>,
    isOwner: Boolean
) {
    val palette = LocalNoorColors.current
    val scope = rememberCoroutineScope()
    val today = Formatters.todayIso()
    val isTa = settings.language == "ta"

    val todayRecord = shopDays.firstOrNull { it.date == today }
    val activeInvoices = invoices.filter { it.status == "active" }
    val todaySalesInvoices = activeInvoices.filter { it.date == today && it.docType == "invoice" }
    val todaySales = todaySalesInvoices.sumOf { it.grandTotal }
    val todayPurchases = purchases.filter { it.status == "active" && it.date == today && it.docType == "purchase" }.sumOf { it.totalAmount }
    val todayExpensesList = expenses.filter { it.date == today }
    val todayExpenses = todayExpensesList.sumOf { it.amount }
    val monthSales = activeInvoices.filter { it.docType == "invoice" && it.date.take(7) == today.take(7) }.sumOf { it.grandTotal }
    val monthBills = activeInvoices.count { it.docType == "invoice" && it.date.take(7) == today.take(7) }

    val openingCashToday = remember(invoices, purchases, expenses, repairs, settings.openingCashBalance, today) {
        vm.repo.computeCashInHand(beforeDateExclusive = today)
    }
    val cashInHand = remember(invoices, purchases, expenses, repairs, settings.openingCashBalance) {
        vm.repo.computeCashInHand()
    }
    val bankBalance = remember(invoices, purchases, expenses, vm.repo.bankAccounts.collectAsState().value) {
        vm.repo.computeBankBalance()
    }

    val partySummaries = remember(parties, invoices, purchases, repairs) {
        parties.map { vm.repo.computePartySummary(it) }
    }
    val dueCustomers = partySummaries.filter { it.receivableBalance > 0.0 }
    val totalToCollect = dueCustomers.sumOf { it.receivableBalance }
    val totalToPay = partySummaries.sumOf { it.payableBalance }

    val inShopRepairs = repairs.count { it.status != "Delivered" }
    val readyRepairs = repairs.count { it.status == "Ready for Pickup" }
    val lowStockItems = items.filter { !it.isService && it.pieces <= it.lowStockAlert }

    // Today's net profit (owner only)
    val todayCogs = todaySalesInvoices.sumOf { inv ->
        inv.items.sumOf { it.costPrice * it.qty } + (if (inv.partSourceMode == "outside") inv.outsidePartCost else 0.0)
    }
    val todayNetProfit = todaySales - todayCogs - todayExpenses

    var trendMode by remember { mutableStateOf("Weekly") }
    val duesReminderDue = settings.duesReminderFreq != "off" && dueCustomers.isNotEmpty()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1) Shop Day Strip Card
        val (dotColor, mainTitle, subTitle, btnLabel, btnAction) = when {
            todayRecord?.isLeave == true -> listOf(
                palette.textMuted,
                "Shop Leave today",
                "Shop is closed for the day",
                "View",
                { vm.setShowShopDaySheet(true) }
            )
            todayRecord?.closedAt != null -> listOf(
                palette.statusPink,
                "Day closed · ${Formatters.formatTime(todayRecord.closedAt)}",
                "Sales ${Formatters.formatInr(todaySales)} · ${todaySalesInvoices.size} bills",
                "View",
                { vm.setShowShopDaySheet(true) }
            )
            todayRecord?.openedAt != null -> listOf(
                palette.statusGreen,
                "Shop open since ${Formatters.formatTime(todayRecord.openedAt)}",
                "Tap to close shop / close day",
                "Close",
                { vm.setShowShopDaySheet(true) }
            )
            else -> listOf(
                palette.statusAmber,
                "Shop Day",
                "Tap to record opening time",
                "Open",
                {
                    scope.launch {
                        vm.repo.saveShopDay(ShopDayRecord(date = today, openedAt = System.currentTimeMillis()))
                    }
                }
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 64.dp)
                .noorCard(palette, radius = 20.dp, highlighted = true)
                .clickable { vm.setShowShopDaySheet(true) }
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .testTag("home_shop_day_card"),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NoorStatusDot(color = dotColor as Color)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(mainTitle as String, color = palette.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.5.sp)
                Text(subTitle as String, color = palette.textSecondary, fontSize = 12.sp)
                Text(
                    "Opening balance ${Formatters.formatInr(openingCashToday)}",
                    color = palette.statusPink,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            OutlinedButton(
                onClick = { (btnAction as () -> Unit)() },
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(btnLabel as String, color = palette.accent, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
            }
        }

        // 2) Pending Dues Card (if any customer owes money)
        if (dueCustomers.isNotEmpty()) {
            val inf = rememberInfiniteTransition(label = "duesPulse")
            val pulseColor by inf.animateColor(
                initialValue = palette.accent.copy(alpha = 0.5f),
                targetValue = palette.statusAmber,
                animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
                label = "pulseBorder"
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(palette.card)
                    .border(
                        1.dp,
                        if (duesReminderDue) pulseColor else palette.border,
                        RoundedCornerShape(16.dp)
                    )
                    .clickable { vm.setShowPendingDuesSheet(true) }
                    .padding(14.dp)
                    .testTag("home_pending_dues_card"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.NotificationsActive, contentDescription = null, tint = palette.statusAmber)
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (duesReminderDue) "Reminder: Pending dues ${Formatters.formatInr(totalToCollect)}"
                        else "Pending dues ${Formatters.formatInr(totalToCollect)}",
                        color = palette.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text("${dueCustomers.size} customers owe balance", color = palette.textSecondary, fontSize = 12.sp)
                }
                TextButton(onClick = { vm.setShowPendingDuesSheet(true) }) {
                    Text("View", color = palette.accent, fontWeight = FontWeight.Bold)
                }
            }
        }

        // 3) Hero Button: Instant bill & receipt
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(12.dp, RoundedCornerShape(18.dp), ambientColor = palette.accent, spotColor = palette.accent)
                .clip(RoundedCornerShape(18.dp))
                .background(palette.primaryGradient)
                .clickable {
                    vm.resetSaleDraft("product")
                    vm.navigateTo(NavSection.BILLING)
                }
                .padding(horizontal = 18.dp, vertical = 18.dp)
                .testTag("home_instant_bill_hero")
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text(
                        if (isTa) "புதிய பில் · Instant bill & receipt" else "Instant bill & receipt",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp
                    )
                    Text(
                        "Create GST/Non-GST bill, share PDF on WhatsApp & print UPI QR",
                        color = Color.White.copy(alpha = 0.88f),
                        fontSize = 12.sp
                    )
                }
                Icon(Icons.Outlined.ReceiptLong, contentDescription = "Instant bill", tint = Color.White, modifier = Modifier.size(30.dp))
            }
        }

        // 4) Action Tiles Grid
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            ActionTileCard(
                title = if (isTa) "புதிய ரிப்பேர் · Add Service" else "Add Service",
                sub = "Log phone & spare part",
                icon = Icons.Outlined.Build,
                onClick = { vm.resetRepairDraft(); vm.navigateTo(NavSection.REPAIRS) },
                modifier = Modifier.weight(1f)
            )
            ActionTileCard(
                title = "Add Stock",
                sub = "Add SKU or update qty",
                icon = Icons.Outlined.Inventory2,
                onClick = { vm.navigateTo(NavSection.ITEMS) },
                modifier = Modifier.weight(1f)
            )
        }

        val quickTiles = listOf(
            Triple(if (isTa) "புதிய பில் · New Sale" else "New Sale", "Bill & GST", Icons.Outlined.ReceiptLong to { vm.resetSaleDraft(); vm.navigateTo(NavSection.BILLING) }),
            Triple("Add Stock", "Bulk Stock In", Icons.Outlined.LocalShipping to { vm.resetPurchaseDraft(); vm.navigateTo(NavSection.PURCHASES) }),
            Triple("Service", "New Phone", Icons.Outlined.PhoneAndroid to { vm.navigateTo(NavSection.REPAIRS) }),
            Triple(if (isTa) "செலவு · Expenses" else "Expenses", "Shop Overhead", Icons.Outlined.CurrencyRupee to { vm.navigateTo(NavSection.EXPENSES) }),
            Triple("Parties", "${parties.size} Contacts", Icons.Outlined.People to { vm.navigateTo(NavSection.PARTIES) }),
            Triple("Reports", "P&L & GST", Icons.Outlined.BarChart to { vm.navigateTo(NavSection.REPORTS) }),
            Triple("Stock", "New SKU / Item", Icons.Outlined.AddBox to { vm.navigateTo(NavSection.ITEMS) }),
            Triple("Service Status", "$inShopRepairs In Shop · $readyRepairs Ready", Icons.Outlined.CheckCircleOutline to { vm.navigateTo(NavSection.SERVICE_STATUS) })
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            quickTiles.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    pair.forEach { (title, sub, iconAction) ->
                        ActionTileCard(
                            title = title,
                            sub = sub,
                            icon = iconAction.first,
                            onClick = iconAction.second,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // 5) Daily Business Snapshot
        Column(modifier = Modifier.fillMaxWidth().noorCard(palette).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Daily Business Snapshot", color = palette.textPrimary, fontWeight = FontWeight.Black, fontSize = 15.5.sp)
                Text(
                    "Day Book →",
                    color = palette.accent,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    modifier = Modifier.clickable { vm.openReportTab("Day Book") }
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                SnapshotStat(
                    label = if (isTa) "இன்றைய விற்பனை" else "Total Sales Today",
                    value = Formatters.formatInr(todaySales),
                    modifier = Modifier.weight(1f)
                )
                SnapshotStat(
                    label = "Invoices Generated",
                    value = todaySalesInvoices.size.toString(),
                    modifier = Modifier.weight(1f)
                )
                SnapshotStat(
                    label = if (isTa) "இன்றைய செலவுகள்" else "Expenses Recorded",
                    value = Formatters.formatInr(todayExpenses),
                    modifier = Modifier.weight(1f)
                )
            }
            if (isOwner) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    SnapshotStat(
                        label = if (isTa) "இன்றைய கொள்முதல்" else "Purchases Today",
                        value = Formatters.formatInr(todayPurchases),
                        modifier = Modifier.weight(1f)
                    )
                    SnapshotStat(
                        label = if (isTa) "இன்றைய நிகர லாபம்" else "Today's Net Profit",
                        value = Formatters.formatInr(todayNetProfit),
                        valueColor = if (todayNetProfit >= 0) palette.statusGreen else palette.statusRed,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Text(
                "$monthBills bills · Month ${Formatters.formatInr(monthSales)}",
                color = palette.textSecondary,
                fontSize = 12.sp
            )
            Text(
                "Opening cash (drawer) ${Formatters.formatInr(openingCashToday)}",
                color = palette.accent,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        // 6) Workspace Cards (Cash/Bank, Active Services, Pending Amounts, Quick Workspace, Service Queue, Supplier Payables, Trend Chart, Inventory)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .noorCard(palette)
                    .clickable { vm.navigateTo(NavSection.CASH_BANK) }
                    .padding(12.dp)
            ) {
                Text(if (isTa) "கையிருப்பு பணம்" else "Cash in Hand", color = palette.textSecondary, fontSize = 11.5.sp)
                Text(Formatters.formatInr(cashInHand), color = palette.textPrimary, fontWeight = FontWeight.Black, fontSize = 16.sp)
                Spacer(Modifier.height(4.dp))
                Text(if (isTa) "வங்கி இருப்பு" else "Bank Balance", color = palette.textSecondary, fontSize = 11.5.sp)
                Text(Formatters.formatInr(bankBalance), color = palette.statusGreen, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .noorCard(palette)
                    .clickable { vm.navigateTo(NavSection.PARTIES) }
                    .padding(12.dp)
            ) {
                Text("Pending Amounts", color = palette.textSecondary, fontSize = 11.5.sp)
                Text(if (isTa) "வரவேண்டிய தொகை" else "To Collect", color = palette.textMuted, fontSize = 10.5.sp)
                Text(Formatters.formatInr(totalToCollect), color = palette.accent, fontWeight = FontWeight.Black, fontSize = 16.sp)
                Spacer(Modifier.height(4.dp))
                Text(if (isTa) "செலுத்த வேண்டியது" else "Supplier Payables →", color = palette.textSecondary, fontSize = 11.5.sp)
                Text(Formatters.formatInr(totalToPay), color = palette.statusAmber, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .noorCard(palette)
                    .clickable { vm.navigateTo(NavSection.SERVICE_STATUS) }
                    .padding(12.dp)
            ) {
                Text("Active Services", color = palette.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text("$inShopRepairs in shop · $readyRepairs ready", color = palette.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(Modifier.height(4.dp))
                Text("Service Queue", color = palette.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .noorCard(palette)
                    .clickable { vm.navigateTo(NavSection.SEARCH) }
                    .padding(12.dp)
            ) {
                Text("Quick Workspace", color = palette.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text("Find phone models, covers & glass", color = palette.textMuted, fontSize = 11.5.sp)
                Spacer(Modifier.height(4.dp))
                Text("Search Models →", color = palette.accent, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Sales Trend Chart (Weekly / Monthly)
        Column(modifier = Modifier.fillMaxWidth().noorCard(palette).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(if (isTa) "விற்பனை போக்கு · Sales Trend" else "Sales Trend", color = palette.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Weekly", "Monthly").forEach { mode ->
                        FilterChip(
                            selected = trendMode == mode,
                            onClick = { trendMode = mode },
                            label = { Text(mode, fontSize = 11.5.sp) }
                        )
                    }
                }
            }
            val bars = remember(activeInvoices, trendMode) {
                val daysBack = if (trendMode == "Weekly") 7 else 14
                val cal = java.util.Calendar.getInstance()
                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                (daysBack - 1 downTo 0).map { offset ->
                    val c = (cal.clone() as java.util.Calendar).apply { add(java.util.Calendar.DAY_OF_YEAR, -offset) }
                    val iso = sdf.format(c.time)
                    val total = activeInvoices.filter { it.docType == "invoice" && it.date == iso }.sumOf { it.grandTotal }
                    iso.takeLast(2) to total
                }
            }
            val maxBar = (bars.maxOfOrNull { it.second } ?: 1.0).coerceAtLeast(1.0)
            Canvas(modifier = Modifier.fillMaxWidth().height(90.dp)) {
                val count = bars.size.coerceAtLeast(1)
                val slotW = size.width / count
                val barW = (slotW * 0.58f).coerceAtMost(24f)
                bars.forEachIndexed { idx, (_, value) ->
                    val ratio = (value / maxBar).toFloat().coerceIn(0.05f, 1f)
                    val barH = size.height * ratio
                    val x = idx * slotW + (slotW - barW) / 2f
                    drawRoundRect(
                        color = if (value > 0) palette.accent else palette.border,
                        topLeft = Offset(x, size.height - barH),
                        size = Size(barW, barH),
                        cornerRadius = CornerRadius(6f, 6f)
                    )
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                bars.forEach { (dayNum, _) ->
                    Text(dayNum, color = palette.textMuted, fontSize = 10.sp)
                }
            }
        }

        // Inventory Card
        Row(
            modifier = Modifier.fillMaxWidth().noorCard(palette).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Inventory", color = palette.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text("Alert below quantity: ${settings.lowStockDefault} pcs", color = palette.textSecondary, fontSize = 12.sp)
                Text("${lowStockItems.size} items need restock", color = if (lowStockItems.isNotEmpty()) palette.statusAmber else palette.statusGreen, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedButton(onClick = { vm.navigateTo(NavSection.ITEMS) }, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) {
                    Text("Restock", color = palette.statusAmber, fontSize = 12.sp)
                }
                Text(
                    "Open Inventory →",
                    color = palette.accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { vm.navigateTo(NavSection.ITEMS) }
                )
            }
        }

        // 7) Recent Transactions
        Column(modifier = Modifier.fillMaxWidth().noorCard(palette).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Recent Transactions", color = palette.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.5.sp)
            val recent = remember(invoices, purchases, expenses) {
                buildList {
                    invoices.take(8).forEach { inv ->
                        add(Triple(inv.createdAt, "${inv.invoiceNumber} · ${inv.customerName.ifBlank { "Walk-in" }}", "${inv.paymentMode}${if (inv.docType == "return") " · Return" else ""}" to inv.grandTotal))
                    }
                    purchases.take(5).forEach { p ->
                        add(Triple(p.createdAt, "${p.billNumber} · ${p.supplierName.ifBlank { "Supplier" }}", p.paymentMode to -p.totalAmount))
                    }
                    expenses.take(5).forEach { e ->
                        add(Triple(e.createdAt, "${e.category} · ${e.reason}", e.paymentMode to -e.amount))
                    }
                }.sortedByDescending { it.first }.take(10)
            }
            if (recent.isEmpty()) {
                Text("No bills or expenses recorded yet.", color = palette.textSecondary, fontSize = 13.sp)
            } else {
                recent.forEach { (_, title, subAmt) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(title, color = palette.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(subAmt.first, color = palette.textMuted, fontSize = 11.5.sp)
                        }
                        val amt = subAmt.second
                        Text(
                            Formatters.formatInr(amt),
                            color = if (amt >= 0) palette.statusGreen else palette.statusRed,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp
                        )
                    }
                    HorizontalDivider(color = palette.border.copy(alpha = 0.4f))
                }
            }
        }

        // 8) Empty-shop help
        if (items.isEmpty() && invoices.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth().noorCard(palette, highlighted = true).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Start by adding your mobile covers, tempered glass, or repair services — or load sample shop data to explore immediately.",
                    color = palette.textSecondary,
                    fontSize = 13.sp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NoorPrimaryButton(
                        text = "Load Sample Data",
                        onClick = { scope.launch { vm.repo.loadSampleData(isOwner) } },
                        modifier = Modifier.weight(1f)
                    )
                    NoorSecondaryButton(
                        text = "Add Stock",
                        onClick = { vm.navigateTo(NavSection.ITEMS) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Spacer(Modifier.height(96.dp))
    }
}

@Composable
private fun ActionTileCard(
    title: String,
    sub: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalNoorColors.current
    Row(
        modifier = modifier
            .noorCard(palette, radius = 14.dp)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = palette.accent, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = palette.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, color = palette.textMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SnapshotStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color? = null
) {
    val palette = LocalNoorColors.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(palette.inputBg)
            .padding(10.dp)
    ) {
        Text(label, color = palette.textMuted, fontSize = 10.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(3.dp))
        Text(value, color = valueColor ?: palette.textPrimary, fontWeight = FontWeight.Black, fontSize = 14.sp)
    }
}
