package com.example.blackout.ui

import android.graphics.Bitmap
import com.example.blackout.engine.InferenceMetrics

sealed interface RedactionUiState {
    data object ModelMissing : RedactionUiState
    data object Initializing : RedactionUiState
    data object Idle : RedactionUiState
    data object Loading : RedactionUiState
    data class PipelineProgress(
        val step: Int,
        val totalSteps: Int,
        val label: String,
        val round: Int = 1,
    ) : RedactionUiState
    data class Success(
        val original: String,
        val redacted: String,
        val backend: String,
        val latencyMs: Long = 0L,
        val tokenCount: Int = 0,
        val tokensPerSecond: Float = 0f,
        val fieldsRedacted: Int = 0,
        val timeToFirstTokenMs: Long = 0L,
        val decodeTokensPerSec: Float = 0f,
        val peakMemoryMb: Float = 0f,
        val sourceBitmap: Bitmap? = null,
        val redactedBitmap: Bitmap? = null,
        val metrics: InferenceMetrics? = null,
    ) : RedactionUiState
    data class Error(val message: String) : RedactionUiState
}
