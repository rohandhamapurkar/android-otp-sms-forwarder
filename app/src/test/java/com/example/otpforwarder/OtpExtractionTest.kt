package com.example.otpforwarder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OtpExtractionTest {

    // We can't test the actual SmsReceiver class easily because it has Android dependencies.
    // However, we can test the extraction logic if we move it to a helper or make it accessible.
    
    @Test
    fun testOtpExtractionWithMatchingFilter() {
        val message = "OTP is 693547 for txn of INR 2.00 at Swiggy Limi on HDFC Bank card ending 0366. Valid till 01:54."
        val filter = "card ending 0366"
        
        val otp = extractOtpLogic(message, filter)
        assertEquals("693547", otp)
    }

    @Test
    fun testOtpExtractionWithNonMatchingFilter() {
        val message = "OTP is 693547 for txn of INR 2.00 at Swiggy Limi on HDFC Bank card ending 9999."
        val filter = "card ending 0366"
        
        val otp = extractOtpLogic(message, filter)
        assertNull(otp)
    }

    @Test
    fun testOtpExtractionWithNoSixDigits() {
        val message = "Your code is 123 for card ending 0366."
        val filter = "card ending 0366"
        
        val otp = extractOtpLogic(message, filter)
        assertNull(otp)
    }

    // This is a copy of the logic in SmsReceiver for testing purposes.
    // In a real project, you'd move this to a separate Utility class.
    private fun extractOtpLogic(message: String, filter: String): String? {
        if (!message.contains(filter, ignoreCase = true)) {
            return null
        }
        val otpRegex = Regex("""\b(\d{6})\b""")
        val matchResult = otpRegex.find(message)
        return matchResult?.groupValues?.get(1)
    }
}
