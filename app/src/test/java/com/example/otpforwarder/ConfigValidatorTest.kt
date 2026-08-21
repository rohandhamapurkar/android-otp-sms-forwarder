package com.example.otpforwarder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigValidatorTest {

    private val validNumber = "9876543210"
    private val validPrefix = "my lord"
    private val validKeywords = listOf("HDFC Bank")

    @Test
    fun fullyPopulatedConfigIsValid() {
        val result = ConfigValidator.validate(validNumber, validPrefix, validKeywords)

        assertTrue(result.isValid)
        assertNull(result.forwardToError)
        assertNull(result.prefixError)
        assertNull(result.keywordsError)
    }

    // --- forward number ---

    @Test
    fun emptyForwardNumberIsRejected() {
        val result = ConfigValidator.validate("", validPrefix, validKeywords)

        assertFalse(result.isValid)
        assertEquals("Forward number is required", result.forwardToError)
    }

    @Test
    fun whitespaceOnlyForwardNumberIsRejected() {
        val result = ConfigValidator.validate("   ", validPrefix, validKeywords)

        assertFalse(result.isValid)
        assertEquals("Forward number is required", result.forwardToError)
    }

    @Test
    fun shortForwardNumberIsRejected() {
        val result = ConfigValidator.validate("98765", validPrefix, validKeywords)

        assertFalse(result.isValid)
        assertEquals("Must be exactly 10 digits", result.forwardToError)
    }

    @Test
    fun longForwardNumberIsRejected() {
        val result = ConfigValidator.validate("987654321012", validPrefix, validKeywords)

        assertFalse(result.isValid)
        assertEquals("Must be exactly 10 digits", result.forwardToError)
    }

    @Test
    fun nonNumericForwardNumberIsRejected() {
        val result = ConfigValidator.validate("98765abcde", validPrefix, validKeywords)

        assertFalse(result.isValid)
        assertEquals("Digits only", result.forwardToError)
    }

    @Test
    fun forwardNumberIsTrimmedBeforeLengthCheck() {
        val result = ConfigValidator.validate("  $validNumber  ", validPrefix, validKeywords)

        assertTrue(result.isValid)
    }

    // --- prefix ---

    @Test
    fun emptyPrefixIsRejected() {
        val result = ConfigValidator.validate(validNumber, "", validKeywords)

        assertFalse(result.isValid)
        assertEquals("Message prefix is required", result.prefixError)
    }

    @Test
    fun whitespaceOnlyPrefixIsRejected() {
        val result = ConfigValidator.validate(validNumber, "   ", validKeywords)

        assertFalse(result.isValid)
        assertEquals("Message prefix is required", result.prefixError)
    }

    // --- keywords ---

    @Test
    fun emptyKeywordListIsRejected() {
        val result = ConfigValidator.validate(validNumber, validPrefix, emptyList())

        assertFalse(result.isValid)
        assertEquals("Add at least one keyword", result.keywordsError)
    }

    @Test
    fun keywordListOfOnlyBlanksIsRejected() {
        val result = ConfigValidator.validate(validNumber, validPrefix, listOf("", "  "))

        assertFalse(result.isValid)
        assertEquals("Add at least one keyword", result.keywordsError)
    }

    @Test
    fun singleKeywordIsEnough() {
        val result = ConfigValidator.validate(validNumber, validPrefix, listOf("Swiggy"))

        assertTrue(result.isValid)
    }

    // --- combined ---

    @Test
    fun allFieldsBlankReportsAllThreeErrorsAtOnce() {
        val result = ConfigValidator.validate("", "", emptyList())

        assertFalse(result.isValid)
        assertNotNull(result.forwardToError)
        assertNotNull(result.prefixError)
        assertNotNull(result.keywordsError)
    }
}
