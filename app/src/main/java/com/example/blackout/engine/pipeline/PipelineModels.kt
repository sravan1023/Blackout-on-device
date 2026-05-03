package com.example.blackout.engine.pipeline

data class Detection(
    val category: String,
    val originalText: String,
    val placeholder: String = "",
)

data class ClassificationResult(
    val documentType: String,
    val category: String,
)

sealed interface ValidationResult {
    data object Pass : ValidationResult
    data class Fail(val missedItems: List<Detection>) : ValidationResult
}

data class PipelineResult(
    val redactedText: String,
    val documentType: String,
    val redactionCategory: String,
    val detections: List<Detection>,
    val validationPassed: Boolean,
    val roundsUsed: Int,
    val totalLlmCalls: Int,
    val totalLatencyMs: Long,
    val usedFallback: Boolean = false,
)
