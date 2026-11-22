package com.nitroza.mathpapermaker.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CuratedQuestionBankAssetTest {
    @Test
    fun expandedAutoBankIsActuallyBundledAndUsableOffline() {
        val file = File("src/main/assets/content-packs/ali_test_series_textbooks.json")
        assertTrue("Auto Paper bank must be packaged as an Android asset", file.isFile)

        val root = JSONObject(file.readText(Charsets.UTF_8))
        assertEquals(3, root.getInt("version"))
        assertEquals(282, root.getJSONObject("totals").getInt("totalQuestions"))

        val expectedCounts = mapOf("9" to 134, "10" to 148)
        expectedCounts.forEach { (grade, expectedCount) ->
            val gradeData = root.getJSONObject("grades").getJSONObject(grade)
            val questions = gradeData.getJSONArray("questions")
            assertEquals("Grade $grade question count", expectedCount, questions.length())
            assertEquals(expectedCount, gradeData.getInt("questionCount"))

            val ids = mutableSetOf<String>()
            val fingerprints = mutableSetOf<String>()
            val contentKeys = mutableSetOf<String>()
            for (index in 0 until questions.length()) {
                val question = questions.getJSONObject(index)
                val id = question.getString("questionId")
                val chapter = question.getString("chapterId")
                val kind = question.getString("questionType")
                val text = question.getString("questionText").trim()
                val answer = question.getString("answerText").trim()

                assertTrue("Duplicate question ID: $id", ids.add(id))
                assertTrue("Duplicate question text in $chapter/$kind: $id",
                    contentKeys.add("$chapter|$kind|$text".lowercase().filter { it.isLetterOrDigit() }))
                assertTrue("Question needs an answer: $id", answer.isNotBlank())
                assertTrue("Question needs text: $id", text.isNotBlank())
                assertTrue("Question must be original: $id",
                    question.getString("copyrightStatus") == "ORIGINAL")
                assertTrue("Question must be independently authored: $id",
                    question.getString("sourceType") == "ORIGINAL_AUTHORED")
                assertTrue("Question type must be supported: $id",
                    kind in setOf("MCQ", "SHORT", "LONG"))
                assertTrue("Question marks must be positive: $id", question.getInt("marks") > 0)
                assertFalse("Question cannot be blank: $id", text.isBlank())
                question.optString("semanticFingerprint").takeIf { it.isNotBlank() }?.let {
                    assertTrue("Duplicate semantic fingerprint: $id", fingerprints.add(it))
                }
            }
            assertTrue("Grade $grade must include all question types",
                setOf("MCQ", "SHORT", "LONG").all { type ->
                    (0 until questions.length()).any { questions.getJSONObject(it).getString("questionType") == type }
                })
        }
    }
}
