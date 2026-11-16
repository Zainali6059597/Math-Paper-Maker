package com.nitroza.mathpapermaker.domain

import org.junit.Assert.*
import org.junit.Test

class CompositePatternEngineTest {
    private val identity = PaperIdentity("BOARD", 9, "MATHEMATICS", "Mathematics", "2027")
    private fun q(id: String, marks: Int) = QuestionCandidate(
        id, identity, "CH1", "SLO1", "SHORT", marks, true, ContentStatus.VERIFIED_OFFICIAL
    )

    @Test fun fixedChoiceAndRequiredPairedPartsShareOneUniqueAssignment() {
        val slots = listOf(
            OfferedQuestionSlot("FIXED", 5),
            OfferedQuestionSlot("PAIR_A", 2, bundleId = "PAIR"),
            OfferedQuestionSlot("PAIR_B", 3, bundleId = "PAIR"),
            OfferedQuestionSlot("CHOICE_1", 2, groupId = "SHORT"),
            OfferedQuestionSlot("CHOICE_2", 2, groupId = "SHORT"),
            OfferedQuestionSlot("CHOICE_3", 2, groupId = "SHORT")
        )
        val pattern = CompositePaperPattern(
            identity, 14, slots,
            listOf(ChoiceGroupRule("SHORT", 3, 2, 2, slots.filter { it.groupId == "SHORT" }))
        )
        val bank = listOf(q("Q5", 5), q("Q2", 2), q("Q3", 3), q("Q21", 2), q("Q22", 2), q("Q23", 2))
        val result = CompositePatternEngine.generate(pattern, bank, GenerationMode.OFFICIAL, 77)
        assertTrue(result is CompositeSelectionResult.Success)
        val paper = (result as CompositeSelectionResult.Success).paper
        assertEquals(16, paper.offeredMarks)
        assertEquals(14, paper.attemptedMarks)
        assertEquals(6, paper.selectedBySlot.size)
        assertEquals(6, paper.selectedBySlot.values.map { it.id }.distinct().size)
    }

    @Test fun aSlotCannotBeBothChoiceAndBundle() {
        val slots = listOf(OfferedQuestionSlot("AMBIGUOUS", 2, bundleId = "PAIR", groupId = "G"))
        val pattern = CompositePaperPattern(identity, 2, slots, listOf(ChoiceGroupRule("G", 1, 1, 2, slots)))
        val result = CompositePatternEngine.generate(pattern, listOf(q("Q", 2)), GenerationMode.PRACTICE, 1)
        assertTrue(result is CompositeSelectionResult.Blocked)
        assertEquals("AMBIGUOUS_SLOT", (result as CompositeSelectionResult.Blocked).code)
    }

    @Test fun requiredMarksAndAttemptedChoiceMarksMustMatchDeclaredTotal() {
        val slots = listOf(
            OfferedQuestionSlot("FIXED", 4),
            OfferedQuestionSlot("C1", 2, groupId = "G"),
            OfferedQuestionSlot("C2", 2, groupId = "G")
        )
        val pattern = CompositePaperPattern(identity, 8, slots, listOf(ChoiceGroupRule("G", 2, 1, 2, slots.drop(1))))
        val result = CompositePatternEngine.generate(pattern, listOf(q("Q4", 4), q("Q2a", 2), q("Q2b", 2)), GenerationMode.PRACTICE, 1)
        assertTrue(result is CompositeSelectionResult.Blocked)
        assertEquals("ATTEMPTED_TOTAL_MISMATCH", (result as CompositeSelectionResult.Blocked).code)
    }
}
