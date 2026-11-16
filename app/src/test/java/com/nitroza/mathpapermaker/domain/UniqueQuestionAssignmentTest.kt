package com.nitroza.mathpapermaker.domain

import org.junit.Assert.*
import org.junit.Test

class UniqueQuestionAssignmentTest {
    private data class Slot(val id: String, val allowed: Set<String>)

    @Test fun backtracksWhenAnEarlyFlexibleSlotWouldStarveAnotherSlot() {
        val identity = PaperIdentity("BOARD", 9, "MATH", "Mathematics", "2027")
        fun q(id: String) = QuestionCandidate(id, identity, "C", "S", "SHORT", 2, true, ContentStatus.VERIFIED_OFFICIAL)
        val slots = listOf(
            Slot("A", setOf("q1", "q2")),
            Slot("B", setOf("q1", "q3")),
            Slot("C", setOf("q1", "q2"))
        )
        val result = UniqueQuestionAssignment.solve(
            slots = slots,
            slotId = { it.id },
            candidates = listOf(q("q1"), q("q2"), q("q3")),
            matches = { candidate, slot -> candidate.id in slot.allowed },
            seed = 11L
        )
        assertNotNull(result.assignment)
        val chosen = result.assignment!!.values.map { it.second.id }
        assertEquals(3, chosen.distinct().size)
        assertTrue(result.exploredNodes >= 3)
    }

    @Test fun duplicateCandidateIdsAreNeverSilentlyAssignedByCallers() {
        val identity = PaperIdentity("BOARD", 9, "MATH", "Mathematics", "2027")
        val slot = QuestionSlot("A", marks = 2)
        val q = QuestionCandidate("same", identity, "C", "S", "SHORT", 2, true, ContentStatus.VERIFIED_OFFICIAL)
        val result = LocalSelectionEngine.select(SelectionRequest(identity, listOf(slot), GenerationMode.PRACTICE, 1), listOf(q, q))
        assertTrue(result is SelectionResult.Blocked)
        assertEquals("DUPLICATE_QUESTION_ID", (result as SelectionResult.Blocked).code)
    }
}
