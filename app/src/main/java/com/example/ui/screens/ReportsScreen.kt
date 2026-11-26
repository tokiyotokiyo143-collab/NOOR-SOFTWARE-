package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.example.data.model.*
import com.example.ui.MainViewModel
import com.example.ui.components.NoorSectionTitle
import com.example.ui.theme.LocalNoorColors
import com.example.ui.theme.noorCard
import com.example.util.Formatters
import com.example.util.PdfAndPrintHelper
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
    val selectedTabState by vm.selectedReportTab.collectAsState()

    val allTabs = listOf(
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
    val visibleTabs = remember(isOwner) {
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
        val pdf = PdfAndPrintHelper.generateTextReportPdf(context, currentTab, "Period: $period", lines, settings)
        if (pdf != null) {
            if (share) PdfAndPrintHelper.sharePdfFile(context, pdf, "$currentTab ($period)")
            else PdfAndPrintHelper.printPdfFile(context, pdf, currentTab)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
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

        // Report Picker Tabs
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

        // Period Selector
        if (currentTab != "Part Price History" && currentTab != "Stock Summary" && currentTab != "Balance Sheet") {
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

        when (currentTab) {
            "Profit & Loss (Private)" -> {
                val salesRevenue = periodSales.sumOf { it.grandTotal }
                val cogs = periodSales.sumOf { inv ->
                    inv.items.sumOf { it.costPrice * it.qty } + (if (inv.partSourceMode == "outside") inv.outsidePartCost else 0.0)
                }
                val expTotal = periodExpenses.sumOf { it.amount }
                val netProfit = salesRevenue - cogs - expTotal

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

                // 30-Day Net Chart
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                val last30Dates = (29 downTo 0).map { off ->
                    Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -off) }.time.let { sdf.format(it) }
                }
                val activeSalesAll = invoices.filter { it.status == "active" && it.docType == "invoice" }
                val sales30 = activeSalesAll.filter { it.date in last30Dates }.sumOf { it.grandTotal }
                val cost30 = activeSalesAll.filter { it.date in last30Dates }.sumOf { inv ->
                    inv.items.sumOf { it.costPrice * it.qty } + (if (inv.partSourceMode == "outside") inv.outsidePartCost else 0.0)
                }
                val exp30 = expenses.filter { it.date in last30Dates }.sumOf { it.amount }
                val net30 = sales30 - cost30 - exp30

                Column(modifier = Modifier.fillMaxWidth().noorCard(palette).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("30-Day Net: ${Formatters.formatInr(net30)}", color = palette.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(
                        "Sales: ${Formatters.formatInr(sales30)} · Item Cost: ${Formatters.formatInr(cost30)} · Expenses: ${Formatters.formatInr(exp30)} · Net Profit: ${Formatters.formatInr(net30)}",
                        color = palette.textSecondary,
                        fontSize = 11.5.sp
                    )
                    val dailyNets = last30Dates.map { d ->
                        val ds = activeSalesAll.filter { it.date == d }.sumOf { it.grandTotal }
                        val dc = activeSalesAll.filter { it.date == d }.sumOf { inv -> inv.items.sumOf { it.costPrice * it.qty } }
                        val de = expenses.filter { it.date == d }.sumOf { it.amount }
                        ds - dc - de
                    }
                    if (dailyNets.all { it == 0.0 }) {
                        Text("Record sales or expenses to see daily net profit peaks across the 30-day timeline.", color = palette.textSecondary, fontSize = 12.5.sp)
                    } else {
                        val maxAbs = dailyNets.maxOf { kotlin.math.abs(it) }.coerceAtLeast(100.0)
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

            "Item-Wise Profit" -> {
                if (periodSales.isEmpty()) {
                    Box(Modifier.fillMaxWidth().noorCard(palette).padding(20.dp), contentAlignment = Alignment.Center) {
                        Text("No items sold in this period.", color = palette.textSecondary)
                    }
                } else {
                    Column(Modifier.fillMaxWidth().noorCard(palette).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Invoice", color = palette.textMuted, fontWeight = FontWeight.Bold, fontSize = 11.5.sp, modifier = Modifier.weight(1.3f))
                            Text("Sell Total", color = palette.textMuted, fontWeight = FontWeight.Bold, fontSize = 11.5.sp, modifier = Modifier.weight(0.9f))
                            Text("Cost", color = palette.textMuted, fontWeight = FontWeight.Bold, fontSize = 11.5.sp, modifier = Modifier.weight(0.8f))
                            Text("Profit", color = palette.textMuted, fontWeight = FontWeight.Bold, fontSize = 11.5.sp, modifier = Modifier.weight(0.8f))
                        }
                        HorizontalDivider(color = palette.border)
                        periodSales.forEach { inv ->
                            val cost = inv.items.sumOf { it.costPrice * it.qty } + (if (inv.partSourceMode == "outside") inv.outsidePartCost else 0.0)
                            val prof = inv.grandTotal - cost
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${inv.invoiceNumber}\n${inv.customerName.ifBlank { "Walk-in" }}", color = palette.textPrimary, fontSize = 11.5.sp, modifier = Modifier.weight(1.3f))
                                Text(Formatters.formatInr(inv.grandTotal), color = palette.textPrimary, fontSize = 11.5.sp, modifier = Modifier.weight(0.9f))
                                Text(Formatters.formatInr(cost), color = palette.textSecondary, fontSize = 11.5.sp, modifier = Modifier.weight(0.8f))
                                Text(Formatters.formatInr(prof), color = if (prof >= 0) palette.statusGreen else palette.statusRed, fontWeight = FontWeight.Bold, fontSize = 11.5.sp, modifier = Modifier.weight(0.8f))
                            }
                            HorizontalDivider(color = palette.border.copy(alpha = 0.4f))
                        }
                    }
                }
            }

            "Sales" -> {
                val rev = periodSales.sumOf { it.grandTotal }
                ReportCard("Revenue", Formatters.formatInr(rev), palette.accent, Modifier.fillMaxWidth())
                if (periodSales.isEmpty()) {
                    Box(Modifier.fillMaxWidth().noorCard(palette).padding(20.dp), contentAlignment = Alignment.Center) {
                        Text("No sales in this period.", color = palette.textSecondary)
                    }
                } else {
                    Column(Modifier.fillMaxWidth().noorCard(palette).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        periodSales.forEach { inv ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${inv.invoiceNumber} · ${inv.customerName.ifBlank { "Walk-in" }} (${Formatters.formatDisplayDate(inv.date)})", color = palette.textPrimary, fontSize = 12.sp, modifier = Modifier.weight(1f))
                                Text(Formatters.formatInr(inv.grandTotal), color = palette.accent, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                            }
                            HorizontalDivider(color = palette.border.copy(alpha = 0.4f))
                        }
                    }
                }
            }

            "Purchases" -> {
                if (periodPurchases.isEmpty()) {
                    Box(Modifier.fillMaxWidth().noorCard(palette).padding(20.dp), contentAlignment = Alignment.Center) {
                        Text("No purchases in this period.", color = palette.textSecondary)
                    }
                } else {
                    Column(Modifier.fillMaxWidth().noorCard(palette).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        periodPurchases.forEach { p ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${p.billNumber} · ${p.supplierName} (${Formatters.formatDisplayDate(p.date)})", color = palette.textPrimary, fontSize = 12.sp, modifier = Modifier.weight(1f))
                                Text(Formatters.formatInr(p.totalAmount), color = palette.textPrimary, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                            }
                            HorizontalDivider(color = palette.border.copy(alpha = 0.4f))
                        }
                    }
                }
                Column(Modifier.fillMaxWidth().noorCard(palette).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Expense Entries", color = palette.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    if (periodExpenses.isEmpty()) {
                        Text("No expenses recorded in this period.", color = palette.textSecondary, fontSize = 12.5.sp)
                    } else {
                        periodExpenses.forEach { exp ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${exp.category} · ${exp.reason}", color = palette.textPrimary, fontSize = 12.sp)
                                Text(Formatters.formatInr(exp.amount), color = palette.statusRed, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            "Part Price History" -> {
                PartPriceHistoryTab(purchases = purchases, items = items)
            }

            "GST Report" -> {
                if (periodSales.isEmpty()) {
                    Box(Modifier.fillMaxWidth().noorCard(palette).padding(20.dp), contentAlignment = Alignment.Center) {
                        Text("No invoices in this period.", color = palette.textSecondary)
                    }
                } else {
                    val taxableTotal = periodSales.sumOf { inv ->
                        val disc = if (inv.discountType == "percent") inv.subtotal * inv.discountAmount / 100.0 else inv.discountAmount
                        (inv.subtotal - disc).coerceAtLeast(0.0)
                    }
                    val gstTotal = periodSales.sumOf { inv ->
                        val disc = if (inv.discountType == "percent") inv.subtotal * inv.discountAmount / 100.0 else inv.discountAmount
                        ((inv.subtotal - disc).coerceAtLeast(0.0)) * inv.gstRate / 100.0
                    }
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
                        periodSales.groupBy { it.gstRate }.toSortedMap().forEach { (slab, list) ->
                            val slabTaxable = list.sumOf { inv ->
                                val disc = if (inv.discountType == "percent") inv.subtotal * inv.discountAmount / 100.0 else inv.discountAmount
                                (inv.subtotal - disc).coerceAtLeast(0.0)
                            }
                            val slabTax = slabTaxable * slab / 100.0
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${slab.toInt()}% GST (${list.size} bills)", color = palette.textPrimary, fontSize = 12.5.sp)
                                Text("Taxable ${Formatters.formatInr(slabTaxable)} · Tax ${Formatters.formatInr(slabTax)}", color = palette.accent, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                            }
                        }
                    }
                }
            }

            "Day Book" -> {
                val entries = buildList {
                    periodInvoices.forEach { add(Triple(it.date, "${it.invoiceNumber} · ${it.customerName.ifBlank { "Sale" }} (${it.paymentMode})", "+${Formatters.formatInr(it.grandTotal)}")) }
                    periodPurchases.forEach { add(Triple(it.date, "${it.billNumber} · ${pLabel(it)} (${it.paymentMode})", "-${Formatters.formatInr(it.totalAmount)}")) }
                    periodExpenses.forEach { add(Triple(it.date, "Expense: ${it.reason} (${it.paymentMode})", "-${Formatters.formatInr(it.amount)}")) }
                    payments.filter { Formatters.isDateInPeriod(it.date, period, customFrom, customTo) }.forEach {
                        add(Triple(it.date, "Payment ${if (it.direction == "in") "In" else "Out"}: ${it.partyName}", Formatters.formatInr(it.amount)))
                    }
                }.sortedByDescending { it.first }
                if (entries.isEmpty()) {
                    Box(Modifier.fillMaxWidth().noorCard(palette).padding(20.dp), contentAlignment = Alignment.Center) {
                        Text("No ledger activity in this period.", color = palette.textSecondary)
                    }
                } else {
                    Column(Modifier.fillMaxWidth().noorCard(palette).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        entries.forEach { (d, title, amt) ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${Formatters.formatDisplayDate(d)} · $title", color = palette.textPrimary, fontSize = 12.sp, modifier = Modifier.weight(1f))
                                Text(amt, color = if (amt.startsWith("+")) palette.statusGreen else palette.accent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            HorizontalDivider(color = palette.border.copy(alpha = 0.4f))
                        }
                    }
                }
            }

            "Stock Summary" -> {
                val physical = items.filter { !it.isService }
                val totalPieces = physical.sumOf { it.pieces }
                val costValue = physical.sumOf { it.costPrice * it.pieces }
                val retailValue = physical.sumOf { it.sellingPrice * it.pieces }
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
                    physical.groupBy { it.category.ifBlank { "Uncategorized" } }.forEach { (cat, list) ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("$cat (${list.size} SKUs)", color = palette.textPrimary, fontSize = 12.5.sp)
                            Text("${list.sumOf { it.pieces }} pcs", color = palette.accent, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                val lowList = physical.filter { it.pieces <= it.lowStockAlert }
                Column(Modifier.fillMaxWidth().noorCard(palette).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Low Stock Items (${lowList.size})", color = palette.statusAmber, fontWeight = FontWeight.Bold)
                    if (lowList.isEmpty()) {
                        Text("No low-stock items right now.", color = palette.textSecondary, fontSize = 12.5.sp)
                    } else {
                        lowList.forEach { st ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(st.displayName, color = palette.textPrimary, fontSize = 12.5.sp)
                                Text("${st.pieces} pcs", color = palette.statusRed, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            "Top Selling & Buy Again" -> {
                val soldCounts = mutableMapOf<String, Pair<String, Int>>()
                invoices.filter { it.status == "active" && it.docType == "invoice" }.forEach { inv ->
                    inv.items.forEach { line ->
                        val k = line.itemId ?: line.name
                        val cur = soldCounts[k]
                        soldCounts[k] = (line.name.ifBlank { k }) to ((cur?.second ?: 0) + line.qty)
                    }
                }
                val top = soldCounts.entries.sortedByDescending { it.value.second }.take(15)
                Column(Modifier.fillMaxWidth().noorCard(palette).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Top Selling Items", color = palette.textPrimary, fontWeight = FontWeight.Bold)
                    if (top.isEmpty()) Text("No sales in this period.", color = palette.textSecondary)
                    top.forEach { (_, p) ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(p.first, color = palette.textPrimary, fontSize = 12.5.sp)
                            Text("${p.second} sold", color = palette.accent, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                val buyAgain = items.filter { !it.isService && it.pieces <= it.lowStockAlert && (soldCounts[it.id]?.second ?: 0) > 0 }
                Column(Modifier.fillMaxWidth().noorCard(palette).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Items to Buy Again (Fast Sellers Running Low)", color = palette.statusAmber, fontWeight = FontWeight.Bold)
                    if (buyAgain.isEmpty()) {
                        Text("None of your top sellers are running low right now.", color = palette.textSecondary, fontSize = 12.5.sp)
                    } else {
                        buyAgain.forEach { st ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(st.displayName, color = palette.textPrimary, fontSize = 12.5.sp)
                                Text("${st.pieces} left", color = palette.statusRed, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            "Party Statements" -> {
                if (parties.isEmpty()) {
                    Box(Modifier.fillMaxWidth().noorCard(palette).padding(20.dp), contentAlignment = Alignment.Center) {
                        Text("No parties available yet.", color = palette.textSecondary)
                    }
                } else {
                    Text("Select Customer or Supplier", color = palette.textMuted, fontSize = 12.sp)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        parties.forEach { p ->
                            FilterChip(selected = selectedPartyId == p.id, onClick = { selectedPartyId = p.id }, label = { Text(p.name, fontSize = 11.5.sp) })
                        }
                    }
                    val party = parties.firstOrNull { it.id == selectedPartyId } ?: parties.first()
                    val summary = vm.repo.computePartySummary(party)
                    val pInv = periodInvoices.filter { it.customerId == party.id || it.customerName.equals(party.name, true) }
                    val pPur = periodPurchases.filter { it.supplierId == party.id || it.supplierName.equals(party.name, true) }
                    val pPay = payments.filter { it.partyId == party.id || it.partyName.equals(party.name, true) }
                    Column(Modifier.fillMaxWidth().noorCard(palette).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("${party.name} · Net ${Formatters.formatInr(summary.netBalance)}", color = palette.accent, fontWeight = FontWeight.Bold)
                        if (pInv.isEmpty() && pPur.isEmpty() && pPay.isEmpty()) {
                            Text("No entries for this party.", color = palette.textSecondary, fontSize = 12.5.sp)
                        } else {
                            pInv.forEach { Text("Sale ${it.invoiceNumber} · ${Formatters.formatDisplayDate(it.date)} · ${Formatters.formatInr(it.grandTotal)}", color = palette.textPrimary, fontSize = 12.sp) }
                            pPur.forEach { Text("Purchase ${it.billNumber} · ${Formatters.formatDisplayDate(it.date)} · ${Formatters.formatInr(it.totalAmount)}", color = palette.textPrimary, fontSize = 12.sp) }
                            pPay.forEach { Text("Payment ${if (it.direction == "in") "In" else "Out"} · ${Formatters.formatDisplayDate(it.date)} · ${Formatters.formatInr(it.amount)}", color = palette.statusGreen, fontSize = 12.sp) }
                        }
                    }
                }
            }

            "Balance Sheet" -> {
                val cashInHand = vm.repo.computeCashInHand()
                val bankBal = vm.repo.computeBankBalance()
                val stockCost = items.filter { !it.isService }.sumOf { it.costPrice * it.pieces }
                val rec = parties.sumOf { vm.repo.computePartySummary(it).receivableBalance }
                val pay = parties.sumOf { vm.repo.computePartySummary(it).payableBalance }
                val totalAssets = cashInHand + bankBal + (if (isOwner) stockCost else 0.0) + rec
                val netWorth = totalAssets - pay

                Column(Modifier.fillMaxWidth().noorCard(palette).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Assets (What Your Shop Owns)", color = palette.accent, fontWeight = FontWeight.Bold, fontSize = 14.5.sp)
                    BsRow("Cash in Hand", Formatters.formatInr(cashInHand))
                    BsRow("Bank Accounts Balance", Formatters.formatInr(bankBal))
                    if (isOwner) {
                        BsRow("Closing Stock (At Cost Price)", Formatters.formatInr(stockCost))
                    }
                    BsRow("Customer Receivables (To Collect)", Formatters.formatInr(rec))
                    HorizontalDivider(color = palette.border)
                    BsRow("Total Assets", Formatters.formatInr(totalAssets), bold = true)
                }

                Column(Modifier.fillMaxWidth().noorCard(palette, highlighted = true).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Liabilities & Shop Net Worth", color = palette.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.5.sp)
                    BsRow("Supplier Payables (To Pay)", Formatters.formatInr(pay))
                    HorizontalDivider(color = palette.border)
                    BsRow("Net Business Capital / Worth", Formatters.formatInr(netWorth), bold = true)
                    Text(
                        "Net Worth = (Cash in Hand + Bank Balance + Stock at Cost + Receivables) − Supplier Payables.",
                        color = palette.textMuted,
                        fontSize = 11.5.sp
                    )
                }
            }
        }
        Spacer(Modifier.height(96.dp))
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
