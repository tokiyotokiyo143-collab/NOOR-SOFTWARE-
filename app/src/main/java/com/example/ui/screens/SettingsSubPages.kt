package com.example.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.*
import com.example.ui.MainViewModel
import com.example.ui.auth.ChangePasswordDialog
import com.example.ui.components.NoorPrimaryButton
import com.example.ui.components.NoorStatusDot
import com.example.ui.theme.LocalNoorColors
import com.example.ui.theme.noorCard
import com.example.util.Formatters
import com.example.util.WhatsAppHelper
import com.example.util.rememberCachedDataUrlBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingExplainer(label: String, explanation: String, isLocked: Boolean = false) {
    val palette = LocalNoorColors.current
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, color = palette.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            if (isLocked) {
                Icon(Icons.Outlined.Lock, contentDescription = "Owner Only", tint = palette.textMuted, modifier = Modifier.size(14.dp))
            }
        }
        Text(explanation, color = palette.textSecondary, fontSize = 12.sp, lineHeight = 16.sp)
    }
}

private fun encodePickedImageUnder200Kb(context: Context, uri: Uri): Pair<String, Boolean> {
    return try {
        val stream = context.contentResolver.openInputStream(uri) ?: return "" to false
        val bmp = BitmapFactory.decodeStream(stream)
        stream.close()
        if (bmp == null) return "" to false

        var scaled = bmp
        if (bmp.width > 512 || bmp.height > 512) {
            val ratio = minOf(512f / bmp.width, 512f / bmp.height)
            scaled = Bitmap.createScaledBitmap(bmp, (bmp.width * ratio).toInt(), (bmp.height * ratio).toInt(), true)
        }
        var quality = 85
        var bytes: ByteArray
        do {
            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
            bytes = out.toByteArray()
            quality -= 15
        } while (bytes.size > 195_000 && quality >= 25)

        val tooBig = bytes.size > 200_000
        val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        "data:image/jpeg;base64,$b64" to tooBig
    } catch (_: Exception) {
        "" to false
    }
}

