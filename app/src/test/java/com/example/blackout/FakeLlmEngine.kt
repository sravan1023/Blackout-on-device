package com.example.blackout

import com.example.blackout.engine.LlmEngine
import com.example.blackout.engine.PreferredBackend
import com.example.blackout.engine.RedactionMode

class FakeLlmEngine(
    var nextResult: String = "Patient [NAME_1] was seen at [LOCATION_1].",
    var initShouldThrow: Boolean = false,
    var redactShouldThrow: Boolean = false,
) : LlmEngine {
    override val isReady: Boolean get() = !initShouldThrow
    override val activeBackend: String = "CPU-Fake"
    override val lastMetrics: com.example.blackout.engine.InferenceMetrics? = null

    var initCallCount = 0
    var redactCallCount = 0
    var lastRedactedText: String? = null
    var lastMode: RedactionMode? = null

    override suspend fun initialize(modelPath: String, preferredBackend: PreferredBackend) {
        initCallCount++
        if (initShouldThrow) error("Fake init failure")
    }

    override suspend fun redact(text: String, mode: RedactionMode): String {
        redactCallCount++
        lastRedactedText = text
        lastMode = mode
        if (redactShouldThrow) error("Fake redact failure")
        return nextResult
    }

    var inferCallCount = 0
    var lastInferPrompt: String? = null
    var nextInferResult: String = ""

    override suspend fun infer(prompt: String): String {
        inferCallCount++
        lastInferPrompt = prompt
        if (redactShouldThrow) error("Fake infer failure")
        return nextInferResult.ifEmpty { nextResult }
    }

    override fun close() {}
}
