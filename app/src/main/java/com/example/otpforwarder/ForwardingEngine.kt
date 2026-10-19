package com.example.otpforwarder

/** One SMS the receiver should send out, derived from a matched config. */
data class ForwardOutcome(
    val configId: Int,
    val forwardTo: String,
    val message: String,
    val matchedFilter: String
)

/**
 * The decision layer between "an SMS arrived" and "send these messages".
 *
 * Kept free of Android types on purpose: SmsReceiver only runs on a device and a real SMS
 * cannot be injected into a physical phone, so all the matching and formatting rules live
 * here where JVM unit tests can exercise them directly.
 */
object ForwardingEngine {

    /** Builds the outgoing text as "Prefix: 123456", tolerating a prefix the user typed with a colon. */
    fun buildMessage(prefix: String, otp: String): String {
        val cleanPrefix = prefix.trim().removeSuffix(":").trim()
        return if (cleanPrefix.isBlank()) otp else "$cleanPrefix: $otp"
    }

    /**
     * Resolves an incoming message body against the configured rules.
     *
     * Each enabled config contributes at most one outgoing message: filters are tried in order
     * and the first that both matches the body and yields an OTP wins. Configs are independent,
     * so one body can fan out to several destinations.
     */
    fun resolve(body: String, configs: List<ForwardConfig>): List<ForwardOutcome> {
        val outcomes = mutableListOf<ForwardOutcome>()

        for (config in configs) {
            if (!config.isEnabled) continue

            for (filter in config.filterKeywords) {
                if (filter.isBlank()) continue
                if (!body.contains(filter, ignoreCase = true)) continue

                val otp = OtpUtils.extractOtp(body, filter, config.otpLength) ?: continue

                outcomes += ForwardOutcome(
                    configId = config.id,
                    forwardTo = config.forwardTo,
                    message = buildMessage(config.messagePrefix, otp),
                    matchedFilter = filter
                )
                // This config has produced its message; remaining filters are redundant.
                break
            }
        }

        return outcomes
    }
}
