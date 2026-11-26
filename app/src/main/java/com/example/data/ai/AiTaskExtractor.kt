package com.example.data.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.example.data.model.AiExtractionResult
import com.example.data.model.ExtractedTaskDraft
import com.example.data.model.TaskItem
import com.example.data.model.TaskPriority
import com.example.data.model.TaskSourceType
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private const val TAG = "AiTaskExtractor"
private const val MAX_INLINE_BYTES = 18 * 1024 * 1024 // Stay safely below 20MB inline limit
private const val MODEL_NAME = "gemini-3.8-flash"

class AiTaskExtractor {

    private val extractionSchema = Schema.obj(
        properties = mapOf(
            "summary" to Schema.string(
                description = "Tóm tắt ngắn gọn 1-2 câu bằng tiếng Việt về nội dung tài liệu hoặc bức ảnh."
            ),
            "tasks" to Schema.array(
                description = "Danh sách các công việc cần thực hiện được trích xuất từ tài liệu hoặc hình ảnh.",
                items = Schema.obj(
                    properties = mapOf(
                        "title" to Schema.string(
                            description = "Tên công việc ngắn gọn, rõ ràng bằng tiếng Việt (tối đa 120 ký tự)."
                        ),
                        "description" to Schema.string(
                            description = "Chi tiết bổ sung, checklist nhỏ hoặc ngữ cảnh liên quan (tối đa 500 ký tự)."
                        ),
                        "priority" to Schema.enumeration(
                            values = listOf("HIGH", "MEDIUM", "LOW"),
                            description = "Mức độ ưu tiên dựa trên độ gấp và tầm quan trọng."
                        ),
                        "category" to Schema.string(
                            description = "Danh mục ngắn gọn: Công việc, Học tập, Cuộc họp, Tài chính, Cá nhân, hoặc Sức khỏe."
                        ),
                        "dueDateIso" to Schema.string(
                            description = "Thời hạn dạng YYYY-MM-DD HH:mm theo giờ địa phương nếu có thể xác định hoặc suy luận hợp lý; để chuỗi rỗng nếu hoàn toàn không có thời hạn.",
                            nullable = true
                        ),
                        "reminderMinutesBefore" to Schema.integer(
                            description = "Số phút nên nhắc trước hạn trên Telegram (ví dụ: 15, 30, 60, 180, hoặc 1440 cho công việc quan trọng)."
                        ),
                        "smartReminderText" to Schema.string(
                            description = "Câu nhắc nhở thông minh, thân thiện và hữu ích bằng tiếng Việt để gửi qua Telegram."
                        )
                    ),
                    optionalProperties = listOf("description", "dueDateIso", "smartReminderText")
                )
            )
        )
    )

