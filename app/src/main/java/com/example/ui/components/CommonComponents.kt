package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.NavSection
import com.example.data.model.ShopSettings
import com.example.data.model.StockItem
import com.example.ui.theme.LocalNoorColors
import com.example.ui.theme.noorCard
import com.example.util.Formatters

fun navSectionIcon(section: NavSection): ImageVector = when (section) {
    NavSection.HOME -> Icons.Outlined.Home
    NavSection.BILLING -> Icons.Outlined.ReceiptLong
    NavSection.ITEMS -> Icons.Outlined.Inventory2
    NavSection.SEARCH -> Icons.Outlined.Search
    NavSection.REPAIRS -> Icons.Outlined.Build
    NavSection.SERVICE_STATUS -> Icons.Outlined.PhoneAndroid
    NavSection.PURCHASES -> Icons.Outlined.LocalShipping
    NavSection.PARTIES -> Icons.Outlined.People
    NavSection.CASH_BANK -> Icons.Outlined.AccountBalanceWallet
    NavSection.EXPENSES -> Icons.Outlined.CurrencyRupee
    NavSection.REPORTS -> Icons.Outlined.BarChart
    NavSection.SETTINGS -> Icons.Outlined.Settings
}

@Composable
fun NoorPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    testTag: String? = null
) {
    val palette = LocalNoorColors.current
    val shape = RoundedCornerShape(13.dp)
    val baseMod = if (testTag != null) modifier.testTag(testTag) else modifier
    Box(
        modifier = baseMod
            .defaultMinSize(minHeight = 48.dp)
            .shadow(
                elevation = if (enabled) 8.dp else 0.dp,
                shape = shape,
                ambientColor = palette.accent.copy(alpha = 0.35f),
                spotColor = palette.accent.copy(alpha = 0.45f)
            )
            .clip(shape)
            .background(
                brush = if (enabled) palette.primaryGradient
                else androidx.compose.ui.graphics.SolidColor(palette.inputBg)
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
            Text(
                text = text,
                color = if (enabled) Color.White else palette.textMuted,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun NoorSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    testTag: String? = null
) {
    val palette = LocalNoorColors.current
    val shape = RoundedCornerShape(13.dp)
    val baseMod = if (testTag != null) modifier.testTag(testTag) else modifier
    Box(
        modifier = baseMod
            .defaultMinSize(minHeight = 48.dp)
            .clip(shape)
            .background(palette.inputBg, shape)
            .border(1.dp, palette.border, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = palette.textPrimary, modifier = Modifier.size(18.dp))
            }
            Text(
                text = text,
                color = palette.textPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun NoorStatusDot(color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(color)
    )
}

@Composable
fun NoorSectionTitle(title: String, subtitle: String? = null, trailing: @Composable (() -> Unit)? = null) {
    val palette = LocalNoorColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = palette.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Black)
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, color = palette.textSecondary, fontSize = 12.5.sp, lineHeight = 16.sp)
            }
        }
        if (trailing != null) trailing()
    }
}

