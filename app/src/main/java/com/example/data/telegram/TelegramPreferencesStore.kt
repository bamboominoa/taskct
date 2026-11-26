package com.example.data.telegram

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.TelegramSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.telegramDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "taskpulse_telegram_prefs"
)

class TelegramPreferencesStore(private val context: Context) {

    private object Keys {
        val BOT_TOKEN = stringPreferencesKey("bot_token")
        val CHAT_ID = stringPreferencesKey("chat_id")
        val BOT_USERNAME = stringPreferencesKey("bot_username")
        val REMINDERS_ENABLED = booleanPreferencesKey("reminders_enabled")
        val DEFAULT_LEAD_MINUTES = intPreferencesKey("default_lead_minutes")
        val DAILY_DIGEST_ENABLED = booleanPreferencesKey("daily_digest_enabled")
        val DAILY_DIGEST_HOUR = intPreferencesKey("daily_digest_hour")
        val DAILY_DIGEST_MINUTE = intPreferencesKey("daily_digest_minute")
        val AUTO_ADD_EXTRACTED = booleanPreferencesKey("auto_add_extracted_tasks")
    }

    val settingsFlow: Flow<TelegramSettings> = context.telegramDataStore.data.map { prefs ->
        TelegramSettings(
            botToken = prefs[Keys.BOT_TOKEN].orEmpty(),
            chatId = prefs[Keys.CHAT_ID].orEmpty(),
            botUsername = prefs[Keys.BOT_USERNAME].orEmpty(),
            remindersEnabled = prefs[Keys.REMINDERS_ENABLED] ?: true,
            defaultLeadMinutes = prefs[Keys.DEFAULT_LEAD_MINUTES] ?: 30,
            dailyDigestEnabled = prefs[Keys.DAILY_DIGEST_ENABLED] ?: false,
            dailyDigestHour = prefs[Keys.DAILY_DIGEST_HOUR] ?: 8,
            dailyDigestMinute = prefs[Keys.DAILY_DIGEST_MINUTE] ?: 0,
            autoAddExtractedTasks = prefs[Keys.AUTO_ADD_EXTRACTED] ?: true
        )
    }

    suspend fun getCurrentSettings(): TelegramSettings = settingsFlow.first()

    suspend fun saveCredentials(botToken: String, chatId: String, botUsername: String = "") {
        context.telegramDataStore.edit { prefs ->
            prefs[Keys.BOT_TOKEN] = botToken.trim()
            prefs[Keys.CHAT_ID] = chatId.trim()
            if (botUsername.isNotBlank()) {
                prefs[Keys.BOT_USERNAME] = botUsername.trim()
            }
        }
    }

    suspend fun updatePreferences(
        remindersEnabled: Boolean? = null,
        defaultLeadMinutes: Int? = null,
        dailyDigestEnabled: Boolean? = null,
        dailyDigestHour: Int? = null,
        dailyDigestMinute: Int? = null,
        autoAddExtractedTasks: Boolean? = null
    ) {
        context.telegramDataStore.edit { prefs ->
            remindersEnabled?.let { prefs[Keys.REMINDERS_ENABLED] = it }
            defaultLeadMinutes?.let { prefs[Keys.DEFAULT_LEAD_MINUTES] = it }
            dailyDigestEnabled?.let { prefs[Keys.DAILY_DIGEST_ENABLED] = it }
            dailyDigestHour?.let { prefs[Keys.DAILY_DIGEST_HOUR] = it }
            dailyDigestMinute?.let { prefs[Keys.DAILY_DIGEST_MINUTE] = it }
            autoAddExtractedTasks?.let { prefs[Keys.AUTO_ADD_EXTRACTED] = it }
        }
    }
}
