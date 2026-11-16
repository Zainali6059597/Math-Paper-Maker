package com.nitroza.mathpapermaker.domain

import org.junit.Assert.*
import org.junit.Test

class QuestionReplacementTest {
    private val identity = PaperIdentity("BOARD", 9, "MATHEMATICS", "Mathematics", "2027")
    private val slot = OfferedQuestionSlot("SHORT-1", 2, "CH3", "SLO-3", "SHORT")
    private fun q(id: String, identity: PaperIdentity = this.identity, chapter: String = "CH3", status: ContentStatus = ContentStatus.VERIFIED_OFFICIAL) =
        QuestionCandidate(id, identity, chapter, "SLO-3", "SHORT", 2, true, status)

    @Test fun onlyExactCompatibleUnusedQuestionsAreReturned() {
        val request = ReplacementRequest(identity, slot, GenerationMode.OFFICIAL, "CURRENT", setOf("USED"))
        val bank = listOf(q("CURRENT"), q("USED"), q("GOOD"), q("WRONG-CHAPTER", chapter = "CH4"),
            q("WRONG-STREAM", identity = identity.copy(stream = "GENERAL_MATHEMATICS")))
        val options = QuestionReplacementEngine.findOptions(request, bank)
        assertEquals(listOf("GOOD"), options.candidates.map { it.id })
        assertEquals(1, options.excludedAsAlreadyUsed)
    }

    @Test fun officialReplacementRequiresVerifiedSummativeContent() {
        val request = ReplacementRequest(identity, slot, GenerationMode.OFFICIAL, "CURRENT", emptySet())
        val options = QuestionReplacementEngine.findOptions(request, listOf(q("PENDING", status = ContentStatus.NEEDS_REVIEW)))
        assertTrue(options.candidates.isEmpty())
        assertEquals(1, options.excludedByVerification)
    }
}
