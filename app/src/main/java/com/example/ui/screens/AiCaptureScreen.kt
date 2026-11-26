package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.AiExtractionResult
import com.example.data.model.AiScanRecord
import com.example.data.model.AiScanUiState
import com.example.data.model.TaskSourceType
import com.example.ui.components.PriorityChip
import com.example.ui.components.formatDeadlineVi
import com.example.ui.components.formatLeadTimeVi
import com.example.ui.viewmodel.TaskPulseViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AiCaptureScreen(
    viewModel: TaskPulseViewModel,
    onGoToTasks: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val aiScanState by viewModel.aiScanState.collectAsStateWithLifecycle()
    val settings by viewModel.telegramSettings.collectAsStateWithLifecycle()
    val scanHistory by viewModel.scansState.collectAsStateWithLifecycle()

    var extraInstruction by remember { mutableStateOf("") }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var pendingPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var permissionNotice by remember { mutableStateOf<String?>(null) }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = pendingPhotoUri
        if (success && uri != null) {
            val bitmap = decodeBitmapFromUri(context, uri)
            if (bitmap != null) {
                previewBitmap = bitmap
                val label = "Ảnh chụp ${SimpleDateFormat("HH:mm dd/MM", Locale.getDefault()).format(Date())}"
                viewModel.analyzePhoto(bitmap, label, extraInstruction)
            } else {
                permissionNotice = "Không thể đọc ảnh vừa chụp. Vui lòng thử lại."
            }
        }
    }

    fun launchCameraCapture() {
        val uri = createTempPhotoUri(context)
        if (uri != null) {
            pendingPhotoUri = uri
            permissionNotice = null
            takePictureLauncher.launch(uri)
        } else {
            permissionNotice = "Không thể khởi tạo tệp ảnh tạm."
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchCameraCapture()
        } else {
            permissionNotice = "Cần cấp quyền Camera để chụp ảnh trực tiếp. Bạn vẫn có thể chọn ảnh từ thư viện hoặc tải tệp."
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val bitmap = decodeBitmapFromUri(context, uri)
            if (bitmap != null) {
                previewBitmap = bitmap
                val name = queryDisplayName(context, uri) ?: "Ảnh thư viện"
                viewModel.analyzePhoto(bitmap, name, extraInstruction)
            } else {
                permissionNotice = "Không thể mở ảnh đã chọn."
            }
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            previewBitmap = null
            val name = queryDisplayName(context, uri) ?: "Tài liệu tải lên"
            val mimeType = context.contentResolver.getType(uri) ?: "application/pdf"
            viewModel.analyzeFile(
                context = context,
                uri = uri,
                fileName = name,
                mimeType = mimeType,
                extraInstruction = extraInstruction
            )
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "capture_header") {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Phòng Quét AI",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Tải tệp tài liệu hoặc chụp ảnh bảng kế hoạch, hóa đơn, ghi chú viết tay để AI tự động trích xuất và thêm công việc.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Capture Action Grid
        item(key = "capture_actions") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CaptureActionTile(
                        icon = Icons.Filled.CameraAlt,
                        title = "Chụp ảnh AI",
                        subtitle = "Camera nhận diện ngay",
                        accentColor = MaterialTheme.colorScheme.primary,
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        onClick = {
                            val granted = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.CAMERA
                            ) == PackageManager.PERMISSION_GRANTED
                            if (granted) {
                                launchCameraCapture()
                            } else {
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("capture_camera_button")
                    )

                    CaptureActionTile(
                        icon = Icons.Filled.UploadFile,
                        title = "Tải tệp lên",
                        subtitle = "PDF, TXT, CSV, MD",
                        accentColor = MaterialTheme.colorScheme.tertiary,
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        onClick = {
                            filePickerLauncher.launch(
                                arrayOf(
                                    "application/pdf",
                                    "text/plain",
                                    "text/csv",
                                    "text/markdown",
                                    "application/json",
                                    "image/*"
                                )
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("upload_file_button")
                    )
                }

                OutlinedButton(
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("pick_gallery_photo_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.PhotoLibrary,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Chọn ảnh từ thư viện để AI nhận diện")
                }
            }
        }

        if (permissionNotice != null) {
            item(key = "permission_notice") {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = permissionNotice.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }
        }

        // Auto-add toggle + extra prompt
        item(key = "capture_options") {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Tự động thêm vào danh sách",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = "AI tự lưu công việc & đặt lịch nhắc Telegram ngay sau khi phân tích.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Switch(
                            checked = settings.autoAddExtractedTasks,
                            onCheckedChange = viewModel::updateAutoAddExtracted,
                            modifier = Modifier.testTag("auto_add_tasks_switch")
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = extraInstruction,
                        onValueChange = { extraInstruction = it.take(300) },
                        label = { Text("Ghi chú hướng dẫn AI (tùy chọn)") },
                        placeholder = { Text("VD: Chỉ lấy các deadline trong tuần này, ưu tiên cao...") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("ai_extra_instruction_input")
                    )
                }
            }
        }

        // Preview thumbnail if photo selected
        previewBitmap?.let { bmp ->
            item(key = "photo_preview") {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Ảnh đang phân tích",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                    )
                }
            }
        }

        // AI Analysis State Panel
        when (val state = aiScanState) {
            is AiScanUiState.Idle -> Unit

            is AiScanUiState.Analyzing -> {
                item(key = "ai_analyzing") {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(28.dp),
                                strokeWidth = 3.dp
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (state.isPhoto) {
                                        "AI đang nhận diện hình ảnh..."
                                    } else {
                                        "AI đang phân tích tài liệu..."
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "${state.sourceName} • Đang tách đầu việc, hạn chót và câu nhắc Telegram",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                                )
                            }
                        }
                    }
                }
            }

            is AiScanUiState.Error -> {
                item(key = "ai_error") {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Không thể hoàn tất phân tích AI",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedButton(onClick = viewModel::resetAiScan) {
                                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Thử lại")
                            }
                        }
                    }
                }
            }

            is AiScanUiState.ResultReady -> {
                item(key = "ai_result_card") {
                    ExtractionResultPanel(
                        result = state.result,
                        onToggleDraft = viewModel::toggleDraftSelection,
                        onCommitSelected = viewModel::commitSelectedDrafts,
                        onGoToTasks = onGoToTasks,
                        onReset = {
                            previewBitmap = null
                            viewModel.resetAiScan()
                        }
                    )
                }
            }
        }

        // Recent AI Scan History
        if (scanHistory.isNotEmpty()) {
            item(key = "history_title") {
                Text(
                    text = "Lịch sử phân tích gần đây",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            items(scanHistory.take(8), key = { it.id }) { record ->
                ScanHistoryCard(
                    record = record,
                    onDelete = { viewModel.deleteScanRecord(record.id) }
                )
            }
        }
    }
}

