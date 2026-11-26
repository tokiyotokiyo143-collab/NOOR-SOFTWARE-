package com.example.ui.components

import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.*
import com.example.data.repository.ShopRepository
import com.example.ui.theme.LocalNoorColors
import com.example.ui.theme.noorCard
import com.example.util.Formatters
import com.example.util.WhatsAppHelper
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShopDayBottomSheet(
    todayRecord: ShopDayRecord?,
    allDays: List<ShopDayRecord>,
    todayInvoices: List<Invoice>,
    todayExpenses: List<ExpenseRecord>,
    onSaveDay: (ShopDayRecord) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalNoorColors.current
    val context = LocalContext.current
    val today = Formatters.todayIso()
    val rec = todayRecord ?: ShopDayRecord(date = today)

    val billsCount = todayInvoices.count { it.status == "active" && it.docType == "invoice" }
    val salesTotal = todayInvoices.filter { it.status == "active" && it.docType == "invoice" }.sumOf { it.grandTotal }
    val collectedTotal = todayInvoices.filter { it.status == "active" && it.docType == "invoice" }.sumOf { it.amountPaid }
    val dueTotal = todayInvoices.filter { it.status == "active" && it.docType == "invoice" }.sumOf { it.balanceDue }
    val expensesTotal = todayExpenses.sumOf { it.amount }

    val previousOpenDay = allDays.firstOrNull {
        it.date < today && !it.isLeave && it.openedAt != null && it.closedAt == null
    }

    var confirmClose by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }

    val (badgeText, badgeColor) = when {
        rec.isLeave -> "Shop Leave" to palette.textSecondary
        rec.closedAt != null -> "Day closed" to palette.statusPink
        rec.openedAt != null -> "Open" to palette.statusGreen
        else -> "Not opened" to palette.statusAmber
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = palette.card,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, palette.accent.copy(alpha = 0.5f), RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Shop Day", color = palette.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Black)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(badgeColor.copy(alpha = 0.18f))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(badgeText, color = badgeColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Text(
                        "${Formatters.formatDisplayDate(today)} · Today: $billsCount bills · ${Formatters.formatInr(salesTotal)} sales",
                        color = palette.textSecondary,
                        fontSize = 12.5.sp
                    )
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, contentDescription = "Close") }
            }

            if (previousOpenDay != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(palette.statusAmber.copy(alpha = 0.14f))
                        .border(1.dp, palette.statusAmber.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = palette.statusAmber, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Previous day (${Formatters.formatDisplayDate(previousOpenDay.date)}) not closed",
                        color = palette.statusAmber,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            if (rec.isLeave) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .noorCard(palette)
                        .padding(14.dp)
                ) {
                    Text("Marked as Shop Leave", color = palette.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("Shop is closed today. No opening or closing time needed.", color = palette.textSecondary, fontSize = 12.5.sp)
                    Spacer(Modifier.height(10.dp))
                    NoorSecondaryButton(
                        text = "Cancel leave",
                        onClick = { onSaveDay(rec.copy(isLeave = false)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                // Opened & Closed pickers
                fun pickTime(currentMs: Long?, onPicked: (Long) -> Unit) {
                    val cal = Calendar.getInstance().apply { if (currentMs != null) timeInMillis = currentMs }
                    TimePickerDialog(
                        context,
                        { _, hour, min ->
                            val c = Calendar.getInstance().apply {
                                set(Calendar.HOUR_OF_DAY, hour)
                                set(Calendar.MINUTE, min)
                                set(Calendar.SECOND, 0)
                            }
                            onPicked(c.timeInMillis)
                        },
                        cal.get(Calendar.HOUR_OF_DAY),
                        cal.get(Calendar.MINUTE),
                        false
                    ).show()
                }

                Row(
                    modifier = Modifier.fillMaxWidth().noorCard(palette).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.clickable { pickTime(rec.openedAt) { onSaveDay(rec.copy(openedAt = it)) } }) {
                        Text("Opened", color = palette.textMuted, fontSize = 11.sp)
                        Text(
                            rec.openedAt?.let { Formatters.formatTime(it) } ?: "Tap to set time",
                            color = palette.textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                    Button(
                        onClick = { onSaveDay(rec.copy(openedAt = System.currentTimeMillis(), isLeave = false)) },
                        colors = ButtonDefaults.buttonColors(containerColor = palette.accent)
                    ) { Text("Now", color = Color.White) }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().noorCard(palette).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.clickable { pickTime(rec.closedAt) { onSaveDay(rec.copy(closedAt = it)) } }) {
                        Text("Closed", color = palette.textMuted, fontSize = 11.sp)
                        Text(
                            rec.closedAt?.let { Formatters.formatTime(it) } ?: "Not closed yet",
                            color = palette.textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                    Button(
                        onClick = { onSaveDay(rec.copy(closedAt = System.currentTimeMillis())) },
                        colors = ButtonDefaults.buttonColors(containerColor = palette.inputBg)
                    ) { Text("Now", color = palette.textPrimary) }
                }

                if (rec.closedAt != null && rec.closedSnapshot != null) {
                    val s = rec.closedSnapshot
                    Text(
                        "Accounts closed: ${s.billsCount} bills · sales ${Formatters.formatInr(s.salesTotal)} · collected ${Formatters.formatInr(s.collectedTotal)} · due ${Formatters.formatInr(s.dueTotal)} · expenses ${Formatters.formatInr(s.expensesTotal)}",
                        color = palette.accent,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    NoorSecondaryButton(
                        text = "Reopen day",
                        onClick = { onSaveDay(rec.copy(closedAt = null, closedSnapshot = null)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        NoorPrimaryButton(
                            text = "Close day (end today’s accounts)",
                            onClick = { confirmClose = true },
                            modifier = Modifier.weight(1.3f)
                        )
                        NoorSecondaryButton(
                            text = "Mark Shop Leave",
                            onClick = { confirmLeave = true },
                            modifier = Modifier.weight(0.9f)
                        )
                    }
                }
            }

            HorizontalDivider(color = palette.border)
            Text("History", color = palette.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            val past = allDays.filter { it.date != today }.take(45)
            if (past.isEmpty()) {
                Text("No past days yet.", color = palette.textMuted, fontSize = 12.5.sp)
            } else {
                past.forEach { d ->
                    val statusLabel = when {
                        d.isLeave -> "Leave"
                        d.closedAt != null -> "closed"
                        else -> "open"
                    }
                    val hours = if (d.openedAt != null && d.closedAt != null) {
                        " · ${Formatters.formatDurationHoursMins(d.openedAt, d.closedAt)}"
                    } else ""
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(Formatters.formatShortWeekday(d.date), color = palette.textPrimary, fontSize = 13.sp)
                        Text("$statusLabel$hours", color = palette.textSecondary, fontSize = 12.5.sp)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (confirmClose) {
        AlertDialog(
            onDismissRequest = { confirmClose = false },
            containerColor = palette.card,
            title = { Text("Close Shop Day", color = palette.textPrimary, fontWeight = FontWeight.Bold) },
            text = { Text("Close today’s day? Sales and accounts will be marked closed.", color = palette.textSecondary) },
            confirmButton = {
                Button(
                    onClick = {
                        confirmClose = false
                        onSaveDay(
                            rec.copy(
                                openedAt = rec.openedAt ?: System.currentTimeMillis(),
                                closedAt = System.currentTimeMillis(),
                                closedSnapshot = ShopDayClosedSnapshot(
                                    billsCount = billsCount,
                                    salesTotal = salesTotal,
                                    collectedTotal = collectedTotal,
                                    dueTotal = dueTotal,
                                    expensesTotal = expensesTotal
                                )
                            )
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = palette.accent)
                ) { Text("Close Day", color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClose = false }) { Text("Cancel") }
            }
        )
    }

    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            containerColor = palette.card,
            title = { Text("Mark Shop Leave", color = palette.textPrimary, fontWeight = FontWeight.Bold) },
            text = { Text("Mark today as Shop Leave?", color = palette.textSecondary) },
            confirmButton = {
                Button(
                    onClick = {
                        confirmLeave = false
                        onSaveDay(ShopDayRecord(date = today, isLeave = true))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = palette.accent)
                ) { Text("Confirm Leave", color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { confirmLeave = false }) { Text("Cancel") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PendingDuesBottomSheet(
    settings: ShopSettings,
    dueSummaries: List<ShopRepository.PartyBalanceSummary>,
    onChangeFreq: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalNoorColors.current
    val context = LocalContext.current
    val total = dueSummaries.sumOf { it.receivableBalance }
    val freqOptions = listOf(
        "off" to "Off",
        "daily" to "Every day",
        "every_3_days" to "Every 3 days",
        "weekly" to "Every week"
    )
    var dropdownOpen by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = palette.card,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, palette.accent.copy(alpha = 0.5f), RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Pending customer dues", color = palette.textPrimary, fontSize = 19.sp, fontWeight = FontWeight.Black)
                    Text("${dueSummaries.size} customers · total ${Formatters.formatInr(total)}", color = palette.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, contentDescription = "Close") }
            }

            Row(
                modifier = Modifier.fillMaxWidth().noorCard(palette).padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Remind me", color = palette.textPrimary, fontWeight = FontWeight.SemiBold)
                Box {
                    val label = freqOptions.firstOrNull { it.first == settings.duesReminderFreq }?.second ?: "Every 3 days"
                    TextButton(onClick = { dropdownOpen = true }) {
                        Text(label, color = palette.accent)
                        Icon(Icons.Outlined.ArrowDropDown, contentDescription = null, tint = palette.accent)
                    }
                    DropdownMenu(expanded = dropdownOpen, onDismissRequest = { dropdownOpen = false }) {
                        freqOptions.forEach { (key, lbl) ->
                            DropdownMenuItem(
                                text = { Text(lbl) },
                                onClick = {
                                    dropdownOpen = false
                                    onChangeFreq(key)
                                }
                            )
                        }
                    }
                }
            }

            if (dueSummaries.isEmpty()) {
                Text("No pending dues.", color = palette.textSecondary, modifier = Modifier.padding(vertical = 20.dp))
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(dueSummaries, key = { it.party.id }) { s ->
                        val daysOld = Formatters.daysBetweenIso(s.oldestDueDate)
                        val ageText = if (daysOld <= 0) "today" else "$daysOld days old"
                        Row(
                            modifier = Modifier.fillMaxWidth().noorCard(palette).padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(s.party.name, color = palette.textPrimary, fontWeight = FontWeight.Bold)
                                Text(
                                    "${s.party.phone.ifBlank { "no phone" }} · $ageText",
                                    color = palette.textSecondary,
                                    fontSize = 12.sp
                                )
                            }
                            Text(
                                Formatters.formatInr(s.receivableBalance),
                                color = palette.accent,
                                fontWeight = FontWeight.Black,
                                fontSize = 15.sp
                            )
                            Spacer(Modifier.width(8.dp))
                            IconButton(onClick = {
                                val msg = "Hello ${s.party.name}, a gentle reminder from ${settings.shopName}: your pending balance is ${Formatters.formatInr(s.receivableBalance)}. Please pay when convenient. Thank you."
                                WhatsAppHelper.openWhatsAppChat(context, s.party.phone, msg)
                            }) {
                                Icon(Icons.Outlined.Send, contentDescription = "WhatsApp reminder", tint = palette.statusGreen)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickCreateSheet(
    onAction: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalNoorColors.current
    val options = listOf(
        Triple("sale", "New Sale Bill", "Create invoice or estimate"),
        Triple("service", "New Service", "Receive phone & log part source"),
        Triple("purchase", "New Purchase", "Bulk supplier bill & stock in"),
        Triple("expense", "New Expense", "Record rent, power or supplies")
    )
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = palette.card,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, palette.accent.copy(alpha = 0.5f), RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Quick Create", color = palette.textPrimary, fontSize = 19.sp, fontWeight = FontWeight.Black)
            options.forEach { (key, title, sub) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .noorCard(palette, highlighted = key == "sale")
                        .clickable { onDismiss(); onAction(key) }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val icon = when (key) {
                        "sale" -> Icons.Outlined.ReceiptLong
                        "service" -> Icons.Outlined.Build
                        "purchase" -> Icons.Outlined.LocalShipping
                        else -> Icons.Outlined.CurrencyRupee
                    }
                    Icon(icon, contentDescription = null, tint = palette.accent, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(title, color = palette.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text(sub, color = palette.textSecondary, fontSize = 12.sp)
                    }
                    Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = palette.textMuted)
                }
            }
            Spacer(Modifier.height(14.dp))
        }
    }
}

@Composable
fun QuickActionsLongPressDialog(
    onAction: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalNoorColors.current
    val items = listOf(
        "Make bill" to "sale",
        "New purchase" to "purchase",
        "Add Stock" to "stock",
        "Add service" to "service",
        "New expense" to "expense",
        "Shop Day" to "shop_day"
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.card,
        title = { Text("Quick Actions", color = palette.textPrimary, fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items.forEach { (label, key) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .noorCard(palette)
                            .clickable { onDismiss(); onAction(key) }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(label, color = palette.textPrimary, fontWeight = FontWeight.SemiBold)
                        Icon(Icons.Outlined.ArrowForward, contentDescription = null, tint = palette.accent, modifier = Modifier.size(18.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close", color = palette.textSecondary) }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompaniesBottomSheet(
    companies: List<CompanyMeta>,
    activeCompany: CompanyMeta,
    onSwitch: (CompanyMeta) -> Unit,
    onCreate: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalNoorColors.current
    var showCreatePrompt by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<CompanyMeta?>(null) }
    var switchTarget by remember { mutableStateOf<CompanyMeta?>(null) }
    var deleteTarget by remember { mutableStateOf<CompanyMeta?>(null) }
    var nameInput by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = palette.card,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, palette.accent.copy(alpha = 0.5f), RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Companies", color = palette.textPrimary, fontSize = 19.sp, fontWeight = FontWeight.Black)
            Text(
                "Each company has its own stock, sales, services, customers and settings. You are in ${activeCompany.name}.",
                color = palette.textSecondary,
                fontSize = 12.5.sp
            )

            companies.forEach { c ->
                val isCurrent = c.id == activeCompany.id
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .noorCard(palette, highlighted = isCurrent)
                        .clickable(enabled = !isCurrent) { switchTarget = c }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            if (isCurrent) "✓ ${c.name}" else c.name,
                            color = palette.textPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            if (isCurrent) "current" else "tap to switch",
                            color = if (isCurrent) palette.accent else palette.textMuted,
                            fontSize = 11.5.sp
                        )
                    }
                    IconButton(onClick = { nameInput = c.name; renameTarget = c }) {
                        Icon(Icons.Outlined.Edit, contentDescription = "Rename company", tint = palette.textSecondary)
                    }
                    if (companies.size > 1) {
                        IconButton(onClick = { deleteTarget = c }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Delete company", tint = palette.statusRed)
                        }
                    }
                }
            }

            NoorPrimaryButton(
                text = "+ New company",
                onClick = { nameInput = ""; showCreatePrompt = true },
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                "Tip: download a backup (Settings) before deleting a company. Deleting is permanent.",
                color = palette.statusAmber,
                fontSize = 11.5.sp
            )
            Spacer(Modifier.height(14.dp))
        }
    }

    if (showCreatePrompt) {
        AlertDialog(
            onDismissRequest = { showCreatePrompt = false },
            containerColor = palette.card,
            title = { Text("New company / shop name", color = palette.textPrimary) },
            text = {
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text("Company name") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (nameInput.isNotBlank()) onCreate(nameInput)
                        showCreatePrompt = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = palette.accent)
                ) { Text("Create", color = Color.White) }
            },
            dismissButton = { TextButton(onClick = { showCreatePrompt = false }) { Text("Cancel") } }
        )
    }

    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            containerColor = palette.card,
            title = { Text("Company name", color = palette.textPrimary) },
            text = {
                OutlinedTextField(value = nameInput, onValueChange = { nameInput = it }, singleLine = true)
            },
            confirmButton = {
                Button(
                    onClick = { onRename(target.id, nameInput); renameTarget = null },
                    colors = ButtonDefaults.buttonColors(containerColor = palette.accent)
                ) { Text("Save", color = Color.White) }
            },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("Cancel") } }
        )
    }

    switchTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { switchTarget = null },
            containerColor = palette.card,
            title = { Text("Switch Company", color = palette.textPrimary) },
            text = { Text("Switch to \"${target.name}\"? The app will reload.", color = palette.textSecondary) },
            confirmButton = {
                Button(
                    onClick = { onSwitch(target); switchTarget = null; onDismiss() },
                    colors = ButtonDefaults.buttonColors(containerColor = palette.accent)
                ) { Text("Switch", color = Color.White) }
            },
            dismissButton = { TextButton(onClick = { switchTarget = null }) { Text("Cancel") } }
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            containerColor = palette.card,
            title = { Text("Delete Company", color = palette.statusRed, fontWeight = FontWeight.Bold) },
            text = { Text("Delete company \"${target.name}\" and ALL its data? This cannot be undone.", color = palette.textSecondary) },
            confirmButton = {
                Button(
                    onClick = { onDelete(target.id); deleteTarget = null },
                    colors = ButtonDefaults.buttonColors(containerColor = palette.statusRed)
                ) { Text("Delete", color = Color.White) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } }
        )
    }
}
