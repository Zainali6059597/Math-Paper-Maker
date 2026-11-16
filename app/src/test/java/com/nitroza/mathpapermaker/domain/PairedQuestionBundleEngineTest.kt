package com.nitroza.mathpapermaker.domain

import org.junit.Assert.*
import org.junit.Test

class PairedQuestionBundleEngineTest {
    private val identity = PaperIdentity("BOARD", 9, "MATHEMATICS", "Mathematics", "2027")
    private fun q(id: String, marks: Int, status: ContentStatus = ContentStatus.VERIFIED_OFFICIAL) =
        QuestionCandidate(id, identity, "CH1", "SLO1", "LONG_PART", marks, true, status)

    @Test fun pairedPartsCanHaveDifferentMarksAndPreserveTheirOwnValues() {
        val bundle = PairedQuestionBundleRule("Q5", 0, listOf(
            OfferedQuestionSlot("Q5A", 4, questionType = "LONG_PART"),
            OfferedQuestionSlot("Q5B", 6, questionType = "LONG_PART")
        ))
        val result = PairedQuestionBundleEngine.generate(identity, 10, listOf(bundle), listOf(q("A", 4), q("B", 6)), GenerationMode.OFFICIAL, 7)
        assertTrue(result is PairedBundleSelectionResult.Success)
        val paper = (result as PairedBundleSelectionResult.Success).paper
        assertEquals(10, paper.totalMarks)
        assertEquals(listOf(4, 6), paper.bundles.single().parts.map { it.first.marks })
        assertTrue(PairedQuestionBundleEngine.instruction(paper.bundles.single()).contains("(a) 4 marks; (b) 6 marks"))
    }

    @Test fun pairedBundleRejectsWrongDeclaredTotal() {
        val bundle = PairedQuestionBundleRule("Q5", 0, listOf(OfferedQuestionSlot("A", 4), OfferedQuestionSlot("B", 6)))
        val result = PairedQuestionBundleEngine.generate(identity, 9, listOf(bundle), listOf(q("A", 4), q("B", 6)), GenerationMode.PRACTICE, 1)
        assertEquals("TOTAL_MARKS_MISMATCH", (result as PairedBundleSelectionResult.Blocked).code)
    }

    @Test fun oneQuestionCannotFillTwoParts() {
        val bundle = PairedQuestionBundleRule("Q5", 0, listOf(OfferedQuestionSlot("A", 4), OfferedQuestionSlot("B", 4)))
        val result = PairedQuestionBundleEngine.generate(identity, 8, listOf(bundle), listOf(q("ONLY", 4)), GenerationMode.PRACTICE, 1)
        assertEquals("INSUFFICIENT_QUESTION_BANK", (result as PairedBundleSelectionResult.Blocked).code)
    }

    @Test fun officialModeRequiresVerifiedSummativeParts() {
        val bundle = PairedQuestionBundleRule("Q5", 0, listOf(OfferedQuestionSlot("A", 4)))
        val result = PairedQuestionBundleEngine.generate(identity, 4, listOf(bundle), listOf(q("A", 4, ContentStatus.NEEDS_REVIEW)), GenerationMode.OFFICIAL, 1)
        assertEquals("SOURCE_VERIFICATION_REQUIRED", (result as PairedBundleSelectionResult.Blocked).code)
    }
}
