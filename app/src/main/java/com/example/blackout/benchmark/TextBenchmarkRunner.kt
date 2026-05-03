package com.example.blackout.benchmark

import android.content.Context
import android.os.Debug
import android.util.Log
import com.example.blackout.engine.LlmEngine
import com.example.blackout.engine.pipeline.ClassificationParser
import com.example.blackout.engine.pipeline.PipelineLog
import com.example.blackout.engine.pipeline.PipelinePrompts
import org.json.JSONObject
import java.io.File

class TextBenchmarkRunner(
    private val engine: LlmEngine,
    private val context: Context,
) {
    companion object {
        private const val TAG = "BlackoutBenchmark"
    }

    data class EntryResult(
        val id: String,
        val mode: String,
        val difficulty: String,
        val inputChars: Int,
        val step1LatencyMs: Long,
        val step1TtftMs: Long,
        val step1Tokens: Int,
        val step1DecodeTokSec: Float,
        val step2LatencyMs: Long,
        val step2TtftMs: Long,
        val step2Tokens: Int,
        val step2DecodeTokSec: Float,
        val step3LatencyMs: Long,
        val step3TtftMs: Long,
        val step3Tokens: Int,
        val step3DecodeTokSec: Float,
        val totalLatencyMs: Long,
        val totalTokens: Int,
        val peakRssMb: Float,
    )

    suspend fun run(count: Int, difficulty: String? = null) {
        val entries = loadDataset()
        val filtered = if (difficulty != null) {
            entries.filter { it.getString("difficulty").equals(difficulty, ignoreCase = true) }
        } else entries
        val subset = filtered.take(count)

        Log.i(TAG, "═══════════════════════════════════════════")
        Log.i(TAG, "TEXT BENCHMARK: ${subset.size} entries, backend=${engine.activeBackend}" +
            (if (difficulty != null) ", difficulty=$difficulty" else ""))
        Log.i(TAG, "═══════════════════════════════════════════")

        if (!engine.isReady) {
            Log.e(TAG, "Engine not ready")
            return
        }

        val results = mutableListOf<EntryResult>()

        for ((idx, entry) in subset.withIndex()) {
            val id = entry.getString("id")
            val mode = entry.getString("mode")
            val difficulty = entry.getString("difficulty")
            val input = entry.getString("input")

            Log.i(TAG, "────── ${idx + 1}/${subset.size}: $id ($mode/$difficulty) ──────")

            try {
                val result = runEntry(id, mode, difficulty, input)
                results.add(result)

                Log.i(TAG, "  Step1: ${result.step1LatencyMs}ms, TTFT=${result.step1TtftMs}ms, ${result.step1DecodeTokSec} tok/s, ${result.step1Tokens} tokens")
                Log.i(TAG, "  Step2: ${result.step2LatencyMs}ms, TTFT=${result.step2TtftMs}ms, ${result.step2DecodeTokSec} tok/s, ${result.step2Tokens} tokens")
                Log.i(TAG, "  Step3: ${result.step3LatencyMs}ms, TTFT=${result.step3TtftMs}ms, ${result.step3DecodeTokSec} tok/s, ${result.step3Tokens} tokens")
                Log.i(TAG, "  Total: ${result.totalLatencyMs}ms, ${result.totalTokens} tokens, RSS=${result.peakRssMb}MB")
            } catch (e: Throwable) {
                Log.e(TAG, "  FAILED: ${e.message}")
            }
        }

        printSummary(results)
    }

    private suspend fun runEntry(id: String, mode: String, difficulty: String, input: String): EntryResult {
        val overallStart = System.currentTimeMillis()
        val rssBeforeMb = readRssMb()

        // Step 1: Classify
        val classifyPrompt = PipelinePrompts.classifyPrompt(input)
        val s1Response = engine.infer(classifyPrompt)
        val s1Metrics = engine.lastMetrics!!
        val classification = ClassificationParser.parse(s1Response)

        // Step 2: Detect
        val detectPrompt = PipelinePrompts.detectPromptFor(classification.category, input)
        val s2Response = engine.infer(detectPrompt)
        val s2Metrics = engine.lastMetrics!!

        // Step 3: Redact
        val detections = com.example.blackout.engine.pipeline.DetectionParser.parse(s2Response)
        val redactPrompt = PipelinePrompts.redactPrompt(input, detections)
        val s3Response = engine.infer(redactPrompt)
        val s3Metrics = engine.lastMetrics!!

        val totalLatency = System.currentTimeMillis() - overallStart
        val rssAfterMb = readRssMb()

        return EntryResult(
            id = id,
            mode = mode,
            difficulty = difficulty,
            inputChars = input.length,
            step1LatencyMs = s1Metrics.latencyMs,
            step1TtftMs = s1Metrics.timeToFirstTokenMs,
            step1Tokens = s1Metrics.tokenCount,
            step1DecodeTokSec = s1Metrics.decodeTokensPerSec,
            step2LatencyMs = s2Metrics.latencyMs,
            step2TtftMs = s2Metrics.timeToFirstTokenMs,
            step2Tokens = s2Metrics.tokenCount,
            step2DecodeTokSec = s2Metrics.decodeTokensPerSec,
            step3LatencyMs = s3Metrics.latencyMs,
            step3TtftMs = s3Metrics.timeToFirstTokenMs,
            step3Tokens = s3Metrics.tokenCount,
            step3DecodeTokSec = s3Metrics.decodeTokensPerSec,
            totalLatencyMs = totalLatency,
            totalTokens = s1Metrics.tokenCount + s2Metrics.tokenCount + s3Metrics.tokenCount,
            peakRssMb = maxOf(rssBeforeMb, rssAfterMb),
        )
    }

    private fun printSummary(results: List<EntryResult>) {
        if (results.isEmpty()) {
            Log.w(TAG, "No results to summarize")
            return
        }

        Log.i(TAG, "═══════════════════════════════════════════")
        Log.i(TAG, "TEXT BENCHMARK SUMMARY (${results.size} entries)")
        Log.i(TAG, "Backend: ${engine.activeBackend}")
        Log.i(TAG, "═══════════════════════════════════════════")

        Log.i(TAG, "── Per-Step Averages ──")
        Log.i(TAG, "  Step1 (Classify): avg=${results.map { it.step1LatencyMs }.average().toLong()}ms, " +
            "TTFT=${results.map { it.step1TtftMs }.average().toLong()}ms, " +
            "tok/s=%.1f, tokens=%.0f".format(
                results.map { it.step1DecodeTokSec }.average(),
                results.map { it.step1Tokens }.average()))

        Log.i(TAG, "  Step2 (Detect):   avg=${results.map { it.step2LatencyMs }.average().toLong()}ms, " +
            "TTFT=${results.map { it.step2TtftMs }.average().toLong()}ms, " +
            "tok/s=%.1f, tokens=%.0f".format(
                results.map { it.step2DecodeTokSec }.average(),
                results.map { it.step2Tokens }.average()))

        Log.i(TAG, "  Step3 (Redact):   avg=${results.map { it.step3LatencyMs }.average().toLong()}ms, " +
            "TTFT=${results.map { it.step3TtftMs }.average().toLong()}ms, " +
            "tok/s=%.1f, tokens=%.0f".format(
                results.map { it.step3DecodeTokSec }.average(),
                results.map { it.step3Tokens }.average()))

        Log.i(TAG, "── Totals ──")
        Log.i(TAG, "  Avg total latency: ${results.map { it.totalLatencyMs }.average().toLong()}ms")
        Log.i(TAG, "  Avg total tokens: %.0f".format(results.map { it.totalTokens }.average()))
        Log.i(TAG, "  Avg input chars: %.0f".format(results.map { it.inputChars }.average()))
        Log.i(TAG, "  Peak RSS: %.1fMB".format(results.maxOf { it.peakRssMb }))

        Log.i(TAG, "── By Mode ──")
        results.groupBy { it.mode }.forEach { (mode, entries) ->
            Log.i(TAG, "  $mode (${entries.size}): avg=${entries.map { it.totalLatencyMs }.average().toLong()}ms, " +
                "tok/s=%.1f".format(entries.map { it.step3DecodeTokSec }.average()))
        }

        Log.i(TAG, "── By Difficulty ──")
        results.groupBy { it.difficulty }.forEach { (diff, entries) ->
            Log.i(TAG, "  $diff (${entries.size}): avg=${entries.map { it.totalLatencyMs }.average().toLong()}ms")
        }

        Log.i(TAG, "═══════════════════════════════════════════")
    }

    private fun loadDataset(): List<JSONObject> {
        val jsonl = context.assets.open("blackout_bench.jsonl").bufferedReader().readText()
        return jsonl.lines().filter { it.isNotBlank() }.map { JSONObject(it) }
    }

    private fun readRssMb(): Float = runCatching {
        val status = File("/proc/self/status").readText()
        val vmRssLine = status.lineSequence().firstOrNull { it.startsWith("VmRSS:") }
        val kb = vmRssLine?.trim()?.split("\\s+".toRegex())?.getOrNull(1)?.toLongOrNull() ?: 0L
        kb / 1024f
    }.getOrElse { 0f }
}
