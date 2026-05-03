package com.example.blackout.pipeline

import com.example.blackout.engine.pipeline.Detection
import com.example.blackout.engine.pipeline.DeterministicRedactor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeterministicRedactorTest {

    @Test
    fun `basic replacement`() {
        val text = "Patient Jane Smith was admitted."
        val detections = listOf(Detection("NAME", "Jane Smith"))
        val output = DeterministicRedactor.redact(text, detections)
        assertEquals("Patient [NAME_1] was admitted.", output.redactedText)
        assertEquals(1, output.replacementsMade)
        assertEquals(0, output.detectionsSkipped)
    }

    @Test
    fun `multiple same-category detections get incrementing numbers`() {
        val text = "Dr. Torres referred Jane Smith."
        val detections = listOf(
            Detection("NAME", "Dr. Torres"),
            Detection("NAME", "Jane Smith"),
        )
        val output = DeterministicRedactor.redact(text, detections)
        assertTrue(output.redactedText.contains("[NAME_1]"))
        assertTrue(output.redactedText.contains("[NAME_2]"))
        assertEquals(2, output.replacementsMade)
    }

    @Test
    fun `longer detection processed first to avoid substring conflict`() {
        val text = "Visit St. Mary's Hospital in Chicago."
        val detections = listOf(
            Detection("LOCATION", "Chicago"),
            Detection("LOCATION", "St. Mary's Hospital in Chicago"),
        )
        val output = DeterministicRedactor.redact(text, detections)
        assertEquals("Visit [LOCATION_1].", output.redactedText)
        assertEquals(1, output.replacementsMade)
        assertEquals(1, output.detectionsSkipped)
    }

    @Test
    fun `detection not found is skipped`() {
        val text = "Patient was healthy."
        val detections = listOf(Detection("NAME", "Nonexistent Person"))
        val output = DeterministicRedactor.redact(text, detections)
        assertEquals("Patient was healthy.", output.redactedText)
        assertEquals(0, output.replacementsMade)
        assertEquals(1, output.detectionsSkipped)
    }

    @Test
    fun `duplicate text in input gets replaced in all instances`() {
        val text = "Call Smith at 555-1234 or Smith at home."
        val detections = listOf(Detection("NAME", "Smith"))
        val output = DeterministicRedactor.redact(text, detections)
        assertEquals("Call [NAME_1] at 555-1234 or [NAME_1] at home.", output.redactedText)
    }

    @Test
    fun `empty detections returns original text`() {
        val text = "No PII here."
        val output = DeterministicRedactor.redact(text, emptyList())
        assertEquals("No PII here.", output.redactedText)
        assertEquals(0, output.replacementsMade)
    }

    @Test
    fun `mixed categories get separate counters`() {
        val text = "Jane Smith born 01/15/1990 SSN 123-45-6789"
        val detections = listOf(
            Detection("NAME", "Jane Smith"),
            Detection("DATE", "01/15/1990"),
            Detection("SSN", "123-45-6789"),
        )
        val output = DeterministicRedactor.redact(text, detections)
        assertEquals("[NAME_1] born [DATE_1] SSN [SSN_1]", output.redactedText)
        assertEquals(3, output.replacementsMade)
    }

    @Test
    fun `enriched detections have placeholders filled in`() {
        val text = "Patient Jane Smith."
        val detections = listOf(Detection("NAME", "Jane Smith"))
        val output = DeterministicRedactor.redact(text, detections)
        assertEquals("[NAME_1]", output.detections[0].placeholder)
        assertEquals("Jane Smith", output.detections[0].originalText)
    }
}