    private val extractionModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_NAME,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = extractionSchema
                temperature = 0.2f
            }
        )
    }

    private val briefingModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_NAME,
            generationConfig = generationConfig {
                temperature = 0.5f
            }
        )
    }

    suspend fun analyzePhoto(
        bitmap: Bitmap,
        sourceName: String,
        extraInstruction: String,
        defaultLeadMinutes: Int
    ): Result<AiExtractionResult> = withContext(Dispatchers.IO) {
        try {
            val scaledBitmap = scaleBitmapIfNeeded(bitmap, maxDim = 1600)
            val prompt = buildExtractionPrompt(
                sourceLabel = "hình ảnh ($sourceName)",
                extraInstruction = extraInstruction,
                defaultLeadMinutes = defaultLeadMinutes
            )

            val response = withTimeout(60_000L) {
                extractionModel.generateContent(
                    content {
                        image(scaledBitmap)
                        text(prompt)
                    }
                )
            }

            val jsonText = response.text
                ?: return@withContext Result.failure(IllegalStateException("AI không trả về kết quả."))

            Result.success(
                parseExtractionResponse(
                    jsonString = jsonText,
                    sourceType = TaskSourceType.PHOTO,
                    sourceName = sourceName,
                    defaultLeadMinutes = defaultLeadMinutes
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Photo AI analysis timed out", e)
            Result.failure(Exception("Quá thời gian phân tích ảnh (60 giây). Vui lòng thử lại."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Photo AI analysis failed", e)
            Result.failure(Exception(e.localizedMessage ?: "Không thể phân tích hình ảnh bằng AI."))
        }
    }

    suspend fun analyzeFile(
        context: Context,
        uri: Uri,
        fileName: String,
        mimeType: String,
        extraInstruction: String,
        defaultLeadMinutes: Int
    ): Result<AiExtractionResult> = withContext(Dispatchers.IO) {
        try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { stream ->
                val data = stream.readBytes()
                if (data.size > MAX_INLINE_BYTES) {
                    return@withContext Result.failure(
                        IllegalArgumentException("Tệp vượt quá giới hạn 18MB. Vui lòng chọn tệp nhỏ hơn.")
                    )
                }
                data
            } ?: return@withContext Result.failure(
                IllegalStateException("Không thể đọc nội dung tệp đã chọn.")
            )

            if (bytes.isEmpty()) {
                return@withContext Result.failure(
                    IllegalArgumentException("Tệp trống, không có dữ liệu để phân tích.")
                )
            }

            val resolvedMime = resolveMimeType(fileName, mimeType)
            val prompt = buildExtractionPrompt(
                sourceLabel = "tệp tài liệu ($fileName)",
                extraInstruction = extraInstruction,
                defaultLeadMinutes = defaultLeadMinutes
            )

            val requestContent = when {
                resolvedMime.startsWith("image/") -> {
                    val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        ?: return@withContext Result.failure(IllegalArgumentException("Không thể giải mã tệp ảnh."))
                    val scaled = scaleBitmapIfNeeded(decoded, maxDim = 1600)
                    content {
                        image(scaled)
                        text(prompt)
                    }
                }
                resolvedMime.startsWith("text/") || resolvedMime == "application/json" -> {
                    val decodedText = bytes.toString(Charsets.UTF_8).take(120_000)
                    content {
                        text("$prompt\n\n--- NỘI DUNG TỆP ($fileName) ---\n$decodedText")
                    }
                }
                else -> {
                    content {
                        inlineData(bytes, resolvedMime)
                        text(prompt)
                    }
                }
            }

            val response = withTimeout(60_000L) {
                extractionModel.generateContent(requestContent)
            }

            val jsonText = response.text
                ?: return@withContext Result.failure(IllegalStateException("AI không trả về kết quả."))

            Result.success(
                parseExtractionResponse(
                    jsonString = jsonText,
                    sourceType = TaskSourceType.FILE,
                    sourceName = fileName,
                    defaultLeadMinutes = defaultLeadMinutes
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "File AI analysis timed out", e)
            Result.failure(Exception("Quá thời gian phân tích tệp (60 giây). Vui lòng thử lại."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "File AI analysis failed", e)
            Result.failure(Exception(e.localizedMessage ?: "Không thể phân tích tệp bằng AI."))
        }
    }

    suspend fun generateDailyTelegramBriefing(
        pendingTasks: List<TaskItem>,
        completedCount: Int
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val nowFormatted = SimpleDateFormat("EEEE, dd/MM/yyyy HH:mm", Locale("vi", "VN"))
                .format(Date())

            val taskLines = pendingTasks.take(20).mapIndexed { index, task ->
                val dueStr = if (task.dueAtMillis > 0L) {
                    SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(task.dueAtMillis))
                } else {
                    "Chưa có hạn"
                }
                "${index + 1}. [${task.priorityEnum.labelVi}] ${task.title} (Danh mục: ${task.category}, Hạn: $dueStr)"
            }.joinToString("\n")

            val prompt = """
                Bạn là trợ lý nhắc nhở thông minh TaskPulse trên Telegram.
                Thời điểm hiện tại: $nowFormatted.
                Số việc đã hoàn thành: $completedCount.
                Danh sách việc chưa hoàn thành (${pendingTasks.size} việc):
                ${taskLines.ifBlank { "Hiện chưa có công việc tồn đọng nào!" }}

                Hãy viết một bản tin nhắc nhở ngắn gọn (tối đa 12 dòng), truyền cảm hứng và thực tế bằng tiếng Việt để gửi trực tiếp qua Telegram:
                - Dùng emoji hợp lý và thẻ <b>...</b> nhẹ nhàng (CHỈ dùng <b> và <i>, KHÔNG dùng markdown ** hay thẻ HTML khác).
                - Nêu rõ 2-3 việc quan trọng/gấp nhất cần làm trước.
                - Gợi ý khung giờ tập trung thông minh.
            """.trimIndent()

            val response = withTimeout(45_000L) {
                briefingModel.generateContent(prompt)
            }
            val text = response.text?.trim()
            if (text.isNullOrBlank()) {
                Result.failure(IllegalStateException("AI không tạo được bản tóm tắt."))
            } else {
                Result.success(text)
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Daily briefing timed out", e)
            Result.failure(Exception("Tạo bản tóm tắt AI bị quá thời gian. Vui lòng thử lại."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Daily briefing failed", e)
            Result.failure(Exception(e.localizedMessage ?: "Không thể tạo bản tóm tắt AI."))
        }
    }

    private fun buildExtractionPrompt(
        sourceLabel: String,
        extraInstruction: String,
        defaultLeadMinutes: Int
    ): String {
        val nowIso = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
        val dayOfWeek = SimpleDateFormat("EEEE", Locale("vi", "VN")).format(Date())
        val extra = if (extraInstruction.isNotBlank()) {
            "\nYêu cầu thêm từ người dùng: $extraInstruction"
        } else ""

        return """
            Bạn là chuyên gia phân tích tài liệu & quản lý công việc cho ứng dụng TaskPulse.
            Thời gian hiện tại của người dùng: $nowIso ($dayOfWeek).
            Hãy đọc kỹ toàn bộ $sourceLabel và trích xuất TẤT CẢ các đầu việc, nhiệm vụ, lịch hẹn, hạn nộp, hóa đơn cần thanh toán, hoặc mục tiêu hành động cụ thể.

            Quy tắc trích xuất:
            1. Viết kết quả bằng tiếng Việt rõ ràng, súc tích, dễ hành động.
            2. Nếu tài liệu/ảnh có chữ viết tay, lịch họp, bảng kế hoạch, hóa đơn, bài tập hoặc ảnh chụp màn hình chat, hãy nhận diện chính xác từng đầu việc riêng biệt (tối đa 15 đầu việc).
            3. Xác định thời hạn (dueDateIso định dạng "YYYY-MM-DD HH:mm") dựa trên mốc hiện tại $nowIso nếu thấy ngày/giờ hoặc cụm từ như "ngày mai", "thứ Sáu", "cuối tuần", "tuần sau". Nếu không có ngày giờ, để chuỗi rỗng "".
            4. Gợi ý reminderMinutesBefore thông minh (mặc định khoảng $defaultLeadMinutes phút; việc rất gấp hoặc hạn xa có thể nhắc trước 60 hoặc 1440 phút).
            5. Viết smartReminderText tự nhiên, ngắn gọn để gửi nhắc nhở qua Telegram.
            $extra
        """.trimIndent()
    }

    private fun parseExtractionResponse(
        jsonString: String,
        sourceType: TaskSourceType,
        sourceName: String,
        defaultLeadMinutes: Int
    ): AiExtractionResult {
        val root = JSONObject(jsonString)
        val summary = root.optString("summary", "Đã phân tích xong nội dung.").trim()
        val tasksArray = root.optJSONArray("tasks")
        val drafts = mutableListOf<ExtractedTaskDraft>()

        if (tasksArray != null) {
            for (i in 0 until tasksArray.length()) {
                val obj = tasksArray.optJSONObject(i) ?: continue
                val title = obj.optString("title", "").trim().take(200)
                if (title.isEmpty()) continue
                val description = obj.optString("description", "").trim().take(2000)
                val priority = TaskPriority.fromRaw(obj.optString("priority", "MEDIUM"))
                val category = obj.optString("category", "Công việc").trim()
                    .ifEmpty { "Công việc" }.take(50)
                val dueIso = obj.optString("dueDateIso", "").trim()
                val dueMillis = parseLocalDateTimeMillis(dueIso)
                val leadMinutes = obj.optInt("reminderMinutesBefore", defaultLeadMinutes)
                    .coerceIn(0, 10080).toLong()
                val reminderText = obj.optString("smartReminderText", "").trim().take(500)

                drafts.add(
                    ExtractedTaskDraft(
                        title = title,
                        description = description,
                        priority = priority,
                        category = category,
                        dueAtMillis = dueMillis,
                        reminderMinutesBefore = leadMinutes,
                        smartReminderText = reminderText,
                        selected = true
                    )
                )
            }
        }

        return AiExtractionResult(
            sourceType = sourceType,
            sourceName = sourceName,
            summary = summary.ifEmpty { "Đã phân tích $sourceName." },
            tasks = drafts
        )
    }

    private fun parseLocalDateTimeMillis(raw: String): Long {
        if (raw.isBlank() || raw.equals("null", ignoreCase = true)) return 0L
        val patterns = listOf(
            "yyyy-MM-dd HH:mm",
            "yyyy-MM-dd'T'HH:mm",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd"
        )
        for (pattern in patterns) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US).apply {
                    isLenient = false
                    timeZone = TimeZone.getDefault()
                }
                val parsed = sdf.parse(raw.take(pattern.replace("'", "").length))
                if (parsed != null) {
                    val millis = if (pattern == "yyyy-MM-dd") {
                        // Default to 09:00 local time if only date is provided
                        parsed.time + 9 * 60 * 60 * 1000L
                    } else {
                        parsed.time
                    }
                    if (millis in 1L..4102444800000L) return millis
                }
            } catch (_: Exception) {
                // Try next format
            }
        }
        return 0L
    }

    private fun resolveMimeType(fileName: String, rawMime: String): String {
        if (rawMime.isNotBlank() && rawMime != "application/octet-stream") {
            return rawMime
        }
        val lower = fileName.lowercase(Locale.US)
        return when {
            lower.endsWith(".pdf") -> "application/pdf"
            lower.endsWith(".png") -> "image/png"
            lower.endsWith(".jpg") || lower.endsWith(".jpeg") -> "image/jpeg"
            lower.endsWith(".webp") -> "image/webp"
            lower.endsWith(".txt") || lower.endsWith(".md") -> "text/plain"
            lower.endsWith(".csv") -> "text/csv"
            lower.endsWith(".json") -> "application/json"
            else -> "application/pdf"
        }
    }

    private fun scaleBitmapIfNeeded(bitmap: Bitmap, maxDim: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxDim && height <= maxDim) return bitmap
        val ratio = maxDim.toFloat() / maxOf(width, height).toFloat()
        val newWidth = (width * ratio).toInt().coerceAtLeast(1)
        val newHeight = (height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }
}
