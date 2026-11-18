package com.nitroza.mathpapermaker.data

import com.nitroza.mathpapermaker.paper.PaperQuestion

/** Pure conversion from approved Room rows to the generator's source-question model. */
object TextbookQuestionBankMapper {
    fun toPaperQuestions(
        candidates: List<TextbookCandidateEntity>,
        selectedChapters: Set<String>
    ): List<PaperQuestion> = candidates.asSequence()
        .filter { it.reviewStatus == "APPROVED" }
        .filter { it.chapterId in selectedChapters }
        .filter { it.questionType in setOf("MCQ", "SHORT", "LONG") }
        .filter { it.questionText.isNotBlank() || !it.imageFile.isNullOrBlank() }
        .map { candidate ->
            PaperQuestion(
                number = candidate.questionNumber,
                text = candidate.questionText,
                kind = candidate.questionType,
                marks = candidate.marks,
                sourcePage = candidate.sourcePage,
                imageFile = candidate.imageFile,
                chapterId = candidate.chapterId,
                sectionId = "BANK"
            )
        }
        .toList()
}
