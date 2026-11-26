package com.example.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.AiTaskExtractor
import com.example.data.model.AiExtractionResult
import com.example.data.model.AiScanRecord
import com.example.data.model.AiScanUiState
import com.example.data.model.ExtractedTaskDraft
import com.example.data.model.TaskItem
import com.example.data.model.TaskPriority
import com.example.data.model.TaskSourceType
import com.example.data.model.TelegramSettings
import com.example.data.model.UiState
import com.example.data.repository.TaskRepository
import com.example.data.telegram.ReminderScheduler
import com.example.data.telegram.TelegramBotClient
import com.example.data.telegram.TelegramPreferencesStore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val TAG = "TaskPulseVM"
private const val STOP_TIMEOUT_MS = 5000L

enum class TaskFilter(val labelVi: String) {
    ALL("Tất cả"),
    TODAY("Hôm nay"),
    HIGH("Ưu tiên cao"),
    PENDING("Đang làm"),
    DONE("Đã xong")
}

class TaskPulseViewModel(
    private val repository: TaskRepository,
    private val aiExtractor: AiTaskExtractor,
    private val telegramPrefs: TelegramPreferencesStore,
    private val telegramClient: TelegramBotClient,
    private val reminderScheduler: ReminderScheduler,
    private val currentUserId: String
) : ViewModel() {

    val tasksState: StateFlow<UiState<List<TaskItem>>> = repository.observeTasks(currentUserId)
        .map<List<TaskItem>, UiState<List<TaskItem>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing tasks", rootError)
            emit(UiState.Error(rootError.message ?: "Không thể tải danh sách công việc."))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = UiState.Loading
        )

    val scansState: StateFlow<List<AiScanRecord>> = repository.observeScans(currentUserId)
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            Log.w(TAG, "Error observing scans", firestoreError ?: error)
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = emptyList()
        )

    val telegramSettings: StateFlow<TelegramSettings> = telegramPrefs.settingsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = TelegramSettings()
        )

    private val _selectedFilter = MutableStateFlow(TaskFilter.ALL)
    val selectedFilter: StateFlow<TaskFilter> = _selectedFilter.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _aiScanState = MutableStateFlow<AiScanUiState>(AiScanUiState.Idle)
    val aiScanState: StateFlow<AiScanUiState> = _aiScanState.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    private val _telegramBusy = MutableStateFlow(false)
    val telegramBusy: StateFlow<Boolean> = _telegramBusy.asStateFlow()

    private val _telegramFeedback = MutableStateFlow<String?>(null)
    val telegramFeedback: StateFlow<String?> = _telegramFeedback.asStateFlow()

    fun setFilter(filter: TaskFilter) {
        _selectedFilter.value = filter
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun clearToast() {
        _toastMessage.value = null
    }

    fun clearTelegramFeedback() {
        _telegramFeedback.value = null
    }

    fun resetAiScan() {
        _aiScanState.value = AiScanUiState.Idle
    }

    fun addManualTask(
        title: String,
        description: String,
        priority: TaskPriority,
        category: String,
        dueAtMillis: Long,
        reminderMinutesBefore: Long,
        reminderEnabled: Boolean,
        smartReminderText: String
    ) {
        viewModelScope.launch {
            val result = repository.addTask(
                title = title,
                description = description,
                priority = priority,
                category = category,
                sourceType = TaskSourceType.MANUAL,
                dueAtMillis = dueAtMillis,
                reminderMinutesBefore = reminderMinutesBefore,
                reminderEnabled = reminderEnabled,
                smartReminderText = smartReminderText
            )
            result.onSuccess { created ->
                if (created.reminderEnabled && created.dueAtMillis > 0L) {
                    reminderScheduler.scheduleTaskReminder(created)
                }
                _toastMessage.value = "Đã thêm công việc \"${created.title}\""
            }.onFailure { err ->
                _toastMessage.value = err.message ?: "Không thể thêm công việc."
            }
        }
    }

    fun updateTask(
        taskId: String,
        title: String,
        description: String,
        priority: TaskPriority,
        category: String,
        dueAtMillis: Long,
        reminderMinutesBefore: Long,
        reminderEnabled: Boolean,
        smartReminderText: String,
        originalDueMillis: Long
    ) {
        viewModelScope.launch {
            val resetNotified = dueAtMillis != originalDueMillis
            val result = repository.updateTask(
                taskId = taskId,
                title = title,
                description = description,
                priority = priority,
                category = category,
                dueAtMillis = dueAtMillis,
                reminderMinutesBefore = reminderMinutesBefore,
                reminderEnabled = reminderEnabled,
                smartReminderText = smartReminderText,
                resetNotified = resetNotified
            )
            result.onSuccess {
                val updatedTask = TaskItem(
                    id = taskId,
                    title = title,
                    description = description,
                    priority = priority.name,
                    category = category,
                    dueAtMillis = dueAtMillis,
                    reminderMinutesBefore = reminderMinutesBefore,
                    reminderEnabled = reminderEnabled,
                    smartReminderText = smartReminderText
                )
                if (reminderEnabled && dueAtMillis > 0L) {
                    reminderScheduler.scheduleTaskReminder(updatedTask)
                } else {
                    reminderScheduler.cancelTaskReminder(taskId)
                }
                _toastMessage.value = "Đã cập nhật công việc."
            }.onFailure { err ->
                _toastMessage.value = err.message ?: "Không thể cập nhật công việc."
            }
        }
    }

    fun toggleTaskCompleted(task: TaskItem) {
        viewModelScope.launch {
            val nextState = !task.completed
            val result = repository.toggleTaskCompleted(task.id, nextState)
            result.onSuccess {
                if (nextState) {
                    reminderScheduler.cancelTaskReminder(task.id)
                } else if (task.reminderEnabled && task.dueAtMillis > 0L) {
                    reminderScheduler.scheduleTaskReminder(task.copy(completed = false))
                }
            }.onFailure { err ->
                _toastMessage.value = err.message ?: "Không thể cập nhật trạng thái."
            }
        }
    }

    fun deleteTask(taskId: String) {
        viewModelScope.launch {
            val result = repository.deleteTask(taskId)
            result.onSuccess {
                reminderScheduler.cancelTaskReminder(taskId)
                _toastMessage.value = "Đã xóa công việc."
            }.onFailure { err ->
                _toastMessage.value = err.message ?: "Không thể xóa công việc."
            }
        }
    }

    fun analyzePhoto(bitmap: Bitmap, sourceName: String, extraInstruction: String) {
        viewModelScope.launch {
            _aiScanState.value = AiScanUiState.Analyzing(sourceName = sourceName, isPhoto = true)
            val settings = telegramSettings.value
            val extractionResult = aiExtractor.analyzePhoto(
                bitmap = bitmap,
                sourceName = sourceName,
                extraInstruction = extraInstruction,
                defaultLeadMinutes = settings.defaultLeadMinutes
            )
            handleExtractionResult(extractionResult, settings)
        }
    }

    fun analyzeFile(
        context: Context,
        uri: Uri,
        fileName: String,
        mimeType: String,
        extraInstruction: String
    ) {
        viewModelScope.launch {
            _aiScanState.value = AiScanUiState.Analyzing(sourceName = fileName, isPhoto = false)
            val settings = telegramSettings.value
            val extractionResult = aiExtractor.analyzeFile(
                context = context,
                uri = uri,
                fileName = fileName,
                mimeType = mimeType,
                extraInstruction = extraInstruction,
                defaultLeadMinutes = settings.defaultLeadMinutes
            )
            handleExtractionResult(extractionResult, settings)
        }
    }

    private suspend fun handleExtractionResult(
        result: Result<AiExtractionResult>,
        settings: TelegramSettings
    ) {
        result.onSuccess { extracted ->
            var autoAdded = 0
            if (settings.autoAddExtractedTasks && extracted.tasks.isNotEmpty()) {
                autoAdded = persistDraftsAndSchedule(
                    drafts = extracted.tasks,
                    sourceType = extracted.sourceType,
                    sourceName = extracted.sourceName,
                    remindersEnabled = settings.remindersEnabled
                )
            }

            repository.addScanRecord(
                sourceType = extracted.sourceType,
                sourceName = extracted.sourceName,
                summary = extracted.summary,
                extractedCount = extracted.tasks.size
            )

            _aiScanState.value = AiScanUiState.ResultReady(
                extracted.copy(autoAddedCount = autoAdded)
            )

            if (autoAdded > 0) {
                _toastMessage.value = "AI đã tự động thêm $autoAdded công việc và đặt nhắc nhở!"
            } else if (extracted.tasks.isEmpty()) {
                _toastMessage.value = "AI không tìm thấy công việc cụ thể nào trong tài liệu này."
            }
        }.onFailure { err ->
            _aiScanState.value = AiScanUiState.Error(
                err.message ?: "Phân tích AI thất bại. Vui lòng thử lại."
            )
        }
    }

    fun toggleDraftSelection(index: Int) {
        val current = _aiScanState.value as? AiScanUiState.ResultReady ?: return
        val updatedList = current.result.tasks.mapIndexed { i, draft ->
            if (i == index) draft.copy(selected = !draft.selected) else draft
        }
        _aiScanState.value = AiScanUiState.ResultReady(current.result.copy(tasks = updatedList))
    }

    fun commitSelectedDrafts() {
        val current = _aiScanState.value as? AiScanUiState.ResultReady ?: return
        val selectedDrafts = current.result.tasks.filter { it.selected }
        if (selectedDrafts.isEmpty()) {
            _toastMessage.value = "Hãy chọn ít nhất 1 công việc để thêm."
            return
        }
        viewModelScope.launch {
            val addedCount = persistDraftsAndSchedule(
                drafts = selectedDrafts,
                sourceType = current.result.sourceType,
                sourceName = current.result.sourceName,
                remindersEnabled = telegramSettings.value.remindersEnabled
            )
            _aiScanState.value = AiScanUiState.ResultReady(
                current.result.copy(autoAddedCount = current.result.autoAddedCount + addedCount)
            )
            _toastMessage.value = "Đã thêm $addedCount công việc vào danh sách!"
        }
    }

    private suspend fun persistDraftsAndSchedule(
        drafts: List<ExtractedTaskDraft>,
        sourceType: TaskSourceType,
        sourceName: String,
        remindersEnabled: Boolean
    ): Int {
        var added = 0
        for (draft in drafts) {
            val res = repository.addTask(
                title = draft.title,
                description = draft.description,
                priority = draft.priority,
                category = draft.category,
                sourceType = sourceType,
                sourceName = sourceName,
                dueAtMillis = draft.dueAtMillis,
                reminderMinutesBefore = draft.reminderMinutesBefore,
                reminderEnabled = remindersEnabled && draft.dueAtMillis > 0L,
                smartReminderText = draft.smartReminderText
            )
            res.onSuccess { created ->
                added++
                if (created.reminderEnabled && created.dueAtMillis > 0L) {
                    reminderScheduler.scheduleTaskReminder(created)
                }
            }
        }
        return added
    }

    fun deleteScanRecord(scanId: String) {
        viewModelScope.launch {
            repository.deleteScanRecord(scanId)
        }
    }

    // --- Telegram Smart Reminders ---

    fun saveTelegramConfig(botToken: String, chatId: String) {
        viewModelScope.launch {
            telegramPrefs.saveCredentials(botToken = botToken, chatId = chatId)
            _telegramFeedback.value = "Đã lưu cấu hình Telegram trên thiết bị."
        }
    }

    fun detectTelegramChatId(botToken: String, onChatDetected: (String) -> Unit) {
        viewModelScope.launch {
            _telegramBusy.value = true
            _telegramFeedback.value = null
            val botCheck = telegramClient.verifyBotToken(botToken)
            val botUsername = botCheck.getOrNull()?.username.orEmpty()
            val result = telegramClient.detectLatestChatId(botToken)
            _telegramBusy.value = false

            result.onSuccess { detected ->
                telegramPrefs.saveCredentials(
                    botToken = botToken,
                    chatId = detected.chatId,
                    botUsername = botUsername
                )
                onChatDetected(detected.chatId)
                _telegramFeedback.value =
                    "Đã tìm thấy Chat ID: ${detected.chatId} (${detected.displayName})"
            }.onFailure { err ->
                _telegramFeedback.value = err.message ?: "Không thể lấy Chat ID."
            }
        }
    }

    fun testTelegramConnection(botToken: String, chatId: String) {
        viewModelScope.launch {
            _telegramBusy.value = true
            _telegramFeedback.value = null

            val botInfoResult = telegramClient.verifyBotToken(botToken)
            if (botInfoResult.isFailure) {
                _telegramBusy.value = false
                _telegramFeedback.value =
                    botInfoResult.exceptionOrNull()?.message ?: "Bot Token không hợp lệ."
                return@launch
            }
            val botInfo = botInfoResult.getOrThrow()
            telegramPrefs.saveCredentials(
                botToken = botToken,
                chatId = chatId,
                botUsername = botInfo.username
            )

            val sendResult = telegramClient.sendMessage(
                botToken = botToken,
                chatId = chatId,
                htmlMessage = TelegramBotClient.formatTestMessageHtml(botInfo.username)
            )
            _telegramBusy.value = false

            sendResult.onSuccess {
                _telegramFeedback.value =
                    "Kết nối thành công với @${botInfo.username}! Đã gửi tin nhắn thử tới Telegram."
                _toastMessage.value = "Đã gửi tin nhắn thử tới Telegram!"
            }.onFailure { err ->
                _telegramFeedback.value = err.message ?: "Gửi tin nhắn thử thất bại."
            }
        }
    }

    fun sendInstantTaskReminder(task: TaskItem) {
        viewModelScope.launch {
            val settings = telegramSettings.value
            if (!settings.isConfigured) {
                _toastMessage.value = "Vui lòng cấu hình Bot Token & Chat ID ở tab Telegram trước."
                return@launch
            }
            _telegramBusy.value = true
            val html = TelegramBotClient.formatTaskReminderHtml(task)
            val result = telegramClient.sendMessage(
                botToken = settings.botToken,
                chatId = settings.chatId,
                htmlMessage = html
            )
            _telegramBusy.value = false

            result.onSuccess {
                repository.markTelegramNotified(task.id, true)
                _toastMessage.value = "Đã gửi nhắc nhở \"${task.title}\" qua Telegram!"
            }.onFailure { err ->
                _toastMessage.value = err.message ?: "Không thể gửi tin Telegram."
            }
        }
    }

    fun sendAiDailyBriefing() {
        viewModelScope.launch {
            val settings = telegramSettings.value
            if (!settings.isConfigured) {
                _telegramFeedback.value = "Vui lòng nhập Bot Token và Chat ID trước khi gửi tóm tắt AI."
                return@launch
            }
            val allTasks = (tasksState.value as? UiState.Success)?.data.orEmpty()
            val pending = allTasks.filter { !it.completed }
            val completedCount = allTasks.count { it.completed }

            _telegramBusy.value = true
            _telegramFeedback.value = "AI đang tổng hợp danh sách công việc..."
            val briefingResult = aiExtractor.generateDailyTelegramBriefing(pending, completedCount)

            if (briefingResult.isFailure) {
                _telegramBusy.value = false
                _telegramFeedback.value =
                    briefingResult.exceptionOrNull()?.message ?: "Không thể tạo bản tóm tắt AI."
                return@launch
            }

            val messageHtml = briefingResult.getOrThrow()
            val sendResult = telegramClient.sendMessage(
                botToken = settings.botToken,
                chatId = settings.chatId,
                htmlMessage = messageHtml
            )
            _telegramBusy.value = false

            sendResult.onSuccess {
                _telegramFeedback.value = "Đã gửi bản tóm tắt thông minh AI qua Telegram!"
                _toastMessage.value = "Đã gửi tóm tắt công việc qua Telegram!"
            }.onFailure { err ->
                _telegramFeedback.value = err.message ?: "Gửi Telegram thất bại."
            }
        }
    }

    fun updateRemindersEnabled(enabled: Boolean) {
        viewModelScope.launch {
            telegramPrefs.updatePreferences(remindersEnabled = enabled)
        }
    }

    fun updateDefaultLeadMinutes(minutes: Int) {
        viewModelScope.launch {
            telegramPrefs.updatePreferences(defaultLeadMinutes = minutes)
        }
    }

    fun updateAutoAddExtracted(enabled: Boolean) {
        viewModelScope.launch {
            telegramPrefs.updatePreferences(autoAddExtractedTasks = enabled)
        }
    }
}
