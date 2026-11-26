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
import java.util.UUID

@Composable
fun SalesScreen(
    vm: MainViewModel,
    settings: ShopSettings,
    items: List<StockItem>,
    invoices: List<Invoice>,
    isOwner: Boolean,
    canEditReturnCancel: Boolean
) {
    val palette = LocalNoorColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val draft by vm.saleDraft.collectAsState()

    var showHeaderTip by remember { mutableStateOf(true) }
    var docTypeMenu by remember { mutableStateOf(false) }
    var gstMenu by remember { mutableStateOf(false) }
    var stockSearch by remember { mutableStateOf("") }
    var customServiceName by remember { mutableStateOf("") }
    var customServicePrice by remember { mutableStateOf("") }
    var showBarcodeScanner by remember { mutableStateOf(false) }
    var previewInvoice by remember { mutableStateOf<Invoice?>(null) }
    var showHistoryPrint by remember { mutableStateOf(false) }
    var invoiceSearch by remember { mutableStateOf("") }

    // v10 Part Source Switch (From My Stock picker)
    var partPickerQuery by remember { mutableStateOf("") }
    var partPickerCat by remember { mutableStateOf("All Categories") }
    var showPartPicker by remember { mutableStateOf(false) }

    val docTypes = listOf(
        "invoice" to "Sale Invoice",
        "estimate" to "Estimate / Quote",
        "order" to "Sale Order",
        "challan" to "Delivery Challan",
        "return" to "Sale Return"
    )
    val gstOptions = listOf(
        0.0 to "No GST (0%)",
        5.0 to "5% GST",
        12.0 to "12% GST",
        18.0 to "18% GST",
        28.0 to "28% GST"
    )
    val paymentModes = listOf("Cash", "UPI", "Card", "Bank Transfer", "Credit (Udhar)")

    fun addStockItemToBill(item: StockItem) {
        vm.updateSaleDraft { d ->
            val list = d.lines.toMutableList()
            val idx = list.indexOfFirst { it.itemId == item.id }
            if (idx >= 0) {
                val cur = list[idx]
                list[idx] = cur.copy(qty = cur.qty + 1)
            } else {
                list.add(
                    InvoiceLineItem(
                        itemId = item.id,
                        name = item.displayName,
                        category = item.category,
                        brand = item.brand,
                        model = item.model,
                        color = item.color,
                        qty = 1,
                        rate = item.sellingPrice,
                        costPrice = if (isOwner) item.costPrice else 0.0,
                        isService = item.isService,
                        partSource = if (item.isService) "labour" else "stock"
                    )
                )
            }
            d.copy(lines = list)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (showHeaderTip) {
            Row(
                modifier = Modifier.fillMaxWidth().noorCard(palette).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Sale & Repair Invoices", color = palette.textPrimary, fontWeight = FontWeight.Black, fontSize = 18.sp)
                    Text("Create bills, share on WhatsApp, print white receipts with UPI QR, edit or return.", color = palette.textSecondary, fontSize = 12.sp)
                }
                TextButton(onClick = { showHeaderTip = false }) { Text("Dismiss", color = palette.accent) }
            }
        } else {
            NoorSectionTitle(
                title = "Sale & Repair Invoices",
                trailing = {
                    OutlinedButton(onClick = { showHistoryPrint = true }) {
                        Icon(Icons.Outlined.Print, contentDescription = null, tint = palette.accent, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Print History", fontSize = 12.sp, color = palette.accent)
                    }
                }
            )
        }

        // Document Type & Bill Type Chips
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.weight(1f)) {
                val currentDocLabel = docTypes.firstOrNull { it.first == draft.docType }?.second ?: "Sale Invoice"
                OutlinedButton(onClick = { docTypeMenu = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(currentDocLabel, color = palette.textPrimary, maxLines = 1)
                    Icon(Icons.Outlined.ArrowDropDown, contentDescription = null)
                }
                DropdownMenu(expanded = docTypeMenu, onDismissRequest = { docTypeMenu = false }) {
                    docTypes.forEach { (k, lbl) ->
                        DropdownMenuItem(text = { Text(lbl) }, onClick = {
                            vm.updateSaleDraft { it.copy(docType = k) }
                            docTypeMenu = false
                        })
                    }
                }
            }
            FilterChip(
                selected = draft.billType == "product",
                onClick = { vm.updateSaleDraft { it.copy(billType = "product") } },
                label = { Text("Product Sale", fontSize = 12.sp) }
            )
            FilterChip(
                selected = draft.billType == "repair",
                onClick = { vm.updateSaleDraft { it.copy(billType = "repair") } },
                label = { Text("Repair Bill", fontSize = 12.sp) }
            )
        }

        // Customer & Device Card
        Column(modifier = Modifier.fillMaxWidth().noorCard(palette).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = draft.customerName,
                    onValueChange = { v -> vm.updateSaleDraft { it.copy(customerName = v) } },
                    label = { Text("Customer name") },
                    placeholder = { Text("Enter customer name") },
                    singleLine = true,
                    modifier = Modifier.weight(1f).testTag("sale_customer_name")
                )
                OutlinedTextField(
                    value = draft.customerPhone,
                    onValueChange = { v -> vm.updateSaleDraft { it.copy(customerPhone = v) } },
                    label = { Text("Phone") },
                    placeholder = { Text("10-digit WhatsApp number") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.weight(1f).testTag("sale_customer_phone")
                )
            }
            OutlinedTextField(
                value = draft.notes,
                onValueChange = { v -> vm.updateSaleDraft { it.copy(notes = v) } },
                label = { Text("Device / Repair Complaint Note") },
                placeholder = { Text("e.g. Vivo Y20 Display Replacement / Warranty 30 days") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = draft.imei,
                onValueChange = { v -> vm.updateSaleDraft { it.copy(imei = Formatters.clampImei(v)) } },
                label = { Text("IMEI Number (15 Digits Only)") },
                placeholder = { Text("Optional 15-digit numeric IMEI") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // v10 CHANGE (Section 7): PART SOURCE SWITCH IN THE NORMAL BILL
        Column(
            modifier = Modifier.fillMaxWidth().noorCard(palette, highlighted = draft.billType == "repair").padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Part Source", color = palette.accent, fontWeight = FontWeight.Black, fontSize = 14.sp)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    "labour" to "Labour Only",
                    "stock" to "From My Stock",
                    "outside" to "Bought Outside"
                ).forEach { (key, label) ->
                    val selected = draft.partSourceMode == key
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selected) palette.accent else palette.inputBg)
                            .border(1.dp, if (selected) palette.accent else palette.border, RoundedCornerShape(10.dp))
                            .clickable {
                                vm.updateSaleDraft { it.copy(partSourceMode = key) }
                                if (key == "stock" && draft.linkedPartItemId == null) showPartPicker = true
                            }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(label, color = if (selected) Color.White else palette.textPrimary, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            when (draft.partSourceMode) {
                "labour" -> {
                    Text(
                        "No spare part — only service/labour charge. No stock deduction.",
                        color = palette.textSecondary,
                        fontSize = 12.sp
                    )
                }
                "stock" -> {
                    val picked = items.find { it.id == draft.linkedPartItemId }
                    if (picked != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth().noorCard(palette).padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(picked.displayName, color = palette.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                                Text("${picked.category} · Stock: ${picked.pieces} · Sell ${Formatters.formatInr(picked.sellingPrice)}", color = palette.textSecondary, fontSize = 11.5.sp)
                            }
                            TextButton(onClick = { showPartPicker = !showPartPicker }) {
                                Text("Change Part", color = palette.accent, fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        NoorSecondaryButton(
                            text = "Select Spare Part from Stock",
                            onClick = { showPartPicker = true },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    if (showPartPicker) {
                        OutlinedTextField(
                            value = partPickerQuery,
                            onValueChange = { partPickerQuery = it },
                            placeholder = { Text("Search stock part by model / display / battery...") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            (listOf("All Categories") + settings.customCategories).forEach { cat ->
                                FilterChip(selected = partPickerCat == cat, onClick = { partPickerCat = cat }, label = { Text(cat, fontSize = 11.sp) })
                            }
                        }
                        val partMatches = remember(items, partPickerQuery, partPickerCat) {
                            items.filter { item ->
                                !item.isService &&
                                    (partPickerCat == "All Categories" || item.category.equals(partPickerCat, true)) &&
                                    Formatters.matchesStockQuery(item, partPickerQuery)
                            }.take(15)
                        }
                        if (partMatches.isEmpty()) {
                            Text("No matching spare part found in stock.", color = palette.textSecondary, fontSize = 12.sp)
                        } else {
                            partMatches.forEach { part ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(part.displayName, color = palette.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                        Text("${part.category} · ${part.pieces} pcs · ${Formatters.formatInr(part.sellingPrice)}", color = palette.textMuted, fontSize = 11.5.sp)
                                    }
                                    Button(
                                        onClick = {
                                            vm.updateSaleDraft { it.copy(linkedPartItemId = part.id) }
                                            addStockItemToBill(part)
                                            showPartPicker = false
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = palette.accent),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                    ) { Text("Select", color = Color.White, fontSize = 12.sp) }
                                }
                            }
                        }
                    }
                }
                "outside" -> {
                    OutlinedTextField(
                        value = draft.outsideSupplierName,
                        onValueChange = { v -> vm.updateSaleDraft { it.copy(outsideSupplierName = v) } },
                        label = { Text("Supplier / shop name") },
                        placeholder = { Text("e.g. Sri Murugan Spares") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = draft.outsidePartName,
                            onValueChange = { v -> vm.updateSaleDraft { it.copy(outsidePartName = v) } },
                            label = { Text("Part name") },
                            placeholder = { Text("e.g. Vivo Y20 Display Combo") },
                            singleLine = true,
                            modifier = Modifier.weight(1.3f)
                        )
                        OutlinedTextField(
                            value = draft.outsidePartCategory,
                            onValueChange = { v -> vm.updateSaleDraft { it.copy(outsidePartCategory = v) } },
                            label = { Text("Category") },
                            singleLine = true,
                            modifier = Modifier.weight(0.8f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = draft.outsidePartCostText,
                            onValueChange = { v -> vm.updateSaleDraft { it.copy(outsidePartCostText = v) } },
                            label = { Text("Buying Cost (₹) *") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = draft.outsidePartChargeText,
                            onValueChange = { v -> vm.updateSaleDraft { it.copy(outsidePartChargeText = v) } },
                            label = { Text("Part Charge (₹)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Text("Paid To Shop", color = palette.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Cash", "UPI", "Bank", "Credit").forEach { m ->
                            FilterChip(
                                selected = draft.outsidePartPaymentMode == m,
                                onClick = { vm.updateSaleDraft { it.copy(outsidePartPaymentMode = m) } },
                                label = { Text(m, fontSize = 11.5.sp) }
                            )
                        }
                    }
                }
            }

            if (draft.billType == "repair" || draft.partSourceMode != "labour") {
                OutlinedTextField(
                    value = draft.labourChargeText,
                    onValueChange = { v -> vm.updateSaleDraft { it.copy(labourChargeText = v) } },
                    label = { Text("Service / Labour (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (isOwner) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("PROFIT (Owner Only)", color = palette.textSecondary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text(
                        Formatters.formatInr(draft.liveProfitOwnerOnly),
                        color = if (draft.liveProfitOwnerOnly >= 0) palette.statusGreen else palette.statusRed,
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp
                    )
                }
            }
        }

        // Add Items from Inventory & Quick Custom Service Line
        Column(modifier = Modifier.fillMaxWidth().noorCard(palette).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = stockSearch,
                onValueChange = { stockSearch = it },
                label = { Text("Add Items from Inventory (Search or Scan Barcode)") },
                placeholder = { Text("Search model, category, or scan barcode + Enter...") },
                trailingIcon = {
                    IconButton(onClick = { showBarcodeScanner = true }) {
                        Icon(Icons.Outlined.QrCodeScanner, contentDescription = "Scan barcode", tint = palette.accent)
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            if (stockSearch.isNotBlank()) {
                val hits = remember(stockSearch, items) {
                    items.filter { Formatters.matchesStockQuery(it, stockSearch) }.take(8)
                }
                hits.forEach { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { addStockItemToBill(item); stockSearch = "" }
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.displayName, color = palette.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text("${item.category} · ${item.pieces} ${item.unit} · ${Formatters.formatInr(item.sellingPrice)}", color = palette.textMuted, fontSize = 11.5.sp)
                        }
                        Text("+ Add", color = palette.accent, fontWeight = FontWeight.Bold)
                    }
                    HorizontalDivider(color = palette.border.copy(alpha = 0.4f))
                }
            }

            Text("Or Add Quick Repair / Custom Service Line", color = palette.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = customServiceName,
                    onValueChange = { customServiceName = it },
                    placeholder = { Text("Service name (e.g. Mic change)") },
                    singleLine = true,
                    modifier = Modifier.weight(1.4f)
                )
                OutlinedTextField(
                    value = customServicePrice,
                    onValueChange = { customServicePrice = it },
                    placeholder = { Text("Price (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(0.8f)
                )
                Button(
                    onClick = {
                        val price = customServicePrice.toDoubleOrNull() ?: 0.0
                        if (customServiceName.isNotBlank()) {
                            vm.updateSaleDraft { d ->
                                d.copy(
                                    lines = d.lines + InvoiceLineItem(
                                        name = customServiceName.trim(),
                                        qty = 1,
                                        rate = price,
                                        isService = true,
                                        partSource = "labour"
                                    )
                                )
                            }
                            customServiceName = ""
                            customServicePrice = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = palette.accent)
                ) { Text("Add", color = Color.White) }
            }

            // Bill lines
            draft.lines.forEach { line ->
                Row(
                    modifier = Modifier.fillMaxWidth().noorCard(palette).padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1.3f)) {
                        Text(line.name, color = palette.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("${Formatters.formatInr(line.rate)} × ${line.qty} = ${Formatters.formatInr(line.total)}", color = palette.accent, fontSize = 12.sp)
                    }
                    IconButton(onClick = {
                        vm.updateSaleDraft { d ->
                            val updated = d.lines.mapNotNull {
                                if (it.id != line.id) it
                                else if (it.qty > 1) it.copy(qty = it.qty - 1) else null
                            }
                            d.copy(lines = updated)
                        }
                    }, modifier = Modifier.size(30.dp)) {
                        Text("-", color = palette.textPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                    Text(line.qty.toString(), color = palette.textPrimary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp))
                    IconButton(onClick = {
                        vm.updateSaleDraft { d ->
                            d.copy(lines = d.lines.map { if (it.id == line.id) it.copy(qty = it.qty + 1) else it })
                        }
                    }, modifier = Modifier.size(30.dp)) {
                        Text("+", color = palette.accent, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                }
            }
        }

        // Discount, GST, Payment Mode, Cash Tender Helper & Totals
        Column(modifier = Modifier.fillMaxWidth().noorCard(palette).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilterChip(
                    selected = draft.discountType == "flat",
                    onClick = { vm.updateSaleDraft { it.copy(discountType = "flat") } },
                    label = { Text("Flat (₹)", fontSize = 11.5.sp) }
                )
                FilterChip(
                    selected = draft.discountType == "percent",
                    onClick = { vm.updateSaleDraft { it.copy(discountType = "percent") } },
                    label = { Text("Percent (%)", fontSize = 11.5.sp) }
                )
                OutlinedTextField(
                    value = draft.discountAmountText,
                    onValueChange = { v -> vm.updateSaleDraft { it.copy(discountAmountText = v) } },
                    label = { Text("Discount") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            Box {
                val gstLabel = gstOptions.firstOrNull { it.first == draft.gstRate }?.second ?: "No GST (0%)"
                OutlinedButton(onClick = { gstMenu = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("GST (%): $gstLabel", color = palette.textPrimary)
                    Icon(Icons.Outlined.ArrowDropDown, contentDescription = null)
                }
                DropdownMenu(expanded = gstMenu, onDismissRequest = { gstMenu = false }) {
                    gstOptions.forEach { (rate, lbl) ->
                        DropdownMenuItem(text = { Text(lbl) }, onClick = {
                            vm.updateSaleDraft { it.copy(gstRate = rate) }
                            gstMenu = false
                        })
                    }
                }
            }

            Text("Payment Mode", color = palette.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                paymentModes.forEach { m ->
                    FilterChip(
                        selected = draft.paymentMode == m,
                        onClick = {
                            vm.updateSaleDraft {
                                if (m.startsWith("Credit", true)) it.copy(paymentMode = m, amountPaidText = "0", amountPaidTouched = true)
                                else it.copy(paymentMode = m, amountPaidTouched = false)
                            }
                        },
                        label = { Text(m, fontSize = 11.5.sp) }
                    )
                }
            }

            OutlinedTextField(
                value = if (draft.amountPaidTouched) draft.amountPaidText else Formatters.formatDecimal(draft.effectivePaid),
                onValueChange = { v -> vm.updateSaleDraft { it.copy(amountPaidText = v, amountPaidTouched = true) } },
                label = { Text("Amount Paid (₹)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                enabled = !draft.paymentMode.startsWith("Credit", true),
                modifier = Modifier.fillMaxWidth()
            )

            // Cash Tender Helper (shows only when Payment Mode is Cash)
            if (draft.paymentMode == "Cash") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = draft.cashTenderText,
                        onValueChange = { v -> vm.updateSaleDraft { it.copy(cashTenderText = v) } },
                        label = { Text("Cash Tender (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = Formatters.formatInr(draft.cashChange),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Cash Given (₹)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Text(
                "UPI QR code from Settings will appear on the printable customer invoice.",
                color = palette.textMuted,
                fontSize = 11.5.sp
            )

            HorizontalDivider(color = palette.border)
            TotalLine("Subtotal", Formatters.formatInr(draft.subtotal))
            if (draft.discountValue > 0) TotalLine("Discount", "-${Formatters.formatInr(draft.discountValue)}")
            if (draft.gstRate > 0) TotalLine("GST (${draft.gstRate.toInt()}%)", Formatters.formatInr(draft.gstAmount))
            TotalLine("Grand Total", Formatters.formatInr(draft.grandTotal), bold = true, valueColor = palette.accent)
            TotalLine("Balance Due", Formatters.formatInr(draft.balanceDue), bold = draft.balanceDue > 0, valueColor = if (draft.balanceDue > 0) palette.statusAmber else palette.statusGreen)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NoorSecondaryButton(
                    text = "Clear",
                    onClick = { vm.resetSaleDraft() },
                    modifier = Modifier.weight(0.7f)
                )
                NoorPrimaryButton(
                    text = if (draft.editingInvoiceId != null) "Update Bill" else "Save & Print Bill",
                    onClick = {
                        scope.launch {
                            val saved = vm.saveCurrentSaleDraft()
                            previewInvoice = saved
                            if (settings.whatsappAutoSend && saved.customerPhone.isNotBlank()) {
                                val res = WhatsAppHelper.sendViaMetaCloudApi(
                                    settings,
                                    saved.customerPhone,
                                    "Thank you for visiting ${settings.shopName}! Bill ${saved.invoiceNumber} Total: ${Formatters.formatInr(saved.grandTotal)}, Paid: ${Formatters.formatInr(saved.amountPaid)}, Due: ${Formatters.formatInr(saved.balanceDue)}."
                                )
                                res.onSuccess { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
                            }
                        }
                    },
                    modifier = Modifier.weight(1.4f),
                    testTag = "sale_save_bill_button"
                )
            }
        }

        // Recent Invoices (Last 10 + Search)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Recent Invoices", color = palette.textPrimary, fontWeight = FontWeight.Black, fontSize = 16.sp)
            OutlinedTextField(
                value = invoiceSearch,
                onValueChange = { invoiceSearch = it },
                placeholder = { Text("Search invoices by customer name, phone, or invoice #...") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, tint = palette.accent) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            val list = remember(invoices, invoiceSearch) {
                val q = invoiceSearch.trim().lowercase()
                val base = if (q.isBlank()) invoices.take(10) else invoices.filter {
                    it.invoiceNumber.lowercase().contains(q) ||
                        it.customerName.lowercase().contains(q) ||
                        it.customerPhone.lowercase().contains(q)
                }
                base
            }
            if (list.isEmpty()) {
                Text(
                    if (invoiceSearch.isBlank()) "No recent invoices yet — Your last 10 generated bills will appear here for instant view, edit, or reprint."
                    else "No matching invoices found.",
                    color = palette.textSecondary,
                    fontSize = 12.5.sp
                )
            } else {
                list.forEach { inv ->
                    val statusChip = when {
                        inv.status == "cancelled" -> "Cancelled" to palette.statusRed
                        inv.docType == "return" -> "Return" to palette.statusAmber
                        inv.balanceDue > 0 -> "Pending" to palette.statusAmber
                        else -> "Paid" to palette.statusGreen
                    }
                    Column(modifier = Modifier.fillMaxWidth().noorCard(palette).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${inv.invoiceNumber} · ${inv.customerName.ifBlank { "Walk-in" }}", color = palette.textPrimary, fontWeight = FontWeight.Bold)
                                Text("${Formatters.formatDisplayDate(inv.date)} · ${inv.paymentMode}", color = palette.textSecondary, fontSize = 11.5.sp)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(Formatters.formatInr(inv.grandTotal), color = palette.textPrimary, fontWeight = FontWeight.Black)
                                Text(statusChip.first, color = statusChip.second, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TextButton(onClick = { previewInvoice = inv }) { Text("View / Reprint", color = palette.accent, fontSize = 12.sp) }
                            TextButton(onClick = {
                                val file = PdfAndPrintHelper.generateInvoicePdf(context, inv, settings)
                                if (file != null) PdfAndPrintHelper.sharePdfFile(context, file, "Bill ${inv.invoiceNumber}")
                            }) { Text("Share", color = palette.statusGreen, fontSize = 12.sp) }
                            if (canEditReturnCancel && inv.status == "active") {
                                TextButton(onClick = { vm.loadInvoiceIntoDraft(inv) }) { Text("Edit", color = palette.textPrimary, fontSize = 12.sp) }
                                TextButton(onClick = {
                                    vm.loadInvoiceIntoDraft(inv.copy(id = UUID.randomUUID().toString(), invoiceNumber = "", docType = "return"))
                                }) { Text("Return", color = palette.statusAmber, fontSize = 12.sp) }
                                TextButton(onClick = { scope.launch { vm.repo.cancelInvoice(inv.id, isOwner) } }) {
                                    Text("Cancel", color = palette.statusRed, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(96.dp))
    }

    if (showBarcodeScanner) {
        CameraBarcodeScannerDialog(
            title = "Scan Barcode to Add Item",
            onScanned = { code ->
                val exact = items.firstOrNull { it.barcode.equals(code, true) || it.imei.equals(code, true) }
                if (exact != null) {
                    addStockItemToBill(exact)
                    Toast.makeText(context, "Added ${exact.displayName}", Toast.LENGTH_SHORT).show()
                } else {
                    stockSearch = code
                }
            },
            onDismiss = { showBarcodeScanner = false }
        )
    }

    previewInvoice?.let { inv ->
        InvoicePrintPreviewDialog(invoice = inv, settings = settings, onDismiss = { previewInvoice = null })
    }

    if (showHistoryPrint) {
        PrintSalesHistoryDialog(invoices = invoices, settings = settings, onDismiss = { showHistoryPrint = false })
    }
}

@Composable
private fun TotalLine(label: String, value: String, bold: Boolean = false, valueColor: Color? = null) {
    val palette = LocalNoorColors.current
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = if (bold) palette.textPrimary else palette.textSecondary, fontWeight = if (bold) FontWeight.Black else FontWeight.Normal, fontSize = if (bold) 15.sp else 13.sp)
        Text(value, color = valueColor ?: palette.textPrimary, fontWeight = if (bold) FontWeight.Black else FontWeight.SemiBold, fontSize = if (bold) 15.sp else 13.sp)
    }
}
