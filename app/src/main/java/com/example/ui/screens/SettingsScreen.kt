package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.CompanyMeta
import com.example.data.model.ShopSettings
import com.example.data.model.ShopUser
import com.example.ui.MainViewModel
import com.example.ui.components.NoorSectionTitle
import com.example.ui.theme.LocalNoorColors
import com.example.ui.theme.noorCard
import com.example.util.Formatters
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class SettingsSubPage(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val keywords: String
) {
    SHOP_PROFILE(
        "Shop Profile",
        "Name, phone, Maps location, Instagram, GSTIN, logo, UPI ID & QR, prefix, terms",
        Icons.Outlined.Storefront,
        "shop name phone location maps instagram website gstin logo upi qr prefix invoice terms"
    ),
    BILLING_DEFAULTS(
        "Billing & Stock Defaults",
        "Low-stock threshold, opening cash in drawer, WhatsApp auto-send",
        Icons.Outlined.ReceiptLong,
        "low stock threshold opening cash drawer whatsapp auto send meta cloud api"
    ),
    LANGUAGE_SECURITY_USERS(
        "Language, Security & Users",
        "English / தமிழ், PIN lock, change password, staff accounts & permissions",
        Icons.Outlined.Security,
        "language tamil english pin lock password users staff permissions role"
    ),
    AI_ASSISTANT(
        "AI Assistant",
        "Floating voice bubble, Gemini API key, model speed, Tamil/English, screen share",
        Icons.Outlined.Mic,
        "ai assistant gemini api key voice bubble tamil english model flash lite screen share"
    ),
    APPEARANCE(
        "Appearance",
        "AMOLED theme presets, custom colours, dark/light, screen mode, tab bar",
        Icons.Outlined.Palette,
        "theme amoled pink cyan emerald gold color dark light screen mode tab navigation"
    ),
    COMPANY(
        "Company",
        "Switch between companies, add new shop, rename or delete company",
        Icons.Outlined.Business,
        "company switch shop multi store create rename delete"
    ),
    BACKUP_DATA(
        "Backup & Data",
        "JSON export/import, automatic backup schedule, cloud sync, sample data & reset",
        Icons.Outlined.CloudDone,
        "backup json export import automatic schedule cloud sync sample data clear reset"
    ),
    DEBUG_ABOUT(
        "Debug & About",
        "Permanent storage status, cloud sync state, error log & app version",
        Icons.Outlined.BugReport,
        "debug error log cloud status storage version about"
    )
}

