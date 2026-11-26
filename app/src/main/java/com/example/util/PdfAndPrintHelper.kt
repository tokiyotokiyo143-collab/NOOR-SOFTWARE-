package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.util.Base64
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.R
import com.example.data.model.*
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object PdfAndPrintHelper {

    fun decodeDataUrlBitmap(dataUrl: String): Bitmap? = BitmapCache.decodeDataUrlCached(dataUrl)

    private fun shopLogoBitmap(context: Context, settings: ShopSettings): Bitmap? {
        decodeDataUrlBitmap(settings.logoDataUrl)?.let { return it }
        val key = "res:default_shop_logo"
        BitmapCache.get(key)?.let { return it }
        return runCatching {
            BitmapFactory.decodeResource(context.resources, R.drawable.noor_software_logo_1795681598224)?.also {
                BitmapCache.put(key, it)
            }
        }.getOrNull()
    }

    private fun upiQrBitmap(settings: ShopSettings, amount: Double): Bitmap? {
        decodeDataUrlBitmap(settings.upiQrDataUrl)?.let { return it }
        if (settings.upiId.isBlank()) return null
        val amtPart = if (amount > 0) "&am=${String.format(java.util.Locale.US, "%.2f", amount)}" else ""
        val uri = "upi://pay?pa=${settings.upiId.trim()}&pn=${android.net.Uri.encode(settings.shopName)}$amtPart&cu=INR"
        return BarcodeUtils.generateQrBitmap(uri, 200)
    }

    fun generateInvoicePdf(
        context: Context,
        invoice: Invoice,
        settings: ShopSettings,
        paperSize: String = "A4", // "A4" | "80mm" | "58mm"
        copyLabel: String = "Customer Copy"
    ): File? {
        return try {
            val doc = PdfDocument()
            val (pageW, pageH) = when (paperSize) {
                "80mm" -> 226 to 680
                "58mm" -> 164 to 640
                else -> 595 to 842
            }
            val isThermal = paperSize == "80mm" || paperSize == "58mm"
            val margin = if (isThermal) 10f else 32f
            val pageInfo = PdfDocument.PageInfo.Builder(pageW, pageH, 1).create()
            val page = doc.startPage(pageInfo)
            val c = page.canvas
            c.drawColor(Color.WHITE)

            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = if (isThermal) 11f else 17f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val boldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = if (isThermal) 8f else 10.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.DKGRAY
                textSize = if (isThermal) 7.5f else 10f
            }
            val mutedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.GRAY
                textSize = if (isThermal) 6.5f else 8.5f
            }
            val linePaint = Paint().apply {
                color = Color.LTGRAY
                strokeWidth = 1f
            }

            var y = margin + 8f
            val contentW = pageW - margin * 2

            // Logo & Copy label
            val logo = shopLogoBitmap(context, settings)
            if (logo != null && !isThermal) {
                val rect = RectF(margin, y - 6f, margin + 44f, y + 38f)
                c.drawBitmap(logo, null, rect, null)
            }
            val headerX = if (logo != null && !isThermal) margin + 54f else margin
            c.drawText(settings.shopName, headerX, y + 6f, titlePaint)
            y += if (isThermal) 13f else 20f

            if (settings.address.isNotBlank()) {
                val addrLine = if (settings.address.startsWith("http", true)) "Find us on Google Maps" else settings.address.take(65)
                c.drawText(addrLine, headerX, y, textPaint)
                y += if (isThermal) 10f else 13f
            }
            if (settings.phone.isNotBlank()) {
                c.drawText("Ph: ${settings.phone}", headerX, y, textPaint)
                y += if (isThermal) 10f else 13f
            }
            val ig = Formatters.extractInstagramHandle(settings.instagram)
            if (ig.isNotBlank()) {
                c.drawText("Instagram: @$ig", headerX, y, textPaint)
                y += if (isThermal) 10f else 13f
            }
            if (settings.gstNumber.isNotBlank()) {
                c.drawText("GSTIN: ${settings.gstNumber}", headerX, y, boldPaint)
                y += if (isThermal) 10f else 13f
            }

            // Copy Label & Doc Title
            y += 4f
            c.drawLine(margin, y, pageW - margin, y, linePaint)
            y += if (isThermal) 11f else 15f
            val docTitle = when (invoice.docType) {
                "estimate" -> "ESTIMATE / QUOTE"
                "order" -> "SALE ORDER"
                "challan" -> "DELIVERY CHALLAN"
                "return" -> "SALE RETURN / CREDIT NOTE"
                else -> "TAX INVOICE / BILL"
            }
            c.drawText("$docTitle  ($copyLabel)", margin, y, boldPaint)
            y += if (isThermal) 11f else 15f
            c.drawText("Bill #: ${invoice.invoiceNumber}   Date: ${Formatters.formatDisplayDate(invoice.date)}", margin, y, textPaint)
            y += if (isThermal) 10f else 14f
            c.drawText("Billed To: ${invoice.customerName.ifBlank { "Walk-in Customer" }} ${if (invoice.customerPhone.isNotBlank()) "(${invoice.customerPhone})" else ""}", margin, y, boldPaint)
            y += if (isThermal) 10f else 14f
            c.drawText("Payment Mode: ${invoice.paymentMode}", margin, y, textPaint)
            if (invoice.imei.isNotBlank()) {
                y += if (isThermal) 10f else 13f
                c.drawText("IMEI: ${invoice.imei}", margin, y, textPaint)
            }
            y += 6f
            c.drawLine(margin, y, pageW - margin, y, linePaint)
            y += if (isThermal) 11f else 16f

            // Table Header
            if (isThermal) {
                c.drawText("Item", margin, y, boldPaint)
                c.drawText("Qty x Rate = Amt", margin + contentW * 0.42f, y, boldPaint)
            } else {
                c.drawText("Item / Service", margin, y, boldPaint)
                c.drawText("Qty", margin + contentW * 0.56f, y, boldPaint)
                c.drawText("Rate", margin + contentW * 0.68f, y, boldPaint)
                c.drawText("Amount", margin + contentW * 0.84f, y, boldPaint)
            }
            y += 5f
            c.drawLine(margin, y, pageW - margin, y, linePaint)
            y += if (isThermal) 10f else 14f

            // Lines (Never print cost price or profit)
            for (line in invoice.items) {
                if (y > pageH - 130) break
                val itemName = line.name.ifBlank { listOf(line.brand, line.model, line.color).filter { it.isNotBlank() }.joinToString(" ") }
                if (isThermal) {
                    c.drawText(itemName.take(28), margin, y, textPaint)
                    y += 9f
                    c.drawText("${line.qty} × ${Formatters.formatInr(line.rate)} = ${Formatters.formatInr(line.total)}", margin + 6f, y, boldPaint)
                    y += 11f
                } else {
                    c.drawText(itemName.take(40), margin, y, textPaint)
                    c.drawText(line.qty.toString(), margin + contentW * 0.57f, y, textPaint)
                    c.drawText(Formatters.formatInr(line.rate), margin + contentW * 0.68f, y, textPaint)
                    c.drawText(Formatters.formatInr(line.total), margin + contentW * 0.84f, y, boldPaint)
                    y += 15f
                }
            }
            if (invoice.partSourceMode != "labour" && invoice.outsidePartCharge > 0 && invoice.items.none { it.name == invoice.outsidePartName }) {
                val pName = invoice.outsidePartName.ifBlank { "Spare Part" }
                c.drawText(pName.take(36), margin, y, textPaint)
                if (!isThermal) c.drawText(Formatters.formatInr(invoice.outsidePartCharge), margin + contentW * 0.84f, y, boldPaint)
                y += if (isThermal) 11f else 15f
            }

            y += 4f
            c.drawLine(margin, y, pageW - margin, y, linePaint)
            y += if (isThermal) 11f else 15f

            fun drawTotalRow(label: String, value: String, isBold: Boolean = false) {
                val p = if (isBold) boldPaint else textPaint
                c.drawText(label, margin, y, p)
                c.drawText(value, margin + contentW * (if (isThermal) 0.56f else 0.78f), y, p)
                y += if (isThermal) 10f else 14f
            }

            drawTotalRow("Subtotal", Formatters.formatInr(invoice.subtotal))
            if (invoice.discountAmount > 0.0) {
                val discVal = if (invoice.discountType == "percent") {
                    invoice.subtotal * invoice.discountAmount / 100.0
                } else invoice.discountAmount
                drawTotalRow("Discount", "-${Formatters.formatInr(discVal)}")
            }
            if (invoice.gstRate > 0.0) {
                val taxable = (invoice.subtotal - if (invoice.discountType == "percent") invoice.subtotal * invoice.discountAmount / 100.0 else invoice.discountAmount).coerceAtLeast(0.0)
                val gstAmt = taxable * invoice.gstRate / 100.0
                drawTotalRow("GST (${invoice.gstRate.toInt()}%)", Formatters.formatInr(gstAmt))
            }
            drawTotalRow("Grand Total", Formatters.formatInr(invoice.grandTotal), isBold = true)
            drawTotalRow("Paid", Formatters.formatInr(invoice.amountPaid))
            drawTotalRow("Balance Due", Formatters.formatInr(invoice.balanceDue), isBold = invoice.balanceDue > 0)

            if (invoice.notes.isNotBlank()) {
                y += 4f
                c.drawText("Notes: ${invoice.notes.take(70)}", margin, y, mutedPaint)
                y += 12f
            }

            // UPI QR
            val qr = upiQrBitmap(settings, if (invoice.balanceDue > 0) invoice.balanceDue else invoice.grandTotal)
            if (qr != null && y + 70 < pageH - 30) {
                y += 4f
                val qrSize = if (isThermal) 54f else 68f
                c.drawBitmap(qr, null, RectF(margin, y, margin + qrSize, y + qrSize), null)
                c.drawText("Scan & Pay via UPI", margin + qrSize + 8f, y + 20f, boldPaint)
                if (settings.upiId.isNotBlank()) {
                    c.drawText(settings.upiId, margin + qrSize + 8f, y + 34f, mutedPaint)
                }
                y += qrSize + 8f
            } else if (qr == null) {
                y += 4f
                c.drawText("Scan & Pay via UPI (QR not set)", margin, y, mutedPaint)
                y += 12f
            }

            if (settings.invoiceTerms.isNotBlank() && y < pageH - 14) {
                c.drawText(settings.invoiceTerms.take(85), margin, y + 6f, mutedPaint)
            }

            doc.finishPage(page)
            val dir = File(context.cacheDir, "pdfs").apply { mkdirs() }
            val outFile = File(dir, "Bill_${invoice.invoiceNumber.replace('/', '_')}.pdf")
            FileOutputStream(outFile).use { doc.writeTo(it) }
            doc.close()
            outFile
        } catch (_: Exception) {
            null
        }
    }

    fun generateRepairSlipPdf(context: Context, repair: RepairJob, settings: ShopSettings): File? {
        return try {
            val doc = PdfDocument()
            val page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
            val c = page.canvas
            c.drawColor(Color.WHITE)

            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 18f; typeface = Typeface.DEFAULT_BOLD }
            val boldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 11f; typeface = Typeface.DEFAULT_BOLD }
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.DKGRAY; textSize = 10.5f }
            val linePaint = Paint().apply { color = Color.LTGRAY; strokeWidth = 1f }

            var y = 42f
            val m = 36f
            c.drawText(settings.shopName, m, y, titlePaint); y += 18f
            if (settings.phone.isNotBlank()) { c.drawText("Ph: ${settings.phone}  ${settings.address}", m, y, textPaint); y += 16f }
            c.drawLine(m, y, 559f, y, linePaint); y += 22f
            c.drawText("REPAIR SLIP · ${repair.jobNumber}", m, y, titlePaint); y += 22f

            fun row(k: String, v: String) {
                c.drawText(k, m, y, boldPaint)
                c.drawText(v, m + 150f, y, textPaint)
                y += 18f
            }
            row("Customer:", "${repair.customerName} (${repair.customerPhone})")
            row("Device:", repair.phoneModel)
            row("IMEI:", repair.imei.ifBlank { "-" })
            row("Issue:", repair.problem)
            row("Received:", Formatters.formatDisplayDate(repair.date))
            row("Est Delivery:", Formatters.formatDisplayDate(repair.deliveryDate).ifBlank { "-" })
            row("Status:", repair.status)
            y += 8f; c.drawLine(m, y, 559f, y, linePaint); y += 20f
            row("Total Service & Parts:", Formatters.formatInr(repair.totalCharged))
            row("Advance / Paid:", Formatters.formatInr(repair.totalPaid))
            row("Balance on Delivery:", Formatters.formatInr(repair.balanceDue))

            y += 18f
            c.drawText("Please bring this slip at the time of device pickup. Backup important data before service.", m, y, textPaint)
            y += 50f
            c.drawLine(m, y, m + 160f, y, linePaint)
            c.drawLine(370f, y, 530f, y, linePaint)
            y += 14f
            c.drawText("Customer Sign", m + 30f, y, textPaint)
            c.drawText("Authorized Sign", 400f, y, textPaint)

            doc.finishPage(page)
            val dir = File(context.cacheDir, "pdfs").apply { mkdirs() }
            val outFile = File(dir, "RepairSlip_${repair.jobNumber}.pdf")
            FileOutputStream(outFile).use { doc.writeTo(it) }
            doc.close()
            outFile
        } catch (_: Exception) {
            null
        }
    }

    fun generateBarcodeLabelsPdf(
        context: Context,
        items: List<Pair<StockItem, Int>>,
        settings: ShopSettings,
        layout: String, // "A4_3" | "A4_4" | "THERMAL"
        showShopName: Boolean,
        showPrice: Boolean,
        showCategory: Boolean
    ): File? {
        return try {
            val doc = PdfDocument()
            val cols = when (layout) {
                "A4_4" -> 4
                "THERMAL" -> 1
                else -> 3
            }
            val pageW = if (layout == "THERMAL") 226 else 595
            val pageH = if (layout == "THERMAL") 140 else 842
            val margin = if (layout == "THERMAL") 8f else 22f
            val cellW = (pageW - margin * 2) / cols
            val cellH = if (layout == "THERMAL") 120f else 112f

            val expanded = mutableListOf<StockItem>()
            items.forEach { (item, qty) -> repeat(qty.coerceIn(1, 50)) { expanded.add(item) } }
            if (expanded.isEmpty()) return null

            val boldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 9f; typeface = Typeface.DEFAULT_BOLD }
            val smallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.DKGRAY; textSize = 7.5f }
            val borderPaint = Paint().apply { color = Color.LTGRAY; style = Paint.Style.STROKE; strokeWidth = 0.8f }

            var pageNum = 1
            var page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNum).create())
            var c = page.canvas
            c.drawColor(Color.WHITE)

            var col = 0
            var row = 0
            val maxRows = ((pageH - margin * 2) / cellH).toInt().coerceAtLeast(1)

            for (item in expanded) {
                if (row >= maxRows) {
                    doc.finishPage(page)
                    pageNum++
                    page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNum).create())
                    c = page.canvas
                    c.drawColor(Color.WHITE)
                    row = 0
                    col = 0
                }
                val x = margin + col * cellW
                val y = margin + row * cellH
                c.drawRect(x + 2f, y + 2f, x + cellW - 2f, y + cellH - 2f, borderPaint)

                var ty = y + 14f
                if (showShopName) {
                    c.drawText(settings.shopName.take(24), x + 8f, ty, smallPaint); ty += 11f
                }
                c.drawText(item.displayName.take(22), x + 8f, ty, boldPaint); ty += 10f
                if (showCategory) {
                    c.drawText("${item.category} ${item.color}".trim().take(24), x + 8f, ty, smallPaint); ty += 10f
                }
                val codeVal = item.barcode.ifBlank { item.id.take(12) }
                val bmp = BarcodeUtils.generateBarcodeBitmap(codeVal, 260, 52)
                if (bmp != null) {
                    c.drawBitmap(bmp, null, RectF(x + 8f, ty, x + cellW - 10f, ty + 34f), null)
                    ty += 42f
                }
                c.drawText(codeVal, x + 8f, ty, smallPaint)
                if (showPrice) {
                    c.drawText(Formatters.formatInr(item.sellingPrice), x + cellW - 56f, ty, boldPaint)
                }

                col++
                if (col >= cols) {
                    col = 0
                    row++
                }
            }
            doc.finishPage(page)
            val dir = File(context.cacheDir, "pdfs").apply { mkdirs() }
            val outFile = File(dir, "Barcode_Labels_${Formatters.todayIso()}.pdf")
            FileOutputStream(outFile).use { doc.writeTo(it) }
            doc.close()
            outFile
        } catch (_: Exception) {
            null
        }
    }

    fun generateTextReportPdf(
        context: Context,
        title: String,
        subtitle: String,
        lines: List<String>,
        settings: ShopSettings
    ): File? {
        return try {
            val doc = PdfDocument()
            var pageNum = 1
            var page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNum).create())
            var c = page.canvas
            c.drawColor(Color.WHITE)

            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 16f; typeface = Typeface.DEFAULT_BOLD }
            val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.DKGRAY; textSize = 10f }
            val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 9.5f; typeface = Typeface.MONOSPACE }

            var y = 40f
            c.drawText("${settings.shopName} — $title", 32f, y, titlePaint); y += 18f
            c.drawText(subtitle, 32f, y, subPaint); y += 20f

            for (line in lines) {
                if (y > 800f) {
                    doc.finishPage(page)
                    pageNum++
                    page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNum).create())
                    c = page.canvas
                    c.drawColor(Color.WHITE)
                    y = 40f
                }
                c.drawText(line.take(92), 32f, y, bodyPaint)
                y += 14f
            }
            doc.finishPage(page)
            val dir = File(context.cacheDir, "pdfs").apply { mkdirs() }
            val safeName = title.replace(Regex("[^A-Za-z0-9_-]"), "_")
            val outFile = File(dir, "${safeName}_${Formatters.todayIso()}.pdf")
            FileOutputStream(outFile).use { doc.writeTo(it) }
            doc.close()
            outFile
        } catch (_: Exception) {
            null
        }
    }

    fun printPdfFile(context: Context, file: File, jobName: String) {
        val pm = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
        if (pm == null) {
            Toast.makeText(context, "Unable to open print dialog on this device.", Toast.LENGTH_SHORT).show()
            return
        }
        pm.print(jobName, object : PrintDocumentAdapter() {
            override fun onLayout(
                oldAttributes: PrintAttributes?,
                newAttributes: PrintAttributes?,
                cancellationSignal: CancellationSignal?,
                callback: LayoutResultCallback,
                extras: Bundle?
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback.onLayoutCancelled()
                    return
                }
                val info = PrintDocumentInfo.Builder(file.name)
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .build()
                callback.onLayoutFinished(info, true)
            }

            override fun onWrite(
                pages: Array<out PageRange>?,
                destination: ParcelFileDescriptor,
                cancellationSignal: CancellationSignal?,
                callback: WriteResultCallback
            ) {
                try {
                    FileInputStream(file).use { input ->
                        FileOutputStream(destination.fileDescriptor).use { output ->
                            input.copyTo(output)
                        }
                    }
                    callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                } catch (e: Exception) {
                    callback.onWriteFailed(e.message)
                }
            }
        }, null)
    }

    fun sharePdfFile(context: Context, file: File, title: String) {
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, title)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(intent, title).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            Toast.makeText(
                context,
                "PDF bill ${file.name} opened in Share sheet — choose WhatsApp to send.",
                Toast.LENGTH_LONG
            ).show()
        } catch (_: Exception) {
            Toast.makeText(context, "Could not generate PDF bill.", Toast.LENGTH_SHORT).show()
        }
    }
}