@Composable
fun StockBadgeChip(item: StockItem) {
    val palette = LocalNoorColors.current
    val (label, color) = when {
        item.isService -> "service" to palette.accent
        item.pieces <= 0 -> "Out (0)" to palette.statusRed
        item.pieces <= item.lowStockAlert -> "Low (${item.pieces})" to palette.statusAmber
        else -> "${item.pieces} ${item.unit}" to palette.statusGreen
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.16f))
            .border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(label, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SwipeActionCard(
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    highlighted: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val palette = LocalNoorColors.current
    val haptic = LocalHapticFeedback.current
    var showActions by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .noorCard(palette, radius = 16.dp, highlighted = highlighted)
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        showActions = !showActions
                    }
                )
            }
            .pointerInput(Unit) {
                var totalDragX = 0f
                detectHorizontalDragGestures(
                    onDragStart = { totalDragX = 0f },
                    onHorizontalDrag = { _, dragAmount ->
                        totalDragX += dragAmount
                        if (totalDragX < -55f && !showActions) {
                            showActions = true
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        } else if (totalDragX > 55f && showActions) {
                            showActions = false
                        }
                    }
                )
            }
            .padding(14.dp)
    ) {
        content()
        if (onEdit != null || onDelete != null) {
            AnimatedVisibility(visible = showActions) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onEdit != null) {
                        TextButton(onClick = { showActions = false; onEdit() }) {
                            Icon(Icons.Outlined.Edit, contentDescription = "Edit", tint = palette.accent, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Edit", color = palette.accent, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    if (onDelete != null) {
                        TextButton(onClick = { showActions = false; onDelete() }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Delete", tint = palette.statusRed, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Delete", color = palette.statusRed, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MobileTopBar(
    settings: ShopSettings,
    currentSection: NavSection,
    items: List<StockItem>,
    isSearchActive: Boolean,
    onSearchActiveChange: (Boolean) -> Unit,
    onSelectStockItem: (StockItem) -> Unit,
    onNavigate: (NavSection) -> Unit,
    onQuickAction: (String) -> Unit
) {
    val palette = LocalNoorColors.current
    val lang = settings.language
    var query by remember { mutableStateOf("") }
    var menuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(palette.background)
            .border(width = 0.5.dp, color = palette.border)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSearchActive) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Type to search stock", fontSize = 13.sp) },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, tint = palette.accent) },
                    trailingIcon = {
                        IconButton(onClick = {
                            query = ""
                            onSearchActiveChange(false)
                        }) {
                            Icon(Icons.Outlined.Close, contentDescription = "Close search")
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                )
            } else {
                IconButton(
                    onClick = { onSearchActiveChange(true) },
                    modifier = Modifier.testTag("top_search_button")
                ) {
                    Icon(Icons.Outlined.Search, contentDescription = "Search stock", tint = palette.textPrimary)
                }

                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.noor_software_logo_1795681598224),
                        contentDescription = "Noor Software Logo",
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(7.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(horizontalAlignment = Alignment.Start) {
                        Text(
                            text = currentSection.title(lang),
                            color = palette.textPrimary,
                            fontWeight = FontWeight.Black,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = settings.shopName,
                            color = palette.textMuted,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.testTag("top_menu_button")
                    ) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = "All Shop Modules", tint = palette.textPrimary)
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        modifier = Modifier
                            .background(palette.card)
                            .widthIn(min = 230.dp)
                    ) {
                        Text(
                            "All Shop Modules",
                            color = palette.accent,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                        NavSection.entries.filter { it.isMain }.forEach { sec ->
                            DropdownMenuItem(
                                text = { Text(sec.title(lang), color = palette.textPrimary) },
                                leadingIcon = { Icon(navSectionIcon(sec), contentDescription = null, tint = palette.accent) },
                                onClick = {
                                    menuExpanded = false
                                    onNavigate(sec)
                                }
                            )
                        }
                        HorizontalDivider(color = palette.border)
                        Text(
                            "More",
                            color = palette.textMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                        NavSection.entries.filter { !it.isMain }.forEach { sec ->
                            DropdownMenuItem(
                                text = { Text(sec.title(lang), color = palette.textPrimary) },
                                leadingIcon = { Icon(navSectionIcon(sec), contentDescription = null, tint = palette.textSecondary) },
                                onClick = {
                                    menuExpanded = false
                                    onNavigate(sec)
                                }
                            )
                        }
                        HorizontalDivider(color = palette.border)
                        Text(
                            "Quick add",
                            color = palette.textMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                        listOf(
                            "New sale bill" to "sale",
                            "New service" to "service",
                            "New purchase" to "purchase",
                            "New expense" to "expense"
                        ).forEach { (label, key) ->
                            DropdownMenuItem(
                                text = { Text(label, color = palette.accent, fontWeight = FontWeight.SemiBold) },
                                leadingIcon = { Icon(Icons.Outlined.Add, contentDescription = null, tint = palette.accent) },
                                onClick = {
                                    menuExpanded = false
                                    onQuickAction(key)
                                }
                            )
                        }
                    }
                }
            }
        }

        if (isSearchActive && query.isNotBlank()) {
            val hits = remember(query, items) {
                items.filter { Formatters.matchesStockQuery(it, query) }.take(40)
            }
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 280.dp)
                    .background(palette.card)
                    .border(0.5.dp, palette.border)
            ) {
                if (hits.isEmpty()) {
                    item {
                        Text(
                            "No items match your search & filter.",
                            color = palette.textSecondary,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                } else {
                    items(hits, key = { it.id }) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSearchActiveChange(false)
                                    query = ""
                                    onSelectStockItem(item)
                                }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.displayName, color = palette.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp)
                                Text(
                                    listOf(item.category, item.color).filter { it.isNotBlank() }.joinToString(" · "),
                                    color = palette.textSecondary,
                                    fontSize = 11.5.sp
                                )
                            }
                            StockBadgeChip(item)
                        }
                        HorizontalDivider(color = palette.border.copy(alpha = 0.4f))
                    }
                }
            }
        }
    }
}

