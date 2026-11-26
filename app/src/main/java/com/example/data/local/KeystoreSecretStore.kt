package com.example.data.local

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.example.data.model.AiSettings
import com.example.data.model.CompanyMeta
import com.example.data.model.OfflineUserSession
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores device-only secrets (Gemini API key, optional PIN lock, offline auth cache,
 * AI settings, and company registry) encrypted via Android Keystore AES-256-GCM.
 * Never synced to Firestore or included in exports.
 */
class KeystoreSecretStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("noor_keystore_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_ALIAS = "noor_software_aes_key"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"

        private const val PREF_GEMINI_KEY = "enc_gemini_api_key"
        private const val PREF_PIN_CODE = "enc_pin_code"
        private const val PREF_OFFLINE_SESSION = "enc_offline_session"
        private const val PREF_AI_SETTINGS = "ai_settings_json"
        private const val PREF_COMPANIES = "companies_json"
        private const val PREF_ACTIVE_COMPANY = "active_company_id"
        private const val PREF_FIRST_LAUNCH_PROMPT_SHOWN = "first_launch_backup_prompt_shown"
        private const val PREF_LAST_WEEKLY_REMINDER = "last_weekly_backup_reminder"
    }

    private fun getOrCreateKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = ks.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
        if (existing != null) return existing.secretKey
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private fun encrypt(plain: String): String {
        if (plain.isEmpty()) return ""
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            val iv = cipher.iv
            val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
            val combined = ByteArray(1 + iv.size + encrypted.size)
            combined[0] = iv.size.toByte()
            System.arraycopy(iv, 0, combined, 1, iv.size)
            System.arraycopy(encrypted, 0, combined, 1 + iv.size, encrypted.size)
            Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (_: Exception) {
            ""
        }
    }

    private fun decrypt(encoded: String): String {
        if (encoded.isEmpty()) return ""
        return try {
            val combined = Base64.decode(encoded, Base64.NO_WRAP)
            val ivLen = combined[0].toInt() and 0xFF
            val iv = combined.copyOfRange(1, 1 + ivLen)
            val data = combined.copyOfRange(1 + ivLen, combined.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(data), Charsets.UTF_8)
        } catch (_: Exception) {
            ""
        }
    }

    fun getGeminiApiKey(): String = decrypt(prefs.getString(PREF_GEMINI_KEY, "") ?: "")
    fun setGeminiApiKey(key: String) {
        prefs.edit().putString(PREF_GEMINI_KEY, encrypt(key.trim())).apply()
    }

    fun getDevicePin(): String = decrypt(prefs.getString(PREF_PIN_CODE, "") ?: "")
    fun setDevicePin(pin: String) {
        prefs.edit().putString(PREF_PIN_CODE, encrypt(pin.trim())).apply()
    }

    fun saveOfflineSession(session: OfflineUserSession?) {
        if (session == null) {
            prefs.edit().remove(PREF_OFFLINE_SESSION).apply()
            return
        }
        val json = JSONObject().apply {
            put("uid", session.uid)
            put("email", session.email)
            put("displayName", session.displayName)
            put("emailVerified", session.emailVerified)
        }.toString()
        prefs.edit().putString(PREF_OFFLINE_SESSION, encrypt(json)).apply()
    }

    fun getOfflineSession(): OfflineUserSession? {
        val raw = decrypt(prefs.getString(PREF_OFFLINE_SESSION, "") ?: "")
        if (raw.isBlank()) return null
        return try {
            val obj = JSONObject(raw)
            OfflineUserSession(
                uid = obj.optString("uid"),
                email = obj.optString("email"),
                displayName = obj.optString("displayName"),
                emailVerified = obj.optBoolean("emailVerified", false)
            ).takeIf { it.email.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    fun getAiSettings(): AiSettings {
        val raw = prefs.getString(PREF_AI_SETTINGS, null) ?: return AiSettings()
        return try {
            val obj = JSONObject(raw)
            AiSettings(
                enabled = obj.optBoolean("enabled", false),
                model = obj.optString("model", "gemini-3.5-flash-lite").ifBlank { "gemini-3.5-flash-lite" }.let { if (it.startsWith("gemini-2")) "gemini-3.5-flash-lite" else it },
                speakRepliesAloud = obj.optBoolean("speakRepliesAloud", true),
                replyLanguage = obj.optString("replyLanguage", "ta").ifBlank { "ta" },
                askConfirmationForDestructive = obj.optBoolean("askConfirmationForDestructive", true),
                allowScreenShare = obj.optBoolean("allowScreenShare", true),
                bubbleOffsetXFraction = obj.optDouble("bubbleOffsetXFraction", 0.92).toFloat(),
                bubbleOffsetYFraction = obj.optDouble("bubbleOffsetYFraction", 0.72).toFloat()
            )
        } catch (_: Exception) {
            AiSettings()
        }
    }

    fun saveAiSettings(settings: AiSettings) {
        val json = JSONObject().apply {
            put("enabled", settings.enabled)
            put("model", settings.model)
            put("speakRepliesAloud", settings.speakRepliesAloud)
            put("replyLanguage", settings.replyLanguage)
            put("askConfirmationForDestructive", settings.askConfirmationForDestructive)
            put("allowScreenShare", settings.allowScreenShare)
            put("bubbleOffsetXFraction", settings.bubbleOffsetXFraction.toDouble())
            put("bubbleOffsetYFraction", settings.bubbleOffsetYFraction.toDouble())
        }.toString()
        prefs.edit().putString(PREF_AI_SETTINGS, json).apply()
    }

    fun getCompanies(): List<CompanyMeta> {
        val raw = prefs.getString(PREF_COMPANIES, null)
        if (raw.isNullOrBlank()) {
            val defaultCompany = CompanyMeta(
                id = "default",
                name = "AN-NOOR Mobile Service Point",
                dbName = "noor_shop_default.db"
            )
            saveCompanies(listOf(defaultCompany))
            return listOf(defaultCompany)
        }
        return try {
            val arr = JSONArray(raw)
            val list = (0 until arr.length()).mapNotNull { i ->
                arr.optJSONObject(i)?.let {
                    CompanyMeta(
                        id = it.optString("id", "default"),
                        name = it.optString("name", "AN-NOOR Mobile Service Point"),
                        dbName = it.optString("dbName", "noor_shop_default.db"),
                        createdAt = it.optLong("createdAt", System.currentTimeMillis())
                    )
                }
            }
            list.ifEmpty {
                listOf(CompanyMeta("default", "AN-NOOR Mobile Service Point", "noor_shop_default.db"))
            }
        } catch (_: Exception) {
            listOf(CompanyMeta("default", "AN-NOOR Mobile Service Point", "noor_shop_default.db"))
        }
    }

    fun saveCompanies(companies: List<CompanyMeta>) {
        val arr = JSONArray().apply {
            companies.forEach { c ->
                put(JSONObject().apply {
                    put("id", c.id)
                    put("name", c.name)
                    put("dbName", c.dbName)
                    put("createdAt", c.createdAt)
                })
            }
        }
        prefs.edit().putString(PREF_COMPANIES, arr.toString()).apply()
    }

    fun getActiveCompanyId(): String = prefs.getString(PREF_ACTIVE_COMPANY, "default") ?: "default"
    fun setActiveCompanyId(id: String) {
        prefs.edit().putString(PREF_ACTIVE_COMPANY, id).apply()
    }

    fun isFirstLaunchPromptShown(): Boolean = prefs.getBoolean(PREF_FIRST_LAUNCH_PROMPT_SHOWN, false)
    fun setFirstLaunchPromptShown() {
        prefs.edit().putBoolean(PREF_FIRST_LAUNCH_PROMPT_SHOWN, true).apply()
    }

    fun getLastWeeklyReminder(): Long = prefs.getLong(PREF_LAST_WEEKLY_REMINDER, 0L)
    fun setLastWeeklyReminder(ts: Long) {
        prefs.edit().putLong(PREF_LAST_WEEKLY_REMINDER, ts).apply()
    }
}
