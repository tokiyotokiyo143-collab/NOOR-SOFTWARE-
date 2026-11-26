package com.example.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.ShopSettings
import com.example.data.model.StockItem
import com.example.ui.components.CameraBarcodeScannerDialog
import com.example.ui.components.NoorPrimaryButton
import com.example.ui.components.NoorSecondaryButton
import com.example.ui.theme.LocalNoorColors
import com.example.ui.theme.noorCard
import com.example.util.Formatters
import com.example.util.PdfAndPrintHelper
import java.util.UUID

@Composable
fun AddEditStockItemDialog(
    initial: StockItem?,
    settings: ShopSettings,
    isOwner: Boolean,
    findDuplicate: (String, String, String, String) -> StockItem?,
    onSave: (StockItem) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalNoorColors.current
    var category by remember { mutableStateOf(initial?.category ?: settings.customCategories.firstOrNull().orEmpty()) }
    var brand by remember { mutableStateOf(initial?.brand ?: "") }
    var model by remember { mutableStateOf(initial?.model ?: "") }
    var color by remember { mutableStateOf(initial?.color ?: "") }
    var alsoFits by remember { mutableStateOf(initial?.alsoFits ?: "") }
    var barcode by remember { mutableStateOf(initial?.barcode ?: "") }
    var unit by remember { mutableStateOf(initial?.unit ?: "pcs") }
    var imei by remember { mutableStateOf(initial?.imei ?: "") }
    var costText by remember { mutableStateOf(if (initial != null && initial.costPrice > 0) Formatters.formatDecimal(initial.costPrice) else "") }
    var sellText by remember { mutableStateOf(if (initial != null && initial.sellingPrice > 0) Formatters.formatDecimal(initial.sellingPrice) else "") }
    var piecesText by remember { mutableStateOf(initial?.pieces?.toString() ?: "1") }
    var lowAlertText by remember { mutableStateOf((initial?.lowStockAlert ?: settings.lowStockDefault).toString()) }
    var isService by remember { mutableStateOf(initial?.isService ?: false) }

    var showScanner by remember { mutableStateOf(false) }
    var duplicateTarget by remember { mutableStateOf<StockItem?>(null) }
    var catMenu by remember { mutableStateOf(false) }
    var unitMenu by remember { mutableStateOf(false) }

    fun buildItem(id: String = initial?.id ?: UUID.randomUUID().toString(), addPiecesTo: Int = 0): StockItem {
        val qty = piecesText.toIntOrNull() ?: 0
        return (initial ?: StockItem(id = id)).copy(
            id = id,
            category = category.trim(),
            brand = brand.trim(),
            model = model.trim(),
            color = color.trim(),
            alsoFits = alsoFits.trim(),
            barcode = barcode.trim(),
            unit = if (isService) "service" else unit,
            imei = Formatters.clampImei(imei),
            costPrice = if (isOwner) (costText.toDoubleOrNull() ?: 0.0) else (initial?.costPrice ?: 0.0),
            sellingPrice = sellText.toDoubleOrNull() ?: 0.0,
            pieces = if (isService) 0 else (qty + addPiecesTo).coerceAtLeast(0),
            lowStockAlert = lowAlertText.toIntOrNull() ?: settings.lowStockDefault,
            isService = isService
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .noorCard(palette, radius = 20.dp, highlighted = true)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                if (initial == null) "Add Item / Service" else "Edit Item / Service",
                color = palette.textPrimary,
                fontWeight = FontWeight.Black,
                fontSize = 18.sp
            )

            Box {
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("Category") },
                    placeholder = { Text("e.g. Silicon Cover, Tempered Glass") },
                    trailingIcon = {
                        IconButton(onClick = { catMenu = true }) {
                            Icon(Icons.Outlined.ArrowDropDown, contentDescription = "Categories")
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                DropdownMenu(expanded = catMenu, onDismissRequest = { catMenu = false }) {
                    Text("Type to add a new value", color = palette.textMuted, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                    settings.customCategories.forEach { c ->
                        DropdownMenuItem(text = { Text(c) }, onClick = { category = c; catMenu = false })
                    }
                }
            }

            OutlinedTextField(
                value = brand,
                onValueChange = { brand = it },
                label = { Text("Brand") },
                placeholder = { Text("e.g. Vivo, Samsung") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = model,
                onValueChange = { model = it },
                label = { Text("Model / Item Name") },
                placeholder = { Text("e.g. Y20, Note 12, Type-C") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = color,
                onValueChange = { color = it },
                label = { Text("Color / Variant") },
                placeholder = { Text("e.g. Black, Clear, 11D") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = alsoFits,
                onValueChange = { alsoFits = it },
                label = { Text("Also Fits Models") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = barcode,
                onValueChange = { barcode = it },
                label = { Text("Barcode (Optional)") },
                placeholder = { Text("Scan / enter barcode") },
                trailingIcon = {
                    IconButton(onClick = { showScanner = true }) {
                        Icon(Icons.Outlined.QrCodeScanner, contentDescription = "Scan barcode", tint = palette.accent)
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(modifier = Modifier.weight(1f)) {
                    OutlinedTextField(
                        value = unit,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Unit") },
                        trailingIcon = {
                            IconButton(onClick = { unitMenu = true }) {
                                Icon(Icons.Outlined.ArrowDropDown, contentDescription = null)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().clickable { unitMenu = true }
                    )
                    DropdownMenu(expanded = unitMenu, onDismissRequest = { unitMenu = false }) {
                        listOf("pcs", "box", "set", "pair", "unit", "service").forEach { u ->
                            DropdownMenuItem(text = { Text(u) }, onClick = { unit = u; unitMenu = false })
                        }
                    }
                }
                OutlinedTextField(
                    value = imei,
                    onValueChange = { imei = Formatters.clampImei(it) },
                    label = { Text("IMEI (15 Digits Only)") },
                    placeholder = { Text("15-digit IMEI") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1.3f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isOwner) {
                    OutlinedTextField(
                        value = costText,
                        onValueChange = { costText = it },
                        label = { Text("Cost Price (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = sellText,
                    onValueChange = { sellText = it },
                    label = { Text("Selling Price") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
            if (!isService) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = piecesText,
                        onValueChange = { piecesText = it.filter { c -> c.isDigit() } },
                        label = { Text("Pieces") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = lowAlertText,
                        onValueChange = { lowAlertText = it.filter { c -> c.isDigit() } },
                        label = { Text("Low Stock Alert At") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = isService, onCheckedChange = { isService = it })
                Text(
                    "Repair / Service item (unlimited stock, no piece tracking)",
                    color = palette.textSecondary,
                    fontSize = 12.5.sp
                )
            }
            Text(
                "Tip: If an item with the same Category, Brand, Model, and Color already exists, pieces will automatically be added to it.",
                color = palette.textMuted,
                fontSize = 11.5.sp
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                NoorSecondaryButton(text = "Cancel", onClick = onDismiss, modifier = Modifier.weight(1f))
                NoorPrimaryButton(
                    text = "Save Item",
                    onClick = {
                        if (model.isBlank() && category.isBlank()) return@NoorPrimaryButton
                        if (initial == null) {
                            val dup = findDuplicate(category, brand, model, color)
                            if (dup != null) {
                                duplicateTarget = dup
                                return@NoorPrimaryButton
                            }
                        }
                        onSave(buildItem())
                        onDismiss()
                    },
                    modifier = Modifier.weight(1.2f)
                )
            }
        }
    }

    if (showScanner) {
        CameraBarcodeScannerDialog(
            title = "Scan Product Barcode",
            onScanned = { barcode = it },
            onDismiss = { showScanner = false }
        )
    }

    duplicateTarget?.let { dup ->
        AlertDialog(
            onDismissRequest = { duplicateTarget = null },
            containerColor = palette.card,
            title = { Text("Duplicate Item Found", color = palette.statusAmber, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "${dup.displayName} (${dup.category} · ${dup.color}) already exists with ${dup.pieces} ${dup.unit}. Add pieces to existing or create again?",
                    color = palette.textSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val qtyToAdd = piecesText.toIntOrNull() ?: 0
                        onSave(dup.copy(pieces = dup.pieces + qtyToAdd))
                        duplicateTarget = null
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = palette.accent)
                ) { Text("Add Pieces", color = Color.White) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        onSave(buildItem())
                        duplicateTarget = null
                        onDismiss()
                    }) { Text("Add Again Anyway", color = palette.textPrimary) }
                    TextButton(onClick = { duplicateTarget = null }) { Text("OK, Don't Add", color = palette.textSecondary) }
                }
            }
        )
    }
}

@Composable
fun BulkAddProductsDialog(
    settings: ShopSettings,
    isOwner: Boolean,
    findDuplicate: (String, String, String, String) -> StockItem?,
    onSaveItems: (List<StockItem>) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalNoorColors.current
    val context = LocalContext.current
    var tab by remember { mutableStateOf("Column-Wise Paste") }
    var duplicateAction by remember { mutableStateOf("Add Pieces to Existing") } // or "Skip Duplicate"

    // Tab 1: Column-Wise Paste
    var colModels by remember { mutableStateOf("") }
    var colColors by remember { mutableStateOf("") }
    var colCats by remember { mutableStateOf("") }
    var colBrands by remember { mutableStateOf("") }
    var colQtys by remember { mutableStateOf("") }
    var colBuys by remember { mutableStateOf("") }
    var colSells by remember { mutableStateOf("") }
    var bulkBuy by remember { mutableStateOf("") }
    var bulkSell by remember { mutableStateOf("") }
    var previewRows by remember { mutableStateOf<List<StockItem>>(emptyList()) }

    // Tab 2: Same Spec List
    var sameBrand by remember { mutableStateOf("") }
    var sameColor by remember { mutableStateOf("") }
    var sameCategory by remember { mutableStateOf(settings.customCategories.firstOrNull() ?: "Silicon Cover") }
    var samePieces by remember { mutableStateOf("5") }
    var sameCost by remember { mutableStateOf("0") }
    var sameSell by remember { mutableStateOf("0") }
    var sameModelsList by remember { mutableStateOf("") }

    // Tab 3: CSV
    var csvText by remember { mutableStateOf("") }
    val csvPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText().orEmpty()
            }.onSuccess { csvText = it }
                .onFailure { Toast.makeText(context, "Failed to parse CSV file.", Toast.LENGTH_SHORT).show() }
        }
    }

    fun buildColumnPreview(): List<StockItem> {
        val models = colModels.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (models.isEmpty()) return emptyList()
        val colors = colColors.lines().map { it.trim() }
        val cats = colCats.lines().map { it.trim() }
        val brands = colBrands.lines().map { it.trim() }
        val qtys = colQtys.lines().map { it.trim() }
        val buys = colBuys.lines().map { it.trim() }
        val sells = colSells.lines().map { it.trim() }
        fun pick(list: List<String>, i: Int, fallback: String = ""): String = when {
            list.isEmpty() -> fallback
            list.size == 1 && list[0].isNotBlank() -> list[0]
            i < list.size -> list[i].ifBlank { fallback }
            else -> fallback
        }
        return models.mapIndexed { i, m ->
            StockItem(
                category = pick(cats, i, "Accessories"),
                brand = pick(brands, i, ""),
                model = m,
                color = pick(colors, i, ""),
                pieces = pick(qtys, i, "1").toIntOrNull() ?: 1,
                costPrice = if (isOwner) (pick(buys, i, "0").toDoubleOrNull() ?: 0.0) else 0.0,
                sellingPrice = pick(sells, i, "0").toDoubleOrNull() ?: 0.0,
                lowStockAlert = settings.lowStockDefault
            )
        }
    }

    fun commitRows(rows: List<StockItem>) {
        val finalToSave = mutableListOf<StockItem>()
        rows.forEach { row ->
            val dup = findDuplicate(row.category, row.brand, row.model, row.color)
            if (dup != null) {
                if (duplicateAction == "Add Pieces to Existing") {
                    finalToSave.add(
                        dup.copy(
                            pieces = dup.pieces + row.pieces,
                            costPrice = if (isOwner && row.costPrice > 0) row.costPrice else dup.costPrice,
                            sellingPrice = if (row.sellingPrice > 0) row.sellingPrice else dup.sellingPrice
                        )
                    )
                }
            } else {
                finalToSave.add(row)
            }
        }
        onSaveItems(finalToSave)
        Toast.makeText(context, "Saved ${finalToSave.size} products.", Toast.LENGTH_SHORT).show()
        onDismiss()
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .noorCard(palette, radius = 20.dp, highlighted = true)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Bulk Add Products", color = palette.textPrimary, fontWeight = FontWeight.Black, fontSize = 18.sp)
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Column-Wise Paste", "Same Spec List", "Import CSV").forEach { t ->
                    FilterChip(selected = tab == t, onClick = { tab = t }, label = { Text(t, fontSize = 12.sp) })
                }
            }
            Text("Duplicates detected automatically by Category, Brand, Model & Color", color = palette.statusAmber, fontSize = 11.5.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Add Pieces to Existing", "Skip Duplicate").forEach { act ->
                    FilterChip(selected = duplicateAction == act, onClick = { duplicateAction = act }, label = { Text(act, fontSize = 11.sp) })
                }
            }

            when (tab) {
                "Column-Wise Paste" -> {
                    Text(
                        "Paste directly from Excel / Google Sheets into each column box below — zero column mixing. Need one Category, Brand, Qty, or Price for all models? Enter it just once in that box.",
                        color = palette.textSecondary,
                        fontSize = 12.sp
                    )
                    OutlinedTextField(value = colModels, onValueChange = { colModels = it }, label = { Text("Models *") }, minLines = 3, maxLines = 5, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = colColors, onValueChange = { colColors = it }, label = { Text("Colors") }, minLines = 2, maxLines = 4, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = colCats, onValueChange = { colCats = it }, label = { Text("Categories") }, minLines = 2, maxLines = 4, modifier = Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = colBrands, onValueChange = { colBrands = it }, label = { Text("Brands") }, minLines = 2, maxLines = 4, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = colQtys, onValueChange = { colQtys = it }, label = { Text("Quantities") }, minLines = 2, maxLines = 4, modifier = Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (isOwner) {
                            OutlinedTextField(value = colBuys, onValueChange = { colBuys = it }, label = { Text("Buy Prices ₹") }, minLines = 2, maxLines = 4, modifier = Modifier.weight(1f))
                        }
                        OutlinedTextField(value = colSells, onValueChange = { colSells = it }, label = { Text("Sell Prices ₹") }, minLines = 2, maxLines = 4, modifier = Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (isOwner) {
                            OutlinedTextField(value = bulkBuy, onValueChange = { bulkBuy = it }, label = { Text("Bulk Buy ₹") }, singleLine = true, modifier = Modifier.weight(1f))
                        }
                        OutlinedTextField(value = bulkSell, onValueChange = { bulkSell = it }, label = { Text("Bulk Sell ₹") }, singleLine = true, modifier = Modifier.weight(1f))
                    }
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(onClick = { previewRows = buildColumnPreview() }) { Text("Preview Columns") }
                        OutlinedButton(onClick = {
                            val base = previewRows.ifEmpty { buildColumnPreview() }
                            val b = bulkBuy.toDoubleOrNull()
                            val s = bulkSell.toDoubleOrNull()
                            previewRows = base.map { it.copy(costPrice = b ?: it.costPrice, sellingPrice = s ?: it.sellingPrice) }
                        }) { Text("Apply Bulk Rate to All") }
                        OutlinedButton(onClick = {
                            val base = previewRows.ifEmpty { buildColumnPreview() }
                            val b = bulkBuy.toDoubleOrNull() ?: 0.0
                            val s = bulkSell.toDoubleOrNull() ?: 0.0
                            previewRows = base.map {
                                it.copy(
                                    costPrice = if (it.costPrice <= 0) b else it.costPrice,
                                    sellingPrice = if (it.sellingPrice <= 0) s else it.sellingPrice
                                )
                            }
                        }) { Text("Fill Blank Rates") }
                    }
                    if (previewRows.isNotEmpty()) {
                        Column(modifier = Modifier.fillMaxWidth().noorCard(palette).padding(8.dp)) {
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Text("Model", color = palette.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.4f))
                                Text("Color", color = palette.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.9f))
                                if (isOwner) Text("Buy ₹", color = palette.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.7f))
                                Text("Sell ₹", color = palette.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.7f))
                            }
                            previewRows.take(20).forEach { r ->
                                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                                    Text(r.model, color = palette.textPrimary, fontSize = 11.5.sp, modifier = Modifier.weight(1.4f))
                                    Text(r.color, color = palette.textSecondary, fontSize = 11.5.sp, modifier = Modifier.weight(0.9f))
                                    if (isOwner) Text("${r.costPrice.toInt()}", color = palette.textSecondary, fontSize = 11.5.sp, modifier = Modifier.weight(0.7f))
                                    Text("${r.sellingPrice.toInt()}", color = palette.textPrimary, fontSize = 11.5.sp, modifier = Modifier.weight(0.7f))
                                }
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NoorSecondaryButton(text = "Cancel", onClick = onDismiss, modifier = Modifier.weight(1f))
                        NoorPrimaryButton(
                            text = "Save Products",
                            onClick = { commitRows(previewRows.ifEmpty { buildColumnPreview() }) },
                            modifier = Modifier.weight(1.3f)
                        )
                    }
                }
                "Same Spec List" -> {
                    OutlinedTextField(value = sameCategory, onValueChange = { sameCategory = it }, label = { Text("Category") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = sameBrand, onValueChange = { sameBrand = it }, label = { Text("Brand (optional)") }, placeholder = { Text("e.g. Vivo") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = sameColor, onValueChange = { sameColor = it }, label = { Text("Color (optional)") }, placeholder = { Text("e.g. Black") }, singleLine = true, modifier = Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = samePieces, onValueChange = { samePieces = it.filter { c -> c.isDigit() } }, label = { Text("Pieces Each") }, singleLine = true, modifier = Modifier.weight(1f))
                        if (isOwner) {
                            OutlinedTextField(value = sameCost, onValueChange = { sameCost = it }, label = { Text("Cost (₹)") }, singleLine = true, modifier = Modifier.weight(1f))
                        }
                        OutlinedTextField(value = sameSell, onValueChange = { sameSell = it }, label = { Text("Sell (₹)") }, singleLine = true, modifier = Modifier.weight(1f))
                    }
                    OutlinedTextField(
                        value = sameModelsList,
                        onValueChange = { sameModelsList = it },
                        label = { Text("Paste List of Models (one per line or comma-separated)") },
                        minLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NoorSecondaryButton(text = "Cancel", onClick = onDismiss, modifier = Modifier.weight(1f))
                        NoorPrimaryButton(
                            text = "Add All Models",
                            onClick = {
                                val models = sameModelsList.split('\n', ',').map { it.trim() }.filter { it.isNotEmpty() }
                                val rows = models.map { m ->
                                    StockItem(
                                        category = sameCategory.ifBlank { "Accessories" },
                                        brand = sameBrand,
                                        model = m,
                                        color = sameColor,
                                        pieces = samePieces.toIntOrNull() ?: 1,
                                        costPrice = if (isOwner) (sameCost.toDoubleOrNull() ?: 0.0) else 0.0,
                                        sellingPrice = sameSell.toDoubleOrNull() ?: 0.0,
                                        lowStockAlert = settings.lowStockDefault
                                    )
                                }
                                commitRows(rows)
                            },
                            modifier = Modifier.weight(1.3f)
                        )
                    }
                }
                else -> {
                    Text("CSV header: category, brand, model, color, alsoFits, costPrice, sellingPrice, pieces", color = palette.textSecondary, fontSize = 11.5.sp)
                    NoorSecondaryButton(
                        text = "Choose CSV File",
                        icon = Icons.Outlined.UploadFile,
                        onClick = { csvPicker.launch(arrayOf("text/*", "text/csv", "application/csv")) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = csvText,
                        onValueChange = { csvText = it },
                        label = { Text("Or Paste CSV Rows") },
                        minLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NoorSecondaryButton(text = "Cancel", onClick = onDismiss, modifier = Modifier.weight(1f))
                        NoorPrimaryButton(
                            text = "Import CSV",
                            onClick = {
                                try {
                                    val parsed = Formatters.parseCsvRows(csvText)
                                    val dataRows = if (parsed.firstOrNull()?.firstOrNull()?.equals("category", true) == true) parsed.drop(1) else parsed
                                    val items = dataRows.filter { it.size >= 3 }.map { r ->
                                        StockItem(
                                            category = r.getOrElse(0) { "" }.ifBlank { "Accessories" },
                                            brand = r.getOrElse(1) { "" },
                                            model = r.getOrElse(2) { "" },
                                            color = r.getOrElse(3) { "" },
                                            alsoFits = r.getOrElse(4) { "" },
                                            costPrice = if (isOwner) (r.getOrElse(5) { "0" }.toDoubleOrNull() ?: 0.0) else 0.0,
                                            sellingPrice = r.getOrElse(6) { "0" }.toDoubleOrNull() ?: 0.0,
                                            pieces = r.getOrElse(7) { "1" }.toIntOrNull() ?: 1,
                                            lowStockAlert = settings.lowStockDefault
                                        )
                                    }
                                    if (items.isEmpty()) {
                                        Toast.makeText(context, "Failed to parse CSV file.", Toast.LENGTH_SHORT).show()
                                    } else commitRows(items)
                                } catch (_: Exception) {
                                    Toast.makeText(context, "Failed to parse CSV file.", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1.3f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BarcodeLabelPrintDialog(
    allItems: List<StockItem>,
    initialSelectedIds: Set<String>,
    settings: ShopSettings,
    onDismiss: () -> Unit
) {
    val palette = LocalNoorColors.current
    val context = LocalContext.current
    var search by remember { mutableStateOf("") }
    var selectedQtyMap by remember {
        mutableStateOf(
            initialSelectedIds.associateWith { id ->
                (allItems.find { it.id == id }?.pieces ?: 1).coerceAtLeast(1)
            }
        )
    }
    var layout by remember { mutableStateOf("A4_3") }
    var showShopName by remember { mutableStateOf(true) }
    var showPrice by remember { mutableStateOf(true) }
    var showCategory by remember { mutableStateOf(false) }

    val filtered = remember(allItems, search) {
        allItems.filter { !it.isService && Formatters.matchesStockQuery(it, search) }
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .noorCard(palette, radius = 20.dp, highlighted = true)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Barcode Label Sheet", color = palette.textPrimary, fontWeight = FontWeight.Black, fontSize = 18.sp)
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    "A4_3" to "A4 · 3 Columns (Shelf)",
                    "A4_4" to "A4 · 4 Columns (Compact)",
                    "THERMAL" to "Thermal Sticker Roll"
                ).forEach { (k, lbl) ->
                    FilterChip(selected = layout == k, onClick = { layout = k }, label = { Text(lbl, fontSize = 11.sp) })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(checked = showShopName, onCheckedChange = { showShopName = it }); Text("Shop Name", fontSize = 11.5.sp, color = palette.textPrimary) }
                Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(checked = showPrice, onCheckedChange = { showPrice = it }); Text("Selling Price", fontSize = 11.5.sp, color = palette.textPrimary) }
                Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(checked = showCategory, onCheckedChange = { showCategory = it }); Text("Category", fontSize = 11.5.sp, color = palette.textPrimary) }
            }

            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                placeholder = { Text("Search products to label...") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = {
                    selectedQtyMap = filtered.associate { it.id to it.pieces.coerceAtLeast(1) }
                }) { Text("Select All Physical", fontSize = 11.sp) }
                OutlinedButton(onClick = {
                    selectedQtyMap = selectedQtyMap.keys.associateWith { id ->
                        (allItems.find { it.id == id }?.pieces ?: 1).coerceAtLeast(1)
                    }
                }) { Text("Match Stock Qty", fontSize = 11.sp) }
                OutlinedButton(onClick = {
                    selectedQtyMap = selectedQtyMap.keys.associateWith { 1 }
                }) { Text("1 Each", fontSize = 11.sp) }
                OutlinedButton(onClick = { selectedQtyMap = emptyMap() }) { Text("Clear", fontSize = 11.sp) }
            }

            if (selectedQtyMap.isEmpty()) {
                Text(
                    "No products selected yet — Check one or more items in the selector to preview and print barcode shelf labels.",
                    color = palette.textSecondary,
                    fontSize = 12.sp
                )
            }
            filtered.take(30).forEach { item ->
                val checked = item.id in selectedQtyMap
                val qty = selectedQtyMap[item.id] ?: 1
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = checked,
                        onCheckedChange = { on ->
                            selectedQtyMap = if (on) selectedQtyMap + (item.id to item.pieces.coerceAtLeast(1))
                            else selectedQtyMap - item.id
                        }
                    )
                    Text(item.displayName, color = palette.textPrimary, fontSize = 12.5.sp, modifier = Modifier.weight(1f))
                    if (checked) {
                        Text("Qty:", color = palette.textMuted, fontSize = 11.sp)
                        Spacer(Modifier.width(4.dp))
                        IconButton(onClick = { if (qty > 1) selectedQtyMap = selectedQtyMap + (item.id to qty - 1) }, modifier = Modifier.size(28.dp)) {
                            Text("-", color = palette.accent, fontWeight = FontWeight.Bold)
                        }
                        Text(qty.toString(), color = palette.textPrimary, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                        IconButton(onClick = { selectedQtyMap = selectedQtyMap + (item.id to qty + 1) }, modifier = Modifier.size(28.dp)) {
                            Text("+", color = palette.accent, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NoorSecondaryButton(text = "Close", onClick = onDismiss, modifier = Modifier.weight(1f))
                NoorPrimaryButton(
                    text = "Save as PDF / Print",
                    enabled = selectedQtyMap.isNotEmpty(),
                    onClick = {
                        val pairs = selectedQtyMap.mapNotNull { (id, q) ->
                            allItems.find { it.id == id }?.let { it to q }
                        }
                        val pdf = PdfAndPrintHelper.generateBarcodeLabelsPdf(
                            context, pairs, settings, layout, showShopName, showPrice, showCategory
                        )
                        if (pdf != null) PdfAndPrintHelper.printPdfFile(context, pdf, "Barcode Labels")
                        else Toast.makeText(context, "Could not generate barcode sheet.", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1.4f)
                )
            }
        }
    }
}
