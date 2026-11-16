package com.nitroza.mathpapermaker.data

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads mathematics textbooks bundled in app/src/main/assets/books/.
 *
 * This is deliberately local-only: it opens APK assets and never downloads a book.
 * Text is extracted page-by-page so later stages can retain reliable page references.
 * Scanned/image-only pages are reported instead of being treated as empty chapters.
 */
object BundledBookPdfReader {
    data class PageText(
        val bookFile: String,
        val pageNumber: Int,
        val text: String,
        val needsOcr: Boolean
    )

    data class BookIndex(
        val bookFile: String,
        val pageCount: Int,
        val pages: List<PageText>,
        val pagesNeedingOcr: List<Int>,
        val readError: String? = null
    )

    suspend fun readAll(context: Context): List<BookIndex> = withContext(Dispatchers.IO) {
        PDFBoxResourceLoader.init(context.applicationContext)
        val assetManager = context.applicationContext.assets
        val bookFiles = assetManager.list("books")
            .orEmpty()
            .filter { it.endsWith(".pdf", ignoreCase = true) }
            .sorted()

        bookFiles.map { fileName ->
            runCatching {
                assetManager.open("books/$fileName").use { input ->
                    PDDocument.load(input).use { document ->
                        val stripper = PDFTextStripper().apply { sortByPosition = true }
                        val pages = (1..document.numberOfPages).map { pageNumber ->
                            stripper.startPage = pageNumber
                            stripper.endPage = pageNumber
                            val extracted = stripper.getText(document).trim()
                            PageText(
                                bookFile = fileName,
                                pageNumber = pageNumber,
                                text = extracted,
                                needsOcr = extracted.length < MIN_TEXT_CHARACTERS
                            )
                        }
                        BookIndex(
                            bookFile = fileName,
                            pageCount = document.numberOfPages,
                            pages = pages,
                            pagesNeedingOcr = pages.filter { it.needsOcr }.map { it.pageNumber }
                        )
                    }
                }
            }.getOrElse { error ->
                // One damaged/unsupported PDF must not prevent other bundled books from loading.
                BookIndex(
                    bookFile = fileName,
                    pageCount = 0,
                    pages = emptyList(),
                    pagesNeedingOcr = emptyList(),
                    readError = error.message?.take(180) ?: error::class.java.simpleName
                )
            }
        }
    }

    private const val MIN_TEXT_CHARACTERS = 24
}
