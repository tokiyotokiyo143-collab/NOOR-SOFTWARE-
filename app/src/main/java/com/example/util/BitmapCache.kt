package com.example.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Thread-safe in-memory LruCache for decoded data-URL bitmaps, QR codes, and barcodes.
 * Prevents repeated Base64/BitmapFactory/ZXing work on the main thread.
 */
object BitmapCache {
    private val maxBytes: Int = minOf(
        24 * 1024 * 1024,
        (Runtime.getRuntime().maxMemory() / 8L).toInt().coerceAtLeast(4 * 1024 * 1024)
    )

    private val cache = object : LruCache<String, Bitmap>(maxBytes) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return value.allocationByteCount.coerceAtLeast(1)
        }
    }

    fun get(key: String): Bitmap? = synchronized(cache) { cache.get(key) }

    fun put(key: String, bitmap: Bitmap) {
        synchronized(cache) {
            cache.put(key, bitmap)
        }
    }

    private fun dataUrlCacheKey(dataUrl: String): String {
        val len = dataUrl.length
        val headHash = if (len > 96) dataUrl.substring(0, 96).hashCode() else dataUrl.hashCode()
        val tailHash = if (len > 48) dataUrl.substring(len - 48).hashCode() else headHash
        return "durl:$len:$headHash:$tailHash"
    }

    fun getDataUrlIfCached(dataUrl: String): Bitmap? {
        if (dataUrl.isBlank()) return null
        return get(dataUrlCacheKey(dataUrl))
    }

    fun decodeDataUrlCached(dataUrl: String): Bitmap? {
        if (dataUrl.isBlank()) return null
        val key = dataUrlCacheKey(dataUrl)
        get(key)?.let { return it }
        val decoded = try {
            val b64 = dataUrl.substringAfter("base64,", dataUrl)
            val bytes = Base64.decode(b64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (_: Exception) {
            null
        }
        if (decoded != null) put(key, decoded)
        return decoded
    }
}

@Composable
fun rememberCachedDataUrlBitmap(dataUrl: String): ImageBitmap? {
    val cached = remember(dataUrl) {
        BitmapCache.getDataUrlIfCached(dataUrl)?.asImageBitmap()
    }
    val state by produceState<ImageBitmap?>(initialValue = cached, key1 = dataUrl) {
        if (dataUrl.isBlank()) {
            value = null
        } else if (value == null) {
            value = withContext(Dispatchers.IO) {
                BitmapCache.decodeDataUrlCached(dataUrl)?.asImageBitmap()
            }
        }
    }
    return state
}

@Composable
fun rememberCachedQrBitmap(content: String, size: Int = 180): ImageBitmap? {
    val clean = remember(content) { content.trim() }
    val key = remember(clean, size) { "qr:$size:$clean" }
    val cached = remember(key) {
        if (clean.isEmpty()) null else BitmapCache.get(key)?.asImageBitmap()
    }
    val state by produceState<ImageBitmap?>(initialValue = cached, key1 = key) {
        if (clean.isEmpty()) {
            value = null
        } else if (value == null) {
            value = withContext(Dispatchers.Default) {
                BarcodeUtils.generateQrBitmap(clean, size)?.asImageBitmap()
            }
        }
    }
    return state
}
