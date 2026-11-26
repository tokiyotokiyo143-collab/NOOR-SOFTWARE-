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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.*
import com.example.ui.MainViewModel
import com.example.ui.components.NoorPrimaryButton
import com.example.ui.components.NoorSectionTitle
import com.example.ui.theme.LocalNoorColors
import com.example.ui.theme.noorCard
import com.example.util.Formatters
import kotlinx.coroutines.launch

@Composable
fun CashBankScreen(
    vm: MainViewModel,
    settings: ShopSettings,
    bankAccounts: List<BankAccount>,
    cashBankTxns: List<CashBankTxn>,
    parties: List<Party>,
    invoices: List<Invoice>,
    purchases: List<Purchase>,
    payments: List<PaymentRecord>,
    expenses: List<ExpenseRecord>,
    repairs: List<RepairJob>
) {
    val palette = LocalNoorColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val cashInHand = remember(settings, invoices, purchases, payments, expenses, repairs, cashBankTxns) {
        vm.repo.computeCashInHand()
    }
    val bankBalance = remember(bankAccounts, invoices, purchases, payments, expenses, cashBankTxns) {
        vm.repo.computeBankBalance()
    }
    val totalReceivables = remember(parties, invoices, purchases, payments, repairs) {
        parties.sumOf { vm.repo.computePartySummary(it).receivableBalance }
    }
    val cheques = remember(cashBankTxns) { cashBankTxns.filter { it.type == "cheque" } }
    val pendingChequesSum = cheques.filter { it.chequeStatus == "Pending" }.sumOf { it.amount }

    var showAddBank by remember { mutableStateOf(false) }
    var showAddEntry by remember { mutableStateOf(false) }
    var showChequesModal by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        NoorSectionTitle(
            title = "Cash & Bank Accounts",
            subtitle = "Live Cash in Hand, UPI/Bank balances, cash transfers & cheques"
        )

        // Live Balance Cards
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(modifier = Modifier.weight(1f).noorCard(palette, radius = 16.dp).padding(12.dp)) {
                Text("Cash in Hand", color = palette.textSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                Text(Formatters.formatInr(cashInHand), color = palette.statusGreen, fontWeight = FontWeight.Black, fontSize = 18.sp)
                Text("Opening: ${Formatters.formatInr(settings.openingCashBalance)}", color = palette.textMuted, fontSize = 11.sp)
            }
            Column(modifier = Modifier.weight(1f).noorCard(palette, radius = 16.dp).padding(12.dp)) {
                Text("Bank Balance", color = palette.textSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                Text(Formatters.formatInr(bankBalance), color = palette.accent, fontWeight = FontWeight.Black, fontSize = 18.sp)
                Text("Includes UPI, Card & Bank transfers plus cleared cheques.", color = palette.textMuted, fontSize = 10.5.sp, lineHeight = 13.sp)
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .noorCard(palette, radius = 16.dp)
                    .clickable { vm.setShowPendingDuesSheet(true) }
                    .padding(12.dp)
            ) {
                Text("Receivables", color = palette.textSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                Text(Formatters.formatInr(totalReceivables), color = palette.statusAmber, fontWeight = FontWeight.Black, fontSize = 17.sp)
                Text("Customer dues & credit balances receivable.", color = palette.textMuted, fontSize = 10.5.sp)
                Spacer(Modifier.height(4.dp))
                Text("View Dues →", color = palette.accent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .noorCard(palette, radius = 16.dp)
                    .clickable { showChequesModal = true }
                    .padding(12.dp)
            ) {
                Text("Cheques (${cheques.size})", color = palette.textSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                Text(Formatters.formatInr(pendingChequesSum), color = palette.textPrimary, fontWeight = FontWeight.Black, fontSize = 17.sp)
                Text("Pending clearance", color = palette.textMuted, fontSize = 10.5.sp)
                Spacer(Modifier.height(4.dp))
                Text("Manage Cheques →", color = palette.accent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }

        // Action Buttons
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { showAddBank = true }, modifier = Modifier.weight(1f)) {
                Text("Add Bank Account", color = palette.accent, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
            }
            NoorPrimaryButton(
                text = "Record Cash / Bank Entry",
                onClick = { showAddEntry = true },
                modifier = Modifier.weight(1.25f)
            )
        }

        // Connected Bank Accounts
        Column(
            modifier = Modifier.fillMaxWidth().noorCard(palette, radius = 16.dp).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Connected Bank Accounts", color = palette.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            if (bankAccounts.isEmpty()) {
                Text("No bank accounts added yet.", color = palette.textSecondary, fontSize = 12.5.sp)
            } else {
                bankAccounts.forEach { acc ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "${acc.name}${if (acc.last4.isNotBlank()) " ·••${acc.last4}" else ""}",
                                color = palette.textPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.5.sp
                            )
                            if (acc.upiHandle.isNotBlank()) {
                                Text("UPI: ${acc.upiHandle}", color = palette.textMuted, fontSize = 11.5.sp)
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(Formatters.formatInr(acc.openingBalance), color = palette.accent, fontWeight = FontWeight.Bold)
                            IconButton(onClick = { scope.launch { vm.repo.deleteBankAccount(acc.id) } }) {
                                Icon(Icons.Outlined.Delete, contentDescription = "Delete Bank", tint = palette.statusRed, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                    HorizontalDivider(color = palette.border.copy(alpha = 0.4f))
                }
            }
        }

        // Cash & Bank Entries
        Column(
            modifier = Modifier.fillMaxWidth().noorCard(palette, radius = 16.dp).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Cash & Bank Transfers", color = palette.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            if (cashBankTxns.isEmpty()) {
                Text(
                    "No cash or bank transactions yet. Sales, purchases, expenses & transfers appear here automatically.",
                    color = palette.textSecondary,
                    fontSize = 12.5.sp
                )
            } else {
                cashBankTxns.forEach { txn ->
                    val label = when (txn.type) {
                        "bank_deposit" -> "Cash Deposited into Bank"
                        "bank_withdraw" -> "Cash Withdrawn from Bank"
                        "cash_in" -> "Manual Cash In (Drawer Top-up)"
                        "cash_out" -> "Manual Cash Out (Owner Drawing)"
                        "cheque" -> "Cheque Received #${txn.chequeNumber} (${txn.chequeStatus})"
                        else -> txn.type
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(label, color = palette.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text("${Formatters.formatDisplayDate(txn.date)}${if (txn.notes.isNotBlank()) " · ${txn.notes}" else ""}", color = palette.textSecondary, fontSize = 11.5.sp)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(Formatters.formatInr(txn.amount), color = palette.accent, fontWeight = FontWeight.Bold)
                            IconButton(onClick = { scope.launch { vm.repo.deleteCashBankTxn(txn.id) } }) {
                                Icon(Icons.Outlined.Delete, contentDescription = "Delete Entry", tint = palette.statusRed, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                    HorizontalDivider(color = palette.border.copy(alpha = 0.4f))
                }
            }
        }
        Spacer(Modifier.height(96.dp))
    }

    if (showAddBank) {
        var name by remember { mutableStateOf("") }
        var last4 by remember { mutableStateOf("") }
        var openBal by remember { mutableStateOf("") }
        var upi by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddBank = false },
            containerColor = palette.card,
            title = { Text("Add Bank Account", color = palette.textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = name, onValueChange = { name = it },
                        label = { Text("Bank Name *") },
                        placeholder = { Text("e.g. Indian Bank Annur, SBI, HDFC") },
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = last4, onValueChange = { last4 = it.filter { c -> c.isDigit() }.take(4) },
                        label = { Text("Account Last 4") },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = openBal, onValueChange = { openBal = it },
                        label = { Text("Opening Balance (₹)") },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = upi, onValueChange = { upi = it },
                        label = { Text("Linked UPI ID") },
                        placeholder = { Text("shop@okaxis") },
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (name.isBlank()) {
                        Toast.makeText(context, "Enter bank name.", Toast.LENGTH_SHORT).show()
                        return@TextButton
                    }
                    scope.launch {
                        vm.repo.saveBankAccount(
                            BankAccount(
                                name = name,
                                last4 = last4,
                                openingBalance = openBal.toDoubleOrNull() ?: 0.0,
                                upiHandle = upi
                            )
                        )
                    }
                    showAddBank = false
                }) { Text("Save Bank Account", color = palette.accent, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showAddBank = false }) { Text("Cancel") } }
        )
    }

    if (showAddEntry) {
        val entryTypes = listOf(
            "bank_deposit" to "Cash Deposited into Bank",
            "bank_withdraw" to "Cash Withdrawn from Bank",
            "cash_in" to "Manual Cash In (Drawer Top-up)",
            "cash_out" to "Manual Cash Out (Owner Drawing)",
            "cheque" to "Cheque Received"
        )
        var type by remember { mutableStateOf("bank_deposit") }
        var selectedAccId by remember { mutableStateOf(bankAccounts.firstOrNull()?.id) }
        var chequeNo by remember { mutableStateOf("") }
        var notes by remember { mutableStateOf("") }
        var amount by remember { mutableStateOf("") }
        var date by remember { mutableStateOf(Formatters.todayIso()) }

        AlertDialog(
            onDismissRequest = { showAddEntry = false },
            containerColor = palette.card,
            title = { Text("Record Cash / Bank Entry", color = palette.textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Entry Type", color = palette.textMuted, fontSize = 11.5.sp)
                    entryTypes.forEach { (k, l) ->
                        Row(modifier = Modifier.fillMaxWidth().clickable { type = k }, verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = type == k, onClick = { type = k })
                            Text(l, color = palette.textPrimary, fontSize = 12.5.sp)
                        }
                    }
                    if (bankAccounts.isNotEmpty() && (type == "bank_deposit" || type == "bank_withdraw" || type == "cheque")) {
                        Text("Bank Account", color = palette.textMuted, fontSize = 11.5.sp)
                        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            bankAccounts.forEach { acc ->
                                FilterChip(
                                    selected = selectedAccId == acc.id,
                                    onClick = { selectedAccId = acc.id },
                                    label = { Text(acc.name, fontSize = 11.sp) }
                                )
                            }
                        }
                    }
                    if (type == "cheque") {
                        OutlinedTextField(value = chequeNo, onValueChange = { chequeNo = it }, label = { Text("Cheque No") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    }
                    OutlinedTextField(
                        value = amount, onValueChange = { amount = it },
                        label = { Text("Amount (₹) *") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(value = date, onValueChange = { date = it }, label = { Text("Date") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Party / Notes") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val amt = amount.toDoubleOrNull() ?: 0.0
                    if (amt <= 0.0) return@TextButton
                    scope.launch {
                        vm.repo.saveCashBankTxn(
                            CashBankTxn(
                                type = type,
                                amount = amt,
                                date = date.ifBlank { Formatters.todayIso() },
                                accountId = selectedAccId,
                                chequeNumber = chequeNo,
                                chequeStatus = "Pending",
                                notes = notes
                            )
                        )
                    }
                    showAddEntry = false
                }) { Text("Save Entry", color = palette.accent, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showAddEntry = false }) { Text("Cancel") } }
        )
    }

    if (showChequesModal) {
        AlertDialog(
            onDismissRequest = { showChequesModal = false },
            containerColor = palette.card,
            title = { Text("Manage Cheques", color = palette.textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (cheques.isEmpty()) {
                        Text("No cheques recorded yet.", color = palette.textSecondary)
                    } else {
                        cheques.forEach { ch ->
                            Column(
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(palette.inputBg).padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("#${ch.chequeNumber.ifBlank { "-" }} · ${Formatters.formatDisplayDate(ch.date)}", color = palette.textPrimary, fontWeight = FontWeight.Bold)
                                    Text(Formatters.formatInr(ch.amount), color = palette.accent, fontWeight = FontWeight.Bold)
                                }
                                if (ch.notes.isNotBlank()) Text(ch.notes, color = palette.textSecondary, fontSize = 12.sp)
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf("Pending", "Cleared", "Bounced").forEach { st ->
                                        FilterChip(
                                            selected = ch.chequeStatus == st,
                                            onClick = { scope.launch { vm.repo.updateChequeStatus(ch.id, st) } },
                                            label = { Text(st, fontSize = 11.sp) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showChequesModal = false }) { Text("Close") } }
        )
    }
}
