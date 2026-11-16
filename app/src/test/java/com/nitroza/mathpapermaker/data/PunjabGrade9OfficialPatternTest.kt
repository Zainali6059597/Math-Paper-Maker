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
        var attemptedShortMarks = 0
        val expectedShort = mapOf("Q2" to 12, "Q3" to 12, "Q4" to 12)
        for (i in 0 until groups.length()) {
            val group = groups.getJSONObject(i)
            val id = group.getString("groupId")
            val marks = group.getInt("attemptCount") * group.getInt("marksPerQuestion")
            assertEquals(expectedShort[id], marks)
            attemptedShortMarks += marks
        }
        assertEquals(36, attemptedShortMarks)
        val paired = pattern.getJSONArray("bundleChoiceGroups")
        assertEquals(2, paired.length())
        val attemptedLongMarks = (0 until paired.length()).sumOf { i ->
            val group = paired.getJSONObject(i)
            group.getInt("attemptBundleCount") * group.getInt("marksPerBundle")
        }
        assertEquals(24, attemptedLongMarks)
        assertEquals(60, attemptedShortMarks + attemptedLongMarks)
        assertEquals(75, mcqs + attemptedShortMarks + attemptedLongMarks)
    }

    @Test fun longQuestionPartsAndChapterAlternativesMatchTheOfficialPairingMap() {
        val pattern = pack().getJSONObject("pattern")
        val slots = pattern.getJSONArray("slots")
        assertEquals(10, (0 until slots.length()).count { slots.getJSONObject(it).optString("questionType") == "LONG" })
        val q8a = (0 until slots.length()).map { slots.getJSONObject(it) }.single { it.getString("slotId") == "Q8_A" }
        val q9b = (0 until slots.length()).map { slots.getJSONObject(it) }.single { it.getString("slotId") == "Q9_B" }
        assertEquals(setOf("CH7", "CH10"), (0 until q8a.getJSONArray("allowedChapterIds").length()).map { q8a.getJSONArray("allowedChapterIds").getString(it) }.toSet())
        assertEquals(setOf("CH11", "CH13"), (0 until q9b.getJSONArray("allowedChapterIds").length()).map { q9b.getJSONArray("allowedChapterIds").getString(it) }.toSet())
        assertEquals("CH9", (0 until slots.length()).map { slots.getJSONObject(it) }.single { it.getString("slotId") == "Q8_B" }.getString("chapterId"))
        assertEquals("CH12", (0 until slots.length()).map { slots.getJSONObject(it) }.single { it.getString("slotId") == "Q9_A" }.getString("chapterId"))
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
