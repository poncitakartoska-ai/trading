package com.example.data

import android.content.Context
import android.util.Log
import com.example.R
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject

enum class OperationType(val value: String) {
    CREATE("create"),
    UPDATE("update"),
    DELETE("delete"),
    LIST("list"),
    GET("get"),
    WRITE("write"),
}

fun handleFirestoreError(exception: Exception, operationType: OperationType, path: String?): String {
    val auth = FirebaseAuth.getInstance()
    val currentUser = auth.currentUser

    val providerInfoList = currentUser?.providerData?.map { provider ->
        JSONObject().apply {
            put("providerId", provider.providerId)
            put("email", provider.email)
        }
    } ?: emptyList()

    val authInfoJson = JSONObject().apply {
        put("userId", currentUser?.uid)
        put("email", currentUser?.email)
        put("emailVerified", currentUser?.isEmailVerified)
        put("tenantId", currentUser?.tenantId)
        put("providerInfo", JSONArray(providerInfoList))
    }

    val errorInfoJson = JSONObject().apply {
        put("error", exception.message ?: exception.toString())
        put("operationType", operationType.value)
        put("path", path)
        put("authInfo", authInfoJson)
    }

    val jsonString = errorInfoJson.toString()
    Log.e("FirestoreError", "Firestore Error: $jsonString")
    return jsonString
}

@IgnoreExtraProperties
data class SignalLogCloud(
    @DocumentId val id: String = "",
    val userId: String = "",
    val ticker: String = "",
    val stockName: String = "",
    val signalType: String = "LONG",
    val entryPrice: Double = 0.0,
    val takeProfit: Double = 0.0,
    val stopLoss: Double = 0.0,
    val confidence: Long = 0L,
    val reason: String = "",
    val status: String = "ACTIVE",
    val pnlPercent: Double = 0.0,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ChatMessageCloud(
    @DocumentId val id: String = "",
    val userId: String = "",
    val role: String = "user",
    val text: String = "",
    val modelMode: String = "PRO_ARGON",
    val sourcesSummary: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

class CloudSignalAndChatRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth: FirebaseAuth
        get() = FirebaseAuth.getInstance()

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun observeUserSignals(userId: String = auth.currentUser?.uid.orEmpty()): Flow<List<SignalLogCloud>> = flow {
        val targetUid = userId.ifBlank { "unauthenticated" }
        val path = "users/$targetUid/signals"
        emitAll(
            db.collection("users").document(targetUid).collection("signals")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        SignalLogCloud::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    suspend fun createSignalLog(
        ticker: String,
        stockName: String,
        signalType: String,
        entryPrice: Double,
        takeProfit: Double,
        stopLoss: Double,
        confidence: Int,
        reason: String,
        status: String = "ACTIVE",
        pnlPercent: Double = 0.0
    ): Result<String> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val signalsRef = db.collection("users").document(uid).collection("signals")
        val docRef = signalsRef.document()
        val payload = mapOf(
            "userId" to uid,
            "ticker" to ticker.take(16),
            "stockName" to stockName.take(64),
            "signalType" to signalType,
            "entryPrice" to entryPrice,
            "takeProfit" to takeProfit,
            "stopLoss" to stopLoss,
            "confidence" to confidence.toLong().coerceIn(0L, 100L),
            "reason" to reason.take(300).ifBlank { "MOEX Signal" },
            "status" to status,
            "pnlPercent" to pnlPercent,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun getUserSignalsOnce(): Result<List<SignalLogCloud>> {
        val uid = auth.currentUser?.uid ?: "unauthenticated"
        val signalsRef = db.collection("users").document(uid).collection("signals")
        return try {
            val snapshot = signalsRef.get().await()
            val list = snapshot.toObjects(
                SignalLogCloud::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            Result.success(list)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, signalsRef.path)
            Result.failure(e)
        }
    }

    suspend fun getSignalById(targetUserId: String, signalId: String): Result<SignalLogCloud?> {
        val docRef = db.collection("users").document(targetUserId).collection("signals").document(signalId)
        return try {
            val snap = docRef.get().await()
            Result.success(snap.toObject(SignalLogCloud::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateSignalOutcome(
        signalId: String,
        newStatus: String,
        newPnlPercent: Double
    ): Result<Unit> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val docRef = db.collection("users").document(uid).collection("signals").document(signalId)
        val updates = mapOf(
            "status" to newStatus,
            "pnlPercent" to newPnlPercent,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    fun observeChatMessages(userId: String = auth.currentUser?.uid.orEmpty()): Flow<List<ChatMessageCloud>> = flow {
        val targetUid = userId.ifBlank { "unauthenticated" }
        val path = "users/$targetUid/chat_messages"
        emitAll(
            db.collection("users").document(targetUid).collection("chat_messages")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ChatMessageCloud::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedBy { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    suspend fun addChatMessage(
        role: String,
        text: String,
        modelMode: String,
        sourcesSummary: String = ""
    ): Result<String> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val colRef = db.collection("users").document(uid).collection("chat_messages")
        val docRef = colRef.document()
        val payload = mapOf(
            "userId" to uid,
            "role" to role,
            "text" to text.take(8000).ifBlank { "..." },
            "modelMode" to modelMode.take(64).ifBlank { "PRO_ARGON" },
            "sourcesSummary" to sourcesSummary.take(2000),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun clearChatHistory(): Result<Unit> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val colRef = db.collection("users").document(uid).collection("chat_messages")
        return try {
            val snap = colRef.get().await()
            for (doc in snap.documents) {
                doc.reference.delete().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, colRef.path)
            Result.failure(e)
        }
    }
}
