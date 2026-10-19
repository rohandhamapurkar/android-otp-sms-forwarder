package com.example.otpforwarder

/** Segments from one SMS_RECEIVED broadcast, in their original PDU order. */
data class IncomingSms(val sender: String?, val body: String)

object SmsAssembly {
    fun assemble(parts: List<IncomingSms>): List<IncomingSms> =
        parts.groupBy { it.sender }.map { (sender, segments) ->
            IncomingSms(sender, segments.joinToString("") { it.body })
        }
}
