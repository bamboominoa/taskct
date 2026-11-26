package com.example.data.telegram

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.data.model.TaskItem
import java.util.concurrent.TimeUnit

class ReminderScheduler(context: Context) {

    private val workManager = WorkManager.getInstance(context.applicationContext)

    fun scheduleTaskReminder(task: TaskItem) {
        if (task.id.isBlank()) return
        if (task.completed || !task.reminderEnabled || task.dueAtMillis <= 0L) {
            cancelTaskReminder(task.id)
            return
        }

        val now = System.currentTimeMillis()
        val triggerAt = task.triggerAtMillis
        val delayMillis = (triggerAt - now).coerceAtLeast(5_000L)

        // Avoid rescheduling reminders for tasks already overdue by more than 24h
        if (task.dueAtMillis < now - TimeUnit.HOURS.toMillis(24)) {
            cancelTaskReminder(task.id)
            return
        }

        val data = Data.Builder()
            .putString(TelegramReminderWorker.KEY_TASK_ID, task.id)
            .putString(TelegramReminderWorker.KEY_TITLE, task.title)
            .putString(TelegramReminderWorker.KEY_DESCRIPTION, task.description)
            .putString(TelegramReminderWorker.KEY_CATEGORY, task.category)
            .putString(TelegramReminderWorker.KEY_PRIORITY, task.priority)
            .putString(TelegramReminderWorker.KEY_SOURCE_TYPE, task.sourceType)
            .putString(TelegramReminderWorker.KEY_SOURCE_NAME, task.sourceName)
            .putLong(TelegramReminderWorker.KEY_DUE_AT_MILLIS, task.dueAtMillis)
            .putString(TelegramReminderWorker.KEY_SMART_TEXT, task.smartReminderText)
            .build()

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<TelegramReminderWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .setInputData(data)
            .setConstraints(constraints)
            .addTag(TAG_ALL_REMINDERS)
            .build()

        workManager.enqueueUniqueWork(
            uniqueWorkName(task.id),
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun cancelTaskReminder(taskId: String) {
        if (taskId.isBlank()) return
        workManager.cancelUniqueWork(uniqueWorkName(taskId))
    }

    private fun uniqueWorkName(taskId: String): String = "taskpulse_reminder_$taskId"

    companion object {
        private const val TAG_ALL_REMINDERS = "taskpulse_telegram_reminders"
    }
}
