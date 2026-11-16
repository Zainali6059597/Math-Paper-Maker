package com.nitroza.mathpapermaker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalSelectionEngineTest {
    private val identity = PaperIdentity("Punjab", 9, "ELECTIVE", "Mathematics", "2026-27")
    private fun question(id: String, stream: String = "ELECTIVE", status: ContentStatus = ContentStatus.VERIFIED_OFFICIAL) =
        QuestionCandidate(id, identity.copy(stream = stream), "CH1", "SLO1", "SHORT", 2, true, status)

    @Test fun selectionIsDeterministicForSameSeed() {
        val bank = listOf(question("Q1"), question("Q2"), question("Q3"))
        val request = SelectionRequest(identity, listOf(QuestionSlot("S1", marks = 2), QuestionSlot("S2", marks = 2)), GenerationMode.OFFICIAL, 42)
        val first = LocalSelectionEngine.select(request, bank) as SelectionResult.Success
        val second = LocalSelectionEngine.select(request, bank) as SelectionResult.Success
        assertEquals(first.selected.map { it.second.id }, second.selected.map { it.second.id })
    }

    @Test fun doesNotMixGeneralAndElectiveStreams() {
        val request = SelectionRequest(identity, listOf(QuestionSlot("S1", marks = 2)), GenerationMode.OFFICIAL, 7)
        val result = LocalSelectionEngine.select(request, listOf(question("GENERAL", "GENERAL")))
        assertTrue(result is SelectionResult.Blocked)
    }

    @Test fun officialModeRejectsUnverifiedQuestions() {
        val request = SelectionRequest(identity, listOf(QuestionSlot("S1", marks = 2)), GenerationMode.OFFICIAL, 7)
        val result = LocalSelectionEngine.select(request, listOf(question("Q1", status = ContentStatus.NEEDS_REVIEW)))
        assertTrue(result is SelectionResult.Blocked)
    }

    @Test fun practiceModeCanUseReviewedLegacyContentButNotConflicts() {
        val request = SelectionRequest(identity, listOf(QuestionSlot("S1", marks = 2)), GenerationMode.PRACTICE, 7)
        val result = LocalSelectionEngine.select(request, listOf(question("OLD", status = ContentStatus.LEGACY)))
        assertTrue(result is SelectionResult.Success)
    }
    @Test fun doesNotCrossExamSession() {
        val request = SelectionRequest(identity, listOf(QuestionSlot("S1", marks = 2)), GenerationMode.PRACTICE, 9)
        val wrongSession = question("Q1").copy(identity = identity.copy(session = "2025-26"))
        assertTrue(LocalSelectionEngine.select(request, listOf(wrongSession)) is SelectionResult.Blocked)
    }

    @Test fun neverUsesSameQuestionTwice() {
        val bank = listOf(question("Q1"))
        val request = SelectionRequest(identity, listOf(QuestionSlot("S1", marks = 2), QuestionSlot("S2", marks = 2)), GenerationMode.PRACTICE, 9)
        assertTrue(LocalSelectionEngine.select(request, bank) is SelectionResult.Blocked)
    }

    @Test fun practiceModeRejectsConflictedContent() {
        val request = SelectionRequest(identity, listOf(QuestionSlot("S1", marks = 2)), GenerationMode.PRACTICE, 9)
        assertTrue(LocalSelectionEngine.select(request, listOf(question("Q1", status = ContentStatus.CONFLICTED))) is SelectionResult.Blocked)
    }

}
