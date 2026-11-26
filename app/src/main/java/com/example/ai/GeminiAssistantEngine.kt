package com.example.ai

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import com.example.data.model.*
import com.example.ui.*
import com.example.util.Formatters
import com.example.util.PdfAndPrintHelper
import com.example.util.WhatsAppHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.concurrent.TimeUnit

class GeminiAssistantEngine(
    private val context: Context,
    private val vm: MainViewModel
) {
    companion object {
        private val warmClient = OkHttpClient.Builder()
            .connectionPool(ConnectionPool(4, 5, TimeUnit.MINUTES))
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    suspend fun handleTurn(
        textPrompt: String? = null,
        wavAudioBytes: ByteArray? = null,
        screenBitmap: Bitmap? = null
    ): AiChatMessage {
        val aiSettings = vm.aiSettings.value
        val apiKey = vm.secretStore.getGeminiApiKey().trim()
        val isOnline = vm.repo.syncStatus.value.isOnline

        if (!aiSettings.enabled) {
            return AiChatMessage(isUser = false, text = "AI Assistant is turned off.")
        }
        if (!isOnline || apiKey.isBlank()) {
            return AiChatMessage(isUser = false, text = "AI needs internet and your Gemini API key")
        }

        val user = vm.currentUser
        val isOwner = user?.isOwnerOrCoOwner == true

        // Build compact screen + catalog snapshot
        val snapshotJson = buildCompactSnapshot(isOwner)
        val systemText = buildSystemPrompt(aiSettings.replyLanguage, isOwner, snapshotJson)

        val parts = JSONArray()
        if (wavAudioBytes != null) {
            parts.put(JSONObject().apply {
                put("inlineData", JSONObject().apply {
                    put("mimeType", "audio/wav")
                    put("data", Base64.encodeToString(wavAudioBytes, Base64.NO_WRAP))
                })
            })
            parts.put(JSONObject().put("text", "Listen to the user's Tamil/Tanglish/English audio command and execute the matching app tool(s) right away. Reply in 1 short line."))
        }
        if (!textPrompt.isNullOrBlank()) {
            parts.put(JSONObject().put("text", textPrompt))
        }
        if (screenBitmap != null && aiSettings.allowScreenShare) {
            val out = ByteArrayOutputStream()
            screenBitmap.compress(Bitmap.CompressFormat.JPEG, 60, out)
            parts.put(JSONObject().apply {
                put("inlineData", JSONObject().apply {
                    put("mimeType", "image/jpeg")
                    put("data", Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP))
                })
            })
        }

        val payload = JSONObject().apply {
            put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemText))))
            put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", parts)))
            put("tools", JSONArray().put(JSONObject().put("functionDeclarations", buildToolDeclarations())))
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.1)
                put("maxOutputTokens", 350)
            })
        }

        val responseJson = callGemini(aiSettings.model, apiKey, payload)
            ?: return AiChatMessage(isUser = false, text = "AI needs internet and your Gemini API key")

        val errorObj = responseJson.optJSONObject("error")
        if (errorObj != null) {
            return AiChatMessage(isUser = false, text = "Gemini error: ${errorObj.optString("message", "Request failed")}")
        }

        val candidateParts = responseJson
            .optJSONArray("candidates")
            ?.optJSONObject(0)
            ?.optJSONObject("content")
            ?.optJSONArray("parts")
            ?: return AiChatMessage(isUser = false, text = "Puriyala. Konjam clear-a sollunga.")

        val toolCalls = mutableListOf<Pair<String, JSONObject>>()
        val modelTextParts = mutableListOf<String>()

        for (i in 0 until candidateParts.length()) {
            val part = candidateParts.optJSONObject(i) ?: continue
            part.optJSONObject("functionCall")?.let { fc ->
                toolCalls.add(fc.optString("name") to (fc.optJSONObject("args") ?: JSONObject()))
            }
            val txt = part.optString("text")
            if (txt.isNotBlank()) modelTextParts.add(txt.trim())
        }

        if (toolCalls.isEmpty()) {
            val reply = modelTextParts.joinToString(" ").ifBlank {
                if (aiSettings.replyLanguage == "ta") "Sari, enna pannanum nu sollunga?" else "How can I help?"
            }
            return AiChatMessage(isUser = false, text = reply)
        }

        val results = mutableListOf<String>()
        val collectedChoices = mutableListOf<AiChoiceOption>()

        for ((name, args) in toolCalls) {
            val (msg, choices) = executeTool(name, args, isOwner, user, aiSettings)
            if (msg.isNotBlank()) results.add(msg)
            collectedChoices.addAll(choices)
        }

        val finalReply = if (modelTextParts.isNotEmpty() && results.isEmpty()) {
            modelTextParts.joinToString(" ")
        } else {
            results.joinToString(" · ").ifBlank { modelTextParts.joinToString(" ") }
        }

        return AiChatMessage(
            isUser = false,
            text = finalReply,
            choices = collectedChoices.take(3)
        )
    }

    private suspend fun callGemini(model: String, apiKey: String, body: JSONObject): JSONObject? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()
            warmClient.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (raw.isBlank()) null else JSONObject(raw)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun buildCompactSnapshot(isOwner: Boolean): String {
        val sec = vm.currentSection.value
        val sale = vm.saleDraft.value
        val rep = vm.repairDraft.value
        val pur = vm.purchaseDraft.value
        val topItems = vm.repo.items.value.take(35).map {
            "${it.displayName} (${it.category}, qty=${it.pieces}, ₹${it.sellingPrice.toInt()})"
        }
        val topParties = vm.repo.parties.value.take(25).map { it.name }

        return JSONObject().apply {
            put("screen", sec.key)
            put("isOwner", isOwner)
            put("saleDraft", JSONObject().apply {
                put("billType", sale.billType)
                put("customer", sale.customerName)
                put("linesCount", sale.lines.size)
                put("partSource", sale.partSourceMode)
                put("total", sale.grandTotal)
                put("mode", sale.paymentMode)
            })
            put("repairDraft", JSONObject().apply {
                put("customer", rep.customerName)
                put("model", rep.phoneModel)
                put("status", rep.status)
            })
            put("purchaseDraft", JSONObject().apply {
                put("supplier", pur.supplierName)
                put("rows", pur.rows.count { it.model.isNotBlank() })
            })
            put("stockSample", JSONArray(topItems))
            put("partyNames", JSONArray(topParties))
        }.toString()
    }

    private fun buildSystemPrompt(replyLanguage: String, isOwner: Boolean, snapshotJson: String): String {
        val langRule = if (replyLanguage == "ta") {
            "Reply in natural short 1-line Tamil/Tanglish by default (e.g. 'Sari, Arun-ku sale open pannitten. Enna item add pannanum?'), unless user clearly spoke/typed pure English."
        } else {
            "Reply in natural short 1-line English."
        }
        val privRule = if (isOwner) "User is Owner." else "User is Staff: NEVER mention cost prices, COGS, or profit."
        return """
You are the in-app AI Assistant for Noor Software (AN-NOOR Mobile Service Point).
Understand Tamil, Tanglish, and English commands. Execute actions immediately via function calls.
$langRule
$privRule
Never invent stock items or customers. Use snapshot + search tools. Keep replies <= 1-2 short lines for counter speed.
Current screen snapshot: $snapshotJson
        """.trimIndent()
    }

    private suspend fun executeTool(
        name: String,
        args: JSONObject,
        isOwner: Boolean,
        user: ShopUser?,
        aiSettings: AiSettings
    ): Pair<String, List<AiChoiceOption>> {
        fun denied(permName: String): Pair<String, List<AiChoiceOption>> =
            "Permission denied ($permName)." to emptyList()

        return when (name) {
            "open_screen" -> {
                val key = args.optString("section").lowercase()
                val target = NavSection.entries.firstOrNull {
                    it.key.equals(key, true) || it.labelEn.equals(key, true)
                } ?: NavSection.HOME
                if (!vm.hasSectionPermission(target)) return denied(target.labelEn)
                val prev = vm.currentSection.value
                withContext(Dispatchers.Main) { vm.navigateTo(target) }
                vm.recordAiAction("Opened ${target.labelEn}") { withContext(Dispatchers.Main) { vm.navigateTo(prev) } }
                "${target.labelEn} open pannitten." to emptyList()
            }

            "open_new_sale" -> {
                if (user?.hasPermission { it.salesCreate } != true) return denied("Sales: Create Bills")
                val billType = args.optString("billType", "product").ifBlank { "product" }
                val prevDraft = vm.saleDraft.value
                withContext(Dispatchers.Main) {
                    vm.resetSaleDraft(billType)
                    vm.navigateTo(NavSection.BILLING)
                }
                vm.recordAiAction("Opened new sale") { withContext(Dispatchers.Main) { vm.updateSaleDraft { prevDraft } } }
                "Puthu sale bill open pannitten." to emptyList()
            }

            "set_customer" -> {
                if (user?.hasPermission { it.salesCreate } != true) return denied("Sales: Create Bills")
                val cName = args.optString("name").trim()
                val cPhone = args.optString("phone").trim()
                val prevDraft = vm.saleDraft.value
                val matchedParty = vm.repo.parties.value.firstOrNull { it.name.equals(cName, true) }
                withContext(Dispatchers.Main) {
                    vm.updateSaleDraft {
                        it.copy(
                            customerId = matchedParty?.id ?: it.customerId,
                            customerName = cName.ifBlank { it.customerName },
                            customerPhone = cPhone.ifBlank { matchedParty?.phone ?: it.customerPhone }
                        )
                    }
                    if (vm.currentSection.value != NavSection.BILLING) vm.navigateTo(NavSection.BILLING)
                }
                vm.recordAiAction("Set customer $cName") { withContext(Dispatchers.Main) { vm.updateSaleDraft { prevDraft } } }
                "Customer $cName set pannitten." to emptyList()
            }

            "add_item_to_bill" -> {
                if (user?.hasPermission { it.salesCreate } != true) return denied("Sales: Create Bills")
                val q = args.optString("query").trim()
                val qty = args.optInt("qty", 1).coerceAtLeast(1)
                val customRate = args.optDouble("rate", -1.0)
                val matches = fuzzyFindStock(q)
                if (matches.isEmpty()) {
                    return "\"$q\" stock-la kidaikala." to emptyList()
                }
                if (matches.size > 1 && !matches.first().displayName.equals(q, true)) {
                    val choices = matches.take(3).map { item ->
                        AiChoiceOption(
                            label = "${item.displayName} (${item.color.ifBlank { item.category }}) ₹${item.sellingPrice.toInt()}",
                            promptToSend = "Add exact stock item '${item.displayName} ${item.color}' qty $qty to bill"
                        )
                    }
                    return "Ethu add pannanum?" to choices
                }
                val item = matches.first()
                val prevDraft = vm.saleDraft.value
                val rate = if (customRate > 0) customRate else item.sellingPrice
                withContext(Dispatchers.Main) {
                    vm.updateSaleDraft { d ->
                        val lines = d.lines.toMutableList()
                        val idx = lines.indexOfFirst { it.itemId == item.id }
                        if (idx >= 0) {
                            lines[idx] = lines[idx].copy(qty = lines[idx].qty + qty, rate = rate)
                        } else {
                            lines.add(
                                InvoiceLineItem(
                                    itemId = item.id,
                                    name = item.displayName,
                                    category = item.category,
                                    brand = item.brand,
                                    model = item.model,
                                    color = item.color,
                                    qty = qty,
                                    rate = rate,
                                    costPrice = if (isOwner) item.costPrice else 0.0,
                                    isService = item.isService,
                                    partSource = if (item.isService) "labour" else "stock"
                                )
                            )
                        }
                        d.copy(lines = lines)
                    }
                    if (vm.currentSection.value != NavSection.BILLING) vm.navigateTo(NavSection.BILLING)
                }
                vm.recordAiAction("Added ${item.displayName} x$qty") { withContext(Dispatchers.Main) { vm.updateSaleDraft { prevDraft } } }
                "${item.displayName} × $qty add pannitten." to emptyList()
            }

            "set_discount" -> {
                val type = args.optString("type", "flat").lowercase()
                val value = args.optDouble("value", 0.0)
                withContext(Dispatchers.Main) {
                    vm.updateSaleDraft {
                        it.copy(
                            discountType = if (type.startsWith("per")) "percent" else "flat",
                            discountAmountText = Formatters.formatDecimal(value)
                        )
                    }
                }
                "Discount ${Formatters.formatDecimal(value)} set pannitten." to emptyList()
            }

            "set_gst" -> {
                val rate = args.optDouble("rate", 0.0)
                withContext(Dispatchers.Main) { vm.updateSaleDraft { it.copy(gstRate = rate) } }
                "GST ${rate.toInt()}% set pannitten." to emptyList()
            }

            "set_payment" -> {
                val mode = args.optString("mode", "Cash")
                val amt = args.optDouble("amountPaid", -1.0)
                withContext(Dispatchers.Main) {
                    vm.updateSaleDraft { d ->
                        d.copy(
                            paymentMode = mode,
                            amountPaidText = if (amt >= 0) Formatters.formatDecimal(amt) else d.amountPaidText,
                            amountPaidTouched = amt >= 0
                        )
                    }
                }
                val amtStr = if (amt >= 0) " ${Formatters.formatInr(amt)}" else ""
                "Payment $mode$amtStr set pannitten." to emptyList()
            }

            "set_part_source" -> {
                val opt = args.optString("option", "labour").lowercase()
                val query = args.optString("query")
                val supplier = args.optString("supplierName")
                val partName = args.optString("partName")
                val cat = args.optString("category", "Display")
                val buyCost = args.optDouble("buyingCost", 0.0)
                val partCharge = args.optDouble("partCharge", 0.0)
                val labour = args.optDouble("labourCharge", 0.0)
                val payMode = args.optString("paymentMode", "Cash")

                val stockId = if (opt == "stock" && query.isNotBlank()) fuzzyFindStock(query).firstOrNull()?.id else null
                withContext(Dispatchers.Main) {
                    vm.updateSaleDraft { d ->
                        d.copy(
                            partSourceMode = opt,
                            linkedPartItemId = stockId ?: d.linkedPartItemId,
                            outsideSupplierName = supplier.ifBlank { d.outsideSupplierName },
                            outsidePartName = partName.ifBlank { d.outsidePartName },
                            outsidePartCategory = cat.ifBlank { d.outsidePartCategory },
                            outsidePartCostText = if (buyCost > 0) Formatters.formatDecimal(buyCost) else d.outsidePartCostText,
                            outsidePartChargeText = if (partCharge > 0) Formatters.formatDecimal(partCharge) else d.outsidePartChargeText,
                            outsidePartPaymentMode = payMode,
                            labourChargeText = if (labour > 0) Formatters.formatDecimal(labour) else d.labourChargeText
                        )
                    }
                }
                "Part source $opt update pannitten." to emptyList()
            }

            "save_bill" -> {
                if (user?.hasPermission { it.salesCreate } != true) return denied("Sales: Create Bills")
                val d = vm.saleDraft.value
                if (d.lines.isEmpty() && d.outsideCharge <= 0 && d.labourCharge <= 0) {
                    return "Bill-la item illa. Enna item add pannanum?" to emptyList()
                }
                val saved = withContext(Dispatchers.Main) { vm.saveCurrentSaleDraft() }
                vm.recordAiAction("Saved bill ${saved.invoiceNumber}") {
                    vm.repo.deleteInvoice(saved.id, isOwner)
                }
                "Bill ${saved.invoiceNumber} save aachu (${Formatters.formatInr(saved.grandTotal)})." to emptyList()
            }

            "share_bill_whatsapp" -> {
                val latest = vm.repo.invoices.value.firstOrNull()
                    ?: return "Share panna invoice illa." to emptyList()
                val settings = vm.repo.settings.value
                withContext(Dispatchers.Main) {
                    val pdf = PdfAndPrintHelper.generateInvoicePdf(context, latest, settings)
                    if (pdf != null) PdfAndPrintHelper.sharePdfFile(context, pdf, "Bill ${latest.invoiceNumber}")
                }
                "Bill ${latest.invoiceNumber} WhatsApp share sheet open pannitten." to emptyList()
            }

            "open_new_service" -> {
                if (user?.hasPermission { it.serviceCreate } != true) return denied("Service: Create")
                val cName = args.optString("customerName")
                val cPhone = args.optString("customerPhone")
                val model = args.optString("phoneModel")
                val prob = args.optString("problem")
                val charge = args.optDouble("serviceCharge", 0.0)
                withContext(Dispatchers.Main) {
                    vm.resetRepairDraft()
                    vm.updateRepairDraft {
                        it.copy(
                            customerName = cName,
                            customerPhone = cPhone,
                            phoneModel = model,
                            problem = prob,
                            serviceChargeText = if (charge > 0) Formatters.formatDecimal(charge) else ""
                        )
                    }
                    vm.navigateTo(NavSection.REPAIRS)
                }
                "Service form open pannitten." to emptyList()
            }

            "update_service_status" -> {
                if (user?.hasPermission { it.serviceUpdateBill } != true) return denied("Service: Update")
                val jq = args.optString("jobQuery").trim()
                val st = args.optString("status", "Ready for Pickup")
                val note = args.optString("note")
                val match = vm.repo.repairs.value.firstOrNull {
                    it.jobNumber.equals(jq, true) ||
                        it.customerName.contains(jq, true) ||
                        it.phoneModel.contains(jq, true)
                } ?: return "\"$jq\" service job kidaikala." to emptyList()
                val oldStatus = match.status
                vm.repo.saveRepair(match.copy(status = st, notes = note.ifBlank { match.notes }), match.billedInvoiceId != null, isOwner, user.email)
                vm.recordAiAction("Updated ${match.jobNumber} -> $st") {
                    vm.repo.saveRepair(match.copy(status = oldStatus), match.billedInvoiceId != null, isOwner, user.email)
                }
                "${match.jobNumber} status $st aachu." to emptyList()
            }

            "open_new_purchase" -> {
                if (user?.hasPermission { it.purchasesCreate } != true) return denied("Purchases: Create")
                val sup = args.optString("supplierName")
                val phone = args.optString("supplierPhone")
                withContext(Dispatchers.Main) {
                    vm.resetPurchaseDraft()
                    vm.updatePurchaseDraft { it.copy(supplierName = sup, supplierPhone = phone) }
                    vm.navigateTo(NavSection.PURCHASES)
                }
                "Purchase bill open pannitten." to emptyList()
            }

            "add_purchase_line" -> {
                if (user?.hasPermission { it.purchasesCreate } != true) return denied("Purchases: Create")
                val line = PurchaseLineItem(
                    model = args.optString("model"),
                    qty = args.optInt("qty", 1).coerceAtLeast(1),
                    buyPrice = args.optDouble("buyPrice", 0.0),
                    sellPrice = args.optDouble("sellPrice", 0.0),
                    color = args.optString("color"),
                    category = args.optString("category"),
                    brand = args.optString("brand")
                )
                withContext(Dispatchers.Main) {
                    vm.updatePurchaseDraft { d -> d.copy(rows = d.rows.filter { it.model.isNotBlank() } + line) }
                    if (vm.currentSection.value != NavSection.PURCHASES) vm.navigateTo(NavSection.PURCHASES)
                }
                "${line.model} × ${line.qty} purchase grid-la add pannitten." to emptyList()
            }

            "add_stock" -> {
                if (user?.hasPermission { it.stockEditBulkRates } != true) return denied("Stock Edit")
                val item = StockItem(
                    brand = args.optString("brand"),
                    model = args.optString("model"),
                    category = args.optString("category", "Accessories"),
                    color = args.optString("color"),
                    sellingPrice = args.optDouble("sellingPrice", 0.0),
                    costPrice = if (isOwner) args.optDouble("costPrice", 0.0) else 0.0,
                    pieces = args.optInt("pieces", 1).coerceAtLeast(0),
                    lowStockAlert = vm.repo.settings.value.lowStockDefault
                )
                vm.repo.saveItem(item, isOwner)
                vm.recordAiAction("Added stock ${item.displayName}") { vm.repo.deleteItem(item.id) }
                "${item.displayName} (${item.pieces} pcs) stock-la save pannitten." to emptyList()
            }

            "adjust_stock" -> {
                if (user?.hasPermission { it.stockEditBulkRates } != true) return denied("Stock Edit")
                val q = args.optString("query")
                val mode = args.optString("mode", "add").lowercase()
                val qty = args.optInt("qty", 1).coerceAtLeast(0)
                val item = fuzzyFindStock(q).firstOrNull() ?: return "\"$q\" stock-la illa." to emptyList()
                val oldPieces = item.pieces
                vm.repo.adjustStock(item.id, mode, qty, isOwner)
                vm.recordAiAction("Adjusted ${item.displayName} stock") {
                    vm.repo.adjustStock(item.id, "set", oldPieces, isOwner)
                }
                "${item.displayName} stock update pannitten." to emptyList()
            }

            "add_expense" -> {
                if (user?.hasPermission { it.expenses } != true) return denied("Expenses")
                val exp = ExpenseRecord(
                    category = args.optString("category", "Expense"),
                    reason = args.optString("reason", "Shop Expense"),
                    amount = args.optDouble("amount", 0.0),
                    date = Formatters.todayIso(),
                    paymentMode = args.optString("mode", "Cash")
                )
                if (exp.amount <= 0) return "Expense amount sollunga." to emptyList()
                vm.repo.saveExpense(exp)
                vm.recordAiAction("Expense ${exp.reason} ₹${exp.amount.toInt()}") { vm.repo.deleteExpense(exp.id) }
                "Expense ${Formatters.formatInr(exp.amount)} (${exp.reason}) save pannitten." to emptyList()
            }

            "record_payment" -> {
                if (user?.hasPermission { it.partiesDues } != true) return denied("Parties & Dues")
                val pName = args.optString("partyName").trim()
                val dir = args.optString("direction", "in")
                val amt = args.optDouble("amount", 0.0)
                val mode = args.optString("mode", "Cash")
                val party = vm.repo.parties.value.firstOrNull { it.name.contains(pName, true) }
                    ?: vm.repo.ensureParty(pName, "", if (dir == "out") "supplier" else "customer")
                if (party == null || amt <= 0) return "Party name and amount sollunga." to emptyList()

                if (aiSettings.askConfirmationForDestructive && amt >= 5000.0) {
                    val pay = PaymentRecord(partyId = party.id, partyName = party.name, direction = dir, amount = amt, paymentMode = mode, date = Formatters.todayIso())
                    vm.setPendingAiConfirmation(
                        PendingAiConfirmation(
                            title = "Confirm Payment?",
                            description = "Record ${Formatters.formatInr(amt)} ($mode) for ${party.name}?"
                        ) {
                            vm.repo.savePayment(pay)
                            "Payment ${Formatters.formatInr(amt)} save pannitten."
                        }
                    )
                    return "${party.name}-ku ${Formatters.formatInr(amt)} payment record pannava? Confirm pannunga." to emptyList()
                }
                val pay = PaymentRecord(partyId = party.id, partyName = party.name, direction = dir, amount = amt, paymentMode = mode, date = Formatters.todayIso())
                vm.repo.savePayment(pay)
                vm.recordAiAction("Payment ${party.name} ${Formatters.formatInr(amt)}") { vm.repo.deletePayment(pay.id) }
                "${party.name} payment ${Formatters.formatInr(amt)} save pannitten." to emptyList()
            }

            "add_party" -> {
                if (user?.hasPermission { it.partiesDues } != true) return denied("Parties")
                val party = Party(
                    name = args.optString("name"),
                    phone = args.optString("phone"),
                    type = args.optString("type", "customer"),
                    address = args.optString("address")
                )
                if (party.name.isBlank()) return "Party name sollunga." to emptyList()
                vm.repo.saveParty(party)
                vm.recordAiAction("Added party ${party.name}") { vm.repo.deleteParty(party.id) }
                "Party ${party.name} save pannitten." to emptyList()
            }

            "search_stock" -> {
                val q = args.optString("query")
                val hits = fuzzyFindStock(q)
                if (hits.isEmpty()) "\"$q\" stock-la illa." to emptyList()
                else {
                    val top = hits.take(3).joinToString(" | ") { "${it.displayName} (${it.color}): ${it.pieces} pcs, ${Formatters.formatInr(it.sellingPrice)}" }
                    top to emptyList()
                }
            }

            "get_party_balance" -> {
                if (user?.hasPermission { it.partiesDues } != true) return denied("Parties & Dues")
                val pName = args.optString("partyName").trim()
                val matches = vm.repo.parties.value.filter { it.name.contains(pName, true) }
                if (matches.isEmpty()) return "\"$pName\" party kidaikala." to emptyList()
                if (matches.size > 1 && matches.none { it.name.equals(pName, true) }) {
                    val choices = matches.take(3).map { p ->
                        AiChoiceOption(p.name, "Check balance for exact party '${p.name}'")
                    }
                    return "Yaaroda balance paakanum?" to choices
                }
                val p = matches.firstOrNull { it.name.equals(pName, true) } ?: matches.first()
                val sum = vm.repo.computePartySummary(p)
                val msg = when {
                    sum.netBalance > 0 -> "${p.name} balance: ${Formatters.formatInr(sum.netBalance)} varanum (to collect)."
                    sum.netBalance < 0 -> "${p.name} balance: ${Formatters.formatInr(-sum.netBalance)} kodukkanum (to pay)."
                    else -> "${p.name} balance settled ₹0."
                }
                msg to emptyList()
            }

            "get_today_summary" -> {
                val today = Formatters.todayIso()
                val sales = vm.repo.invoices.value.filter { it.status == "active" && it.docType == "invoice" && it.date == today }
                val totalSales = sales.sumOf { it.grandTotal }
                val totalExp = vm.repo.expenses.value.filter { it.date == today }.sumOf { it.amount }
                val cash = vm.repo.computeCashInHand()
                val summary = if (isOwner) {
                    val cogs = sales.sumOf { inv -> inv.items.sumOf { it.costPrice * it.qty } }
                    "Innaiku sales ${Formatters.formatInr(totalSales)} (${sales.size} bills), expenses ${Formatters.formatInr(totalExp)}, cash ${Formatters.formatInr(cash)}, net profit ${Formatters.formatInr(totalSales - cogs - totalExp)}."
                } else {
                    "Innaiku sales ${Formatters.formatInr(totalSales)} (${sales.size} bills), expenses ${Formatters.formatInr(totalExp)}."
                }
                summary to emptyList()
            }

            "get_report" -> {
                val type = args.optString("type", "Sales")
                if (!isOwner && (type.contains("Profit", true) || type.contains("Price", true))) {
                    return denied("Owner-Only Profit Report")
                }
                withContext(Dispatchers.Main) { vm.openReportTab(type) }
                "$type report open pannitten." to emptyList()
            }

            "open_shop_day" -> {
                val today = Formatters.todayIso()
                val cur = vm.repo.shopDays.value.firstOrNull { it.date == today }
                vm.repo.saveShopDay((cur ?: ShopDayRecord(date = today)).copy(openedAt = System.currentTimeMillis(), isLeave = false))
                "Shop Day open pannitten." to emptyList()
            }

            "close_day" -> {
                val today = Formatters.todayIso()
                val doClose: suspend () -> String = {
                    val todaySales = vm.repo.invoices.value.filter { it.status == "active" && it.docType == "invoice" && it.date == today }
                    val todayExp = vm.repo.expenses.value.filter { it.date == today }.sumOf { it.amount }
                    val cur = vm.repo.shopDays.value.firstOrNull { it.date == today } ?: ShopDayRecord(date = today)
                    vm.repo.saveShopDay(
                        cur.copy(
                            closedAt = System.currentTimeMillis(),
                            closedSnapshot = ShopDayClosedSnapshot(
                                billsCount = todaySales.size,
                                salesTotal = todaySales.sumOf { it.grandTotal },
                                collectedTotal = todaySales.sumOf { it.amountPaid },
                                dueTotal = todaySales.sumOf { it.balanceDue },
                                expensesTotal = todayExp
                            )
                        )
                    )
                    "Day closed! Sales ${Formatters.formatInr(todaySales.sumOf { it.grandTotal })}."
                }
                if (aiSettings.askConfirmationForDestructive) {
                    vm.setPendingAiConfirmation(
                        PendingAiConfirmation("Close today's day?", "Sales and accounts will be marked closed.") { doClose() }
                    )
                    return "Innaiku day close pannava? Confirm pannunga." to emptyList()
                }
                doClose() to emptyList()
            }

            "switch_language" -> {
                val lang = args.optString("language", "ta").let { if (it.startsWith("e", true)) "en" else "ta" }
                vm.repo.updateSettings { it.copy(language = lang) }
                (if (lang == "ta") "App language Tamil-ku maathitten." else "Switched app language to English.") to emptyList()
            }

            else -> "Action done." to emptyList()
        }
    }

    private fun fuzzyFindStock(query: String): List<StockItem> {
        val cleaned = query.trim().lowercase()
        if (cleaned.isBlank()) return emptyList()
        val strict = vm.repo.items.value.filter { Formatters.matchesStockQuery(it, cleaned) }
        if (strict.isNotEmpty()) return strict
        // Tanglish / phonetic token subset fallback
        val tokens = cleaned.split(Regex("\\s+")).filter { it.length >= 2 }
        return vm.repo.items.value.mapNotNull { item ->
            val hay = listOf(item.brand, item.model, item.color, item.category, item.alsoFits, item.barcode)
                .joinToString(" ").lowercase()
            val score = tokens.count { hay.contains(it) }
            if (score > 0) item to score else null
        }.sortedByDescending { it.second }.map { it.first }
    }

    private fun buildToolDeclarations(): JSONArray {
        fun decl(name: String, desc: String, props: Map<String, Pair<String, String>> = emptyMap(), required: List<String> = emptyList()): JSONObject {
            val propsObj = JSONObject()
            props.forEach { (k, pair) ->
                propsObj.put(k, JSONObject().put("type", pair.first).put("description", pair.second))
            }
            return JSONObject().apply {
                put("name", name)
                put("description", desc)
                put("parameters", JSONObject().apply {
                    put("type", "OBJECT")
                    put("properties", propsObj)
                    if (required.isNotEmpty()) put("required", JSONArray(required))
                })
            }
        }
        return JSONArray(
            listOf(
                decl("open_screen", "Navigate to an app section", mapOf("section" to ("STRING" to "home, billing, items, search, repairs, service_status, purchases, parties, cash_bank, expenses, reports, settings")), listOf("section")),
                decl("open_new_sale", "Open a fresh sales bill", mapOf("billType" to ("STRING" to "product or repair"))),
                decl("set_customer", "Set customer name and phone on current sale bill", mapOf("name" to ("STRING" to "Customer name"), "phone" to ("STRING" to "Phone number")), listOf("name")),
                decl("add_item_to_bill", "Search and add a stock item to the current sale bill", mapOf("query" to ("STRING" to "Item model/name/barcode"), "qty" to ("INTEGER" to "Quantity"), "rate" to ("NUMBER" to "Optional override rate")), listOf("query")),
                decl("set_discount", "Set bill discount", mapOf("type" to ("STRING" to "flat or percent"), "value" to ("NUMBER" to "Discount value")), listOf("value")),
                decl("set_gst", "Set GST slab percentage", mapOf("rate" to ("NUMBER" to "0, 5, 12, 18, or 28")), listOf("rate")),
                decl("set_payment", "Set payment mode and paid amount on current sale bill", mapOf("mode" to ("STRING" to "Cash, UPI, Card, Bank Transfer, Credit (Udhar)"), "amountPaid" to ("NUMBER" to "Amount paid")), listOf("mode")),
                decl("set_part_source", "Set v10 Part Source switch on current sale bill", mapOf("option" to ("STRING" to "labour, stock, or outside"), "query" to ("STRING" to "Stock part search"), "supplierName" to ("STRING" to "Outside supplier"), "partName" to ("STRING" to "Outside part name"), "buyingCost" to ("NUMBER" to "Buying cost"), "partCharge" to ("NUMBER" to "Customer charge"), "labourCharge" to ("NUMBER" to "Labour fee")), listOf("option")),
                decl("save_bill", "Save the current sale bill"),
                decl("share_bill_whatsapp", "Share the latest invoice PDF on WhatsApp"),
                decl("open_new_service", "Open service job form with customer & device info", mapOf("customerName" to ("STRING" to "Name"), "customerPhone" to ("STRING" to "Phone"), "phoneModel" to ("STRING" to "Device model"), "problem" to ("STRING" to "Issue"), "serviceCharge" to ("NUMBER" to "Service charge"))),
                decl("update_service_status", "Update repair job status", mapOf("jobQuery" to ("STRING" to "JOB-xxxx or customer/model"), "status" to ("STRING" to "Received, In-Progress, Ready for Pickup, Delivered"), "note" to ("STRING" to "Progress note")), listOf("jobQuery", "status")),
                decl("open_new_purchase", "Open purchase bill screen", mapOf("supplierName" to ("STRING" to "Supplier"), "supplierPhone" to ("STRING" to "Phone"))),
                decl("add_purchase_line", "Add a row to the current purchase bill", mapOf("model" to ("STRING" to "Model"), "qty" to ("INTEGER" to "Qty"), "buyPrice" to ("NUMBER" to "Buy price"), "sellPrice" to ("NUMBER" to "Sell price")), listOf("model")),
                decl("add_stock", "Create a new stock SKU", mapOf("brand" to ("STRING" to "Brand"), "model" to ("STRING" to "Model"), "category" to ("STRING" to "Category"), "color" to ("STRING" to "Color"), "sellingPrice" to ("NUMBER" to "Sell price"), "costPrice" to ("NUMBER" to "Cost price"), "pieces" to ("INTEGER" to "Qty")), listOf("model")),
                decl("adjust_stock", "Adjust stock pieces (add, reduce, set)", mapOf("query" to ("STRING" to "Item query"), "mode" to ("STRING" to "add, reduce, or set"), "qty" to ("INTEGER" to "Quantity")), listOf("query", "mode", "qty")),
                decl("add_expense", "Record a shop expense", mapOf("category" to ("STRING" to "Category"), "reason" to ("STRING" to "Reason"), "amount" to ("NUMBER" to "Amount"), "mode" to ("STRING" to "Cash or UPI")), listOf("reason", "amount")),
                decl("record_payment", "Record payment in or out for a party", mapOf("partyName" to ("STRING" to "Party name"), "direction" to ("STRING" to "in or out"), "amount" to ("NUMBER" to "Amount"), "mode" to ("STRING" to "Cash or UPI")), listOf("partyName", "amount")),
                decl("add_party", "Save a customer or supplier party", mapOf("name" to ("STRING" to "Name"), "phone" to ("STRING" to "Phone"), "type" to ("STRING" to "customer, supplier, or both")), listOf("name")),
                decl("search_stock", "Check stock pieces and selling price", mapOf("query" to ("STRING" to "Model/brand/color")), listOf("query")),
                decl("get_party_balance", "Check a party's pending due balance", mapOf("partyName" to ("STRING" to "Customer/supplier name")), listOf("partyName")),
                decl("get_today_summary", "Get today's sales, bills, expenses, and cash summary"),
                decl("get_report", "Open a specific report tab", mapOf("type" to ("STRING" to "Report tab name")), listOf("type")),
                decl("open_shop_day", "Record today's shop opening time"),
                decl("close_day", "Close today's shop day and snapshot accounts"),
                decl("switch_language", "Switch app language between Tamil and English", mapOf("language" to ("STRING" to "ta or en")), listOf("language"))
            )
        )
    }
}
