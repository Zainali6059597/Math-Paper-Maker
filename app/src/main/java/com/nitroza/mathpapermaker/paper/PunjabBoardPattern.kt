package com.nitroza.mathpapermaker.paper

data class PaperSection(
    val heading: String,
    val instruction: String,
    val questions: List<PaperQuestion>
)

data class PaperQuestion(
    val number: String,
    val text: String,
    val kind: String,
    val marks: Int,
    val sourcePage: Int,
    val imageFile: String? = null,
    val chapterId: String = ""
)

/** Manual-selection rules for the Punjab Board 2026-27 Class 9/10 Mathematics paper. */
object PunjabBoardPattern {
    const val MCQ_COUNT = 15
    const val SHORT_COUNT = 27
    const val LONG_PART_COUNT = 10
    const val TOTAL_OFFERED_MARKS = 109
    const val TOTAL_ATTEMPTED_MARKS = 75
    const val DURATION_MINUTES = 150

    private val grade9Mcqs = mapOf(
        "CH1" to 1, "CH2" to 1, "CH3" to 2, "CH4" to 2, "CH5" to 1,
        "CH6" to 2, "CH7" to 1, "CH9" to 1, "CH10" to 1, "CH11" to 1,
        "CH12" to 1, "CH13" to 1
    )
    private val grade10Mcqs = mapOf(
        "CH1" to 1, "CH2" to 1, "CH3" to 1, "CH4" to 1, "CH5" to 1,
        "CH6" to 1, "CH7" to 1, "CH8" to 1, "CH9" to 2, "CH10" to 1,
        "CH11" to 2, "CH12" to 2
    )

    private val grade9ShortGroups = listOf(
        mapOf("CH1" to 2, "CH2" to 2, "CH3" to 2, "CH4" to 2, "CH5" to 1),
        mapOf("CH6" to 3, "CH7" to 3, "CH9" to 3),
        mapOf("CH10" to 2, "CH11" to 2, "CH12" to 3, "CH13" to 2)
    )
    private val grade10ShortGroups = listOf(
        setOf("CH1", "CH2", "CH3", "CH4"),
        setOf("CH5", "CH6", "CH7", "CH8"),
        setOf("CH9", "CH10", "CH11", "CH12")
    )

    private data class LongPairRule(val first: Set<String>, val second: Set<String>)
    private val grade9LongPairs = listOf(
        LongPairRule(setOf("CH1"), setOf("CH2")),
        LongPairRule(setOf("CH3"), setOf("CH4")),
        LongPairRule(setOf("CH5"), setOf("CH6")),
        LongPairRule(setOf("CH7", "CH10"), setOf("CH9")),
        LongPairRule(setOf("CH12"), setOf("CH11", "CH13"))
    )
    private val grade10LongPairs = listOf(
        LongPairRule(setOf("CH1"), setOf("CH2")),
        LongPairRule(setOf("CH3"), setOf("CH4")),
        LongPairRule(setOf("CH5"), setOf("CH7")),
        LongPairRule(setOf("CH6"), setOf("CH8", "CH11")),
        LongPairRule(setOf("CH10"), setOf("CH9", "CH12"))
    )

    fun count(questions: List<PaperQuestion>, kind: String): Int =
        questions.count { it.kind.equals(kind, ignoreCase = true) }

    fun marksValid(questions: List<PaperQuestion>): Boolean =
        questions.all { question ->
            when (question.kind.uppercase()) {
                "MCQ" -> question.marks == 1
                "SHORT" -> question.marks == 2
                "LONG" -> question.marks == 4
                else -> false
            }
        }

