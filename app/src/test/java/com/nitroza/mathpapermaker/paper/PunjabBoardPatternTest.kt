package com.nitroza.mathpapermaker.paper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PunjabBoardPatternTest {
    private fun fullPaper(grade: Int): List<PaperQuestion> {
        val mcqMap = if (grade == 9) mapOf(
            "CH1" to 1, "CH2" to 1, "CH3" to 2, "CH4" to 2, "CH5" to 1,
            "CH6" to 2, "CH7" to 1, "CH9" to 1, "CH10" to 1, "CH11" to 1,
            "CH12" to 1, "CH13" to 1
        ) else mapOf(
            "CH1" to 1, "CH2" to 1, "CH3" to 1, "CH4" to 1, "CH5" to 1,
            "CH6" to 1, "CH7" to 1, "CH8" to 1, "CH9" to 2, "CH10" to 1,
            "CH11" to 2, "CH12" to 2
        )
        val shortGroups = if (grade == 9) listOf(
            mapOf("CH1" to 2, "CH2" to 2, "CH3" to 2, "CH4" to 2, "CH5" to 1),
            mapOf("CH6" to 3, "CH7" to 3, "CH9" to 3),
            mapOf("CH10" to 2, "CH11" to 2, "CH12" to 3, "CH13" to 2)
        ) else listOf(
            mapOf("CH1" to 3, "CH2" to 2, "CH3" to 2, "CH4" to 2),
            mapOf("CH5" to 3, "CH6" to 2, "CH7" to 2, "CH8" to 2),
            mapOf("CH9" to 3, "CH10" to 2, "CH11" to 2, "CH12" to 2)
        )
        val longPairs = if (grade == 9) listOf(
            "CH1" to "CH2", "CH3" to "CH4", "CH5" to "CH6", "CH7" to "CH9", "CH12" to "CH11"
        ) else listOf(
            "CH1" to "CH2", "CH3" to "CH4", "CH5" to "CH7", "CH6" to "CH8", "CH10" to "CH9"
        )
        val mcqs = mcqMap.flatMap { (chapter, count) ->
            (1..count).map { PaperQuestion("M${chapter}_$it", "MCQ", "MCQ", 1, 1, chapterId = chapter) }
        }
        val shorts = shortGroups.flatMapIndexed { groupIndex, group ->
            group.flatMap { (chapter, count) ->
                (1..count).map { PaperQuestion("S${groupIndex}_${chapter}_$it", "Short", "SHORT", 2, 2, chapterId = chapter) }
            }
        }
        val longs = longPairs.flatMapIndexed { index, pair ->
            listOf(
                PaperQuestion("L${index}a", "Long part a", "LONG", 4, 3, chapterId = pair.first),
                PaperQuestion("L${index}b", "Long part b", "LONG", 4, 3, chapterId = pair.second)
            )
        }
        return mcqs + shorts + longs
    }

    @Test fun classNineMatchesOfficialCountsMarksAndChapterDistribution() {
        val questions = fullPaper(9)
        assertEquals(15, PunjabBoardPattern.count(questions, "MCQ"))
        assertEquals(27, PunjabBoardPattern.count(questions, "SHORT"))
        assertEquals(10, PunjabBoardPattern.count(questions, "LONG"))
        assertEquals(109, questions.sumOf { it.marks })
        assertTrue(PunjabBoardPattern.isComplete(9, questions))
    }

    @Test fun classTenMatchesOfficialCountsMarksAndChapterDistribution() {
        assertTrue(PunjabBoardPattern.isComplete(10, fullPaper(10)))
    }

    @Test fun incompleteOrWrongMarksPaperCannotExport() {
        val questions = fullPaper(9).toMutableList().apply { removeAt(0) }
        assertFalse(PunjabBoardPattern.isComplete(9, questions))
        val wrongMarks = fullPaper(9).toMutableList().apply { this[0] = this[0].copy(marks = 2) }
        assertFalse(PunjabBoardPattern.isComplete(9, wrongMarks))
    }

    @Test fun duplicateManualNumberingCannotExport() {
        val questions = fullPaper(9).toMutableList()
        questions[1] = questions[1].copy(number = questions[0].number)
        assertFalse(PunjabBoardPattern.isComplete(9, questions))
    }

    @Test fun boardSectionsIncludeThreeShortGroupsAndFiveLongQuestions() {
        val sections = PunjabBoardPattern.sections(fullPaper(9))
        assertEquals(1, sections.count { it.heading.contains("MULTIPLE CHOICE") })
        assertEquals(3, sections.count { it.heading.contains("SHORT QUESTIONS") })
        assertEquals(5, sections.count { it.heading.contains("LONG QUESTION") })
        assertTrue(sections.any { it.instruction.contains("Attempt any 6 of the 9") })
        assertTrue(sections.any { it.instruction.contains("Attempt any 2 of Q5, Q6 and Q7") })
        assertTrue(sections.any { it.instruction.contains("Attempt any 1 of Q8 and Q9") })
    }
}
