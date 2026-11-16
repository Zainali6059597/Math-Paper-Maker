package com.nitroza.mathpapermaker.domain

import org.junit.Assert.*
import org.junit.Test

class QuotaAwareSelectionEngineTest {
    private val identity = PaperIdentity("Test Board", 9, "GENERAL", "Mathematics", "2026")
    private fun q(id: String, chapter: String, cognitive: String = "Knowledge") =
        QuestionCandidate(id, identity, chapter, "SLO-$chapter", "SHORT", 2, true, ContentStatus.VERIFIED_OFFICIAL, cognitive, "Easy")

    @Test fun satisfiesOverlappingMetadataQuotasWithoutDuplicates() {
        val slots = (1..4).map { QuestionSlot("S$it", marks = 2) }
        val bank = listOf(q("a", "1"), q("b", "1"), q("c", "2", "Application"), q("d", "2", "Application"), q("e", "3"))
        val quotas = listOf(MetadataQuota(QuotaDimension.CHAPTER, "1", 2, 2), MetadataQuota(QuotaDimension.CHAPTER, "2", 2, 2), MetadataQuota(QuotaDimension.COGNITIVE_LEVEL, "Application", 2, 2))
        val result = QuotaAwareSelectionEngine.select(QuotaSelectionRequest(identity, slots, quotas, GenerationMode.OFFICIAL, 42L), bank)
        assertTrue(result is QuotaSelectionResult.Success)
        val selected = (result as QuotaSelectionResult.Success).selected.map { it.second }
        assertEquals(4, selected.map { it.id }.distinct().size)
        assertEquals(2, selected.count { it.chapterId == "1" })
        assertEquals(2, selected.count { it.cognitiveLevel == "Application" })
    }

    @Test fun blocksUnsatisfiableQuotaRatherThanRelaxingIt() {
        val slots = (1..2).map { QuestionSlot("S$it", marks = 2) }
        val result = QuotaAwareSelectionEngine.select(QuotaSelectionRequest(identity, slots,
            listOf(MetadataQuota(QuotaDimension.CHAPTER, "9", 1, 1)), GenerationMode.PRACTICE, 7L), listOf(q("a", "1"), q("b", "2")))
        assertTrue(result is QuotaSelectionResult.Blocked)
        assertEquals("QUOTA_UNSATISFIABLE", (result as QuotaSelectionResult.Blocked).code)
    }

    @Test fun officialModeRequiresVerifiedSummativeQuestions() {
        val result = QuotaAwareSelectionEngine.select(QuotaSelectionRequest(identity, listOf(QuestionSlot("S1", marks = 2)), emptyList(), GenerationMode.OFFICIAL, 1L),
            listOf(q("pending", "1").copy(status = ContentStatus.NEEDS_REVIEW), q("formative", "1").copy(summative = false)))
        assertTrue(result is QuotaSelectionResult.Blocked)
        assertEquals("SOURCE_VERIFICATION_REQUIRED", (result as QuotaSelectionResult.Blocked).code)
    }

    @Test fun sameSeedIsDeterministic() {
        val slots = (1..3).map { QuestionSlot("S$it", marks = 2) }
        val bank = (1..8).map { q("q$it", "1") }
        fun run() = (QuotaAwareSelectionEngine.select(QuotaSelectionRequest(identity, slots, emptyList(), GenerationMode.PRACTICE, 99L), bank) as QuotaSelectionResult.Success).selected.map { it.second.id }
        assertEquals(run(), run())
    }
}
