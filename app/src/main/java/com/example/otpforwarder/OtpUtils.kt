package com.example.otpforwarder

object OtpUtils {
    // Pre-compile the regexes once
    private val OTP_6_PATTERN = Regex("""\b(\d{6})\b""")
    private val OTP_4_PATTERN = Regex("""\b(\d{4})\b""")

    /**
     * Extracts a 4 or 6-digit OTP from the message body, provided the filter matches.
     * To avoid false positives (like card endings in the filter itself), 
     * it searches in the text with the filter removed.
     */
    fun extractOtp(body: String, matchedFilter: String): String? {
        if (!body.contains(matchedFilter, ignoreCase = true)) {
            return null
        }

        // Remove the matched filter from the body to avoid picking up 
        // numbers that are part of the filter (e.g., "card ending 1234")
        val bodyWithoutFilter = body.replace(matchedFilter, "", ignoreCase = true)

        // Check for 6-digit OTP first, then 4-digit OTP
        return OTP_6_PATTERN.find(bodyWithoutFilter)?.groupValues?.get(1)
            ?: OTP_4_PATTERN.find(bodyWithoutFilter)?.groupValues?.get(1)
    }
}
