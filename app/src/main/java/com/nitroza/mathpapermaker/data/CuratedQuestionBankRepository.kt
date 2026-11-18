package com.nitroza.mathpapermaker.data

import android.content.Context
import com.nitroza.mathpapermaker.paper.PaperQuestion

/**
 * Offline access to research/import packages after both the question and its source
 * have passed review. Unverified guesses and unreviewed imports never enter Auto.
 */
object CuratedQuestionBankRepository {
    suspend fun approvedPunjabMaths(
        context: Context,
        grade: Int,
        selectedChapters: Set<String>
    ): List<PaperQuestion> {
        require(grade in 9..10)
        if (selectedChapters.isEmpty()) return emptyList()
        return AppDatabase.get(context.applicationContext)
            .contentDao()
            .approvedPunjabMathematicsQuestionBank(grade)
            .asSequence()
            .filter { it.chapterId in selectedChapters }
            .filter { it.questionType.uppercase() in setOf("MCQ", "SHORT", "LONG") }
            .filter { it.questionText.isNotBlank() && it.marks > 0 }
            .distinctBy { it.semanticFingerprint }
            .map { item ->
                PaperQuestion(
                    number = item.questionId,
                    text = item.questionText,
                    kind = item.questionType.uppercase(),
                    marks = item.marks,
                    sourcePage = 0,
                    imageFile = null,
                    chapterId = item.chapterId,
                    sectionId = "CURATED_BANK"
                )
            }
            .toList()
    }
}
