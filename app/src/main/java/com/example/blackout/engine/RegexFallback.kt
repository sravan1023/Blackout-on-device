package com.example.blackout.engine

data class RegexHit(val matchedText: String, val category: String, val range: IntRange)

object RegexFallback {

    private val patterns = listOf(
        "SSN"   to Regex("""\b\d{3}-\d{2}-\d{4}\b"""),
        "EMAIL" to Regex("""[a-zA-Z0-9._%+\-]+@[a-zA-Z0-9.\-]+\.[a-zA-Z]{2,}"""),
        "PHONE" to Regex("""\b(\+1[-.\s]?)?\(?\d{3}\)?[-.\s]?\d{3}[-.\s]?\d{4}\b"""),
        "MRN"   to Regex("""\bMRN\s*[:#]?\s*\d{5,10}\b""", RegexOption.IGNORE_CASE),
        "CARD"  to Regex("""\b(?:\d{4}[-\s]?){3}\d{4}\b"""),
        "IP"    to Regex("""\b(?:\d{1,3}\.){3}\d{1,3}\b"""),
        "DOB"   to Regex("""\b(0[1-9]|1[0-2])[-/](0[1-9]|[12]\d|3[01])[-/](19|20)\d{2}\b"""),
        "SSN_PLAIN" to Regex("""\b\d{9}\b"""),
    )

    fun scan(text: String): List<RegexHit> =
        patterns.flatMap { (category, regex) ->
            regex.findAll(text).map { match ->
                RegexHit(match.value, category, match.range)
            }
        }

    /**
     * Force-redacts anything the regex found that the LLM left verbatim in its output.
     * Safety net only — the LLM handles the bulk of redaction.
     */
    fun mergeIntoLlm(llmOutput: String, originalText: String, hits: List<RegexHit>): String {
        if (hits.isEmpty()) return llmOutput
        var result = llmOutput
        val counters = mutableMapOf<String, Int>()
        hits.forEach { hit ->
            if (result.contains(hit.matchedText)) {
                val n = counters.merge(hit.category, 1, Int::plus)!!
                result = result.replace(hit.matchedText, "[${hit.category}_$n]")
            }
        }
        return result
    }
}
