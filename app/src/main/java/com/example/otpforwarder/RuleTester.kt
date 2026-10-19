package com.example.otpforwarder

/** A dry run has no Android dependencies and cannot send SMS or change saved rules. */
object RuleTester {
    sealed interface Result {
        data object EmptyMessage : Result
        data object NoKeywordMatch : Result
        data object NoOtp : Result
        data class Match(val outcome: ForwardOutcome) : Result
    }

    fun preview(body: String, config: ForwardConfig): Result {
        if (body.isBlank()) return Result.EmptyMessage
        if (config.filterKeywords.none { it.isNotBlank() && body.contains(it, ignoreCase = true) }) {
            return Result.NoKeywordMatch
        }
        // Preview disabled rules too; the UI explicitly explains that switches are ignored.
        val outcome = ForwardingEngine.resolve(body, listOf(config.copy(isEnabled = true)))
            .firstOrNull() ?: return Result.NoOtp
        return Result.Match(outcome)
    }
}
