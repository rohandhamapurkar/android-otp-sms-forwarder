package com.example.otpforwarder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsManager
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "OTP_APP"
        private const val DEBUG_CHANNEL_ID = "sms_forwarder_debug"
        private const val FAILURE_CHANNEL_ID = "sms_forwarder_failure"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return

        val sharedPrefs = context.getSharedPreferences("forwarder_prefs", Context.MODE_PRIVATE)
        val masterEnabled = sharedPrefs.getBoolean("master_enabled", true)
        if (!masterEnabled) return

        val goAsync = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getDatabase(context)
                val activeConfigs = db.configDao().getActiveConfigs()

                if (activeConfigs.isEmpty()) return@launch

                for (sms in messages) {
                    val body = sms.displayMessageBody ?: continue
                    val sender = sms.displayOriginatingAddress ?: "Unknown"

                    for (outcome in ForwardingEngine.resolve(body, activeConfigs)) {
                        forwardSms(context, outcome.forwardTo, outcome.message)
                        showDebugNotification(context, sender, outcome.message)
                    }
                }
            } finally {
                goAsync.finish()
            }
        }
    }

    private fun forwardSms(context: Context, phoneNumber: String, message: String) {
        try {
            // getSystemService(SmsManager::class.java) only exists on API 31+; on older
            // devices it returns null and forwarding would silently do nothing.
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            } ?: throw IllegalStateException("SmsManager unavailable on this device")

            smsManager.sendTextMessage(phoneNumber, null, message, null, null)
            Log.d(TAG, "Forwarded SMS to $phoneNumber")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to forward SMS", e)
            showFailureNotification(context, phoneNumber, e)
        }
    }

    private fun ensureChannel(
        notificationManager: NotificationManager,
        channelId: String,
        channelName: String
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }
    }

    /** Mirrors every forwarded message on-device. Debug builds only — it echoes OTP contents. */
    private fun showDebugNotification(context: Context, sender: String, message: String) {
        if (!BuildConfig.DEBUG) return

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureChannel(notificationManager, DEBUG_CHANNEL_ID, "SMS Forwarder Debug")

        val notification = NotificationCompat.Builder(context, DEBUG_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("SMS from $sender")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    /** Shown in every build: a silent forwarding failure is otherwise invisible to the user. */
    private fun showFailureNotification(context: Context, phoneNumber: String, error: Exception) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureChannel(notificationManager, FAILURE_CHANNEL_ID, "Forwarding Failures")

        val reason = error.message ?: error::class.java.simpleName

        val notification = NotificationCompat.Builder(context, FAILURE_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("OTP forwarding failed")
            .setContentText("Could not forward to $phoneNumber")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Could not forward the OTP to $phoneNumber.\n$reason")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
