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
    @Test fun acceptsStandaloneQuestionNumberAndWrappedContinuation() {
        val book = BundledBookPdfReader.BookIndex(
            "maths.pdf", 2,
            listOf(
                BundledBookPdfReader.PageText(
                    "maths.pdf", 7,
                    "Chapter 3: Sets\\nExercise 3.1\\n1.\\nDefine a set and give two examples.\\nExplain your answer.",
                    false
                ),
                BundledBookPdfReader.PageText(
                    "maths.pdf", 8,
                    "2.\\nWrite the following set in roster form.",
                    false
                )
            ),
            emptyList()
        )
        val result = TextbookExerciseParser.parse(book)
        assertEquals(2, result.candidates.size)
        assertEquals("Define a set and give two examples. Explain your answer.", result.candidates[0].text)
        assertEquals(7, result.candidates[0].sourcePage)
        assertEquals("Write the following set in roster form.", result.candidates[1].text)
        assertEquals(8, result.candidates[1].sourcePage)
        assertTrue(result.candidates.all { it.verificationStatus == "NEEDS_REVIEW" })
    }

}
