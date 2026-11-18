package com.nitroza.mathpapermaker.paper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoPaperGeneratorTest {
    private fun completeBank(grade: Int): List<PaperQuestion> {
        val questions = mutableListOf<PaperQuestion>()
        PunjabBoardPattern.mcqDistribution(grade).forEach { (chapter, count) ->
            repeat(count) { index ->
                questions += PaperQuestion("M$chapter-$index", "MCQ $chapter $index", "MCQ", 1, 1,
                    chapterId = chapter, sectionId = "Q1")
            }
        }
        (2..4).forEach { section ->
            val chapters = if (grade == 9) {
                PunjabBoardPattern.shortDistribution(grade, section).keys
            } else PunjabBoardPattern.shortAllowedChapters(grade, section)
            chapters.forEach { chapter ->
                repeat(3) { index ->
                    questions += PaperQuestion("S$section-$chapter-$index", "Short $section $chapter $index",
                        "SHORT", 2, 2, chapterId = chapter, sectionId = "Q$section")
                }
            }
        }
        PunjabBoardPattern.longPairRules(grade).forEachIndexed { index, rule ->
            rule.first.forEach { chapter ->
                questions += PaperQuestion("L${index}A$chapter", "Long first $chapter", "LONG", 4, 3,
                    chapterId = chapter, sectionId = "Q${index + 5}")
            }
            rule.second.forEach { chapter ->
                questions += PaperQuestion("L${index}B$chapter", "Long second $chapter", "LONG", 4, 3,
                    chapterId = chapter, sectionId = "Q${index + 5}")
            }
        }
        return questions
    }

    @Test fun createsAValidClassNinePaperFromFullBook() {
        val result = AutoPaperGenerator.generate(9, (1..13).map { "CH$it" }.toSet(), completeBank(9))
        assertTrue(result.toString(), result is AutoPaperGenerator.Result.Success)
        val questions = (result as AutoPaperGenerator.Result.Success).questions
        assertEquals(52, questions.size)
        assertTrue(PunjabBoardPattern.isComplete(9, questions))
    }

    @Test fun createsAValidClassTenPaperFromFullBook() {
        val result = AutoPaperGenerator.generate(10, (1..12).map { "CH$it" }.toSet(), completeBank(10))
        assertTrue(result.toString(), result is AutoPaperGenerator.Result.Success)
        val questions = (result as AutoPaperGenerator.Result.Success).questions
        assertEquals(52, questions.size)
        assertTrue(PunjabBoardPattern.isComplete(10, questions))
    }

    @Test fun reportsMissingQuestionsInsteadOfReturningAnInvalidPaper() {
        val result = AutoPaperGenerator.generate(9, (1..13).map { "CH$it" }.toSet(), emptyList())
        assertFalse(result is AutoPaperGenerator.Result.Success)
        assertTrue((result as AutoPaperGenerator.Result.NeedsMoreQuestions).message.isNotBlank())
    }

    @Test fun selectedChaptersLimitWhichQuestionsCanBeUsed() {
        val result = AutoPaperGenerator.generate(9, setOf("CH1"), completeBank(9))
        assertFalse(result is AutoPaperGenerator.Result.Success)
    }

    @Test fun rejectsInvalidClassAndEmptyChapterSelection() {
        assertTrue(AutoPaperGenerator.generate(8, setOf("CH1"), emptyList()) is AutoPaperGenerator.Result.NeedsMoreQuestions)
        assertTrue(AutoPaperGenerator.generate(9, emptySet(), emptyList()) is AutoPaperGenerator.Result.NeedsMoreQuestions)
    }
}
