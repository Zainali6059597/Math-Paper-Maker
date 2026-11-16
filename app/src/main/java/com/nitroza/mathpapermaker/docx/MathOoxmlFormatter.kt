package com.nitroza.mathpapermaker.docx

/**
 * Converts the simple editable templates offered by the in-app maths keyboard
 * into native Office Math Markup Language (OMML) fragments.
 *
 * Supported templates: (numerator)/(denominator), √(expression),
 * variable^(superscript), and variable_(subscript). Unrecognized text is kept
 * as ordinary editable Word text; it is never dropped or rasterized.
 */
object MathOoxmlFormatter {
    fun inlineRuns(source: String): String {
        val output = StringBuilder()
        val plain = StringBuilder()

        fun flushPlain() {
            if (plain.isNotEmpty()) {
                output.append(wordRun(plain.toString()))
                plain.clear()
            }
        }

        var index = 0
        while (index < source.length) {
            val parsed = parseConstruct(source, index)
            if (parsed != null) {
                flushPlain()
                output.append("<m:oMath>").append(parsed.xml).append("</m:oMath>")
                index = parsed.endExclusive
            } else {
                plain.append(source[index])
                index++
            }
        }
        flushPlain()
        return output.toString()
    }

    private data class Parsed(val endExclusive: Int, val xml: String)

    private fun parseConstruct(source: String, start: Int): Parsed? {
        if (source.startsWith("√(", start)) {
            val end = matchingParen(source, start + 1) ?: return null
            val inner = source.substring(start + 2, end)
            return Parsed(end + 1, "<m:rad><m:radPr><m:degHide m:val=\"1\"/></m:radPr><m:deg/><m:e>${mathContent(inner)}</m:e></m:rad>")
        }

        if (source[start] == '(') {
            val numeratorEnd = matchingParen(source, start) ?: return null
            if (!source.startsWith("/(", numeratorEnd + 1)) return null
            val denominatorStart = numeratorEnd + 2
            val denominatorEnd = matchingParen(source, denominatorStart - 1) ?: return null
            val numerator = source.substring(start + 1, numeratorEnd)
            val denominator = source.substring(denominatorStart, denominatorEnd)
            return Parsed(
                denominatorEnd + 1,
                "<m:f><m:fPr><m:type m:val=\"bar\"/></m:fPr><m:num>${mathContent(numerator)}</m:num><m:den>${mathContent(denominator)}</m:den></m:f>"
            )
        }

        if (source[start].isLetterOrDigit()) {
            var baseEnd = start + 1
            while (baseEnd < source.length && source[baseEnd].isLetterOrDigit()) baseEnd++
            if (baseEnd + 1 < source.length &&
                (source.startsWith("^(", baseEnd) || source.startsWith("_(", baseEnd))
            ) {
                val marker = source[baseEnd]
                val argumentStart = baseEnd + 1
                val argumentEnd = matchingParen(source, argumentStart) ?: return null
                val base = source.substring(start, baseEnd)
                val argument = source.substring(argumentStart + 1, argumentEnd)
                val tag = if (marker == '^') "sSup" else "sSub"
                val slot = if (marker == '^') "sup" else "sub"
                return Parsed(
                    argumentEnd + 1,
                    "<m:$tag><m:e>${mathContent(base)}</m:e><m:$slot>${mathContent(argument)}</m:$slot></m:$tag>"
                )
            }
        }
        return null
    }

    private fun matchingParen(source: String, openIndex: Int): Int? {
        if (openIndex !in source.indices || source[openIndex] != '(') return null
        var depth = 0
        var index = openIndex
        while (index < source.length) {
            when (source[index]) {
                '(' -> depth++
                ')' -> {
                    depth--
                    if (depth == 0) return index
                }
            }
            index++
        }
        return null
    }

    private fun mathContent(value: String): String {
        val runs = inlineRuns(value)
        return runs.replace("<m:oMath>", "").replace("</m:oMath>", "")
    }

    private fun wordRun(value: String): String =
        "<w:r><w:t xml:space=\"preserve\">${xmlEscape(value)}</w:t></w:r>"

    private fun xmlEscape(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}
