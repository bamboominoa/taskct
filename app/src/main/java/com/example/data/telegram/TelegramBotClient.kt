package com.example.data.telegram

import android.util.Log
import com.example.data.model.TaskItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

private const val TAG = "TelegramBotClient"
private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

data class TelegramBotInfo(
    val username: String,
    val firstName: String
)

data class DetectedChat(
    val chatId: String,
    val displayName: String
)

class TelegramBotClient(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()
) {

    suspend fun verifyBotToken(botToken: String): Result<TelegramBotInfo> = withContext(Dispatchers.IO) {
        val cleanToken = botToken.trim()
        if (cleanToken.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Vui lòng nhập Bot Token."))
        }
        try {
            val request = Request.Builder()
                .url("https://api.telegram.org/bot$cleanToken/getMe")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                val json = JSONObject(body)
                if (response.isSuccessful && json.optBoolean("ok", false)) {
                    val result = json.getJSONObject("result")
                    Result.success(
                        TelegramBotInfo(
                            username = result.optString("username", ""),
                            firstName = result.optString("first_name", "TaskPulse Bot")
                        )
                    )
                } else {
                    val desc = json.optString("description", "Bot Token không hợp lệ.")
                    Result.failure(Exception(desc))
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "verifyBotToken failed", e)
            Result.failure(Exception("Lỗi kết nối Telegram: ${e.localizedMessage ?: "Không xác định"}"))
        }
    }

    suspend fun detectLatestChatId(botToken: String): Result<DetectedChat> = withContext(Dispatchers.IO) {
        val cleanToken = botToken.trim()
        if (cleanToken.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Vui lòng nhập Bot Token trước."))
        }
        try {
            val request = Request.Builder()
                .url("https://api.telegram.org/bot$cleanToken/getUpdates?limit=20")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                val json = JSONObject(body)
                if (!response.isSuccessful || !json.optBoolean("ok", false)) {
                    val desc = json.optString("description", "Không thể đọc tin nhắn từ bot.")
                    return@withContext Result.failure(Exception(desc))
                }

                val updates = json.optJSONArray("result")
                if (updates == null || updates.length() == 0) {
                    return@withContext Result.failure(
                        Exception("Chưa có tin nhắn nào. Hãy mở Telegram, gửi /start cho bot rồi bấm lại nút này.")
                    )
                }

                for (i in updates.length() - 1 downTo 0) {
                    val item = updates.optJSONObject(i) ?: continue
                    val msg = item.optJSONObject("message")
                        ?: item.optJSONObject("channel_post")
                        ?: item.optJSONObject("my_chat_member")
                        ?: continue
                    val chat = msg.optJSONObject("chat") ?: continue
                    val chatId = chat.optLong("id", 0L)
                    if (chatId != 0L) {
                        val title = chat.optString("title", "").ifBlank {
                            listOf(
                                chat.optString("first_name", ""),
                                chat.optString("last_name", "")
                            ).filter { it.isNotBlank() }.joinToString(" ")
                        }.ifBlank { chat.optString("username", "Telegram Chat") }
                        return@withContext Result.success(
                            DetectedChat(chatId = chatId.toString(), displayName = title)
                        )
                    }
                }

                Result.failure(
                    Exception("Không tìm thấy Chat ID. Hãy gửi một tin nhắn bất kỳ cho bot của bạn.")
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "detectLatestChatId failed", e)
            Result.failure(Exception("Lỗi lấy Chat ID: ${e.localizedMessage ?: "Không xác định"}"))
        }
    }

    suspend fun sendMessage(
        botToken: String,
        chatId: String,
        htmlMessage: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val cleanToken = botToken.trim()
        val cleanChatId = chatId.trim()
        if (cleanToken.isBlank() || cleanChatId.isBlank()) {
            return@withContext Result.failure(
                IllegalArgumentException("Chưa cấu hình đầy đủ Bot Token và Chat ID Telegram.")
            )
        }
        try {
            val payload = JSONObject().apply {
                put("chat_id", cleanChatId)
                put("text", htmlMessage)
                put("parse_mode", "HTML")
                put("disable_web_page_preview", true)
            }
            val request = Request.Builder()
                .url("https://api.telegram.org/bot$cleanToken/sendMessage")
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                val json = JSONObject(body)
                if (response.isSuccessful && json.optBoolean("ok", false)) {
                    Result.success(Unit)
                } else {
                    val desc = json.optString("description", "Telegram từ chối tin nhắn (${response.code}).")
                    Result.failure(Exception(desc))
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "sendMessage failed", e)
            Result.failure(Exception("Gửi Telegram thất bại: ${e.localizedMessage ?: "Lỗi mạng"}"))
        }
    }

    companion object {
        fun escapeHtml(text: String): String = text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")

        fun formatTaskReminderHtml(task: TaskItem): String {
            val dueText = if (task.dueAtMillis > 0L) {
                SimpleDateFormat("HH:mm - dd/MM/yyyy", Locale("vi", "VN"))
                    .format(Date(task.dueAtMillis))
            } else {
                "Không có hạn cụ thể"
            }

            return buildString {
                append("⏰ <b>NHẮC VIỆC THÔNG MINH • TASKPULSE</b>\n\n")
                append("${task.priorityEnum.emoji} <b>${escapeHtml(task.title)}</b>\n")
                append("📂 Danh mục: ${escapeHtml(task.category)} • Ưu tiên: ${task.priorityEnum.labelVi}\n")
                append("🗓 Hạn chót: <code>$dueText</code>\n")
                if (task.sourceName.isNotBlank()) {
                    append("📎 Nguồn: ${task.sourceEnum.labelVi} (${escapeHtml(task.sourceName)})\n")
                }
                if (task.description.isNotBlank()) {
                    append("\n📝 ${escapeHtml(task.description.take(300))}\n")
                }
                if (task.smartReminderText.isNotBlank()) {
                    append("\n💡 <i>${escapeHtml(task.smartReminderText)}</i>")
                }
            }
        }

        fun formatTestMessageHtml(botUsername: String): String {
            val now = SimpleDateFormat("HH:mm dd/MM/yyyy", Locale("vi", "VN")).format(Date())
            val handle = if (botUsername.isNotBlank()) "@${escapeHtml(botUsername)}" else "TaskPulse Bot"
            return """
                ✅ <b>Kết nối TaskPulse thành công!</b>

                🤖 Bot: $handle
                🕒 Thời gian: <code>$now</code>

                Từ giờ các công việc được AI trích xuất từ tệp hoặc ảnh chụp sẽ tự động gửi nhắc nhở thông minh tới cuộc trò chuyện này.
            """.trimIndent()
        }
    }
}
