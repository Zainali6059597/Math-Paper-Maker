package com.nitroza.mathpapermaker.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BundledBookManifestReaderTest {
    private val hash = "a".repeat(64)
    private val hashes = mapOf("maths9.pdf" to hash, "fbise9.pdf" to hash)
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
              "edition": "2025-26", "language": "en", "publisher": "PECTAA",
              "sourceUrl": "https://pectaa.edu.pk/math9.pdf", "licenseOrPermission": "REDISTRIBUTION_CONFIRMED",
              "pageCount": 20, "sha256": "$hash",
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
              "edition": "2025-26", "language": "en", "publisher": "PECTAA",
              "sourceUrl": "https://pectaa.edu.pk/fbise9.pdf", "licenseOrPermission": "REDISTRIBUTION_CONFIRMED",
              "pageCount": 20, "sha256": "$hash",
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
            "Punjab", 9, "MATHEMATICS", "Mathematics", "2026-27", hashes
        )
        assertEquals(listOf("1", "2"), chapters.map { it.id })
        assertEquals(listOf("Matrices", "Real Numbers"), chapters.map { it.title })
    }

    @Test fun ignoresManifestBooksWhosePdfIsNotBundled() {
        val chapters = BundledBookManifestReader.parseManifest(
            manifest, setOf("fbise9.pdf"),
            "Punjab", 9, "MATHEMATICS", "Mathematics", "2026-27", mapOf("fbise9.pdf" to hash)
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
                manifest, setOf("maths9.pdf"),
                request[0] as String, request[1] as Int, request[2] as String,
                request[3] as String, request[4] as String, hashes
            )
            assertTrue(chapters.isEmpty())
        }
    }

    @Test fun ignoresBooksWithoutConfirmedRedistributionPermissionOrMatchingHash() {
        val unlicensed = manifest.replace("REDISTRIBUTION_CONFIRMED", "PUBLIC_DOWNLOAD_ONLY")
        val denied = BundledBookManifestReader.parseManifest(
            unlicensed, setOf("maths9.pdf"), "Punjab", 9, "MATHEMATICS", "Mathematics", "2026-27", hashes
        )
        assertTrue(denied.isEmpty())
        val wrongHash = BundledBookManifestReader.parseManifest(
            manifest, setOf("maths9.pdf"), "Punjab", 9, "MATHEMATICS", "Mathematics", "2026-27",
            mapOf("maths9.pdf" to "b".repeat(64))
        )
        assertTrue(wrongHash.isEmpty())
    }

    @Test fun rejectsOverlappingChapterPageRanges() {
        val overlapping = manifest.replace(
            "\"startPage\":10,\"endPage\":18",
            "\"startPage\":8,\"endPage\":18"
        )
        val chapters = BundledBookManifestReader.parseManifest(
            overlapping, setOf("maths9.pdf"),
            "Punjab", 9, "MATHEMATICS", "Mathematics", "2026-27", hashes
        )
        assertTrue(chapters.isEmpty())
    }

    @Test fun rejectsDuplicateChapterIds() {
        val duplicateIds = manifest.replace(
            "\"chapterId\":\"2\"",
            "\"chapterId\":\"1\""
        )
        val chapters = BundledBookManifestReader.parseManifest(
            duplicateIds, setOf("maths9.pdf"),
            "Punjab", 9, "MATHEMATICS", "Mathematics", "2026-27", hashes
        )
        assertTrue(chapters.isEmpty())
    }

    @Test fun rejectsMultipleBooksForTheSameIdentity() {
        val duplicateBook = """
            {
              "file": "maths9-copy.pdf",
              "board": "Punjab",
              "grade": 9,
              "stream": "MATHEMATICS",
              "subject": "Mathematics",
              "session": "2026-27",
              "edition": "2025-26", "language": "en", "publisher": "PECTAA",
              "sourceUrl": "https://pectaa.edu.pk/math9-copy.pdf", "licenseOrPermission": "REDISTRIBUTION_CONFIRMED",
              "pageCount": 20, "sha256": "$hash",
              "chapters": [
                {"chapterId":"1","title":"Different edition","startPage":1,"endPage":5}
              ]
            }
        """.trimIndent()
        val duplicatedManifest = manifest.replace(
            "\"books\": [",
            "\"books\": [\n$duplicateBook,"
        )
        val chapters = BundledBookManifestReader.parseManifest(
            duplicatedManifest, setOf("maths9.pdf", "maths9-copy.pdf", "fbise9.pdf"),
            "Punjab", 9, "MATHEMATICS", "Mathematics", "2026-27",
            hashes + ("maths9-copy.pdf" to hash)
        )
        assertTrue(chapters.isEmpty())
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
