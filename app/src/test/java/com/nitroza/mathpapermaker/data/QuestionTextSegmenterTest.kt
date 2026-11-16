package com.nitroza.mathpapermaker.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuestionTextSegmenterTest {
    @Test fun separatesNumberedQuestionsAndWrappedLines() {
        val result = QuestionTextSegmenter.segment(
            """
            Exercise 2.1
            1. Find the value of x + 2.
            when x = 3.
            2.
            Solve the following equation 2x = 10.
            3) Calculate the area of a circle.
            """.trimIndent()
        )
        assertEquals(listOf("1", "2", "3"), result.map { it.sourceNumber })
        assertEquals("Find the value of x + 2. when x = 3.", result[0].text)
        assertTrue(result[1].text.startsWith("Solve the following"))
    }

    @Test fun ignoresShortFragmentsAndDoesNotTreatLetterOptionsAsQuestions() {
        val result = QuestionTextSegmenter.segment(
            """
            1. Find x from the given equation.
            a) 2
            b) 4
            2. x
            3. Prove that the given identity is true.
            """.trimIndent()
        )
        assertEquals(listOf("1", "3"), result.map { it.sourceNumber })
    }

    @Test fun acceptsQuestionWordAndQPrefixNumbering() {
        val result = QuestionTextSegmenter.segment(
            """
            Question 1. Find the roots of the quadratic equation.
            Q. 2) Solve the simultaneous equations by elimination.
            """.trimIndent()
        )
        assertEquals(listOf("1", "2"), result.map { it.sourceNumber })
    }

    @Test fun retainsShortMathematicsPromptsButDropsSingleCharacterNoise() {
        val result = QuestionTextSegmenter.segment(
            """
            1. x = 2
            2. x
            3. Find x.
            """.trimIndent()
        )
        assertEquals(listOf("1", "3"), result.map { it.sourceNumber })
    }

    @Test fun emptyOrUnnumberedPageProducesNoCandidates() {
        assertTrue(QuestionTextSegmenter.segment("A page with ordinary text only.").isEmpty())
        assertTrue(QuestionTextSegmenter.segment("").isEmpty())
    }
}
