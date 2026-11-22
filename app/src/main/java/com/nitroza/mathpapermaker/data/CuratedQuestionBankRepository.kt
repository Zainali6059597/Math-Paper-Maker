package com.nitroza.mathpapermaker.data

import android.content.Context
import com.nitroza.mathpapermaker.paper.PaperQuestion
import org.json.JSONObject

/**
 * Offline question bank for the Punjab Mathematics Auto paper workflow.
 *
 * Original, independently authored questions ship inside the APK. They are
 * intentionally read directly from assets rather than routed through the
 * research-import approval queue.
 */
object CuratedQuestionBankRepository {
    private const val BUNDLED_BANK_ASSET = "content-packs/ali_test_series_textbooks.json"

    suspend fun approvedPunjabMaths(
        context: Context,
        grade: Int,
        selectedChapters: Set<String>
    ): List<PaperQuestion> {
        require(grade in 9..10)
        if (selectedChapters.isEmpty()) return emptyList()

        val appContext = context.applicationContext
        val bundled = bundledOriginalQuestions(appContext, grade, selectedChapters)
        val databaseQuestions = runCatching {
            AppDatabase.get(appContext)
                .contentDao()
                .approvedPunjabMathematicsQuestionBank(grade)
                .asSequence()
                .filter { it.chapterId in selectedChapters }
                .filter { it.questionType.uppercase() in setOf("MCQ", "SHORT", "LONG") }
                .filter { it.questionText.isNotBlank() && it.marks > 0 }
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
        }.getOrDefault(emptyList())

        return (bundled + databaseQuestions)
            .distinctBy { listOf(it.kind.uppercase(), it.chapterId, it.text.trim().lowercase()).joinToString("|") }
    }

    private fun bundledOriginalQuestions(
        context: Context,
        grade: Int,
        selectedChapters: Set<String>
    ): List<PaperQuestion> {
        val raw = context.assets.open(BUNDLED_BANK_ASSET).bufferedReader(Charsets.UTF_8).use { it.readText() }
        val root = JSONObject(raw)
        require(root.optInt("version") == 3) { "Unsupported bundled question-bank version." }
        val gradeData = root.getJSONObject("grades").getJSONObject(grade.toString())
        val identity = gradeData.getJSONObject("identity")
        require(identity.optInt("grade") == grade && identity.optString("board") == "Punjab") {
            "Bundled question-bank identity does not match the selected class."
        }
        val questions = gradeData.getJSONArray("questions")
        return (0 until questions.length()).mapNotNull { index ->
            val item = questions.getJSONObject(index)
            val chapterId = item.optString("chapterId")
            val kind = item.optString("questionType").uppercase()
            val text = item.optString("questionText").trim()
            val marks = item.optInt("marks")
            val original = item.optString("copyrightStatus") == "ORIGINAL"
            val authored = item.optString("sourceType") == "ORIGINAL_AUTHORED"
            if (!original || !authored || chapterId !in selectedChapters ||
                kind !in setOf("MCQ", "SHORT", "LONG") || text.isBlank() || marks <= 0
            ) return@mapNotNull null

            PaperQuestion(
                number = item.optString("questionId"),
                text = text,
                kind = kind,
                marks = marks,
                sourcePage = 0,
                imageFile = null,
                chapterId = chapterId,
                sectionId = "BUNDLED_BANK"
            )
        }
    }
}
