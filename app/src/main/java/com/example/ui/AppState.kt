package com.example.ui

import com.example.data.model.InvoiceLineItem
import com.example.data.model.PurchaseLineItem
import com.example.util.Formatters
import java.util.UUID

data class SaleDraftState(
    val editingInvoiceId: String? = null,
    val docType: String = "invoice", // invoice | estimate | order | challan | return
    val billType: String = "product", // product | repair
    val date: String = Formatters.todayIso(),
    val customerId: String? = null,
    val customerName: String = "",
    val customerPhone: String = "",
    val notes: String = "",
    val imei: String = "",
    val lines: List<InvoiceLineItem> = emptyList(),
    val discountType: String = "flat", // flat | percent
    val discountAmountText: String = "",
    val gstRate: Double = 0.0,
    val paymentMode: String = "Cash",
    val amountPaidText: String = "",
    val amountPaidTouched: Boolean = false,
    val cashTenderText: String = "",
    // v10 Part Source Switch (Labour Only | From My Stock | Bought Outside)
    val partSourceMode: String = "labour",
    val linkedPartItemId: String? = null,
    val outsideSupplierName: String = "",
    val outsidePartName: String = "",
    val outsidePartCategory: String = "Display",
    val outsidePartCostText: String = "",
    val outsidePartChargeText: String = "",
    val outsidePartPaymentMode: String = "Cash",
    val labourChargeText: String = ""
) {
    val outsideCost: Double get() = outsidePartCostText.toDoubleOrNull() ?: 0.0
    val outsideCharge: Double get() = outsidePartChargeText.toDoubleOrNull() ?: 0.0
    val labourCharge: Double get() = labourChargeText.toDoubleOrNull() ?: 0.0

    val subtotal: Double
        get() {
            val linesSum = lines.sumOf { it.total }
            val extraPart = if (partSourceMode == "outside") outsideCharge else 0.0
            val extraLab = if (billType == "repair" || partSourceMode != "labour") labourCharge else 0.0
            return linesSum + extraPart + extraLab
        }

    val discountValue: Double
        get() {
            val raw = discountAmountText.toDoubleOrNull() ?: 0.0
            return if (discountType == "percent") (subtotal * raw / 100.0).coerceAtMost(subtotal)
            else raw.coerceAtMost(subtotal)
        }

    val taxableAmount: Double get() = (subtotal - discountValue).coerceAtLeast(0.0)
    val gstAmount: Double get() = taxableAmount * gstRate / 100.0
    val grandTotal: Double get() = taxableAmount + gstAmount

    val effectivePaid: Double
        get() = when {
            paymentMode.startsWith("Credit", true) -> 0.0
            amountPaidTouched -> (amountPaidText.toDoubleOrNull() ?: 0.0).coerceIn(0.0, grandTotal)
            else -> grandTotal
        }

    val balanceDue: Double get() = (grandTotal - effectivePaid).coerceAtLeast(0.0)
    val cashTender: Double get() = cashTenderText.toDoubleOrNull() ?: 0.0
    val cashChange: Double get() = (cashTender - effectivePaid).coerceAtLeast(0.0)

    val liveProfitOwnerOnly: Double
        get() {
            val itemsCost = lines.sumOf { it.costPrice * it.qty }
            val partCost = if (partSourceMode == "outside") outsideCost else 0.0
            return taxableAmount - itemsCost - partCost
        }
}

data class RepairDraftState(
    val editingRepairId: String? = null,
    val customerName: String = "",
    val customerPhone: String = "",
    val phoneModel: String = "",
    val imei: String = "",
    val problem: String = "",
    val status: String = "Received",
    val date: String = Formatters.todayIso(),
    val deliveryDate: String = Formatters.todayIso(),
    val notes: String = "",
    val partSource: String = "labour", // labour | stock | outside
    val stockPartItemId: String? = null,
    val outsideSupplierName: String = "",
    val outsidePartName: String = "",
    val outsidePartCategory: String = "Display",
    val outsidePartPaymentMode: String = "Cash",
    val partBuyingCostText: String = "",
    val partChargeText: String = "",
    val serviceChargeText: String = "",
    val advancePaidText: String = "",
    val billingPaymentMode: String = "Cash",
    val createBillNow: Boolean = false
) {
    val partBuyCost: Double get() = partBuyingCostText.toDoubleOrNull() ?: 0.0
    val partCharge: Double get() = partChargeText.toDoubleOrNull() ?: 0.0
    val serviceCharge: Double get() = serviceChargeText.toDoubleOrNull() ?: 0.0
    val advancePaid: Double get() = advancePaidText.toDoubleOrNull() ?: 0.0
    val totalCharged: Double get() = partCharge + serviceCharge
    val customerBalance: Double get() = (totalCharged - advancePaid).coerceAtLeast(0.0)
    val profitOwnerOnly: Double get() = totalCharged - partBuyCost
}

data class PurchaseDraftState(
    val editingPurchaseId: String? = null,
    val docType: String = "purchase", // purchase | order | return
    val supplierName: String = "",
    val supplierPhone: String = "",
    val date: String = Formatters.todayIso(),
    val supplierBillNo: String = "",
    val notes: String = "",
    val rows: List<PurchaseLineItem> = List(3) { PurchaseLineItem(id = UUID.randomUUID().toString()) },
    val discountText: String = "",
    val transportText: String = "",
    val paymentMode: String = "Cash",
    val amountPaidText: String = "",
    val amountPaidTouched: Boolean = false,
    val generateBarcodes: Boolean = false
) {
    val subtotal: Double get() = rows.filter { it.model.isNotBlank() }.sumOf { it.total }
    val discount: Double get() = discountText.toDoubleOrNull() ?: 0.0
    val transport: Double get() = transportText.toDoubleOrNull() ?: 0.0
    val billTotal: Double get() = (subtotal - discount + transport).coerceAtLeast(0.0)
    val effectivePaid: Double
        get() = when {
            paymentMode.startsWith("Credit", true) -> 0.0
            amountPaidTouched -> (amountPaidText.toDoubleOrNull() ?: 0.0).coerceIn(0.0, billTotal)
            else -> billTotal
        }
    val balanceDue: Double get() = (billTotal - effectivePaid).coerceAtLeast(0.0)
}

data class AiChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val isUser: Boolean,
    val text: String,
    val choices: List<AiChoiceOption> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)

data class AiChoiceOption(
    val label: String,
    val promptToSend: String
)

data class AiActionRecord(
    val id: String = UUID.randomUUID().toString(),
    val description: String,
    val timestamp: Long = System.currentTimeMillis(),
    val undoBlock: (suspend () -> Unit)? = null
)

data class PendingAiConfirmation(
    val title: String,
    val description: String,
    val onConfirm: suspend () -> String
)