@Composable
fun TabletSidebar(
    settings: ShopSettings,
    currentSection: NavSection,
    stockCount: Int,
    repairsInShopCount: Int,
    partiesCount: Int,
    onNavigate: (NavSection) -> Unit
) {
    val palette = LocalNoorColors.current
    val lang = settings.language
    Column(
        modifier = Modifier
            .width(228.dp)
            .fillMaxHeight()
            .background(palette.card)
            .border(0.5.dp, palette.border)
            .padding(12.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
            Image(
                painter = painterResource(id = R.drawable.noor_software_logo_1795681598224),
                contentDescription = "Noor Software Logo",
                modifier = Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text("Noor Software", color = palette.textPrimary, fontWeight = FontWeight.Black, fontSize = 15.sp)
                Text(settings.shopName, color = palette.textMuted, fontSize = 10.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.height(6.dp))
        NavSection.entries.forEach { sec ->
            val selected = sec == currentSection
            val countBadge = when (sec) {
                NavSection.ITEMS -> stockCount.toString()
                NavSection.REPAIRS, NavSection.SERVICE_STATUS -> repairsInShopCount.takeIf { it > 0 }?.toString()
                NavSection.PARTIES -> partiesCount.takeIf { it > 0 }?.toString()
                else -> null
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (selected) palette.accent.copy(alpha = 0.16f) else Color.Transparent)
                    .clickable { onNavigate(sec) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    navSectionIcon(sec),
                    contentDescription = null,
                    tint = if (selected) palette.accent else palette.textSecondary,
                    modifier = Modifier.size(19.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    sec.title(lang),
                    color = if (selected) palette.accent else palette.textPrimary,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 13.5.sp,
                    modifier = Modifier.weight(1f)
                )
                if (countBadge != null) {
                    Text(countBadge, color = palette.textMuted, fontSize = 11.5.sp)
                }
            }
        }
    }
}

@Composable
fun FloatingQuickCreatePlusButton(
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalNoorColors.current
    val haptic = LocalHapticFeedback.current
    Box(
        modifier = modifier
            .size(56.dp)
            .shadow(12.dp, CircleShape, ambientColor = palette.accent, spotColor = palette.accent)
            .clip(CircleShape)
            .background(palette.primaryGradient)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() },
                    onLongPress = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLongPress()
                    }
                )
            }
            .testTag("quick_create_plus_button"),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Outlined.Add, contentDescription = "Quick Create", tint = Color.White, modifier = Modifier.size(28.dp))
    }
}

@Composable
fun EdgeStepArrowButton(
    isNext: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalNoorColors.current
    Box(
        modifier = modifier
            .size(width = 28.dp, height = 44.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(palette.card.copy(alpha = 0.85f))
            .border(1.dp, palette.border, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            if (isNext) Icons.AutoMirrored.Outlined.KeyboardArrowRight else Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
            contentDescription = if (isNext) "Next section" else "Previous section",
            tint = palette.textSecondary,
            modifier = Modifier.size(20.dp)
        )
    }
}
