package com.nitroza.mathpapermaker.data

import android.graphics.Bitmap
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

    suspend fun readPage(pdfFile: File, zeroBasedPage: Int): String =
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
                        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { image ->
                            try {
                                page.render(image, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            } catch (error: Throwable) {
                                image.recycle()
                                throw error
                            }
                        }
                    }
                }
            }

            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            suspendCancellableCoroutine { continuation ->
                val released = AtomicBoolean(false)
                fun releaseResources() {
                    if (released.compareAndSet(false, true)) {
                        try {
                            recognizer.close()
                        } finally {
                            if (!bitmap.isRecycled) bitmap.recycle()
                        }
                    }
                }

                try {
                    val task = recognizer.process(InputImage.fromBitmap(bitmap, 0))
                    task.addOnSuccessListener { result ->
                        if (continuation.isActive) continuation.resume(result.text)
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
