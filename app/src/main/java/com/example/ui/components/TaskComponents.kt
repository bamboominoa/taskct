package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.TaskItem
import com.example.data.model.TaskPriority
import com.example.data.model.TaskSourceType
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

fun formatDeadlineVi(dueAtMillis: Long): String {
    if (dueAtMillis <= 0L) return "Chưa đặt hạn"
    val nowCal = Calendar.getInstance()
    val targetCal = Calendar.getInstance().apply { timeInMillis = dueAtMillis }
    val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(dueAtMillis))

    return when {
        nowCal.get(Calendar.YEAR) == targetCal.get(Calendar.YEAR) &&
            nowCal.get(Calendar.DAY_OF_YEAR) == targetCal.get(Calendar.DAY_OF_YEAR) ->
            "Hôm nay • $timeFmt"
        nowCal.get(Calendar.YEAR) == targetCal.get(Calendar.YEAR) &&
            nowCal.get(Calendar.DAY_OF_YEAR) + 1 == targetCal.get(Calendar.DAY_OF_YEAR) ->
            "Ngày mai • $timeFmt"
        else -> SimpleDateFormat("dd/MM • HH:mm", Locale("vi", "VN")).format(Date(dueAtMillis))
    }
}

fun formatLeadTimeVi(minutes: Long): String = when {
    minutes <= 0L -> "Đúng giờ"
    minutes < 60L -> "Trước ${minutes}p"
    minutes == 60L -> "Trước 1 giờ"
    minutes < 1440L -> "Trước ${minutes / 60} giờ"
    else -> "Trước ${minutes / 1440} ngày"
}

