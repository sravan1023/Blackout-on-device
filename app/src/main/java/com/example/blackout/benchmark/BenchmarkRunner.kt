package com.example.blackout.benchmark

import android.graphics.BitmapFactory
import android.util.Log
import com.example.blackout.engine.LlmEngine
import com.example.blackout.engine.OcrProcessor
import com.example.blackout.engine.pipeline.RedactionPipeline
import java.io.File

class BenchmarkRunner(
    private val engine: LlmEngine,
) {
    companion object {
        private const val TAG = "BlackoutBenchmark"
    }

    suspend fun run(category: String, count: Int) {
        Log.i(TAG, "═══════════════════════════════════════════")
        Log.i(TAG, "BENCHMARK START: category=$category, count=$count, backend=${engine.activeBackend}")
        Log.i(TAG, "═══════════════════════════════════════════")

        val testDir = File("/sdcard/BlackoutTestData/$category")
        if (!testDir.exists() || !testDir.isDirectory) {
            Log.e(TAG, "Test data not found: ${testDir.absolutePath}")
            val parent = File("/sdcard/BlackoutTestData")
            Log.e(TAG, "Available dirs: ${parent.listFiles()?.flatMap { d -> if (d.isDirectory) d.listFiles()?.map { "${d.name}/${it.name}" }?.toList() ?: listOf(d.name) else listOf(d.name) }}")
            return
        }

        val imageFiles = testDir.walkTopDown()
            .filter { it.isFile && it.extension.lowercase() in listOf("jpg", "jpeg", "png") }
            .toList()
            .sorted()
            .take(count)

        if (imageFiles.isEmpty()) {
            Log.e(TAG, "No image files found in $testDir")
            return
        }

        Log.i(TAG, "Found ${imageFiles.size} files to process")

        if (!engine.isReady) {
            Log.e(TAG, "Engine not ready — wait for app to finish initializing")
            return
        }

        val pipeline = RedactionPipeline(engine)
        val ocrProcessor = OcrProcessor()
        val results = mutableListOf<Map<String, Any>>()

        for ((idx, file) in imageFiles.withIndex()) {
            Log.i(TAG, "────────────────────────────────────────")
            Log.i(TAG, "Processing ${idx + 1}/${imageFiles.size}: ${file.name}")

            val sampleStart = System.currentTimeMillis()
            try {
                val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                if (bitmap == null) {
                    Log.e(TAG, "Failed to decode: ${file.name}")
                    continue
                }

                val ocrStart = System.currentTimeMillis()
                val ocrText = ocrProcessor.process(bitmap)
                val ocrLatency = System.currentTimeMillis() - ocrStart
                Log.i(TAG, "OCR: ${ocrLatency}ms, ${ocrText.length} chars")
                Log.i(TAG, "OCR text: ${ocrText.take(300)}")

                if (ocrText.isBlank()) {
                    Log.w(TAG, "No text detected in ${file.name}, skipping")
                    bitmap.recycle()
                    continue
                }

                val pipelineResult = pipeline.process(ocrText)
                val totalLatency = System.currentTimeMillis() - sampleStart

                Log.i(TAG, "RESULT: ${file.name}")
                Log.i(TAG, "  Type: ${pipelineResult.documentType} | Category: ${pipelineResult.redactionCategory}")
                Log.i(TAG, "  OCR: ${ocrLatency}ms (${ocrText.length} chars)")
                Log.i(TAG, "  Pipeline: ${pipelineResult.totalLatencyMs}ms | Total: ${totalLatency}ms")
                Log.i(TAG, "  Detections: ${pipelineResult.detections.size} | Rounds: ${pipelineResult.roundsUsed} | LLM calls: ${pipelineResult.totalLlmCalls}")
                Log.i(TAG, "  Validation: ${if (pipelineResult.validationPassed) "PASS" else "FAIL"} | Fallback: ${pipelineResult.usedFallback}")
                Log.i(TAG, "  Redacted preview: ${pipelineResult.redactedText.take(400)}")

                val metrics = engine.lastMetrics
                if (metrics != null) {
                    Log.i(TAG, "  Last LLM call metrics: TTFT=${metrics.timeToFirstTokenMs}ms, decode=${metrics.decodeTokensPerSec} tok/s, tokens=${metrics.tokenCount}")
                }

                results.add(mapOf(
                    "file" to file.name,
                    "ocrMs" to ocrLatency,
                    "pipelineMs" to pipelineResult.totalLatencyMs,
                    "totalMs" to totalLatency,
                    "detections" to pipelineResult.detections.size,
                    "rounds" to pipelineResult.roundsUsed,
                    "llmCalls" to pipelineResult.totalLlmCalls,
                    "passed" to pipelineResult.validationPassed,
                    "fallback" to pipelineResult.usedFallback,
                ))

                bitmap.recycle()
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to process ${file.name}", e)
            }
        }

        Log.i(TAG, "═══════════════════════════════════════════")
        Log.i(TAG, "BENCHMARK SUMMARY")
        Log.i(TAG, "═══════════════════════════════════════════")
        if (results.isNotEmpty()) {
            Log.i(TAG, "  Samples: ${results.size}/${imageFiles.size}")
            Log.i(TAG, "  Avg OCR: ${results.map { it["ocrMs"] as Long }.average().toLong()}ms")
            Log.i(TAG, "  Avg pipeline: ${results.map { it["pipelineMs"] as Long }.average().toLong()}ms")
            Log.i(TAG, "  Avg total: ${results.map { it["totalMs"] as Long }.average().toLong()}ms")
            Log.i(TAG, "  Avg detections: %.1f".format(results.map { it["detections"] as Int }.average()))
            Log.i(TAG, "  Avg rounds: %.1f".format(results.map { it["rounds"] as Int }.average()))
            Log.i(TAG, "  Validation pass: ${results.count { it["passed"] == true }}/${results.size}")
            Log.i(TAG, "  Fallback: ${results.count { it["fallback"] == true }}/${results.size}")
        } else {
            Log.w(TAG, "  No samples processed")
        }
        Log.i(TAG, "═══════════════════════════════════════════")
        ocrProcessor.close()
    }
}
