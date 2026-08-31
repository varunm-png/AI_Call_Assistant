package in.aicallassistant.app.call

import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log
import in.aicallassistant.app.data.ApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AICallScreeningService : CallScreeningService() {
    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onScreenCall(callDetails: Call.Details) {
        val number = callDetails.handle?.schemeSpecificPart ?: "Unknown"
        Log.i("AICallAssistant", "Incoming call: $number")

        // IMPORTANT: respond immediately. The Android framework requires a
        // screening response within a short timeout (a few seconds).
        //
        // This service only handles the identify/allow-or-block decision that
        // Android's CallScreeningService API exposes. It does not, and cannot,
        // inject AI-generated audio into the call itself — that requires the
        // telephony/VoIP media-bridge path described in docs/ARCHITECTURE.md.
        val response = CallScreeningService.CallResponse.Builder()
            .setDisallowCall(false)
            .setRejectCall(false)
            .setSilenceCall(false)
            .setSkipCallLog(false)
            .setSkipNotification(false)
            .build()

        respondToCall(callDetails, response)

        // Start a backend session the instant the phone rings (before the user
        // has even picked up), so call history and the transcript already exist
        // for this call, and fire a high-priority notification with a direct
        // "Answer with AI" shortcut into the in-app conversation screen —
        // the fastest possible AI-assisted handoff on stock Android.
        scope.launch {
            try {
                val sessionId = ApiClient.startSession(number, "en")
                CallNotifier.notifyIncomingCall(applicationContext, number, sessionId)
            } catch (e: Exception) {
                Log.w("AICallAssistant", "Could not start AI session for $number: ${e.message}")
            }
        }
    }
}
