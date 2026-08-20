package com.example.otpforwarder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {

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

                    for (config in activeConfigs) {
                        for (filter in config.filterKeywords) {
                            if (body.contains(filter, ignoreCase = true)) {
                                val otp = OtpUtils.extractOtp(body, filter)
                                if (otp != null) {
                                    val cleanPrefix = config.messagePrefix.trim().removeSuffix(":")
                                    val finalMessage = "$cleanPrefix ($filter): $otp"
                                    
                                    forwardSms(context, config.forwardTo, finalMessage)
                                    showNotification(context, sender, finalMessage)
                                    // Match found for this config, move to next config
                                    break 
                                }
                            }
                        }
                    }
                }
            } finally {
                goAsync.finish()
            }
        }
    }

    private fun forwardSms(context: Context, phoneNumber: String, message: String) {
        try {
            val smsManager =
                context.getSystemService(android.telephony.SmsManager::class.java)
            smsManager?.sendTextMessage(phoneNumber, null, message, null, null)
            Log.d("OTP_APP", "Forwarded SMS to $phoneNumber")
        } catch (e: Exception) {
            Log.e("OTP_APP", "Failed to forward SMS", e)
        }
    }

    private fun showNotification(context: Context, sender: String, message: String) {
        val channelId = "sms_forwarder_debug"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "SMS Forwarder Debug",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("SMS from $sender")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
