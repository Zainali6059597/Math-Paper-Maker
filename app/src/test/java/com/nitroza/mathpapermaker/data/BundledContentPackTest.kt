package com.nitroza.mathpapermaker.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BundledContentPackTest {
    @Test
    fun pectaaGrade9PackParsesAndStaysUnverifiedUntilReview() {
        val file = File("src/main/assets/content-packs/pectaa-grade9-2027.json")
        assertTrue("Bundled PECTAA pack must exist", file.isFile)

        val parsed = PackageImporter.parse(file.readText(Charsets.UTF_8))

        assertEquals("PECTAA_G9_MATH_MODEL_2027", parsed.source.sourceId)
        assertEquals("NEEDS_REVIEW", parsed.source.verificationStatus)
        assertEquals("PUNJAB_G9_MATH_2027_PECTAA_ALP", parsed.pattern.patternId)
        assertEquals(75, parsed.pattern.totalMarks)
        assertEquals(9, parsed.questions.size)
        assertTrue(parsed.questions.all { it.verificationStatus == "NEEDS_REVIEW" && it.summative })
        assertTrue(parsed.warnings.any { it.contains("no SLO mapping") })
    }
}
