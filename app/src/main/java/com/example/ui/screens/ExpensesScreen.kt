package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.BankAccount
import com.example.data.model.ExpenseRecord
import com.example.ui.MainViewModel
import com.example.ui.components.NoorPrimaryButton
import com.example.ui.components.NoorSectionTitle
import com.example.ui.components.SwipeActionCard
import com.example.ui.theme.LocalNoorColors
import com.example.ui.theme.noorCard
import com.example.util.Formatters
import kotlinx.coroutines.launch

@Composable
fun ExpensesScreen(
    vm: MainViewModel,
    expenses: List<ExpenseRecord>,
    bankAccounts: List<BankAccount>
) {
    val palette = LocalNoorColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var editingId by remember { mutableStateOf<String?>(null) }
    var category by remember { mutableStateOf("Expense") }
    var reason by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(Formatters.todayIso()) }
    var paidFrom by remember { mutableStateOf("Cash") }
    var notes by remember { mutableStateOf("") }
    var period by remember { mutableStateOf("This Month") }
    var deleteTarget by remember { mutableStateOf<ExpenseRecord?>(null) }

    val quickCats = remember { listOf("Expense", "Rent", "Electricity", "Tea", "Repairs", "Supplies", "Other") }
    val payModes = remember(bankAccounts) {
        listOf("Cash", "UPI", "Card", "Bank Transfer") + bankAccounts.map { it.name }
    }

    val periodExpenses = remember(expenses, period) {
        expenses.filter { Formatters.isDateInPeriod(it.date, period) }
    }
    val totalPeriod = remember(periodExpenses) { periodExpenses.sumOf { it.amount } }
    val byCat = remember(periodExpenses) {
        periodExpenses.groupBy { it.category.ifBlank { "Expense" } }
            .mapValues { (_, list) -> list.sumOf { it.amount } }
            .entries.sortedByDescending { it.value }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "header") {
            NoorSectionTitle(
                title = "Daily & Overhead Expenses",
                subtitle = "Track shop rent, electricity, tea, courier & daily cash drawer expenses."
            )
        }

        // Form Card
        item(key = "form") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .noorCard(palette, radius = 16.dp, highlighted = editingId != null)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(if (editingId != null) "Edit Expense" else "Record Expense", color = palette.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    if (editingId != null) {
                        TextButton(onClick = {
                            editingId = null; category = "Expense"; reason = ""; amountText = ""; notes = ""
                        }) { Text("Cancel Edit", color = palette.accent, fontSize = 12.sp) }
                    }
                }

                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    quickCats.forEach { c ->
                        FilterChip(selected = category == c, onClick = { category = c }, label = { Text(c, fontSize = 11.5.sp) })
                    }
                }

                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("Category") },
                    placeholder = { Text("Rent, Electricity, Repairs, Supplies...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Reason / Description *") },
                    placeholder = { Text("e.g. Monthly shop rent, EB bill, Soldering wire") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("expense_reason"),
                    shape = RoundedCornerShape(12.dp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it },
                        label = { Text("Amount (₹) *") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f).testTag("expense_amount"),
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = date,
                        onValueChange = { date = it },
                        label = { Text("Date") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                Text("Paid From", color = palette.textMuted, fontSize = 11.5.sp)
                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    payModes.forEach { pm ->
                        FilterChip(selected = paidFrom == pm, onClick = { paidFrom = pm }, label = { Text(pm, fontSize = 11.5.sp) })
                    }
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes") },
                    placeholder = { Text("Optional note...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                NoorPrimaryButton(
                    text = if (editingId != null) "Update Expense" else "Save Expense",
                    icon = Icons.Outlined.CurrencyRupee,
                    testTag = "expense_save_button",
                    onClick = {
                        val amt = amountText.toDoubleOrNull() ?: 0.0
                        if (reason.isBlank() || amt <= 0.0) {
                            Toast.makeText(context, "Enter reason and valid amount.", Toast.LENGTH_SHORT).show()
                            return@NoorPrimaryButton
                        }
                        scope.launch {
                            val existing = editingId?.let { id -> expenses.find { it.id == id } }
                            vm.repo.saveExpense(
                                (existing ?: ExpenseRecord()).copy(
                                    category = category.ifBlank { "Expense" },
                                    reason = reason,
                                    amount = amt,
                                    date = date.ifBlank { Formatters.todayIso() },
                                    paymentMode = paidFrom,
                                    notes = notes
                                )
                            )
                            editingId = null; reason = ""; amountText = ""; notes = ""
                            Toast.makeText(context, "Expense saved", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Period Filter
        item(key = "period") {
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Today", "This Week", "This Month", "This Year", "All").forEach { p ->
                    FilterChip(selected = period == p, onClick = { period = p }, label = { Text(p, fontSize = 11.5.sp) })
                }
            }
        }

        // Spending by Category Card
        item(key = "by_cat") {
            Column(
                modifier = Modifier.fillMaxWidth().noorCard(palette, radius = 16.dp).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Spending by Category", color = palette.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(Formatters.formatInr(totalPeriod), color = palette.accent, fontWeight = FontWeight.Black, fontSize = 15.sp)
                }
                if (byCat.isEmpty()) {
                    Text("No expenses recorded in this period.", color = palette.textSecondary, fontSize = 13.sp)
                } else {
                    byCat.forEach { (cat, sum) ->
                        val pct = if (totalPeriod > 0) (sum / totalPeriod * 100.0) else 0.0
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(cat, color = palette.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp)
                                Text("Spent: ${Formatters.formatInr(sum)} · Share: ${pct.toInt()}%", color = palette.textSecondary, fontSize = 12.sp)
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(palette.inputBg)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth((pct / 100f).toFloat().coerceIn(0.02f, 1f))
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(palette.accent)
                                )
                            }
                        }
                    }
                }
            }
        }

        item(key = "tip") {
            Text("Tip: Swipe left on any expense card to Edit or Delete", color = palette.textMuted, fontSize = 11.5.sp)
        }

        if (periodExpenses.isEmpty()) {
            item(key = "empty") {
                Box(
                    modifier = Modifier.fillMaxWidth().noorCard(palette).padding(22.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No expenses recorded in this period.", color = palette.textSecondary, fontSize = 13.5.sp)
                }
            }
        } else {
            items(periodExpenses, key = { it.id }) { exp ->
                SwipeActionCard(
                    onEdit = {
                        editingId = exp.id
                        category = exp.category
                        reason = exp.reason
                        amountText = Formatters.formatDecimal(exp.amount)
                        date = exp.date
                        paidFrom = exp.paymentMode
                        notes = exp.notes
                    },
                    onDelete = { deleteTarget = exp }
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(exp.reason, color = palette.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(
                                "${exp.category} · ${exp.paymentMode} · ${Formatters.formatDisplayDate(exp.date)}",
                                color = palette.textSecondary,
                                fontSize = 12.sp
                            )
                            if (exp.notes.isNotBlank()) {
                                Text(exp.notes, color = palette.textMuted, fontSize = 11.5.sp)
                            }
                        }
                        Text(Formatters.formatInr(exp.amount), color = palette.statusRed, fontWeight = FontWeight.Black, fontSize = 14.5.sp)
                    }
                }
            }
        }
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            containerColor = palette.card,
            title = { Text("Delete Expense?", color = palette.statusRed, fontWeight = FontWeight.Bold) },
            text = { Text("Remove \"${target.reason}\" (${Formatters.formatInr(target.amount)})?", color = palette.textSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { vm.repo.deleteExpense(target.id) }
                    deleteTarget = null
                }) { Text("Delete", color = palette.statusRed, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } }
        )
    }
}
