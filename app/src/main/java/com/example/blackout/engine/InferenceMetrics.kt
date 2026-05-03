package com.example.blackout.engine

data class InferenceMetrics(
    val backend: String,
    val latencyMs: Long,
    val timeToFirstTokenMs: Long,
    val tokenCount: Int,
    val tokensPerSec: Float,
    val decodeTokensPerSec: Float,
    val peakNativeHeapMb: Float,
    val peakRssMb: Float,
)
