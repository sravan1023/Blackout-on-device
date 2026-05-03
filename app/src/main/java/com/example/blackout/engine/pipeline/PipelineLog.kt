package com.example.blackout.engine.pipeline

import android.os.Debug
import android.util.Log
import java.io.File

object PipelineLog {

    private const val TAG = "BlackoutPipeline"
    private var pipelineStartMs = 0L

    fun pipelineStart() {
        pipelineStartMs = System.currentTimeMillis()
        log(TAG, "════════ Pipeline START ════════")
        logMemory()
    }

    fun pipelineEnd(totalLlmCalls: Int, totalRounds: Int, passed: Boolean, detectionCount: Int) {
        val elapsed = elapsed()
        log(TAG, "════════ Pipeline END ════════")
        log(TAG, "  Total time: ${elapsed}ms")
        log(TAG, "  LLM calls: $totalLlmCalls")
        log(TAG, "  Rounds: $totalRounds")
        log(TAG, "  Validation: ${if (passed) "PASS" else "FAIL"}")
        log(TAG, "  Detections: $detectionCount")
        logMemory()
    }

    fun stepStart(step: String, round: Int = 1) {
        log("$TAG.$step", "── Step START (round $round) [+${elapsed()}ms]")
    }

    fun stepEnd(step: String, round: Int = 1, latencyMs: Long, detail: String) {
        log("$TAG.$step", "── Step END (round $round) [+${elapsed()}ms] latency=${latencyMs}ms | $detail")
    }

    fun promptSending(step: String, promptLength: Int, inputLength: Int) {
        log("$TAG.$step", "Sending prompt: ${promptLength} chars prompt + ${inputLength} chars input")
    }

    fun promptFull(step: String, prompt: String) {
        val truncated = if (prompt.length > 800) prompt.take(800) + "...[truncated ${prompt.length - 800} chars]" else prompt
        Log.d("$TAG.$step", "Full prompt:\n$truncated")
    }

    fun llmResponse(step: String, response: String) {
        val truncated = if (response.length > 500) response.take(500) + "...[truncated]" else response
        log("$TAG.$step", "LLM response (${response.length} chars):\n$truncated")
    }

    fun parseResult(step: String, detail: String) {
        log("$TAG.$step", "Parsed: $detail")
    }

    fun retry(round: Int, missedCount: Int, detail: String) {
        log("$TAG.Retry", "╔═ RETRY round $round: $missedCount missed items | $detail")
    }

    fun fallback(step: String, error: String) {
        Log.w("$TAG.$step", "FALLBACK triggered: $error")
    }

    fun warn(step: String, message: String) {
        Log.w("$TAG.$step", message)
    }

    fun error(step: String, message: String, throwable: Throwable? = null) {
        Log.e("$TAG.$step", message, throwable)
    }

    fun conversationOpen(step: String) {
        Log.d("$TAG.$step", "Conversation opened [+${elapsed()}ms]")
        logMemory()
    }

    fun conversationClose(step: String) {
        Log.d("$TAG.$step", "Conversation closed [+${elapsed()}ms]")
    }

    private fun log(tag: String, message: String) {
        Log.i(tag, message)
    }

    private fun elapsed(): Long = System.currentTimeMillis() - pipelineStartMs

    fun logMemory() {
        val heapMb = Debug.getNativeHeapAllocatedSize() / (1024f * 1024f)
        val rssMb = runCatching {
            val status = File("/proc/self/status").readText()
            val vmRssLine = status.lineSequence().firstOrNull { it.startsWith("VmRSS:") }
            val kb = vmRssLine?.trim()?.split("\\s+".toRegex())?.getOrNull(1)?.toLongOrNull() ?: 0L
            kb / 1024f
        }.getOrElse { 0f }
        Log.d(TAG, "Memory: heap=%.1fMB rss=%.1fMB".format(heapMb, rssMb))
    }
}
