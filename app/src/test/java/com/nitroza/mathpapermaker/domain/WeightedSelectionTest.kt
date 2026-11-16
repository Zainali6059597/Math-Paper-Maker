package com.nitroza.mathpapermaker.domain

import org.junit.Assert.assertTrue
import org.junit.Test

class WeightedSelectionTest {
    @Test fun higherWeightIsSelectedMoreOftenAcrossSeeds() {
        val identity = PaperIdentity("Punjab", 9, "MATHEMATICS", "Mathematics", "2026-27")
        fun candidate(id: String, weight: Double) = QuestionCandidate(
            id, identity, "CH1", "SLO1", "SHORT", 2, true,
            ContentStatus.VERIFIED_OFFICIAL, selectionWeight = weight
        )
        val bank = listOf(candidate("NORMAL", 1.0), candidate("IMPORTANT", 4.0))
        var importantSelections = 0
        repeat(500) { seed ->
            val request = SelectionRequest(identity, listOf(QuestionSlot("S1", marks = 2)), GenerationMode.OFFICIAL, seed.toLong())
            val result = LocalSelectionEngine.select(request, bank) as SelectionResult.Success
            if (result.selected.single().second.id == "IMPORTANT") importantSelections++
        }
        assertTrue("Higher-weight candidate should win substantially more often: $importantSelections", importantSelections > 300)
    }
}
