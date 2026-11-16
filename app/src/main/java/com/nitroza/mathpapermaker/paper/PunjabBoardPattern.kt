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
    val imageFile: String? = null
)

/**
 * Punjab Board Mathematics 9th/10th (2026-27) common paper totals and
 * offered/attempted section structure. Chapter pairing is validated in the
 * next pattern integration stage; these rules never generate questions.
 */
object PunjabBoardPattern {
    const val MCQ_COUNT = 15
    const val SHORT_COUNT = 27
    const val LONG_PART_COUNT = 10
    const val TOTAL_OFFERED_MARKS = 109
    const val TOTAL_ATTEMPTED_MARKS = 75
    const val DURATION_MINUTES = 150

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

    fun isComplete(questions: List<PaperQuestion>): Boolean =
        count(questions, "MCQ") == MCQ_COUNT &&
            count(questions, "SHORT") == SHORT_COUNT &&
            count(questions, "LONG") == LONG_PART_COUNT &&
            marksValid(questions) &&
            questions.map { it.number.trim() }.all { it.isNotEmpty() } &&
            questions.map { it.number.trim() }.distinct().size == questions.size

    fun sections(questions: List<PaperQuestion>): List<PaperSection> {
        val mcqs = questions.filter { it.kind.equals("MCQ", true) }
        val shorts = questions.filter { it.kind.equals("SHORT", true) }
        val longs = questions.filter { it.kind.equals("LONG", true) }
        return buildList {
            if (mcqs.isNotEmpty()) add(PaperSection(
                "Q1 — MULTIPLE CHOICE QUESTIONS",
                "Attempt all ${MCQ_COUNT} questions. Each question carries 1 mark.",
                mcqs
            ))
            shorts.chunked(9).forEachIndexed { index, group ->
                add(PaperSection(
                    "Q${index + 2} — SHORT QUESTIONS",
                    "Attempt any 6 of the 9 questions. Each question carries 2 marks.",
                    group
                ))
            }
            val longBundles = longs.chunked(2)
            val first = longBundles.take(3)
            val second = longBundles.drop(3)
            if (first.isNotEmpty()) {
                first.forEachIndexed { index, bundle ->
                    add(PaperSection(
                        "Q${index + 5} — LONG QUESTION",
                        "This question has two parts. Each part carries 4 marks.",
                        bundle
                    ))
                }
                if (first.size == 3) add(PaperSection(
                    "LONG QUESTION CHOICE",
                    "Attempt any 2 of Q5, Q6 and Q7.",
                    emptyList()
                ))
            }
            if (second.isNotEmpty()) {
                second.forEachIndexed { index, bundle ->
                    add(PaperSection(
                        "Q${index + 8} — LONG QUESTION",
                        "This question has two parts. Each part carries 4 marks.",
                        bundle
                    ))
                }
                if (second.size == 2) add(PaperSection(
                    "LONG QUESTION CHOICE",
                    "Attempt any 1 of Q8 and Q9.",
                    emptyList()
                ))
            }
        }
    }

    fun status(questions: List<PaperQuestion>): String =
        "MCQs ${count(questions, "MCQ")}/$MCQ_COUNT · Short ${count(questions, "SHORT")}/$SHORT_COUNT · Long parts ${count(questions, "LONG")}/$LONG_PART_COUNT"
}
