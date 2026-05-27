package com.example.otpforwarder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OtpExtractionTest {

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
}
