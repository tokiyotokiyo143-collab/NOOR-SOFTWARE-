package com.example.data.remote

import android.content.Context
import com.example.data.backup.BackupJsonCodec
import com.example.data.model.*
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject

data class SyncStatusState(
    val isOnline: Boolean = true,
    val isSyncing: Boolean = false,
    val hasPendingWrites: Boolean = false,
    val cloudError: String? = null,
    val storageWarning: String? = null,
    val accessRevokedReason: String? = null,
    val lastSyncedAt: Long = 0L
)

class FirestoreSyncEngine(
    private val context: Context,
    private val onRemoteSettings: suspend (ShopSettings) -> Unit,
    private val onRemoteOwnerCosts: suspend (Map<String, Double>) -> Unit,
    private val onRemoteUsers: suspend (List<ShopUser>) -> Unit,
    private val onRemoteItems: suspend (List<StockItem>) -> Unit,
    private val onRemoteInvoices: suspend (List<Invoice>) -> Unit,
    private val onRemotePurchases: suspend (List<Purchase>) -> Unit,
    private val onRemoteParties: suspend (List<Party>) -> Unit,
    private val onRemotePayments: suspend (List<PaymentRecord>) -> Unit,
    private val onRemoteExpenses: suspend (List<ExpenseRecord>) -> Unit,
    private val onRemoteRepairs: suspend (List<RepairJob>) -> Unit,
    private val onRemoteBankAccounts: suspend (List<BankAccount>) -> Unit,
    private val onRemoteCashBankTxns: suspend (List<CashBankTxn>) -> Unit,
    private val onLog: (level: String, source: String, message: String) -> Unit
) {
    private val _status = MutableStateFlow(SyncStatusState())
    val status: StateFlow<SyncStatusState> = _status.asStateFlow()

    private val registrations = mutableListOf<ListenerRegistration>()
    private var currentUserEmail: String = ""
    private var currentUserUid: String = ""
    private var isOwnerOrCoOwner: Boolean = false

    fun setOnlineState(online: Boolean) {
        _status.value = _status.value.copy(isOnline = online)
    }

    fun clearWarnings() {
        _status.value = _status.value.copy(cloudError = null, storageWarning = null)
    }

    fun startListening(
        userEmail: String,
        userUid: String,
        ownerOrCoOwner: Boolean,
        scopeScopeCallback: (suspend () -> Unit) -> Unit
    ) {
        stopListening()
        currentUserEmail = userEmail.lowercase()
        currentUserUid = userUid
        isOwnerOrCoOwner = ownerOrCoOwner
        if (currentUserEmail.isBlank() || currentUserUid.isBlank()) return

        val db = try {
            FirebaseManager.firestore(context)
        } catch (e: Exception) {
            _status.value = _status.value.copy(
                cloudError = "Please check your Firebase configuration. ${e.message.orEmpty()}"
            )
            onLog("ERROR", "Firestore", "Please check your Firebase configuration: ${e.message}")
            return
        }

        // 1. shopConfig/main
        registrations += db.collection("shopConfig").document("main")
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    handleListenerError("shopConfig/main", err)
                    return@addSnapshotListener
                }
                if (snap != null && snap.exists()) {
                    _status.value = _status.value.copy(
                        hasPendingWrites = snap.metadata.hasPendingWrites(),
                        lastSyncedAt = System.currentTimeMillis(),
                        cloudError = null
                    )
                    val data = snap.data ?: return@addSnapshotListener
                    val parsed = BackupJsonCodec.settingsFromJson(mapToJsonObject(data))
                    scopeScopeCallback { onRemoteSettings(parsed) }
                }
            }

        // 2. ownerCosts/main (Owner / Co-Owner only)
        if (ownerOrCoOwner) {
            registrations += db.collection("ownerCosts").document("main")
                .addSnapshotListener { snap, err ->
                    if (err != null) {
                        // Do not block app if ownerCosts missing yet
                        onLog("WARN", "Firestore", "ownerCosts listener: ${err.message}")
                        return@addSnapshotListener
                    }
                    if (snap != null && snap.exists()) {
                        val costsMap = (snap.data?.get("costs") as? Map<*, *>)?.entries
                            ?.mapNotNull { (k, v) ->
                                val key = k?.toString() ?: return@mapNotNull null
                                val num = (v as? Number)?.toDouble() ?: return@mapNotNull null
                                key to num
                            }?.toMap() ?: emptyMap()
                        scopeScopeCallback { onRemoteOwnerCosts(costsMap) }
                    }
                }
        }

        // 3. users collection
        registrations += db.collection("users")
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    handleListenerError("users", err)
                    return@addSnapshotListener
                }
                if (snap != null) {
                    val users = snap.documents.mapNotNull { doc ->
                        doc.data?.let { data ->
                            val j = mapToJsonObject(data).apply {
                                if (!has("id")) put("id", doc.id)
                            }
                            BackupJsonCodec.userFromJson(j)
                        }
                    }
                    if (users.isNotEmpty()) {
                        scopeScopeCallback { onRemoteUsers(users) }
                    }
                }
            }

        // Helper for entity collections
        fun <T> listenCollection(
            name: String,
            mapper: (JSONObject) -> T,
            onData: suspend (List<T>) -> Unit
        ) {
            registrations += db.collection(name)
                .addSnapshotListener { snap, err ->
                    if (err != null) {
                        handleListenerError(name, err)
                        return@addSnapshotListener
                    }
                    if (snap != null) {
                        _status.value = _status.value.copy(
                            hasPendingWrites = snap.metadata.hasPendingWrites(),
                            lastSyncedAt = System.currentTimeMillis(),
                            cloudError = null
                        )
                        val list = snap.documents.mapNotNull { doc ->
                            doc.data?.let { d ->
                                val j = mapToJsonObject(d).apply { put("id", doc.id) }
                                mapper(j)
                            }
                        }
                        scopeScopeCallback { onData(list) }
                    }
                }
        }

        listenCollection("items", BackupJsonCodec::itemFromJson, onRemoteItems)
        listenCollection("invoices", BackupJsonCodec::invoiceFromJson, onRemoteInvoices)
        listenCollection("purchases", BackupJsonCodec::purchaseFromJson, onRemotePurchases)
        listenCollection("parties", BackupJsonCodec::partyFromJson, onRemoteParties)
        listenCollection("payments", BackupJsonCodec::paymentFromJson, onRemotePayments)
        listenCollection("expenses", BackupJsonCodec::expenseFromJson, onRemoteExpenses)
        listenCollection("repairs", BackupJsonCodec::repairFromJson, onRemoteRepairs)
        listenCollection("bankAccounts", BackupJsonCodec::bankAccountFromJson, onRemoteBankAccounts)
        listenCollection("cashBankTxns", BackupJsonCodec::cashBankTxnFromJson, onRemoteCashBankTxns)
    }

    private fun handleListenerError(collection: String, err: FirebaseFirestoreException) {
        val msg = err.message ?: "Unknown Firestore error"
        onLog("ERROR", "Firestore:$collection", msg)
        if (err.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
            val revoked = if (collection == "shopConfig/main") {
                "Access to shop configuration was revoked by the shop owner."
            } else {
                "Cloud sync blocked - check Firestore rules"
            }
            _status.value = _status.value.copy(
                cloudError = "Firestore Error: $msg",
                accessRevokedReason = if (collection == "shopConfig/main" && !currentUserEmail.equals(PRIMARY_OWNER_EMAIL, true)) {
                    "Access to shop configuration was revoked by the shop owner."
                } else null
            )
            if (_status.value.cloudError == null) {
                _status.value = _status.value.copy(cloudError = revoked)
            }
        } else {
            _status.value = _status.value.copy(cloudError = "Cloud sync error: $msg")
        }
    }

    fun stopListening() {
        registrations.forEach { runCatching { it.remove() } }
        registrations.clear()
    }

    private fun authMetadata(settings: ShopSettings, isNew: Boolean = false): Map<String, Any> {
        val allowedEmails = (listOf(PRIMARY_OWNER_EMAIL) +
            settings.users.filter { it.active }.map { it.email.lowercase() })
            .filter { it.isNotBlank() }
            .distinct()
        val allowedUids = settings.users.filter { it.active && it.uid.isNotBlank() }
            .map { it.uid }
            .plus(currentUserUid)
            .filter { it.isNotBlank() }
            .distinct()
        val map = mutableMapOf<String, Any>(
            "ownerEmail" to PRIMARY_OWNER_EMAIL,
            "allowedEmails" to allowedEmails,
            "allowedUids" to allowedUids,
            "updatedByEmail" to currentUserEmail,
            "serverUpdatedAt" to FieldValue.serverTimestamp()
        )
        if (isNew) {
            map["serverCreatedAt"] = FieldValue.serverTimestamp()
        }
        return map
    }

    suspend fun pushSettings(settings: ShopSettings) {
        if (currentUserEmail.isBlank()) return
        try {
            val json = BackupJsonCodec.settingsToJson(settings)
            val warnings = mutableListOf<String>()
            if (settings.logoDataUrl.toByteArray(Charsets.UTF_8).size > 200 * 1024) {
                json.put("logoDataUrl", "")
                warnings += "Shop logo exceeds 200KB limit and could not sync to the cloud."
            }
            if (settings.upiQrDataUrl.toByteArray(Charsets.UTF_8).size > 200 * 1024) {
                json.put("upiQrDataUrl", "")
                warnings += "UPI QR image exceeds 200KB limit and could not sync to the cloud."
            }
            _status.value = _status.value.copy(
                storageWarning = warnings.joinToString("\n").takeIf { it.isNotBlank() }
            )

            val payload = jsonObjectToMap(json).toMutableMap()
            payload.putAll(authMetadata(settings, isNew = false))
            FirebaseManager.firestore(context)
                .collection("shopConfig")
                .document("main")
                .set(payload, SetOptions.merge())
                .await()
        } catch (e: Exception) {
            onLog("ERROR", "Sync:Settings", "Save failed: ${e.message}")
            _status.value = _status.value.copy(cloudError = "Save failed: ${e.message}")
        }
    }

    suspend fun pushUser(user: ShopUser, settings: ShopSettings) {
        if (currentUserEmail.isBlank()) return
        try {
            val payload = jsonObjectToMap(BackupJsonCodec.userToJson(user)).toMutableMap()
            payload.putAll(authMetadata(settings))
            FirebaseManager.firestore(context)
                .collection("users")
                .document(user.id)
                .set(payload, SetOptions.merge())
                .await()
        } catch (e: Exception) {
            onLog("ERROR", "Sync:Users", "Sync failed: ${e.message}")
        }
    }

    suspend fun deleteUserDoc(userId: String) {
        if (currentUserEmail.isBlank()) return
        runCatching {
            FirebaseManager.firestore(context).collection("users").document(userId).delete().await()
        }
    }

    suspend fun pushItem(item: StockItem, settings: ShopSettings, ownerOrCoOwner: Boolean) {
        if (currentUserEmail.isBlank()) return
        try {
            // Owner-Only Privacy Rule: staff copies in items collection have costPrice = 0
            val sanitized = item.copy(costPrice = 0.0)
            val payload = jsonObjectToMap(BackupJsonCodec.itemToJson(sanitized)).toMutableMap()
            payload.putAll(authMetadata(settings))
            val db = FirebaseManager.firestore(context)
            db.collection("items").document(item.id).set(payload, SetOptions.merge()).await()

            if (ownerOrCoOwner) {
                val costPayload = mutableMapOf<String, Any>(
                    "costs" to mapOf(item.id to item.costPrice)
                )
                costPayload.putAll(authMetadata(settings))
                db.collection("ownerCosts").document("main").set(costPayload, SetOptions.merge()).await()
            }
        } catch (e: Exception) {
            onLog("ERROR", "Sync:Item", "Sync failed: ${e.message}")
        }
    }

    suspend fun pushAllOwnerCosts(items: List<StockItem>, settings: ShopSettings) {
        if (currentUserEmail.isBlank()) return
        runCatching {
            val costs = items.associate { it.id to it.costPrice }
            val payload = mutableMapOf<String, Any>("costs" to costs)
            payload.putAll(authMetadata(settings))
            FirebaseManager.firestore(context)
                .collection("ownerCosts")
                .document("main")
                .set(payload, SetOptions.merge())
                .await()
        }
    }

    suspend fun pushDoc(collection: String, id: String, json: JSONObject, settings: ShopSettings) {
        if (currentUserEmail.isBlank()) return
        try {
            val payload = jsonObjectToMap(json).toMutableMap()
            payload.putAll(authMetadata(settings))
            FirebaseManager.firestore(context)
                .collection(collection)
                .document(id)
                .set(payload, SetOptions.merge())
                .await()
        } catch (e: Exception) {
            onLog("ERROR", "Sync:$collection", "Sync failed: ${e.message}")
            _status.value = _status.value.copy(cloudError = "Sync failed: ${e.message}")
        }
    }

    suspend fun deleteDoc(collection: String, id: String) {
        if (currentUserEmail.isBlank()) return
        try {
            FirebaseManager.firestore(context)
                .collection(collection)
                .document(id)
                .delete()
                .await()
        } catch (e: Exception) {
            onLog("ERROR", "Sync:$collection", "Delete failed: ${e.message}")
        }
    }

    private fun jsonObjectToMap(obj: JSONObject): Map<String, Any> {
        val map = linkedMapOf<String, Any>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            val v = obj.opt(k)
            if (v != null && v != JSONObject.NULL) {
                map[k] = jsonValueToFirestore(v)
            }
        }
        return map
    }

    private fun jsonValueToFirestore(v: Any): Any = when (v) {
        is JSONObject -> jsonObjectToMap(v)
        is JSONArray -> (0 until v.length()).mapNotNull { i ->
            val elem = v.opt(i)
            if (elem == null || elem == JSONObject.NULL) null else jsonValueToFirestore(elem)
        }
        else -> v
    }

    private fun mapToJsonObject(map: Map<String, Any?>): JSONObject {
        val obj = JSONObject()
        map.forEach { (k, v) ->
            if (v != null) obj.put(k, firestoreToJsonValue(v))
        }
        return obj
    }

    private fun firestoreToJsonValue(v: Any): Any = when (v) {
        is Map<*, *> -> JSONObject().apply {
            v.forEach { (k, value) ->
                if (k != null && value != null) put(k.toString(), firestoreToJsonValue(value))
            }
        }
        is List<*> -> JSONArray().apply {
            v.forEach { elem ->
                if (elem != null) put(firestoreToJsonValue(elem))
            }
        }
        is com.google.firebase.Timestamp -> v.toDate().time
        else -> v
    }
}
