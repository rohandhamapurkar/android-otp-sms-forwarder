package com.example.otpforwarder

object OtpUtils {
    // Words that typically introduce (or trail) the actual one-time code.
    private const val OTP_WORDS = """otp|o\.t\.p|code|pin|passcode|password"""

    // "OTP is 123456", "your code: 4321", "OTP 123456"
    private val KEYWORD_BEFORE_PATTERN = Regex(
        """\b(?:$OTP_WORDS)\b[^\d\n]{0,25}?(\d{4,8})(?!\d)""",
        RegexOption.IGNORE_CASE
    )

    // "123456 is your OTP", "4321 is the verification code"
    private val KEYWORD_AFTER_PATTERN = Regex(
        """(?<!\d)(\d{4,8})(?!\d)[^\d\n]{0,25}?\b(?:$OTP_WORDS)\b""",
        RegexOption.IGNORE_CASE
    )

    // Fallbacks when no keyword is present at all.
    private val OTP_6_PATTERN = Regex("""\b(\d{6})\b""")
    private val OTP_4_PATTERN = Regex("""\b(\d{4})\b""")

    private val exactLengthPatterns = listOf(4, 6).associateWith { length ->
        listOf(
            Regex("""\b(?:$OTP_WORDS)\b[^\d\n]{0,25}?(\d{$length})(?!\d)""", RegexOption.IGNORE_CASE),
            Regex("""(?<!\d)(\d{$length})(?!\d)[^\d\n]{0,25}?\b(?:$OTP_WORDS)\b""", RegexOption.IGNORE_CASE),
            Regex("""\b(\d{$length})\b""")
        )
    }

    /**
     * Extracts a one-time code from the message body, provided the filter matches.
     *
     * Codes anchored to an OTP keyword ("OTP is 123456", "123456 is your code") win, so a
     * transaction reference that happens to appear earlier is not mistaken for the code.
     * Only when no keyword is found does it fall back to the first 6- then 4-digit number.
     *
     * To avoid false positives, the search runs on the text with the matched filter removed
     * (e.g. a filter of "card ending 0366" must not contribute "0366" as the code).
     */
    fun extractOtp(body: String, matchedFilter: String, otpLength: Int? = null): String? {
        if (!body.contains(matchedFilter, ignoreCase = true)) {
            return null
        }

        val bodyWithoutFilter = body.replace(matchedFilter, " ", ignoreCase = true)

        if (otpLength != null) {
            // Search for a full code of the requested length, never a slice of a longer number.
            val patterns = exactLengthPatterns[otpLength] ?: return null
            return patterns.firstNotNullOfOrNull { it.find(bodyWithoutFilter)?.groupValues?.get(1) }
        }

        return KEYWORD_BEFORE_PATTERN.find(bodyWithoutFilter)?.groupValues?.get(1)
            ?: KEYWORD_AFTER_PATTERN.find(bodyWithoutFilter)?.groupValues?.get(1)
            ?: OTP_6_PATTERN.find(bodyWithoutFilter)?.groupValues?.get(1)
            ?: OTP_4_PATTERN.find(bodyWithoutFilter)?.groupValues?.get(1)
    }
}
