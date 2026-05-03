package com.example.blackout.pipeline

import com.example.blackout.engine.pipeline.ClassificationParser
import org.junit.Assert.assertEquals
import org.junit.Test

class ClassificationParserTest {

    @Test
    fun `parse well-formed response`() {
        val raw = "DOCUMENT_TYPE: patient record\nCATEGORY: Medical"
        val result = ClassificationParser.parse(raw)
        assertEquals("patient record", result.documentType)
        assertEquals("Medical", result.category)
    }

    @Test
    fun `parse with extra whitespace`() {
        val raw = "  DOCUMENT_TYPE:  clinical note  \n  CATEGORY:   Medical  "
        val result = ClassificationParser.parse(raw)
        assertEquals("clinical note", result.documentType)
        assertEquals("Medical", result.category)
    }

    @Test
    fun `parse with extra explanatory text`() {
        val raw = """
            The text appears to be a medical document.
            DOCUMENT_TYPE: lab result
            CATEGORY: Medical
            This contains patient health information.
        """.trimIndent()
        val result = ClassificationParser.parse(raw)
        assertEquals("lab result", result.documentType)
        assertEquals("Medical", result.category)
    }

    @Test
    fun `missing DOCUMENT_TYPE defaults to unknown`() {
        val raw = "CATEGORY: Financial"
        val result = ClassificationParser.parse(raw)
        assertEquals("unknown", result.documentType)
        assertEquals("Financial", result.category)
    }

    @Test
    fun `unrecognized category defaults to General`() {
        val raw = "DOCUMENT_TYPE: invoice\nCATEGORY: Shopping"
        val result = ClassificationParser.parse(raw)
        assertEquals("invoice", result.documentType)
        assertEquals("General", result.category)
    }

    @Test
    fun `empty response defaults to unknown and General`() {
        val result = ClassificationParser.parse("")
        assertEquals("unknown", result.documentType)
        assertEquals("General", result.category)
    }

    @Test
    fun `case insensitive category matching`() {
        val raw = "DOCUMENT_TYPE: bank statement\nCATEGORY: financial"
        val result = ClassificationParser.parse(raw)
        assertEquals("Financial", result.category)
    }
}
