package com.nitroza.mathpapermaker.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextbookExerciseParserTest {
    @Test fun extractsNumberedExerciseWithChapterExerciseAndPageProvenance() {
        val book = BundledBookPdfReader.BookIndex(
            bookFile = "maths.pdf",
            pageCount = 1,
            pages = listOf(BundledBookPdfReader.PageText(
                "maths.pdf", 12,
                "Chapter 2: Real Numbers\nExercise 2.1\n1. Define a rational number.\n2. Simplify the following expression.",
                false
            )),
            pagesNeedingOcr = emptyList()
        )
        val result = TextbookExerciseParser.parse(book)
        assertEquals(2, result.candidates.size)
        assertEquals("2", result.candidates.first().chapterId)
        assertEquals("2.1", result.candidates.first().exerciseId)
        assertEquals(12, result.candidates.first().sourcePage)
        assertEquals("NEEDS_REVIEW", result.candidates.first().verificationStatus)
    }

    @Test fun skipsScannedPagesAndReportsOcrRequirement() {
        val book = BundledBookPdfReader.BookIndex(
            "scan.pdf", 1,
            listOf(BundledBookPdfReader.PageText("scan.pdf", 4, "", true)),
            listOf(4)
        )
        val result = TextbookExerciseParser.parse(book)
        assertTrue(result.candidates.isEmpty())
        assertEquals(listOf(4), result.pagesNeedingOcr)
        assertTrue(result.warnings.any { it.contains("OCR") })
    }
}
