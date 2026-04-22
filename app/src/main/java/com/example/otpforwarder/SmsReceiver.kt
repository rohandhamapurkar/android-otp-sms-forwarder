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

    companion object {
        // Pre-compile the regex once to save CPU cycles on every message
        private val OTP_PATTERN = Regex("""\b(\d{6})\b""")
        private val gson = Gson()
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        
        val sharedPrefs = context.getSharedPreferences("forwarder_prefs", Context.MODE_PRIVATE)
        val forwardTo = sharedPrefs.getString("forward_to", "") ?: ""
        val prefixText = sharedPrefs.getString("prefix_text", "my lord here you go:") ?: ""
        
        val filtersJson = sharedPrefs.getString("filter_texts_json", "[]")
        val filtersArray: Array<String> = gson.fromJson(filtersJson, Array<String>::class.java)
        val filters = filtersArray.toList()
        
        if (forwardTo.isEmpty() || filters.isEmpty()) return

        for (sms in messages) {
            val body = sms.displayMessageBody ?: continue
            
            // Check if any of the filters match
            val isMatch = filters.any { body.contains(it, ignoreCase = true) }
            
            if (isMatch) {
                val otp = OTP_PATTERN.find(body)?.groupValues?.get(1)
                if (otp != null) {
                    forwardSms(context, forwardTo, "$prefixText $otp")
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
