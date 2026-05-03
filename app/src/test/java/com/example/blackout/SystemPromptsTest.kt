package com.example.blackout

import com.example.blackout.engine.RedactionMode
import com.example.blackout.engine.SystemPrompts
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemPromptsTest {

    @Test
    fun `HIPAA prompt contains PHI instruction`() {
        val prompt = SystemPrompts.build(RedactionMode.HIPAA, "test")
        assertTrue(prompt.contains("HIPAA", ignoreCase = true))
        assertTrue(prompt.contains("PHI", ignoreCase = true))
        assertTrue(prompt.contains("OUTPUT", ignoreCase = true))
    }

    @Test
    fun `TACTICAL prompt instructs to keep suspect info`() {
        val prompt = SystemPrompts.build(RedactionMode.TACTICAL, "test")
        assertTrue(prompt.contains("KEEP", ignoreCase = true))
        assertTrue(prompt.contains("suspect", ignoreCase = true))
        assertTrue(prompt.contains("VICTIM", ignoreCase = true))
    }

    @Test
    fun `JOURNALISM prompt mentions source protection`() {
        val prompt = SystemPrompts.build(RedactionMode.JOURNALISM, "test")
        assertTrue(prompt.contains("source", ignoreCase = true))
        assertTrue(prompt.contains("SOURCE_", ignoreCase = false))
    }

    @Test
    fun `FIELD_SERVICE prompt mentions security credentials`() {
        val prompt = SystemPrompts.build(RedactionMode.FIELD_SERVICE, "test")
        assertTrue(prompt.contains("SECURE", ignoreCase = true))
        assertTrue(prompt.contains("gate code", ignoreCase = true))
    }

    @Test
    fun `FINANCIAL prompt mentions SSN and account numbers`() {
        val prompt = SystemPrompts.build(RedactionMode.FINANCIAL, "test")
        assertTrue(prompt.contains("SSN", ignoreCase = false))
        assertTrue(prompt.contains("ACCOUNT", ignoreCase = false))
    }

    @Test
    fun `all prompts include input text in output`() {
        val inputText = "Patient John Smith was here."
        RedactionMode.entries.forEach { mode ->
            val prompt = SystemPrompts.build(mode, inputText)
            assertTrue(
                "Mode $mode prompt should contain input text",
                prompt.contains(inputText)
            )
        }
    }

    @Test
    fun `all prompts include OUTPUT marker`() {
        RedactionMode.entries.forEach { mode ->
            val prompt = SystemPrompts.build(mode, "sample")
            assertTrue(
                "Mode $mode prompt should end with OUTPUT: marker",
                prompt.contains("OUTPUT:")
            )
        }
    }
}
