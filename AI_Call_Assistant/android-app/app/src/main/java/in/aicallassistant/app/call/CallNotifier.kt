package in.aicallassistant.app.call

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import in.aicallassistant.app.MainActivity

/**
 * Fires an immediate, high-priority notification the instant a call rings,
 * with a direct "Answer with AI" action. This is the fastest legal path to
 * an AI-assisted call on stock Android: the platform does not allow a
 * third-party app to inject AI audio into the real cellular call itself
 * (see docs/ARCHITECTURE.md), so tapping this takes the user straight into
 * the in-app AI conversation screen, pre-loaded with the caller's number and
 * the backend session that was already started when the phone rang.
 */
object CallNotifier {
    private const val CHANNEL_ID = "incoming_calls"
    const val EXTRA_CALLER_NUMBER = "extra_caller_number"
    const val EXTRA_SESSION_ID = "extra_session_id"

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            val existing = manager.getNotificationChannel(CHANNEL_ID)
            if (existing == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Incoming calls",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Alerts you the instant a call rings so you can let the AI assist"
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    fun notifyIncomingCall(context: Context, number: String, sessionId: String) {
        ensureChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_CALLER_NUMBER, number)
            putExtra(EXTRA_SESSION_ID, sessionId)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            number.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Incoming call: $number")
            .setContentText("Tap to let the AI assistant handle this call")
            .setSmallIcon(android.R.drawable.sym_call_incoming)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .addAction(0, "Answer with AI", pendingIntent)
            .build()

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(number.hashCode(), notification)
    }
}
