package com.nitroza.mathpapermaker.domain

import org.junit.Assert.*
import org.junit.Test

class ChoicePatternEngineTest {
    private val identity = PaperIdentity("BOARD", 9, "MATHEMATICS", "Mathematics", "2027")

    private fun q(id: String, marks: Int = 2, status: ContentStatus = ContentStatus.VERIFIED_OFFICIAL) =
        QuestionCandidate(id, identity, "CH1", "SLO1", "SHORT", marks, true, status)

    @Test fun offeredAndAttemptedMarksAreDifferentAndCorrect() {
        val slots = (1..12).map { OfferedQuestionSlot("S$it", 2, "CH1", "SLO1", "SHORT") }
        val pattern = ChoicePaperPattern(identity, 18, listOf(ChoiceGroupRule("SHORT", 12, 9, 2, slots)))
        val result = ChoicePatternEngine.generate(pattern, (1..12).map { q("Q$it") }, GenerationMode.OFFICIAL, 42)
        assertTrue(result is ChoiceSelectionResult.Success)
        val paper = (result as ChoiceSelectionResult.Success).paper
        assertEquals(24, paper.offeredMarks)
        assertEquals(18, paper.attemptedMarks)
        assertEquals(12, paper.groups.single().offered.size)
    }

    @Test fun exactIdentityAndNoDuplicateQuestionsAreEnforced() {
        val slots = (1..2).map { OfferedQuestionSlot("S$it", 2) }
        val pattern = ChoicePaperPattern(identity, 2, listOf(ChoiceGroupRule("G", 2, 1, 2, slots)))
        val wrongStream = q("OTHER").copy(identity = identity.copy(stream = "GENERAL_MATHEMATICS"))
        val result = ChoicePatternEngine.generate(pattern, listOf(q("ONLY"), wrongStream), GenerationMode.PRACTICE, 1)
        assertTrue(result is ChoiceSelectionResult.Blocked)
    }

    @Test fun officialModeBlocksUnverifiedQuestions() {
        val slots = listOf(OfferedQuestionSlot("S1", 2))
        val pattern = ChoicePaperPattern(identity, 2, listOf(ChoiceGroupRule("G", 1, 1, 2, slots)))
        val result = ChoicePatternEngine.generate(pattern, listOf(q("Q1", status = ContentStatus.NEEDS_REVIEW)), GenerationMode.OFFICIAL, 1)
        assertTrue(result is ChoiceSelectionResult.Blocked)
        assertEquals("SOURCE_VERIFICATION_REQUIRED", (result as ChoiceSelectionResult.Blocked).code)
    }

    @Test fun invalidAttemptedTotalIsRejectedBeforeSelection() {
        val slots = listOf(OfferedQuestionSlot("S1", 2), OfferedQuestionSlot("S2", 2))
        val pattern = ChoicePaperPattern(identity, 6, listOf(ChoiceGroupRule("G", 2, 1, 2, slots)))
        val result = ChoicePatternEngine.generate(pattern, listOf(q("Q1"), q("Q2")), GenerationMode.PRACTICE, 1)
        assertEquals("ATTEMPTED_TOTAL_MISMATCH", (result as ChoiceSelectionResult.Blocked).code)
    }
}
