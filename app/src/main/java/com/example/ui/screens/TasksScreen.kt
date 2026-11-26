package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.TaskItem
import com.example.data.model.TaskPriority
import com.example.data.model.UiState
import com.example.ui.components.EmptyTasksView
import com.example.ui.components.TaskEditorBottomSheet
import com.example.ui.components.TaskItemCard
import com.example.ui.viewmodel.TaskFilter
import com.example.ui.viewmodel.TaskPulseViewModel
import java.util.Calendar

@Composable
fun TasksScreen(
    viewModel: TaskPulseViewModel,
    userDisplayName: String,
    onOpenAiCapture: () -> Unit,
    onOpenTelegramTab: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tasksState by viewModel.tasksState.collectAsStateWithLifecycle()
    val selectedFilter by viewModel.selectedFilter.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val telegramSettings by viewModel.telegramSettings.collectAsStateWithLifecycle()

    var showCreateSheet by remember { mutableStateOf(false) }
    var editingTask by remember { mutableStateOf<TaskItem?>(null) }

    val allTasks = (tasksState as? UiState.Success)?.data.orEmpty()
    val completedCount = remember(allTasks) { allTasks.count { it.completed } }
    val pendingCount = remember(allTasks) { allTasks.count { !it.completed } }
    val progress = remember(allTasks) {
        if (allTasks.isEmpty()) 0f else completedCount.toFloat() / allTasks.size.toFloat()
    }

    val filteredTasks by remember(allTasks, selectedFilter, searchQuery) {
        derivedStateOf {
            val query = searchQuery.trim().lowercase()
            allTasks.filter { task ->
                val matchesFilter = when (selectedFilter) {
                    TaskFilter.ALL -> true
                    TaskFilter.TODAY -> !task.completed && isToday(task.dueAtMillis)
                    TaskFilter.HIGH -> !task.completed && task.priorityEnum == TaskPriority.HIGH
                    TaskFilter.PENDING -> !task.completed
                    TaskFilter.DONE -> task.completed
                }
                val matchesQuery = query.isEmpty() ||
                    task.title.lowercase().contains(query) ||
                    task.description.lowercase().contains(query) ||
                    task.category.lowercase().contains(query)
                matchesFilter && matchesQuery
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Editorial summary banner
            item(key = "header_banner") {
                OverviewBannerCard(
                    userDisplayName = userDisplayName,
                    pendingCount = pendingCount,
                    completedCount = completedCount,
                    totalCount = allTasks.size,
                    progress = progress,
                    telegramConnected = telegramSettings.isConfigured,
                    onTelegramClick = onOpenTelegramTab
                )
            }

            // Quick AI Actions
            item(key = "quick_ai_actions") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    QuickAiActionCard(
                        icon = Icons.Filled.Description,
                        title = "Tải tệp AI",
                        subtitle = "PDF, TXT, ghi chú",
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        onClick = onOpenAiCapture,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("quick_upload_file_card")
                    )
                    QuickAiActionCard(
                        icon = Icons.Filled.CameraAlt,
                        title = "Chụp ảnh AI",
                        subtitle = "Bảng trắng, sổ tay",
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        onClick = onOpenAiCapture,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("quick_camera_card")
                    )
                }
            }

            // Search bar
            item(key = "search_bar") {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = viewModel::setSearchQuery,
                    placeholder = { Text("Tìm kiếm công việc, danh mục...") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = "Tìm kiếm"
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                Icon(
                                    imageVector = Icons.Filled.Clear,
                                    contentDescription = "Xóa từ khóa"
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("search_tasks_input")
                )
            }

            // Filter chips
            item(key = "filter_chips") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TaskFilter.entries.forEach { filter ->
                        FilterChip(
                            selected = selectedFilter == filter,
                            onClick = { viewModel.setFilter(filter) },
                            label = { Text(filter.labelVi) },
                            modifier = Modifier.testTag("filter_chip_${filter.name.lowercase()}")
                        )
                    }
                }
            }

            when (val state = tasksState) {
                is UiState.Loading -> {
                    item(key = "loading") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }

                is UiState.Error -> {
                    item(key = "error") {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }

                is UiState.Success -> {
                    if (allTasks.isEmpty()) {
                        item(key = "empty_all") {
                            EmptyTasksView(
                                onOpenAiCapture = onOpenAiCapture,
                                onAddManual = { showCreateSheet = true }
                            )
                        }
                    } else if (filteredTasks.isEmpty()) {
                        item(key = "empty_filter") {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                shape = RoundedCornerShape(18.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "Không có công việc phù hợp bộ lọc",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Thử chọn mục \"Tất cả\" hoặc xóa từ khóa tìm kiếm.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else {
                        items(filteredTasks, key = { it.id }) { task ->
                            TaskItemCard(
                                task = task,
                                onToggleComplete = { viewModel.toggleTaskCompleted(task) },
                                onClick = { editingTask = task },
                                onSendTelegramNow = { viewModel.sendInstantTaskReminder(task) }
                            )
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showCreateSheet = true },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .testTag("add_task_fab")
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = "Thêm công việc mới"
            )
        }
    }

    if (showCreateSheet) {
        TaskEditorBottomSheet(
            existingTask = null,
            defaultLeadMinutes = telegramSettings.defaultLeadMinutes,
            onDismiss = { showCreateSheet = false },
            onSave = { title, desc, priority, cat, due, lead, remEnabled, smartText ->
                viewModel.addManualTask(
                    title = title,
                    description = desc,
                    priority = priority,
                    category = cat,
                    dueAtMillis = due,
                    reminderMinutesBefore = lead,
                    reminderEnabled = remEnabled,
                    smartReminderText = smartText
                )
                showCreateSheet = false
            }
        )
    }

    editingTask?.let { task ->
        TaskEditorBottomSheet(
            existingTask = task,
            defaultLeadMinutes = telegramSettings.defaultLeadMinutes,
            onDismiss = { editingTask = null },
            onSave = { title, desc, priority, cat, due, lead, remEnabled, smartText ->
                viewModel.updateTask(
                    taskId = task.id,
                    title = title,
                    description = desc,
                    priority = priority,
                    category = cat,
                    dueAtMillis = due,
                    reminderMinutesBefore = lead,
                    reminderEnabled = remEnabled,
                    smartReminderText = smartText,
                    originalDueMillis = task.dueAtMillis
                )
                editingTask = null
            },
            onDelete = {
                viewModel.deleteTask(task.id)
                editingTask = null
            },
            onSendTelegramNow = {
                viewModel.sendInstantTaskReminder(task)
            }
        )
    }
}

@Composable
private fun OverviewBannerCard(
    userDisplayName: String,
    pendingCount: Int,
    completedCount: Int,
    totalCount: Int,
    progress: Float,
    telegramConnected: Boolean,
    onTelegramClick: () -> Unit
) {
    val firstName = userDisplayName.trim().substringBefore(" ").ifBlank { "bạn" }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF0F172A),
                            Color(0xFF134E4A)
                        )
                    )
                )
                .padding(20.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Xin chào, $firstName",
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (pendingCount == 0) {
                                "Mọi việc đều gọn gàng!"
                            } else {
                                "Bạn còn $pendingCount việc đang chờ xử lý"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFCBD5E1)
                        )
                    }

                    Surface(
                        color = if (telegramConnected) {
                            Color(0x3314B8A6)
                        } else {
                            Color(0x33F59E0B)
                        },
                        shape = RoundedCornerShape(999.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .clickable(onClick = onTelegramClick)
                            .testTag("banner_telegram_status")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Trạng thái Telegram",
                                tint = if (telegramConnected) Color(0xFF5EEAD4) else Color(0xFFFDE68A),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = if (telegramConnected) "Telegram Bật" else "Cài Telegram",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Tiến độ hoàn thành",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFFE2E8F0)
                    )
                    Text(
                        text = "$completedCount / $totalCount việc",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF5EEAD4),
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                LinearProgressIndicator(
                    progress = { progress },
                    color = Color(0xFF2DD4BF),
                    trackColor = Color(0x33FFFFFF),
                    strokeCap = StrokeCap.Round,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(999.dp))
                )
            }
        }
    }
}

@Composable
private fun QuickAiActionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = containerColor,
        shape = RoundedCornerShape(18.dp),
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(contentColor.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = contentColor
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.8f)
                )
            }
        }
    }
}

private fun isToday(millis: Long): Boolean {
    if (millis <= 0L) return false
    val now = Calendar.getInstance()
    val target = Calendar.getInstance().apply { timeInMillis = millis }
    return now.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)
}
