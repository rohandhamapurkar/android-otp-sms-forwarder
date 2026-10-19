package com.example.otpforwarder

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
import android.util.Log

/** Receives the modem's send result, including failures that do not throw at submission. */
class SmsSentReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_SENT = "com.example.otpforwarder.SMS_SENT"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SENT) return
        if (resultCode == Activity.RESULT_OK) {
            Log.d("OTP_APP", "SMS part sent successfully")
            return
        }
        val reason = when (resultCode) {
            SmsManager.RESULT_ERROR_RADIO_OFF -> "Phone radio is off"
            SmsManager.RESULT_ERROR_NO_SERVICE -> "No mobile service"
            SmsManager.RESULT_ERROR_LIMIT_EXCEEDED -> "SMS sending limit exceeded"
            else -> "SMS sending failed (result $resultCode). Check mobile service and the default SMS SIM."
        }
        SmsReceiver().showFailureNotification(context,
            intent.getStringExtra("destination") ?: "configured destination", IllegalStateException(reason))
    }
}
