package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.example.data.model.ShopSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

object WhatsAppHelper {
    private val httpClient = OkHttpClient()

    fun openWhatsAppChat(context: Context, phone: String, message: String) {
        val encoded = Uri.encode(message)
        val normalized = Formatters.normalizeWhatsAppPhone(phone)
        val url = if (normalized.isNotBlank()) {
            "https://wa.me/$normalized?text=$encoded"
        } else {
            "https://wa.me/?text=$encoded"
        }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(context, "WhatsApp is not installed on this device.", Toast.LENGTH_SHORT).show()
        }
    }

    fun openMapsLink(context: Context, addressOrUrl: String) {
        val trimmed = addressOrUrl.trim()
        if (trimmed.isBlank()) return
        val uri = if (trimmed.startsWith("http://", true) || trimmed.startsWith("https://", true)) {
            Uri.parse(trimmed)
        } else {
            Uri.parse("https://maps.google.com/?q=${Uri.encode(trimmed)}")
        }
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    }

    suspend fun sendViaMetaCloudApi(
        settings: ShopSettings,
        phone: String,
        message: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val phoneId = settings.metaWhatsappPhoneId.trim()
        val token = settings.metaWhatsappToken.trim()
        val to = Formatters.normalizeWhatsAppPhone(phone)
        if (phoneId.isBlank() || token.isBlank() || to.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Meta WhatsApp API credentials or phone missing"))
        }
        try {
            val bodyJson = JSONObject().apply {
                put("messaging_product", "whatsapp")
                put("to", to)
                put("type", "text")
                put("text", JSONObject().put("body", message))
            }
            val request = Request.Builder()
                .url("https://graph.facebook.com/v20.0/$phoneId/messages")
                .addHeader("Authorization", "Bearer $token")
                .post(bodyJson.toString().toRequestBody("application/json".toMediaType()))
                .build()
            httpClient.newCall(request).execute().use { resp ->
                if (resp.isSuccessful) Result.success("WhatsApp message sent via Cloud API.")
                else Result.failure(IllegalStateException("Meta API ${resp.code}: ${resp.body?.string().orEmpty()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
