package com.example.util

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix

object BarcodeUtils {
    fun generateBarcodeBitmap(content: String, width: Int = 360, height: Int = 100): Bitmap? {
        val clean = content.trim()
        if (clean.isEmpty()) return null
        val cacheKey = "bc:${width}x$height:$clean"
        BitmapCache.get(cacheKey)?.let { return it }
        val bmp = try {
            val format = if (clean.length == 13 && clean.all { it.isDigit() }) {
                BarcodeFormat.EAN_13
            } else {
                BarcodeFormat.CODE_128
            }
            val matrix: BitMatrix = MultiFormatWriter().encode(clean, format, width, height)
            matrixToBitmap(matrix)
        } catch (_: Exception) {
            try {
                val matrix = MultiFormatWriter().encode(clean, BarcodeFormat.CODE_128, width, height)
                matrixToBitmap(matrix)
            } catch (_: Exception) {
                null
            }
        }
        if (bmp != null) BitmapCache.put(cacheKey, bmp)
        return bmp
    }

    fun generateQrBitmap(content: String, size: Int = 220): Bitmap? {
        val clean = content.trim()
        if (clean.isEmpty()) return null
        val cacheKey = "qr:$size:$clean"
        BitmapCache.get(cacheKey)?.let { return it }
        val bmp = try {
            val matrix = MultiFormatWriter().encode(clean, BarcodeFormat.QR_CODE, size, size)
            matrixToBitmap(matrix)
        } catch (_: Exception) {
            null
        }
        if (bmp != null) BitmapCache.put(cacheKey, bmp)
        return bmp
    }

    private fun matrixToBitmap(matrix: BitMatrix): Bitmap {
        val w = matrix.width
        val h = matrix.height
        val pixels = IntArray(w * h)
        for (y in 0 until h) {
            val offset = y * w
            for (x in 0 until w) {
                pixels[offset + x] = if (matrix.get(x, y)) Color.BLACK else Color.WHITE
            }
        }
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, w, 0, 0, w, h)
        }
    }
}
