package com.nitroza.mathpapermaker.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PunjabGrade9OfficialPatternTest {
    private fun pack(): JSONObject {
        val candidates = listOf(
            File("src/main/assets/content-packs/punjab-grade9-mathematics-2026-27.json"),
            File("app/src/main/assets/content-packs/punjab-grade9-mathematics-2026-27.json")
        )
        val file = candidates.firstOrNull { it.isFile }
            ?: error("Punjab Grade 9 2026-27 content pack not found")
        return JSONObject(file.readText())
    }

    @Test fun identifiesTheExactCurrentPunjabGrade9SessionAndOfficialSource() {
        val root = pack()
        val identity = root.getJSONObject("identity")
        val source = root.getJSONObject("source")
        assertEquals("Punjab", identity.getString("board"))
        assertEquals(9, identity.getInt("grade"))
        assertEquals("MATHEMATICS", identity.getString("stream"))
        assertEquals("Mathematics", identity.getString("subject"))
        assertEquals("2026-27", identity.getString("session"))
        assertEquals("OFFICIAL_PAIRING_SCHEME", source.getString("sourceType"))
        assertTrue(source.getString("sourceUrl").contains("pectaa.edu.pk/"))
        assertTrue(source.getString("title").contains("2026"))
    }

    @Test fun paperTotalsSeventyFiveMarksWithCorrectAttemptedSectionArithmetic() {
        val pattern = pack().getJSONObject("pattern")
        assertEquals(75, pattern.getInt("totalMarks"))
        assertEquals(150, pattern.getInt("durationMinutes"))

        val slots = pattern.getJSONArray("slots")
        var mcqs = 0
        for (i in 0 until slots.length()) {
            if (slots.getJSONObject(i).getString("questionType") == "MCQ") mcqs++
        }
        assertEquals(15, mcqs)

        val groups = pattern.getJSONArray("choiceGroups")
        var attemptedSubjectiveMarks = 0
        val expected = mapOf("Q2" to 12, "Q3" to 12, "Q4" to 12, "Q5-Q7" to 16, "Q8-Q9" to 8)
        for (i in 0 until groups.length()) {
            val group = groups.getJSONObject(i)
            val id = group.getString("groupId")
            val marks = group.getInt("attemptCount") * group.getInt("marksPerQuestion")
            assertEquals(expected[id], marks)
            attemptedSubjectiveMarks += marks
        }
        assertEquals(60, attemptedSubjectiveMarks)
        assertEquals(75, mcqs + attemptedSubjectiveMarks)
    }

    @Test fun excludedChapterEightNeverAppearsInGenerationSlots() {
        val pattern = pack().getJSONObject("pattern")
        val slots = pattern.getJSONArray("slots")
        for (i in 0 until slots.length()) {
            assertFalse("Deleted Chapter 8 must not appear in a slot",
                slots.getJSONObject(i).optString("chapterId") == "CH8")
        }
        val exclusions = pattern.getJSONArray("exclusions")
        assertTrue((0 until exclusions.length()).any {
            exclusions.getJSONObject(it).optString("value") == "CH8"
        })
    }
}
