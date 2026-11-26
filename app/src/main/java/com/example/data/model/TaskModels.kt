package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class TaskPriority(val labelVi: String, val emoji: String) {
    HIGH("Cao", "🔴"),
    MEDIUM("Trung bình", "🟡"),
    LOW("Thấp", "🟢");

    companion object {
        fun fromRaw(raw: String?): TaskPriority =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: MEDIUM
    }
}

enum class TaskSourceType(val labelVi: String) {
    MANUAL("Thủ công"),
    FILE("Tệp AI"),
    PHOTO("Ảnh AI");

    companion object {
        fun fromRaw(raw: String?): TaskSourceType =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: MANUAL
    }
}

@Keep
@IgnoreExtraProperties
data class TaskItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val description: String = "",
    val priority: String = TaskPriority.MEDIUM.name,
    val category: String = "Công việc",
    val sourceType: String = TaskSourceType.MANUAL.name,
    val sourceName: String = "",
    val completed: Boolean = false,
    val dueAtMillis: Long = 0L,
    val reminderMinutesBefore: Long = 30L,
    val reminderEnabled: Boolean = true,
    val telegramNotified: Boolean = false,
    val smartReminderText: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val priorityEnum: TaskPriority
        get() = TaskPriority.fromRaw(priority)

    val sourceEnum: TaskSourceType
        get() = TaskSourceType.fromRaw(sourceType)

    val hasDeadline: Boolean
        get() = dueAtMillis > 0L

    val triggerAtMillis: Long
        get() = if (dueAtMillis > 0L) {
            (dueAtMillis - reminderMinutesBefore * 60_000L).coerceAtLeast(0L)
        } else 0L
}

@Keep
@IgnoreExtraProperties
data class AiScanRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val sourceType: String = TaskSourceType.FILE.name,
    val sourceName: String = "",
    val summary: String = "",
    val extractedCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    val sourceEnum: TaskSourceType
        get() = TaskSourceType.fromRaw(sourceType)
}

data class ExtractedTaskDraft(
    val title: String,
    val description: String,
    val priority: TaskPriority,
    val category: String,
    val dueAtMillis: Long,
    val reminderMinutesBefore: Long,
    val smartReminderText: String,
    val selected: Boolean = true
)

data class AiExtractionResult(
    val sourceType: TaskSourceType,
    val sourceName: String,
    val summary: String,
    val tasks: List<ExtractedTaskDraft>,
    val autoAddedCount: Int = 0
)

data class TelegramSettings(
    val botToken: String = "",
    val chatId: String = "",
    val botUsername: String = "",
    val remindersEnabled: Boolean = true,
    val defaultLeadMinutes: Int = 30,
    val dailyDigestEnabled: Boolean = false,
    val dailyDigestHour: Int = 8,
    val dailyDigestMinute: Int = 0,
    val autoAddExtractedTasks: Boolean = true
) {
    val isConfigured: Boolean
        get() = botToken.isNotBlank() && chatId.isNotBlank()
}

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

sealed interface AiScanUiState {
    data object Idle : AiScanUiState
    data class Analyzing(val sourceName: String, val isPhoto: Boolean) : AiScanUiState
    data class ResultReady(val result: AiExtractionResult) : AiScanUiState
    data class Error(val message: String) : AiScanUiState
}
