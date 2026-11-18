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

    @Test fun selectedUnitPaperKeepsBoardStructureUsingOnlyChosenChapter() {
        val bank = buildList {
            repeat(15) { add(PaperQuestion("M$it", "MCQ $it", "MCQ", 1, 1, chapterId = "CH1")) }
            repeat(27) { add(PaperQuestion("S$it", "Short $it", "SHORT", 2, 2, chapterId = "CH1")) }
            repeat(10) { add(PaperQuestion("L$it", "Long $it", "LONG", 4, 3, chapterId = "CH1")) }
        }
        val result = AutoPaperGenerator.generate(9, setOf("CH1"), bank)
        assertTrue(result.toString(), result is AutoPaperGenerator.Result.Success)
        val questions = (result as AutoPaperGenerator.Result.Success).questions
        assertEquals(15, questions.count { it.kind == "MCQ" })
        assertEquals(27, questions.count { it.kind == "SHORT" })
        assertEquals(10, questions.count { it.kind == "LONG" })
        assertTrue(questions.all { it.chapterId == "CH1" })
        assertTrue(PunjabBoardPattern.validationErrors(9, questions, setOf("CH1")).isEmpty())
    }

    @Test fun selectedUnitPaperReportsExactMissingQuestionType() {
        val result = AutoPaperGenerator.generate(9, setOf("CH1"), emptyList())
        assertTrue(result is AutoPaperGenerator.Result.NeedsMoreQuestions)
        assertTrue((result as AutoPaperGenerator.Result.NeedsMoreQuestions).message.contains("15 MCQs"))
    }

    @Test fun rejectsInvalidClassAndEmptyChapterSelection() {
        assertTrue(AutoPaperGenerator.generate(8, setOf("CH1"), emptyList()) is AutoPaperGenerator.Result.NeedsMoreQuestions)
        assertTrue(AutoPaperGenerator.generate(9, emptySet(), emptyList()) is AutoPaperGenerator.Result.NeedsMoreQuestions)
    }

    @Test fun generatedPaperNeverReusesOneSourceQuestion() {
        val bank = completeBank(9)
        val result = AutoPaperGenerator.generate(9, (1..13).map { "CH$it" }.toSet(), bank)
        assertTrue(result is AutoPaperGenerator.Result.Success)
        val generated = (result as AutoPaperGenerator.Result.Success).questions
        val identities = generated.map {
            listOf(it.kind.uppercase(), it.chapterId, it.sourcePage, it.text, it.imageFile).joinToString("|")
        }
        assertEquals(identities.size, identities.distinct().size)
    }

    @Test fun generatedQuestionsHaveSequentialBoardLabelsAndCorrectSections() {
        val result = AutoPaperGenerator.generate(9, (1..13).map { "CH$it" }.toSet(), completeBank(9))
        assertTrue(result is AutoPaperGenerator.Result.Success)
        val generated = (result as AutoPaperGenerator.Result.Success).questions
        assertEquals((1..15).map { "Q1(${romanForTest(it)})" },
            generated.filter { it.kind == "MCQ" }.map { it.number })
        (2..4).forEach { section ->
            assertEquals((1..9).map { "Q$section(${romanForTest(it)})" },
                generated.filter { it.sectionId == "Q$section" }.map { it.number })
        }
        (5..9).forEach { section ->
            assertEquals(listOf("Q$section(a)", "Q$section(b)"),
                generated.filter { it.sectionId == "Q$section" }.map { it.number })
        }
    }

    @Test fun imageOnlyApprovedQuestionCanBeUsedByGenerator() {
        val bank = completeBank(9).mapIndexed { index, question ->
            if (index == 0) question.copy(text = "", imageFile = "/app/question_images/q1.png")
            else question
        }
        val result = AutoPaperGenerator.generate(9, (1..13).map { "CH$it" }.toSet(), bank)
        assertTrue(result.toString(), result is AutoPaperGenerator.Result.Success)
        assertEquals("/app/question_images/q1.png",
            (result as AutoPaperGenerator.Result.Success).questions.first().imageFile)
    }

    @Test fun invalidKindsAndEmptyQuestionsAreIgnored() {
        val bank = completeBank(9) + listOf(
            PaperQuestion("bad-kind", "Not a supported type", "ESSAY", 5, 1, chapterId = "CH1"),
            PaperQuestion("blank", " ", "MCQ", 1, 1, chapterId = "CH1")
        )
        val result = AutoPaperGenerator.generate(9, (1..13).map { "CH$it" }.toSet(), bank)
        assertTrue(result is AutoPaperGenerator.Result.Success)
        assertEquals(52, (result as AutoPaperGenerator.Result.Success).questions.size)
    }

    private fun romanForTest(value: Int): String {
        val values = listOf(10 to "X", 9 to "IX", 5 to "V", 4 to "IV", 1 to "I")
        var remaining = value
        return buildString {
            values.forEach { (amount, symbol) ->
                while (remaining >= amount) {
                    append(symbol)
                    remaining -= amount
                }
            }
        }
    }


    @Test fun sourceIdentityDoesNotCollideWhenQuestionTextContainsDelimiter() {
        val base = completeBank(9).filterNot { it.kind == "SHORT" && it.chapterId == "CH3" }
        val collidingIfJoined = listOf(
            PaperQuestion("A", "x|y", "SHORT", 2, 7, chapterId = "CH3"),
            PaperQuestion("B", "x", "SHORT", 2, 7, imageFile = "y|null", chapterId = "CH3")
        )
        val result = AutoPaperGenerator.generate(
            9, (1..13).map { "CH$it" }.toSet(), base + collidingIfJoined
        )
        assertTrue(result.toString(), result is AutoPaperGenerator.Result.Success)
        val selected = (result as AutoPaperGenerator.Result.Success).questions
            .filter { it.kind == "SHORT" && it.chapterId == "CH3" }
        assertEquals(2, selected.size)
        assertEquals(setOf("x|y", "x"), selected.map { it.text }.toSet())
    }

}
