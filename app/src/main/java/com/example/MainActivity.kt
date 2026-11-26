package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.ai.AiTaskExtractor
import com.example.data.firebase.configureAppCheck
import com.example.data.repository.TaskRepository
import com.example.data.telegram.ReminderScheduler
import com.example.data.telegram.TelegramBotClient
import com.example.data.telegram.TelegramPreferencesStore
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.signOut
import com.example.ui.screens.AiCaptureScreen
import com.example.ui.screens.TasksScreen
import com.example.ui.screens.TelegramScreen
import com.example.ui.theme.TaskPulseTheme
import com.example.ui.viewmodel.TaskPulseViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

enum class AppDestination(
    val labelVi: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val tag: String
) {
    TASKS("Công việc", Icons.Filled.Checklist, Icons.Outlined.Checklist, "nav_tasks"),
    AI_CAPTURE("Quét AI", Icons.Filled.AutoAwesome, Icons.Outlined.AutoAwesome, "nav_ai_capture"),
    TELEGRAM("Telegram", Icons.AutoMirrored.Filled.Send, Icons.AutoMirrored.Outlined.Send, "nav_telegram")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure Firebase App Check with launch intent BEFORE UI, ViewModels, or Firebase operations.
        configureAppCheck(this, intent)

        enableEdgeToEdge()

        setContent {
            TaskPulseTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()
                }
            }
        }
    }
}

private fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

@Composable
fun AppNavigation(auth: FirebaseAuth = Firebase.auth) {
    val currentUser by auth.authStateFlow()
        .collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    if (user == null) {
        AuthScreen(onAuthSuccess = { /* AuthStateListener drives navigation reactively */ })
    } else {
        key(user.uid) {
            AuthenticatedTaskPulseShell(
                currentUserId = user.uid,
                userDisplayName = user.displayName.orEmpty(),
                userEmail = user.email.orEmpty()
            )
        }
    }
}

@Composable
private fun AuthenticatedTaskPulseShell(
    currentUserId: String,
    userDisplayName: String,
    userEmail: String
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: TaskPulseViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                TaskPulseViewModel(
                    repository = TaskRepository(db),
                    aiExtractor = AiTaskExtractor(),
                    telegramPrefs = TelegramPreferencesStore(app),
                    telegramClient = TelegramBotClient(),
                    reminderScheduler = ReminderScheduler(app),
                    currentUserId = currentUserId
                )
            }
        }
    )

    var currentDestination by rememberSaveable { mutableStateOf(AppDestination.TASKS) }
    val snackbarHostState = remember { SnackbarHostState() }
    val toastMessage by viewModel.toastMessage.collectAsStateWithLifecycle()

    LaunchedEffect(toastMessage) {
        toastMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearToast()
        }
    }

    // System back returns from secondary tabs to Tasks tab
    BackHandler(enabled = currentDestination != AppDestination.TASKS) {
        currentDestination = AppDestination.TASKS
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (!useNavRail) {
                    NavigationBar {
                        AppDestination.entries.forEach { dest ->
                            val selected = currentDestination == dest
                            NavigationBarItem(
                                selected = selected,
                                onClick = { currentDestination = dest },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = dest.labelVi
                                    )
                                },
                                label = { Text(dest.labelVi) },
                                modifier = Modifier.testTag(dest.tag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavRail) {
                    NavigationRail(modifier = Modifier.fillMaxHeight()) {
                        AppDestination.entries.forEach { dest ->
                            val selected = currentDestination == dest
                            NavigationRailItem(
                                selected = selected,
                                onClick = { currentDestination = dest },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = dest.labelVi
                                    )
                                },
                                label = { Text(dest.labelVi) },
                                modifier = Modifier.testTag(dest.tag)
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    val contentModifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 760.dp)

                    when (currentDestination) {
                        AppDestination.TASKS -> TasksScreen(
                            viewModel = viewModel,
                            userDisplayName = userDisplayName,
                            onOpenAiCapture = { currentDestination = AppDestination.AI_CAPTURE },
                            onOpenTelegramTab = { currentDestination = AppDestination.TELEGRAM },
                            modifier = contentModifier
                        )

                        AppDestination.AI_CAPTURE -> AiCaptureScreen(
                            viewModel = viewModel,
                            onGoToTasks = { currentDestination = AppDestination.TASKS },
                            modifier = contentModifier
                        )

                        AppDestination.TELEGRAM -> TelegramScreen(
                            viewModel = viewModel,
                            userEmail = userEmail,
                            userDisplayName = userDisplayName,
                            onSignOutClick = {
                                signOut(
                                    credentialManager = credentialManager,
                                    scope = scope
                                )
                            },
                            modifier = contentModifier
                        )
                    }
                }
            }
        }
    }
}
