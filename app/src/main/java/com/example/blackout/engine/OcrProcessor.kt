package com.example.blackout.engine

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

data class OcrElement(val text: String, val boundingBox: Rect)
data class OcrResult(val fullText: String, val elements: List<OcrElement>)

class OcrProcessor {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun process(bitmap: Bitmap): String = processWithBounds(bitmap).fullText

    suspend fun processWithBounds(bitmap: Bitmap): OcrResult = suspendCoroutine { cont ->
        val softBitmap = if (bitmap.config == Bitmap.Config.HARDWARE) {
            bitmap.copy(Bitmap.Config.ARGB_8888, false)
        } else bitmap
        val image = InputImage.fromBitmap(softBitmap, 0)
        recognizer.process(image)
            .addOnSuccessListener { result ->
                val elements = result.textBlocks.flatMap { block ->
                    block.lines.flatMap { line ->
                        line.elements.mapNotNull { el ->
                            el.boundingBox?.let { box -> OcrElement(el.text, box) }
                        }
                    }
                }
                val fullText = result.textBlocks
                    .joinToString("\n") { block -> block.text }
                    .normalizeWhitespace()
                cont.resume(OcrResult(fullText, elements))
            }
            .addOnFailureListener { cont.resumeWithException(it) }
    }

    fun close() = recognizer.close()

    private fun String.normalizeWhitespace(): String =
        trim().replace(Regex("""\s{2,}"""), " ")
}
