package com.nitroza.mathpapermaker.paper

import android.content.Context
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

data class StoredPaperQuestion(
    val id: Long,
    val number: String,
    val text: String,
    val kind: String,
    val marks: Int,
    val sourcePage: Int,
    val imageFile: String?,
    val chapterId: String,
    val sectionId: String
)

data class PaperDraftSnapshot(
    val grade: Int,
    val examTitle: String,
    val schoolName: String,
    val questions: List<StoredPaperQuestion>
)

/** One local working draft; no account, network or cloud sync. */
object PaperDraftStore {
    private const val PREFS = "ali_test_series_draft"
    private const val KEY_DRAFT = "current"

    fun save(context: Context, draft: PaperDraftSnapshot) {
        val questions = JSONArray().apply {
            draft.questions.forEach { q ->
                put(JSONObject()
                    .put("id", q.id)
                    .put("number", q.number)
                    .put("text", q.text)
                    .put("kind", q.kind)
                    .put("marks", q.marks)
                    .put("sourcePage", q.sourcePage)
                    .put("imageFile", q.imageFile ?: JSONObject.NULL)
                    .put("chapterId", q.chapterId)
                    .put("sectionId", q.sectionId))
            }
        }
        val json = JSONObject()
            .put("grade", draft.grade)
            .put("examTitle", draft.examTitle)
            .put("schoolName", draft.schoolName)
            .put("questions", questions)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_DRAFT, json.toString()).apply()
    }

    fun load(context: Context): PaperDraftSnapshot? {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_DRAFT, null) ?: return null
        return parseSnapshot(raw, context.filesDir)
    }

    internal fun parseSnapshot(raw: String, filesDir: File): PaperDraftSnapshot? {
        return runCatching {
            val json = JSONObject(raw)
            val grade = json.optInt("grade", 9)
            require(grade == 9 || grade == 10) { "Invalid saved grade." }

            val array = json.getJSONArray("questions")
            val questions = (0 until array.length()).map { index ->
                val item = array.getJSONObject(index)
                val number = item.getString("number").trim()
                val kind = item.getString("kind")
                val marks = item.getInt("marks")
                val sourcePage = item.getInt("sourcePage")
                val chapterId = item.optString("chapterId")
                val sectionId = item.optString(
                    "sectionId",
                    number.substringBefore("(")
                )
                val imageFile = item.optString("imageFile")
                    .takeIf { it.isNotBlank() && it != "null" }
                imageFile?.let { path ->
                    val image = File(path).canonicalFile
                    val imageDirectory = File(filesDir, "question_images").canonicalFile
                    require(image.parentFile == imageDirectory &&
                        image.name.startsWith("ali_question_") &&
                        image.extension.equals("png", ignoreCase = true)) {
                        "Saved question image path is outside the app image folder."
                    }
                }

                require(item.getLong("id") > 0L) { "Invalid saved question ID." }
                require(number.isNotBlank()) { "Saved question number is blank." }
                require(kind in setOf("MCQ", "SHORT", "LONG")) { "Invalid saved question type." }
                require(marks > 0 && sourcePage > 0) { "Invalid saved question marks or page." }
                require(PunjabBoardPattern.isValidChapter(grade, chapterId)) {
                    "Invalid saved chapter."
                }
                require(sectionId.isNotBlank()) { "Saved question section is blank." }

                StoredPaperQuestion(
                    item.getLong("id"),
                    number,
                    item.optString("text"),
                    kind,
                    marks,
                    sourcePage,
                    imageFile,
                    chapterId,
                    sectionId
                )
            }
            require(questions.map { it.id }.distinct().size == questions.size) {
                "Duplicate saved question IDs."
            }

            PaperDraftSnapshot(
                grade,
                json.optString("examTitle", "Mathematics Examination"),
                json.optString("schoolName"),
                questions
            )
        }.getOrNull()
    }
}
