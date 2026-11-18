package com.nitroza.mathpapermaker.paper

/**
 * Builds a complete board-pattern paper from already indexed textbook questions.
 * It never writes or invents question text: every output item comes from [questionBank].
 */
object AutoPaperGenerator {
    sealed class Result {
        data class Success(val questions: List<PaperQuestion>) : Result()
        data class NeedsMoreQuestions(val message: String) : Result()
    }

    fun generate(
        grade: Int,
        selectedChapters: Set<String>,
        questionBank: List<PaperQuestion>
    ): Result {
        if (grade !in 9..10) return Result.NeedsMoreQuestions("Choose Class 9 or Class 10.")
        if (selectedChapters.isEmpty()) return Result.NeedsMoreQuestions("Select at least one chapter.")

        val validChapters = selectedChapters.filter {
            PunjabBoardPattern.isValidChapter(grade, it)
        }.toSet()
        if (validChapters != selectedChapters) {
            return Result.NeedsMoreQuestions("The selected chapter list contains an invalid chapter.")
        }

        val available = questionBank
            .filter { it.chapterId in selectedChapters }
            .filter { it.kind.uppercase() in setOf("MCQ", "SHORT", "LONG") }
            .filter { it.text.isNotBlank() || !it.imageFile.isNullOrBlank() }
            .distinctBy { listOf(it.kind.uppercase(), it.chapterId, it.sourcePage, it.text, it.imageFile).joinToString("|") }

        val used = mutableSetOf<String>()
        val result = mutableListOf<PaperQuestion>()

        fun take(kind: String, chapter: String, section: String, number: String): PaperQuestion? {
            val identity = available.firstOrNull {
                it.kind.equals(kind, true) && it.chapterId == chapter &&
                    listOf(it.kind.uppercase(), it.chapterId, it.sourcePage, it.text, it.imageFile).joinToString("|") !in used
            } ?: return null
            val key = listOf(identity.kind.uppercase(), identity.chapterId, identity.sourcePage, identity.text, identity.imageFile).joinToString("|")
            used += key
            return identity.copy(number = number, kind = kind, marks = when (kind) {
                "MCQ" -> 1
                "SHORT" -> 2
                else -> 4
            }, sectionId = section)
        }

        fun roman(value: Int): String {
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

        fun fail(message: String): Result.NeedsMoreQuestions =
            Result.NeedsMoreQuestions(message)

        // Q1: exact chapter-wise MCQ allocation.
        var mcqIndex = 1
        for ((chapter, count) in PunjabBoardPattern.mcqDistribution(grade)) {
            repeat(count) {
                val item = take("MCQ", chapter, "Q1", "Q1(${roman(mcqIndex++)})")
                    ?: return fail("Not enough MCQs from $chapter. Add more indexed questions or select a wider chapter range.")
                result += item
            }
        }

        // Q2-Q4: exact Class 9 allocation; Class 10 requires at least two from each chapter.
        for (section in 2..4) {
            if (grade == 9) {
                for ((chapter, count) in PunjabBoardPattern.shortDistribution(grade, section)) {
                    repeat(count) { index ->
                        val item = take("SHORT", chapter, "Q$section", "Q$section(${roman(index + 1)})")
                            ?: return fail("Not enough short questions from $chapter for Q$section.")
                        result += item
                    }
                }
            } else {
                val chapters = PunjabBoardPattern.shortAllowedChapters(grade, section).toList()
                val sectionItems = mutableListOf<PaperQuestion>()
                chapters.forEach { chapter ->
                    repeat(2) {
                        val item = take("SHORT", chapter, "Q$section", "")
                            ?: return fail("Q$section needs at least two short questions from $chapter.")
                        sectionItems += item
                    }
                }
                val extraChapter = chapters.firstOrNull { chapter ->
                    available.any {
                        it.kind.equals("SHORT", true) && it.chapterId == chapter &&
                            listOf(it.kind.uppercase(), it.chapterId, it.sourcePage, it.text, it.imageFile).joinToString("|") !in used
                    }
                } ?: return fail("Q$section needs one additional short question from its prescribed chapters.")
                take("SHORT", extraChapter, "Q$section", "")?.let(sectionItems::add)
                    ?: return fail("Q$section could not fill its ninth short question.")
                sectionItems.forEachIndexed { index, item ->
                    result += item.copy(number = "Q$section(${roman(index + 1)})")
                }
            }
        }

        // Q5-Q9: each pair must follow the board's prescribed chapter pairing.
        PunjabBoardPattern.longPairRules(grade).forEachIndexed { index, rule ->
            val section = "Q${index + 5}"
            val firstChapter = rule.first.firstOrNull { chapter ->
                available.any { it.kind.equals("LONG", true) && it.chapterId == chapter &&
                    listOf(it.kind.uppercase(), it.chapterId, it.sourcePage, it.text, it.imageFile).joinToString("|") !in used }
            } ?: return fail("No unused long question is available for $section (first prescribed chapter group).")
            val first = take("LONG", firstChapter, section, "$section(a)")
                ?: return fail("Could not select the first part of $section.")
            val secondChapter = rule.second.firstOrNull { chapter ->
                available.any { it.kind.equals("LONG", true) && it.chapterId == chapter &&
                    listOf(it.kind.uppercase(), it.chapterId, it.sourcePage, it.text, it.imageFile).joinToString("|") !in used }
            } ?: return fail("No unused long question is available for $section (second prescribed chapter group).")
            val second = take("LONG", secondChapter, section, "$section(b)")
                ?: return fail("Could not select the second part of $section.")
            result += first
            result += second
        }

        val errors = PunjabBoardPattern.validationErrors(grade, result)
        return if (errors.isEmpty()) Result.Success(result)
        else Result.NeedsMoreQuestions("The selected textbook questions cannot form a valid board paper: ${errors.joinToString(" ")}")
    }
}
