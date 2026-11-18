package com.nitroza.mathpapermaker.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextbookQuestionBankMapperTest {
    private fun row(
        id: String,
        chapter: String = "CH1",
        status: String = "APPROVED",
        type: String = "SHORT"
    ) = TextbookCandidateEntity(
        candidateId = id,
        bookFile = "CLASS9|Maths 9.pdf",
        chapterId = chapter,
        exerciseId = "PAGE",
        questionNumber = "3",
        questionText = "Find the value of x.",
        sourcePage = 42,
        questionType = type,
        marks = if (type == "MCQ") 1 else if (type == "LONG") 4 else 2,
        reviewStatus = status,
        detectedAt = 1L
    )

    @Test fun mapsApprovedRowsAndPreservesSourceMetadata() {
        val mapped = TextbookQuestionBankMapper.toPaperQuestions(
            listOf(row("one", type = "LONG")), setOf("CH1")
        )
        assertEquals(1, mapped.size)
        val question = mapped.single()
        assertEquals("3", question.number)
        assertEquals("LONG", question.kind)
        assertEquals(4, question.marks)
        assertEquals(42, question.sourcePage)
        assertEquals("CH1", question.chapterId)
        assertEquals("BANK", question.sectionId)
    }

    @Test fun excludesUnapprovedRowsAndChaptersOutsideSelection() {
        val mapped = TextbookQuestionBankMapper.toPaperQuestions(
            listOf(row("approved"), row("pending", status = "NEEDS_REVIEW"), row("other", chapter = "CH2")),
            setOf("CH1")
        )
        assertEquals(1, mapped.size)
        assertEquals("CH1", mapped.single().chapterId)
    }

    @Test fun ignoresUnsupportedQuestionTypes() {
        val mapped = TextbookQuestionBankMapper.toPaperQuestions(
            listOf(row("bad", type = "ESSAY")), setOf("CH1")
        )
        assertTrue(mapped.isEmpty())
    }
}
