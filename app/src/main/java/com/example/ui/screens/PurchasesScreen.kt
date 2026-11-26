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
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun PurchasesScreen(
    vm: MainViewModel,
    settings: ShopSettings,
    items: List<StockItem>,
    purchases: List<Purchase>,
    parties: List<Party>,
    isOwner: Boolean,
    canEditDelete: Boolean
) {
    val palette = LocalNoorColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val draft by vm.purchaseDraft.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var entryTab by remember { mutableStateOf("Grid") } // Grid | Column Paste | Quick-Add Item | Paste / CSV

    // Column Paste states
    var colModels by remember { mutableStateOf("") }
    var colColors by remember { mutableStateOf("") }
    var colCategories by remember { mutableStateOf("") }
    var colBrands by remember { mutableStateOf("") }
    var colQtys by remember { mutableStateOf("") }
    var colBuys by remember { mutableStateOf("") }
    var colSells by remember { mutableStateOf("") }
    var colParty by remember { mutableStateOf("") }

    // Quick-Add states
    var quickSearch by remember { mutableStateOf("") }

    // Paste / CSV states
    var csvText by remember { mutableStateOf("") }

    // Dialog states
    var duplicateWarningBill by remember { mutableStateOf<Purchase?>(null) }
    var deleteTarget by remember { mutableStateOf<Purchase?>(null) }
    var applyAllBuyText by remember { mutableStateOf("") }
    var applyAllSellText by remember { mutableStateOf("") }

    val suppliers = remember(parties) {
        parties.filter { it.type == "supplier" || it.type == "both" }
    }
    val postingBills = remember(purchases) {
        purchases.filter { it.status == "active" && it.docType == "purchase" }
    }
    val totalPurchased = postingBills.sumOf { it.totalAmount }
    val totalPaid = postingBills.sumOf { it.amountPaid }
    val totalDue = postingBills.sumOf { it.balanceDue }

    val filteredPurchases = remember(purchases, searchQuery) {
        val q = searchQuery.trim().lowercase()
        purchases.filter { p ->
            if (q.isEmpty()) true
            else listOf(p.supplierName, p.billNumber, p.supplierBillNo)
                .any { it.lowercase().contains(q) } ||
                p.items.any { it.model.lowercase().contains(q) || it.brand.lowercase().contains(q) }
        }
    }

    val payModes = listOf("Cash", "UPI", "Card", "Bank Transfer", "Credit (Pay Later)")

    suspend fun commitSavePurchase() {
        val validRows = draft.rows.filter { it.model.isNotBlank() }
        if (validRows.isEmpty()) {
            Toast.makeText(context, "Add at least one item row.", Toast.LENGTH_SHORT).show()
            return
        }
        val existing = draft.editingPurchaseId?.let { id -> purchases.find { it.id == id } }
        val p = Purchase(
            id = existing?.id ?: UUID.randomUUID().toString(),
            billNumber = existing?.billNumber ?: "",
            docType = draft.docType,
            supplierBillNo = draft.supplierBillNo,
            date = draft.date.ifBlank { Formatters.todayIso() },
            supplierId = existing?.supplierId,
            supplierName = draft.supplierName.ifBlank { "Cash Supplier" },
            supplierPhone = draft.supplierPhone,
            items = validRows,
            discount = draft.discount,
            transportExtra = draft.transport,
            totalAmount = draft.billTotal,
            paymentMode = draft.paymentMode,
            amountPaid = draft.effectivePaid,
            balanceDue = draft.balanceDue,
            status = "active",
            generateBarcodes = draft.generateBarcodes,
            notes = draft.notes,
            createdAt = existing?.createdAt ?: System.currentTimeMillis()
        )
        val saved = vm.repo.savePurchase(p, isOwner)
        if (saved.generateBarcodes) {
            val labelPairs = saved.items.mapNotNull { line ->
                if (line.barcode.isBlank()) null
                else StockItem(
                    category = line.category,
                    brand = line.brand,
                    model = line.model,
                    color = line.color,
                    barcode = line.barcode,
                    sellingPrice = line.sellPrice,
                    pieces = line.qty
                ) to line.qty.coerceAtLeast(1)
            }
            if (labelPairs.isNotEmpty()) {
                val pdf = PdfAndPrintHelper.generateBarcodeLabelsPdf(
                    context = context,
                    items = labelPairs,
                    settings = settings,
                    layout = "A4_3",
                    showShopName = true,
                    showPrice = true,
                    showCategory = true
                )
                if (pdf != null) PdfAndPrintHelper.printPdfFile(context, pdf, "Purchase_Labels_${saved.billNumber}")
            }
        }
        vm.resetPurchaseDraft()
        Toast.makeText(context, "Saved ${saved.billNumber}", Toast.LENGTH_SHORT).show()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        NoorSectionTitle(
            title = "Purchases & Bulk Stock In",
            subtitle = "Spreadsheet grid, multi-line paste, CSV import, last buy auto-fill & supplier ledger sync."
        )

        // 4 Summary Cards
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PurchaseSummaryBox("Posting Bills", postingBills.size.toString(), palette.textPrimary, Modifier.weight(1f))
            PurchaseSummaryBox("Total Purchased", Formatters.formatInr(totalPurchased), palette.accent, Modifier.weight(1f))
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PurchaseSummaryBox("Amount Paid", Formatters.formatInr(totalPaid), palette.statusGreen, Modifier.weight(1f))
            PurchaseSummaryBox("Balance Due (Credit)", Formatters.formatInr(totalDue), palette.statusAmber, Modifier.weight(1f))
        }

        // Purchase Form Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .noorCard(palette, radius = 16.dp, highlighted = draft.editingPurchaseId != null)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("purchase" to "Purchase Bill", "order" to "Purchase Order", "return" to "Return / Debit Note").forEach { (k, label) ->
                        FilterChip(
                            selected = draft.docType == k,
                            onClick = { vm.updatePurchaseDraft { it.copy(docType = k) } },
                            label = { Text(label, fontSize = 11.sp) }
                        )
                    }
                }
                if (draft.editingPurchaseId != null) {
                    TextButton(onClick = { vm.resetPurchaseDraft() }) {
                        Text("Discard", color = palette.statusRed, fontSize = 12.sp)
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = draft.supplierName,
                    onValueChange = { v -> vm.updatePurchaseDraft { it.copy(supplierName = v) } },
                    label = { Text("Party / Supplier") },
                    placeholder = { Text("e.g. Coimbatore Mobile Hub") },
                    singleLine = true,
                    modifier = Modifier.weight(1f).testTag("purchase_supplier"),
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = draft.supplierPhone,
                    onValueChange = { v -> vm.updatePurchaseDraft { it.copy(supplierPhone = Formatters.clampPhone(v)) } },
                    label = { Text("Supplier Phone") },
                    placeholder = { Text("Optional phone") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            if (suppliers.isNotEmpty()) {
                Text("Saved Suppliers:", color = palette.textMuted, fontSize = 11.5.sp)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    suppliers.take(15).forEach { sup ->
                        AssistChip(
                            onClick = {
                                vm.updatePurchaseDraft {
                                    it.copy(supplierName = sup.name, supplierPhone = sup.phone)
                                }
                            },
                            label = { Text(sup.name, fontSize = 11.5.sp) }
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = draft.date,
                    onValueChange = { v -> vm.updatePurchaseDraft { it.copy(date = v) } },
                    label = { Text("Bill Date") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = draft.supplierBillNo,
                    onValueChange = { v -> vm.updatePurchaseDraft { it.copy(supplierBillNo = v) } },
                    label = { Text("Supplier Invoice / Ref # (optional)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            OutlinedTextField(
                value = draft.notes,
                onValueChange = { v -> vm.updatePurchaseDraft { it.copy(notes = v) } },
                label = { Text("Notes (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            // Entry Mode Tabs
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("Grid", "Column Paste", "Quick-Add Item", "Paste / CSV").forEach { tab ->
                    val sel = entryTab == tab
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (sel) palette.accent else palette.inputBg)
                            .border(1.dp, if (sel) palette.accent else palette.border, RoundedCornerShape(10.dp))
                            .clickable { entryTab = tab }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(tab, color = if (sel) Color.White else palette.textPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }

            when (entryTab) {
                "Column Paste" -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(palette.inputBg)
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Column-Wise Bulk Paste (Copy Each Excel Column Separately)", color = palette.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(
                            "Paste directly from Excel/Sheets into each column box. Entering a single Category, Brand, Qty, Price, or Party broadcasts it to all rows.",
                            color = palette.textSecondary,
                            fontSize = 11.5.sp
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedTextField(colModels, { colModels = it }, label = { Text("Models *") }, minLines = 3, maxLines = 5, modifier = Modifier.weight(1f))
                            OutlinedTextField(colColors, { colColors = it }, label = { Text("Colors") }, minLines = 3, maxLines = 5, modifier = Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedTextField(colCategories, { colCategories = it }, label = { Text("Categories") }, minLines = 2, maxLines = 4, modifier = Modifier.weight(1f))
                            OutlinedTextField(colBrands, { colBrands = it }, label = { Text("Brands") }, minLines = 2, maxLines = 4, modifier = Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedTextField(colQtys, { colQtys = it }, label = { Text("Quantities") }, minLines = 2, maxLines = 4, modifier = Modifier.weight(1f))
                            OutlinedTextField(colBuys, { colBuys = it }, label = { Text("Buy Prices (₹)") }, minLines = 2, maxLines = 4, modifier = Modifier.weight(1f))
                            OutlinedTextField(colSells, { colSells = it }, label = { Text("Sell Prices (₹)") }, minLines = 2, maxLines = 4, modifier = Modifier.weight(1f))
                        }
                        OutlinedTextField(colParty, { colParty = it }, label = { Text("Party / Supplier") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(onClick = {
                                colModels = ""; colColors = ""; colCategories = ""; colBrands = ""
                                colQtys = ""; colBuys = ""; colSells = ""; colParty = ""
                            }) { Text("Clear Boxes", fontSize = 11.5.sp) }
                            OutlinedButton(onClick = {
                                val models = colModels.lines().map { it.trim() }.filter { it.isNotEmpty() }
                                val colors = colColors.lines().map { it.trim() }.filter { it.isNotEmpty() }
                                val cats = colCategories.lines().map { it.trim() }.filter { it.isNotEmpty() }
                                val brands = colBrands.lines().map { it.trim() }.filter { it.isNotEmpty() }
                                val qtys = colQtys.lines().map { it.trim() }.filter { it.isNotEmpty() }
                                val buys = colBuys.lines().map { it.trim() }.filter { it.isNotEmpty() }
                                val sells = colSells.lines().map { it.trim() }.filter { it.isNotEmpty() }
                                val built = models.mapIndexed { idx, m ->
                                    PurchaseLineItem(
                                        model = m,
                                        color = colors.getOrNull(idx) ?: colors.singleOrNull().orEmpty(),
                                        category = cats.getOrNull(idx) ?: cats.singleOrNull().orEmpty(),
                                        brand = brands.getOrNull(idx) ?: brands.singleOrNull().orEmpty(),
                                        qty = (qtys.getOrNull(idx) ?: qtys.singleOrNull())?.toIntOrNull()?.coerceAtLeast(1) ?: 1,
                                        buyPrice = (buys.getOrNull(idx) ?: buys.singleOrNull())?.toDoubleOrNull() ?: 0.0,
                                        sellPrice = (sells.getOrNull(idx) ?: sells.singleOrNull())?.toDoubleOrNull() ?: 0.0
                                    )
                                }
                                if (built.isNotEmpty()) {
                                    vm.updatePurchaseDraft { d ->
                                        d.copy(
                                            supplierName = colParty.trim().ifBlank { d.supplierName },
                                            rows = d.rows.filter { it.model.isNotBlank() } + built
                                        )
                                    }
                                    entryTab = "Grid"
                                }
                            }) { Text("Match", color = palette.accent, fontWeight = FontWeight.Bold, fontSize = 11.5.sp) }
                        }
                    }
                }

                "Quick-Add Item" -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(palette.inputBg)
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Search Existing Stock (Last Purchase Price Auto-Filled)", color = palette.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("Tap Add to insert directly into the bill grid", color = palette.textSecondary, fontSize = 11.5.sp)
                        OutlinedTextField(
                            value = quickSearch,
                            onValueChange = { quickSearch = it },
                            placeholder = { Text("Search model, brand, barcode, IMEI, category...") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        val hits = items.filter { !it.isService && Formatters.matchesStockQuery(it, quickSearch) }.take(20)
                        if (hits.isEmpty()) {
                            Text(
                                "No matching inventory items found. Use Grid or Paste/CSV to add new products.",
                                color = palette.textSecondary,
                                fontSize = 12.sp
                            )
                        } else {
                            hits.forEach { st ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(st.displayName, color = palette.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                        Text(
                                            "${st.category} · ${st.color} · Stock ${st.pieces}${if (isOwner) " · Buy ${Formatters.formatInr(st.costPrice)}" else ""}",
                                            color = palette.textSecondary,
                                            fontSize = 11.sp
                                        )
                                    }
                                    OutlinedButton(onClick = {
                                        val row = PurchaseLineItem(
                                            itemId = st.id,
                                            model = st.model,
                                            color = st.color,
                                            category = st.category,
                                            brand = st.brand,
                                            barcode = st.barcode,
                                            qty = 1,
                                            buyPrice = if (isOwner) st.costPrice else 0.0,
                                            sellPrice = st.sellingPrice
                                        )
                                        vm.updatePurchaseDraft { d ->
                                            d.copy(rows = d.rows.filter { it.model.isNotBlank() } + row)
                                        }
                                        Toast.makeText(context, "Added ${st.displayName}", Toast.LENGTH_SHORT).show()
                                    }) {
                                        Text("Add", color = palette.accent, fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                "Paste / CSV" -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(palette.inputBg)
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Paste Many Lines or Import CSV / Excel Sheet", color = palette.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("Format: Model | Color | Category | Brand | Qty | Buy | Sell\nExample: Vivo Y20 silicon black - 5 - 45 - 120", color = palette.textSecondary, fontSize = 11.5.sp)
                        OutlinedTextField(
                            value = csvText,
                            onValueChange = { csvText = it },
                            placeholder = { Text("Paste lines here...") },
                            minLines = 4,
                            maxLines = 8,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = {
                                val parsed = csvText.lines().mapNotNull { line ->
                                    val trimmed = line.trim()
                                    if (trimmed.isEmpty()) return@mapNotNull null
                                    val parts = if (trimmed.contains("|")) trimmed.split("|")
                                    else if (trimmed.contains(",")) trimmed.split(",")
                                    else trimmed.split(" - ")
                                    val p = parts.map { it.trim() }
                                    if (p.isEmpty() || p[0].equals("model", true)) return@mapNotNull null
                                    PurchaseLineItem(
                                        model = p.getOrNull(0).orEmpty(),
                                        color = p.getOrNull(1).orEmpty(),
                                        category = p.getOrNull(2).orEmpty(),
                                        brand = p.getOrNull(3).orEmpty(),
                                        qty = p.getOrNull(4)?.toIntOrNull()?.coerceAtLeast(1) ?: 1,
                                        buyPrice = p.getOrNull(5)?.toDoubleOrNull() ?: 0.0,
                                        sellPrice = p.getOrNull(6)?.toDoubleOrNull() ?: 0.0
                                    )
                                }
                                if (parsed.isNotEmpty()) {
                                    vm.updatePurchaseDraft { d -> d.copy(rows = d.rows.filter { it.model.isNotBlank() } + parsed) }
                                    entryTab = "Grid"
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = palette.accent)
                        ) {
                            Text("Insert Lines into Grid", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Spreadsheet Grid Rows
            Text("PURCHASE ITEMS GRID", color = palette.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = {
                    vm.updatePurchaseDraft { d -> d.copy(rows = d.rows + List(5) { PurchaseLineItem() }) }
                }) { Text("+5 Rows", fontSize = 11.5.sp) }

                OutlinedTextField(
                    value = applyAllBuyText,
                    onValueChange = { applyAllBuyText = it },
                    placeholder = { Text("Buy ₹") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.width(86.dp)
                )
                OutlinedTextField(
                    value = applyAllSellText,
                    onValueChange = { applyAllSellText = it },
                    placeholder = { Text("Sell ₹") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.width(86.dp)
                )
                OutlinedButton(onClick = {
                    val b = applyAllBuyText.toDoubleOrNull()
                    val s = applyAllSellText.toDoubleOrNull()
                    vm.updatePurchaseDraft { d ->
                        d.copy(rows = d.rows.map { row ->
                            row.copy(
                                buyPrice = b ?: row.buyPrice,
                                sellPrice = s ?: row.sellPrice
                            )
                        })
                    }
                }) { Text("Apply to All", fontSize = 11.5.sp) }
                OutlinedButton(onClick = {
                    val b = applyAllBuyText.toDoubleOrNull()
                    val s = applyAllSellText.toDoubleOrNull()
                    vm.updatePurchaseDraft { d ->
                        d.copy(rows = d.rows.map { row ->
                            row.copy(
                                buyPrice = if (row.buyPrice <= 0.0 && b != null) b else row.buyPrice,
                                sellPrice = if (row.sellPrice <= 0.0 && s != null) s else row.sellPrice
                            )
                        })
                    }
                }) { Text("Fill Blanks", fontSize = 11.5.sp) }
            }

            draft.rows.forEachIndexed { idx, row ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(palette.inputBg)
                        .border(1.dp, palette.border, RoundedCornerShape(12.dp))
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Row #${idx + 1} · Total ${Formatters.formatInr(row.total)}", color = palette.accent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Row {
                            IconButton(
                                onClick = {
                                    vm.updatePurchaseDraft { d ->
                                        val list = d.rows.toMutableList()
                                        list.add(idx + 1, row.copy(id = UUID.randomUUID().toString()))
                                        d.copy(rows = list)
                                    }
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Outlined.ContentCopy, contentDescription = "Duplicate Row", tint = palette.textSecondary, modifier = Modifier.size(16.dp))
                            }
                            IconButton(
                                onClick = {
                                    vm.updatePurchaseDraft { d ->
                                        val list = d.rows.toMutableList()
                                        if (list.size > 1) list.removeAt(idx)
                                        d.copy(rows = list)
                                    }
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Outlined.Delete, contentDescription = "Delete Row", tint = palette.statusRed, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = row.model,
                            onValueChange = { v ->
                                vm.updatePurchaseDraft { d ->
                                    d.copy(rows = d.rows.mapIndexed { i, r -> if (i == idx) r.copy(model = v) else r })
                                }
                            },
                            label = { Text("Model Name *") },
                            placeholder = { Text("e.g. Y20, Note 12") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = row.color,
                            onValueChange = { v ->
                                vm.updatePurchaseDraft { d ->
                                    d.copy(rows = d.rows.mapIndexed { i, r -> if (i == idx) r.copy(color = v) else r })
                                }
                            },
                            label = { Text("Color") },
                            placeholder = { Text("Black") },
                            singleLine = true,
                            modifier = Modifier.weight(0.8f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = row.category,
                            onValueChange = { v ->
                                vm.updatePurchaseDraft { d ->
                                    d.copy(rows = d.rows.mapIndexed { i, r -> if (i == idx) r.copy(category = v) else r })
                                }
                            },
                            label = { Text("Category") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = row.brand,
                            onValueChange = { v ->
                                vm.updatePurchaseDraft { d ->
                                    d.copy(rows = d.rows.mapIndexed { i, r -> if (i == idx) r.copy(brand = v) else r })
                                }
                            },
                            label = { Text("Brand") },
                            placeholder = { Text("Company / Brand") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = row.qty.toString(),
                            onValueChange = { v ->
                                val q = v.toIntOrNull()?.coerceAtLeast(1) ?: 1
                                vm.updatePurchaseDraft { d ->
                                    d.copy(rows = d.rows.mapIndexed { i, r -> if (i == idx) r.copy(qty = q) else r })
                                }
                            },
                            label = { Text("Qty") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(0.7f)
                        )
                        OutlinedTextField(
                            value = if (row.buyPrice > 0) Formatters.formatDecimal(row.buyPrice) else "",
                            onValueChange = { v ->
                                val bp = v.toDoubleOrNull() ?: 0.0
                                vm.updatePurchaseDraft { d ->
                                    d.copy(rows = d.rows.mapIndexed { i, r -> if (i == idx) r.copy(buyPrice = bp) else r })
                                }
                            },
                            label = { Text("Buy ₹/pc") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = if (row.sellPrice > 0) Formatters.formatDecimal(row.sellPrice) else "",
                            onValueChange = { v ->
                                val sp = v.toDoubleOrNull() ?: 0.0
                                vm.updatePurchaseDraft { d ->
                                    d.copy(rows = d.rows.mapIndexed { i, r -> if (i == idx) r.copy(sellPrice = sp) else r })
                                }
                            },
                            label = { Text("Sell ₹/pc") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = draft.discountText,
                    onValueChange = { v -> vm.updatePurchaseDraft { it.copy(discountText = v) } },
                    label = { Text("Discount (₹)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = draft.transportText,
                    onValueChange = { v -> vm.updatePurchaseDraft { it.copy(transportText = v) } },
                    label = { Text("Transport / Extra (₹)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                payModes.forEach { pm ->
                    FilterChip(
                        selected = draft.paymentMode == pm,
                        onClick = { vm.updatePurchaseDraft { it.copy(paymentMode = pm, amountPaidTouched = false) } },
                        label = { Text(pm, fontSize = 11.sp) }
                    )
                }
            }

            if (!draft.paymentMode.startsWith("Credit", true)) {
                OutlinedTextField(
                    value = if (draft.amountPaidTouched) draft.amountPaidText else Formatters.formatDecimal(draft.billTotal),
                    onValueChange = { v -> vm.updatePurchaseDraft { it.copy(amountPaidText = v, amountPaidTouched = true) } },
                    label = { Text("Amount Paid (₹)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { vm.updatePurchaseDraft { it.copy(generateBarcodes = !it.generateBarcodes) } },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = draft.generateBarcodes,
                    onCheckedChange = { c -> vm.updatePurchaseDraft { it.copy(generateBarcodes = c) } }
                )
                Text("Generate unique item barcodes & per-piece barcode labels on save", color = palette.textSecondary, fontSize = 12.sp)
            }

            Column(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(palette.inputBg).padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Bill Total:", color = palette.textSecondary)
                    Text(Formatters.formatInr(draft.billTotal), color = palette.textPrimary, fontWeight = FontWeight.Black)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Paid / Payable:", color = palette.textSecondary)
                    Text("${Formatters.formatInr(draft.effectivePaid)} / ${Formatters.formatInr(draft.balanceDue)}", color = palette.accent, fontWeight = FontWeight.Bold)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Balance Due:", color = palette.textSecondary)
                    Text(Formatters.formatInr(draft.balanceDue), color = palette.statusAmber, fontWeight = FontWeight.Bold)
                }
            }

            NoorPrimaryButton(
                text = "Save Purchase & Update Stock",
                icon = Icons.Outlined.LocalShipping,
                testTag = "purchase_save_button",
                onClick = {
                    val dup = purchases.firstOrNull {
                        it.id != draft.editingPurchaseId &&
                            it.supplierName.equals(draft.supplierName.trim(), true) &&
                            draft.supplierBillNo.isNotBlank() &&
                            it.supplierBillNo.equals(draft.supplierBillNo.trim(), true)
                    }
                    if (dup != null) {
                        duplicateWarningBill = dup
                    } else {
                        scope.launch { commitSavePurchase() }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Search & List
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search supplier, bill #, model or brand...") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, tint = palette.accent) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        )

        Text("← Swipe left to edit or delete", color = palette.textMuted, fontSize = 11.5.sp)

        if (filteredPurchases.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().noorCard(palette).padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("No matching purchase bills recorded yet.", color = palette.textSecondary, fontSize = 13.5.sp)
            }
        } else {
            filteredPurchases.forEach { p ->
                SwipeActionCard(
                    onEdit = if (canEditDelete) ({ vm.loadPurchaseIntoDraft(p) }) else null,
                    onDelete = if (canEditDelete) ({ deleteTarget = p }) else null
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("${p.billNumber} · ${p.supplierName}", color = palette.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (p.docType == "order") {
                                Text("Order · Pending Stock In", color = palette.statusAmber, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            } else if (p.docType == "return") {
                                Text("Debit Note", color = palette.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            if (p.status == "cancelled") {
                                Text("Cancelled", color = palette.statusRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("${Formatters.formatDisplayDate(p.date)} · ${p.items.size} lines · ${p.paymentMode}", color = palette.textSecondary, fontSize = 12.sp)
                    Spacer(Modifier.height(4.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Total ${Formatters.formatInr(p.totalAmount)}", color = palette.textPrimary, fontWeight = FontWeight.Bold)
                        Text(
                            if (p.balanceDue > 0) "Due ${Formatters.formatInr(p.balanceDue)}" else "Paid",
                            color = if (p.balanceDue > 0) palette.statusAmber else palette.statusGreen,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(96.dp))
    }

    duplicateWarningBill?.let { dup ->
        AlertDialog(
            onDismissRequest = { duplicateWarningBill = null },
            containerColor = palette.card,
            title = { Text("Duplicate Purchase Bill?", color = palette.statusAmber, fontWeight = FontWeight.Bold) },
            text = {
                Text("A bill (${dup.billNumber}) with supplier ${dup.supplierName} and ref #${dup.supplierBillNo} already exists.", color = palette.textSecondary)
            },
            confirmButton = {
                TextButton(onClick = {
                    duplicateWarningBill = null
                    scope.launch { commitSavePurchase() }
                }) { Text("Save Again Anyway", color = palette.accent, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { duplicateWarningBill = null }) { Text("OK, Don't Save") }
            }
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            containerColor = palette.card,
            title = { Text("Delete ${target.billNumber}?", color = palette.statusRed, fontWeight = FontWeight.Bold) },
            text = {
                Text("• Supplier ledger balance & cash/bank entry for this bill will be removed.", color = palette.textSecondary)
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { vm.repo.deletePurchase(target.id, reverseStock = true, isOwner = isOwner) }
                    deleteTarget = null
                }) { Text("Delete", color = palette.statusRed, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Keep Bill", color = palette.textPrimary) }
            }
        )
    }
}

@Composable
private fun PurchaseSummaryBox(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    val palette = LocalNoorColors.current
    Column(modifier = modifier.noorCard(palette, radius = 14.dp).padding(12.dp)) {
        Text(label, color = palette.textSecondary, fontSize = 11.5.sp)
        Spacer(Modifier.height(4.dp))
        Text(value, color = valueColor, fontWeight = FontWeight.Black, fontSize = 17.sp)
    }
}
