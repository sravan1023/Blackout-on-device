package com.example.blackout

import com.example.blackout.engine.RegexFallback
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RegexFallbackTest {

    // ── scan ──────────────────────────────────────────────────────────────────

    @Test
    fun `scan detects SSN with dashes`() {
        val hits = RegexFallback.scan("His SSN is 123-45-6789 on file.")
        assertTrue(hits.any { it.category == "SSN" && it.matchedText == "123-45-6789" })
    }

    @Test
    fun `scan detects email address`() {
        val hits = RegexFallback.scan("Contact jane.doe+work@example.co.uk for details.")
        assertTrue(hits.any { it.category == "EMAIL" })
    }

    @Test
    fun `scan detects US phone with dashes`() {
        val hits = RegexFallback.scan("Call me at 555-867-5309 anytime.")
        assertTrue(hits.any { it.category == "PHONE" && it.matchedText == "555-867-5309" })
    }

    @Test
    fun `scan detects US phone with parentheses`() {
        val hits = RegexFallback.scan("Reach her at (800) 555-0199.")
        assertTrue(hits.any { it.category == "PHONE" })
    }

    @Test
    fun `scan detects DOB with slashes`() {
        val hits = RegexFallback.scan("DOB: 03/15/1978")
        assertTrue(hits.any { it.category == "DOB" })
    }

    @Test
    fun `scan detects MRN token`() {
        val hits = RegexFallback.scan("MRN: 4821933 assigned at admission.")
        assertTrue(hits.any { it.category == "MRN" })
    }

    @Test
    fun `scan returns empty for clean text`() {
        val hits = RegexFallback.scan("The weather is nice today.")
        assertTrue(hits.isEmpty())
    }

    @Test
    fun `scan detects credit card number`() {
        val hits = RegexFallback.scan("Card: 4111 1111 1111 1111 expired.")
        assertTrue(hits.any { it.category == "CARD" })
    }

    // ── mergeIntoLlm ─────────────────────────────────────────────────────────

    @Test
    fun `mergeIntoLlm replaces verbatim SSN the LLM missed`() {
        val original = "SSN is 123-45-6789."
        val llmOutput = "SSN is 123-45-6789."   // LLM forgot to redact
        val hits = RegexFallback.scan(original)
        val result = RegexFallback.mergeIntoLlm(llmOutput, original, hits)
        assertTrue("SSN should be replaced", !result.contains("123-45-6789"))
        assertTrue(result.contains("[SSN_1]"))
    }

    @Test
    fun `mergeIntoLlm leaves text unchanged when LLM already redacted`() {
        val original = "SSN is 123-45-6789."
        val llmOutput = "SSN is [SSN_1]."
        val hits = RegexFallback.scan(original)
        val result = RegexFallback.mergeIntoLlm(llmOutput, original, hits)
        assertEquals(llmOutput, result)
    }

    @Test
    fun `mergeIntoLlm handles multiple regex hits`() {
        val original = "Call 555-111-2222 or email a@b.com."
        val llmOutput = "Call 555-111-2222 or email a@b.com."
        val hits = RegexFallback.scan(original)
        val result = RegexFallback.mergeIntoLlm(llmOutput, original, hits)
        assertTrue(!result.contains("555-111-2222"))
        assertTrue(!result.contains("a@b.com"))
    }

    @Test
    fun `mergeIntoLlm with empty hits returns llmOutput unchanged`() {
        val llmOutput = "Clean text here."
        val result = RegexFallback.mergeIntoLlm(llmOutput, llmOutput, emptyList())
        assertEquals(llmOutput, result)
    }
}