@Composable
fun ShopProfileSettingsPage(
    vm: MainViewModel,
    settings: ShopSettings,
    onSaved: () -> Unit
) {
    val palette = LocalNoorColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val logoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                val (dataUrl, tooBig) = withContext(Dispatchers.IO) {
                    encodePickedImageUnder200Kb(context, uri)
                }
                if (dataUrl.isNotBlank()) {
                    vm.repo.updateSettings { it.copy(logoDataUrl = dataUrl) }
                    onSaved()
                    if (tooBig) {
                        Toast.makeText(context, "Shop logo exceeds 200KB limit and could not sync to the cloud.", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    val qrPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                val (dataUrl, tooBig) = withContext(Dispatchers.IO) {
                    encodePickedImageUnder200Kb(context, uri)
                }
                if (dataUrl.isNotBlank()) {
                    vm.repo.updateSettings { it.copy(upiQrDataUrl = dataUrl) }
                    onSaved()
                    if (tooBig) {
                        Toast.makeText(context, "UPI QR image exceeds 200KB limit and could not sync to the cloud.", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Shop Logo
        Column(Modifier.fillMaxWidth().noorCard(palette).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SettingExplainer(
                "Shop Logo",
                "Shown on the Sign-In header, app top bar, and printed customer bills. Max 200 KB for cloud sync."
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val bmp = rememberCachedDataUrlBitmap(settings.logoDataUrl)
                if (bmp != null) {
                    Image(bmp, contentDescription = "Shop Logo", modifier = Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)))
                }
                OutlinedButton(onClick = {
                    logoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) { Text("Upload Logo", color = palette.accent, fontWeight = FontWeight.Bold) }
                OutlinedButton(onClick = {
                    scope.launch { vm.repo.updateSettings { it.copy(logoDataUrl = "") }; onSaved() }
                }) { Text("Use Default") }
            }
        }

        Column(Modifier.fillMaxWidth().noorCard(palette).padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SettingExplainer("Shop Name", "Your business name displayed across the app, WhatsApp messages, and printed receipts.")
            OutlinedTextField(
                value = settings.shopName,
                onValueChange = { v -> scope.launch { vm.repo.updateSettings { it.copy(shopName = Formatters.clampName(v)) }; onSaved() } },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )

            SettingExplainer("Shop Phone", "Printed at the top of invoices, repair slips, and included in WhatsApp updates.")
            OutlinedTextField(
                value = settings.phone,
                onValueChange = { v -> scope.launch { vm.repo.updateSettings { it.copy(phone = Formatters.clampPhone(v)) }; onSaved() } },
                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth()
            )

            SettingExplainer(
                "Location (paste Google Maps link or address)",
                "Tap to check the link opens the right place. Printed as a clickable \"Find us on Google Maps\" link on PDFs."
            )
            OutlinedTextField(
                value = settings.address,
                onValueChange = { v -> scope.launch { vm.repo.updateSettings { it.copy(address = Formatters.clampAddress(v)) }; onSaved() } },
                placeholder = { Text("Paste Google Maps link here") },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            if (settings.address.isNotBlank()) {
                TextButton(onClick = { WhatsAppHelper.openMapsLink(context, settings.address) }) {
                    Icon(Icons.Outlined.LocationOn, contentDescription = null, tint = palette.accent, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Open this location", color = palette.accent, fontWeight = FontWeight.Bold)
                }
            }

            SettingExplainer("Instagram ID or link", "Appears on bills as \"Instagram: @handle\" so customers can follow your shop.")
            OutlinedTextField(
                value = settings.instagram,
                onValueChange = { v -> scope.launch { vm.repo.updateSettings { it.copy(instagram = v.trim()) }; onSaved() } },
                placeholder = { Text("@yourshop or instagram.com/yourshop") },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )

            SettingExplainer("Website", "Optional shop website link saved in your shop profile.")
            OutlinedTextField(
                value = settings.website,
                onValueChange = { v -> scope.launch { vm.repo.updateSettings { it.copy(website = v.trim()) }; onSaved() } },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )

            SettingExplainer("GSTIN Number (Optional)", "Printed on tax invoices when filled. Leave blank if not GST registered.")
            OutlinedTextField(
                value = settings.gstNumber,
                onValueChange = { v -> scope.launch { vm.repo.updateSettings { it.copy(gstNumber = v.trim()) }; onSaved() } },
                placeholder = { Text("e.g. 33ABCDE1234F1Z5") },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
        }

        Column(Modifier.fillMaxWidth().noorCard(palette).padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SettingExplainer("UPI ID (Shown on UPI Invoices & WhatsApp)", "Used to auto-generate a Scan & Pay QR on invoices if no custom QR image is uploaded.")
            OutlinedTextField(
                value = settings.upiId,
                onValueChange = { v -> scope.launch { vm.repo.updateSettings { it.copy(upiId = v.trim()) }; onSaved() } },
                placeholder = { Text("e.g. shop@okaxis") },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )

            SettingExplainer("UPI QR Code Image", "Printed at the bottom of bills for instant customer payment. Max 200 KB.")
            val qrBmp = rememberCachedDataUrlBitmap(settings.upiQrDataUrl)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (qrBmp != null) {
                    Image(qrBmp, contentDescription = "UPI QR", modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)))
                } else {
                    Text("No QR", color = palette.textMuted, fontSize = 12.5.sp)
                }
                OutlinedButton(onClick = {
                    qrPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) { Text("Upload QR", color = palette.accent, fontWeight = FontWeight.Bold) }
                if (settings.upiQrDataUrl.isNotBlank()) {
                    OutlinedButton(onClick = {
                        scope.launch { vm.repo.updateSettings { it.copy(upiQrDataUrl = "") }; onSaved() }
                    }) { Text("Remove", color = palette.statusRed) }
                }
            }

            SettingExplainer("Invoice Prefix", "Letters printed before every bill number, for example AN-0001.")
            OutlinedTextField(
                value = settings.invoicePrefix,
                onValueChange = { v -> scope.launch { vm.repo.updateSettings { it.copy(invoicePrefix = v.take(10)) }; onSaved() } },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )

            SettingExplainer("Invoice & Warranty Terms", "Footer line printed at the bottom of every customer receipt.")
            OutlinedTextField(
                value = settings.invoiceTerms,
                onValueChange = { v -> scope.launch { vm.repo.updateSettings { it.copy(invoiceTerms = Formatters.clampNotes(v)) }; onSaved() } },
                placeholder = { Text("Goods once sold cannot be returned without original bill.") },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun BillingDefaultsSettingsPage(
    vm: MainViewModel,
    settings: ShopSettings,
    onSaved: () -> Unit
) {
    val palette = LocalNoorColors.current
    val scope = rememberCoroutineScope()
    var lowStockText by remember(settings.lowStockDefault) { mutableStateOf(settings.lowStockDefault.toString()) }
    var openCashText by remember(settings.openingCashBalance) { mutableStateOf(Formatters.formatDecimal(settings.openingCashBalance)) }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(Modifier.fillMaxWidth().noorCard(palette).padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SettingExplainer(
                "Default Low-Stock Threshold (pcs)",
                "When an item has this many pieces or fewer, it shows as Low Stock and appears in the Restock list."
            )
            OutlinedTextField(
                value = lowStockText,
                onValueChange = { v ->
                    lowStockText = v
                    v.toIntOrNull()?.let { n ->
                        scope.launch { vm.repo.updateSettings { it.copy(lowStockDefault = n.coerceAtLeast(0)) }; onSaved() }
                    }
                },
                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth()
            )

            SettingExplainer(
                "Opening Cash in Drawer (₹)",
                "Cash already in your drawer when you started using the app. Used to calculate today's opening balance."
            )
            OutlinedTextField(
                value = openCashText,
                onValueChange = { v ->
                    openCashText = v
                    v.toDoubleOrNull()?.let { amt ->
                        scope.launch { vm.repo.updateSettings { it.copy(openingCashBalance = Formatters.clampAmount(amt)) }; onSaved() }
                    }
                },
                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth()
            )
        }

        Column(Modifier.fillMaxWidth().noorCard(palette).padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    SettingExplainer(
                        "WhatsApp Auto-Send on Bill Creation",
                        "Automatically sends a summary message to the customer's WhatsApp number when you save a new bill."
                    )
                }
                Switch(
                    checked = settings.whatsappAutoSend,
                    onCheckedChange = { c ->
                        scope.launch { vm.repo.updateSettings { it.copy(whatsappAutoSend = c) }; onSaved() }
                    }
                )
            }

            SettingExplainer(
                "Meta WhatsApp Business Cloud API — Phone Number ID",
                "Optional Meta Cloud API Phone ID for silent auto-sending; if blank, standard WhatsApp chat opens instead."
            )
            OutlinedTextField(
                value = settings.metaWhatsappPhoneId,
                onValueChange = { v -> scope.launch { vm.repo.updateSettings { it.copy(metaWhatsappPhoneId = v.trim()) }; onSaved() } },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )

            SettingExplainer(
                "Meta WhatsApp Business Cloud API — Permanent Access Token",
                "Optional bearer token for Meta Cloud API; kept on this shop configuration."
            )
            OutlinedTextField(
                value = settings.metaWhatsappToken,
                onValueChange = { v -> scope.launch { vm.repo.updateSettings { it.copy(metaWhatsappToken = v.trim()) }; onSaved() } },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun LanguageSecurityUsersPage(
    vm: MainViewModel,
    settings: ShopSettings,
    currentUser: ShopUser?,
    isOwner: Boolean,
    onSaved: () -> Unit
) {
    val palette = LocalNoorColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var pinText by remember { mutableStateOf(vm.secretStore.getDevicePin()) }
    var showChangePass by remember { mutableStateOf(false) }

    // Staff creation form
    var staffName by remember { mutableStateOf("") }
    var staffEmail by remember { mutableStateOf("") }
    var staffTempPass by remember { mutableStateOf("") }
    var staffRole by remember { mutableStateOf("staff") }
    var staffPerms by remember { mutableStateOf(UserPermissions.preset("Sales Counter")) }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Language
        Column(Modifier.fillMaxWidth().noorCard(palette).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SettingExplainer(
                "App Language (English / தமிழ்)",
                "Switches navigation tabs & key dashboard buttons immediately across the app."
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("en" to "English", "ta" to "தமிழ்").forEach { (code, label) ->
                    val sel = settings.language == code
                    FilterChip(
                        selected = sel,
                        onClick = { scope.launch { vm.repo.updateSettings { it.copy(language = code) }; onSaved() } },
                        label = { Text(label, fontWeight = FontWeight.Bold) }
                    )
                }
            }
        }

        // Device PIN Lock & Password
        Column(Modifier.fillMaxWidth().noorCard(palette).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SettingExplainer(
                "Device App PIN Lock",
                "Optional quick register lock with a 4–6 digit PIN on this device. Never synced to the cloud.",
                isLocked = !isOwner
            )
            OutlinedTextField(
                value = pinText,
                onValueChange = { v ->
                    if (!isOwner) {
                        Toast.makeText(context, "Only Owner can change PIN lock settings.", Toast.LENGTH_SHORT).show()
                        return@OutlinedTextField
                    }
                    val digits = v.filter { it.isDigit() }.take(6)
                    pinText = digits
                    if (digits.isEmpty() || digits.length in 4..6) {
                        vm.secretStore.setDevicePin(digits)
                        onSaved()
                    }
                },
                enabled = isOwner,
                label = { Text("4–6 Digit PIN Code") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    if (!vm.lockNowWithPin()) {
                        Toast.makeText(context, "Enter a 4–6 digit PIN code first.", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Lock Now", color = palette.accent, fontWeight = FontWeight.Bold) }

                OutlinedButton(onClick = { showChangePass = true }) {
                    Text("Change Password", color = palette.textPrimary)
                }
                OutlinedButton(onClick = { vm.signOut() }) {
                    Text("Sign Out", color = palette.statusRed)
                }
            }
        }

        // Users & Permissions (Owner only)
        Column(Modifier.fillMaxWidth().noorCard(palette).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SettingExplainer(
                "Users & Feature Permissions (Email & Password Login)",
                "Add staff with their name, email, temporary password, and custom permissions. Cost prices & Profit & Loss remain Owner-Only.",
                isLocked = !isOwner
            )

            settings.users.forEach { u ->
                val isSelf = currentUser?.email?.equals(u.email, true) == true
                Column(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(palette.inputBg).padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(u.name.ifBlank { u.email }, color = palette.textPrimary, fontWeight = FontWeight.Bold)
                            Text(u.email, color = palette.textSecondary, fontSize = 12.sp)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (isSelf) Text("You (Signed In)", color = palette.statusGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            if (!u.active) Text("Deactivated", color = palette.statusRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            else Text(
                                when {
                                    u.role.equals("co_owner", true) -> "Co-Owner"
                                    u.role.equals("owner", true) || u.email.equals(PRIMARY_OWNER_EMAIL, true) -> "Owner"
                                    else -> "Staff"
                                },
                                color = palette.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    if (isOwner && !u.email.equals(PRIMARY_OWNER_EMAIL, true)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TextButton(onClick = {
                                scope.launch { vm.repo.addOrUpdateUser(u.copy(active = !u.active)); onSaved() }
                            }) { Text(if (u.active) "Deactivate" else "Reactivate", color = palette.statusAmber, fontSize = 11.5.sp) }

                            TextButton(onClick = {
                                if (u.email.isBlank()) Toast.makeText(context, "User has no email address.", Toast.LENGTH_SHORT).show()
                                else vm.sendPasswordReset(u.email) { _, msg -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() }
                            }) { Text("Reset Password", fontSize = 11.5.sp) }

                            TextButton(onClick = {
                                val remainingOwners = settings.users.count { it.id != u.id && it.isOwnerOrCoOwner && it.active }
                                if (u.isOwnerOrCoOwner && remainingOwners == 0) {
                                    Toast.makeText(context, "At least one Owner account is required.", Toast.LENGTH_SHORT).show()
                                } else {
                                    scope.launch {
                                        vm.repo.removeUser(u.id)
                                        Toast.makeText(context, "Removed access for ${u.email.ifBlank { u.name }}.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }) { Text("Remove", color = palette.statusRed, fontSize = 11.5.sp) }
                        }
                    }
                }
            }

            if (isOwner) {
                HorizontalDivider(color = palette.border)
                Text("Add New Staff Member", color = palette.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                OutlinedTextField(staffName, { staffName = it }, label = { Text("Staff Name") }, placeholder = { Text("Staff Name (e.g. Arun)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(staffEmail, { staffEmail = it }, label = { Text("Email") }, placeholder = { Text("Email (e.g. arun@gmail.com)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(staffTempPass, { staffTempPass = it }, label = { Text("Temporary Password") }, placeholder = { Text("Temporary Password (6+ chars)") }, singleLine = true, modifier = Modifier.fillMaxWidth())

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = staffRole == "staff", onClick = { staffRole = "staff" }, label = { Text("Staff (Custom Permissions)", fontSize = 11.sp) })
                    FilterChip(selected = staffRole == "co_owner", onClick = { staffRole = "co_owner" }, label = { Text("Co-Owner (Full Access)", fontSize = 11.sp) })
                }

                if (staffRole == "staff") {
                    Text("Quick Role Presets:", color = palette.textMuted, fontSize = 11.5.sp)
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Sales Counter", "Purchase & Stock", "Service Technician", "Cash & Bank", "Full Staff").forEach { preset ->
                            AssistChip(onClick = { staffPerms = UserPermissions.preset(preset) }, label = { Text(preset, fontSize = 11.sp) })
                        }
                    }
                    PermissionSwitchRow("Sales: Create Bills", staffPerms.salesCreate) { staffPerms = staffPerms.copy(salesCreate = it) }
                    PermissionSwitchRow("Sales: Edit / Return / Cancel", staffPerms.salesEditReturnCancel) { staffPerms = staffPerms.copy(salesEditReturnCancel = it) }
                    PermissionSwitchRow("Purchases: Create Bills", staffPerms.purchasesCreate) { staffPerms = staffPerms.copy(purchasesCreate = it) }
                    PermissionSwitchRow("Purchases: Edit / Delete", staffPerms.purchasesEditDelete) { staffPerms = staffPerms.copy(purchasesEditDelete = it) }
                    PermissionSwitchRow("Service: Create", staffPerms.serviceCreate) { staffPerms = staffPerms.copy(serviceCreate = it) }
                    PermissionSwitchRow("Service: Update Status & Bill", staffPerms.serviceUpdateBill) { staffPerms = staffPerms.copy(serviceUpdateBill = it) }
                    PermissionSwitchRow("Stock: View Items", staffPerms.stockView) { staffPerms = staffPerms.copy(stockView = it) }
                    PermissionSwitchRow("Stock: Add / Edit / Bulk / Rates", staffPerms.stockEditBulkRates) { staffPerms = staffPerms.copy(stockEditBulkRates = it) }
                    PermissionSwitchRow("Parties & Customer Dues", staffPerms.partiesDues) { staffPerms = staffPerms.copy(partiesDues = it) }
                    PermissionSwitchRow("Shop Expenses", staffPerms.expenses) { staffPerms = staffPerms.copy(expenses = it) }
                    PermissionSwitchRow("Reports (Non-Profit Summary)", staffPerms.reports) { staffPerms = staffPerms.copy(reports = it) }
                    PermissionSwitchRow("Search Models", staffPerms.searchModels) { staffPerms = staffPerms.copy(searchModels = it) }
                    PermissionSwitchRow("Settings Access", staffPerms.settings) { staffPerms = staffPerms.copy(settings = it) }
                }

                NoorPrimaryButton(
                    text = "Create Staff Account",
                    onClick = {
                        vm.createStaffAccount(staffName, staffEmail, staffTempPass, staffRole, staffPerms) { ok, msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            if (ok) { staffName = ""; staffEmail = ""; staffTempPass = "" }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    if (showChangePass) {
        ChangePasswordDialog(
            onDismiss = { showChangePass = false },
            onSubmit = { cur, np, cp, cb -> vm.changePassword(cur, np, cp, cb) }
        )
    }
}

@Composable
private fun PermissionSwitchRow(label: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    val palette = LocalNoorColors.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = palette.textPrimary, fontSize = 12.5.sp, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onToggle)
    }
}

@Composable
fun AiAssistantSettingsPage(
    vm: MainViewModel,
    onSaved: () -> Unit
) {
    val palette = LocalNoorColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val aiSettings by vm.aiSettings.collectAsStateWithLifecycle()

    var apiKey by remember { mutableStateOf(vm.secretStore.getGeminiApiKey()) }
    var showKey by remember { mutableStateOf(false) }
    var testStatus by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(Modifier.fillMaxWidth().noorCard(palette).padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    SettingExplainer(
                        "Enable AI Assistant",
                        "Turn the floating AI bubble on or off. When off, the bubble is hidden, the microphone is not used and nothing is sent to Gemini."
                    )
                }
                Switch(
                    checked = aiSettings.enabled,
                    modifier = Modifier.testTag("ai_enable_switch"),
                    onCheckedChange = { c ->
                        if (c && apiKey.isBlank()) {
                            Toast.makeText(context, "Enter your Gemini API Key below first.", Toast.LENGTH_SHORT).show()
                        }
                        vm.updateAiSettings { it.copy(enabled = c) }
                        onSaved()
                    }
                )
            }

            SettingExplainer(
                "Gemini API Key",
                "Your personal Gemini API key stored encrypted in Android Keystore on this device only. Never synced to cloud or printed."
            )
            OutlinedTextField(
                value = apiKey,
                onValueChange = { v ->
                    apiKey = v
                    vm.secretStore.setGeminiApiKey(v)
                    onSaved()
                },
                label = { Text("Gemini API Key") },
                placeholder = { Text("AIza...") },
                singleLine = true,
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    TextButton(onClick = { showKey = !showKey }) {
                        Text(if (showKey) "Hide" else "Show", color = palette.accent, fontSize = 11.5.sp)
                    }
                },
                modifier = Modifier.fillMaxWidth().testTag("ai_api_key_input")
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    enabled = !testing && apiKey.isNotBlank(),
                    onClick = {
                        testing = true
                        testStatus = "Testing..."
                        scope.launch {
                            val res = withContext(Dispatchers.IO) {
                                runCatching {
                                    val req = Request.Builder()
                                        .url("https://generativelanguage.googleapis.com/v1beta/models/${aiSettings.model}?key=${apiKey.trim()}")
                                        .get()
                                        .build()
                                    OkHttpClient().newCall(req).execute().use { r ->
                                        if (r.isSuccessful) "Connected! ${aiSettings.model} is ready."
                                        else "Error HTTP ${r.code}: Check key or quota."
                                    }
                                }.getOrElse { "Network error: ${it.message}" }
                            }
                            testStatus = res
                            testing = false
                        }
                    }
                ) { Text("Test", color = palette.accent, fontWeight = FontWeight.Bold) }
                if (testStatus != null) {
                    Text(testStatus!!, color = palette.textSecondary, fontSize = 12.sp, modifier = Modifier.weight(1f))
                }
            }

            SettingExplainer(
                "AI Model",
                "Choose Flash-Lite for the fastest 1–2 second voice actions, or Flash for deeper multi-step reasoning."
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "gemini-3.5-flash-lite" to "Flash-Lite (Fastest)",
                    "gemini-3.5-flash" to "Flash"
                ).forEach { (m, label) ->
                    FilterChip(
                        selected = aiSettings.model == m,
                        onClick = { vm.updateAiSettings { it.copy(model = m) }; onSaved() },
                        label = { Text(label, fontSize = 12.sp) }
                    )
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    SettingExplainer("Speak replies aloud", "Reads the AI assistant's short confirmation aloud using Android text-to-speech.")
                }
                Switch(
                    checked = aiSettings.speakRepliesAloud,
                    onCheckedChange = { c -> vm.updateAiSettings { it.copy(speakRepliesAloud = c) }; onSaved() }
                )
            }

            SettingExplainer(
                "Reply language",
                "Default language for short AI replies (automatically follows English when you speak English)."
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = aiSettings.replyLanguage == "ta",
                    onClick = { vm.updateAiSettings { it.copy(replyLanguage = "ta") }; onSaved() },
                    label = { Text("Tamil (Default)") }
                )
                FilterChip(
                    selected = aiSettings.replyLanguage == "en",
                    onClick = { vm.updateAiSettings { it.copy(replyLanguage = "en") }; onSaved() },
                    label = { Text("English") }
                )
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    SettingExplainer("Ask confirmation before delete or big actions", "Prompts \"confirm?\" before deleting records, cancelling bills, closing day, or large payments.")
                }
                Switch(
                    checked = aiSettings.askConfirmationForDestructive,
                    onCheckedChange = { c -> vm.updateAiSettings { it.copy(askConfirmationForDestructive = c) }; onSaved() }
                )
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    SettingExplainer("Allow screen share", "Enables the Share screen button in the AI panel so Gemini can see your current bill or report.")
                }
                Switch(
                    checked = aiSettings.allowScreenShare,
                    onCheckedChange = { c -> vm.updateAiSettings { it.copy(allowScreenShare = c) }; onSaved() }
                )
            }

            SettingExplainer("Bubble position reset", "Returns the floating AI bubble to the bottom-right corner.")
            OutlinedButton(onClick = {
                vm.updateAiSettings { it.copy(bubbleOffsetXFraction = 0.92f, bubbleOffsetYFraction = 0.72f) }
                onSaved()
            }) { Text("Reset Bubble Position", color = palette.accent, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
fun AppearanceSettingsPage(
    vm: MainViewModel,
    settings: ShopSettings,
    detectedMode: String,
    onSaved: () -> Unit
) {
    val palette = LocalNoorColors.current
    val scope = rememberCoroutineScope()

    val accentSwatches = listOf("#FF1F8E", "#06B6D4", "#10B981", "#F59E0B", "#EC4899", "#8B5CF6")
    val bgSwatches = listOf("#000000", "#090D12", "#080C0A", "#0A0908", "#121216")
    val cardSwatches = listOf("#0E0E12", "#131A22", "#121A16", "#171512", "#1A1A22")

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(Modifier.fillMaxWidth().noorCard(palette).padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SettingExplainer(
                "Theme Presets",
                "Pick one of the 4 AMOLED colour themes. Changes apply live across all cards and buttons."
            )
            THEME_PRESETS.forEach { preset ->
                val sel = settings.themePreset == preset.id
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (sel) palette.accent.copy(alpha = 0.16f) else palette.inputBg)
                        .border(1.dp, if (sel) palette.accent else palette.border, RoundedCornerShape(12.dp))
                        .clickable {
                            scope.launch {
                                vm.repo.updateSettings {
                                    it.copy(
                                        themePreset = preset.id,
                                        accentColor = preset.accentHex,
                                        bgColor = preset.bgHex,
                                        cardColor = preset.cardHex
                                    )
                                }
                                onSaved()
                            }
                        }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        val swatchColor = remember(preset.accentHex) { Color(android.graphics.Color.parseColor(preset.accentHex)) }
                        Box(
                            Modifier.size(20.dp).clip(CircleShape)
                                .background(swatchColor)
                        )
                        Text(preset.label, color = palette.textPrimary, fontWeight = FontWeight.Bold)
                    }
                    if (sel) Icon(Icons.Outlined.Check, contentDescription = null, tint = palette.accent)
                }
            }

            SettingExplainer("Custom Accent Color", "Changes highlights, primary buttons, borders, and active badges.")
            ColorPickerRow(accentSwatches, settings.accentColor) { hex ->
                scope.launch { vm.repo.updateSettings { it.copy(accentColor = hex) }; onSaved() }
            }

            SettingExplainer("AMOLED Background Color", "Pure black saves battery on OLED phones; choose a tint if preferred.")
            ColorPickerRow(bgSwatches, settings.bgColor) { hex ->
                scope.launch { vm.repo.updateSettings { it.copy(bgColor = hex) }; onSaved() }
            }

            SettingExplainer("AMOLED Card Color", "Background shade of cards, dialogs, and bottom sheets.")
            ColorPickerRow(cardSwatches, settings.cardColor) { hex ->
                scope.launch { vm.repo.updateSettings { it.copy(cardColor = hex) }; onSaved() }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    SettingExplainer("Display Mode (AMOLED Dark / Light)", "Pure black AMOLED saves battery, or switch to crisp Daylight Light mode.")
                }
                Switch(
                    checked = settings.colorMode == "light",
                    onCheckedChange = { isLight ->
                        scope.launch { vm.repo.updateSettings { it.copy(colorMode = if (isLight) "light" else "dark") }; onSaved() }
                    }
                )
            }
        }

        Column(Modifier.fillMaxWidth().noorCard(palette).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SettingExplainer(
                "Screen Mode",
                if (settings.screenMode == "auto") "Auto-detected: $detectedMode — Mobile uses top menu + swipe; Tab/Computer shows left sidebar."
                else "Set manually on this device (${settings.screenMode})."
            )
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("auto" to "Auto", "mobile" to "Mobile", "tab" to "Tab", "computer" to "Computer").forEach { (k, l) ->
                    FilterChip(
                        selected = settings.screenMode == k,
                        onClick = { scope.launch { vm.repo.updateSettings { it.copy(screenMode = k) }; onSaved() } },
                        label = { Text(l, fontSize = 12.sp) }
                    )
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    SettingExplainer("Bottom / Tab Navigation", "Show quick section tabs at the bottom on mobile screens.")
                }
                Switch(
                    checked = settings.useTabNavigation,
                    onCheckedChange = { c -> scope.launch { vm.repo.updateSettings { it.copy(useTabNavigation = c) }; onSaved() } }
                )
            }
        }
    }
}

@Composable
private fun ColorPickerRow(swatches: List<String>, selectedHex: String, onSelect: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        swatches.forEach { hex ->
            val col = remember(hex) { Color(android.graphics.Color.parseColor(hex)) }
            val sel = selectedHex.equals(hex, true)
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(col)
                    .border(if (sel) 3.dp else 1.dp, if (sel) Color.White else Color.Gray, CircleShape)
                    .clickable { onSelect(hex) }
            )
        }
    }
}

@Composable
fun BackupDataSettingsPage(
    vm: MainViewModel,
    settings: ShopSettings,
    isOwner: Boolean,
    onSaved: () -> Unit
) {
    val palette = LocalNoorColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val syncStatus by vm.repo.syncStatus.collectAsStateWithLifecycle()
    val autoBackups by vm.repo.autoBackups.collectAsStateWithLifecycle()

    var showClearConfirm by remember { mutableStateOf(false) }
    var showCustomDays by remember { mutableStateOf(false) }
    var customDaysInput by remember { mutableStateOf(settings.backupCustomDays.toString()) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            scope.launch {
                val json = vm.repo.exportBackupJson()
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                }
                vm.repo.updateSettings { it.copy(lastBackupAt = System.currentTimeMillis()) }
                Toast.makeText(context, "Backup exported!", Toast.LENGTH_SHORT).show()
                onSaved()
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val text = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
                }
                vm.importBackupJsonText(text) { _, msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Permanent Storage & Cloud Sync
        Column(Modifier.fillMaxWidth().noorCard(palette).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SettingExplainer(
                "Permanent Device Storage (Never Auto-Deleted)",
                "All bills, reports, stock, repairs, and ledger records stay permanently saved in device database + Cloud Sync."
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NoorStatusDot(if (syncStatus.isOnline) palette.statusGreen else palette.statusAmber)
                Text(
                    if (syncStatus.isOnline) "Cloud Sync Active · ${if (syncStatus.lastSyncedAt > 0L) Formatters.formatTime(syncStatus.lastSyncedAt) else "Connected"}"
                    else "Offline Mode (Pending writes will sync automatically)",
                    color = palette.textPrimary, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold
                )
            }
        }

        // JSON Backup & Restore
        Column(Modifier.fillMaxWidth().noorCard(palette).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SettingExplainer(
                "One-Tap JSON Backup & Restore",
                "Export or import full shop JSON backups compatible with the web app (Noor-Billing-Backup-YYYY-MM-DD.json)."
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NoorPrimaryButton(
                    text = "Export Backup",
                    onClick = { exportLauncher.launch("Noor-Billing-Backup-${Formatters.todayIso()}.json") },
                    modifier = Modifier.weight(1f)
                )
                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) },
                    modifier = Modifier.weight(1f)
                ) { Text("Import Backup (JSON)", color = palette.accent, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
            }
        }

        // Automatic Backup Schedule
        Column(Modifier.fillMaxWidth().noorCard(palette).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SettingExplainer(
                "Back up automatically",
                "Saves an on-device JSON snapshot automatically on your chosen schedule (keeps the last 6 snapshots)."
            )
            val options = listOf(
                "monthly" to "Monthly",
                "7" to "Every 7 days",
                "15" to "Every 15 days",
                "30" to "Every 30 days",
                "custom" to "Custom days...",
                "off" to "Off"
            )
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                options.forEach { (k, label) ->
                    FilterChip(
                        selected = settings.backupSchedule == k,
                        onClick = {
                            if (k == "custom") showCustomDays = true
                            else scope.launch { vm.repo.updateSettings { it.copy(backupSchedule = k) }; onSaved() }
                        },
                        label = { Text(label, fontSize = 11.5.sp) }
                    )
                }
            }
            val lastStr = if (settings.lastBackupAt > 0) Formatters.formatDisplayDate(
                SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(settings.lastBackupAt))
            ) else "Never"
            val nextIntervalDays = when (settings.backupSchedule) {
                "7" -> 7
                "15" -> 15
                "30", "monthly" -> 30
                "custom" -> settings.backupCustomDays
                else -> 0
            }
            val nextStr = if (nextIntervalDays == 0) "Off" else Formatters.formatDisplayDate(
                SimpleDateFormat("yyyy-MM-dd", Locale.US).format(
                    Date((if (settings.lastBackupAt > 0) settings.lastBackupAt else System.currentTimeMillis()) + nextIntervalDays * 86_400_000L)
                )
            )
            Text("Last backup: $lastStr · next: $nextStr", color = palette.textSecondary, fontSize = 12.sp)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.triggerManualBackup(); onSaved() }) {
                    Text("Back up now", color = palette.accent, fontWeight = FontWeight.Bold)
                }
                if (autoBackups.isNotEmpty()) {
                    OutlinedButton(onClick = { exportLauncher.launch(autoBackups.first().label) }) {
                        Text("Download last backup", color = palette.textPrimary)
                    }
                }
            }
        }

        // Sample Data
        Column(Modifier.fillMaxWidth().noorCard(palette).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SettingExplainer(
                "Sample Data",
                "Load demo mobile shop items, a repair job, a bank account, and a sample invoice to test features quickly."
            )
            OutlinedButton(onClick = {
                scope.launch {
                    vm.repo.loadSampleData(isOwner)
                    Toast.makeText(context, "Sample shop items, repair, bank account & invoice added.", Toast.LENGTH_LONG).show()
                }
            }) { Text("Load Sample Data", color = palette.accent, fontWeight = FontWeight.Bold) }
        }

        // Danger Zone
        if (isOwner) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(palette.statusRed.copy(alpha = 0.08f))
                    .border(1.dp, palette.statusRed.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("DANGER ZONE", color = palette.statusRed, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 1.sp)
                SettingExplainer(
                    "Clear / Reset All Data",
                    "Erases all items, bills, repairs, parties, and bank entries in the active company database. Export JSON first!"
                )
                Button(
                    onClick = { showClearConfirm = true },
                    colors = ButtonDefaults.buttonColors(containerColor = palette.statusRed)
                ) {
                    Text("Clear / Reset All Data", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            containerColor = palette.card,
            title = { Text("Clear / Reset All Data?", color = palette.statusRed, fontWeight = FontWeight.Bold) },
            text = { Text("Erase all items, bills, repairs, parties, and bank entries? Export JSON first if needed.", color = palette.textSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        vm.repo.clearAllData()
                        Toast.makeText(context, "All shop data cleared.", Toast.LENGTH_SHORT).show()
                    }
                    showClearConfirm = false
                }) { Text("Yes, Clear Everything", color = palette.statusRed, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showClearConfirm = false }) { Text("Cancel") } }
        )
    }

    if (showCustomDays) {
        AlertDialog(
            onDismissRequest = { showCustomDays = false },
            containerColor = palette.card,
            title = { Text("Custom Backup Interval (Days)", color = palette.textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = customDaysInput,
                    onValueChange = { customDaysInput = it.filter { c -> c.isDigit() } },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val days = (customDaysInput.toIntOrNull() ?: 30).coerceIn(1, 365)
                    scope.launch {
                        vm.repo.updateSettings { it.copy(backupSchedule = "custom", backupCustomDays = days) }
                        onSaved()
                    }
                    showCustomDays = false
                }) { Text("Save", color = palette.accent, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showCustomDays = false }) { Text("Cancel") } }
        )
    }
}
