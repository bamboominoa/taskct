package com.example.data.telegram

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.R
import com.example.data.model.TaskItem
import com.example.data.model.TaskPriority
import com.example.data.model.TaskSourceType
import com.example.data.repository.TaskRepository

private const val WORKER_TAG = "TelegramReminderWorker"
private const val CHANNEL_ID = "taskpulse_smart_reminders"

class TelegramReminderWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val taskId = inputData.getString(KEY_TASK_ID).orEmpty()
        val title = inputData.getString(KEY_TITLE).orEmpty()
        if (title.isBlank()) return Result.success()

        val description = inputData.getString(KEY_DESCRIPTION).orEmpty()
        val category = inputData.getString(KEY_CATEGORY).orEmpty().ifBlank { "Công việc" }
        val priority = inputData.getString(KEY_PRIORITY).orEmpty().ifBlank { TaskPriority.MEDIUM.name }
        val sourceType = inputData.getString(KEY_SOURCE_TYPE).orEmpty().ifBlank { TaskSourceType.MANUAL.name }
        val sourceName = inputData.getString(KEY_SOURCE_NAME).orEmpty()
        val dueAtMillis = inputData.getLong(KEY_DUE_AT_MILLIS, 0L)
        val smartText = inputData.getString(KEY_SMART_TEXT).orEmpty()

        val taskSnapshot = TaskItem(
            id = taskId,
            title = title,
            description = description,
            priority = priority,
            category = category,
            sourceType = sourceType,
            sourceName = sourceName,
            dueAtMillis = dueAtMillis,
            smartReminderText = smartText
        )

        val prefsStore = TelegramPreferencesStore(applicationContext)
        val settings = prefsStore.getCurrentSettings()
        val telegramClient = TelegramBotClient()

        var sentTelegram = false
        if (settings.remindersEnabled && settings.isConfigured) {
            val html = TelegramBotClient.formatTaskReminderHtml(taskSnapshot)
            val sendResult = telegramClient.sendMessage(
                botToken = settings.botToken,
                chatId = settings.chatId,
                htmlMessage = html
            )
            if (sendResult.isSuccess) {
                sentTelegram = true
                if (taskId.isNotBlank()) {
                    runCatching {
                        TaskRepository(applicationContext).markTelegramNotified(taskId, true)
                    }
                }
            } else {
                Log.w(WORKER_TAG, "Telegram send failed: ${sendResult.exceptionOrNull()?.message}")
                if (runAttemptCount < 2) {
                    return Result.retry()
                }
            }
        }

        showLocalNotificationIfPermitted(
            title = title,
            body = smartText.ifBlank {
                if (sentTelegram) "Đã gửi nhắc nhở qua Telegram" else "Sắp đến hạn công việc này"
            },
            notificationId = taskId.hashCode()
        )

        return Result.success()
    }

    private fun showLocalNotificationIfPermitted(title: String, body: String, notificationId: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Nhắc việc thông minh TaskPulse",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Thông báo nhắc nhở công việc cùng Telegram"
            }
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("TaskPulse: $title")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        runCatching {
            NotificationManagerCompat.from(applicationContext).notify(notificationId, notification)
        }
    }

    companion object {
        const val KEY_TASK_ID = "task_id"
        const val KEY_TITLE = "title"
        const val KEY_DESCRIPTION = "description"
        const val KEY_CATEGORY = "category"
        const val KEY_PRIORITY = "priority"
        const val KEY_SOURCE_TYPE = "source_type"
        const val KEY_SOURCE_NAME = "source_name"
        const val KEY_DUE_AT_MILLIS = "due_at_millis"
        const val KEY_SMART_TEXT = "smart_text"
    }
}
