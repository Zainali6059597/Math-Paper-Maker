package com.nitroza.mathpapermaker.data

/**
 * Splits one selectable-text PDF page into conservative numbered question candidates.
 * It deliberately does not OCR or guess at unnumbered/scanned content: those pages
 * continue through the manual text editor or crop workflow.
 */
object QuestionTextSegmenter {
    data class Segment(val sourceNumber: String, val text: String)

    private val marker = Regex("""^\s*(?:(?:Q(?:uestion)?\.?\s*))?(\d{1,2})\s*[.)](?:\s*(.*))?$""", RegexOption.IGNORE_CASE)

    fun segment(pageText: String): List<Segment> {
        val result = mutableListOf<Segment>()
        var currentNumber: String? = null
        val currentLines = mutableListOf<String>()
        fun flush() {
            val body = currentLines.joinToString(" ").replace(Regex("""\s+"""), " ").trim()
            if (!currentNumber.isNullOrBlank() && body.length >= 2) result += Segment(currentNumber!!, body)
            currentNumber = null
            currentLines.clear()
        }
        pageText.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (line.isBlank()) return@forEach
            val match = marker.matchEntire(line)
            if (match != null) {
                flush()
                currentNumber = match.groupValues[1]
                match.groupValues.getOrNull(2)?.trim()?.takeIf { it.isNotBlank() }?.let(currentLines::add)
            } else if (currentNumber != null && line.length <= 500) {
                currentLines += line
            }
        }
        flush()
        return result
    }
}