@Composable
fun SettingsScreen(
    vm: MainViewModel,
    settings: ShopSettings,
    activeCompany: CompanyMeta,
    currentUser: ShopUser?,
    isOwner: Boolean,
    detectedScreenMode: String
) {
    val palette = LocalNoorColors.current
    val scope = rememberCoroutineScope()
    val debugLogs by vm.repo.debugLogs.collectAsStateWithLifecycle()
    val syncStatus by vm.repo.syncStatus.collectAsStateWithLifecycle()

    var activeSubPage by remember { mutableStateOf<SettingsSubPage?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var savedBadgeVisible by remember { mutableStateOf(false) }

    fun markSaved() {
        savedBadgeVisible = true
        scope.launch {
            delay(2000L)
            savedBadgeVisible = false
        }
    }

    if (activeSubPage != null) {
        BackHandler { activeSubPage = null }
    }

    val q = remember(searchQuery) { searchQuery.trim().lowercase() }
    val visibleGroups = remember(q) {
        SettingsSubPage.entries.filter { page ->
            q.isEmpty() ||
                page.title.lowercase().contains(q) ||
                page.subtitle.lowercase().contains(q) ||
                page.keywords.contains(q)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (activeSubPage == null) {
            item(key = "settings_header") {
                NoorSectionTitle(
                    title = "Settings & Safety",
                    subtitle = "Cloud sync, staff email accounts & permissions, shop profile & UPI QR, AMOLED theme, and permanent backup.",
                    trailing = {
                        if (savedBadgeVisible) {
                            Text("Saved", color = palette.statusGreen, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                        }
                    }
                )
            }

            item(key = "settings_search") {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search settings") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, tint = palette.accent) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("settings_search_input"),
                    shape = RoundedCornerShape(14.dp)
                )
            }

            items(visibleGroups, key = { it.name }) { page ->
                val needsOwnerBadge = !isOwner && (page == SettingsSubPage.BACKUP_DATA || page == SettingsSubPage.COMPANY)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .noorCard(palette, radius = 16.dp)
                        .clickable { activeSubPage = page }
                        .padding(14.dp)
                        .testTag("settings_group_${page.name.lowercase()}"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(palette.accent.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(page.icon, contentDescription = null, tint = palette.accent, modifier = Modifier.size(21.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(page.title, color = palette.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.5.sp)
                            if (needsOwnerBadge) {
                                Icon(Icons.Outlined.Lock, contentDescription = "Owner Only", tint = palette.textMuted, modifier = Modifier.size(14.dp))
                            }
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(page.subtitle, color = palette.textSecondary, fontSize = 12.sp, lineHeight = 16.sp)
                    }
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = palette.textMuted)
                }
            }
        } else {
            // Sub-page Header
            item(key = "subpage_header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        IconButton(onClick = { activeSubPage = null }) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = palette.textPrimary)
                        }
                        Column {
                            Text(activeSubPage!!.title, color = palette.textPrimary, fontWeight = FontWeight.Black, fontSize = 18.sp)
                            Text(activeSubPage!!.subtitle, color = palette.textSecondary, fontSize = 11.5.sp)
                        }
                    }
                    if (savedBadgeVisible) {
                        Text("Saved", color = palette.statusGreen, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                    }
                }
            }

            item(key = "subpage_${activeSubPage!!.name}") {
                when (activeSubPage!!) {
                    SettingsSubPage.SHOP_PROFILE -> ShopProfileSettingsPage(vm, settings, onSaved = ::markSaved)
                    SettingsSubPage.BILLING_DEFAULTS -> BillingDefaultsSettingsPage(vm, settings, onSaved = ::markSaved)
                    SettingsSubPage.LANGUAGE_SECURITY_USERS -> LanguageSecurityUsersPage(vm, settings, currentUser, isOwner, onSaved = ::markSaved)
                    SettingsSubPage.AI_ASSISTANT -> AiAssistantSettingsPage(vm, onSaved = ::markSaved)
                    SettingsSubPage.APPEARANCE -> AppearanceSettingsPage(vm, settings, detectedScreenMode, onSaved = ::markSaved)
                    SettingsSubPage.COMPANY -> {
                        Column(Modifier.fillMaxWidth().noorCard(palette).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("COMPANY", color = palette.accent, fontWeight = FontWeight.Bold, fontSize = 11.5.sp, letterSpacing = 1.sp)
                            SettingExplainer(
                                "Active Company: ${activeCompany.name}",
                                "Current: ${activeCompany.name}. Each company keeps separate stock, sales, services and settings."
                            )
                            Button(
                                onClick = { vm.setShowCompaniesSheet(true) },
                                colors = ButtonDefaults.buttonColors(containerColor = palette.accent)
                            ) {
                                Text("Manage companies", color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.Bold)
                            }
                            Text("Tip: download a backup (Settings) before deleting a company. Deleting is permanent.", color = palette.statusAmber, fontSize = 12.sp)
                        }
                    }
                    SettingsSubPage.BACKUP_DATA -> BackupDataSettingsPage(vm, settings, isOwner, onSaved = ::markSaved)
                    SettingsSubPage.DEBUG_ABOUT -> {
                        Column(Modifier.fillMaxWidth().noorCard(palette).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            SettingExplainer(
                                "Storage & Cloud Status",
                                "Checked permanent device storage status. Local Room database and Firebase Cloud Firestore offline cache are active."
                            )
                            Text("App Name: Noor Software (v10.0 Native Android)", color = palette.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text("Cloud Status: ${if (syncStatus.isOnline) "Online" else "Offline"} · Pending writes sync automatically", color = palette.textSecondary, fontSize = 12.5.sp)
                            if (!syncStatus.cloudError.isNullOrBlank()) {
                                Text("Cloud Sync Error: ${syncStatus.cloudError}", color = palette.statusRed, fontSize = 12.sp)
                            }
                            HorizontalDivider(color = palette.border)
                            Text("Error Log (${debugLogs.size})", color = palette.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                            if (debugLogs.isEmpty()) {
                                Text("No errors logged. Everything is running cleanly.", color = palette.statusGreen, fontSize = 12.5.sp)
                            } else {
                                debugLogs.take(25).forEach { log ->
                                    Text(
                                        "[${log.level}] ${Formatters.formatTime(log.timestamp)} (${log.source}): ${log.message}",
                                        color = if (log.level == "ERROR") palette.statusRed else palette.textSecondary,
                                        fontSize = 11.5.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
