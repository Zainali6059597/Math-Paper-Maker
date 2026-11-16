package com.nitroza.mathpapermaker.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PackageImporterTest {
    private fun packageJson(allowed: String = """["CH8","CH11"]""") = """
        {
          "schemaVersion": 5,
          "identity": {"board":"PUNJAB","grade":10,"stream":"MATHEMATICS","subject":"Mathematics","session":"2026-27"},
          "source": {"sourceId":"TEST-SOURCE","issuer":"PECTAA","title":"Test pattern","sourceType":"OFFICIAL_PAIRING_SCHEME","session":"2026-27"},
          "pattern": {
            "patternId":"TEST-PATTERN","version":1,"totalMarks":25,"durationMinutes":150,
            "slots":[{"slotId":"ALT1","marks":25,"questionType":"LONG","groupId":"Q2","allowedChapterIds":$allowed},{"slotId":"ALT2","marks":25,"questionType":"LONG","groupId":"Q2","allowedChapterIds":$allowed},{"slotId":"ALT3","marks":25,"questionType":"LONG","groupId":"Q2","allowedChapterIds":$allowed}],
            "choiceGroups":[{"groupId":"Q2","offeredCount":3,"attemptCount":1,"marksPerQuestion":25}],
            "quotas":[{"dimension":"CHAPTER_IN_GROUP","value":"Q2|CH1","minimum":2,"maximum":3}]
          },
          "questions":[]
        }
    """.trimIndent()

    @Test fun importsPatternOnlyPackageAndRetainsChapterAlternativesAndScopedQuota() {
        val parsed = PackageImporter.parse(packageJson())
        assertTrue(parsed.questions.isEmpty())
        assertEquals("Q2", parsed.choiceGroups.single().groupId)
        assertEquals(setOf("CH8", "CH11"),
            (0 until org.json.JSONArray(parsed.slots.single().allowedChapterIdsJson).length())
                .map { org.json.JSONArray(parsed.slots.single().allowedChapterIdsJson).getString(it) }.toSet())
        assertEquals("CHAPTER_IN_GROUP", parsed.quotas.single().dimension)
        assertEquals("Q2|CH1", parsed.quotas.single().value)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsDuplicateChapterAlternatives() {
        PackageImporter.parse(packageJson("""["CH8","CH8"]"""))
    }
}
