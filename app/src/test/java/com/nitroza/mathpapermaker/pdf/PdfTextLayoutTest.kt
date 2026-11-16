package com.nitroza.mathpapermaker.pdf

import org.junit.Assert.assertEquals
import org.junit.Test

class PdfTextLayoutTest {
    private val measure: (String) -> Float = { it.length.toFloat() }

    @Test fun wrapsAtWordBoundaries() {
        assertEquals(listOf("Maths paper", "maker offline"), PdfTextLayout.wrap("Maths paper maker offline", 13f, measure))
    }

    @Test fun splitsAnUnbrokenLongToken() {
        assertEquals(listOf("ABCDEFGHIJ", "KLMN"), PdfTextLayout.wrap("ABCDEFGHIJKLMN", 10f, measure))
    }

    @Test fun emptyTextStillProducesOneLine() {
        assertEquals(listOf(""), PdfTextLayout.wrap("   ", 40f, measure))
    }

    @Test fun veryNarrowWidthDoesNotLoop() {
        assertEquals(listOf("longword"), PdfTextLayout.wrap("longword", 1f, measure))
    }
}
