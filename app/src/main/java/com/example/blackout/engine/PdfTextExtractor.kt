package com.example.blackout.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object PdfTextExtractor {

    suspend fun extract(context: Context, uri: Uri, ocrProcessor: OcrProcessor): String =
        withContext(Dispatchers.IO) {
            val fd = context.contentResolver.openFileDescriptor(uri, "r")
                ?: throw IllegalStateException("Cannot open PDF")
            fd.use { descriptor ->
                val renderer = PdfRenderer(descriptor)
                renderer.use { pdf ->
                    val pages = (0 until pdf.pageCount).map { idx ->
                        val page = pdf.openPage(idx)
                        val bitmap = Bitmap.createBitmap(
                            page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888
                        )
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        page.close()
                        val text = ocrProcessor.process(bitmap)
                        bitmap.recycle()
                        text
                    }
                    pages.filter { it.isNotBlank() }.joinToString("\n\n")
                }
            }
        }
}
