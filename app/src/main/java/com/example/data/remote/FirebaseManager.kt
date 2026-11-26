package com.example.data.remote

import android.content.Context
import com.example.data.model.FIRESTORE_DATABASE_ID
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseOptions
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings

object FirebaseManager {
    private const val API_KEY = "AIzaSyC7EqZZ9_Qi-eSOov24va3VNHEWYXtoagI"
    private const val PROJECT_ID = "hale-thought-sk4jk"
    private const val STORAGE_BUCKET = "hale-thought-sk4jk.firebasestorage.app"
    private const val SENDER_ID = "749103154741"
    private const val FALLBACK_APP_ID = "1:749103154741:android:4379c32bd94a4b6e917822"
    private const val STAFF_CREATOR_APP_NAME = "noor_staff_creator"

    @Volatile
    private var initialized = false

    fun ensureInitialized(context: Context): FirebaseApp {
        val appContext = context.applicationContext
        val existing = FirebaseApp.getApps(appContext).firstOrNull { it.name == FirebaseApp.DEFAULT_APP_NAME }
        if (existing != null) {
            if (!initialized) {
                configureFirestore(existing)
                initialized = true
            }
            return existing
        }

        val options = FirebaseOptions.Builder()
            .setApiKey(API_KEY)
            .setApplicationId(FALLBACK_APP_ID)
            .setProjectId(PROJECT_ID)
            .setStorageBucket(STORAGE_BUCKET)
            .setGcmSenderId(SENDER_ID)
            .build()

        val app = FirebaseApp.initializeApp(appContext, options)
        configureFirestore(app)
        initialized = true
        return app
    }

    private fun configureFirestore(app: FirebaseApp) {
        try {
            val db = FirebaseFirestore.getInstance(app, FIRESTORE_DATABASE_ID)
            val settings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(PersistentCacheSettings.newBuilder().build())
                .build()
            db.firestoreSettings = settings
        } catch (_: Exception) {
            // Already configured
        }
    }

    fun auth(context: Context): FirebaseAuth {
        return FirebaseAuth.getInstance(ensureInitialized(context))
    }

    fun firestore(context: Context): FirebaseFirestore {
        return FirebaseFirestore.getInstance(ensureInitialized(context), FIRESTORE_DATABASE_ID)
    }

    /**
     * Secondary FirebaseApp used purely for createUserWithEmailAndPassword
     * when the Shop Owner adds a new staff login, so the Owner stays signed in.
     */
    fun secondaryAuthForStaffCreation(context: Context): FirebaseAuth {
        val appContext = context.applicationContext
        val existing = FirebaseApp.getApps(appContext).firstOrNull { it.name == STAFF_CREATOR_APP_NAME }
        val secondary = existing ?: run {
            val primary = ensureInitialized(appContext)
            FirebaseApp.initializeApp(appContext, primary.options, STAFF_CREATOR_APP_NAME)
        }
        return FirebaseAuth.getInstance(secondary)
    }

    fun mapAuthErrorMessage(e: Exception): String {
        return when (e) {
            is FirebaseAuthInvalidUserException -> "No account found for this email address."
            is FirebaseAuthInvalidCredentialsException -> "Incorrect email or password. Please try again."
            is FirebaseNetworkException -> "Network error. Check your internet connection and try again."
            is FirebaseTooManyRequestsException ->
                "Too many unsuccessful login attempts. Wait a minute or tap \"Forgot password?\" to reset."
            else -> {
                val msg = e.message.orEmpty()
                when {
                    msg.contains("INVALID_LOGIN_CREDENTIALS", ignoreCase = true) ||
                        msg.contains("wrong-password", ignoreCase = true) ||
                        msg.contains("invalid-credential", ignoreCase = true) ->
                        "Incorrect email or password. Please try again."
                    msg.contains("user-not-found", ignoreCase = true) ->
                        "No account found for this email address."
                    msg.contains("network", ignoreCase = true) ->
                        "Network error. Check your internet connection and try again."
                    msg.contains("too-many-requests", ignoreCase = true) ->
                        "Too many unsuccessful login attempts. Wait a minute or tap \"Forgot password?\" to reset."
                    else -> "Authentication failed. Please try again."
                }
            }
        }
    }
}
