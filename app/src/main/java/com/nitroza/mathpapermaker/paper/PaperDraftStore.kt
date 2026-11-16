package com.nitroza.mathpapermaker.paper

import android.content.Context
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
    val chapterId: String
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
                    .put("chapterId", q.chapterId))
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
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_DRAFT, null)
            ?: return null
        return runCatching {
            val json = JSONObject(raw)
        val array = json.getJSONArray("questions")
        val questions = (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            StoredPaperQuestion(
                item.getLong("id"),
                item.getString("number"),
                item.optString("text"),
                item.getString("kind"),
                item.getInt("marks"),
                item.getInt("sourcePage"),
                item.optString("imageFile").takeIf { it.isNotBlank() && it != "null" },
                item.optString("chapterId")
            )
        }
            PaperDraftSnapshot(
                json.optInt("grade", 9).coerceIn(9, 10),
                json.optString("examTitle", "Mathematics Examination"),
                json.optString("schoolName"),
                questions
            )
        }.getOrNull()
    }
}
