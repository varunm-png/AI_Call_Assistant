package in.aicallassistant.app.ui

import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import in.aicallassistant.app.data.ApiClient
import kotlinx.coroutines.launch
import java.util.Locale

data class Line(val who: String, val text: String)

/**
 * Live AI conversation screen. Starts a backend session on entry, lets the
 * user speak turns via SpeechRecognizer, plays AI replies via TextToSpeech,
 * and ends the session (generating a call summary) when the user leaves.
 */
@Composable
fun ConversationScreen(
    caller: String,
    onBack: () -> Unit,
    existingSessionId: String? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var listening by remember { mutableStateOf(false) }
    var ending by remember { mutableStateOf(false) }
    var language by remember { mutableStateOf("en") }
    var sessionId by remember { mutableStateOf(existingSessionId) }
    val lines = remember {
        mutableStateListOf(
            Line(
                "System",
                if (existingSessionId != null)
                    "This call started ringing a moment ago — joining the AI session already in progress..."
                else "Starting AI Call Assistant..."
            )
        )
    }

    val tts = remember { TextToSpeech(context) { } }

    fun speak(text: String) {
        tts.language = if (language == "hi") Locale("hi", "IN") else Locale.US
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "reply")
    }

    LaunchedEffect(Unit) {
        if (existingSessionId != null) {
            // A session was already created the instant the phone rang
            // (see AICallScreeningService) — just attach to it.
            lines[0] = Line("System", "AI Call Assistant is ready. Tap 'Speak to AI' to talk.")
            return@LaunchedEffect
        }
        try {
            sessionId = ApiClient.startSession(caller, language)
            lines[0] = Line("System", "AI Call Assistant is ready. Tap 'Speak to AI' to talk.")
        } catch (e: Exception) {
            lines[0] = Line("System", "Could not reach backend: ${e.message}. Check API_BASE_URL and that the FastAPI server is running.")
        }
    }

    fun startListening() {
        val sid = sessionId
        if (sid == null) {
            lines.add(Line("System", "Session not ready yet — try again in a moment."))
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            lines.add(Line("System", "Speech recognition is not available on this device."))
            return
        }
        listening = true
        val sr = SpeechRecognizer.createSpeechRecognizer(context)
        sr.setRecognitionListener(object : android.speech.RecognitionListener {
            override fun onResults(results: android.os.Bundle?) {
                listening = false
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                sr.destroy()
                if (text.isNullOrBlank()) return
                lines.add(Line("Caller", text))
                scope.launch {
                    try {
                        val reply = ApiClient.chat(sid, text)
                        lines.add(Line("AI", reply.text))
                        speak(reply.text)
                    } catch (e: Exception) {
                        lines.add(Line("System", "Backend error: ${e.message}"))
                    }
                }
            }
            override fun onError(error: Int) {
                listening = false
                sr.destroy()
            }
            override fun onReadyForSpeech(params: android.os.Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: android.os.Bundle?) {}
            override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (language == "hi") "hi-IN" else "en-IN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        sr.startListening(intent)
    }

    fun endCall() {
        val sid = sessionId ?: return onBack()
        ending = true
        scope.launch {
            try {
                val summary = ApiClient.endSession(sid)
                lines.add(Line("System", "Call ended. Summary: $summary"))
            } catch (_: Exception) {
                // Best-effort; still let the user leave the screen.
            } finally {
                ending = false
                onBack()
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { tts.shutdown() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Call: $caller") },
                navigationIcon = { TextButton(onClick = { endCall() }) { Text("Back") } }
            )
        }
    ) { p ->
        Column(
            Modifier.padding(p).padding(16.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(language == "en", { language = "en" }, label = { Text("English") })
                FilterChip(language == "hi", { language = "hi" }, label = { Text("Hindi") })
            }

            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(lines) { line ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(10.dp)) {
                            Text(line.who, style = MaterialTheme.typography.labelLarge)
                            Text(line.text)
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { startListening() },
                    enabled = !listening && !ending && sessionId != null,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (listening) "Listening..." else "Speak to AI")
                }
                OutlinedButton(
                    onClick = { endCall() },
                    enabled = !ending,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (ending) "Saving..." else "End Call")
                }
            }
        }
    }
}

/** Read-only view of a past call's saved transcript and summary. */
@Composable
fun CallHistoryScreen(callId: String, onBack: () -> Unit) {
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var detail by remember { mutableStateOf<in.aicallassistant.app.data.CallDetail?>(null) }

    LaunchedEffect(callId) {
        try {
            detail = ApiClient.getCallDetail(callId)
        } catch (e: Exception) {
            error = e.message
        } finally {
            loading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(detail?.number ?: "Call details") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }
            )
        }
    ) { p ->
        Column(Modifier.padding(p).padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when {
                loading -> CircularProgressIndicator()
                error != null -> Text("Could not load call: $error")
                detail != null -> {
                    Text("Summary", style = MaterialTheme.typography.titleMedium)
                    Text(detail!!.summary)
                    Text("Intent: ${detail!!.intent}  ·  Status: ${detail!!.status}")
                    Text("Transcript", style = MaterialTheme.typography.titleMedium)
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(detail!!.transcript.split("\n").filter { it.isNotBlank() }) { line ->
                            Text(line)
                        }
                    }
                }
            }
        }
    }
}
