package com.example.otpforwarder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ForwardingEngineTest {

    private fun config(
        id: Int = 1,
        forwardTo: String = "9876543210",
        keywords: List<String> = listOf("HDFC Bank"),
        prefix: String = "my lord",
        enabled: Boolean = true
    ) = ForwardConfig(
        id = id,
        forwardTo = forwardTo,
        filterKeywords = keywords,
        messagePrefix = prefix,
        isEnabled = enabled
    )

    // --- buildMessage: the exact text that goes out ---

    @Test
    fun messageIsPrefixThenOtp() {
        assertEquals("my lord: 123456", ForwardingEngine.buildMessage("my lord", "123456"))
    }

    @Test
    fun messageDoesNotIncludeTheMatchedFilter() {
        // Regression: the old format was "prefix (HDFC Bank): 123456".
        val message = ForwardingEngine.buildMessage("my lord", "123456")
        assertEquals("my lord: 123456", message)
        assertTrue(!message.contains("("))
    }

    @Test
    fun messageStripsTrailingColonFromPrefix() {
        assertEquals("my lord: 123456", ForwardingEngine.buildMessage("my lord:", "123456"))
    }

    @Test
    fun messageStripsSurroundingWhitespaceFromPrefix() {
        assertEquals("my lord: 123456", ForwardingEngine.buildMessage("  my lord  ", "123456"))
    }

    @Test
    fun messageStripsWhitespaceLeftBehindByATrailingColon() {
        assertEquals("my lord: 123456", ForwardingEngine.buildMessage("my lord :", "123456"))
    }

    @Test
    fun blankPrefixSendsBareOtpWithoutLeadingSpace() {
        // Legacy rows saved before the prefix became mandatory can still be blank.
        assertEquals("123456", ForwardingEngine.buildMessage("", "123456"))
        assertEquals("123456", ForwardingEngine.buildMessage("   ", "123456"))
    }

    // --- resolve: which configs fire, and what they send ---

    @Test
    fun matchingConfigProducesOneOutcome() {
        val body = "OTP is 693547 for your HDFC Bank txn."
        val outcomes = ForwardingEngine.resolve(body, listOf(config()))

        assertEquals(1, outcomes.size)
        assertEquals("9876543210", outcomes[0].forwardTo)
        assertEquals("my lord: 693547", outcomes[0].message)
        assertEquals("HDFC Bank", outcomes[0].matchedFilter)
    }

    @Test
    fun nonMatchingBodyProducesNothing() {
        val body = "OTP is 693547 for your ICICI Bank txn."
        assertTrue(ForwardingEngine.resolve(body, listOf(config())).isEmpty())
    }

    @Test
    fun disabledConfigIsSkipped() {
        val body = "OTP is 693547 for your HDFC Bank txn."
        val outcomes = ForwardingEngine.resolve(body, listOf(config(enabled = false)))
        assertTrue(outcomes.isEmpty())
    }

    @Test
    fun filterMatchWithoutAnyOtpProducesNothing() {
        val body = "Your HDFC Bank statement is ready."
        assertTrue(ForwardingEngine.resolve(body, listOf(config())).isEmpty())
    }

    @Test
    fun configFiresAtMostOnceEvenWhenSeveralFiltersMatch() {
        val body = "OTP is 693547 for your HDFC Bank card txn."
        val outcomes = ForwardingEngine.resolve(
            body,
            listOf(config(keywords = listOf("HDFC Bank", "card", "txn")))
        )

        assertEquals(1, outcomes.size)
        assertEquals("HDFC Bank", outcomes[0].matchedFilter)
    }

    @Test
    fun laterFilterIsUsedWhenTheFirstOneYieldsNoOtp() {
        // "statement" matches nothing usable; the engine must keep trying the rest.
        val body = "OTP is 693547 for your HDFC Bank txn."
        val outcomes = ForwardingEngine.resolve(
            body,
            listOf(config(keywords = listOf("nonexistent", "HDFC Bank")))
        )

        assertEquals(1, outcomes.size)
        assertEquals("HDFC Bank", outcomes[0].matchedFilter)
    }

    @Test
    fun oneMessageCanFanOutToSeveralConfigs() {
        val body = "OTP is 693547 for your HDFC Bank txn at Swiggy."
        val outcomes = ForwardingEngine.resolve(
            body,
            listOf(
                config(id = 1, forwardTo = "1111111111", keywords = listOf("HDFC Bank"), prefix = "bank"),
                config(id = 2, forwardTo = "2222222222", keywords = listOf("Swiggy"), prefix = "food")
            )
        )

        assertEquals(2, outcomes.size)
        assertEquals("bank: 693547", outcomes[0].message)
        assertEquals("1111111111", outcomes[0].forwardTo)
        assertEquals("food: 693547", outcomes[1].message)
        assertEquals("2222222222", outcomes[1].forwardTo)
    }

    @Test
    fun filterMatchingIsCaseInsensitive() {
        val body = "OTP is 693547 for your hdfc bank txn."
        val outcomes = ForwardingEngine.resolve(body, listOf(config(keywords = listOf("HDFC BANK"))))

        assertEquals(1, outcomes.size)
        assertEquals("my lord: 693547", outcomes[0].message)
    }

    @Test
    fun blankFilterIsIgnoredRatherThanMatchingEverything() {
        // "".contains-style matching would otherwise make every message match.
        val body = "Nothing of interest here."
        assertTrue(ForwardingEngine.resolve(body, listOf(config(keywords = listOf("")))).isEmpty())
    }

    @Test
    fun emptyConfigListProducesNothing() {
        val body = "OTP is 693547 for your HDFC Bank txn."
        assertTrue(ForwardingEngine.resolve(body, emptyList()).isEmpty())
    }

    @Test
    fun transactionReferenceIsNotForwardedAsTheOtp() {
        // End-to-end guard on the anchoring fix, through the real forwarding path.
        val body = "Ref 987654 dated 21-08. OTP is 123456 for your HDFC Bank txn."
        val outcomes = ForwardingEngine.resolve(body, listOf(config()))

        assertEquals("my lord: 123456", outcomes[0].message)
    }

    // --- Real HDFC transaction SMS, end to end ---

    private val hdfcTxnSms =
        "OTP is 687398 for txn of INR 415.00 at Swiggy Limi on HDFC Bank card ending 0366. Valid till  08:29."

    @Test
    fun realHdfcTxnSmsForwardsExactlyThePrefixedOtp() {
        val outcomes = ForwardingEngine.resolve(hdfcTxnSms, listOf(config()))

        assertEquals(1, outcomes.size)
        // This is the literal text the phone would send out.
        assertEquals("my lord: 687398", outcomes[0].message)
        assertEquals("9876543210", outcomes[0].forwardTo)
    }

    @Test
    fun realHdfcTxnSmsDoesNotLeakAmountOrCardEnding() {
        val message = ForwardingEngine.resolve(hdfcTxnSms, listOf(config()))[0].message

        assertTrue(!message.contains("0366"))
        assertTrue(!message.contains("415"))
    }

    @Test
    fun realHdfcTxnSmsFansOutToBankAndMerchantConfigs() {
        val outcomes = ForwardingEngine.resolve(
            hdfcTxnSms,
            listOf(
                config(id = 1, forwardTo = "1111111111", keywords = listOf("HDFC Bank"), prefix = "bank"),
                config(id = 2, forwardTo = "2222222222", keywords = listOf("Swiggy"), prefix = "food")
            )
        )

        assertEquals(2, outcomes.size)
        assertEquals("bank: 687398", outcomes[0].message)
        assertEquals("food: 687398", outcomes[1].message)
    }

    @Test
    fun realHdfcTxnSmsIsIgnoredWhenItsConfigIsDisabled() {
        val outcomes = ForwardingEngine.resolve(hdfcTxnSms, listOf(config(enabled = false)))
        assertTrue(outcomes.isEmpty())
    }
}
