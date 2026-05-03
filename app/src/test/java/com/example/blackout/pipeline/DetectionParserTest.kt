package com.example.blackout.pipeline

import com.example.blackout.engine.pipeline.DetectionParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DetectionParserTest {

    @Test
    fun `parse normal detection lines`() {
        val raw = """
            NAME: Jane Smith
            DATE: 04/12/1978
            SSN: 123-45-6789
            PHONE: (312) 555-0148
        """.trimIndent()
        val result = DetectionParser.parse(raw)
        assertEquals(4, result.size)
        assertEquals("NAME", result[0].category)
        assertEquals("Jane Smith", result[0].originalText)
        assertEquals("SSN", result[2].category)
        assertEquals("123-45-6789", result[2].originalText)
    }

    @Test
    fun `skip explanatory text lines`() {
        val raw = """
            Here are the detected identifiers:
            NAME: John Doe
            The patient was born on:
            DATE: 01/15/1990
        """.trimIndent()
        val result = DetectionParser.parse(raw)
        assertEquals(2, result.size)
    }

    @Test
    fun `deduplicate same category and text`() {
        val raw = """
            NAME: Jane Smith
            NAME: Jane Smith
            NAME: Dr. Torres
        """.trimIndent()
        val result = DetectionParser.parse(raw)
        assertEquals(2, result.size)
    }

    @Test
    fun `empty input returns empty list`() {
        assertEquals(0, DetectionParser.parse("").size)
    }

    @Test
    fun `handles lines without colon`() {
        val raw = """
            NAME: Valid Name
            This is not a detection
            Another invalid line
            SSN: 111-22-3333
        """.trimIndent()
        val result = DetectionParser.parse(raw)
        assertEquals(2, result.size)
    }

    @Test
    fun `preserves original text exactly`() {
        val raw = "LOCATION: St. Mary's Hospital in Chicago, IL"
        val result = DetectionParser.parse(raw)
        assertEquals(1, result.size)
        assertEquals("St. Mary's Hospital in Chicago, IL", result[0].originalText)
    }
}
