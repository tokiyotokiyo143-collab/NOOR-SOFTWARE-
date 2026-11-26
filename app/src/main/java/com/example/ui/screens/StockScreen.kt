package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Invoice
import com.example.data.model.ShopSettings
import com.example.data.model.StockItem
import com.example.ui.MainViewModel
import com.example.ui.components.*
import com.example.ui.theme.LocalNoorColors
import com.example.ui.theme.noorCard
import com.example.util.Formatters
import kotlinx.coroutines.launch

@Composable
fun StockScreen(
    vm: MainViewModel,
    settings: ShopSettings,
    items: List<StockItem>,
    invoices: List<Invoice>,
    initialFilter: String,
    isOwner: Boolean,
    canEditStock: Boolean
) {
    val palette = LocalNoorColors.current
    val scope = rememberCoroutineScope()

    var searchQuery by remember(initialFilter) { mutableStateOf(initialFilter) }
    var selectedBrand by remember { mutableStateOf<String?>(null) }
    var statusFilter by remember { mutableStateOf("All") } // All | In Stock | Low Stock | Out / Need to Buy | Sold Items
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var selectedForBarcode by remember { mutableStateOf<Set<String>>(emptySet()) }

    var showAddEditDialog by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<StockItem?>(null) }
    var showBulkDialog by remember { mutableStateOf(false) }
    var showBarcodeDialog by remember { mutableStateOf(false) }
    var showCategoriesDialog by remember { mutableStateOf(false) }
    var adjustTarget by remember { mutableStateOf<StockItem?>(null) }
    var deleteConfirmTarget by remember { mutableStateOf<StockItem?>(null) }
    var showScanner by remember { mutableStateOf(false) }

    val soldItemIds = remember(invoices) {
        invoices.filter { it.status == "active" && it.docType == "invoice" }
            .flatMap { it.items.mapNotNull { l -> l.itemId } }
            .toSet()
    }

    val brands = remember(items) {
        items.map { it.brand.trim() }.filter { it.isNotEmpty() }.distinct().sorted()
    }

    val filtered = remember(items, searchQuery, selectedBrand, statusFilter, selectedCategory, soldItemIds) {
        items.filter { item ->
            val brandOk = selectedBrand == null || item.brand.equals(selectedBrand, true)
            val catOk = selectedCategory == null || item.category.equals(selectedCategory, true)
            val statusOk = when (statusFilter) {
                "In Stock" -> item.isService || item.pieces > item.lowStockAlert
                "Low Stock" -> !item.isService && item.pieces in 1..item.lowStockAlert
                "Out / Need to Buy" -> !item.isService && item.pieces <= 0
                "Sold Items" -> item.id in soldItemIds
                else -> true
            }
            brandOk && catOk && statusOk && Formatters.matchesStockQuery(item, searchQuery)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            NoorSectionTitle(
                title = "Inventory & Services",
                subtitle = "Tip: Swipe left on any item card to Edit or Delete (or long-press card)",
                trailing = {
                    if (canEditStock) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            IconButton(onClick = { showCategoriesDialog = true }) {
                                Icon(Icons.Outlined.Category, contentDescription = "Shop Categories", tint = palette.textSecondary)
                            }
                            IconButton(onClick = { showBarcodeDialog = true }) {
                                Icon(Icons.Outlined.QrCode2, contentDescription = "Barcode Label Sheet", tint = palette.accent)
                            }
                        }
                    }
                }
            )
        }

        if (canEditStock) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    NoorPrimaryButton(
                        text = "+ Add Item",
                        onClick = { editingItem = null; showAddEditDialog = true },
                        modifier = Modifier.weight(1f),
                        testTag = "stock_add_item_button"
                    )
                    NoorSecondaryButton(
                        text = "Bulk Add Products",
                        onClick = { showBulkDialog = true },
                        modifier = Modifier.weight(1.1f)
                    )
                }
            }
        }

        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it; vm.setStockSearchFilter(it) },
                placeholder = { Text("Filter by model, brand, barcode, IMEI, color, category...") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, tint = palette.accent) },
                trailingIcon = {
                    Row {
                        IconButton(onClick = { showScanner = true }) {
                            Icon(Icons.Outlined.QrCodeScanner, contentDescription = "Scan barcode", tint = palette.accent)
                        }
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { searchQuery = ""; vm.setStockSearchFilter("") }) {
                                Icon(Icons.Outlined.Clear, contentDescription = "Clear")
                            }
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Brand-Wise Stock Boxes
        if (brands.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Brand-Wise Stock Boxes (Tap to Filter)", color = palette.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        if (selectedBrand != null) {
                            Text(
                                "Show All Brands",
                                color = palette.accent,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable { selectedBrand = null }
                            )
                        }
                    }
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        brands.forEach { b ->
                            val count = items.filter { it.brand.equals(b, true) }.sumOf { it.pieces }
                            val selected = selectedBrand == b
                            Column(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (selected) palette.accent.copy(alpha = 0.18f) else palette.card)
                                    .border(1.dp, if (selected) palette.accent else palette.border, RoundedCornerShape(12.dp))
                                    .clickable { selectedBrand = if (selected) null else b }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text(b, color = if (selected) palette.accent else palette.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("$count pcs", color = palette.textMuted, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }

        // Status chips & category chips
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("All", "In Stock", "Low Stock", "Out / Need to Buy", "Sold Items").forEach { st ->
                        FilterChip(selected = statusFilter == st, onClick = { statusFilter = st }, label = { Text(st, fontSize = 11.5.sp) })
                    }
                }
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = selectedCategory == null, onClick = { selectedCategory = null }, label = { Text("All Categories", fontSize = 11.sp) })
                    settings.customCategories.forEach { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = if (selectedCategory == cat) null else cat },
                            label = { Text(cat, fontSize = 11.sp) }
                        )
                    }
                }
            }
        }

        if (filtered.isEmpty()) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().noorCard(palette).padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("No items match your search & filter.", color = palette.textSecondary)
                    TextButton(onClick = {
                        searchQuery = ""
                        selectedBrand = null
                        selectedCategory = null
                        statusFilter = "All"
                        vm.setStockSearchFilter("")
                    }) {
                        Text("Reset Filters", color = palette.accent, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            items(filtered, key = { it.id }) { item ->
                SwipeActionCard(
                    onEdit = if (canEditStock) { { editingItem = item; showAddEditDialog = true } } else null,
                    onDelete = if (canEditStock) { { deleteConfirmTarget = item } } else null
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.displayName, color = palette.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text(
                                listOf(item.category, item.color).filter { it.isNotBlank() }.joinToString(" · "),
                                color = palette.textSecondary,
                                fontSize = 12.sp
                            )
                            if (item.alsoFits.isNotBlank()) {
                                Text("Also Fits: ${item.alsoFits}", color = palette.accent, fontSize = 11.5.sp)
                            }
                            if (item.barcode.isNotBlank() || item.imei.isNotBlank()) {
                                Text(
                                    listOfNotNull(
                                        item.barcode.takeIf { it.isNotBlank() }?.let { "Barcode: $it" },
                                        item.imei.takeIf { it.isNotBlank() }?.let { "IMEI: $it" }
                                    ).joinToString(" · "),
                                    color = palette.textMuted,
                                    fontSize = 11.sp
                                )
                            }
                        }
                        StockBadgeChip(item)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Sell: ${Formatters.formatInr(item.sellingPrice)}", color = palette.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                            if (isOwner) {
                                Text("Cost: ${Formatters.formatInr(item.costPrice)}", color = palette.textMuted, fontSize = 11.5.sp)
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (!item.isService && canEditStock) {
                                OutlinedButton(
                                    onClick = { adjustTarget = item },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text("Adjust Stock", color = palette.accent, fontSize = 11.5.sp)
                                }
                            }
                            Checkbox(
                                checked = item.id in selectedForBarcode,
                                onCheckedChange = { checked ->
                                    selectedForBarcode = if (checked) selectedForBarcode + item.id else selectedForBarcode - item.id
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddEditDialog) {
        AddEditStockItemDialog(
            initial = editingItem,
            settings = settings,
            isOwner = isOwner,
            findDuplicate = vm.repo::findDuplicateItem,
            onSave = { item -> scope.launch { vm.repo.saveItem(item, isOwner) } },
            onDismiss = { showAddEditDialog = false }
        )
    }

    if (showBulkDialog) {
        BulkAddProductsDialog(
            settings = settings,
            isOwner = isOwner,
            findDuplicate = vm.repo::findDuplicateItem,
            onSaveItems = { list -> scope.launch { list.forEach { vm.repo.saveItem(it, isOwner) } } },
            onDismiss = { showBulkDialog = false }
        )
    }

    if (showBarcodeDialog) {
        BarcodeLabelPrintDialog(
            allItems = items,
            initialSelectedIds = selectedForBarcode,
            settings = settings,
            onDismiss = { showBarcodeDialog = false }
        )
    }

    if (showCategoriesDialog) {
        var newCat by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCategoriesDialog = false },
            containerColor = palette.card,
            title = { Text("Shop Categories", color = palette.textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = newCat,
                            onValueChange = { newCat = it },
                            placeholder = { Text("New category name...") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                val clean = newCat.trim()
                                if (clean.isNotBlank() && clean !in settings.customCategories) {
                                    scope.launch {
                                        vm.repo.updateSettings { it.copy(customCategories = it.customCategories + clean) }
                                    }
                                    newCat = ""
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = palette.accent)
                        ) { Text("Add", color = androidx.compose.ui.graphics.Color.White) }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        settings.customCategories.forEach { cat ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(cat, color = palette.textPrimary, fontSize = 13.sp)
                                IconButton(
                                    onClick = {
                                        scope.launch {
                                            vm.repo.updateSettings { it.copy(customCategories = it.customCategories - cat) }
                                        }
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Outlined.Close, contentDescription = "Remove", tint = palette.textMuted, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCategoriesDialog = false }) { Text("Done", color = palette.accent) }
            }
        )
    }

    adjustTarget?.let { target ->
        var mode by remember { mutableStateOf("add") }
        var qtyText by remember { mutableStateOf("1") }
        AlertDialog(
            onDismissRequest = { adjustTarget = null },
            containerColor = palette.card,
            title = { Text("Adjust Stock · ${target.displayName}", color = palette.textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Current stock: ${target.pieces} ${target.unit}", color = palette.textSecondary, fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("add" to "+ Add", "reduce" to "- Reduce", "set" to "Set Exact").forEach { (k, lbl) ->
                            FilterChip(selected = mode == k, onClick = { mode = k }, label = { Text(lbl, fontSize = 12.sp) })
                        }
                    }
                    OutlinedTextField(
                        value = qtyText,
                        onValueChange = { qtyText = it.filter { c -> c.isDigit() } },
                        label = { Text("Quantity") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val qty = qtyText.toIntOrNull() ?: 0
                        scope.launch { vm.repo.adjustStock(target.id, mode, qty, isOwner) }
                        adjustTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = palette.accent)
                ) { Text("Update Stock", color = androidx.compose.ui.graphics.Color.White) }
            },
            dismissButton = { TextButton(onClick = { adjustTarget = null }) { Text("Cancel") } }
        )
    }

    deleteConfirmTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteConfirmTarget = null },
            containerColor = palette.card,
            title = { Text("Delete Item", color = palette.statusRed, fontWeight = FontWeight.Bold) },
            text = { Text("Delete \"${target.displayName}\" from inventory?", color = palette.textSecondary) },
            confirmButton = {
                Button(
                    onClick = { scope.launch { vm.repo.deleteItem(target.id) }; deleteConfirmTarget = null },
                    colors = ButtonDefaults.buttonColors(containerColor = palette.statusRed)
                ) { Text("Delete", color = androidx.compose.ui.graphics.Color.White) }
            },
            dismissButton = { TextButton(onClick = { deleteConfirmTarget = null }) { Text("Keep") } }
        )
    }

    if (showScanner) {
        CameraBarcodeScannerDialog(
            title = "Scan Stock Barcode / IMEI",
            onScanned = { code -> searchQuery = code; vm.setStockSearchFilter(code) },
            onDismiss = { showScanner = false }
        )
    }
}
