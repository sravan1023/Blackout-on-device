package com.example.blackout.engine.pipeline

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.Log
import com.example.blackout.engine.LlmEngine
import com.example.blackout.engine.OcrElement
import com.example.blackout.engine.OcrResult

class ImageRedactionPipeline(
    private val engine: LlmEngine,
) {
    companion object {
        private const val TAG = "RedactoPipeline.Image"
        private const val MAX_ELEMENTS_PER_CHUNK = 150
        private const val MAX_VALIDATE_ROUNDS = 3
    }

    var onProgress: ((step: Int, totalSteps: Int, label: String, round: Int) -> Unit)? = null

    private fun reportProgress(step: Int, label: String, round: Int = 1) {
        onProgress?.invoke(step, 4, label, round)
    }

    data class ImageRedactionResult(
        val redactedBitmap: Bitmap,
        val redactedText: String,
        val documentType: String,
        val category: String,
        val totalElements: Int,
        val redactedElements: Int,
        val redactedIndices: Set<Int>,
        val categoryMap: Map<Int, String>,
        val roundsUsed: Int,
        val totalLlmCalls: Int,
        val totalLatencyMs: Long,
        val validationPassed: Boolean,
    )

    suspend fun process(bitmap: Bitmap, ocrResult: OcrResult): ImageRedactionResult {
        PipelineLog.pipelineStart()
        val overallStart = System.currentTimeMillis()
        var llmCalls = 0
        val elements = ocrResult.elements

        Log.i(TAG, "Processing ${elements.size} OCR elements")

        // ── Step 1: CLASSIFY ──
        reportProgress(1, "Classifying document…")
        PipelineLog.stepStart("Step1")
        val step1Start = System.currentTimeMillis()
        val classifyPrompt = PipelinePrompts.classifyPrompt(ocrResult.fullText.take(2000))
        val classifyResponse = try {
            engine.infer(classifyPrompt).also { llmCalls++ }
        } catch (e: Throwable) {
            PipelineLog.error("Step1", "Classification failed", e)
            "DOCUMENT_TYPE: unknown\nCATEGORY: General"
        }
        val classification = ClassificationParser.parse(classifyResponse)
        PipelineLog.stepEnd("Step1", 1, System.currentTimeMillis() - step1Start,
            "type='${classification.documentType}', category='${classification.category}'")

        // ── Step 2: DETECT via indexed elements ──
        reportProgress(2, "Detecting ${classification.category} identifiers…")

        val allRedactedIndices = mutableSetOf<Int>()
        val allCategoryMap = mutableMapOf<Int, String>()

        val chunks = chunkElements(elements)
        Log.i(TAG, "Split ${elements.size} elements into ${chunks.size} chunk(s)")

        for ((chunkIdx, chunk) in chunks.withIndex()) {
            PipelineLog.stepStart("Step2", chunkIdx + 1)
            val step2Start = System.currentTimeMillis()

            val indexedText = buildIndexedPrompt(chunk, classification)
            PipelineLog.promptSending("Step2", indexedText.length, 0)
            PipelineLog.promptFull("Step2", indexedText)

            try {
                val response = engine.infer(indexedText)
                llmCalls++
                PipelineLog.llmResponse("Step2", response)

                val parsed = parseIndexedResponse(response, chunk)
                allRedactedIndices.addAll(parsed.indices)
                allCategoryMap.putAll(parsed.categoryMap)

                PipelineLog.stepEnd("Step2", chunkIdx + 1, System.currentTimeMillis() - step2Start,
                    "chunk ${chunkIdx + 1}/${chunks.size}: ${parsed.indices.size} elements flagged")
            } catch (e: Throwable) {
                PipelineLog.error("Step2", "Detection failed for chunk ${chunkIdx + 1}", e)
            }
        }

        Log.i(TAG, "Total flagged: ${allRedactedIndices.size}/${elements.size} elements")

        // ── Step 3: REDACT (draw on bitmap) ──
        reportProgress(3, "Redacting ${allRedactedIndices.size} fields…")
        PipelineLog.stepStart("Step3")
        val step3Start = System.currentTimeMillis()

        val redactedBitmap = drawRedactionBoxes(bitmap, elements, allRedactedIndices, allCategoryMap)
        val redactedText = buildRedactedText(elements, allRedactedIndices, allCategoryMap)

        PipelineLog.stepEnd("Step3", 1, System.currentTimeMillis() - step3Start,
            "${allRedactedIndices.size} boxes drawn")

        // ── Step 4: VALIDATE ──
        reportProgress(4, "Validating redaction…")
        var validationPassed = false
        var round = 1

        while (round <= MAX_VALIDATE_ROUNDS) {
            PipelineLog.stepStart("Step4", round)
            val step4Start = System.currentTimeMillis()

            val validatePrompt = buildValidatePrompt(elements, allRedactedIndices, classification.category)
            try {
                val response = engine.infer(validatePrompt)
                llmCalls++
                PipelineLog.llmResponse("Step4", response)

                val result = ValidationParser.parse(response)
                PipelineLog.stepEnd("Step4", round, System.currentTimeMillis() - step4Start,
                    if (result is ValidationResult.Pass) "PASS" else "FAIL")

                when (result) {
                    is ValidationResult.Pass -> {
                        validationPassed = true
                        break
                    }
                    is ValidationResult.Fail -> {
                        if (round < MAX_VALIDATE_ROUNDS) {
                            val newIndices = findMissedIndices(elements, result.missedItems)
                            if (newIndices.isEmpty()) {
                                validationPassed = true
                                break
                            }
                            Log.i(TAG, "Validation round $round: ${newIndices.size} new elements to redact")
                            allRedactedIndices.addAll(newIndices)
                            drawAdditionalBoxes(redactedBitmap, elements, newIndices)
                        }
                    }
                }
            } catch (e: Throwable) {
                PipelineLog.error("Step4", "Validation failed", e)
                validationPassed = true
                break
            }
            round++
        }

        val totalLatency = System.currentTimeMillis() - overallStart
        PipelineLog.pipelineEnd(llmCalls, round.coerceAtMost(MAX_VALIDATE_ROUNDS), validationPassed, allRedactedIndices.size)

        return ImageRedactionResult(
            redactedBitmap = redactedBitmap,
            redactedText = redactedText,
            documentType = classification.documentType,
            category = classification.category,
            totalElements = elements.size,
            redactedElements = allRedactedIndices.size,
            redactedIndices = allRedactedIndices,
            categoryMap = allCategoryMap,
            roundsUsed = round.coerceAtMost(MAX_VALIDATE_ROUNDS),
            totalLlmCalls = llmCalls,
            totalLatencyMs = totalLatency,
            validationPassed = validationPassed,
        )
    }

    private data class ChunkedElement(
        val globalIndex: Int,
        val element: OcrElement,
    )

    private fun chunkElements(elements: List<OcrElement>): List<List<ChunkedElement>> {
        val indexed = elements.mapIndexed { i, el -> ChunkedElement(i, el) }
        return indexed.chunked(MAX_ELEMENTS_PER_CHUNK)
    }

    private fun buildIndexedPrompt(chunk: List<ChunkedElement>, classification: ClassificationResult): String {
        val elementLines = chunk.joinToString("\n") { "[${it.globalIndex}] ${it.element.text}" }
        val categoryRules = when (classification.category.lowercase()) {
            "medical" -> """
REDACT: person names, dates (month/day), phone/fax, email, SSN, MRN,
insurance IDs, addresses, account numbers, license numbers, URLs, IPs.
PRESERVE: diagnoses, medications, vitals, body locations, lab values,
clinical procedures, ages under 90, year alone, state names alone."""
            "financial" -> """
REDACT: person names, SSN, DOB, addresses, personal phone/email,
account numbers, routing numbers, card numbers, tax IDs, transaction IDs.
PRESERVE: dollar amounts, institution names, toll-free numbers,
account types, interest rates, tax form labels, financial terms."""
            "legal" -> """
REDACT: buyer/seller/tenant names, personal addresses, SSN, phone,
email, account numbers, license numbers, notary numbers.
PRESERVE: property specs, legal terms, dollar amounts, zoning,
company names, general location descriptions."""
            "tactical" -> """
REDACT: victim names, witness names, minor names, victim/witness
home addresses, victim/witness phone/email.
PRESERVE: suspect descriptions, suspect vehicle info, officer names,
crime scene locations, case numbers, crime details."""
            "journalism" -> """
REDACT: source names/aliases, meeting locations, source phone/email,
source identifying details, badge/employee IDs.
PRESERVE: public official names, institution names, reporter names,
published facts, general regions."""
            "field service" -> """
REDACT: customer names, customer addresses, customer phone/email,
gate codes, alarm codes, Wi-Fi passwords, access PINs, account numbers.
PRESERVE: equipment make/model/serial, fault codes, parts numbers,
service instructions, technician names."""
            else -> """
REDACT: person names, dates, phone numbers, email addresses, SSN,
addresses, account numbers, any unique identifying numbers.
PRESERVE: non-personal information, general terms."""
        }

        return """
You are a PII detection engine. Below are numbered text elements
from a ${classification.documentType} document (${classification.category} category).

Return the INDEX numbers of elements that contain sensitive
personal information that must be redacted.
$categoryRules

Format: one line per element to redact:
INDEX:CATEGORY

Example:
3:NAME
5:DATE
8:PHONE

If an element is SAFE (not PII), do NOT list it.
Output ONLY index:category lines. No explanations.

TEXT ELEMENTS:
$elementLines
""".trimIndent()
    }

    private data class IndexedDetectionResult(
        val indices: Set<Int>,
        val categoryMap: Map<Int, String>,
    )

    private fun parseIndexedResponse(response: String, chunk: List<ChunkedElement>): IndexedDetectionResult {
        val validGlobalIndices = chunk.map { it.globalIndex }.toSet()
        val pattern = Regex("""^(\d+)\s*:\s*([A-Z_]+)""")
        val indices = mutableSetOf<Int>()
        val categoryMap = mutableMapOf<Int, String>()

        for (line in response.lines().map { it.trim() }.filter { it.isNotBlank() }) {
            val match = pattern.matchEntire(line) ?: continue
            val idx = match.groupValues[1].toIntOrNull() ?: continue
            val cat = match.groupValues[2]
            if (idx in validGlobalIndices) {
                indices.add(idx)
                categoryMap[idx] = cat
            } else {
                PipelineLog.warn("Step2", "Index $idx out of range, skipping")
            }
        }

        PipelineLog.parseResult("Step2", "${indices.size} indices flagged: $indices")
        return IndexedDetectionResult(indices, categoryMap)
    }

    private fun drawRedactionBoxes(
        original: Bitmap,
        elements: List<OcrElement>,
        redactedIndices: Set<Int>,
        categoryMap: Map<Int, String>,
    ): Bitmap {
        val mutable = original.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(mutable)
        val paint = Paint().apply {
            color = android.graphics.Color.BLACK
            style = Paint.Style.FILL
        }

        for (idx in redactedIndices) {
            val el = elements.getOrNull(idx) ?: continue
            val rect = RectF(el.boundingBox)
            rect.inset(-2f, -2f)
            canvas.drawRect(rect, paint)
        }

        return mutable
    }

    private fun drawAdditionalBoxes(
        bitmap: Bitmap,
        elements: List<OcrElement>,
        newIndices: Set<Int>,
    ) {
        val canvas = Canvas(bitmap)
        val paint = Paint().apply {
            color = android.graphics.Color.BLACK
            style = Paint.Style.FILL
        }
        for (idx in newIndices) {
            val el = elements.getOrNull(idx) ?: continue
            canvas.drawRect(RectF(el.boundingBox), paint)
        }
    }

    private fun buildRedactedText(
        elements: List<OcrElement>,
        redactedIndices: Set<Int>,
        categoryMap: Map<Int, String>,
    ): String {
        val counters = mutableMapOf<String, Int>()
        return elements.mapIndexed { idx, el ->
            if (idx in redactedIndices) {
                val cat = categoryMap[idx] ?: "ID"
                val count = counters.getOrDefault(cat, 0) + 1
                counters[cat] = count
                "[${cat}_$count]"
            } else {
                el.text
            }
        }.joinToString(" ")
    }

    private fun buildValidatePrompt(
        elements: List<OcrElement>,
        redactedIndices: Set<Int>,
        category: String,
    ): String {
        val visibleElements = elements.mapIndexedNotNull { idx, el ->
            if (idx !in redactedIndices) "[${idx}] ${el.text}" else null
        }
        return """
You are a privacy auditor. Below are the REMAINING VISIBLE text
elements from a redacted $category document. Elements that were
already redacted are not shown.

Check if any of these visible elements still contain PII that
should have been redacted (names, dates, phone numbers, emails,
SSNs, addresses, account numbers, etc.).

If ALL visible elements are safe, respond: RESULT: PASS
If some still contain PII, respond: RESULT: FAIL
Then list each leaked element index and category:
INDEX:CATEGORY

VISIBLE ELEMENTS:
${visibleElements.joinToString("\n")}
""".trimIndent()
    }

    private fun findMissedIndices(
        elements: List<OcrElement>,
        missedItems: List<Detection>,
    ): Set<Int> {
        val newIndices = mutableSetOf<Int>()
        for (missed in missedItems) {
            val text = missed.originalText
            for ((idx, el) in elements.withIndex()) {
                if (el.text.equals(text, ignoreCase = true) ||
                    el.text.contains(text, ignoreCase = true) ||
                    text.contains(el.text, ignoreCase = true)
                ) {
                    newIndices.add(idx)
                }
            }
        }
        return newIndices
    }
}
