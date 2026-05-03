package com.example.blackout.engine.pipeline

object DetectionParser {

    private val DETECTION_PATTERN = Regex("""^([A-Z][A-Z_]*)\s*:\s*(.+)$""")

    private val VALID_CATEGORIES = setOf(
        "NAME", "DATE", "PHONE", "FAX", "EMAIL", "SSN", "MRN",
        "LOCATION", "ADDRESS", "ACCOUNT", "LICENSE", "VEHICLE",
        "DEVICE", "URL", "IP", "BIOMETRIC", "ID", "PERSON",
        "DOB", "INSURANCE", "CONTACT", "SECURE",
        "VICTIM", "WITNESS", "MINOR", "SOURCE", "CUSTOMER",
        "CARD", "ROUTING", "TAXID", "TXN", "BROKERAGE",
    )

    fun parse(raw: String): List<Detection> {
        val lines = raw.lines().map { it.trim() }.filter { it.isNotBlank() }
        val detections = mutableListOf<Detection>()
        var skippedLines = 0

        for (line in lines) {
            val match = DETECTION_PATTERN.matchEntire(line)
            if (match != null) {
                val category = match.groupValues[1]
                val text = match.groupValues[2].trim()
                if (text.isNotEmpty() && category in VALID_CATEGORIES) {
                    detections.add(Detection(category = category, originalText = text))
                } else if (text.isNotEmpty()) {
                    PipelineLog.warn("Step2", "Unknown category '$category' for text '$text' — including anyway")
                    detections.add(Detection(category = category, originalText = text))
                }
            } else {
                skippedLines++
            }
        }

        val deduped = detections.distinctBy { "${it.category}|${it.originalText}" }
        val dupeCount = detections.size - deduped.size

        PipelineLog.parseResult("Step2",
            "lines=${lines.size}, valid=${detections.size}, deduped=$dupeCount, skipped=$skippedLines, " +
            "categories=${deduped.map { it.category }.toSet()}"
        )
        return deduped
    }
}
