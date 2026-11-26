package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.*
import com.example.ui.MainViewModel
import com.example.ui.components.NoorSectionTitle
import com.example.ui.theme.LocalNoorColors
import com.example.ui.theme.noorCard
import com.example.util.Formatters
import com.example.util.PdfAndPrintHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun ReportsScreen(
    vm: MainViewModel,
    settings: ShopSettings,
    items: List<StockItem>,
    invoices: List<Invoice>,
    purchases: List<Purchase>,
    parties: List<Party>,
    payments: List<PaymentRecord>,
    expenses: List<ExpenseRecord>,
    repairs: List<RepairJob>,
    bankAccounts: List<BankAccount>,
    cashBankTxns: List<CashBankTxn>,
    isOwner: Boolean
) {
    val palette = LocalNoorColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val selectedTabState by vm.selectedReportTab.collectAsStateWithLifecycle()
    val analytics by vm.repo.analytics.collectAsStateWithLifecycle()

    val allTabs = remember {
        listOf(
            "Profit & Loss (Private)",
            "Item-Wise Profit",
            "Sales",
            "Purchases",
            "Part Price History",
            "GST Report",
            "Day Book",
            "Stock Summary",
            "Top Selling & Buy Again",
            "Party Statements",
            "Balance Sheet"
        )
    }
    val visibleTabs = remember(isOwner, allTabs) {
        if (isOwner) allTabs
        else allTabs.filterNot {
            it == "Profit & Loss (Private)" || it == "Item-Wise Profit" || it == "Part Price History"
        }
    }
    val currentTab = if (selectedTabState in visibleTabs) selectedTabState else visibleTabs.first()

    var period by remember { mutableStateOf("This Month") }
    var customFrom by remember { mutableStateOf("") }
    var customTo by remember { mutableStateOf("") }
    var selectedPartyId by remember(parties) { mutableStateOf(parties.firstOrNull()?.id) }

    val periodInvoices = remember(invoices, period, customFrom, customTo) {
        invoices.filter { it.status == "active" && Formatters.isDateInPeriod(it.date, period, customFrom, customTo) }
    }
    val periodSales = remember(periodInvoices) { periodInvoices.filter { it.docType == "invoice" } }
    val periodPurchases = remember(purchases, period, customFrom, customTo) {
        purchases.filter { it.status == "active" && Formatters.isDateInPeriod(it.date, period, customFrom, customTo) }
    }
    val periodExpenses = remember(expenses, period, customFrom, customTo) {
        expenses.filter { Formatters.isDateInPeriod(it.date, period, customFrom, customTo) }
    }

    fun exportReportPdf(share: Boolean) {
        scope.launch {
            val pdf = withContext(Dispatchers.IO) {
                val lines = mutableListOf<String>()
                lines.add("Report: $currentTab | Period: $period")
                lines.add("------------------------------------------------------")
                when (currentTab) {
                    "Profit & Loss (Private)" -> {
                        val rev = periodSales.sumOf { it.grandTotal }
                        val cogs = periodSales.sumOf { inv -> inv.items.sumOf { it.costPrice * it.qty } + (if (inv.partSourceMode == "outside") inv.outsidePartCost else 0.0) }
                        val exp = periodExpenses.sumOf { it.amount }
                        lines.add("Sales Revenue: ${Formatters.formatInr(rev)}")
                        lines.add("Item Cost (COGS): ${Formatters.formatInr(cogs)}")
                        lines.add("Expenses: ${Formatters.formatInr(exp)}")
                        lines.add("Net Shop Profit: ${Formatters.formatInr(rev - cogs - exp)}")
                    }
                    "Sales" -> {
                        periodSales.forEach { lines.add("${it.date} | ${it.invoiceNumber} | ${it.customerName.ifBlank { "Walk-in" }} | ${Formatters.formatInr(it.grandTotal)}") }
                    }
                    "Purchases" -> {
                        periodPurchases.forEach { lines.add("${it.date} | ${it.billNumber} | ${it.supplierName} | ${Formatters.formatInr(it.totalAmount)}") }
                    }
                    else -> {
                        periodSales.take(25).forEach { lines.add("${it.date} | ${it.invoiceNumber} | ${Formatters.formatInr(it.grandTotal)}") }
                        periodExpenses.take(25).forEach { lines.add("${it.date} | Expense: ${it.reason} | ${Formatters.formatInr(it.amount)}") }
                    }
                }
                PdfAndPrintHelper.generateTextReportPdf(context, currentTab, "Period: $period", lines, settings)
            }
            if (pdf != null) {
                if (share) PdfAndPrintHelper.sharePdfFile(context, pdf, "$currentTab ($period)")
                else PdfAndPrintHelper.printPdfFile(context, pdf, currentTab)
            }
        }
    }

    // Tab-scoped memoized datasets
    val plMetrics = remember(currentTab, periodSales, periodExpenses, invoices, expenses) {
        if (currentTab != "Profit & Loss (Private)") null
        else {
            val salesRevenue = periodSales.sumOf { it.grandTotal }
            val cogs = periodSales.sumOf { inv ->
                inv.items.sumOf { it.costPrice * it.qty } + (if (inv.partSourceMode == "outside") inv.outsidePartCost else 0.0)
            }
            val expTotal = periodExpenses.sumOf { it.amount }
            val netProfit = salesRevenue - cogs - expTotal

            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val cal = Calendar.getInstance()
            val last30Dates = (29 downTo 0).map { off ->
                cal.timeInMillis = System.currentTimeMillis()
                cal.add(Calendar.DAY_OF_YEAR, -off)
                sdf.format(cal.time)
            }
            val last30Set = last30Dates.toHashSet()
            val salesByDate = HashMap<String, Double>(32)
            val costByDate = HashMap<String, Double>(32)
            var sales30 = 0.0
            var cost30 = 0.0
            invoices.forEach { inv ->
                if (inv.status == "active" && inv.docType == "invoice") {
                    val d = inv.date.take(10)
                    if (d in last30Set) {
                        val c = inv.items.sumOf { it.costPrice * it.qty } + (if (inv.partSourceMode == "outside") inv.outsidePartCost else 0.0)
                        val cNoOutside = inv.items.sumOf { it.costPrice * it.qty }
                        sales30 += inv.grandTotal
                        cost30 += c
                        salesByDate[d] = (salesByDate[d] ?: 0.0) + inv.grandTotal
                        costByDate[d] = (costByDate[d] ?: 0.0) + cNoOutside
                    }
                }
            }
            val expByDate = HashMap<String, Double>(32)
            var exp30 = 0.0
            expenses.forEach { e ->
                val d = e.date.take(10)
                if (d in last30Set) {
                    exp30 += e.amount
                    expByDate[d] = (expByDate[d] ?: 0.0) + e.amount
                }
            }
            val net30 = sales30 - cost30 - exp30
            val dailyNets = last30Dates.map { d ->
                (salesByDate[d] ?: 0.0) - (costByDate[d] ?: 0.0) - (expByDate[d] ?: 0.0)
            }
            listOf(salesRevenue, cogs, expTotal, netProfit, sales30, cost30, exp30, net30, dailyNets)
        }
    }

    val dayBookEntries = remember(currentTab, periodInvoices, periodPurchases, periodExpenses, payments, period, customFrom, customTo) {
        if (currentTab != "Day Book") emptyList()
        else buildList {
            periodInvoices.forEach { add(Triple(it.date, "${it.invoiceNumber} · ${it.customerName.ifBlank { "Sale" }} (${it.paymentMode})", "+${Formatters.formatInr(it.grandTotal)}")) }
            periodPurchases.forEach { add(Triple(it.date, "${it.billNumber} · ${pLabel(it)} (${it.paymentMode})", "-${Formatters.formatInr(it.totalAmount)}")) }
            periodExpenses.forEach { add(Triple(it.date, "Expense: ${it.reason} (${it.paymentMode})", "-${Formatters.formatInr(it.amount)}")) }
            payments.filter { Formatters.isDateInPeriod(it.date, period, customFrom, customTo) }.forEach {
                add(Triple(it.date, "Payment ${if (it.direction == "in") "In" else "Out"}: ${it.partyName}", Formatters.formatInr(it.amount)))
            }
        }.sortedByDescending { it.first }
    }

    val stockSummaryData = remember(currentTab, items) {
        if (currentTab != "Stock Summary") null
        else {
            val physical = items.filter { !it.isService }
            val totalPieces = physical.sumOf { it.pieces }
            val costValue = physical.sumOf { it.costPrice * it.pieces }
            val retailValue = physical.sumOf { it.sellingPrice * it.pieces }
            val byCategory = physical.groupBy { it.category.ifBlank { "Uncategorized" } }
                .map { (cat, list) -> Triple(cat, list.size, list.sumOf { it.pieces }) }
            val lowList = physical.filter { it.pieces <= it.lowStockAlert }
            listOf(totalPieces, costValue, retailValue, byCategory, lowList)
        }
    }

    val topSellingData: Pair<List<Pair<String, Int>>, List<StockItem>> = remember(currentTab, items, invoices, analytics.soldStatsByItemId) {
        if (currentTab != "Top Selling & Buy Again") emptyList<Pair<String, Int>>() to emptyList<StockItem>()
        else {
            val soldCounts = mutableMapOf<String, Pair<String, Int>>()
            invoices.forEach { inv ->
                if (inv.status == "active" && inv.docType == "invoice") {
                    inv.items.forEach { line ->
                        val k = line.itemId ?: line.name
                        val cur = soldCounts[k]
                        soldCounts[k] = (line.name.ifBlank { k }) to ((cur?.second ?: 0) + line.qty)
                    }
                }
            }
            val top = soldCounts.entries.sortedByDescending { it.value.second }.take(15).map { it.value }
            val buyAgain = items.filter {
                !it.isService && it.pieces <= it.lowStockAlert && (analytics.soldStatsByItemId[it.id]?.soldQty ?: 0) > 0
            }
            top to buyAgain
        }
    }

    val partyStatementData = remember(currentTab, parties, selectedPartyId, periodInvoices, periodPurchases, payments, analytics) {
        if (currentTab != "Party Statements" || parties.isEmpty()) null
        else {
            val party = parties.firstOrNull { it.id == selectedPartyId } ?: parties.first()
            val summary = analytics.partySummaryById[party.id] ?: vm.repo.computePartySummary(party)
            val pInv = periodInvoices.filter { it.customerId == party.id || it.customerName.equals(party.name, true) }
            val pPur = periodPurchases.filter { it.supplierId == party.id || it.supplierName.equals(party.name, true) }
            val pPay = payments.filter { it.partyId == party.id || it.partyName.equals(party.name, true) }
            listOf(party, summary, pInv, pPur, pPay)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "header") {
            NoorSectionTitle(
                title = "Reports",
                subtitle = "Shop analytics, private profit, GST slabs, stock valuation & balance sheet",
                trailing = {
                    Row {
                        IconButton(onClick = { exportReportPdf(share = false) }) {
                            Icon(Icons.Outlined.Print, contentDescription = "Print Report", tint = palette.textPrimary)
                        }
                        IconButton(onClick = { exportReportPdf(share = true) }) {
                            Icon(Icons.Outlined.Share, contentDescription = "Share Report PDF", tint = palette.accent)
                        }
                    }
                }
            )
        }

        // Report Picker Tabs
        item(key = "tabs") {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                visibleTabs.forEach { tab ->
                    val sel = currentTab == tab
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (sel) palette.accent else palette.inputBg)
                            .border(1.dp, if (sel) palette.accent else palette.border, RoundedCornerShape(12.dp))
                            .clickable { vm.openReportTab(tab) }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(tab, color = if (sel) Color.White else palette.textPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }

        // Period Selector
        if (currentTab != "Part Price History" && currentTab != "Stock Summary" && currentTab != "Balance Sheet") {
            item(key = "period") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Today", "This Week", "This Month", "This Year", "Custom").forEach { p ->
                            FilterChip(selected = period == p, onClick = { period = p }, label = { Text(p, fontSize = 11.5.sp) })
                        }
                    }
                    if (period == "Custom") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(customFrom, { customFrom = it }, label = { Text("From (YYYY-MM-DD)") }, singleLine = true, modifier = Modifier.weight(1f))
                            OutlinedTextField(customTo, { customTo = it }, label = { Text("To (YYYY-MM-DD)") }, singleLine = true, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        when (currentTab) {
            "Profit & Loss (Private)" -> {
                item(key = "pl_content") {
                    val m = plMetrics!!
                    val salesRevenue = m[0] as Double
                    val cogs = m[1] as Double
                    val netProfit = m[3] as Double
                    val sales30 = m[4] as Double
                    val cost30 = m[5] as Double
                    val exp30 = m[6] as Double
                    val net30 = m[7] as Double
                    @Suppress("UNCHECKED_CAST")
                    val dailyNets = m[8] as List<Double>

                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(palette.accent.copy(alpha = 0.14f))
                                .border(1.dp, palette.accent.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        ) {
                            Text(
                                "Shop-Only Private View: Cost prices and profits are never printed on customer invoices.",
                                color = palette.textPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.5.sp
                            )
                        }

                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ReportCard("Sales Revenue", Formatters.formatInr(salesRevenue), palette.textPrimary, Modifier.weight(1f))
                            ReportCard("Item Cost (COGS)", Formatters.formatInr(cogs), palette.statusAmber, Modifier.weight(1f))
                        }
                        Column(modifier = Modifier.fillMaxWidth().noorCard(palette, highlighted = true).padding(14.dp)) {
                            Text("Net Shop Profit", color = palette.textSecondary, fontSize = 12.5.sp)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                Formatters.formatInr(netProfit),
                                color = if (netProfit >= 0) palette.statusGreen else palette.statusRed,
                                fontWeight = FontWeight.Black,
                                fontSize = 22.sp
                            )
                            Text(
                                "Net daily profit calculated from active sale invoices minus item costs and daily expenses.",
                                color = palette.textMuted,
                                fontSize = 11.5.sp
                            )
                        }

                        Column(modifier = Modifier.fillMaxWidth().noorCard(palette).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("30-Day Net: ${Formatters.formatInr(net30)}", color = palette.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text(
                                "Sales: ${Formatters.formatInr(sales30)} · Item Cost: ${Formatters.formatInr(cost30)} · Expenses: ${Formatters.formatInr(exp30)} · Net Profit: ${Formatters.formatInr(net30)}",
                                color = palette.textSecondary,
                                fontSize = 11.5.sp
                            )
                            if (dailyNets.all { it == 0.0 }) {
                                Text("Record sales or expenses to see daily net profit peaks across the 30-day timeline.", color = palette.textSecondary, fontSize = 12.5.sp)
                            } else {
                                val maxAbs = remember(dailyNets) { dailyNets.maxOf { kotlin.math.abs(it) }.coerceAtLeast(100.0) }
                                val accent = palette.accent
                                val red = palette.statusRed
                                Canvas(modifier = Modifier.fillMaxWidth().height(90.dp)) {
                                    val barW = size.width / dailyNets.size
                                    val midY = size.height * 0.65f
                                    dailyNets.forEachIndexed { i, v ->
                                        val barH = (kotlin.math.abs(v) / maxAbs * size.height * 0.55).toFloat().coerceAtLeast(3f)
                                        val top = if (v >= 0) midY - barH else midY
                                        drawRect(
                                            color = if (v >= 0) accent else red,
                                            topLeft = Offset(i * barW + 2f, top),
                                            size = Size((barW - 4f).coerceAtLeast(2f), barH)
                                        )
                                    }
                                }
                            }
                        }
                        if (periodSales.isEmpty()) {
                            Text("No active invoices in this period.", color = palette.textSecondary, fontSize = 13.sp)
                        }
                    }
                }
            }

            "Item-Wise Profit" -> {
                if (periodSales.isEmpty()) {
                    item(key = "iwp_empty") {
                        Box(Modifier.fillMaxWidth().noorCard(palette).padding(20.dp), contentAlignment = Alignment.Center) {
                            Text("No items sold in this period.", color = palette.textSecondary)
                        }
                    }
                } else {
                    item(key = "iwp_header") {
                        Column(Modifier.fillMaxWidth().noorCard(palette).padding(horizontal = 14.dp, vertical = 10.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Invoice", color = palette.textMuted, fontWeight = FontWeight.Bold, fontSize = 11.5.sp, modifier = Modifier.weight(1.3f))
                                Text("Sell Total", color = palette.textMuted, fontWeight = FontWeight.Bold, fontSize = 11.5.sp, modifier = Modifier.weight(0.9f))
                                Text("Cost", color = palette.textMuted, fontWeight = FontWeight.Bold, fontSize = 11.5.sp, modifier = Modifier.weight(0.8f))
                                Text("Profit", color = palette.textMuted, fontWeight = FontWeight.Bold, fontSize = 11.5.sp, modifier = Modifier.weight(0.8f))
                            }
                        }
                    }
                    items(periodSales, key = { "iwp_${it.id}" }) { inv ->
                        val cost = inv.items.sumOf { it.costPrice * it.qty } + (if (inv.partSourceMode == "outside") inv.outsidePartCost else 0.0)
                        val prof = inv.grandTotal - cost
                        Column(Modifier.fillMaxWidth().noorCard(palette, radius = 12.dp).padding(horizontal = 14.dp, vertical = 10.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${inv.invoiceNumber}\n${inv.customerName.ifBlank { "Walk-in" }}", color = palette.textPrimary, fontSize = 11.5.sp, modifier = Modifier.weight(1.3f))
                                Text(Formatters.formatInr(inv.grandTotal), color = palette.textPrimary, fontSize = 11.5.sp, modifier = Modifier.weight(0.9f))
                                Text(Formatters.formatInr(cost), color = palette.textSecondary, fontSize = 11.5.sp, modifier = Modifier.weight(0.8f))
                                Text(Formatters.formatInr(prof), color = if (prof >= 0) palette.statusGreen else palette.statusRed, fontWeight = FontWeight.Bold, fontSize = 11.5.sp, modifier = Modifier.weight(0.8f))
                            }
                        }
                    }
                }
            }

            "Sales" -> {
                item(key = "sales_rev") {
                    val rev = remember(periodSales) { periodSales.sumOf { it.grandTotal } }
                    ReportCard("Revenue", Formatters.formatInr(rev), palette.accent, Modifier.fillMaxWidth())
                }
                if (periodSales.isEmpty()) {
                    item(key = "sales_empty") {
                        Box(Modifier.fillMaxWidth().noorCard(palette).padding(20.dp), contentAlignment = Alignment.Center) {
                            Text("No sales in this period.", color = palette.textSecondary)
                        }
                    }
                } else {
                    items(periodSales, key = { "rs_${it.id}" }) { inv ->
                        Row(
                            Modifier.fillMaxWidth().noorCard(palette, radius = 12.dp).padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("${inv.invoiceNumber} · ${inv.customerName.ifBlank { "Walk-in" }} (${Formatters.formatDisplayDate(inv.date)})", color = palette.textPrimary, fontSize = 12.sp, modifier = Modifier.weight(1f))
                            Text(Formatters.formatInr(inv.grandTotal), color = palette.accent, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                        }
                    }
                }
            }

            "Purchases" -> {
                if (periodPurchases.isEmpty()) {
                    item(key = "pur_empty") {
                        Box(Modifier.fillMaxWidth().noorCard(palette).padding(20.dp), contentAlignment = Alignment.Center) {
                            Text("No purchases in this period.", color = palette.textSecondary)
                        }
                    }
                } else {
                    items(periodPurchases, key = { "rp_${it.id}" }) { p ->
                        Row(
                            Modifier.fillMaxWidth().noorCard(palette, radius = 12.dp).padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("${p.billNumber} · ${p.supplierName} (${Formatters.formatDisplayDate(p.date)})", color = palette.textPrimary, fontSize = 12.sp, modifier = Modifier.weight(1f))
                            Text(Formatters.formatInr(p.totalAmount), color = palette.textPrimary, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                        }
                    }
                }
                item(key = "pur_exp_hdr") {
                    Column(Modifier.fillMaxWidth().noorCard(palette).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Expense Entries", color = palette.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        if (periodExpenses.isEmpty()) {
                            Text("No expenses recorded in this period.", color = palette.textSecondary, fontSize = 12.5.sp)
                        }
                    }
                }
                if (periodExpenses.isNotEmpty()) {
                    items(periodExpenses, key = { "rexp_${it.id}" }) { exp ->
                        Row(
                            Modifier.fillMaxWidth().noorCard(palette, radius = 12.dp).padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("${exp.category} · ${exp.reason}", color = palette.textPrimary, fontSize = 12.sp, modifier = Modifier.weight(1f))
                            Text(Formatters.formatInr(exp.amount), color = palette.statusRed, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }

            "Part Price History" -> {
                item(key = "part_price_hist") {
                    PartPriceHistoryTab(purchases = purchases, items = items)
                }
            }

            "GST Report" -> {
                if (periodSales.isEmpty()) {
                    item(key = "gst_empty") {
                        Box(Modifier.fillMaxWidth().noorCard(palette).padding(20.dp), contentAlignment = Alignment.Center) {
                            Text("No invoices in this period.", color = palette.textSecondary)
                        }
                    }
                } else {
                    item(key = "gst_content") {
                        val gstData = remember(periodSales) {
                            var taxTot = 0.0
                            var gstTot = 0.0
                            val bySlab = sortedMapOf<Double, Pair<Int, Pair<Double, Double>>>()
                            periodSales.forEach { inv ->
                                val disc = if (inv.discountType == "percent") inv.subtotal * inv.discountAmount / 100.0 else inv.discountAmount
                                val taxable = (inv.subtotal - disc).coerceAtLeast(0.0)
                                val tax = taxable * inv.gstRate / 100.0
                                taxTot += taxable
                                gstTot += tax
                                val cur = bySlab[inv.gstRate]
                                bySlab[inv.gstRate] = ((cur?.first ?: 0) + 1) to (((cur?.second?.first ?: 0.0) + taxable) to ((cur?.second?.second ?: 0.0) + tax))
                            }
                            Triple(taxTot, gstTot, bySlab)
                        }
                        val taxableTotal = gstData.first
                        val gstTotal = gstData.second
                        val bySlab = gstData.third
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ReportCard("Taxable Sales", Formatters.formatInr(taxableTotal), palette.textPrimary, Modifier.weight(1f))
                                ReportCard("Total GST Collected", Formatters.formatInr(gstTotal), palette.accent, Modifier.weight(1f))
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ReportCard("CGST", Formatters.formatInr(gstTotal / 2.0), palette.textSecondary, Modifier.weight(1f))
                                ReportCard("SGST", Formatters.formatInr(gstTotal / 2.0), palette.textSecondary, Modifier.weight(1f))
                            }
                            Column(Modifier.fillMaxWidth().noorCard(palette).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Tax Summary by GST Slab", color = palette.textPrimary, fontWeight = FontWeight.Bold)
                                bySlab.forEach { (slab, info) ->
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("${slab.toInt()}% GST (${info.first} bills)", color = palette.textPrimary, fontSize = 12.5.sp)
                                        Text("Taxable ${Formatters.formatInr(info.second.first)} · Tax ${Formatters.formatInr(info.second.second)}", color = palette.accent, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            "Day Book" -> {
                if (dayBookEntries.isEmpty()) {
                    item(key = "db_empty") {
                        Box(Modifier.fillMaxWidth().noorCard(palette).padding(20.dp), contentAlignment = Alignment.Center) {
                            Text("No ledger activity in this period.", color = palette.textSecondary)
                        }
                    }
                } else {
                    itemsIndexed(dayBookEntries, key = { idx, e -> "db_${idx}_${e.first}_${e.second}" }) { _, (d, title, amt) ->
                        Row(
                            Modifier.fillMaxWidth().noorCard(palette, radius = 12.dp).padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("${Formatters.formatDisplayDate(d)} · $title", color = palette.textPrimary, fontSize = 12.sp, modifier = Modifier.weight(1f))
                            Text(amt, color = if (amt.startsWith("+")) palette.statusGreen else palette.accent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }

            "Stock Summary" -> {
                val sd = stockSummaryData!!
                val totalPieces = sd[0] as Int
                val costValue = sd[1] as Double
                val retailValue = sd[2] as Double
                @Suppress("UNCHECKED_CAST")
                val byCategory = sd[3] as List<Triple<String, Int, Int>>
                @Suppress("UNCHECKED_CAST")
                val lowList = sd[4] as List<StockItem>

                item(key = "ss_cards") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ReportCard("Total SKUs", items.size.toString(), palette.textPrimary, Modifier.weight(1f))
                            ReportCard("Total Pieces", totalPieces.toString(), palette.accent, Modifier.weight(1f))
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (isOwner) {
                                ReportCard("Stock Cost Value", Formatters.formatInr(costValue), palette.statusAmber, Modifier.weight(1f))
                            }
                            ReportCard("Stock Retail Value", Formatters.formatInr(retailValue), palette.statusGreen, Modifier.weight(1f))
                        }
                        Column(Modifier.fillMaxWidth().noorCard(palette).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Stock by Category", color = palette.textPrimary, fontWeight = FontWeight.Bold)
                            byCategory.forEach { (cat, skuCount, pcs) ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("$cat ($skuCount SKUs)", color = palette.textPrimary, fontSize = 12.5.sp)
                                    Text("$pcs pcs", color = palette.accent, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        Column(Modifier.fillMaxWidth().noorCard(palette).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Low Stock Items (${lowList.size})", color = palette.statusAmber, fontWeight = FontWeight.Bold)
                            if (lowList.isEmpty()) {
                                Text("No low-stock items right now.", color = palette.textSecondary, fontSize = 12.5.sp)
                            }
                        }
                    }
                }
                if (lowList.isNotEmpty()) {
                    items(lowList, key = { "ss_low_${it.id}" }) { st ->
                        Row(
                            Modifier.fillMaxWidth().noorCard(palette, radius = 12.dp).padding(horizontal = 14.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(st.displayName, color = palette.textPrimary, fontSize = 12.5.sp)
                            Text("${st.pieces} pcs", color = palette.statusRed, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            "Top Selling & Buy Again" -> {
                val (top, buyAgain) = topSellingData
                item(key = "top_selling_card") {
                    Column(Modifier.fillMaxWidth().noorCard(palette).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Top Selling Items", color = palette.textPrimary, fontWeight = FontWeight.Bold)
                        if (top.isEmpty()) Text("No sales in this period.", color = palette.textSecondary)
                        top.forEach { (name, qty) ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(name, color = palette.textPrimary, fontSize = 12.5.sp)
                                Text("$qty sold", color = palette.accent, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                item(key = "buy_again_hdr") {
                    Column(Modifier.fillMaxWidth().noorCard(palette).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Items to Buy Again (Fast Sellers Running Low)", color = palette.statusAmber, fontWeight = FontWeight.Bold)
                        if (buyAgain.isEmpty()) {
                            Text("None of your top sellers are running low right now.", color = palette.textSecondary, fontSize = 12.5.sp)
                        }
                    }
                }
                if (buyAgain.isNotEmpty()) {
                    items(buyAgain, key = { "ba_${it.id}" }) { st ->
                        Row(
                            Modifier.fillMaxWidth().noorCard(palette, radius = 12.dp).padding(horizontal = 14.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(st.displayName, color = palette.textPrimary, fontSize = 12.5.sp)
                            Text("${st.pieces} left", color = palette.statusRed, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            "Party Statements" -> {
                val ps = partyStatementData
                if (ps == null) {
                    item(key = "ps_empty") {
                        Box(Modifier.fillMaxWidth().noorCard(palette).padding(20.dp), contentAlignment = Alignment.Center) {
                            Text("No parties available yet.", color = palette.textSecondary)
                        }
                    }
                } else {
                    val party = ps[0] as Party
                    val summary = ps[1] as com.example.data.repository.ShopRepository.PartyBalanceSummary
                    @Suppress("UNCHECKED_CAST")
                    val pInv = ps[2] as List<Invoice>
                    @Suppress("UNCHECKED_CAST")
                    val pPur = ps[3] as List<Purchase>
                    @Suppress("UNCHECKED_CAST")
                    val pPay = ps[4] as List<PaymentRecord>

                    item(key = "ps_picker") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Select Customer or Supplier", color = palette.textMuted, fontSize = 12.sp)
                            LazyRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(parties, key = { it.id }) { p ->
                                    FilterChip(selected = selectedPartyId == p.id, onClick = { selectedPartyId = p.id }, label = { Text(p.name, fontSize = 11.5.sp) })
                                }
                            }
                            Column(Modifier.fillMaxWidth().noorCard(palette).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("${party.name} · Net ${Formatters.formatInr(summary.netBalance)}", color = palette.accent, fontWeight = FontWeight.Bold)
                                if (pInv.isEmpty() && pPur.isEmpty() && pPay.isEmpty()) {
                                    Text("No entries for this party.", color = palette.textSecondary, fontSize = 12.5.sp)
                                }
                            }
                        }
                    }
                    items(pInv, key = { "ps_inv_${it.id}" }) {
                        Text("Sale ${it.invoiceNumber} · ${Formatters.formatDisplayDate(it.date)} · ${Formatters.formatInr(it.grandTotal)}", color = palette.textPrimary, fontSize = 12.sp, modifier = Modifier.fillMaxWidth().noorCard(palette, radius = 10.dp).padding(horizontal = 14.dp, vertical = 8.dp))
                    }
                    items(pPur, key = { "ps_pur_${it.id}" }) {
                        Text("Purchase ${it.billNumber} · ${Formatters.formatDisplayDate(it.date)} · ${Formatters.formatInr(it.totalAmount)}", color = palette.textPrimary, fontSize = 12.sp, modifier = Modifier.fillMaxWidth().noorCard(palette, radius = 10.dp).padding(horizontal = 14.dp, vertical = 8.dp))
                    }
                    items(pPay, key = { "ps_pay_${it.id}" }) {
                        Text("Payment ${if (it.direction == "in") "In" else "Out"} · ${Formatters.formatDisplayDate(it.date)} · ${Formatters.formatInr(it.amount)}", color = palette.statusGreen, fontSize = 12.sp, modifier = Modifier.fillMaxWidth().noorCard(palette, radius = 10.dp).padding(horizontal = 14.dp, vertical = 8.dp))
                    }
                }
            }

            "Balance Sheet" -> {
                item(key = "bs_content") {
                    val bs = remember(analytics, items, isOwner) {
                        val cashInHand = analytics.cashInHand
                        val bankBal = analytics.bankBalance
                        val stockCost = if (isOwner) items.filter { !it.isService }.sumOf { it.costPrice * it.pieces } else 0.0
                        val rec = analytics.partySummaries.sumOf { it.receivableBalance }
                        val pay = analytics.partySummaries.sumOf { it.payableBalance }
                        val totalAssets = cashInHand + bankBal + stockCost + rec
                        val netWorth = totalAssets - pay
                        listOf(cashInHand, bankBal, stockCost, rec, pay, totalAssets, netWorth)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.fillMaxWidth().noorCard(palette).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Assets (What Your Shop Owns)", color = palette.accent, fontWeight = FontWeight.Bold, fontSize = 14.5.sp)
                            BsRow("Cash in Hand", Formatters.formatInr(bs[0]))
                            BsRow("Bank Accounts Balance", Formatters.formatInr(bs[1]))
                            if (isOwner) {
                                BsRow("Closing Stock (At Cost Price)", Formatters.formatInr(bs[2]))
                            }
                            BsRow("Customer Receivables (To Collect)", Formatters.formatInr(bs[3]))
                            HorizontalDivider(color = palette.border)
                            BsRow("Total Assets", Formatters.formatInr(bs[5]), bold = true)
                        }

                        Column(Modifier.fillMaxWidth().noorCard(palette, highlighted = true).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Liabilities & Shop Net Worth", color = palette.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.5.sp)
                            BsRow("Supplier Payables (To Pay)", Formatters.formatInr(bs[4]))
                            HorizontalDivider(color = palette.border)
                            BsRow("Net Business Capital / Worth", Formatters.formatInr(bs[6]), bold = true)
                            Text(
                                "Net Worth = (Cash in Hand + Bank Balance + Stock at Cost + Receivables) − Supplier Payables.",
                                color = palette.textMuted,
                                fontSize = 11.5.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun pLabel(p: Purchase): String = p.supplierName.ifBlank { "Supplier" }

@Composable
private fun ReportCard(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    val palette = LocalNoorColors.current
    Column(modifier = modifier.noorCard(palette, radius = 14.dp).padding(12.dp)) {
        Text(label, color = palette.textSecondary, fontSize = 11.5.sp)
        Spacer(Modifier.height(4.dp))
        Text(value, color = color, fontWeight = FontWeight.Black, fontSize = 17.sp)
    }
}

@Composable
private fun BsRow(label: String, value: String, bold: Boolean = false) {
    val palette = LocalNoorColors.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = if (bold) palette.textPrimary else palette.textSecondary, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, fontSize = 13.sp)
        Text(value, color = if (bold) palette.accent else palette.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}
