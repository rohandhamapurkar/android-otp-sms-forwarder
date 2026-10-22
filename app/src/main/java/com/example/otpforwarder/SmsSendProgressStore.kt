package com.example.otpforwarder

import android.content.Context

/** Separate from backed-up forwarding preferences; callback progress is device-local. */
internal object SmsSendProgressStore {
    private const val PREFS = "sms_send_progress"
    private const val RETENTION_MS = 24 * 60 * 60 * 1000L

    @Synchronized
    fun start(context: Context, sendId: String, partCount: Int) {
        require(partCount > 0)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val editor = prefs.edit()
        // Bound stale records without storing message contents or destination numbers.
        for ((key, value) in prefs.all) {
            if (key.endsWith(".started") && value is Long && now - value > RETENTION_MS) {
                val prefix = key.removeSuffix("started")
                prefs.all.keys.filter { it.startsWith(prefix) }.forEach { editor.remove(it) }
            }
        }
        check(editor.putInt("$sendId.count", partCount).putLong("$sendId.started", now)
            .putStringSet("$sendId.sent", emptySet()).putBoolean("$sendId.failed", false).commit()) {
            "Could not track SMS send results"
        }
    }

    /** A null result means an unknown, invalid, or duplicate callback. */
    @Synchronized
    fun record(context: Context, sendId: String, partIndex: Int, successful: Boolean): SmsSendProgress? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val count = prefs.getInt("$sendId.count", 0)
        if (count <= 0) return null
        val previous = SmsSendProgress(count,
            prefs.getStringSet("$sendId.sent", emptySet()).orEmpty().mapNotNull { it.toIntOrNull() }
                .filter { it in 0 until count }.toSet(),
            prefs.getBoolean("$sendId.failed", false))
        val next = previous.record(partIndex, successful)
        if (next == previous) return null
        check(prefs.edit().putStringSet("$sendId.sent", next.sentParts.map { it.toString() }.toSet())
            .putBoolean("$sendId.failed", next.hasFailed).commit()) { "Could not save SMS send result" }
        // Once a failure is shown, later part successes must not replace it.
        return next.takeUnless { previous.hasFailed }
    }
}
