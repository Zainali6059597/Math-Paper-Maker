package com.nitroza.mathpapermaker.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextbookDraftFactoryTest {
    private fun candidate(status: String = "ACCEPTED_FOR_CLASSIFICATION") = TextbookCandidateEntity(
        candidateId = "candidate-123",
        bookFile = "Punjab-Maths-9.pdf",
        chapterId = "2",
        exerciseId = "2.1",
        questionNumber = "4",
        questionText = "Simplify the following expression.",
        sourcePage = 37,
        reviewStatus = status,
        reviewNote = "Accepted by human reviewer",
        detectedAt = 123L
    )

    private fun classification() = TextbookDraftFactory.Classification(
        board = "Punjab", grade = 9, stream = "MATHEMATICS",
        subject = "Mathematics", session = "2026-27",
        chapterId = "2", topic = "Real numbers",
        questionType = "SHORT", marks = 3,
        cognitiveLevel = "APPLICATION", difficulty = "MEDIUM"
    )

    @Test fun createsQuarantinedQuestionWithExactBookPageProvenance() {
        val draft = TextbookDraftFactory.create(candidate(), classification())
        assertEquals("NEEDS_REVIEW", draft.source.verificationStatus)
        assertEquals("NEEDS_REVIEW", draft.question.verificationStatus)
        assertEquals("UNREVIEWED", draft.question.copyrightStatus)
        assertFalse(draft.question.summative)
        assertEquals("TEXTBOOK_PDF_UNVERIFIED", draft.source.sourceType)
        assertTrue(draft.source.pageReference!!.contains("Page 37"))
        assertEquals(draft.source.sourceId, draft.question.sourceId)
        assertEquals(3, draft.question.marks)
        assertEquals("SHORT", draft.question.questionType)
    }

    @Test fun boardAndSessionArePartOfQuestionIdentity() {
        val first = TextbookDraftFactory.create(candidate(), classification()).question
        val other = TextbookDraftFactory.create(candidate(), classification().copy(board = "FBISE")).question
        val nextSession = TextbookDraftFactory.create(candidate(), classification().copy(session = "2027-28")).question
        assertNotEquals(first.questionId, other.questionId)
        assertNotEquals(first.questionId, nextSession.questionId)
        assertEquals("Punjab", first.board)
    }

    @Test fun refusesCandidateNotAcceptedForClassification() {
        val error = runCatching {
            TextbookDraftFactory.create(candidate("NEEDS_REVIEW"), classification())
        }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }

    @Test fun refusesMissingMarksOrUnsupportedBoardGradeStreamMetadata() {
        assertTrue(runCatching {
            TextbookDraftFactory.create(candidate(), classification().copy(marks = 0))
        }.isFailure)
        assertTrue(runCatching {
            TextbookDraftFactory.create(candidate(), classification().copy(grade = 11))
        }.isFailure)
        assertTrue(runCatching {
            TextbookDraftFactory.create(candidate(), classification().copy(stream = "SCIENCE"))
        }.isFailure)
    }
}
