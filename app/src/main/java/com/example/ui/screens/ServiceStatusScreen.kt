package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.NavSection
import com.example.data.model.RepairJob
import com.example.data.model.ShopSettings
import com.example.ui.MainViewModel
import com.example.ui.components.NoorSectionTitle
import com.example.ui.theme.LocalNoorColors
import com.example.ui.theme.noorCard
import com.example.util.Formatters
import com.example.util.WhatsAppHelper
import kotlinx.coroutines.launch

@Composable
fun ServiceStatusScreen(
    vm: MainViewModel,
    settings: ShopSettings,
    repairs: List<RepairJob>,
    isOwner: Boolean,
    canUpdateAndBill: Boolean,
    userEmail: String
) {
    val palette = LocalNoorColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val today = Formatters.todayIso()

    var selectedFilter by remember { mutableStateOf("In Shop (With Me)") }
    var query by remember { mutableStateOf("") }

    val filters = listOf(
        "In Shop (With Me)",
        "Service in Progress",
        "Ready for Delivery",
        "Payment Pending",
        "Waiting for Part"
    )

    val phonesInShop = repairs.count { it.status != "Delivered" }
    val waitingPickup = repairs.count { it.status == "Ready for Pickup" }
    val pendingCollect = repairs.filter { it.balanceDue > 0.0 }.sumOf { it.balanceDue }
    val todaysDeliveries = repairs.count { it.status == "Delivered" && (it.deliveredDate == today || it.deliveryDate == today) }

    val filtered = remember(repairs, selectedFilter, query) {
        val q = query.trim().lowercase()
        repairs.filter { r ->
            val matchChip = when (selectedFilter) {
                "In Shop (With Me)" -> r.status != "Delivered"
                "Service in Progress" -> r.status == "In-Progress"
                "Ready for Delivery" -> r.status == "Ready for Pickup"
                "Payment Pending" -> r.balanceDue > 0.0
                "Waiting for Part" -> r.status != "Delivered" && (
                    r.notes.contains("wait", true) ||
                        r.notes.contains("part", true) ||
                        (r.partSource == "outside" && r.partBuyingCost <= 0.0)
                    )
                else -> true
            }
            val matchQuery = q.isEmpty() || listOf(
                r.customerName, r.customerPhone, r.phoneModel, r.jobNumber, r.problem
            ).any { it.lowercase().contains(q) }
            matchChip && matchQuery
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        NoorSectionTitle(
            title = "Service Status",
            subtitle = "Live phone tracking · Ready WhatsApp alerts, part source, payment collection & 3-day overdue highlights.",
            trailing = {
                TextButton(onClick = { vm.navigateTo(NavSection.REPAIRS) }) {
                    Text("+ Service", color = palette.accent, fontWeight = FontWeight.Bold)
                }
            }
        )

        // 4 Counters
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ServiceCounterTile("Phones in Shop", phonesInShop.toString(), palette.accent, Modifier.weight(1f))
            ServiceCounterTile("Waiting for pickup", waitingPickup.toString(), palette.statusGreen, Modifier.weight(1f))
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ServiceCounterTile("Pending to Collect", Formatters.formatInr(pendingCollect), palette.statusAmber, Modifier.weight(1f))
            ServiceCounterTile("Today's Deliveries", todaysDeliveries.toString(), palette.textPrimary, Modifier.weight(1f))
        }

        // Filter Chips
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            filters.forEach { f ->
                val sel = selectedFilter == f
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (sel) palette.accent else palette.inputBg)
                        .border(1.dp, if (sel) palette.accent else palette.border, RoundedCornerShape(12.dp))
                        .clickable { selectedFilter = f }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(f, color = if (sel) androidx.compose.ui.graphics.Color.White else palette.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search by customer name, phone number, phone model, ref # or problem...") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, tint = palette.accent) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        )

        if (filtered.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().noorCard(palette).padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("No phones in this category.", color = palette.textSecondary, fontSize = 13.5.sp)
            }
        } else {
            filtered.forEach { r ->
                val ageDays = Formatters.daysBetweenIso(r.date, today)
                val isOverdue = r.status != "Delivered" && ageDays >= 3
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .noorCard(palette, radius = 16.dp, highlighted = isOverdue)
                        .border(
                            width = if (isOverdue) 1.dp else 0.dp,
                            color = if (isOverdue) palette.statusAmber else androidx.compose.ui.graphics.Color.Transparent,
                            shape = RoundedCornerShape(16.dp)
                        )
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(r.jobNumber, color = palette.accent, fontWeight = FontWeight.Black, fontSize = 14.sp)
                            if (isOverdue) {
                                Text(
                                    "${ageDays}d overdue",
                                    color = palette.statusAmber,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            }
                        }
                        Text(
                            when (r.partSource) {
                                "stock" -> "From Stock"
                                "outside" -> "Outside Part"
                                else -> "Labour Only"
                            },
                            color = palette.textMuted,
                            fontSize = 11.5.sp
                        )
                    }

                    Text("${r.phoneModel} · ${r.problem}", color = palette.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.5.sp)
                    Text(
                        "${r.customerName.ifBlank { "Walk-in" }} ${if (r.customerPhone.isNotBlank()) "· ${r.customerPhone}" else ""} · Received ${Formatters.formatDisplayDate(r.date)}",
                        color = palette.textSecondary,
                        fontSize = 12.sp
                    )

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Total ${Formatters.formatInr(r.totalCharged)} · Bal ${Formatters.formatInr(r.balanceDue)}",
                            color = if (r.balanceDue > 0) palette.statusAmber else palette.statusGreen,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.5.sp
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (canUpdateAndBill && r.status != "Ready for Pickup" && r.status != "Delivered") {
                                TextButton(onClick = {
                                    scope.launch {
                                        vm.repo.saveRepair(r.copy(status = "Ready for Pickup"), r.billedInvoiceId != null, isOwner, userEmail)
                                    }
                                }) {
                                    Text("Mark Ready", color = palette.accent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                            Button(
                                onClick = {
                                    val msg = buildString {
                                        append("Hello *${r.customerName.ifBlank { "Customer" }}*,\n")
                                        append("Your device *${r.phoneModel}* (Ref *${r.jobNumber}*) is *READY FOR PICKUP* at *${settings.shopName}*!\n")
                                        append("Total Charge: ${Formatters.formatInr(r.totalCharged)}\n")
                                        append("Advance Paid: ${Formatters.formatInr(r.totalPaid)}\n")
                                        append("Repair Balance: *${Formatters.formatInr(r.balanceDue)}*")
                                        if (settings.address.isNotBlank()) append("\nPickup Address: ${settings.address}")
                                    }
                                    WhatsAppHelper.openWhatsAppChat(context, r.customerPhone, msg)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = palette.statusGreen)
                            ) {
                                Text("Ready", color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(96.dp))
    }
}

@Composable
private fun ServiceCounterTile(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    val palette = LocalNoorColors.current
    Column(
        modifier = modifier
            .noorCard(palette, radius = 14.dp)
            .padding(12.dp)
    ) {
        Text(label, color = palette.textSecondary, fontSize = 11.5.sp)
        Spacer(Modifier.height(4.dp))
        Text(value, color = valueColor, fontWeight = FontWeight.Black, fontSize = 18.sp)
    }
}
