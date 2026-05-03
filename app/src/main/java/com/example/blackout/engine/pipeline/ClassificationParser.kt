package com.example.blackout.engine.pipeline

object ClassificationParser {

    private val VALID_CATEGORIES = setOf(
        "Medical", "Financial", "Legal", "Tactical",
        "Journalism", "Field Service", "General",
    )

    fun parse(raw: String): ClassificationResult {
        val lines = raw.lines().map { it.trim() }.filter { it.isNotBlank() }

        var documentType = "unknown"
        var category = "General"

        for (line in lines) {
            val upper = line.uppercase()
            when {
                upper.startsWith("DOCUMENT_TYPE:") || upper.startsWith("DOCUMENT TYPE:") -> {
                    documentType = line.substringAfter(":").trim()
                }
                upper.startsWith("CATEGORY:") -> {
                    val parsed = line.substringAfter(":").trim()
                    val matched = VALID_CATEGORIES.firstOrNull { it.equals(parsed, ignoreCase = true) }
                    if (matched != null) {
                        category = matched
                    } else {
                        PipelineLog.warn("Step1", "Unrecognized category '$parsed', defaulting to General")
                    }
                }
            }
        }

        if (documentType == "unknown") {
            PipelineLog.warn("Step1", "No DOCUMENT_TYPE line found in response")
        }

        val result = ClassificationResult(documentType, category)
        PipelineLog.parseResult("Step1", "type='$documentType', category='$category'")
        return result
    }
}
