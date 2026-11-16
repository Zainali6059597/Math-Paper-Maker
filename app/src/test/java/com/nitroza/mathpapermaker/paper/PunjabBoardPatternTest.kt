package com.nitroza.mathpapermaker.paper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PunjabBoardPatternTest {
    private fun fullPaper(): List<PaperQuestion> {
        val mcqs = (1..15).map { PaperQuestion("Q1($it)", "MCQ $it", "MCQ", 1, 1) }
        val shorts = (1..27).map { index ->
            PaperQuestion("Q${2 + (index - 1) / 9}(${index})", "Short $index", "SHORT", 2, 2)
        }
        val longs = (1..10).map { index ->
            PaperQuestion("Q${5 + (index - 1) / 2}(${if (index % 2 == 1) "a" else "b"})", "Long $index", "LONG", 4, 3)
        }
        return mcqs + shorts + longs
    }

    @Test fun fullPunjabPaperMatchesOfferedCountsAndMarks() {
        val questions = fullPaper()
        assertEquals(15, PunjabBoardPattern.count(questions, "MCQ"))
        assertEquals(27, PunjabBoardPattern.count(questions, "SHORT"))
        assertEquals(10, PunjabBoardPattern.count(questions, "LONG"))
        assertEquals(109, questions.sumOf { it.marks })
        assertTrue(PunjabBoardPattern.isComplete(questions))
    }

    @Test fun incompleteOrWrongMarksPaperCannotExport() {
        val questions = fullPaper().toMutableList().apply { removeAt(0) }
        assertFalse(PunjabBoardPattern.isComplete(questions))
        val wrongMarks = fullPaper().toMutableList().apply { this[0] = this[0].copy(marks = 2) }
        assertFalse(PunjabBoardPattern.isComplete(wrongMarks))
    }

    @Test fun duplicateManualNumberingCannotExport() {
        val questions = fullPaper().toMutableList()
        questions[1] = questions[1].copy(number = questions[0].number)
        assertFalse(PunjabBoardPattern.isComplete(questions))
    }

    @Test fun boardSectionsIncludeThreeShortGroupsAndFiveLongQuestions() {
        val sections = PunjabBoardPattern.sections(fullPaper())
        assertEquals(1, sections.count { it.heading.contains("MULTIPLE CHOICE") })
        assertEquals(3, sections.count { it.heading.contains("SHORT QUESTIONS") })
        assertEquals(5, sections.count { it.heading.contains("LONG QUESTION") })
        assertTrue(sections.any { it.instruction.contains("Attempt any 6 of the 9") })
        assertTrue(sections.any { it.instruction.contains("Attempt any 2 of Q5, Q6 and Q7") })
        assertTrue(sections.any { it.instruction.contains("Attempt any 1 of Q8 and Q9") })
    }
}
