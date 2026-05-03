package com.example.blackout.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.blackout.ui.theme.HeatContact
import com.example.blackout.ui.theme.HeatDate
import com.example.blackout.ui.theme.HeatDefault
import com.example.blackout.ui.theme.HeatId
import com.example.blackout.ui.theme.HeatLocation
import com.example.blackout.ui.theme.HeatName

@Composable
fun RedactionHeatmap(redactedText: String, modifier: Modifier = Modifier) {
    val annotated = remember(redactedText) { buildHeatmapAnnotatedString(redactedText) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
    ) {
        Text(
            text = annotated,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Composable
fun InteractiveRedactionHeatmap(
    redactedText: String,
    placeholderMap: Map<String, String>,
    revealedPlaceholders: Set<String>,
    disabledCategories: Set<String>,
    onPlaceholderClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val annotated = remember(redactedText, placeholderMap, revealedPlaceholders, disabledCategories) {
        buildInteractiveAnnotatedString(redactedText, placeholderMap, revealedPlaceholders, disabledCategories)
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
    ) {
        ClickableText(
            text = annotated,
            style = MaterialTheme.typography.bodyMedium.merge(TextStyle(color = Color(0xFF333333))),
            modifier = Modifier.padding(16.dp),
            onClick = { offset ->
                annotated.getStringAnnotations("placeholder", offset, offset)
                    .firstOrNull()
                    ?.let { onPlaceholderClick(it.item) }
            },
        )
    }
}

private fun buildInteractiveAnnotatedString(
    redactedText: String,
    placeholderMap: Map<String, String>,
    revealedPlaceholders: Set<String>,
    disabledCategories: Set<String>,
): AnnotatedString = buildAnnotatedString {
    val pattern = Regex("""\[[A-Z_]+(?:_\d+)?]""")
    var lastEnd = 0

    pattern.findAll(redactedText).forEach { match ->
        append(redactedText.substring(lastEnd, match.range.first))

        val tag = match.value
        val rawCategory = tag.removePrefix("[").removeSuffix("]").substringBeforeLast("_")
        val group = com.example.blackout.engine.PlaceholderMapper.categoryGroup(rawCategory)
        val isRevealed = tag in revealedPlaceholders || group in disabledCategories
        val originalValue = placeholderMap[tag]

        if (isRevealed && originalValue != null) {
            pushStringAnnotation("placeholder", tag)
            withStyle(
                SpanStyle(
                    color = Color(0xFF2E7D32),
                    textDecoration = TextDecoration.Underline,
                    fontWeight = FontWeight.Medium,
                )
            ) {
                append(originalValue)
            }
            pop()
        } else {
            val color = categoryColor(rawCategory)
            pushStringAnnotation("placeholder", tag)
            withStyle(
                SpanStyle(
                    background = color.copy(alpha = 0.25f),
                    color = color,
                    fontWeight = FontWeight.SemiBold,
                )
            ) {
                append(tag)
            }
            pop()
        }
        lastEnd = match.range.last + 1
    }

    if (lastEnd < redactedText.length) {
        append(redactedText.substring(lastEnd))
    }
}

private fun buildHeatmapAnnotatedString(redactedText: String) = buildAnnotatedString {
    val pattern = Regex("""\[[A-Z_]+(?:_\d+)?]""")
    var lastEnd = 0

    pattern.findAll(redactedText).forEach { match ->
        append(redactedText.substring(lastEnd, match.range.first))

        val category = match.value
            .removePrefix("[")
            .removeSuffix("]")
            .substringBeforeLast("_")
        val color = categoryColor(category)

        withStyle(
            SpanStyle(
                background = color.copy(alpha = 0.25f),
                color = color,
                fontWeight = FontWeight.SemiBold,
            )
        ) {
            append(match.value)
        }
        lastEnd = match.range.last + 1
    }

    if (lastEnd < redactedText.length) {
        append(redactedText.substring(lastEnd))
    }
}

private fun categoryColor(category: String): Color = when {
    category.contains("NAME") || category.contains("PATIENT") ||
            category.contains("VICTIM") || category.contains("WITNESS") ||
            category.contains("SOURCE") || category.contains("CUSTOMER") ||
            category.contains("PERSON") || category.contains("MINOR") -> HeatName

    category.contains("LOCATION") || category.contains("ADDRESS") -> HeatLocation

    category.contains("DATE") || category.contains("DOB") -> HeatDate

    category.contains("PHONE") || category.contains("EMAIL") ||
            category.contains("CONTACT") -> HeatContact

    category.contains("SSN") || category.contains("MRN") || category.contains("ID") ||
            category.contains("ACCOUNT") || category.contains("CARD") ||
            category.contains("ROUTING") || category.contains("TAXID") ||
            category.contains("TXN") || category.contains("BROKERAGE") ||
            category.contains("SECURE") -> HeatId

    else -> HeatDefault
}
