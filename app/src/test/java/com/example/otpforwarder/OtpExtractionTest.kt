package com.example.otpforwarder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OtpExtractionTest {

    @Test
    fun existingPermissiveFallbackRemainsSupported() {
        assertEquals("0366", OtpUtils.extractOtp("HDFC Bank account ending 0366 credited", "HDFC Bank"))
        assertEquals("778899", OtpUtils.extractOtp("778899 - use this at Amazon to continue", "Amazon"))
    }

    @Test
    fun existingFilterRemovalAndNumberPriorityRemainUnchanged() {
        // Compatibility snapshots: changing these requires a deliberate matching-policy change.
        assertEquals("987654", OtpUtils.extractOtp("Ref 987654. OTP is 123456", "OTP"))
        assertEquals("123456", OtpUtils.extractOtp("Amazon 4321 ref 123456", "Amazon"))
    }

    @Test
    fun keywordAnchoringStillAcceptsFiveAndEightDigitCodes() {
        assertEquals("12345", OtpUtils.extractOtp("Amazon OTP is 12345", "Amazon"))
        assertEquals("12345678", OtpUtils.extractOtp("Amazon OTP is 12345678", "Amazon"))
    }

    @Test
    fun testOtpExtractionWithMatchingFilter() {
        val message = "OTP is 693547 for txn of INR 2.00 at Swiggy Limi on HDFC Bank card ending 0366. Valid till 01:54."
        val filter = "HDFC Bank"

        val otp = OtpUtils.extractOtp(message, filter)
        assertEquals("693547", otp)
    }

    @Test
    fun testOtpExtractionWithNonMatchingFilter() {
        val message = "OTP is 693547 for txn of INR 2.00 at Swiggy Limi on HDFC Bank card ending 9999."
        val filter = "ICICI Bank"

        val otp = OtpUtils.extractOtp(message, filter)
        assertNull(otp)
    }

    @Test
    fun testOtpExtractionAvoidsFilterNumbers() {
        // This was previously failing because "0366" was being picked up as a 4-digit OTP.
        // Now we remove the matched filter before searching.
        val message = "Your code is 1234 for card ending 0366."
        val filter = "card ending 0366"

        val otp = OtpUtils.extractOtp(message, filter)
        assertEquals("1234", otp)
    }

    @Test
    fun testOtpExtractionWithNoDigits() {
        val message = "Your login was successful for card ending 0366."
        val filter = "card ending 0366"

        val otp = OtpUtils.extractOtp(message, filter)
        assertNull(otp)
    }

    @Test
    fun testOtpExtractionWithFourDigits() {
        val message = "Your login code is 1234 for Swiggy."
        val filter = "Swiggy"

        val otp = OtpUtils.extractOtp(message, filter)
        assertEquals("1234", otp)
    }

    @Test
    fun testOtpExtractionSixDigitsPreferredOverFour() {
        val message = "Code 123456 and extra 7890 for Amazon."
        val filter = "Amazon"

        val otp = OtpUtils.extractOtp(message, filter)
        assertEquals("123456", otp)
    }

    // --- Keyword anchoring: the code near an OTP word wins over an earlier number ---

    @Test
    fun testTransactionRefBeforeOtpIsNotMistakenForCode() {
        // Plain "first 6-digit number" would wrongly return the reference 987654.
        val message = "Ref 987654 dated 21-08. OTP is 123456 for your HDFC Bank txn."
        val filter = "HDFC Bank"

        val otp = OtpUtils.extractOtp(message, filter)
        assertEquals("123456", otp)
    }

    @Test
    fun testAmountBeforeOtpIsNotMistakenForCode() {
        val message = "INR 250000 debited at Amazon. Use code 445566 to confirm."
        val filter = "Amazon"

        val otp = OtpUtils.extractOtp(message, filter)
        assertEquals("445566", otp)
    }

    @Test
    fun testCodeStatedBeforeKeyword() {
        val message = "112233 is your Amazon verification code. Do not share it."
        val filter = "Amazon"

        val otp = OtpUtils.extractOtp(message, filter)
        assertEquals("112233", otp)
    }

    @Test
    fun testKeywordAnchoringIsCaseInsensitive() {
        val message = "Ref 987654. otp 445566 for Swiggy."
        val filter = "Swiggy"

        val otp = OtpUtils.extractOtp(message, filter)
        assertEquals("445566", otp)
    }

    @Test
    fun testFourDigitPinAnchoredToKeyword() {
        val message = "Order 998877 confirmed. Delivery PIN 4321 for Swiggy."
        val filter = "Swiggy"

        val otp = OtpUtils.extractOtp(message, filter)
        assertEquals("4321", otp)
    }

    @Test
    fun testFallbackUsedWhenNoKeywordPresent() {
        // No OTP word anywhere, so the 6-digit fallback still applies.
        val message = "778899 - use this at Amazon to continue."
        val filter = "Amazon"

        val otp = OtpUtils.extractOtp(message, filter)
        assertEquals("778899", otp)
    }

    @Test
    fun testLongNumbersAreNotTreatedAsOtp() {
        // A 10-digit account/phone number must not be sliced into a 6-digit "code".
        val message = "Account 1234567890 credited at HDFC Bank."
        val filter = "HDFC Bank"

        val otp = OtpUtils.extractOtp(message, filter)
        assertNull(otp)
    }

    @Test
    fun testFilterRemovalDoesNotJoinAdjacentDigits() {
        // Removing the filter must not glue "12" and "3456" into a bogus "123456".
        val message = "12 Amazon 3456"
        val filter = "Amazon"

        val otp = OtpUtils.extractOtp(message, filter)
        assertEquals("3456", otp)
    }

    // --- Real HDFC transaction SMS ---
    //
    // Distractors: the amount "415.00", the card ending "0366", and the time "08:29".
    // The card ending is the dangerous one: it is a standalone 4-digit number, so the
    // 4-digit fallback would return it if keyword anchoring ever stopped working.

    private val hdfcTxnSms =
        "OTP is 687398 for txn of INR 415.00 at Swiggy Limi on HDFC Bank card ending 0366. Valid till  08:29."

    @Test
    fun testRealHdfcTxnSmsWithBankFilter() {
        assertEquals("687398", OtpUtils.extractOtp(hdfcTxnSms, "HDFC Bank"))
    }

    @Test
    fun testRealHdfcTxnSmsWithPartialBankFilter() {
        assertEquals("687398", OtpUtils.extractOtp(hdfcTxnSms, "HDFC"))
    }

    @Test
    fun testRealHdfcTxnSmsWithMerchantFilter() {
        // Filtering on the merchant leaves "card ending 0366" in the body, so the OTP
        // keyword is the only thing keeping 0366 from being forwarded.
        assertEquals("687398", OtpUtils.extractOtp(hdfcTxnSms, "Swiggy"))
    }

    @Test
    fun testRealHdfcTxnSmsWithCardEndingFilter() {
        assertEquals("687398", OtpUtils.extractOtp(hdfcTxnSms, "card ending 0366"))
    }

    @Test
    fun testRealHdfcTxnSmsIgnoresAmountCardEndingAndTime() {
        val otp = OtpUtils.extractOtp(hdfcTxnSms, "HDFC Bank")

        assertNotEquals("0366", otp)   // card ending
        assertNotEquals("4150", otp)   // amount digits
        assertNotEquals("0829", otp)   // time
    }

    @Test
    fun testRealHdfcTxnSmsWithNonMatchingFilter() {
        assertNull(OtpUtils.extractOtp(hdfcTxnSms, "ICICI Bank"))
    }
}
