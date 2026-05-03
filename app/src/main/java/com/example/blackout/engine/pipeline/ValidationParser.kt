package com.example.blackout.engine.pipeline

object ValidationParser {

    private val PLACEHOLDER_PATTERN = Regex("""\[[A-Z_]+_\d+]""")

    fun parse(raw: String): ValidationResult {
        val lines = raw.lines().map { it.trim() }.filter { it.isNotBlank() }

        var foundPass = false
        var foundFail = false
        val missedItems = mutableListOf<Detection>()

        for (line in lines) {
            val upper = line.uppercase()
            when {
                upper.startsWith("RESULT:") -> {
                    val status = upper.substringAfter("RESULT:").trim()
                    when {
                        status.startsWith("PASS") -> foundPass = true
                        status.startsWith("FAIL") -> foundFail = true
                    }
                }
                line.contains(":") -> {
                    val colonIdx = line.indexOf(':')
                    val category = line.substring(0, colonIdx).trim().uppercase()
                    val text = line.substring(colonIdx + 1).trim()
                    if (text.isNotEmpty() && category.length in 2..20 &&
                        category.all { it.isUpperCase() || it == '_' } &&
                        category != "RESULT" &&
                        !PLACEHOLDER_PATTERN.matches(text)
                    ) {
                        missedItems.add(Detection(category = category, originalText = text))
                    } else if (PLACEHOLDER_PATTERN.matches(text)) {
                        PipelineLog.warn("Step4", "Ignoring placeholder reported as leak: $text")
                    }
                }
            }
        }

        val result = when {
            foundPass && missedItems.isEmpty() -> ValidationResult.Pass
            foundFail && missedItems.isNotEmpty() -> ValidationResult.Fail(missedItems)
            missedItems.isNotEmpty() -> {
                PipelineLog.warn("Step4", "No explicit RESULT line but found ${missedItems.size} leaked items — treating as FAIL")
                ValidationResult.Fail(missedItems)
            }
            foundFail -> {
                PipelineLog.warn("Step4", "FAIL declared but no missed items parsed — treating as PASS")
                ValidationResult.Pass
            }
            foundPass -> ValidationResult.Pass
            else -> {
                PipelineLog.warn("Step4", "No RESULT line found and no items — treating as PASS")
                ValidationResult.Pass
            }
        }

        val status = when (result) {
            is ValidationResult.Pass -> "PASS"
            is ValidationResult.Fail -> "FAIL (${result.missedItems.size} missed)"
        }
        PipelineLog.parseResult("Step4", status)
        return result
    }
}
