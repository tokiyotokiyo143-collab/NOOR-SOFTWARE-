package com.example.ui.screens

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.Invoice
import com.example.data.model.ShopSettings
import com.example.ui.components.NoorPrimaryButton
import com.example.ui.components.NoorSecondaryButton
import com.example.ui.theme.LocalNoorColors
import com.example.ui.theme.noorCard
import com.example.util.Formatters
import com.example.util.PdfAndPrintHelper
import com.example.util.WhatsAppHelper
import com.example.util.rememberCachedDataUrlBitmap
import com.example.util.rememberCachedQrBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun InvoicePrintPreviewDialog(
    invoice: Invoice,
    settings: ShopSettings,
    onDismiss: () -> Unit
) {
    val palette = LocalNoorColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var paperSize by remember { mutableStateOf("A4") } // A4 | 80mm | 58mm
    var copyLabel by remember { mutableStateOf("Customer Copy") }

    val logoBmp = rememberCachedDataUrlBitmap(settings.logoDataUrl)
    val uploadedQr = rememberCachedDataUrlBitmap(settings.upiQrDataUrl)
    val upiAmt = if (invoice.balanceDue > 0) invoice.balanceDue else invoice.grandTotal
    val qrUri = if (settings.upiQrDataUrl.isBlank() && settings.upiId.isNotBlank()) {
        "upi://pay?pa=${settings.upiId}&pn=${settings.shopName}&am=$upiAmt&cu=INR"
    } else ""
    val generatedQr = rememberCachedQrBitmap(qrUri, 180)
    val qrBmp = uploadedQr ?: generatedQr

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .noorCard(palette, radius = 20.dp, highlighted = true)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Receipt Preview", color = palette.textPrimary, fontWeight = FontWeight.Black, fontSize = 18.sp)
                IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, contentDescription = "Close") }
            }

            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("A4" to "A4 / A5 Sheet", "80mm" to "80mm Thermal", "58mm" to "58mm Thermal").forEach { (k, lbl) ->
                    FilterChip(selected = paperSize == k, onClick = { paperSize = k }, label = { Text(lbl, fontSize = 11.5.sp) })
                }
            }
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Customer Copy", "Original Receipt", "Shop Copy").forEach { lbl ->
                    FilterChip(selected = copyLabel == lbl, onClick = { copyLabel = lbl }, label = { Text(lbl, fontSize = 11.5.sp) })
                }
            }

            // Crisp white receipt preview (matching web printable white receipt)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (logoBmp != null) {
                        Image(bitmap = logoBmp, contentDescription = "Shop Logo", modifier = Modifier.size(40.dp))
                    }
                    Column {
                        Text(settings.shopName, color = Color.Black, fontWeight = FontWeight.Black, fontSize = 15.sp)
                        if (settings.address.isNotBlank()) {
                            Text(
                                if (settings.address.startsWith("http", true)) "Find us on Google Maps" else settings.address,
                                color = Color(0xFF2563EB),
                                fontSize = 11.sp,
                                modifier = Modifier.clickable { WhatsAppHelper.openMapsLink(context, settings.address) }
                            )
                        }
                        if (settings.phone.isNotBlank()) Text("Ph: ${settings.phone}", color = Color.DarkGray, fontSize = 11.sp)
                        val ig = Formatters.extractInstagramHandle(settings.instagram)
                        if (ig.isNotBlank()) Text("Instagram: @$ig", color = Color.DarkGray, fontSize = 11.sp)
                        if (settings.gstNumber.isNotBlank()) Text("GSTIN: ${settings.gstNumber}", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
                HorizontalDivider(color = Color.LightGray)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(copyLabel, color = Color.DarkGray, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                    Text("${invoice.invoiceNumber} · ${Formatters.formatDisplayDate(invoice.date)}", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Text("Billed To: ${invoice.customerName.ifBlank { "Walk-in Customer" }} ${if (invoice.customerPhone.isNotBlank()) "(${invoice.customerPhone})" else ""}", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text("Payment Mode: ${invoice.paymentMode}", color = Color.DarkGray, fontSize = 11.sp)

                HorizontalDivider(color = Color.LightGray)
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text("Item / Service", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.weight(2f))
                    Text("Qty", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.weight(0.5f))
                    Text("Rate", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.weight(0.8f))
                    Text("Amount", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.weight(0.9f))
                }
                invoice.items.forEach { l ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(l.name, color = Color.DarkGray, fontSize = 11.sp, modifier = Modifier.weight(2f))
                        Text("${l.qty}", color = Color.DarkGray, fontSize = 11.sp, modifier = Modifier.weight(0.5f))
                        Text(Formatters.formatInr(l.rate), color = Color.DarkGray, fontSize = 11.sp, modifier = Modifier.weight(0.8f))
                        Text(Formatters.formatInr(l.total), color = Color.Black, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, modifier = Modifier.weight(0.9f))
                    }
                }
                if (invoice.partSourceMode != "labour" && invoice.outsidePartCharge > 0 && invoice.items.none { it.name == invoice.outsidePartName }) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(invoice.outsidePartName.ifBlank { "Spare Part" }, color = Color.DarkGray, fontSize = 11.sp, modifier = Modifier.weight(2.5f))
                        Text(Formatters.formatInr(invoice.outsidePartCharge), color = Color.Black, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, modifier = Modifier.weight(1.7f))
                    }
                }
                if (invoice.notes.isNotBlank()) {
                    Text("Notes: ${invoice.notes}", color = Color.Gray, fontSize = 10.5.sp)
                }
                HorizontalDivider(color = Color.LightGray)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Subtotal", color = Color.DarkGray, fontSize = 11.5.sp)
                    Text(Formatters.formatInr(invoice.subtotal), color = Color.Black, fontSize = 11.5.sp)
                }
                if (invoice.discountAmount > 0) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Discount", color = Color.DarkGray, fontSize = 11.5.sp)
                        Text(Formatters.formatInr(invoice.discountAmount), color = Color.Black, fontSize = 11.5.sp)
                    }
                }
                if (invoice.gstRate > 0) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("GST (${invoice.gstRate.toInt()}%)", color = Color.DarkGray, fontSize = 11.5.sp)
                        val taxable = (invoice.subtotal - invoice.discountAmount).coerceAtLeast(0.0)
                        Text(Formatters.formatInr(taxable * invoice.gstRate / 100.0), color = Color.Black, fontSize = 11.5.sp)
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Grand Total", color = Color.Black, fontWeight = FontWeight.Black, fontSize = 13.5.sp)
                    Text(Formatters.formatInr(invoice.grandTotal), color = Color.Black, fontWeight = FontWeight.Black, fontSize = 13.5.sp)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Paid", color = Color.DarkGray, fontSize = 11.5.sp)
                    Text(Formatters.formatInr(invoice.amountPaid), color = Color.Black, fontSize = 11.5.sp)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Balance Due", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                    Text(Formatters.formatInr(invoice.balanceDue), color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 6.dp)) {
                    if (qrBmp != null) {
                        Image(bitmap = qrBmp, contentDescription = "UPI QR", modifier = Modifier.size(64.dp))
                        Column {
                            Text("Scan & Pay via UPI", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            if (settings.upiId.isNotBlank()) Text(settings.upiId, color = Color.DarkGray, fontSize = 10.sp)
                        }
                    } else {
                        Text("QR not set · Upload QR in Settings", color = Color.Gray, fontSize = 10.5.sp)
                    }
                }
                if (settings.invoiceTerms.isNotBlank()) {
                    Text(settings.invoiceTerms, color = Color.Gray, fontSize = 10.sp)
                }
            }

            Text(
                "Verify shop branding and customer totals before printing or saving as PDF.",
                color = palette.textMuted,
                fontSize = 11.5.sp
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                NoorSecondaryButton(
                    text = "Print / PDF",
                    icon = Icons.Outlined.Print,
                    onClick = {
                        scope.launch {
                            val file = withContext(Dispatchers.IO) {
                                PdfAndPrintHelper.generateInvoicePdf(context, invoice, settings, paperSize, copyLabel)
                            }
                            if (file != null) PdfAndPrintHelper.printPdfFile(context, file, "Bill ${invoice.invoiceNumber}")
                            else Toast.makeText(context, "Could not generate PDF bill.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
                NoorPrimaryButton(
                    text = "Share PDF / WhatsApp",
                    icon = Icons.Outlined.Share,
                    onClick = {
                        scope.launch {
                            val file = withContext(Dispatchers.IO) {
                                PdfAndPrintHelper.generateInvoicePdf(context, invoice, settings, paperSize, copyLabel)
                            }
                            if (file != null) PdfAndPrintHelper.sharePdfFile(context, file, "Bill ${invoice.invoiceNumber}")
                            else Toast.makeText(context, "Could not generate PDF bill.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.weight(1.3f)
                )
            }
        }
    }
}

@Composable
fun PrintSalesHistoryDialog(
    invoices: List<Invoice>,
    settings: ShopSettings,
    onDismiss: () -> Unit
) {
    val palette = LocalNoorColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var period by remember { mutableStateOf("This Month") }
    var fromDate by remember { mutableStateOf(Formatters.todayIso()) }
    var toDate by remember { mutableStateOf(Formatters.todayIso()) }
    var includeReturnedCancelled by remember { mutableStateOf(false) }
    var formatChoice by remember { mutableStateOf("A4 / A5 Receipts") }

    val matching = remember(invoices, period, fromDate, toDate, includeReturnedCancelled) {
        invoices.filter { inv ->
            val dateOk = if (period == "All Dates") true else Formatters.isDateInPeriod(inv.date, period, fromDate, toDate)
            val statusOk = includeReturnedCancelled || (inv.status == "active" && inv.docType != "return")
            dateOk && statusOk
        }
    }

    val activePosting = matching.filter { it.status == "active" && it.docType == "invoice" }
    val netSales = activePosting.sumOf { it.grandTotal }
    val collected = activePosting.sumOf { it.amountPaid }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .noorCard(palette, radius = 20.dp, highlighted = true)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Print Sales History & Batch Receipts", color = palette.textPrimary, fontWeight = FontWeight.Black, fontSize = 17.sp)

            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Today", "This Week", "This Month", "All Dates", "Custom").forEach { p ->
                    FilterChip(selected = period == p, onClick = { period = p }, label = { Text(p, fontSize = 11.sp) })
                }
            }
            if (period == "Custom") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = fromDate, onValueChange = { fromDate = it }, label = { Text("From") }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(value = toDate, onValueChange = { toDate = it }, label = { Text("to") }, modifier = Modifier.weight(1f), singleLine = true)
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = includeReturnedCancelled, onCheckedChange = { includeReturnedCancelled = it })
                Text("Include Returned/Cancelled", color = palette.textPrimary, fontSize = 13.sp)
            }

            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("A4 / A5 Receipts", "80mm Thermal Roll", "58mm Thermal Roll").forEach { f ->
                    FilterChip(selected = formatChoice == f, onClick = { formatChoice = f }, label = { Text(f, fontSize = 11.sp) })
                }
            }

            Column(modifier = Modifier.fillMaxWidth().noorCard(palette).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Sales History Statement", color = palette.accent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("Active Bills: ${activePosting.size} · Net Sales Total: ${Formatters.formatInr(netSales)} · Collected: ${Formatters.formatInr(collected)}", color = palette.textPrimary, fontSize = 12.sp)
                val byMode = activePosting.groupBy { it.paymentMode }.mapValues { (_, list) -> list.sumOf { it.amountPaid } }
                Text("By Payment Mode: " + byMode.entries.joinToString(" | ") { "${it.key}: ${Formatters.formatInr(it.value)}" }, color = palette.textSecondary, fontSize = 11.5.sp)
            }

            if (matching.isEmpty()) {
                Text(
                    "Select a different date range or toggle “Include Returned/Cancelled” above.",
                    color = palette.textSecondary,
                    fontSize = 12.5.sp
                )
            } else {
                Text("Totals (Active Posting Bills): ${Formatters.formatInr(netSales)}", color = palette.textPrimary, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NoorSecondaryButton(text = "Close", onClick = onDismiss, modifier = Modifier.weight(1f))
                NoorPrimaryButton(
                    text = "Print Statement PDF",
                    enabled = matching.isNotEmpty(),
                    onClick = {
                        val lines = buildList {
                            add("Date | Bill # | Customer | Mode | Total | Paid | Due")
                            add("-".repeat(76))
                            matching.forEach { inv ->
                                add("${inv.date} | ${inv.invoiceNumber} | ${inv.customerName.ifBlank { "Walk-in" }.take(16)} | ${inv.paymentMode} | ${Formatters.formatInr(inv.grandTotal)} | ${Formatters.formatInr(inv.amountPaid)} | ${Formatters.formatInr(inv.balanceDue)}")
                            }
                            add("-".repeat(76))
                            add("Totals (Active Posting Bills): ${activePosting.size} bills | Net: ${Formatters.formatInr(netSales)} | Collected: ${Formatters.formatInr(collected)}")
                        }
                        scope.launch {
                            val pdf = withContext(Dispatchers.IO) {
                                PdfAndPrintHelper.generateTextReportPdf(context, "Sales History Statement", "Period: $period · Format: $formatChoice", lines, settings)
                            }
                            if (pdf != null) PdfAndPrintHelper.printPdfFile(context, pdf, "Sales History Statement")
                        }
                    },
                    modifier = Modifier.weight(1.4f)
                )
            }
        }
    }
}