@Composable
fun TaskItemCard(
    task: TaskItem,
    onToggleComplete: () -> Unit,
    onClick: () -> Unit,
    onSendTelegramNow: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isOverdue = !task.completed && task.dueAtMillis > 0L && task.dueAtMillis < System.currentTimeMillis()
    val checkBg by animateColorAsState(
        targetValue = if (task.completed) MaterialTheme.colorScheme.primary else Color.Transparent,
        label = "check_bg"
    )

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (task.completed) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (isOverdue) {
                MaterialTheme.colorScheme.error.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
            }
        ),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .testTag("task_item_card_${task.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Custom tactile checkbox with 48dp touch target
            Box(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(checkBg)
                    .clickable(onClick = onToggleComplete)
                    .testTag("task_checkbox_${task.id}"),
                contentAlignment = Alignment.Center
            ) {
                if (task.completed) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "Đánh dấu hoàn thành",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                } else {
                    Surface(
                        shape = CircleShape,
                        color = Color.Transparent,
                        border = BorderStroke(2.dp, priorityColor(task.priorityEnum)),
                        modifier = Modifier.size(24.dp)
                    ) {}
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PriorityChip(priority = task.priorityEnum)
                    SourceChip(sourceType = task.sourceEnum)
                    if (task.category.isNotBlank()) {
                        Text(
                            text = task.category,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (task.completed) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    textDecoration = if (task.completed) TextDecoration.LineThrough else TextDecoration.None,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (task.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = task.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (task.dueAtMillis > 0L) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.AccessTime,
                                    contentDescription = null,
                                    tint = if (isOverdue) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = formatDeadlineVi(task.dueAtMillis),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isOverdue) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }

                        if (task.reminderEnabled) {
                            Surface(
                                color = if (task.telegramNotified) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.secondaryContainer
                                },
                                shape = RoundedCornerShape(999.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.NotificationsActive,
                                        contentDescription = null,
                                        tint = if (task.telegramNotified) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSecondaryContainer
                                        },
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = if (task.telegramNotified) {
                                            "Đã nhắc TG"
                                        } else {
                                            formatLeadTimeVi(task.reminderMinutesBefore)
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (task.telegramNotified) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSecondaryContainer
                                        }
                                    )
                                }
                            }
                        }
                    }

                    if (!task.completed) {
                        IconButton(
                            onClick = onSendTelegramNow,
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("send_telegram_now_${task.id}")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Gửi nhắc Telegram ngay",
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun priorityColor(priority: TaskPriority): Color = when (priority) {
    TaskPriority.HIGH -> MaterialTheme.colorScheme.error
    TaskPriority.MEDIUM -> MaterialTheme.colorScheme.tertiary
    TaskPriority.LOW -> MaterialTheme.colorScheme.primary
}

@Composable
fun PriorityChip(priority: TaskPriority) {
    val (bg, fg) = when (priority) {
        TaskPriority.HIGH -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        TaskPriority.MEDIUM -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        TaskPriority.LOW -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
    }
    Surface(color = bg, shape = RoundedCornerShape(6.dp)) {
        Text(
            text = priority.labelVi,
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun SourceChip(sourceType: TaskSourceType) {
    if (sourceType == TaskSourceType.MANUAL) return
    val icon = if (sourceType == TaskSourceType.PHOTO) Icons.Filled.CameraAlt else Icons.Filled.Description
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(6.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(11.dp)
            )
            Text(
                text = sourceType.labelVi,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun EmptyTasksView(
    onOpenAiCapture: () -> Unit,
    onAddManual: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_empty_tasks),
            contentDescription = "Chưa có công việc nào",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth(0.75f)
                .height(170.dp)
                .clip(RoundedCornerShape(22.dp))
        )
        Spacer(modifier = Modifier.height(18.dp))
        Text(
            text = "Danh sách công việc đang trống",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Tải lên tệp tài liệu, chụp ảnh bảng kế hoạch để AI tự động trích xuất hoặc thêm công việc thủ công.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = onOpenAiCapture,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.testTag("empty_ai_scan_button")
            ) {
                Text("Quét tệp / ảnh AI")
            }
            OutlinedButton(
                onClick = onAddManual,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.testTag("empty_add_manual_button")
            ) {
                Text("Thêm thủ công")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TaskEditorBottomSheet(
    existingTask: TaskItem?,
    defaultLeadMinutes: Int,
    onDismiss: () -> Unit,
    onSave: (
        title: String,
        description: String,
        priority: TaskPriority,
        category: String,
        dueAtMillis: Long,
        reminderMinutesBefore: Long,
        reminderEnabled: Boolean,
        smartReminderText: String
    ) -> Unit,
    onDelete: (() -> Unit)? = null,
    onSendTelegramNow: (() -> Unit)? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var title by remember(existingTask) { mutableStateOf(existingTask?.title.orEmpty()) }
    var description by remember(existingTask) { mutableStateOf(existingTask?.description.orEmpty()) }
    var priority by remember(existingTask) {
        mutableStateOf(existingTask?.priorityEnum ?: TaskPriority.MEDIUM)
    }
    var category by remember(existingTask) {
        mutableStateOf(existingTask?.category?.ifBlank { "Công việc" } ?: "Công việc")
    }
    var dueAtMillis by remember(existingTask) {
        mutableLongStateOf(existingTask?.dueAtMillis ?: 0L)
    }
    var leadMinutes by remember(existingTask) {
        mutableLongStateOf(existingTask?.reminderMinutesBefore ?: defaultLeadMinutes.toLong())
    }
    var reminderEnabled by remember(existingTask) {
        mutableStateOf(existingTask?.reminderEnabled ?: true)
    }
    var smartReminderText by remember(existingTask) {
        mutableStateOf(existingTask?.smartReminderText.orEmpty())
    }

    val categories = listOf("Công việc", "Cuộc họp", "Học tập", "Tài chính", "Cá nhân", "Sức khỏe")
    val leadOptions = listOf(15L to "15 phút", 30L to "30 phút", 60L to "1 giờ", 1440L to "1 ngày")

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = if (existingTask == null) "Công việc mới" else "Chi tiết công việc",
                        style = MaterialTheme.typography.titleLarge
                    )
                }
                if (onDelete != null) {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.testTag("delete_task_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "Xóa công việc",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = title,
                onValueChange = { title = it.take(200) },
                label = { Text("Tên công việc *") },
                placeholder = { Text("Ví dụ: Gửi báo cáo doanh thu tuần") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("task_title_input")
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = description,
                onValueChange = { description = it.take(2000) },
                label = { Text("Mô tả chi tiết") },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("task_description_input")
            )

            Spacer(modifier = Modifier.height(14.dp))
            Text("Mức độ ưu tiên", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TaskPriority.entries.forEach { level ->
                    FilterChip(
                        selected = priority == level,
                        onClick = { priority = level },
                        label = { Text("${level.emoji} ${level.labelVi}") }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text("Danh mục", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                categories.forEach { cat ->
                    FilterChip(
                        selected = category == cat,
                        onClick = { category = cat },
                        label = { Text(cat) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Text("Hạn chót nhanh", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Hiện tại: ${formatDeadlineVi(dueAtMillis)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                FilterChip(
                    selected = dueAtMillis == 0L,
                    onClick = { dueAtMillis = 0L },
                    label = { Text("Không hạn") }
                )
                FilterChip(
                    selected = false,
                    onClick = { dueAtMillis = System.currentTimeMillis() + 2 * 3600_000L },
                    label = { Text("+2 giờ") }
                )
                FilterChip(
                    selected = false,
                    onClick = { dueAtMillis = endOfDayMillis(0, 17, 0) },
                    label = { Text("17:00 hôm nay") }
                )
                FilterChip(
                    selected = false,
                    onClick = { dueAtMillis = endOfDayMillis(1, 9, 0) },
                    label = { Text("09:00 ngày mai") }
                )
                FilterChip(
                    selected = false,
                    onClick = { dueAtMillis = endOfDayMillis(3, 10, 0) },
                    label = { Text("+3 ngày") }
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (reminderEnabled) Icons.Filled.NotificationsActive else Icons.Outlined.NotificationsOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Column {
                                Text(
                                    text = "Nhắc nhở thông minh Telegram",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = "Tự động gửi tin nhắn trước hạn",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = reminderEnabled,
                            onCheckedChange = { reminderEnabled = it }
                        )
                    }

                    if (reminderEnabled) {
                        Spacer(modifier = Modifier.height(10.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            leadOptions.forEach { (mins, label) ->
                                FilterChip(
                                    selected = leadMinutes == mins,
                                    onClick = { leadMinutes = mins },
                                    label = { Text("Trước $label") }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = smartReminderText,
                            onValueChange = { smartReminderText = it.take(500) },
                            label = { Text("Lời nhắn nhắc nhở tùy chỉnh") },
                            placeholder = { Text("AI tự viết hoặc nhập lời nhắc của bạn...") },
                            singleLine = false,
                            maxLines = 2,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            if (existingTask != null && onSendTelegramNow != null) {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onSendTelegramNow,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Gửi nhắc nhở Telegram ngay bây giờ")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Hủy")
                }
                Button(
                    onClick = {
                        if (title.isNotBlank()) {
                            onSave(
                                title,
                                description,
                                priority,
                                category,
                                dueAtMillis,
                                leadMinutes,
                                reminderEnabled,
                                smartReminderText
                            )
                        }
                    },
                    enabled = title.isNotBlank(),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1.5f)
                        .testTag("save_task_button")
                ) {
                    Text(
                        text = if (existingTask == null) "Thêm công việc" else "Lưu thay đổi",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

private fun endOfDayMillis(daysAhead: Int, hour: Int, minute: Int): Long {
    return Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, daysAhead)
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
