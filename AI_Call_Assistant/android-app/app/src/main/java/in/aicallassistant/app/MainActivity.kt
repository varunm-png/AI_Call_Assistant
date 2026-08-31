package in.aicallassistant.app

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import in.aicallassistant.app.call.CallNotifier
import in.aicallassistant.app.data.ApiClient
import in.aicallassistant.app.data.CallItem
import in.aicallassistant.app.ui.CallHistoryScreen
import in.aicallassistant.app.ui.ConversationScreen
import in.aicallassistant.app.ui.HomeScreen
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}

    // Holds an incoming call's caller/session, set from the notification tap
    // (see CallNotifier + AICallScreeningService) so the UI can jump straight
    // into that call's AI conversation instead of the home screen.
    private var pendingCaller by mutableStateOf<String?>(null)
    private var pendingSessionId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.READ_CALL_LOG,
                Manifest.permission.POST_NOTIFICATIONS
            )
        )

        consumeIncomingCallIntent(intent)

        setContent {
            MaterialTheme {
                AppRoot(
                    activity = this,
                    initialCaller = pendingCaller,
                    initialSessionId = pendingSessionId,
                    onConsumedInitialCall = {
                        pendingCaller = null
                        pendingSessionId = null
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeIncomingCallIntent(intent)
    }

    private fun consumeIncomingCallIntent(intent: Intent?) {
        val number = intent?.getStringExtra(CallNotifier.EXTRA_CALLER_NUMBER)
        val sessionId = intent?.getStringExtra(CallNotifier.EXTRA_SESSION_ID)
        if (number != null && sessionId != null) {
            pendingCaller = number
            pendingSessionId = sessionId
        }
    }

    fun requestCallScreeningRole() {
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            val rm = getSystemService(RoleManager::class.java)
            if (rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) &&
                !rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
            ) {
                startActivityForResult(
                    rm.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING),
                    700
                )
            }
        }
    }
}

@Composable
fun AppRoot(
    activity: MainActivity,
    initialCaller: String?,
    initialSessionId: String?,
    onConsumedInitialCall: () -> Unit
) {
    var screen by remember { mutableStateOf(if (initialCaller != null) "conversation" else "home") }
    var selectedCaller by remember { mutableStateOf(initialCaller ?: "Test Caller") }
    var selectedSessionId by remember { mutableStateOf(initialSessionId) }
    var selectedCallId by remember { mutableStateOf<String?>(null) }
    var calls by remember { mutableStateOf<List<CallItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun refresh() {
        loading = true
        loadError = null
        try {
            calls = ApiClient.listCalls()
        } catch (e: Exception) {
            loadError = e.message
        } finally {
            loading = false
        }
    }

    LaunchedEffect(Unit) { refresh() }

    // If a new incoming-call notification is tapped while the app is already
    // open, jump straight into that call's conversation too.
    LaunchedEffect(initialCaller, initialSessionId) {
        if (initialCaller != null && initialSessionId != null) {
            selectedCaller = initialCaller
            selectedSessionId = initialSessionId
            screen = "conversation"
            onConsumedInitialCall()
        }
    }

    when (screen) {
        "conversation" -> ConversationScreen(
            caller = selectedCaller,
            existingSessionId = selectedSessionId,
            onBack = {
                selectedSessionId = null
                screen = "home"
                scope.launch { refresh() }
            }
        )
        "history" -> CallHistoryScreen(
            callId = selectedCallId ?: "",
            onBack = { screen = "home" }
        )
        else -> HomeScreen(
            calls = calls,
            loading = loading,
            loadError = loadError,
            onStart = {
                selectedCaller = "Test Caller"
                selectedSessionId = null
                screen = "conversation"
            },
            onScreenCalls = { activity.requestCallScreeningRole() },
            onOpen = {
                selectedCallId = it.id
                screen = "history"
            }
        )
    }
}