    fun validationErrors(grade: Int, questions: List<PaperQuestion>): List<String> {
        if (grade !in 9..10) return listOf("Only Class 9 and Class 10 are supported.")
        val errors = mutableListOf<String>()
        val mcqs = questions.filter { it.kind.equals("MCQ", true) }
        val shorts = questions.filter { it.kind.equals("SHORT", true) }
        val longs = questions.filter { it.kind.equals("LONG", true) }

        if (mcqs.size != MCQ_COUNT) errors += "Select exactly 15 MCQs."
        if (shorts.size != SHORT_COUNT) errors += "Select exactly 27 short questions (9 in each of Q2, Q3 and Q4)."
        if (longs.size != LONG_PART_COUNT) errors += "Select exactly 10 long-question parts (two parts for each of Q5–Q9)."
        if (!marksValid(questions)) errors += "Marks must be 1 for MCQs, 2 for shorts and 4 for long parts."
        val labels = questions.map { it.number.trim() }
        if (labels.any { it.isEmpty() }) errors += "Every selected question needs a paper number."
        if (labels.distinct().size != labels.size) errors += "Paper question numbers must be unique."
        if (questions.any { it.chapterId !in 1..13.let { _ -> emptyList<String>() } }) {
            // Chapter validation is handled below using normalized CH-number IDs.
        }
        if (questions.any { !it.chapterId.matches(Regex("CH([1-9]|1[0-3])")) }) {
            errors += "Choose the correct chapter (CH1, CH2, etc.) for every question."
            return errors
        }

        val expectedMcqs = if (grade == 9) grade9Mcqs else grade10Mcqs
        val actualMcqs = mcqs.groupingBy { it.chapterId }.eachCount()
        if (actualMcqs != expectedMcqs) {
            errors += "MCQ chapter distribution does not match the official Class $grade pattern."
        }

        if (shorts.size == SHORT_COUNT) {
            shorts.chunked(9).forEachIndexed { index, group ->
                if (grade == 9) {
                    val expected = grade9ShortGroups[index]
                    val actual = group.groupingBy { it.chapterId }.eachCount()
                    if (actual != expected) errors += "Short-question group Q${index + 2} has the wrong chapter distribution."
                } else {
                    val allowed = grade10ShortGroups[index]
                    val actual = group.groupingBy { it.chapterId }.eachCount()
                    if (allowed.any { (actual[it] ?: 0) < 2 } || actual.keys.any { it !in allowed }) {
                        errors += "Short-question group Q${index + 2} needs at least two questions from each prescribed chapter."
                    }
                }
            }
        }

        if (longs.size == LONG_PART_COUNT) {
            val rules = if (grade == 9) grade9LongPairs else grade10LongPairs
            longs.chunked(2).forEachIndexed { index, pair ->
                val rule = rules[index]
                val chapters = pair.map { it.chapterId }
                val valid = (chapters[0] in rule.first && chapters[1] in rule.second) ||
                    (chapters[1] in rule.first && chapters[0] in rule.second)
                if (!valid) errors += "Long-question pair Q${index + 5} has an invalid chapter pairing."
            }
        }
        return errors
    }

    fun isComplete(grade: Int, questions: List<PaperQuestion>): Boolean =
        validationErrors(grade, questions).isEmpty()

    fun sections(questions: List<PaperQuestion>): List<PaperSection> {
        val mcqs = questions.filter { it.kind.equals("MCQ", true) }
        val shorts = questions.filter { it.kind.equals("SHORT", true) }
        val longs = questions.filter { it.kind.equals("LONG", true) }
        return buildList {
            if (mcqs.isNotEmpty()) add(PaperSection(
                "Q1 — MULTIPLE CHOICE QUESTIONS",
                "Attempt all 15 questions. Each question carries 1 mark.",
                mcqs
            ))
            shorts.chunked(9).forEachIndexed { index, group ->
                add(PaperSection(
                    "Q${index + 2} — SHORT QUESTIONS",
                    "Attempt any 6 of the 9 questions. Each question carries 2 marks.",
                    group
                ))
            }
            val bundles = longs.chunked(2)
            bundles.take(3).forEachIndexed { index, bundle ->
                add(PaperSection(
                    "Q${index + 5} — LONG QUESTION",
                    "This question has two parts. Each part carries 4 marks.",
                    bundle
                ))
            }
            if (bundles.size >= 3) add(PaperSection("LONG QUESTION CHOICE", "Attempt any 2 of Q5, Q6 and Q7.", emptyList()))
            bundles.drop(3).forEachIndexed { index, bundle ->
                add(PaperSection(
                    "Q${index + 8} — LONG QUESTION",
                    "This question has two parts. Each part carries 4 marks.",
                    bundle
                ))
            }
            if (bundles.size >= 5) add(PaperSection("LONG QUESTION CHOICE", "Attempt any 1 of Q8 and Q9.", emptyList()))
        }
    }

    fun status(questions: List<PaperQuestion>): String =
        "MCQs ${count(questions, "MCQ")}/$MCQ_COUNT · Short ${count(questions, "SHORT")}/$SHORT_COUNT · Long parts ${count(questions, "LONG")}/$LONG_PART_COUNT"
}
