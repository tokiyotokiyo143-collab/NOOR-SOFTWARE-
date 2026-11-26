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
import com.example.util.PdfAndPrintHelper
import com.example.util.WhatsAppHelper
import kotlinx.coroutines.launch

@Composable
fun RepairsScreen(
    vm: MainViewModel,
    settings: ShopSettings,
    items: List<StockItem>,
    repairs: List<RepairJob>,
    isOwner: Boolean,
    canUpdateAndBill: Boolean,
    userEmail: String
) {
    val palette = LocalNoorColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val draft by vm.repairDraft.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var statusMenu by remember { mutableStateOf(false) }
    var showStockPartPicker by remember { mutableStateOf(false) }
    var stockPartSearch by remember { mutableStateOf("") }
    var collectPaymentTarget by remember { mutableStateOf<RepairJob?>(null) }
    var deleteTarget by remember { mutableStateOf<RepairJob?>(null) }

    val statuses = listOf(
        "Received" to "Checked in",
        "In-Progress" to "Bench repair",
        "Ready for Pickup" to "Tested & OK",
        "Delivered" to "Handed over"
    )
    val payModes = listOf("Cash", "UPI", "Bank", "Credit")

    val filteredRepairs = remember(repairs, searchQuery) {
        val q = searchQuery.trim().lowercase()
        repairs.filter { r ->
            if (q.isEmpty()) true
            else listOf(r.jobNumber, r.customerName, r.customerPhone, r.phoneModel, r.imei, r.problem)
                .any { it.lowercase().contains(q) }
        }
    }

    val editingExisting = remember(draft.editingRepairId, repairs) {
        draft.editingRepairId?.let { id -> repairs.find { it.id == id } }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        NoorSectionTitle(
            title = "Repair & Service",
            subtitle = "Pick parts from stock or outside purchase, auto-bill with live profit, print service slips & send Ready alerts.",
            trailing = {
                TextButton(onClick = { vm.navigateTo(NavSection.SERVICE_STATUS) }) {
                    Text("Service Status →", color = palette.accent, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                }
            }
        )

        // Form Card: Service & Part Billing
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .noorCard(palette, radius = 16.dp, highlighted = draft.editingRepairId != null)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (editingExisting != null) "Editing ${editingExisting.jobNumber}" else "New Service / Repair Job",
                    color = palette.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.5.sp
                )
                if (draft.editingRepairId != null) {
                    TextButton(onClick = { vm.resetRepairDraft() }) {
                        Text("New Job", color = palette.accent, fontSize = 12.sp)
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = draft.customerName,
                    onValueChange = { v -> vm.updateRepairDraft { it.copy(customerName = v) } },
                    label = { Text("Customer full name") },
                    placeholder = { Text("Customer full name") },
                    singleLine = true,
                    modifier = Modifier.weight(1f).testTag("repair_customer_name"),
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = draft.customerPhone,
                    onValueChange = { v -> vm.updateRepairDraft { it.copy(customerPhone = Formatters.clampPhone(v)) } },
                    label = { Text("Phone Number") },
                    placeholder = { Text("10-digit mobile") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = draft.phoneModel,
                    onValueChange = { v -> vm.updateRepairDraft { it.copy(phoneModel = v) } },
                    label = { Text("Phone Model") },
                    placeholder = { Text("e.g. Vivo Y20, Redmi Note 11") },
                    singleLine = true,
                    modifier = Modifier.weight(1f).testTag("repair_phone_model"),
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = draft.imei,
                    onValueChange = { v -> vm.updateRepairDraft { it.copy(imei = Formatters.clampImei(v)) } },
                    label = { Text("IMEI / Serial (15 digits)") },
                    placeholder = { Text("15-digit IMEI (numbers only)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            OutlinedTextField(
                value = draft.problem,
                onValueChange = { v -> vm.updateRepairDraft { it.copy(problem = v) } },
                label = { Text("Problem / Fault") },
                placeholder = { Text("e.g. Display change, Battery replacement, Charging IC") },
                modifier = Modifier.fillMaxWidth().testTag("repair_problem"),
                shape = RoundedCornerShape(12.dp)
            )

            // Repair Progress Tracker
            Text("REPAIR PROGRESS TRACKER", color = palette.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                statuses.forEach { (st, sub) ->
                    val selected = draft.status == st
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selected) palette.accent.copy(alpha = 0.2f) else palette.inputBg)
                            .border(1.dp, if (selected) palette.accent else palette.border, RoundedCornerShape(12.dp))
                            .clickable(enabled = canUpdateAndBill || draft.editingRepairId == null) {
                                vm.updateRepairDraft { it.copy(status = st) }
                            }
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Text(st, color = if (selected) palette.accent else palette.textPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text(sub, color = palette.textMuted, fontSize = 10.sp)
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = draft.date,
                    onValueChange = { v -> vm.updateRepairDraft { it.copy(date = v) } },
                    label = { Text("Date Received") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = draft.deliveryDate,
                    onValueChange = { v -> vm.updateRepairDraft { it.copy(deliveryDate = v) } },
                    label = { Text("Expected Delivery") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            OutlinedTextField(
                value = draft.notes,
                onValueChange = { v -> vm.updateRepairDraft { it.copy(notes = v) } },
                label = { Text("Technician Notes") },
                placeholder = { Text("Add progress note for \"${draft.status}\" (e.g., Display tested, customer informed)... Screen lock, SIM tray, warranty terms...") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            // Status & Activity History if editing
            if (editingExisting != null && editingExisting.statusHistory.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(palette.inputBg)
                        .border(1.dp, palette.border, RoundedCornerShape(12.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("Status & Activity History", color = palette.textPrimary, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                    editingExisting.statusHistory.reversed().forEachIndexed { idx, entry ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            NoorStatusDot(if (idx == 0) palette.accent else palette.textMuted)
                            Text(
                                "${entry.status}${if (idx == 0) " (Current)" else ""} · ${Formatters.formatTime(entry.timestamp)}${if (entry.note.isNotBlank()) " — ${entry.note}" else ""}",
                                color = if (idx == 0) palette.textPrimary else palette.textSecondary,
                                fontSize = 11.5.sp
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = palette.border)

            // Part Source Switch
            Text("PART SOURCE", color = palette.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("labour" to "Labour Only", "stock" to "From My Stock", "outside" to "Buy Outside").forEach { (key, label) ->
                    val sel = draft.partSource == key
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (sel) palette.accent.copy(alpha = 0.2f) else palette.inputBg)
                            .border(1.dp, if (sel) palette.accent else palette.border, RoundedCornerShape(10.dp))
                            .clickable {
                                vm.updateRepairDraft { it.copy(partSource = key) }
                                if (key == "stock") showStockPartPicker = true
                            }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(label, color = if (sel) palette.accent else palette.textPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }

            if (draft.partSource == "stock") {
                val selectedStock = items.find { it.id == draft.stockPartItemId }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(palette.inputBg)
                        .border(1.dp, palette.border, RoundedCornerShape(12.dp))
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(selectedStock?.displayName ?: "No stock part selected", color = palette.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        if (selectedStock != null) {
                            Text("Stock: ${selectedStock.pieces} pcs · Sell: ${Formatters.formatInr(selectedStock.sellingPrice)}", color = palette.textSecondary, fontSize = 11.5.sp)
                        }
                    }
                    TextButton(onClick = { showStockPartPicker = true }) {
                        Text("Change Part", color = palette.accent, fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (draft.partSource == "outside") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = draft.outsideSupplierName,
                        onValueChange = { v -> vm.updateRepairDraft { it.copy(outsideSupplierName = v) } },
                        label = { Text("Supplier / Shop Name") },
                        placeholder = { Text("e.g. Sri Murugan Spares") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = draft.outsidePartName,
                        onValueChange = { v -> vm.updateRepairDraft { it.copy(outsidePartName = v) } },
                        label = { Text("Part Name") },
                        placeholder = { Text("e.g. Vivo Y20 Display Combo") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = draft.outsidePartCategory,
                        onValueChange = { v -> vm.updateRepairDraft { it.copy(outsidePartCategory = v) } },
                        label = { Text("Category") },
                        placeholder = { Text("Display") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Paid To Shop", color = palette.textMuted, fontSize = 11.sp)
                        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            payModes.forEach { pm ->
                                FilterChip(
                                    selected = draft.outsidePartPaymentMode == pm,
                                    onClick = { vm.updateRepairDraft { it.copy(outsidePartPaymentMode = pm) } },
                                    label = { Text(pm, fontSize = 11.sp) }
                                )
                            }
                        }
                    }
                }
            }

            if (draft.partSource != "labour") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (isOwner) {
                        OutlinedTextField(
                            value = draft.partBuyingCostText,
                            onValueChange = { v -> vm.updateRepairDraft { it.copy(partBuyingCostText = v) } },
                            label = { Text(if (draft.partSource == "outside") "Buying Cost (₹) *" else "Part Buying Cost (₹)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                    OutlinedTextField(
                        value = draft.partChargeText,
                        onValueChange = { v -> vm.updateRepairDraft { it.copy(partChargeText = v) } },
                        label = { Text(if (draft.partSource == "outside") "Part Charge (₹)" else "Part Price Charged (₹)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = draft.serviceChargeText,
                    onValueChange = { v -> vm.updateRepairDraft { it.copy(serviceChargeText = v) } },
                    label = { Text("Service / Labour (₹)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f).testTag("repair_service_charge"),
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = draft.advancePaidText,
                    onValueChange = { v -> vm.updateRepairDraft { it.copy(advancePaidText = v) } },
                    label = { Text("Advance Paid (₹)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            // Summary strip
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(palette.inputBg)
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total Charged", color = palette.textSecondary, fontSize = 12.5.sp)
                    Text(Formatters.formatInr(draft.totalCharged), color = palette.textPrimary, fontWeight = FontWeight.Bold)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Advance Paid", color = palette.textSecondary, fontSize = 12.5.sp)
                    Text(Formatters.formatInr(draft.advancePaid), color = palette.statusGreen, fontWeight = FontWeight.Bold)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Customer Balance", color = palette.textSecondary, fontSize = 12.5.sp)
                    Text(Formatters.formatInr(draft.customerBalance), color = palette.accent, fontWeight = FontWeight.Black)
                }
                if (isOwner) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("PROFIT (Owner Only)", color = palette.textMuted, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        Text(
                            Formatters.formatInr(draft.profitOwnerOnly),
                            color = if (draft.profitOwnerOnly >= 0) palette.statusGreen else palette.statusRed,
                            fontWeight = FontWeight.Black,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            if (canUpdateAndBill) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { vm.updateRepairDraft { it.copy(createBillNow = !it.createBillNow) } },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = draft.createBillNow,
                        onCheckedChange = { c -> vm.updateRepairDraft { it.copy(createBillNow = c) } }
                    )
                    Text(
                        "Create / update Service Bill (Sales Invoice) now — posts to Sales Report, Day Book & reduces stock part by 1",
                        color = palette.textSecondary,
                        fontSize = 12.sp
                    )
                }
            }

            NoorPrimaryButton(
                text = if (draft.createBillNow) "Generate Service Bill (Invoice) & deduct stock part automatically" else "Save Service Job",
                icon = Icons.Outlined.Build,
                testTag = "repair_save_button",
                onClick = {
                    if (draft.phoneModel.isBlank()) {
                        Toast.makeText(context, "Enter phone model first.", Toast.LENGTH_SHORT).show()
                        return@NoorPrimaryButton
                    }
                    scope.launch {
                        val existing = editingExisting
                        val job = RepairJob(
                            id = existing?.id ?: java.util.UUID.randomUUID().toString(),
                            jobNumber = existing?.jobNumber ?: "",
                            date = draft.date.ifBlank { Formatters.todayIso() },
                            deliveryDate = draft.deliveryDate.ifBlank { Formatters.todayIso() },
                            deliveredDate = existing?.deliveredDate ?: "",
                            customerId = existing?.customerId,
                            customerName = draft.customerName,
                            customerPhone = draft.customerPhone,
                            phoneModel = draft.phoneModel,
                            imei = draft.imei,
                            problem = draft.problem,
                            status = draft.status,
                            partSource = draft.partSource,
                            outsideSupplierName = draft.outsideSupplierName,
                            outsidePartName = draft.outsidePartName,
                            outsidePartCategory = draft.outsidePartCategory,
                            outsidePartPaymentMode = draft.outsidePartPaymentMode,
                            outsidePartPurchaseId = existing?.outsidePartPurchaseId,
                            outsidePartItemId = draft.stockPartItemId,
                            partBuyingCost = draft.partBuyCost,
                            partCharge = draft.partCharge,
                            serviceCharge = draft.serviceCharge,
                            advancePaid = draft.advancePaid,
                            postBillPaid = existing?.postBillPaid ?: 0.0,
                            billedInvoiceId = existing?.billedInvoiceId,
                            billingPaymentMode = draft.billingPaymentMode,
                            partStockDeducted = existing?.partStockDeducted ?: false,
                            notes = draft.notes,
                            statusHistory = existing?.statusHistory ?: emptyList(),
                            createdAt = existing?.createdAt ?: System.currentTimeMillis()
                        )
                        val saved = vm.repo.saveRepair(job, draft.createBillNow, isOwner, userEmail)
                        vm.resetRepairDraft()
                        Toast.makeText(context, "Saved ${saved.jobNumber}", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Search & List
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search by ref #, customer name, phone, model, IMEI, or fault...") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, tint = palette.accent) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        )

        Text("Tip: Swipe left on any service to Edit or Delete", color = palette.textMuted, fontSize = 11.5.sp)

        if (filteredRepairs.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().noorCard(palette).padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("No repairs found.", color = palette.textSecondary, fontSize = 13.5.sp)
            }
        } else {
            filteredRepairs.forEach { r ->
                SwipeActionCard(
                    onEdit = if (canUpdateAndBill) ({ vm.loadRepairIntoDraft(r) }) else null,
                    onDelete = if (canUpdateAndBill) ({ deleteTarget = r }) else null
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(r.jobNumber, color = palette.accent, fontWeight = FontWeight.Black, fontSize = 14.sp)
                            if (r.billedInvoiceId != null) {
                                Text("· Billed", color = palette.statusGreen, fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                            }
                        }
                        val stColor = when (r.status) {
                            "Ready for Pickup" -> palette.statusGreen
                            "In-Progress" -> palette.statusAmber
                            "Delivered" -> palette.textMuted
                            else -> palette.accent
                        }
                        Text(r.status, color = stColor, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("${r.phoneModel} · ${r.problem}", color = palette.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text("${r.customerName.ifBlank { "Walk-in" }} ${if (r.customerPhone.isNotBlank()) "(${r.customerPhone})" else ""} · ${Formatters.formatDisplayDate(r.date)}", color = palette.textSecondary, fontSize = 12.sp)
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            "Total / Balance: ${Formatters.formatInr(r.totalCharged)} / ${Formatters.formatInr(r.balanceDue)}",
                            color = palette.textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.5.sp
                        )
                        if (isOwner) {
                            Text("PROFIT ${Formatters.formatInr(r.profit)}", color = palette.statusGreen, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(onClick = {
                            val pdf = PdfAndPrintHelper.generateRepairSlipPdf(context, r, settings)
                            if (pdf != null) PdfAndPrintHelper.printPdfFile(context, pdf, "REPAIR SLIP ${r.jobNumber}")
                        }) {
                            Icon(Icons.Outlined.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("REPAIR SLIP", fontSize = 11.5.sp)
                        }
                        if (r.balanceDue > 0 && canUpdateAndBill) {
                            OutlinedButton(onClick = { collectPaymentTarget = r }) {
                                Text("Collect", fontSize = 11.5.sp, color = palette.accent)
                            }
                        }
                        if (r.status == "Ready for Pickup") {
                            OutlinedButton(onClick = {
                                val msg = buildString {
                                    append("Hello *${r.customerName.ifBlank { "Customer" }}*,\n")
                                    append("Your device *${r.phoneModel}* (Ref *${r.jobNumber}*) is *READY FOR PICKUP* at *${settings.shopName}*!\n")
                                    append("Total Charge: ${Formatters.formatInr(r.totalCharged)}\n")
                                    append("Advance Paid: ${Formatters.formatInr(r.totalPaid)}\n")
                                    append("Repair Balance: *${Formatters.formatInr(r.balanceDue)}*")
                                    if (settings.address.isNotBlank()) append("\nPickup Address: ${settings.address}")
                                }
                                WhatsAppHelper.openWhatsAppChat(context, r.customerPhone, msg)
                            }) {
                                Text("Ready Alert", fontSize = 11.5.sp, color = palette.statusGreen)
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(96.dp))
    }

    // Stock Part Picker Dialog
    if (showStockPartPicker) {
        AlertDialog(
            onDismissRequest = { showStockPartPicker = false },
            containerColor = palette.card,
            title = { Text("Select Part from Stock", color = palette.textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = stockPartSearch,
                        onValueChange = { stockPartSearch = it },
                        placeholder = { Text("Search stock part...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    val hits = items.filter { !it.isService && Formatters.matchesStockQuery(it, stockPartSearch) }.take(30)
                    Column(modifier = Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState())) {
                        hits.forEach { itItem ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        vm.updateRepairDraft { d ->
                                            d.copy(
                                                stockPartItemId = itItem.id,
                                                partBuyingCostText = if (isOwner && itItem.costPrice > 0) Formatters.formatDecimal(itItem.costPrice) else d.partBuyingCostText,
                                                partChargeText = if (itItem.sellingPrice > 0) Formatters.formatDecimal(itItem.sellingPrice) else d.partChargeText
                                            )
                                        }
                                        showStockPartPicker = false
                                    }
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(itItem.displayName, color = palette.textPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                Text("${itItem.pieces} pcs · ${Formatters.formatInr(itItem.sellingPrice)}", color = palette.accent, fontSize = 12.sp)
                            }
                            HorizontalDivider(color = palette.border.copy(alpha = 0.4f))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showStockPartPicker = false }) { Text("Close") }
            }
        )
    }

    // Collect Payment Dialog
    collectPaymentTarget?.let { target ->
        var amtText by remember { mutableStateOf(Formatters.formatDecimal(target.balanceDue)) }
        var mode by remember { mutableStateOf(target.billingPaymentMode.ifBlank { "Cash" }) }
        var note by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { collectPaymentTarget = null },
            containerColor = palette.card,
            title = { Text("Collect Payment · ${target.jobNumber}", color = palette.textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Total Bill: ${Formatters.formatInr(target.totalCharged)}", color = palette.textSecondary, fontSize = 12.5.sp)
                    Text("Paid So Far: ${Formatters.formatInr(target.totalPaid)}", color = palette.statusGreen, fontSize = 12.5.sp)
                    Text("Pending Bal: ${Formatters.formatInr(target.balanceDue)}", color = palette.accent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    OutlinedTextField(
                        value = amtText,
                        onValueChange = { amtText = it },
                        label = { Text("Amount Received (₹) *") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Cash", "UPI", "Card", "Bank Transfer").forEach { m ->
                            FilterChip(selected = mode == m, onClick = { mode = m }, label = { Text(m, fontSize = 11.sp) })
                        }
                    }
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("Notes") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val amt = amtText.toDoubleOrNull() ?: 0.0
                    if (amt > 0) {
                        scope.launch {
                            vm.repo.collectRepairPayment(target.id, amt, mode, note, isOwner)
                        }
                    }
                    collectPaymentTarget = null
                }) { Text("Save", color = palette.accent, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { collectPaymentTarget = null }) { Text("Cancel") }
            }
        )
    }

    // Delete Service Dialog
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            containerColor = palette.card,
            title = { Text("Delete ${target.jobNumber}?", color = palette.statusRed, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("• Stock part quantity used will be restored back to inventory.", color = palette.textSecondary, fontSize = 13.sp)
                    Text("• Linked Service Bill invoice will also be removed cleanly.", color = palette.textSecondary, fontSize = 13.sp)
                    Text("• Auto-recorded outside purchase bill will also be removed cleanly.", color = palette.textSecondary, fontSize = 13.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { vm.repo.deleteRepair(target.id, isOwner) }
                    deleteTarget = null
                }) { Text("Delete", color = palette.statusRed, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Keep", color = palette.textPrimary) }
            }
        )
    }
}
