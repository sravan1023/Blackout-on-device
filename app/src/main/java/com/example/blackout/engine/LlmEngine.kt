package com.example.blackout.engine

enum class PreferredBackend { CPU, GPU, NPU }

interface LlmEngine {
    val isReady: Boolean
    val activeBackend: String
    val lastMetrics: InferenceMetrics?
    suspend fun initialize(modelPath: String, preferredBackend: PreferredBackend = PreferredBackend.CPU)
    suspend fun redact(text: String, mode: RedactionMode): String
    suspend fun infer(prompt: String): String
    fun close()
}
