package com.example.blackout.engine

// Debug/emulator engine: uses RegexFallback only (no LLM model needed).
// Selected automatically in debug builds when the model file is absent.
internal class RegexOnlyLlmEngine : LlmEngine {
    override val isReady = true
    override val activeBackend = "Regex-Debug"
    override val lastMetrics: InferenceMetrics? = null

    override suspend fun initialize(modelPath: String, preferredBackend: PreferredBackend) {}

    override suspend fun redact(text: String, mode: RedactionMode): String {
        val hits = RegexFallback.scan(text)
        if (hits.isEmpty()) return text
        var result = text
        val counters = mutableMapOf<String, Int>()
        hits.forEach { hit ->
            val n = counters.merge(hit.category, 1, Int::plus)!!
            result = result.replace(hit.matchedText, "[${hit.category}_$n]")
        }
        return result
    }

    override suspend fun infer(prompt: String): String = redact(prompt, RedactionMode.HIPAA)

    override fun close() {}
}
