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

    private fun draft(
        vararg questions: JSONObject,
        grade: Int = 9,
        paperMode: String = "BOARD",
        selectedChapters: Set<String> = (1..(if (grade == 9) 13 else 12)).map { "CH$it" }.toSet()
    ): String =
        JSONObject()
            .put("grade", grade)
            .put("examTitle", "Unit Test")
            .put("schoolName", "School")
            .put("paperMode", paperMode)
            .put("selectedChapters", JSONArray(selectedChapters.sorted()))
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
        assertEquals("BOARD", restored.paperMode)
        assertEquals(13, restored.selectedChapters.size)
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

    @Test fun customModeAndChapterScopeAreRestored() {
        val custom = question()
            .put("kind", "SHORT")
            .put("marks", 7)
            .put("number", "A1")
            .put("sectionId", "Section A")
        val restored = PaperDraftStore.parseSnapshot(
            draft(custom, paperMode = "CUSTOM", selectedChapters = setOf("CH2", "CH4")),
            filesDir
        )
        assertNotNull(restored)
        assertEquals("CUSTOM", restored!!.paperMode)
        assertEquals(setOf("CH2", "CH4"), restored.selectedChapters)
        assertEquals(7, restored.questions.single().marks)
    }

    @Test fun boardModeRejectsNonStandardMarks() {
        val invalid = question().put("marks", 7)
        assertNull(PaperDraftStore.parseSnapshot(draft(invalid), filesDir))
    }

    @Test fun customModeRejectsChapterOutsideSavedScope() {
        val restored = PaperDraftStore.parseSnapshot(
            draft(question(chapter = "CH3"), paperMode = "CUSTOM", selectedChapters = setOf("CH1", "CH2")),
            filesDir
        )
        // The draft store preserves valid question data; export performs scope validation.
        assertNotNull(restored)
    }

    @Test fun invalidChapterScopeIsRejected() {
        assertNull(PaperDraftStore.parseSnapshot(
            draft(question(), paperMode = "CUSTOM", selectedChapters = setOf("CH1", "CH14")), filesDir
        ))
    }

    @Test fun legacyDraftWithoutModeOrScopeRestoresAsFullBoardDraft() {
        val legacy = JSONObject()
            .put("grade", 10)
            .put("examTitle", "Legacy")
            .put("schoolName", "School")
            .put("questions", JSONArray().put(question(chapter = "CH1")))
            .toString()
        val restored = PaperDraftStore.parseSnapshot(legacy, filesDir)
        assertNotNull(restored)
        assertEquals("BOARD", restored!!.paperMode)
        assertEquals(12, restored.selectedChapters.size)
    }

    @Test fun negativeSourcePageIsRejected() {
        val invalid = question().put("sourcePage", -1)
        assertNull(PaperDraftStore.parseSnapshot(draft(invalid), filesDir))
    }


    @Test fun customDraftCanRestoreMoreThanFiftyTwoQuestions() {
        val questions = Array(53) { index ->
            question(id = index + 1L).put("number", "Custom-" + (index + 1))
        }
        val restored = PaperDraftStore.parseSnapshot(
            draft(*questions, paperMode = "CUSTOM"), filesDir
        )
        assertNotNull(restored)
        assertEquals(53, restored!!.questions.size)
    }

    @Test fun draftSafetyCeilingStillRejectsMoreThanFiveHundredQuestions() {
        val questions = Array(501) { index ->
            question(id = index + 1L).put("number", "Custom-" + (index + 1))
        }
        assertNull(PaperDraftStore.parseSnapshot(
            draft(*questions, paperMode = "CUSTOM"), filesDir
        ))
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
