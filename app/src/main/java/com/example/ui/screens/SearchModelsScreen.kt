package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.Invoice
import com.example.data.model.NavSection
import com.example.data.model.ShopSettings
import com.example.data.model.StockItem
import com.example.ui.MainViewModel
import com.example.ui.components.NoorSectionTitle
import com.example.ui.components.StockBadgeChip
import com.example.ui.theme.LocalNoorColors
import com.example.ui.theme.noorCard
import com.example.util.Formatters

@Composable
fun SearchModelsScreen(
    vm: MainViewModel,
    settings: ShopSettings,
    items: List<StockItem>,
    invoices: List<Invoice>,
    isOwner: Boolean
) {
    val palette = LocalNoorColors.current
    val analytics by vm.repo.analytics.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<String?>(null) }

    val filtered = remember(items, query, selectedCategory) {
        val tokens = Formatters.tokenizeQuery(query)
        items.filter { item ->
            (selectedCategory == null || item.category.equals(selectedCategory, true)) &&
                Formatters.matchesStockQueryTokens(item, tokens)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "header") {
            NoorSectionTitle(
                title = "Quick Model & Stock Search",
                subtitle = "Tap a category chip or type any phone model (like “Y20” or “Note 12”) to inspect stock, compatible models & sold history."
            )
        }

        item(key = "search_bar") {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search model (e.g. Y20), brand, color, or also-fits...") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, tint = palette.accent) },
                trailingIcon = {
                    if (query.isNotBlank()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Outlined.Clear, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        item(key = "category_chips") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Quick Category Filters", color = palette.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    TextButton(onClick = { query = ""; selectedCategory = null }) {
                        Text("Clear all filters", color = palette.accent, fontSize = 12.sp)
                    }
                }
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    settings.customCategories.forEach { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = if (selectedCategory == cat) null else cat },
                            label = { Text(cat, fontSize = 12.sp) }
                        )
                    }
                }
            }
        }

        if (filtered.isEmpty()) {
            item(key = "empty") {
                Column(
                    modifier = Modifier.fillMaxWidth().noorCard(palette).padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("No items match your search & filter.", color = palette.textSecondary)
                    TextButton(onClick = { query = ""; selectedCategory = null }) {
                        Text("Reset Filters", color = palette.accent, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            items(filtered, key = { it.id }) { item ->
                val soldStat = analytics.soldStatsByItemId[item.id]
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .noorCard(palette)
                        .clickable {
                            vm.setStockSearchFilter(item.model.ifBlank { item.displayName })
                            vm.navigateTo(NavSection.ITEMS)
                        }
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.displayName, color = palette.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text(
                                listOf(item.category, item.color).filter { it.isNotBlank() }.joinToString(" · "),
                                color = palette.textSecondary,
                                fontSize = 12.sp
                            )
                        }
                        StockBadgeChip(item)
                    }
                    if (item.alsoFits.isNotBlank()) {
                        Text("Also Fits: ${item.alsoFits}", color = palette.accent, fontSize = 12.sp)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Sell: ${Formatters.formatInr(item.sellingPrice)}", color = palette.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp)
                        if (isOwner) {
                            Text("Buy: ${Formatters.formatInr(item.costPrice)}", color = palette.textMuted, fontSize = 12.5.sp)
                        }
                    }
                    if (soldStat == null) {
                        Text("No sales recorded for this item yet.", color = palette.textMuted, fontSize = 11.5.sp)
                    } else {
                        Text(
                            "Sold ${soldStat.soldQty} ${item.unit} · Last: ${soldStat.lastInvoiceNumber} (${Formatters.formatDisplayDate(soldStat.lastSaleDate)})",
                            color = palette.statusGreen,
                            fontSize = 11.5.sp
                        )
                    }
                }
            }
        }
    }
}
