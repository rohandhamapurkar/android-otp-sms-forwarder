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
        const val EXTRA_SEND_ID = "send_id"
        const val EXTRA_PART_INDEX = "part_index"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SENT) return
        val sendId = intent.getStringExtra(EXTRA_SEND_ID) ?: return
        val partIndex = intent.getIntExtra(EXTRA_PART_INDEX, -1)
        val successful = resultCode == Activity.RESULT_OK
        val progress = try {
            SmsSendProgressStore.record(context, sendId, partIndex, successful)
        } catch (e: Exception) {
            Log.e("OTP_APP", "Could not record SMS send callback (result $resultCode)", e)
            if (!successful) SmsReceiver().showFailureNotification(context,
                intent.getStringExtra("destination") ?: "configured destination",
                IllegalStateException("SMS sending failed (result $resultCode)", e), sendId)
            return
        } ?: return
        Log.d("OTP_APP", "SMS part result: part=$partIndex result=$resultCode status=${progress.status}")
        val destination = intent.getStringExtra("destination") ?: "configured destination"
        if (progress.status == SmsSendProgress.Status.SENT) {
            SmsReceiver().showSentNotification(context, destination, sendId)
            return
        }
        if (progress.status != SmsSendProgress.Status.FAILED) return
        val reason = when (resultCode) {
            SmsManager.RESULT_ERROR_RADIO_OFF -> "Phone radio is off"
            SmsManager.RESULT_ERROR_NO_SERVICE -> "No mobile service"
            SmsManager.RESULT_ERROR_LIMIT_EXCEEDED -> "SMS sending limit exceeded"
            else -> "SMS sending failed (result $resultCode). Check mobile service and the default SMS SIM."
        }
        SmsReceiver().showFailureNotification(context,
            destination, IllegalStateException(reason), sendId)
    }
}
