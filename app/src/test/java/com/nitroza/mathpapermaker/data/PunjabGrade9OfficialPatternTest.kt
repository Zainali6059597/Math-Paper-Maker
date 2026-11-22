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
        assertEquals(2, root.getJSONObject("pattern").getInt("version"))
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

    @Test fun mcqAndShortQuestionChapterDistributionsMatchNotification1238() {
        val pattern = pack().getJSONObject("pattern")
        val slots = pattern.getJSONArray("slots")
        val mcqCounts = mutableMapOf<String, Int>()
        val shortCounts = mutableMapOf<String, MutableMap<String, Int>>()
        for (i in 0 until slots.length()) {
            val slot = slots.getJSONObject(i)
            val chapter = slot.optString("chapterId")
            when (slot.optString("questionType")) {
                "MCQ" -> mcqCounts[chapter] = (mcqCounts[chapter] ?: 0) + 1
                "SHORT" -> {
                    val group = slot.getString("groupId")
                    val counts = shortCounts.getOrPut(group) { mutableMapOf() }
                    counts[chapter] = (counts[chapter] ?: 0) + 1
                }
            }
        }
        assertEquals(mapOf("CH1" to 1, "CH2" to 1, "CH3" to 2, "CH4" to 2, "CH5" to 1,
            "CH6" to 2, "CH7" to 1, "CH9" to 1, "CH10" to 1, "CH11" to 1, "CH12" to 1, "CH13" to 1), mcqCounts)
        assertEquals(mapOf("CH1" to 2, "CH2" to 2, "CH3" to 2, "CH4" to 2, "CH5" to 1), shortCounts["Q2"])
        assertEquals(mapOf("CH6" to 3, "CH7" to 3, "CH9" to 3), shortCounts["Q3"])
        assertEquals(mapOf("CH10" to 2, "CH11" to 2, "CH12" to 3, "CH13" to 2), shortCounts["Q4"])
    }

    @Test fun correctedGrade9PackageImportsWholePairedBundlesAndAlternatives() {
        val parsed = PackageImporter.parse(pack().toString())
        assertEquals(52, parsed.slots.size)
        assertEquals(3, parsed.choiceGroups.size)
        assertEquals(2, parsed.bundleChoiceGroups.size)
        assertEquals(134, parsed.questions.size)
        val q8a = parsed.slots.single { it.slotId == "Q8_A" }
        val q9b = parsed.slots.single { it.slotId == "Q9_B" }
        assertEquals(setOf("CH7", "CH10"),
            (0 until org.json.JSONArray(q8a.allowedChapterIdsJson).length()).map { org.json.JSONArray(q8a.allowedChapterIdsJson).getString(it) }.toSet())
        assertEquals(setOf("CH11", "CH13"),
            (0 until org.json.JSONArray(q9b.allowedChapterIdsJson).length()).map { org.json.JSONArray(q9b.allowedChapterIdsJson).getString(it) }.toSet())
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
