package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.firebase.OperationType
import com.example.data.firebase.handleFirestoreError
import com.example.data.model.AiScanRecord
import com.example.data.model.TaskItem
import com.example.data.model.TaskPriority
import com.example.data.model.TaskSourceType
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class TaskRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Vui lòng đăng nhập Google trước khi truy cập dữ liệu.")
    }

    private fun tasksCollection(uid: String) =
        db.collection("users").document(uid).collection("tasks")

    private fun scansCollection(uid: String) =
        db.collection("users").document(uid).collection("ai_scans")

    fun observeTasks(userId: String): Flow<List<TaskItem>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Phiên đăng nhập không khớp." }
        val path = "users/$uid/tasks"
        emitAll(
            tasksCollection(uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        TaskItem::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedWith(
                        compareBy<TaskItem> { it.completed }
                            .thenBy { if (it.dueAtMillis > 0L) 0 else 1 }
                            .thenBy { if (it.dueAtMillis > 0L) it.dueAtMillis else Long.MAX_VALUE }
                            .thenBy { priorityRank(it.priorityEnum) }
                            .thenByDescending { it.createdAt?.seconds ?: 0L }
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeScans(userId: String): Flow<List<AiScanRecord>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Phiên đăng nhập không khớp." }
        val path = "users/$uid/ai_scans"
        emitAll(
            scansCollection(uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        AiScanRecord::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun addTask(
        title: String,
        description: String = "",
        priority: TaskPriority = TaskPriority.MEDIUM,
        category: String = "Công việc",
        sourceType: TaskSourceType = TaskSourceType.MANUAL,
        sourceName: String = "",
        dueAtMillis: Long = 0L,
        reminderMinutesBefore: Long = 30L,
        reminderEnabled: Boolean = true,
        smartReminderText: String = ""
    ): Result<TaskItem> {
        val safeTitle = title.trim().take(200)
        if (safeTitle.isEmpty()) {
            return Result.failure(IllegalArgumentException("Tên công việc không được để trống."))
        }
        val safeCategory = category.trim().ifEmpty { "Công việc" }.take(50)
        val safeDescription = description.trim().take(2000)
        val safeSourceName = sourceName.trim().take(200)
        val safeReminderText = smartReminderText.trim().take(500)
        val safeDue = dueAtMillis.coerceIn(0L, 4102444800000L)
        val safeLead = reminderMinutesBefore.coerceIn(0L, 10080L)

        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/tasks"
        return try {
            val uid = requireUserId()
            val docRef = tasksCollection(uid).document()
            docPath = docRef.path

            val payload = buildMap<String, Any> {
                put("userId", uid)
                put("title", safeTitle)
                if (safeDescription.isNotEmpty()) put("description", safeDescription)
                put("priority", priority.name)
                put("category", safeCategory)
                put("sourceType", sourceType.name)
                if (safeSourceName.isNotEmpty()) put("sourceName", safeSourceName)
                put("completed", false)
                put("dueAtMillis", safeDue)
                put("reminderMinutesBefore", safeLead)
                put("reminderEnabled", reminderEnabled)
                put("telegramNotified", false)
                if (safeReminderText.isNotEmpty()) put("smartReminderText", safeReminderText)
                put("createdAt", FieldValue.serverTimestamp())
                put("updatedAt", FieldValue.serverTimestamp())
            }

            docRef.set(payload).await()
            Result.success(
                TaskItem(
                    id = docRef.id,
                    userId = uid,
                    title = safeTitle,
                    description = safeDescription,
                    priority = priority.name,
                    category = safeCategory,
                    sourceType = sourceType.name,
                    sourceName = safeSourceName,
                    completed = false,
                    dueAtMillis = safeDue,
                    reminderMinutesBefore = safeLead,
                    reminderEnabled = reminderEnabled,
                    telegramNotified = false,
                    smartReminderText = safeReminderText
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun toggleTaskCompleted(taskId: String, completed: Boolean): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/tasks/$taskId"
        return try {
            val uid = requireUserId()
            require(taskId.isNotBlank()) { "Thiếu mã công việc." }
            val docRef = tasksCollection(uid).document(taskId)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "completed" to completed,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun markTelegramNotified(taskId: String, notified: Boolean): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/tasks/$taskId"
        return try {
            val uid = requireUserId()
            require(taskId.isNotBlank()) { "Thiếu mã công việc." }
            val docRef = tasksCollection(uid).document(taskId)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "telegramNotified" to notified,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateTask(
        taskId: String,
        title: String,
        description: String,
        priority: TaskPriority,
        category: String,
        dueAtMillis: Long,
        reminderMinutesBefore: Long,
        reminderEnabled: Boolean,
        smartReminderText: String,
        resetNotified: Boolean
    ): Result<Unit> {
        val safeTitle = title.trim().take(200)
        if (safeTitle.isEmpty()) {
            return Result.failure(IllegalArgumentException("Tên công việc không được để trống."))
        }
        val safeCategory = category.trim().ifEmpty { "Công việc" }.take(50)
        val safeDescription = description.trim().take(2000)
        val safeReminderText = smartReminderText.trim().take(500)
        val safeDue = dueAtMillis.coerceIn(0L, 4102444800000L)
        val safeLead = reminderMinutesBefore.coerceIn(0L, 10080L)

        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/tasks/$taskId"
        return try {
            val uid = requireUserId()
            require(taskId.isNotBlank()) { "Thiếu mã công việc." }
            val docRef = tasksCollection(uid).document(taskId)
            docPath = docRef.path

            val updates = buildMap<String, Any> {
                put("title", safeTitle)
                put("description", safeDescription)
                put("priority", priority.name)
                put("category", safeCategory)
                put("dueAtMillis", safeDue)
                put("reminderMinutesBefore", safeLead)
                put("reminderEnabled", reminderEnabled)
                put("smartReminderText", safeReminderText)
                if (resetNotified) put("telegramNotified", false)
                put("updatedAt", FieldValue.serverTimestamp())
            }
            docRef.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteTask(taskId: String): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/tasks/$taskId"
        return try {
            val uid = requireUserId()
            require(taskId.isNotBlank()) { "Thiếu mã công việc." }
            val docRef = tasksCollection(uid).document(taskId)
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun addScanRecord(
        sourceType: TaskSourceType,
        sourceName: String,
        summary: String,
        extractedCount: Int
    ): Result<Unit> {
        val validSource = if (sourceType == TaskSourceType.PHOTO) "PHOTO" else "FILE"
        val safeName = sourceName.trim().ifEmpty { "Tài liệu" }.take(200)
        val safeSummary = summary.trim().ifEmpty { "Đã phân tích tài liệu bằng AI." }.take(1000)
        val safeCount = extractedCount.coerceIn(0, 50).toLong()

        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/ai_scans"
        return try {
            val uid = requireUserId()
            val docRef = scansCollection(uid).document()
            docPath = docRef.path
            val payload = mapOf(
                "userId" to uid,
                "sourceType" to validSource,
                "sourceName" to safeName,
                "summary" to safeSummary,
                "extractedCount" to safeCount,
                "createdAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteScanRecord(scanId: String): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/ai_scans/$scanId"
        return try {
            val uid = requireUserId()
            require(scanId.isNotBlank()) { "Thiếu mã lịch sử quét." }
            val docRef = scansCollection(uid).document(scanId)
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    private fun priorityRank(priority: TaskPriority): Int = when (priority) {
        TaskPriority.HIGH -> 0
        TaskPriority.MEDIUM -> 1
        TaskPriority.LOW -> 2
    }
}
