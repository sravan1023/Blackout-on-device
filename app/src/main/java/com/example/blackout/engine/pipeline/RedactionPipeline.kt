package com.example.blackout.engine.pipeline

import com.example.blackout.engine.LlmEngine
import com.example.blackout.engine.RedactionMode
import com.example.blackout.engine.SystemPrompts

class RedactionPipeline(
    private val engine: LlmEngine,
) {
    companion object {
        private const val MAX_ROUNDS = 3
    }

    var onProgress: ((step: Int, totalSteps: Int, label: String, round: Int) -> Unit)? = null

    private fun reportProgress(step: Int, label: String, round: Int = 1) {
        onProgress?.invoke(step, 4, label, round)
    }

    suspend fun process(text: String): PipelineResult {
        PipelineLog.pipelineStart()
        val overallStart = System.currentTimeMillis()
        var llmCalls = 0

        // ── Step 1: CLASSIFY ──
        reportProgress(1, "Classifying document…")
        val classification: ClassificationResult
        try {
            PipelineLog.stepStart("Step1")
            val stepStart = System.currentTimeMillis()
            val classifyPrompt = PipelinePrompts.classifyPrompt(text)
            PipelineLog.promptSending("Step1", classifyPrompt.length, text.length)
            PipelineLog.promptFull("Step1", classifyPrompt)

            val rawResponse = engine.infer(classifyPrompt)
            llmCalls++
            PipelineLog.llmResponse("Step1", rawResponse)

            classification = ClassificationParser.parse(rawResponse)
            PipelineLog.stepEnd("Step1", 1, System.currentTimeMillis() - stepStart,
                "type='${classification.documentType}', category='${classification.category}'")
        } catch (e: Throwable) {
            PipelineLog.error("Step1", "Classification failed, falling back to single-pass", e)
            return fallback(text, overallStart)
        }

        // ── Step 2: DETECT (initial) ──
        reportProgress(2, "Detecting ${classification.category} identifiers…")
        var allDetections: MutableList<Detection>
        try {
            PipelineLog.stepStart("Step2")
            val stepStart = System.currentTimeMillis()
            val detectPrompt = PipelinePrompts.detectPromptFor(classification.category, text)
            PipelineLog.promptSending("Step2", detectPrompt.length, text.length)
            PipelineLog.promptFull("Step2", detectPrompt)

            val rawResponse = engine.infer(detectPrompt)
            llmCalls++
            PipelineLog.llmResponse("Step2", rawResponse)

            allDetections = DetectionParser.parse(rawResponse).toMutableList()
            PipelineLog.stepEnd("Step2", 1, System.currentTimeMillis() - stepStart,
                "${allDetections.size} detections across ${allDetections.map { it.category }.toSet()}")
        } catch (e: Throwable) {
            PipelineLog.error("Step2", "Detection failed, falling back to single-pass", e)
            return fallback(text, overallStart)
        }

        // ── Step 3 + 4: REDACT + VALIDATE loop ──
        var round = 1
        var validationPassed = false
        var redactedText = ""
        var enrichedDetections = allDetections.toList()

        while (round <= MAX_ROUNDS) {
            // Step 3: REDACT (LLM call)
            reportProgress(3, "Redacting sensitive fields…", round)
            try {
                PipelineLog.stepStart("Step3", round)
                val step3Start = System.currentTimeMillis()
                val redactPrompt = PipelinePrompts.redactPrompt(text, allDetections)
                PipelineLog.promptSending("Step3", redactPrompt.length, text.length)
                PipelineLog.promptFull("Step3", redactPrompt)

                val rawLlmOutput = engine.infer(redactPrompt)
                llmCalls++
                PipelineLog.llmResponse("Step3", rawLlmOutput)

                // Strip common preamble patterns the model adds before the actual redacted text.
                // E.g. Gemma 2B frequently echoes "REDACTED TEXT:" or adds "Here is the redacted..."
                redactedText = stripLlmPreamble(rawLlmOutput)

                val placeholderPattern = Regex("""\[([A-Z_]+)_(\d+)]""")
                val foundPlaceholders = placeholderPattern.findAll(redactedText).toList()

                if (foundPlaceholders.isEmpty() && allDetections.isNotEmpty()) {
                    // LLM produced output with no placeholders despite having detections.
                    // Fall back to deterministic string replacement so redaction still happens.
                    PipelineLog.warn("Step3", "LLM output had no placeholders — using deterministic fallback")
                    val fallbackOutput = DeterministicRedactor.redact(text, allDetections)
                    redactedText = fallbackOutput.redactedText
                    enrichedDetections = fallbackOutput.detections
                } else {
                    enrichedDetections = foundPlaceholders.mapNotNull { match ->
                        val cat = match.groupValues[1]
                        val det = allDetections.firstOrNull { it.category == cat }
                        det?.copy(placeholder = match.value)
                    }
                }

                PipelineLog.stepEnd("Step3", round, System.currentTimeMillis() - step3Start,
                    "${foundPlaceholders.size} placeholders in output")
            } catch (e: Throwable) {
                PipelineLog.error("Step3", "LLM redaction failed, using deterministic fallback", e)
                val fallbackOutput = DeterministicRedactor.redact(text, allDetections)
                redactedText = fallbackOutput.redactedText
                enrichedDetections = fallbackOutput.detections
            }

            // Step 4: VALIDATE
            reportProgress(4, if (round == 1) "Validating redaction…" else "Re-validating (round $round)…", round)
            try {
                PipelineLog.stepStart("Step4", round)
                val step4Start = System.currentTimeMillis()
                val validatePrompt = PipelinePrompts.validatePromptFor(classification.category, redactedText)
                PipelineLog.promptSending("Step4", validatePrompt.length, redactedText.length)
                PipelineLog.promptFull("Step4", validatePrompt)

                val rawValidation = engine.infer(validatePrompt)
                llmCalls++
                PipelineLog.llmResponse("Step4", rawValidation)

                val validationResult = ValidationParser.parse(rawValidation)
                PipelineLog.stepEnd("Step4", round, System.currentTimeMillis() - step4Start,
                    if (validationResult is ValidationResult.Pass) "PASS" else "FAIL")

                when (validationResult) {
                    is ValidationResult.Pass -> {
                        validationPassed = true
                        break
                    }
                    is ValidationResult.Fail -> {
                        if (round < MAX_ROUNDS) {
                            PipelineLog.retry(round + 1, validationResult.missedItems.size,
                                validationResult.missedItems.joinToString { "${it.category}: ${it.originalText}" })
                            allDetections.addAll(validationResult.missedItems)
                        } else {
                            PipelineLog.warn("Step4", "Still failing after $MAX_ROUNDS rounds — returning best effort")
                        }
                    }
                }
            } catch (e: Throwable) {
                PipelineLog.error("Step4", "Validation failed — treating as PASS (round $round)", e)
                validationPassed = true
                break
            }

            round++
        }

        val totalLatency = System.currentTimeMillis() - overallStart
        val result = PipelineResult(
            redactedText = redactedText,
            documentType = classification.documentType,
            redactionCategory = classification.category,
            detections = enrichedDetections,
            validationPassed = validationPassed,
            roundsUsed = round.coerceAtMost(MAX_ROUNDS),
            totalLlmCalls = llmCalls,
            totalLatencyMs = totalLatency,
        )
        PipelineLog.pipelineEnd(llmCalls, result.roundsUsed, validationPassed, enrichedDetections.size)
        return result
    }

    private fun stripLlmPreamble(raw: String): String {
        // Gemma 2B often echoes the final prompt marker or adds a conversational opener.
        // Strip everything up to and including the marker, then trim.
        val markers = listOf("REDACTED TEXT:", "Redacted text:", "redacted text:")
        for (marker in markers) {
            val idx = raw.indexOf(marker)
            if (idx >= 0) return raw.substring(idx + marker.length).trim()
        }
        return raw.trim()
    }

    private suspend fun fallback(text: String, overallStart: Long): PipelineResult {
        PipelineLog.fallback("Pipeline", "Executing single-pass fallback")
        return try {
            val redacted = engine.redact(text, RedactionMode.HIPAA)
            val totalLatency = System.currentTimeMillis() - overallStart
            PipelineResult(
                redactedText = redacted,
                documentType = "unknown",
                redactionCategory = "General",
                detections = emptyList(),
                validationPassed = false,
                roundsUsed = 1,
                totalLlmCalls = 1,
                totalLatencyMs = totalLatency,
                usedFallback = true,
            )
        } catch (e: Throwable) {
            PipelineLog.error("Fallback", "Even single-pass fallback failed", e)
            PipelineResult(
                redactedText = text,
                documentType = "unknown",
                redactionCategory = "General",
                detections = emptyList(),
                validationPassed = false,
                roundsUsed = 0,
                totalLlmCalls = 0,
                totalLatencyMs = System.currentTimeMillis() - overallStart,
                usedFallback = true,
            )
        }
    }
}
