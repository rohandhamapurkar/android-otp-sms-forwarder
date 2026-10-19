package com.example.otpforwarder

import org.junit.Assert.assertEquals
import org.junit.Test

class SmsAssemblyTest {
    @Test fun singlePartRetainsExistingMatching() {
        val body = "OTP is 687398 for HDFC Bank card ending 0366."
        val config = ForwardConfig(forwardTo = "9876543210", filterKeywords = listOf("hdfc"), messagePrefix = "Bank")
        val assembled = SmsAssembly.assemble(listOf(IncomingSms("Bank", body))).single()
        assertEquals(ForwardingEngine.resolve(body, listOf(config)), ForwardingEngine.resolve(assembled.body, listOf(config)))
    }

    @Test fun joinsKeywordAndCodeAcrossSegmentsWithoutAddingWhitespace() {
        val assembled = SmsAssembly.assemble(listOf(
            IncomingSms("Bank", "OTP is 687"),
            IncomingSms("Bank", "398 for HD"),
            IncomingSms("Bank", "FC Bank card ending 0366.")
        )).single()
        assertEquals("687398", OtpUtils.extractOtp(assembled.body, "HDFC Bank"))
    }

    @Test fun keepsDifferentSendersSeparate() {
        assertEquals(listOf(IncomingSms("A", "123456"), IncomingSms("B", "Bank")),
            SmsAssembly.assemble(listOf(IncomingSms("A", "123456"), IncomingSms("B", "Bank"))))
    }
}
