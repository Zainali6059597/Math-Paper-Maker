package com.nitroza.mathpapermaker.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BundledBookManifestReaderTest {
    private val manifest = """
        {
          "manifestVersion": 1,
          "books": [
            {
              "file": "maths9.pdf",
              "board": "Punjab",
              "grade": 9,
              "stream": "MATHEMATICS",
              "subject": "Mathematics",
              "session": "2026-27",
              "chapters": [
                {"chapterId":"2","title":"Real Numbers","startPage":10,"endPage":18},
                {"chapterId":"1","title":"Matrices","startPage":2,"endPage":9},
                {"chapterId":"3","title":"Sets","startPage":19,"endPage":18}
              ]
            },
            {
              "file": "fbise9.pdf",
              "board": "FBISE",
              "grade": 9,
              "stream": "MATHEMATICS",
              "subject": "Mathematics",
              "session": "2026-27",
              "chapters": [
                {"chapterId":"1","title":"FBISE Chapter","startPage":1,"endPage":5}
              ]
            }
          ]
        }
    """.trimIndent()

    @Test fun returnsOnlyBundledExactIdentityChaptersInNumericOrder() {
        val chapters = BundledBookManifestReader.parseManifest(
            manifest, setOf("maths9.pdf", "fbise9.pdf"),
            "Punjab", 9, "MATHEMATICS", "Mathematics", "2026-27"
        )
        assertEquals(listOf("1", "2"), chapters.map { it.id })
        assertEquals(listOf("Matrices", "Real Numbers"), chapters.map { it.title })
    }

    @Test fun ignoresManifestBooksWhosePdfIsNotBundled() {
        val chapters = BundledBookManifestReader.parseManifest(
            manifest, setOf("fbise9.pdf"),
            "Punjab", 9, "MATHEMATICS", "Mathematics", "2026-27"
        )
        assertTrue(chapters.isEmpty())
    }

    @Test fun refusesCrossBoardGradeStreamSubjectOrSessionFallback() {
        val requests = listOf(
            listOf("FBISE", 9, "MATHEMATICS", "Mathematics", "2026-27"),
            listOf("Punjab", 10, "MATHEMATICS", "Mathematics", "2026-27"),
            listOf("Punjab", 9, "GENERAL_MATHEMATICS", "Mathematics", "2026-27"),
            listOf("Punjab", 9, "MATHEMATICS", "General Mathematics", "2026-27"),
            listOf("Punjab", 9, "MATHEMATICS", "Mathematics", "2025-26")
        )
        requests.forEach { request ->
            val chapters = BundledBookManifestReader.parseManifest(
                manifest, setOf("maths9.pdf", "fbise9.pdf"),
                request[0] as String, request[1] as Int, request[2] as String,
                request[3] as String, request[4] as String
            )
            assertTrue(chapters.isEmpty())
        }
    }

    @Test fun rejectsUnsupportedManifestVersion() {
        val error = runCatching {
            BundledBookManifestReader.parseManifest(
                """{"manifestVersion":2,"books":[]}""", emptySet(),
                "Punjab", 9, "MATHEMATICS", "Mathematics", "2026-27"
            )
        }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }
}
