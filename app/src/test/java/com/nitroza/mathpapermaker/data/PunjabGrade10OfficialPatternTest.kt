package com.nitroza.mathpapermaker.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PunjabGrade10OfficialPatternTest {
    private fun pack(): JSONObject {
        val file = listOf(
            File("src/main/assets/content-packs/punjab-grade10-mathematics-2026-27.json"),
            File("app/src/main/assets/content-packs/punjab-grade10-mathematics-2026-27.json")
        ).firstOrNull { it.isFile } ?: error("Punjab Grade 10 2026-27 pattern pack not found")
        return JSONObject(file.readText())
    }

    @Test fun officialIdentityAndPaperTotalsAreExact() {
        val root = pack()
        val identity = root.getJSONObject("identity")
        val pattern = root.getJSONObject("pattern")
        assertEquals("Punjab", identity.getString("board"))
        assertEquals(10, identity.getInt("grade"))
        assertEquals("MATHEMATICS", identity.getString("stream"))
        assertEquals("Mathematics", identity.getString("subject"))
        assertEquals("2026-27", identity.getString("session"))
        assertEquals(75, pattern.getInt("totalMarks"))
        assertEquals(150, pattern.getInt("durationMinutes"))
        assertEquals(52, pattern.getJSONArray("slots").length())
        assertEquals(15, (0 until pattern.getJSONArray("slots").length()).count {
            pattern.getJSONArray("slots").getJSONObject(it).optString("questionType") == "MCQ"
        })
        val mcqCounts = mutableMapOf<String, Int>()
        for (i in 0 until pattern.getJSONArray("slots").length()) {
            val slot = pattern.getJSONArray("slots").getJSONObject(i)
            if (slot.optString("questionType") == "MCQ") {
                val chapter = slot.getString("chapterId")
                mcqCounts[chapter] = (mcqCounts[chapter] ?: 0) + 1
            }
        }
        assertEquals(mapOf("CH1" to 1, "CH2" to 1, "CH3" to 1, "CH4" to 1, "CH5" to 1,
            "CH6" to 1, "CH7" to 1, "CH8" to 1, "CH9" to 2, "CH10" to 1,
            "CH11" to 2, "CH12" to 2), mcqCounts)
    }

    @Test fun shortQuestionChapterMinimumsAreScopedToTheirOwnSection() {
        val quotas = pack().getJSONObject("pattern").getJSONArray("quotas")
        assertEquals(12, quotas.length())
        val expected = mapOf("Q2" to setOf("CH1", "CH2", "CH3", "CH4"),
            "Q3" to setOf("CH5", "CH6", "CH7", "CH8"),
            "Q4" to setOf("CH9", "CH10", "CH11", "CH12"))
        val actual = mutableMapOf<String, MutableSet<String>>()
        for (i in 0 until quotas.length()) {
            val q = quotas.getJSONObject(i)
            assertEquals("CHAPTER_IN_GROUP", q.getString("dimension"))
            val parts = q.getString("value").split("|", limit = 2)
            actual.getOrPut(parts[0]) { mutableSetOf() }.add(parts[1])
            assertEquals(2, q.getInt("minimum"))
            assertEquals(3, q.getInt("maximum"))
        }
        expected.forEach { (group, chapters) -> assertEquals(chapters, actual[group]) }
    }

    @Test fun pairedLongQuestionsAndAlternativeChaptersMatchOfficialScheme() {
        val pattern = pack().getJSONObject("pattern")
        val groups = pattern.getJSONArray("bundleChoiceGroups")
        assertEquals(2, groups.length())
        val first = groups.getJSONObject(0)
        assertEquals("Q5-Q7", first.getString("groupId"))
        assertEquals(3, first.getInt("offeredBundleCount"))
        assertEquals(2, first.getInt("attemptBundleCount"))
        assertEquals(8, first.getInt("marksPerBundle"))
        val second = groups.getJSONObject(1)
        assertEquals("Q8-Q9", second.getString("groupId"))
        assertEquals(2, second.getInt("offeredBundleCount"))
        assertEquals(1, second.getInt("attemptBundleCount"))
        val slots = pattern.getJSONArray("slots")
        val q8b = (0 until slots.length()).map { slots.getJSONObject(it) }.single { it.getString("slotId") == "Q8_B" }
        val q9b = (0 until slots.length()).map { slots.getJSONObject(it) }.single { it.getString("slotId") == "Q9_B" }
        assertEquals(setOf("CH8", "CH11"), (0 until q8b.getJSONArray("allowedChapterIds").length()).map { q8b.getJSONArray("allowedChapterIds").getString(it) }.toSet())
        assertEquals(setOf("CH9", "CH12"), (0 until q9b.getJSONArray("allowedChapterIds").length()).map { q9b.getJSONArray("allowedChapterIds").getString(it) }.toSet())
    }

    @Test fun bundledQuestionBankParsesAlongsideOfficialPattern() {
        val parsed = PackageImporter.parse(pack().toString())
        assertEquals(148, parsed.questions.size)
        assertEquals(148, parsed.questions.map { it.questionId }.toSet().size)
        assertEquals(51, parsed.questions.count { it.questionType == "MCQ" })
        assertEquals(63, parsed.questions.count { it.questionType == "SHORT" })
        assertEquals(34, parsed.questions.count { it.questionType == "LONG" })
        assertTrue(parsed.questions.all { !it.answerText.isNullOrBlank() })
        assertEquals(52, parsed.slots.size)
        assertEquals(3, parsed.choiceGroups.size)
        assertEquals(2, parsed.bundleChoiceGroups.size)
        assertEquals(12, parsed.quotas.size)
        assertEquals(109, parsed.slots.sumOf { it.marks })
        val attempted = parsed.slots.filter { it.groupId == null && it.bundleChoiceGroupId == null }.sumOf { it.marks } +
            parsed.choiceGroups.sumOf { it.attemptCount * it.marksPerQuestion } +
            parsed.bundleChoiceGroups.sumOf { it.attemptBundleCount * it.marksPerBundle }
        assertEquals(75, attempted)
    }
}
