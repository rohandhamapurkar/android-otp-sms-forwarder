package com.example.otpforwarder

/** Tracks modem callbacks for one outgoing message; contains no OTP or message text. */
data class SmsSendProgress(
    val partCount: Int,
    val sentParts: Set<Int> = emptySet(),
    val hasFailed: Boolean = false
) {
    init { require(partCount > 0) }

    enum class Status { QUEUED, SENT, FAILED }

    val status: Status
        get() = when {
            hasFailed -> Status.FAILED
            sentParts.size == partCount -> Status.SENT
            else -> Status.QUEUED
        }

    fun record(partIndex: Int, successful: Boolean): SmsSendProgress {
        if (partIndex !in 0 until partCount) return this
        return copy(
            sentParts = if (successful) sentParts + partIndex else sentParts,
            hasFailed = hasFailed || !successful
        )
    }
}
