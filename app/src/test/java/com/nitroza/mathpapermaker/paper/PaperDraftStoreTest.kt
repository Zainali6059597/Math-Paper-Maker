package com.nitroza.mathpapermaker.paper

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class PaperDraftStoreTest {
    private val filesDir = File(System.getProperty("java.io.tmpdir"), "ali-test-series-test-files")

    private fun question(
        id: Long = 1L,
        chapter: String = "CH1",
        imageFile: String? = null
    ) = JSONObject()
        .put("id", id)
        .put("number", "Q1(i)")
        .put("text", "Find the value of x.")
        .put("kind", "MCQ")
        .put("marks", 1)
        .put("sourcePage", 1)
        .put("imageFile", imageFile ?: JSONObject.NULL)
        .put("chapterId", chapter)
        .put("sectionId", "Q1")

    private fun draft(vararg questions: JSONObject, grade: Int = 9): String =
        JSONObject()
            .put("grade", grade)
            .put("examTitle", "Unit Test")
            .put("schoolName", "School")
            .put("questions", JSONArray().apply { questions.forEach { put(it) } })
            .toString()

    @Test fun validDraftRestoresAllQuestionFields() {
        val restored = PaperDraftStore.parseSnapshot(draft(question()), filesDir)
        assertNotNull(restored)
        assertEquals(9, restored!!.grade)
        assertEquals("Unit Test", restored.examTitle)
        assertEquals(1, restored.questions.size)
        assertEquals("Q1(i)", restored.questions.single().number)
        assertEquals("CH1", restored.questions.single().chapterId)
    }

    @Test fun malformedJsonIsRejectedWithoutCrashing() {
        assertNull(PaperDraftStore.parseSnapshot("{not-json", filesDir))
    }

    @Test fun unsupportedGradeIsRejected() {
        assertNull(PaperDraftStore.parseSnapshot(draft(question(), grade = 11), filesDir))
    }

    @Test fun duplicateQuestionIdsAreRejected() {
        assertNull(PaperDraftStore.parseSnapshot(draft(question(7), question(7)), filesDir))
    }

    @Test fun chapterOutsideSelectedGradeIsRejected() {
        assertNull(PaperDraftStore.parseSnapshot(draft(question(chapter = "CH13"), grade = 10), filesDir))
    }

    @Test fun unsupportedQuestionTypeIsRejected() {
        val invalid = question().put("kind", "ESSAY")
        assertNull(PaperDraftStore.parseSnapshot(draft(invalid), filesDir))
    }

    @Test fun wrongMarksForQuestionTypeAreRejected() {
        val invalid = question().put("marks", 2)
        assertNull(PaperDraftStore.parseSnapshot(draft(invalid), filesDir))
    }

    @Test fun invalidImageExtensionIsRejected() {
        val invalidPath = File(filesDir, "question_images/ali_question_1.jpg").absolutePath
        assertNull(PaperDraftStore.parseSnapshot(draft(question(imageFile = invalidPath)), filesDir))
    }

    @Test fun manuallyTypedEditableQuestionCanBeRestored() {
        val manual = question()
            .put("sourcePage", 0)
            .put("chapterId", "")
            .put("sectionId", "Custom Section")
            .put("number", "1")
        val restored = PaperDraftStore.parseSnapshot(draft(manual), filesDir)
        assertNotNull(restored)
        assertEquals(0, restored!!.questions.single().sourcePage)
        assertEquals("", restored.questions.single().chapterId)
        assertEquals("Custom Section", restored.questions.single().sectionId)
    }

    @Test fun negativeSourcePageIsRejected() {
        val invalid = question().put("sourcePage", -1)
        assertNull(PaperDraftStore.parseSnapshot(draft(invalid), filesDir))
    }


    @Test fun tooManyQuestionsAreRejected() {
        val questions = Array(53) { index -> question(id = index + 1L) }
        assertNull(PaperDraftStore.parseSnapshot(draft(*questions), filesDir))
    }

    @Test fun controlCharactersInCustomSectionAreRejected() {
        val invalid = question().put("sectionId", "Custom\u0001Section")
        assertNull(PaperDraftStore.parseSnapshot(draft(invalid), filesDir))
    }

    @Test fun oversizedQuestionTextIsRejected() {
        val invalid = question().put("text", "x".repeat(100_001))
        assertNull(PaperDraftStore.parseSnapshot(draft(invalid), filesDir))
    }

    @Test fun imagePathOutsideAppOwnedFolderIsRejected() {
        val outside = File(filesDir, "not-question-images/ali_question_1.png").absolutePath
        assertNull(PaperDraftStore.parseSnapshot(draft(question(imageFile = outside)), filesDir))
    }

    @Test fun imagePathInsideAppOwnedFolderIsAccepted() {
        val inside = File(filesDir, "question_images/ali_question_1.png").absolutePath
        assertNotNull(PaperDraftStore.parseSnapshot(draft(question(imageFile = inside)), filesDir))
    }
}
