package com.nitroza.mathpapermaker.data

import android.graphics.Bitmap
import android.graphics.RectF
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Offline OCR for one scanned textbook page.
 *
 * ML Kit's Latin recognition model is packaged in the app dependency; no network
 * fetch is needed at recognition time. The rendered bitmap is capped to avoid
 * exhausting memory on low-RAM Android phones.
 */
object OfflinePdfOcr {
    private const val MAX_PIXELS = 2_000_000L
    private const val MAX_WIDTH = 1800f

    // Reuse the bundled recognizer; constructing it for every page adds avoidable latency.
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    suspend fun readPage(pdfFile: File, zeroBasedPage: Int, selectedRegion: RectF? = null): String =
        withContext(Dispatchers.IO) {
            require(pdfFile.isFile && pdfFile.length() > 0L) { "The PDF file is missing or empty." }
            require(zeroBasedPage >= 0) { "Choose a valid PDF page." }

            val bitmap = ParcelFileDescriptor.open(
                pdfFile, ParcelFileDescriptor.MODE_READ_ONLY
            ).use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    require(zeroBasedPage < renderer.pageCount) { "The selected page is outside this PDF." }
                    renderer.openPage(zeroBasedPage).use { page ->
                        val pageWidth = page.width.toFloat()
                        val pageHeight = page.height.toFloat()
                        val scale = min(
                            MAX_WIDTH / pageWidth,
                            sqrt(MAX_PIXELS.toFloat() / (pageWidth * pageHeight))
                        ).coerceAtMost(2.5f)
                        val width = (pageWidth * scale).toInt().coerceAtLeast(1)
                        val height = (pageHeight * scale).toInt().coerceAtLeast(1)
                        val fullPage = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        try {
                            fullPage.eraseColor(android.graphics.Color.WHITE)
                            page.render(fullPage, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                            val box = selectedRegion?.let { region ->
                                val left = (region.left.coerceIn(0f, 1f) * width).toInt().coerceIn(0, width - 1)
                                val top = (region.top.coerceIn(0f, 1f) * height).toInt().coerceIn(0, height - 1)
                                val right = (region.right.coerceIn(0f, 1f) * width).toInt().coerceIn(left + 1, width)
                                val bottom = (region.bottom.coerceIn(0f, 1f) * height).toInt().coerceIn(top + 1, height)
                                android.graphics.Rect(left, top, right, bottom)
                            }
                            if (box == null) fullPage else {
                                Bitmap.createBitmap(fullPage, box.left, box.top, box.width(), box.height()).also {
                                    fullPage.recycle()
                                }
                            }
                        } catch (error: Throwable) {
                            if (!fullPage.isRecycled) fullPage.recycle()
                            throw error
                        }
                    }
                }
            }

            val textRecognizer = recognizer
            suspendCancellableCoroutine { continuation ->
                val released = AtomicBoolean(false)
                fun releaseResources() {
                    if (released.compareAndSet(false, true)) {
                        if (!bitmap.isRecycled) bitmap.recycle()
                    }
                }

                try {
                    val task = textRecognizer.process(InputImage.fromBitmap(bitmap, 0))
                    task.addOnSuccessListener { result ->
                        if (continuation.isActive) {
                            val cleaned = removePctbWatermarkLines(result.text)
                            continuation.resume(rejectLikelyWatermarkNoise(cleaned))
                        }
                        releaseResources()
                    }
                    task.addOnFailureListener { error ->
                        if (continuation.isActive) continuation.resumeWithException(error)
                        releaseResources()
                    }
                    task.addOnCanceledListener {
                        if (continuation.isActive) continuation.resumeWithException(CancellationException("Offline OCR was cancelled."))
                        releaseResources()
                    }
                    // ML Kit's Task cannot be cancelled. Keep the bitmap alive until
                    // its callback completes, even if the calling coroutine is cancelled.
                    continuation.invokeOnCancellation { /* completion callback owns cleanup */ }
                } catch (error: Throwable) {
                    releaseResources()
                    if (continuation.isActive) continuation.resumeWithException(error)
                }
            }
        }
}

 
private fun removePctbWatermarkLines(text: String): String =
    text.lineSequence()
        .filterNot { line ->
            val normalized = line.lowercase().filter(Char::isLetterOrDigit)
            normalized.contains("webversionofpctb") ||
                normalized.contains("pctbwebversion")
        }
        .joinToString("\n")
        .trim()

/** Reject OCR output dominated by isolated glyphs instead of readable question text. */
private fun rejectLikelyWatermarkNoise(text: String): String {
    if (text.isBlank()) return text
    val tokens = text.split(Regex("\\s+")).filter { it.isNotBlank() }
    if (tokens.size < 8) return text
    val shortTokens = tokens.count { it.count(Char::isLetterOrDigit) <= 2 }
    val hasQuestionNumber = Regex("(?m)^\\s*(?:Q(?:uestion)?\\s*)?\\d{1,2}\\s*[.)]", RegexOption.IGNORE_CASE)
        .containsMatchIn(text)
    return if (!hasQuestionNumber && shortTokens.toFloat() / tokens.size >= 0.60f) "" else text
}
