package com.nitroza.mathpapermaker.data

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import java.io.File

/**
 * Keeps one disk-backed PDFBox document open while a textbook is being used.
 * Loading a large textbook for every page extraction made repeated selection
 * unnecessarily slow. Only one document is cached and it is closed on exit or
 * when the selected PDF changes.
 */
object PdfTextDocumentCache {
    private var cachedPath: String? = null
    private var cachedLength: Long = -1L
    private var cachedModified: Long = -1L
    private var cachedDocument: PDDocument? = null

    @Synchronized
    fun <T> withDocument(context: Context, file: File, action: (PDDocument) -> T): T {
        require(file.isFile && file.length() > 0L) { "The PDF file is missing or empty." }
        val canonicalPath = file.canonicalPath
        val length = file.length()
        val modified = file.lastModified()
        if (cachedDocument == null || cachedPath != canonicalPath ||
            cachedLength != length || cachedModified != modified
        ) {
            closeLocked()
            PDFBoxResourceLoader.init(context.applicationContext)
            cachedDocument = PDDocument.load(file, MemoryUsageSetting.setupTempFileOnly())
            cachedPath = canonicalPath
            cachedLength = length
            cachedModified = modified
        }
        return action(checkNotNull(cachedDocument))
    }

    @Synchronized
    fun close() {
        closeLocked()
    }

    private fun closeLocked() {
        try {
            cachedDocument?.close()
        } finally {
            cachedDocument = null
            cachedPath = null
            cachedLength = -1L
            cachedModified = -1L
        }
    }
}
