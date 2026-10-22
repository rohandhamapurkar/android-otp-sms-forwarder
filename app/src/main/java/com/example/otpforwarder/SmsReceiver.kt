package com.example.otpforwarder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.net.Uri
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import java.util.UUID

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
                try {
                    LegacyConfigMigration.migrate(context, db)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Legacy configuration retained after migration failure", e)
                }
                val activeConfigs = db.configDao().getActiveConfigs()

                if (activeConfigs.isEmpty()) return@launch

                val assembled = SmsAssembly.assemble(messages.mapNotNull { sms ->
                    sms.displayMessageBody?.let { IncomingSms(sms.displayOriginatingAddress, it) }
                })
                for (sms in assembled) {
                    val body = sms.body
                    val sender = sms.sender ?: "Unknown"

                    for (outcome in ForwardingEngine.resolve(body, activeConfigs)) {
                        forwardSms(context, outcome.forwardTo, outcome.message, sender)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to process incoming SMS", e)
                showFailureNotification(context, "configured destinations", e)
            } finally {
                goAsync.finish()
            }
        }
    }

    private fun forwardSms(context: Context, phoneNumber: String, message: String, sender: String) {
        val callbacks = arrayListOf<PendingIntent>()
        val sendId = UUID.randomUUID().toString()
        try {
            // getSystemService(SmsManager::class.java) only exists on API 31+; on older
            // devices it returns null and forwarding would silently do nothing.
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            } ?: throw IllegalStateException("SmsManager unavailable on this device")

            val parts = smsManager.divideMessage(message)
            SmsSendProgressStore.start(context, sendId, parts.size)
            for (index in parts.indices) {
                val sent = Intent(context, SmsSentReceiver::class.java)
                    .setAction(SmsSentReceiver.ACTION_SENT)
                    .setData(Uri.parse("otpforwarder://sent/$sendId/$index"))
                    .putExtra("destination", phoneNumber)
                    .putExtra(SmsSentReceiver.EXTRA_SEND_ID, sendId)
                    .putExtra(SmsSentReceiver.EXTRA_PART_INDEX, index)
                callbacks += PendingIntent.getBroadcast(context, 0, sent,
                    PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE)
            }
            // Post before submitting: a fast modem callback must never be overwritten by Queued.
            runCatching { showDebugNotification(context, sender, message, sendId) }
                .onFailure { Log.w(TAG, "Could not show queued notification", it) }
            if (parts.size == 1) {
                smsManager.sendTextMessage(phoneNumber, null, parts[0], callbacks[0], null)
            } else {
                smsManager.sendMultipartTextMessage(phoneNumber, null, parts, callbacks, null)
            }
            Log.d(TAG, "SMS submitted for sending")
        } catch (e: Exception) {
            callbacks.forEach { it.cancel() }
            Log.e(TAG, "Failed to forward SMS", e)
            runCatching { SmsSendProgressStore.record(context, sendId, 0, successful = false) }
                .onFailure { Log.w(TAG, "Could not track immediate send failure", it) }
            showFailureNotification(context, phoneNumber, e, sendId)
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
    internal fun showDebugNotification(context: Context, sender: String, message: String, sendId: String) {
        if (!BuildConfig.DEBUG) return
        if (!canNotify(context)) return

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureChannel(notificationManager, DEBUG_CHANNEL_ID, "SMS Forwarder Debug")

        val notification = NotificationCompat.Builder(context, DEBUG_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Forwarding queued: $sender")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        try {
            notificationManager.notify("otp-send:$sendId", 0, notification)
        } catch (e: SecurityException) {
            Log.w(TAG, "Debug notification permission denied", e)
        }
    }

    /** Shown in every build: a silent forwarding failure is otherwise invisible to the user. */
    internal fun showFailureNotification(context: Context, phoneNumber: String, error: Exception, sendId: String = UUID.randomUUID().toString()) {
        if (!canNotify(context)) {
            Log.w(TAG, "Cannot show forwarding failure: notification permission denied", error)
            return
        }
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureChannel(notificationManager, FAILURE_CHANNEL_ID, "Forwarding Failures")

        val reason = error.message ?: error::class.java.simpleName

        val notification = NotificationCompat.Builder(context, FAILURE_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("Failed: OTP forwarding")
            .setContentText("Could not forward to $phoneNumber")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Could not forward the OTP to $phoneNumber.\n$reason")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        try {
            notificationManager.notify("otp-send:$sendId", 0, notification)
        } catch (e: SecurityException) {
            Log.w(TAG, "Failure notification permission denied", e)
        }
    }

    internal fun showSentNotification(context: Context, phoneNumber: String, sendId: String) {
        if (!BuildConfig.DEBUG || !canNotify(context)) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureChannel(manager, DEBUG_CHANNEL_ID, "SMS Forwarder Debug")
        val notification = NotificationCompat.Builder(context, DEBUG_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Sent: OTP forwarding")
            .setContentText("Sent to $phoneNumber. Recipient delivery is not confirmed.")
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .build()
        try {
            manager.notify("otp-send:$sendId", 0, notification)
        } catch (e: SecurityException) {
            Log.w(TAG, "Sent notification permission denied", e)
        }
    }

    private fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
}
