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
    private const val MAX_QUESTIONS = 52
    private const val MAX_DRAFT_CHARS = 2_000_000
    private const val MAX_QUESTION_TEXT_CHARS = 100_000

    fun save(context: Context, draft: PaperDraftSnapshot) {
        // Reject oversized in-memory input before building a second JSON copy of it.
        if (draft.grade !in 9..10 || draft.questions.size > MAX_QUESTIONS ||
            draft.examTitle.length > 500 || draft.schoolName.length > 500 ||
            draft.questions.any { it.text.length > MAX_QUESTION_TEXT_CHARS } ||
            draft.questions.sumOf { it.text.length.toLong() } > MAX_DRAFT_CHARS.toLong()
        ) return

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
        val serialized = json.toString()
        // A draft is local recovery data, not an unbounded document store.
        if (serialized.length > MAX_DRAFT_CHARS) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_DRAFT, serialized).apply()
    }

    fun load(context: Context): PaperDraftSnapshot? {
        val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = preferences.getString(KEY_DRAFT, null) ?: return null
        val restored = parseSnapshot(raw, context.filesDir)
        if (restored == null) {
            // Drop an unreadable/corrupt draft once, rather than parsing it on every launch.
            preferences.edit().remove(KEY_DRAFT).apply()
        }
        return restored
    }

    internal fun parseSnapshot(raw: String, filesDir: File): PaperDraftSnapshot? {
        return runCatching {
            require(raw.length <= MAX_DRAFT_CHARS) { "Saved draft is too large." }
            val json = JSONObject(raw)
            val grade = json.optInt("grade", 9)
            require(grade == 9 || grade == 10) { "Invalid saved grade." }

            require(json.optString("examTitle").length <= 500 &&
                json.optString("schoolName").length <= 500) { "Saved paper details are too long." }
            val array = json.getJSONArray("questions")
            require(array.length() <= MAX_QUESTIONS) { "Saved draft has too many questions." }
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
                require(number.isNotBlank() && number.length <= 16) { "Saved question number is invalid." }
                require(item.optString("text").length <= MAX_QUESTION_TEXT_CHARS) {
                    "Saved question text is too long."
                }
                require(kind in setOf("MCQ", "SHORT", "LONG")) { "Invalid saved question type." }
                require(marks == when (kind) { "MCQ" -> 1; "SHORT" -> 2; else -> 4 } &&
                    sourcePage >= 0 && (sourcePage > 0 || item.optString("text").isNotBlank())
                ) {
                    "Invalid saved question marks or page. Manually typed questions must retain editable text."
                }
                require(item.optString("text").isNotBlank() || !imageFile.isNullOrBlank()) {
                    "A saved question must contain editable text or a scanned image."
                }
                require(PunjabBoardPattern.isValidChapter(grade, chapterId)) {
                    "Invalid saved chapter."
                }
                require(sectionId in (1..9).map { "Q$it" }) { "Invalid saved question section." }

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
