package com.example.otpforwarder

import android.Manifest
import android.app.Activity
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.telephony.SmsManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises real broadcast callbacks and notifications, without calling any SMS send API. */
@RunWith(AndroidJUnit4::class)
class SmsSendNotificationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val ids = mutableListOf<String>()

    @Before fun requireNotifications() {
        assumeTrue(BuildConfig.DEBUG)
        assumeTrue(Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
        if (Build.VERSION.SDK_INT >= 24) assumeTrue(manager.areNotificationsEnabled())
    }

    private fun queued(parts: Int): String {
        val id = UUID.randomUUID().toString()
        ids += id
        SmsSendProgressStore.start(context, id, parts)
        SmsReceiver().showDebugNotification(context, "Diagnostic", "Notification test only. No SMS sent.", id)
        awaitTitle(id, "Forwarding queued:")
        return id
    }

    private fun callback(id: String, part: Int, result: Int) {
        val intent = Intent(context, SmsSentReceiver::class.java)
            .setAction(SmsSentReceiver.ACTION_SENT)
            .setData(Uri.parse("otpforwarder://notification-test/$id/$part/${UUID.randomUUID()}"))
            .putExtra(SmsSentReceiver.EXTRA_SEND_ID, id)
            .putExtra(SmsSentReceiver.EXTRA_PART_INDEX, part)
            .putExtra("destination", "diagnostic destination")
        PendingIntent.getBroadcast(context, 0, intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE).send(context, result, null)
    }

    private fun awaitTitle(id: String, prefix: String): Notification {
        val deadline = SystemClock.elapsedRealtime() + 5_000
        while (SystemClock.elapsedRealtime() < deadline) {
            val active = manager.activeNotifications.filter { it.tag == "otp-send:$id" }
            if (active.size == 1 && active.single().notification.extras
                    .getCharSequence(Notification.EXTRA_TITLE).toString().startsWith(prefix)) {
                assertEquals(0, active.single().id)
                return active.single().notification
            }
            SystemClock.sleep(50)
        }
        error("Expected a single $prefix notification replacing queued state")
    }

    @Test fun successReplacesQueuedNotification() {
        val id = queued(1)
        callback(id, 0, Activity.RESULT_OK)
        val sent = awaitTitle(id, "Sent:")
        assertTrue(sent.extras.getCharSequence(Notification.EXTRA_TEXT).toString()
            .contains("delivery is not confirmed"))
    }

    @Test fun failureReplacesQueuedNotificationWithReason() {
        val id = queued(1)
        callback(id, 0, SmsManager.RESULT_ERROR_RADIO_OFF)
        val failed = awaitTitle(id, "Failed:")
        assertTrue(failed.extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString().contains("radio is off"))
    }

    @Test fun multipartOnlyBecomesSentWhenAllPartsSucceed() {
        val id = queued(2)
        callback(id, 1, Activity.RESULT_OK)
        awaitStoredResult(id, "sent", setOf("1"))
        awaitTitle(id, "Forwarding queued:")
        callback(id, 0, Activity.RESULT_OK)
        awaitTitle(id, "Sent:")
    }

    @Test fun laterPartSuccessDoesNotOverwriteFailure() {
        val id = queued(2)
        callback(id, 0, SmsManager.RESULT_ERROR_NO_SERVICE)
        awaitTitle(id, "Failed:")
        callback(id, 1, Activity.RESULT_OK)
        awaitStoredResult(id, "sent", setOf("1"))
        awaitTitle(id, "Failed:")
    }

    private fun awaitStoredResult(id: String, field: String, expected: Set<String>) {
        val prefs = context.getSharedPreferences("sms_send_progress", Context.MODE_PRIVATE)
        val deadline = SystemClock.elapsedRealtime() + 5_000
        while (SystemClock.elapsedRealtime() < deadline) {
            if (prefs.getStringSet("$id.$field", emptySet()) == expected) return
            SystemClock.sleep(50)
        }
        error("Send-result callback was not recorded")
    }

    @After fun cleanup() {
        val prefs = context.getSharedPreferences("sms_send_progress", Context.MODE_PRIVATE)
        val editor = prefs.edit()
        for (id in ids) {
            manager.cancel("otp-send:$id", 0)
            prefs.all.keys.filter { it.startsWith("$id.") }.forEach { editor.remove(it) }
        }
        editor.commit()
    }
}
