package com.example.otpforwarder

/**
 * Validation rules for the add/edit config dialog.
 *
 * Lives outside MainActivity so the rules can be unit tested without inflating a dialog.
 */
object ConfigValidator {

    const val PHONE_LENGTH = 10

    data class Result(
        val forwardToError: String? = null,
        val prefixError: String? = null,
        val keywordsError: String? = null
    ) {
        val isValid: Boolean
            get() = forwardToError == null && prefixError == null && keywordsError == null
    }

    fun validate(forwardTo: String, prefix: String, keywords: List<String>): Result {
        val trimmedNumber = forwardTo.trim()
        val trimmedPrefix = prefix.trim()

        return Result(
            forwardToError = when {
                trimmedNumber.isEmpty() -> "Forward number is required"
                !trimmedNumber.all { it.isDigit() } -> "Digits only"
                trimmedNumber.length != PHONE_LENGTH -> "Must be exactly $PHONE_LENGTH digits"
                else -> null
            },
            prefixError = if (trimmedPrefix.isEmpty()) "Message prefix is required" else null,
            keywordsError = if (keywords.none { it.isNotBlank() }) {
                "Add at least one keyword"
            } else {
                null
            }
        )
    }
}
