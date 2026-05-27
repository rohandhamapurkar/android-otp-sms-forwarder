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
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        
        val sharedPrefs = context.getSharedPreferences("forwarder_prefs", Context.MODE_PRIVATE)
        val masterEnabled = sharedPrefs.getBoolean("master_enabled", true)
        if (!masterEnabled) return

        val forwardTo = sharedPrefs.getString("forward_to", "") ?: ""
        val prefixText = sharedPrefs.getString("prefix_text", "my lord here you go") ?: ""
        val includeFilter = sharedPrefs.getBoolean("include_filter", true)
        
        val filtersJson = sharedPrefs.getString("filter_texts_json", "[]")
        val filters: List<String> = try {
            Gson().fromJson(filtersJson, object : TypeToken<List<String>>() {}.type)
        } catch (e: Exception) {
            emptyList()
        }
        
        if (forwardTo.isEmpty() || filters.isEmpty()) return

        for (sms in messages) {
            val body = sms.displayMessageBody ?: continue
            
            // Check if any of the filters match
            val matchedFilter = filters.find { body.contains(it, ignoreCase = true) }
            
            if (matchedFilter != null) {
                val otp = OtpUtils.extractOtp(body, matchedFilter)

                if (otp != null) {
                    val cleanPrefix = prefixText.trim().removeSuffix(":")
                    val finalMessage = if (includeFilter) {
                        "$cleanPrefix ($matchedFilter): $otp"
                    } else {
                        "$cleanPrefix: $otp"
                    }
                    forwardSms(context, forwardTo, finalMessage)
                    val sender = sms.displayOriginatingAddress ?: "Unknown"
                    showNotification(context, sender, finalMessage)
                    // Found the OTP, no need to process remaining parts of this intent
                    break
                }
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