@Composable
private fun CaptureActionTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accentColor: Color,
    containerColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = containerColor,
        shape = RoundedCornerShape(22.dp),
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = accentColor,
                    modifier = Modifier.size(24.dp)
                )
            }
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ExtractionResultPanel(
    result: AiExtractionResult,
    onToggleDraft: (Int) -> Unit,
    onCommitSelected: () -> Unit,
    onGoToTasks: () -> Unit,
    onReset: () -> Unit
) {
    val alreadyAdded = result.autoAddedCount > 0
    val selectedCount = result.tasks.count { it.selected }

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
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
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Kết quả phân tích AI",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(999.dp)
                ) {
                    Text(
                        text = "${result.tasks.size} việc",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = result.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (alreadyAdded) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Đã tự động thêm ${result.autoAddedCount} công việc vào danh sách và kích hoạt nhắc nhở Telegram.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            if (result.tasks.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    result.tasks.forEachIndexed { index, draft ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                if (!alreadyAdded) {
                                    Checkbox(
                                        checked = draft.selected,
                                        onCheckedChange = { onToggleDraft(index) }
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        PriorityChip(priority = draft.priority)
                                        Text(
                                            text = draft.category,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = draft.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    if (draft.description.isNotBlank()) {
                                        Text(
                                            text = draft.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "${formatDeadlineVi(draft.dueAtMillis)} • Nhắc ${formatLeadTimeVi(draft.reminderMinutesBefore)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onReset,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Quét tiếp")
                }
                if (alreadyAdded) {
                    Button(
                        onClick = onGoToTasks,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1.3f)
                            .testTag("go_to_tasks_after_ai_button")
                    ) {
                        Text("Xem danh sách")
                    }
                } else if (result.tasks.isNotEmpty()) {
                    Button(
                        onClick = onCommitSelected,
                        enabled = selectedCount > 0,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1.4f)
                            .testTag("commit_selected_drafts_button")
                    ) {
                        Text("Thêm $selectedCount việc")
                    }
                }
            }
        }
    }
}

@Composable
private fun ScanHistoryCard(
    record: AiScanRecord,
    onDelete: () -> Unit
) {
    val icon = if (record.sourceEnum == TaskSourceType.PHOTO) Icons.Filled.CameraAlt else Icons.Filled.Description
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = record.sourceName,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = record.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Trích xuất ${record.extractedCount} công việc",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Xóa lịch sử quét",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

private fun createTempPhotoUri(context: Context): Uri? {
    return runCatching {
        val dir = File(context.cacheDir, "captured_photos").apply { mkdirs() }
        val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }.getOrNull()
}

private fun decodeBitmapFromUri(context: Context, uri: Uri): Bitmap? {
    return runCatching {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream)
        }
    }.getOrNull()
}

private fun queryDisplayName(context: Context, uri: Uri): String? {
    return runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
    }.getOrNull()
}
