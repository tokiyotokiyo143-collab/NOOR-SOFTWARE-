package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.*
import com.example.ui.MainViewModel
import com.example.ui.components.*
import com.example.ui.theme.LocalNoorColors
import com.example.ui.theme.noorCard
import com.example.util.Formatters
import com.example.util.WhatsAppHelper
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun PartiesScreen(
    vm: MainViewModel,
    settings: ShopSettings,
    parties: List<Party>,
    invoices: List<Invoice>,
    purchases: List<Purchase>,
    repairs: List<RepairJob>,
    payments: List<PaymentRecord>
) {
    val palette = LocalNoorColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var searchQuery by remember { mutableStateOf("") }
    var filterChip by remember { mutableStateOf("All Parties") }
    var showPartyForm by remember { mutableStateOf(false) }
    var editingParty by remember { mutableStateOf<Party?>(null) }
    var selectedPartyDetail by remember { mutableStateOf<Party?>(null) }
    var paymentTarget by remember { mutableStateOf<Party?>(null) }
    var deleteTarget by remember { mutableStateOf<Party?>(null) }
    var showQuickTemplates by remember { mutableStateOf(false) }

    val filters = listOf("All Parties", "Customers", "Suppliers", "Pending Dues")
    val summaries = remember(parties, invoices, purchases, repairs, payments) {
        parties.map { vm.repo.computePartySummary(it) }
    }

    val filtered = remember(summaries, filterChip, searchQuery) {
        val q = searchQuery.trim().lowercase()
        summaries.filter { s ->
            val p = s.party
            val matchChip = when (filterChip) {
                "Customers" -> p.type == "customer" || p.type == "both"
                "Suppliers" -> p.type == "supplier" || p.type == "both"
                "Pending Dues" -> s.receivableBalance > 0.0 || s.payableBalance > 0.0
                else -> true
            }
            val matchQ = q.isEmpty() || p.name.lowercase().contains(q) || p.phone.lowercase().contains(q)
            matchChip && matchQ
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
            title = "Parties (Customers & Suppliers)",
            trailing = {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = { showQuickTemplates = true }) {
                        Text("Quick Message", fontSize = 11.5.sp, color = palette.accent, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = { editingParty = null; showPartyForm = true },
                        colors = ButtonDefaults.buttonColors(containerColor = palette.accent),
                        modifier = Modifier.testTag("add_party_button")
                    ) {
                        Text("+ Party", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        )

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search customer or supplier by name or phone...") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, tint = palette.accent) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            filters.forEach { f ->
                val sel = filterChip == f
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (sel) palette.accent else palette.inputBg)
                        .border(1.dp, if (sel) palette.accent else palette.border, RoundedCornerShape(12.dp))
                        .clickable { filterChip = f }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(f, color = if (sel) Color.White else palette.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Text("Tip: Swipe left on a party card to quick-edit details", color = palette.textMuted, fontSize = 11.5.sp)

        if (filtered.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().noorCard(palette).padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("No matching parties found.", color = palette.textSecondary, fontSize = 13.5.sp)
            }
        } else {
            filtered.forEach { s ->
                val p = s.party
                SwipeActionCard(
                    onEdit = { editingParty = p; showPartyForm = true },
                    onDelete = { deleteTarget = p }
                ) {
                    Column(modifier = Modifier.clickable { selectedPartyDetail = p }) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(p.name, color = palette.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text(
                                    "${p.phone.ifBlank { "no phone" }} · ${p.type.replaceFirstChar { it.uppercase() }}",
                                    color = palette.textSecondary,
                                    fontSize = 12.sp
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    when {
                                        s.netBalance > 0 -> "To Collect ${Formatters.formatInr(s.netBalance)}"
                                        s.netBalance < 0 -> "To Pay ${Formatters.formatInr(-s.netBalance)}"
                                        else -> "Settled ₹0"
                                    },
                                    color = when {
                                        s.netBalance > 0 -> palette.accent
                                        s.netBalance < 0 -> palette.statusAmber
                                        else -> palette.statusGreen
                                    },
                                    fontWeight = FontWeight.Black,
                                    fontSize = 13.5.sp
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(onClick = { selectedPartyDetail = p }) {
                                Text("Ledger", fontSize = 11.5.sp)
                            }
                            OutlinedButton(onClick = { paymentTarget = p }) {
                                Text("Record Payment", fontSize = 11.5.sp, color = palette.accent)
                            }
                            if (s.receivableBalance > 0) {
                                OutlinedButton(onClick = {
                                    val msg = "Hello ${p.name}, a gentle reminder from ${settings.shopName}: your pending balance is ${Formatters.formatInr(s.receivableBalance)}. Please pay when convenient. Thank you."
                                    WhatsAppHelper.openWhatsAppChat(context, p.phone, msg)
                                }) {
                                    Text("Payment Reminder", fontSize = 11.5.sp, color = palette.statusGreen)
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(96.dp))
    }

    if (showPartyForm) {
        PartyFormDialog(
            initial = editingParty,
            onDismiss = { showPartyForm = false },
            onSave = { party ->
                scope.launch { vm.repo.saveParty(party) }
                showPartyForm = false
            }
        )
    }

    selectedPartyDetail?.let { party ->
        val summary = vm.repo.computePartySummary(party)
        PartyDetailDialog(
            party = party,
            summary = summary,
            invoices = invoices,
            purchases = purchases,
            repairs = repairs,
            payments = payments,
            onEditContact = {
                editingParty = party
                selectedPartyDetail = null
                showPartyForm = true
            },
            onRecordPayment = {
                paymentTarget = party
            },
            onDismiss = { selectedPartyDetail = null }
        )
    }

    paymentTarget?.let { party ->
        RecordPaymentDialog(
            party = party,
            onDismiss = { paymentTarget = null },
            onSave = { pay ->
                scope.launch { vm.repo.savePayment(pay) }
                paymentTarget = null
            }
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            containerColor = palette.card,
            title = { Text("Delete ${target.name}?", color = palette.statusRed, fontWeight = FontWeight.Bold) },
            text = { Text("Remove this party contact? Existing invoices and bills are kept.", color = palette.textSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { vm.repo.deleteParty(target.id) }
                    deleteTarget = null
                }) { Text("Delete", color = palette.statusRed, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            }
        )
    }

    if (showQuickTemplates) {
        QuickMessageTemplatesDialog(
            settings = settings,
            parties = parties,
            repairs = repairs,
            computeSummary = { vm.repo.computePartySummary(it) },
            onDismiss = { showQuickTemplates = false }
        )
    }
}

@Composable
private fun PartyFormDialog(
    initial: Party?,
    onDismiss: () -> Unit,
    onSave: (Party) -> Unit
) {
    val palette = LocalNoorColors.current
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var phone by remember { mutableStateOf(initial?.phone ?: "") }
    var address by remember { mutableStateOf(initial?.address ?: "") }
    var type by remember { mutableStateOf(initial?.type ?: "customer") }
    var openDir by remember {
        mutableStateOf(if ((initial?.openingPayable ?: 0.0) > 0) "payable" else "receivable")
    }
    var openAmt by remember {
        val v = maxOf(initial?.openingReceivable ?: 0.0, initial?.openingPayable ?: 0.0)
        mutableStateOf(if (v > 0) Formatters.formatDecimal(v) else "")
    }
    var notes by remember { mutableStateOf(initial?.notes ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.card,
        title = { Text(if (initial != null) "Edit Contact" else "Add Party", color = palette.textPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Party Name *") },
                    placeholder = { Text("Customer or supplier name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = Formatters.clampPhone(it) },
                    label = { Text("Phone") },
                    placeholder = { Text("WhatsApp / mobile number") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Address / Town") },
                    placeholder = { Text("Optional street / town") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Party Type", color = palette.textMuted, fontSize = 11.5.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("customer" to "Customer", "supplier" to "Supplier", "both" to "Both").forEach { (k, l) ->
                        FilterChip(selected = type == k, onClick = { type = k }, label = { Text(l, fontSize = 11.sp) })
                    }
                }
                OutlinedTextField(
                    value = openAmt,
                    onValueChange = { openAmt = it },
                    label = { Text("Opening Balance") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Opening Balance Direction", color = palette.textMuted, fontSize = 11.5.sp)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(
                        "receivable" to "To Collect (Customer owes shop)",
                        "payable" to "To Pay (Shop owes supplier)"
                    ).forEach { (k, l) ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { openDir = k },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = openDir == k, onClick = { openDir = k })
                            Text(l, color = palette.textPrimary, fontSize = 12.sp)
                        }
                    }
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isBlank()) return@TextButton
                val amt = openAmt.toDoubleOrNull() ?: 0.0
                onSave(
                    (initial ?: Party()).copy(
                        name = name,
                        phone = phone,
                        address = address,
                        type = type,
                        openingReceivable = if (openDir == "receivable") amt else 0.0,
                        openingPayable = if (openDir == "payable") amt else 0.0,
                        notes = notes
                    )
                )
            }) { Text("Save Party", color = palette.accent, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun PartyDetailDialog(
    party: Party,
    summary: com.example.data.repository.ShopRepository.PartyBalanceSummary,
    invoices: List<Invoice>,
    purchases: List<Purchase>,
    repairs: List<RepairJob>,
    payments: List<PaymentRecord>,
    onEditContact: () -> Unit,
    onRecordPayment: () -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalNoorColors.current
    val partyInvoices = invoices.filter { it.customerId == party.id || it.customerName.equals(party.name, true) }
    val partyPurchases = purchases.filter { it.supplierId == party.id || it.supplierName.equals(party.name, true) }
    val partyRepairs = repairs.filter { it.customerId == party.id || it.customerName.equals(party.name, true) }
    val partyPayments = payments.filter { it.partyId == party.id || it.partyName.equals(party.name, true) }

    val totalPaidOnBills = partyInvoices.sumOf { it.amountPaid } + partyPayments.filter { it.direction == "in" }.sumOf { it.amount }
    val billsBalance = partyInvoices.filter { it.status == "active" }.sumOf { it.balanceDue }
    val lastPurchase = partyPurchases.maxByOrNull { it.date }?.date?.let { Formatters.formatDisplayDate(it) } ?: "-"
    val lastVisit = (partyInvoices.map { it.date } + partyRepairs.map { it.date }).maxOrNull()?.let { Formatters.formatDisplayDate(it) } ?: "-"

    var tab by remember { mutableStateOf("All History") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.card,
        title = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(party.name, color = palette.textPrimary, fontWeight = FontWeight.Black, fontSize = 17.sp)
                    Text("Account Balance: ${Formatters.formatInr(summary.netBalance)}", color = palette.accent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                TextButton(onClick = onEditContact) { Text("Edit Contact", color = palette.accent) }
            }
        },
        text = {
            Column(modifier = Modifier.heightIn(max = 430.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MiniStat("Total Paid", Formatters.formatInr(totalPaidOnBills), "On bills", Modifier.weight(1f))
                    MiniStat("Bills Balance", Formatters.formatInr(billsBalance), "Unpaid on bills", Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MiniStat("Last Purchase", lastPurchase, "Past Sales: ${partyInvoices.size}", Modifier.weight(1f))
                    MiniStat("Repairs: ${partyRepairs.size}", "Last Visit", lastVisit, Modifier.weight(1f))
                }

                val tabs = listOf(
                    "All History" to (partyInvoices.size + partyPurchases.size + partyRepairs.size + partyPayments.size),
                    "Purchases" to partyPurchases.size,
                    "Past Sales" to partyInvoices.size,
                    "Repairs" to partyRepairs.size,
                    "Ledger" to partyPayments.size
                )
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    tabs.forEach { (name, count) ->
                        FilterChip(
                            selected = tab == name,
                            onClick = { tab = name },
                            label = { Text("$name ($count)", fontSize = 11.sp) }
                        )
                    }
                }

                when (tab) {
                    "Purchases" -> {
                        if (partyPurchases.isEmpty()) Text("No transactions recorded yet.", color = palette.textSecondary)
                        partyPurchases.forEach { p ->
                            Text("${p.billNumber} · ${Formatters.formatDisplayDate(p.date)} · ${Formatters.formatInr(p.totalAmount)} (${p.paymentMode})", color = palette.textPrimary, fontSize = 12.sp)
                        }
                    }
                    "Past Sales" -> {
                        if (partyInvoices.isEmpty()) Text("No transactions recorded yet.", color = palette.textSecondary)
                        partyInvoices.forEach { inv ->
                            val lbl = if (inv.docType == "return") "Credit Note" else "Sale"
                            Text("$lbl ${inv.invoiceNumber} · ${Formatters.formatDisplayDate(inv.date)} · ${Formatters.formatInr(inv.grandTotal)} (${inv.paymentMode})", color = palette.textPrimary, fontSize = 12.sp)
                        }
                    }
                    "Repairs" -> {
                        if (partyRepairs.isEmpty()) Text("No transactions recorded yet.", color = palette.textSecondary)
                        partyRepairs.forEach { r ->
                            Text("${r.jobNumber} · ${r.phoneModel} · ${r.status} · ${Formatters.formatInr(r.totalCharged)}", color = palette.textPrimary, fontSize = 12.sp)
                        }
                    }
                    "Ledger" -> {
                        if (partyPayments.isEmpty()) Text("No posting ledger entries recorded yet.", color = palette.textSecondary)
                        partyPayments.forEach { pay ->
                            val dir = if (pay.direction == "in") "Payment In" else "Payment Out"
                            Text("$dir (${pay.paymentMode}) · ${Formatters.formatDisplayDate(pay.date)} · ${Formatters.formatInr(pay.amount)}", color = palette.textPrimary, fontSize = 12.sp)
                        }
                    }
                    else -> {
                        if (partyInvoices.isEmpty() && partyPurchases.isEmpty() && partyRepairs.isEmpty() && partyPayments.isEmpty()) {
                            Text("No transactions recorded yet.", color = palette.textSecondary)
                        } else {
                            partyInvoices.forEach { inv -> Text("Sale ${inv.invoiceNumber} · ${Formatters.formatInr(inv.grandTotal)} (${inv.paymentMode})", color = palette.textPrimary, fontSize = 12.sp) }
                            partyPurchases.forEach { p -> Text("Purchase ${p.billNumber} · ${Formatters.formatInr(p.totalAmount)} (${p.paymentMode})", color = palette.textPrimary, fontSize = 12.sp) }
                            partyRepairs.forEach { r -> Text("Repair ${r.jobNumber} · ${r.phoneModel} · ${Formatters.formatInr(r.totalCharged)}", color = palette.textPrimary, fontSize = 12.sp) }
                            partyPayments.forEach { pay -> Text("${if (pay.direction == "in") "Payment In" else "Payment Out"} (${pay.paymentMode}) · ${Formatters.formatInr(pay.amount)}", color = palette.statusGreen, fontSize = 12.sp) }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onRecordPayment) { Text("Record Payment", color = palette.accent, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
private fun MiniStat(title: String, value: String, sub: String, modifier: Modifier = Modifier) {
    val palette = LocalNoorColors.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(palette.inputBg)
            .padding(8.dp)
    ) {
        Text(title, color = palette.textMuted, fontSize = 10.5.sp)
        Text(value, color = palette.textPrimary, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
        Text(sub, color = palette.textSecondary, fontSize = 10.sp)
    }
}

@Composable
private fun RecordPaymentDialog(
    party: Party,
    onDismiss: () -> Unit,
    onSave: (PaymentRecord) -> Unit
) {
    val palette = LocalNoorColors.current
    var amt by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf("Cash") }
    var date by remember { mutableStateOf(Formatters.todayIso()) }
    var notes by remember { mutableStateOf("") }
    var direction by remember { mutableStateOf(if (party.type == "supplier") "out" else "in") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.card,
        title = { Text("Record Payment · ${party.name}", color = palette.textPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = direction == "in", onClick = { direction = "in" }, label = { Text("Payment In (Receive)") })
                    FilterChip(selected = direction == "out", onClick = { direction = "out" }, label = { Text("Payment Out (Pay)") })
                }
                OutlinedTextField(
                    value = amt,
                    onValueChange = { amt = it },
                    label = { Text("Amount (₹) *") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Cash", "UPI", "Card", "Bank Transfer").forEach { m ->
                        FilterChip(selected = mode == m, onClick = { mode = m }, label = { Text(m, fontSize = 11.sp) })
                    }
                }
                OutlinedTextField(value = date, onValueChange = { date = it }, label = { Text("Date") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes") },
                    placeholder = { Text("Optional reference...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val a = amt.toDoubleOrNull() ?: 0.0
                if (a > 0) {
                    onSave(
                        PaymentRecord(
                            partyId = party.id,
                            partyName = party.name,
                            direction = direction,
                            amount = a,
                            paymentMode = mode,
                            date = date,
                            notes = notes
                        )
                    )
                }
            }) { Text("Save Payment", color = palette.accent, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun QuickMessageTemplatesDialog(
    settings: ShopSettings,
    parties: List<Party>,
    repairs: List<RepairJob>,
    computeSummary: (Party) -> com.example.data.repository.ShopRepository.PartyBalanceSummary,
    onDismiss: () -> Unit
) {
    val palette = LocalNoorColors.current
    val context = LocalContext.current
    var selectedParty by remember { mutableStateOf(parties.firstOrNull()) }
    var selectedScenario by remember { mutableStateOf("Service / Repair Ready") }

    val scenarios = listOf(
        "Order Ready / Update",
        "Service / Repair Ready",
        "Payment Received / Thanks",
        "Service / Purchase Follow-up",
        "Supplier Stock / Payment"
    )

    fun buildTemplate(party: Party?, scenario: String): String {
        val name = party?.name ?: "Customer"
        val summary = party?.let(computeSummary)
        val rec = summary?.receivableBalance ?: 0.0
        val pay = summary?.payableBalance ?: 0.0
        val rep = party?.let { p -> repairs.firstOrNull { it.customerId == p.id || it.customerName.equals(p.name, true) } }
        val footer = buildString {
            if (settings.phone.isNotBlank()) append("\nPh: ${settings.phone}")
            val ig = Formatters.extractInstagramHandle(settings.instagram)
            if (ig.isNotBlank()) append(" · IG: @$ig")
            if (settings.address.isNotBlank()) append("\nLocation: ${settings.address}")
        }
        return when (scenario) {
            "Order Ready / Update" ->
                "Hello *$name*,\nYour order is *READY FOR PICKUP* at *${settings.shopName}*!\nUpdated Balance Due: *${Formatters.formatInr(rec)}*$footer"
            "Service / Repair Ready" -> {
                val model = rep?.phoneModel ?: "your phone"
                val job = rep?.jobNumber ?: "JOB-0001"
                val total = rep?.totalCharged ?: 0.0
                val adv = rep?.totalPaid ?: 0.0
                val bal = rep?.balanceDue ?: rec
                "Hello *$name*,\nYour device *$model* (Ref *$job*) is *READY FOR PICKUP* at *${settings.shopName}*!\nTotal Charge: ${Formatters.formatInr(total)}\nAdvance Paid: ${Formatters.formatInr(adv)}\nRepair Balance: *${Formatters.formatInr(bal)}*\nPickup Address: ${settings.address}$footer"
            }
            "Payment Received / Thanks" -> {
                val balLine = if (rec > 0) "Updated Balance Due: *${Formatters.formatInr(rec)}*" else "Account Status: *Settled in Full (₹0 due)*"
                "Hello *$name*,\nThank you for your payment received on ${Formatters.formatDisplayDate(Formatters.todayIso())}.\n$balLine\nWe appreciate your continued trust!$footer"
            }
            "Service / Purchase Follow-up" -> {
                val model = rep?.phoneModel ?: "device"
                val job = rep?.jobNumber ?: "service"
                "Hello *$name*,\nHope your *$model* ($job) is working smoothly after our recent service!\nPlease feel free to reply if you need any covers, tempered glass, accessories, or support.$footer"
            }
            else ->
                "Greetings from *${settings.shopName}*.\nOur ledger shows a payable balance of *${Formatters.formatInr(pay)}*. Please share your updated statement / UPI details for settlement.$footer"
        }
    }

    var messageText by remember(selectedParty, selectedScenario) {
        mutableStateOf(buildTemplate(selectedParty, selectedScenario))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.card,
        title = {
            Column {
                Text("Quick Message Templates", color = palette.textPrimary, fontWeight = FontWeight.Black, fontSize = 17.sp)
                Text("Auto-fills customer name, due balance & active services for WhatsApp", color = palette.textSecondary, fontSize = 12.sp)
            }
        },
        text = {
            Column(modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (parties.isEmpty()) {
                    Text("Add a customer or supplier party first to compose Quick Messages.", color = palette.statusAmber, fontSize = 13.sp)
                } else {
                    Text("Select Contact", color = palette.textMuted, fontSize = 11.5.sp)
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        parties.forEach { p ->
                            FilterChip(
                                selected = selectedParty?.id == p.id,
                                onClick = { selectedParty = p },
                                label = { Text(p.name, fontSize = 11.5.sp) }
                            )
                        }
                    }
                    if (selectedParty?.phone.isNullOrBlank()) {
                        Text(
                            "No phone number saved for this contact — WhatsApp will open contact picker so you can choose a recipient.",
                            color = palette.statusAmber,
                            fontSize = 11.5.sp
                        )
                    }
                    Text("Scenario Templates · Smart-picked for this contact", color = palette.textMuted, fontSize = 11.5.sp)
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        scenarios.forEach { sc ->
                            FilterChip(
                                selected = selectedScenario == sc,
                                onClick = { selectedScenario = sc },
                                label = { Text(sc, fontSize = 11.sp) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = messageText,
                        onValueChange = { messageText = it },
                        label = { Text("Message Preview (Editable)") },
                        minLines = 5,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            if (parties.isNotEmpty()) {
                TextButton(onClick = {
                    WhatsAppHelper.openWhatsAppChat(context, selectedParty?.phone.orEmpty(), messageText)
                }) { Text("Send on WhatsApp", color = palette.statusGreen, fontWeight = FontWeight.Bold) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}
