package com.nitroza.mathpapermaker.pdf

/** Pure text wrapping helper so width/overflow edge cases can be tested without Android. */
internal object PdfTextLayout {
    fun wrap(text: String, maxWidth: Float, measureText: (String) -> Float): List<String> {
        if (maxWidth <= 1f) return listOf(text)
        val result = mutableListOf<String>()
        var line = ""

        fun appendToken(token: String) {
            var remaining = token
            while (remaining.isNotEmpty()) {
                val candidate = if (line.isEmpty()) remaining else "$line $remaining"
                if (measureText(candidate) <= maxWidth) {
                    line = candidate
                    remaining = ""
                } else if (line.isNotEmpty()) {
                    result += line
                    line = ""
                } else {
                    var cut = remaining.length
                    while (cut > 1 && measureText(remaining.substring(0, cut)) > maxWidth) cut--
                    result += remaining.substring(0, cut)
                    remaining = remaining.substring(cut)
                }
            }
        }

        text.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.forEach(::appendToken)
        if (line.isNotEmpty()) result += line
        return result.ifEmpty { listOf("") }
    }
}
