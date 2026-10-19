package com.example.otpforwarder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OtpLengthTest {
    @Test fun selectsFourDigitCodeEvenWhenSixDigitCodeComesFirst() {
        assertEquals("4321", OtpUtils.extractOtp("Bank OTP 123456. PIN 4321", "Bank", 4))
    }

    @Test fun selectsSixDigitCodeEvenWhenFourDigitCodeComesFirst() {
        assertEquals("123456", OtpUtils.extractOtp("Bank OTP 4321. Code 123456", "Bank", 6))
    }

    @Test fun mismatchedLengthsProduceNoCodeAndNeverTruncateLongNumbers() {
        for (length in listOf(4, 6)) {
            for (number in listOf("123", "12345", "1234567", "12345678", "1234567890")) {
                assertNull(OtpUtils.extractOtp("Bank OTP $number", "Bank", length))
                assertNull(OtpUtils.extractOtp("Bank $number is your code", "Bank", length))
            }
        }
        assertNull(OtpUtils.extractOtp("Bank OTP 123456", "Bank", 4))
        assertNull(OtpUtils.extractOtp("Bank OTP 1234", "Bank", 6))
    }

    @Test fun codeBeforeKeywordStillTakesPriorityOverOtherNumbers() {
        assertEquals("4321", OtpUtils.extractOtp("Bank ref 8765 dated 21-08. 4321 is your PIN", "Bank", 4))
        assertEquals("123456", OtpUtils.extractOtp("Bank ref 987654 dated 21-08. 123456 is your code", "Bank", 6))
    }

    @Test fun fallbackUsesOnlyRequestedLength() {
        assertEquals("4321", OtpUtils.extractOtp("Bank 123456 and 4321", "Bank", 4))
        assertEquals("123456", OtpUtils.extractOtp("Bank 4321 and 123456", "Bank", 6))
    }

    @Test fun filterNumberExclusionAndCaseInsensitiveMatchingArePreserved() {
        assertEquals("4321", OtpUtils.extractOtp("BANK card ending 0366 PIN 4321", "bank card ending 0366", 4))
        assertNull(OtpUtils.extractOtp("Bank card ending 0366", "card ending 0366", 4))
        assertNull(OtpUtils.extractOtp("Bank OTP 123456", "Other Bank", 6))
    }

    @Test fun separateRulesCanSelectDifferentLengthsFromSameMessage() {
        val base = ForwardConfig(id = 1, forwardTo = "9876543210", filterKeywords = listOf("Bank"), messagePrefix = "Four", otpLength = 4)
        val six = base.copy(id = 2, messagePrefix = "Six", otpLength = 6)
        val auto = base.copy(id = 3, messagePrefix = "Auto", otpLength = null)
        val outcomes = ForwardingEngine.resolve("Bank OTP 123456. PIN 4321", listOf(base, six, auto))
        assertEquals(listOf("Four: 4321", "Six: 123456", "Auto: 123456"), outcomes.map { it.message })
    }

    @Test fun explicitLengthWithoutACandidateDoesNotForward() {
        val config = ForwardConfig(forwardTo = "9876543210", filterKeywords = listOf("Bank"), messagePrefix = "Bank", otpLength = 4)
        assertEquals(emptyList<ForwardOutcome>(), ForwardingEngine.resolve("Bank OTP 123456", listOf(config)))
    }

    @Test fun unsupportedLengthsFailClosed() {
        assertNull(OtpUtils.extractOtp("Bank OTP 12345", "Bank", 5))
    }
}
