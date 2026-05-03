package com.example.blackout.pipeline

import com.example.blackout.engine.pipeline.ValidationParser
import com.example.blackout.engine.pipeline.ValidationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidationParserTest {

    @Test
    fun `parse PASS response`() {
        val result = ValidationParser.parse("RESULT: PASS")
        assertTrue(result is ValidationResult.Pass)
    }

    @Test
    fun `parse FAIL with missed items`() {
        val raw = """
            RESULT: FAIL
            NAME: Jane Smith
            SSN: 123-45-6789
        """.trimIndent()
        val result = ValidationParser.parse(raw)
        assertTrue(result is ValidationResult.Fail)
        val fail = result as ValidationResult.Fail
        assertEquals(2, fail.missedItems.size)
        assertEquals("NAME", fail.missedItems[0].category)
        assertEquals("Jane Smith", fail.missedItems[0].originalText)
    }

    @Test
    fun `FAIL with no items treated as PASS`() {
        val result = ValidationParser.parse("RESULT: FAIL")
        assertTrue(result is ValidationResult.Pass)
    }

    @Test
    fun `no RESULT line treated as PASS`() {
        val result = ValidationParser.parse("Everything looks clean.")
        assertTrue(result is ValidationResult.Pass)
    }

    @Test
    fun `PASS with extra text`() {
        val raw = """
            The document appears properly redacted.
            RESULT: PASS
            All identifiers have been replaced.
        """.trimIndent()
        val result = ValidationParser.parse(raw)
        assertTrue(result is ValidationResult.Pass)
    }

    @Test
    fun `FAIL items only parsed after FAIL declaration`() {
        val raw = """
            RESULT: FAIL
            NAME: Leaked Name
        """.trimIndent()
        val result = ValidationParser.parse(raw)
        assertTrue(result is ValidationResult.Fail)
        assertEquals(1, (result as ValidationResult.Fail).missedItems.size)
    }
}
