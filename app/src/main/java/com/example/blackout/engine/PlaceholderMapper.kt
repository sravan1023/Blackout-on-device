package com.example.blackout.engine

object PlaceholderMapper {

    private val PLACEHOLDER = Regex("""\[[A-Z_]+(?:_\d+)?]""")

    fun buildMap(original: String, redacted: String): Map<String, String> {
        val placeholders = PLACEHOLDER.findAll(redacted).toList()
        if (placeholders.isEmpty()) return emptyMap()

        val framePieces = PLACEHOLDER.split(redacted)
        val map = mutableMapOf<String, String>()
        var searchStart = 0

        for (i in placeholders.indices) {
            val before = framePieces[i]
            val after = framePieces.getOrElse(i + 1) { "" }

            val beforeIdx = if (before.isEmpty()) {
                searchStart
            } else {
                val idx = original.indexOf(before, searchStart)
                if (idx < 0) searchStart else idx + before.length
            }

            val afterIdx = if (after.isEmpty()) {
                original.length
            } else {
                val trimmedAfter = after.trimStart()
                val idx = original.indexOf(trimmedAfter, beforeIdx)
                if (idx < 0) original.length else idx
            }

            if (beforeIdx <= afterIdx) {
                map[placeholders[i].value] = original.substring(beforeIdx, afterIdx).trim()
            }
            searchStart = afterIdx
        }

        return map
    }

    fun categoryOf(placeholder: String): String =
        placeholder.removePrefix("[").removeSuffix("]").substringBeforeLast("_")

    fun categoryGroup(category: String): String = when {
        category in setOf("NAME", "PERSON", "PATIENT", "VICTIM", "WITNESS", "MINOR", "SOURCE", "CUSTOMER") -> "Names"
        category in setOf("LOCATION", "ADDRESS") -> "Location"
        category in setOf("DATE", "DOB") -> "Dates"
        category in setOf("PHONE", "EMAIL", "CONTACT") -> "Contact"
        category in setOf("SSN") -> "SSN"
        category in setOf("ACCOUNT", "CARD", "ROUTING", "TAXID", "TXN", "BROKERAGE", "MRN", "ID", "SECURE") -> "Financial"
        else -> "Other"
    }
}
