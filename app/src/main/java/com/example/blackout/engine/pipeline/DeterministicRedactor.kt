package com.example.blackout.engine.pipeline

object DeterministicRedactor {

    data class RedactionOutput(
        val redactedText: String,
        val detections: List<Detection>,
        val replacementsMade: Int,
        val detectionsSkipped: Int,
    )

    fun redact(originalText: String, detections: List<Detection>): RedactionOutput {
        val sorted = detections.sortedByDescending { it.originalText.length }
        val counters = mutableMapOf<String, Int>()
        val enriched = mutableListOf<Detection>()
        var result = originalText
        var replacementsMade = 0
        var detectionsSkipped = 0

        for (detection in sorted) {
            if (!result.contains(detection.originalText)) {
                PipelineLog.warn("Step3", "Detection not found in text, skipping: '${detection.originalText}' [${detection.category}]")
                detectionsSkipped++
                continue
            }
            val count = counters.getOrDefault(detection.category, 0) + 1
            counters[detection.category] = count
            val placeholder = "[${detection.category}_$count]"
            result = result.replace(detection.originalText, placeholder)
            enriched.add(detection.copy(placeholder = placeholder))
            replacementsMade++
        }

        PipelineLog.parseResult("Step3",
            "replacements=$replacementsMade, skipped=$detectionsSkipped, " +
            "original=${originalText.length} chars → redacted=${result.length} chars"
        )
        return RedactionOutput(result, enriched, replacementsMade, detectionsSkipped)
    }
}
