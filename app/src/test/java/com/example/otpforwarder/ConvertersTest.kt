package com.example.otpforwarder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConvertersTest {

    private val converters = Converters()

    private fun roundTrip(list: List<String>): List<String> =
        converters.fromStringList(converters.fromList(list))

    @Test
    fun singleKeywordSurvivesRoundTrip() {
        assertEquals(listOf("HDFC Bank"), roundTrip(listOf("HDFC Bank")))
    }

    @Test
    fun multipleKeywordsKeepTheirOrder() {
        val keywords = listOf("HDFC Bank", "Swiggy", "Amazon")
        assertEquals(keywords, roundTrip(keywords))
    }

    @Test
    fun emptyListSurvivesRoundTrip() {
        assertTrue(roundTrip(emptyList()).isEmpty())
    }

    @Test
    fun keywordsWithCommasAreNotSplit() {
        // A naive comma-join encoding would corrupt this; JSON must keep it as one keyword.
        val keywords = listOf("Bank, Ltd", "Swiggy")
        assertEquals(keywords, roundTrip(keywords))
    }

    @Test
    fun keywordsWithQuotesAndBackslashesAreEscaped() {
        val keywords = listOf("""say "hi"""", """back\slash""")
        assertEquals(keywords, roundTrip(keywords))
    }

    @Test
    fun keywordsWithUnicodeSurviveRoundTrip() {
        val keywords = listOf("बैंक", "银行")
        assertEquals(keywords, roundTrip(keywords))
    }

    @Test
    fun blankStoredValueDecodesToEmptyListRatherThanNull() {
        assertTrue(converters.fromStringList("").isEmpty())
        assertTrue(converters.fromStringList("   ").isEmpty())
    }

    @Test
    fun nullStoredValueDecodesToEmptyList() {
        assertTrue(converters.fromStringList(null).isEmpty())
    }

    @Test
    fun corruptStoredValueDecodesToEmptyListRatherThanCrashing() {
        assertTrue(converters.fromStringList("{not valid json").isEmpty())
    }
}
