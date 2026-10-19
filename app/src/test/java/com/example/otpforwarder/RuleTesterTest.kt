package com.example.otpforwarder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleTesterTest {
    private val config = ForwardConfig(
        id = 7,
        forwardTo = "9876543210",
        filterKeywords = listOf("Bank", "Merchant"),
        messagePrefix = "Payment"
    )

    private fun matched(body: String, rule: ForwardConfig = config): ForwardOutcome {
        val result = RuleTester.preview(body, rule)
        assertTrue("Expected a match, got $result", result is RuleTester.Result.Match)
        return (result as RuleTester.Result.Match).outcome
    }

    @Test fun enabledRulePreviewExactlyMatchesLiveEngine() {
        val body = "Ref 987654 dated 21-08. OTP is 123456 for Bank"
        assertEquals(ForwardingEngine.resolve(body, listOf(config)).single(), matched(body))
        assertEquals("123456", matched(body).otp)
    }

    @Test fun reportsBlankSamplesSeparately() {
        assertEquals(RuleTester.Result.EmptyMessage, RuleTester.preview("", config))
        assertEquals(RuleTester.Result.EmptyMessage, RuleTester.preview(" \n ", config))
    }

    @Test fun reportsNoKeywordMatchEvenIfSmsContainsACode() {
        assertEquals(RuleTester.Result.NoKeywordMatch, RuleTester.preview("OtherCompany OTP 123456", config))
    }

    @Test fun reportsMatchingKeywordWithoutAnExtractableCode() {
        assertEquals(RuleTester.Result.NoOtp, RuleTester.preview("Bank statement is ready", config))
    }

    @Test fun disabledRulesCanBePreviewedWithoutEnablingThem() {
        val disabled = config.copy(isEnabled = false)
        assertEquals("Payment: 123456", matched("Bank OTP 123456", disabled).message)
        assertFalse(disabled.isEnabled)
        assertTrue(ForwardingEngine.resolve("Bank OTP 123456", listOf(disabled)).isEmpty())
    }

    @Test fun preservesCaseInsensitiveMatchingAndKeywordOrder() {
        val outcome = matched("merchant bAnK OTP 123456")
        assertEquals("Bank", outcome.matchedFilter)
        assertEquals(config.forwardTo, outcome.forwardTo)
        assertEquals(config.id, outcome.configId)
    }

    @Test fun honorsFourAndSixDigitRuleSelections() {
        val body = "Bank OTP 123456. PIN 4321"
        assertEquals("4321", matched(body, config.copy(otpLength = 4)).otp)
        assertEquals("123456", matched(body, config.copy(otpLength = 6)).otp)
    }

    @Test fun fixedLengthMismatchDoesNotShowASuccessfulPreview() {
        assertEquals(RuleTester.Result.NoOtp, RuleTester.preview("Bank OTP 123456", config.copy(otpLength = 4)))
    }

    @Test fun excludesNumbersInsideTheFilter() {
        val rule = config.copy(filterKeywords = listOf("card ending 0366"), otpLength = 4)
        assertEquals(RuleTester.Result.NoOtp, RuleTester.preview("Bank card ending 0366", rule))
        assertEquals("0042", matched("Bank card ending 0366 PIN 0042", rule).otp)
    }

    @Test fun retainsLegacyFallbackAndMessageFormatting() {
        val rule = config.copy(messagePrefix = " Payment : ")
        assertEquals("Payment: 0366", matched("Bank account ending 0366 credited", rule).message)
    }

    @Test fun ignoresBlankFiltersAndLeavesRuleUnchanged() {
        val keywords = mutableListOf("", "Bank")
        val rule = config.copy(filterKeywords = keywords)
        val before = rule.copy(filterKeywords = keywords.toList())
        matched("Bank OTP 123456", rule)
        assertEquals(before, rule)
        assertEquals(listOf("", "Bank"), keywords)
        assertEquals(RuleTester.Result.NoKeywordMatch, RuleTester.preview("OTP 123456", rule))
    }
}
