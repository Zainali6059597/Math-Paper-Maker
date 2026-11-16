package com.nitroza.mathpapermaker.data

/** Conservative parser: extracts numbered exercise candidates only, never ordinary prose. */
object TextbookExerciseParser {
    data class Candidate(
        val bookFile: String, val chapterId: String, val exerciseId: String,
        val questionNumber: String, val text: String, val sourcePage: Int,
        val verificationStatus: String = "NEEDS_REVIEW"
    )
    data class ParseResult(val candidates: List<Candidate>, val pagesNeedingOcr: List<Int>, val warnings: List<String>)

    private val chapterPattern = Regex("""(?i)^\s*(?:chapter|unit)\s+([0-9]{1,2}|[ivxlcdm]+)\b.*$""")
    private val exercisePattern = Regex("""(?i)^\s*(?:exercise|ex\.?|review exercise)\s*([0-9]+(?:\.[0-9]+)?)\b.*$""")
    private val questionStartPattern = Regex("""^\s*(?:Q\.?\s*)?([0-9]{1,2})(?:\s*[.)])(?:\s+(.*))?$""")

    fun parse(book: BundledBookPdfReader.BookIndex): ParseResult {
        var chapter = "UNMAPPED"
        var exercise = "UNMAPPED"
        val candidates = mutableListOf<Candidate>()
        var current: MutableCandidate? = null
        fun flush() {
            current?.let { item ->
                val text = item.lines.joinToString(" ").replace(Regex("""\s+"""), " ").trim()
                if (text.length >= 8) candidates += Candidate(
                    book.bookFile, item.chapter, item.exercise, item.number, text, item.page
                )
            }
            current = null
        }
        for (page in book.pages) {
            if (page.needsOcr) continue
            for (rawLine in page.text.lines()) {
                val line = rawLine.trim()
                if (line.isBlank()) continue
                val chapterMatch = chapterPattern.matchEntire(line)
                if (chapterMatch != null) {
                    flush(); chapter = chapterMatch.groupValues[1].uppercase()
                    exercise = "UNMAPPED"; continue
                }
                val exerciseMatch = exercisePattern.matchEntire(line)
                if (exerciseMatch != null) {
                    flush(); exercise = exerciseMatch.groupValues[1]; continue
                }
                val questionMatch = questionStartPattern.matchEntire(line)
                if (questionMatch != null && exercise != "UNMAPPED") {
                    flush()
                    val firstLine = questionMatch.groupValues.getOrNull(2).orEmpty().trim()
                    current = MutableCandidate(
                        chapter, exercise, questionMatch.groupValues[1],
                        page.pageNumber, mutableListOf<String>().apply {
                            if (firstLine.isNotBlank()) add(firstLine)
                        }
                    )
                } else if (current != null && line.length < 240 &&
                    !chapterPattern.matches(line) && !exercisePattern.matches(line)
                ) current!!.lines += line
            }
            flush()
        }
        val warnings = buildList {
            if (book.readError != null) add("Could not read ${book.bookFile}: ${book.readError}")
            if (book.pages.isEmpty() && book.readError == null) add("No readable pages were found.")
            if (book.pagesNeedingOcr.isNotEmpty()) add(
                "${book.pagesNeedingOcr.size} scanned/image-only pages need OCR and were skipped."
            )
            if (candidates.isEmpty()) add("No numbered exercise questions detected; check PDF text and heading format.")
            if (candidates.any { it.chapterId == "UNMAPPED" }) add("Some questions have no recognized chapter heading.")
        }
        return ParseResult(candidates, book.pagesNeedingOcr, warnings)
    }
    private data class MutableCandidate(
        val chapter: String, val exercise: String, val number: String,
        val page: Int, val lines: MutableList<String>
    )
}
