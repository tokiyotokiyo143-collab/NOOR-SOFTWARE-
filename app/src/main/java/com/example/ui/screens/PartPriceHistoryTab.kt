package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Purchase
import com.example.data.model.StockItem
import com.example.ui.theme.LocalNoorColors
import com.example.ui.theme.noorCard
import com.example.util.Formatters

private data class PartPurchasePoint(
    val date: String,
    val billNumber: String,
    val supplier: String,
    val qty: Int,
    val buyCost: Double,
    val sellPrice: Double
)

private data class TrackedPartSummary(
    val key: String,
    val displayTitle: String,
    val category: String,
    val brand: String,
    val points: List<PartPurchasePoint>,
    val latestBuyCost: Double,
    val firstBuyCost: Double,
    val lowestCost: Double,
    val weightedAvgCost: Double,
    val totalQty: Int,
    val costDelta: Double,
    val pctChange: Double,
    val retailPrice: Double
)

@Composable
fun PartPriceHistoryTab(
    purchases: List<Purchase>,
    items: List<StockItem>
) {
    val palette = LocalNoorColors.current

    val trackedParts = remember(purchases, items) {
        val grouped = linkedMapOf<String, MutableList<PartPurchasePoint>>()
        val meta = mutableMapOf<String, Triple<String, String, String>>()
        purchases.filter { it.status == "active" && it.docType == "purchase" }
            .sortedBy { it.date }
            .forEach { pur ->
                pur.items.forEach { line ->
                    val modelName = line.model.trim()
                    if (modelName.isBlank() || line.buyPrice <= 0.0) return@forEach
                    val normKey = "${line.brand.trim().lowercase()}|${modelName.lowercase()}"
                    meta.putIfAbsent(normKey, Triple(
                        listOf(line.brand, modelName).filter { it.isNotBlank() }.joinToString(" "),
                        line.category,
                        line.brand
                    ))
                    grouped.getOrPut(normKey) { mutableListOf() }.add(
                        PartPurchasePoint(
                            date = pur.date,
                            billNumber = pur.billNumber,
                            supplier = pur.supplierName.ifBlank { "Supplier" },
                            qty = line.qty.coerceAtLeast(1),
                            buyCost = line.buyPrice,
                            sellPrice = line.sellPrice
                        )
                    )
                }
            }

        grouped.map { (k, pts) ->
            val sorted = pts.sortedBy { it.date }
            val first = sorted.first().buyCost
            val latest = sorted.last().buyCost
            val lowest = sorted.minOf { it.buyCost }
            val totalQty = sorted.sumOf { it.qty }
            val wAvg = if (totalQty > 0) sorted.sumOf { it.buyCost * it.qty } / totalQty else latest
            val delta = latest - first
            val pct = if (first > 0) (delta / first) * 100.0 else 0.0
            val info = meta[k]!!
            val stockMatch = items.firstOrNull {
                it.model.equals(k.substringAfter("|"), true)
            }
            val retail = sorted.lastOrNull { it.sellPrice > 0 }?.sellPrice ?: (stockMatch?.sellingPrice ?: 0.0)
            TrackedPartSummary(
                key = k,
                displayTitle = info.first,
                category = info.second,
                brand = info.third,
                points = sorted,
                latestBuyCost = latest,
                firstBuyCost = first,
                lowestCost = lowest,
                weightedAvgCost = wAvg,
                totalQty = totalQty,
                costDelta = delta,
                pctChange = pct,
                retailPrice = retail
            )
        }
    }

    var searchQuery by remember { mutableStateOf("") }
    var sortMode by remember { mutableStateOf("Sort: Highest Cost Increase") }
    var filterChip by remember { mutableStateOf("All Tracked") }
    var selectedPartKey by remember(trackedParts) { mutableStateOf(trackedParts.firstOrNull()?.key) }
    var showAllTimeHistory by remember { mutableStateOf(false) }

    val increasingCount = remember(trackedParts) { trackedParts.count { it.costDelta > 0.01 } }
    val dropCount = remember(trackedParts) { trackedParts.count { it.costDelta < -0.01 } }
    val twoPlusCount = remember(trackedParts) { trackedParts.count { it.points.size >= 2 } }
    val highestHike = remember(trackedParts) { trackedParts.filter { it.costDelta > 0.01 }.maxByOrNull { it.costDelta } }

    val filteredList = remember(trackedParts, searchQuery, sortMode, filterChip) {
        val q = searchQuery.trim().lowercase()
        val base = trackedParts.filter { p ->
            val matchChip = when (filterChip) {
                "Increasing Cost" -> p.costDelta > 0.01
                "Price Dropped" -> p.costDelta < -0.01
                "2+ Purchases" -> p.points.size >= 2
                else -> true
            }
            val matchQ = q.isEmpty() ||
                p.displayTitle.lowercase().contains(q) ||
                p.category.lowercase().contains(q) ||
                p.points.any { it.supplier.lowercase().contains(q) }
            matchChip && matchQ
        }
        when (sortMode) {
            "Sort: Most Purchased" -> base.sortedByDescending { it.totalQty }
            "Sort: Latest Purchase" -> base.sortedByDescending { it.points.last().date }
            "Sort: Model Name (A–Z)" -> base.sortedBy { it.displayTitle.lowercase() }
            else -> base.sortedByDescending { it.costDelta }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(modifier = Modifier.fillMaxWidth().noorCard(palette, radius = 16.dp).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Supplier Part Price History & Cost Inflation Tracker", color = palette.textPrimary, fontWeight = FontWeight.Black, fontSize = 15.5.sp)
            Text("Compare historical supplier rates per model · spot rising part costs · pick the lowest-cost supplier", color = palette.textSecondary, fontSize = 12.sp)
        }

        // 4 Summary Cards
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PriceStatCard("Tracked Part Models", trackedParts.size.toString(), palette.textPrimary, Modifier.weight(1f))
            PriceStatCard("Costs Increasing", increasingCount.toString(), palette.statusAmber, Modifier.weight(1f))
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PriceStatCard(
                "Highest Cost Hike",
                if (highestHike != null) "+${Formatters.formatInr(highestHike.costDelta)} (${highestHike.displayTitle})" else "No increases recorded",
                palette.statusRed,
                Modifier.weight(1f)
            )
            PriceStatCard("Price Drops", dropCount.toString(), palette.statusGreen, Modifier.weight(1f))
        }

        if (trackedParts.isEmpty()) {
            Column(modifier = Modifier.fillMaxWidth().noorCard(palette).padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("No Supplier Price History Yet", color = palette.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Supplier cost timelines appear automatically whenever you record Purchase Bills or log outside spare part purchases in Service.",
                    color = palette.textSecondary,
                    fontSize = 12.5.sp
                )
            }
            return@Column
        }

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search model, brand, category or supplier...") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, tint = palette.accent) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        )

        // Sort Chips
        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(
                "Sort: Highest Cost Increase",
                "Sort: Most Purchased",
                "Sort: Latest Purchase",
                "Sort: Model Name (A–Z)"
            ).forEach { s ->
                FilterChip(selected = sortMode == s, onClick = { sortMode = s }, label = { Text(s, fontSize = 11.sp) })
            }
        }

        // Filter Chips
        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(
                "All Tracked" to trackedParts.size,
                "Increasing Cost" to increasingCount,
                "Price Dropped" to dropCount,
                "2+ Purchases" to twoPlusCount
            ).forEach { (label, cnt) ->
                FilterChip(
                    selected = filterChip == label,
                    onClick = { filterChip = label },
                    label = { Text("$label ($cnt)", fontSize = 11.sp) }
                )
            }
        }

        if (filteredList.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().noorCard(palette).padding(20.dp), contentAlignment = Alignment.Center) {
                Text("No parts match the current filter. Try switching back to All Tracked parts.", color = palette.textSecondary, fontSize = 13.sp)
            }
        } else {
            // Part selector list
            Text("Select Part Model", color = palette.textMuted, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
            LazyRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(filteredList, key = { it.key }) { part ->
                    val sel = selectedPartKey == part.key
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (sel) palette.accent else palette.inputBg)
                            .border(1.dp, if (sel) palette.accent else palette.border, RoundedCornerShape(12.dp))
                            .clickable { selectedPartKey = part.key }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(part.displayTitle, color = if (sel) Color.White else palette.textPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }

            val selected = filteredList.firstOrNull { it.key == selectedPartKey } ?: filteredList.first()
            Column(modifier = Modifier.fillMaxWidth().noorCard(palette, radius = 16.dp).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(selected.displayTitle, color = palette.textPrimary, fontWeight = FontWeight.Black, fontSize = 16.sp)
                    Text(
                        if (selected.points.size == 1) "1 Purchase Recorded" else "${selected.points.size} Purchases",
                        color = palette.accent,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.5.sp
                    )
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PriceStatCard("Latest Buy Cost", Formatters.formatInr(selected.latestBuyCost), palette.textPrimary, Modifier.weight(1f))
                    PriceStatCard("Lowest Cost Seen", Formatters.formatInr(selected.lowestCost), palette.statusGreen, Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PriceStatCard("Weighted Avg Cost", Formatters.formatInr(selected.weightedAvgCost), palette.textPrimary, Modifier.weight(1f))
                    val changeText = when {
                        selected.costDelta > 0.01 -> "+${Formatters.formatInr(selected.costDelta)} (+${selected.pctChange.toInt()}%)"
                        selected.costDelta < -0.01 -> "${Formatters.formatInr(selected.costDelta)} (${selected.pctChange.toInt()}%)"
                        else -> "0% Stable"
                    }
                    PriceStatCard("Cost Change", changeText, if (selected.costDelta > 0) palette.statusAmber else palette.statusGreen, Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val margin = if (selected.retailPrice > 0) selected.retailPrice - selected.latestBuyCost else 0.0
                    PriceStatCard("Retail Price (Margin)", "${Formatters.formatInr(selected.retailPrice)} (${Formatters.formatInr(margin)})", palette.accent, Modifier.weight(1f))
                    PriceStatCard("Qty Purchased", "${selected.totalQty} (${selected.points.size} buy${if (selected.points.size > 1) "s" else ""})", palette.textPrimary, Modifier.weight(1f))
                }

                // Cost Trend Line Chart
                if (selected.points.size < 2) {
                    Text(
                        "Add a second purchase bill for this model to plot its cost trend line",
                        color = palette.textSecondary,
                        fontSize = 12.sp
                    )
                } else {
                    val pts = selected.points
                    val maxY = maxOf(pts.maxOf { it.buyCost }, selected.retailPrice).coerceAtLeast(10.0)
                    val accent = palette.accent
                    val green = palette.statusGreen
                    Canvas(modifier = Modifier.fillMaxWidth().height(120.dp).padding(vertical = 6.dp)) {
                        val w = size.width
                        val h = size.height
                        if (selected.retailPrice > 0) {
                            val ry = h - (selected.retailPrice / maxY * h).toFloat()
                            drawLine(green.copy(alpha = 0.6f), Offset(0f, ry), Offset(w, ry), strokeWidth = 2f)
                        }
                        val path = Path()
                        pts.forEachIndexed { idx, p ->
                            val x = idx.toFloat() / (pts.size - 1).coerceAtLeast(1) * w
                            val y = h - (p.buyCost / maxY * h).toFloat()
                            if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
                            drawCircle(accent, radius = 6f, center = Offset(x, y))
                        }
                        drawPath(path, accent, style = Stroke(width = 4f, cap = StrokeCap.Round))
                    }
                    Text("Pink line: Supplier Buy Cost · Green line: Retail Price", color = palette.textMuted, fontSize = 10.5.sp)
                }

                // Supplier Comparison Table
                Text("Supplier Comparison · Sorted lowest latest rate first", color = palette.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                val supplierRows = remember(selected) {
                    selected.points.groupBy { it.supplier }.map { (sup, list) ->
                        val latest = list.last().buyCost
                        val avg = list.sumOf { it.buyCost * it.qty } / list.sumOf { it.qty }.coerceAtLeast(1)
                        val qty = list.sumOf { it.qty }
                        val lastDate = list.last().date
                        Triple(sup, latest, Triple(avg, "${list.size} · $qty", lastDate))
                    }.sortedBy { it.second }
                }

                supplierRows.forEachIndexed { idx, (sup, latest, extra) ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            sup + if (idx == 0) " · Best Rate" else "",
                            color = if (idx == 0) palette.statusGreen else palette.textPrimary,
                            fontWeight = if (idx == 0) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1.2f)
                        )
                        Text(Formatters.formatInr(latest), color = palette.textPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.weight(0.7f))
                        Text("Avg ${Formatters.formatInr(extra.first)}", color = palette.textSecondary, fontSize = 11.5.sp, modifier = Modifier.weight(0.8f))
                        Text("${extra.second} · ${Formatters.formatDisplayDate(extra.third)}", color = palette.textMuted, fontSize = 11.sp, modifier = Modifier.weight(1f))
                    }
                }

                HorizontalDivider(color = palette.border)
                Text("Date · Bill # | Buy Cost | Change", color = palette.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                val shownPts = remember(selected, showAllTimeHistory) {
                    if (showAllTimeHistory) selected.points.reversed() else selected.points.reversed().take(6)
                }
                shownPts.forEachIndexed { i, pt ->
                    val prev = shownPts.getOrNull(i + 1)
                    val diff = if (prev != null) pt.buyCost - prev.buyCost else 0.0
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${Formatters.formatDisplayDate(pt.date)} · ${pt.billNumber} (${pt.supplier})", color = palette.textPrimary, fontSize = 12.sp, modifier = Modifier.weight(1.4f))
                        Text(Formatters.formatInr(pt.buyCost), color = palette.textPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.weight(0.6f))
                        Text(
                            when {
                                diff > 0.01 -> "+${Formatters.formatInr(diff)}"
                                diff < -0.01 -> Formatters.formatInr(diff)
                                else -> "-"
                            },
                            color = if (diff > 0) palette.statusAmber else if (diff < 0) palette.statusGreen else palette.textMuted,
                            fontSize = 11.5.sp
                        )
                    }
                }
                if (selected.points.size > 6 && !showAllTimeHistory) {
                    TextButton(onClick = { showAllTimeHistory = true }) {
                        Text("Show All-Time Purchase History", color = palette.accent, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun PriceStatCard(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    val palette = LocalNoorColors.current
    Column(modifier = modifier.clip(RoundedCornerShape(12.dp)).background(palette.inputBg).padding(10.dp)) {
        Text(label, color = palette.textMuted, fontSize = 11.sp)
        Spacer(Modifier.height(2.dp))
        Text(value, color = color, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
    }
}
