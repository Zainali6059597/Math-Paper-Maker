package com.nitroza.mathpapermaker.docx

import android.content.Context
import android.net.Uri
import com.nitroza.mathpapermaker.paper.PaperQuestion
import com.nitroza.mathpapermaker.paper.PunjabBoardPattern
import com.nitroza.mathpapermaker.paper.PaperSection
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.util.zip.ZipFile
import kotlin.math.min

/**
 * Creates a compact OOXML Word file. Extracted PDF text remains editable;
 * scanned question crops are embedded as PNGs.
 */
object DocxPaperExporter {
    private const val MAX_HEADER_CHARS = 500
    private const val MAX_QUESTION_TEXT_CHARS = 100_000
    private const val MAX_TOTAL_TEXT_CHARS = 2_000_000L
    private const val MAX_IMAGE_BYTES = 8L * 1024L * 1024L
    private const val MAX_TOTAL_IMAGE_BYTES = 24L * 1024L * 1024L

    fun write(
        context: Context,
        destination: Uri,
        grade: Int,
        examTitle: String,
        schoolName: String,
        durationMinutes: Int?,
        questions: List<PaperQuestion>,
        paperMode: String = "BOARD"
    ) {
        require(grade == 9 || grade == 10) { "Choose Class 9 or Class 10." }
        require(paperMode == "CUSTOM" || paperMode == "BOARD") { "Choose Board Pattern or Custom paper mode." }
        if (paperMode == "BOARD") {
            require(PunjabBoardPattern.isComplete(grade, questions)) {
                "The Punjab Board pattern needs 15 one-mark MCQs, 27 two-mark short questions and 10 four-mark long-question parts, with unique numbering."
            }
        } else {
            require(questions.isNotEmpty()) { "Add at least one question before exporting." }
            require(questions.all { it.number.isNotBlank() && it.marks in 1..100 && it.kind.uppercase() in setOf("MCQ", "SHORT", "LONG") }) {
                "Every custom question needs a number, a question type and marks between 1 and 100."
            }
            require(questions.map { it.number.trim().lowercase() }.distinct().size == questions.size) {
                "Custom question numbers must be unique."
            }
        }
        require(examTitle.length <= MAX_HEADER_CHARS && schoolName.length <= MAX_HEADER_CHARS) {
            "Paper title or school name is too long."
        }
        require(durationMinutes == null || durationMinutes in 1..600) {
            "Exam duration must be between 1 and 600 minutes."
        }
        require(questions.all { it.text.length <= MAX_QUESTION_TEXT_CHARS }) {
            "A selected question contains too much text. Shorten it before exporting."
        }
        require(questions.sumOf { it.text.length.toLong() } <= MAX_TOTAL_TEXT_CHARS) {
            "The selected question text is too large for one paper."
        }
        questions.filter { it.imageFile != null && it.text.isBlank() }.forEach {
            require(File(it.imageFile!!).isFile) { "A selected scanned-question image is missing. Re-select that question." }
        }

        val imageEntries = questions.mapIndexedNotNull { index, q ->
            if (q.text.isNotBlank()) null else q.imageFile?.let { index to File(it) }
        }
        require(imageEntries.all { (_, file) -> file.isFile && file.length() in 24L..MAX_IMAGE_BYTES }) {
            "A scanned question image is missing or exceeds the per-image size limit."
        }
        require(imageEntries.sumOf { (_, file) -> file.length() } <= MAX_TOTAL_IMAGE_BYTES) {
            "Scanned question images are too large for one Word paper. Use smaller crops."
        }
        val imageRelIds = imageEntries.associate { (index, _) -> index to "rIdImage${index + 1}" }
        val sections = if (paperMode == "BOARD") PunjabBoardPattern.sections(questions) else questions.groupBy { it.sectionId.ifBlank { "QUESTIONS" } }.map { (section, items) -> PaperSection(section, "Answer the following questions.", items) }
        val consumedQuestionIndices = mutableSetOf<Int>()
        val document = buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            append("""<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture" xmlns:m="http://schemas.openxmlformats.org/officeDocument/2006/math"><w:body>""")
            paragraph(escape(schoolName.ifBlank { "ALI TEST SERIES" }), bold = true, centered = true, size = 30)
            paragraph("CLASS $grade · MATHEMATICS", bold = true, centered = true, size = 26)
            paragraph(escape(examTitle.ifBlank { "Mathematics Examination" }), bold = true, centered = true, size = 24)
            paragraph("Student Name: __________________________________    Roll No: ______________", size = 20)
            val paperTotalMarks = if (paperMode == "BOARD") PunjabBoardPattern.TOTAL_ATTEMPTED_MARKS else questions.sumOf { it.marks }
            paragraph("Time: ${durationMinutes?.let { "$it minutes" } ?: "150 minutes"}     Total Marks: $paperTotalMarks", size = 20)
            paragraph("Instructions: Follow the attempt choices printed with each section. Show necessary working.", size = 19)
            paragraph("", size = 16)
            sections.forEach { section ->
                paragraph(escape(section.heading), bold = true, size = 23)
                paragraph(escape(section.instruction), size = 19)
                section.questions.forEach { q ->
                    val index = questions.indices.firstOrNull { candidate ->
                        candidate !in consumedQuestionIndices && questions[candidate] == q
                    } ?: error("A selected question could not be matched to its export entry.")
                    consumedQuestionIndices += index
                    val questionText = q.text.ifBlank { "[Scanned question]" }
                    questionParagraph(q.number, questionText, q.marks)
                    val relId = imageRelIds[index]
                    if (relId != null) {
                        val imageFile = imageEntries.first { it.first == index }.second
                        val dimensions = imageDimensions(imageFile)
                        // Word drawing extents use EMUs, not raw pixels (96 DPI = 9,525 EMUs/px).
                        val rawWidthEmu = dimensions.first.toLong() * 9_525L
                        val rawHeightEmu = dimensions.second.toLong() * 9_525L
                        val maxWidth = 5_800_000L
                        val maxHeight = 6_800_000L
                        val scale = min(
                            maxWidth.toDouble() / rawWidthEmu,
                            maxHeight.toDouble() / rawHeightEmu
                        ).coerceAtMost(1.0)
                        val cx = (rawWidthEmu * scale).toLong().coerceAtLeast(1L)
                        val cy = (rawHeightEmu * scale).toLong().coerceAtLeast(1L)
                        append(imageParagraph(relId, index + 1, cx, cy))
                    }
                    paragraph(" ", size = 12)
                }
            }
            append("""<w:sectPr><w:pgSz w:w="11906" w:h="16838"/><w:pgMar w:top="1080" w:right="1080" w:bottom="1080" w:left="1080" w:header="720" w:footer="720" w:gutter="0"/></w:sectPr></w:body></w:document>""")
        }

        val relationships = buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
            append("""<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>""")
            imageEntries.forEach { (index, _) ->
                append("""<Relationship Id="rIdImage${index + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/image${index + 1}.png"/>""")
            }
            append("</Relationships>")
        }
        val contentTypes = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Default Extension="png" ContentType="image/png"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/><Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/></Types>"""
        val rootRels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>"""
        val styles = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:style w:type="paragraph" w:default="1" w:styleId="Normal"><w:name w:val="Normal"/><w:rPr><w:rFonts w:ascii="Arial" w:hAnsi="Arial"/><w:sz w:val="22"/></w:rPr><w:pPr><w:spacing w:after="100"/></w:pPr></w:style></w:styles>"""

        // Finish the complete ZIP package in app cache before touching the user's
        // destination. If packaging fails (e.g. a damaged crop), no half-built DOCX
        // is left at the selected document URI.
        val stagedDocx = File.createTempFile("ali-test-series-", ".docx", context.cacheDir)
        try {
            FileOutputStream(stagedDocx).use { staged ->
                ZipOutputStream(staged).use { zip ->
                    zip.putText("[Content_Types].xml", contentTypes)
                    zip.putText("_rels/.rels", rootRels)
                    zip.putText("word/document.xml", document)
                    zip.putText("word/_rels/document.xml.rels", relationships)
                    zip.putText("word/styles.xml", styles)
                    imageEntries.forEach { (index, file) ->
                        zip.putNextEntry(ZipEntry("word/media/image${index + 1}.png"))
                        file.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                }
            }
            // Re-open the finished package and read every entry before export.
            // ZipFile verifies the central directory and entry CRCs during reads.
            ZipFile(stagedDocx).use { archive ->
                val requiredEntries = listOf(
                    "[Content_Types].xml",
                    "_rels/.rels",
                    "word/document.xml",
                    "word/_rels/document.xml.rels",
                    "word/styles.xml"
                )
                requiredEntries.forEach { name ->
                    require(archive.getEntry(name) != null) { "The generated Word file is incomplete." }
                }
                val buffer = ByteArray(16 * 1024)
                val entries = archive.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    archive.getInputStream(entry).use { input ->
                        while (input.read(buffer) != -1) Unit
                    }
                }
            }
            context.contentResolver.openOutputStream(destination, "w")?.use { output ->
                stagedDocx.inputStream().use { it.copyTo(output) }
                output.flush()
            } ?: error("Cannot open the selected Word document destination.")
        } finally {
            stagedDocx.delete()
        }
    }

    private fun ZipOutputStream.putText(path: String, text: String) {
        putNextEntry(ZipEntry(path))
        write(text.toByteArray(Charsets.UTF_8))
        closeEntry()
    }


    private fun questionParagraph(number: String, text: String, marks: Int): String {
        val prefix = MathOoxmlFormatter.inlineRuns("$number. ")
        val body = MathOoxmlFormatter.inlineRuns(text)
        val suffix = MathOoxmlFormatter.inlineRuns(" [$marks marks]")
        return """<w:p><w:pPr><w:spacing w:after="100"/></w:pPr>$prefix$body$suffix</w:p>"""
    }

    private fun paragraph(text: String, bold: Boolean = false, centered: Boolean = false, size: Int = 22): String {
        val alignment = if (centered) """<w:jc w:val="center"/>""" else ""
        val boldTag = if (bold) "<w:b/>" else ""
        return """<w:p><w:pPr>$alignment</w:pPr><w:r><w:rPr>$boldTag<w:sz w:val="$size"/></w:rPr><w:t xml:space="preserve">$text</w:t></w:r></w:p>"""
    }

    private fun imageParagraph(relId: String, id: Int, cx: Long, cy: Long): String =
        """<w:p><w:r><w:drawing><wp:inline distT="0" distB="0" distL="0" distR="0"><wp:extent cx="$cx" cy="$cy"/><wp:docPr id="$id" name="Question image $id"/><wp:cNvGraphicFramePr><a:graphicFrameLocks noChangeAspect="1"/></wp:cNvGraphicFramePr><a:graphic><a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture"><pic:pic><pic:nvPicPr><pic:cNvPr id="$id" name="Question image $id"/><pic:cNvPicPr/></pic:nvPicPr><pic:blipFill><a:blip r:embed="$relId"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill><pic:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="$cx" cy="$cy"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom></pic:spPr></pic:pic></a:graphicData></a:graphic></wp:inline></w:drawing></w:r></w:p>"""

    private fun escape(value: String): String {
        // XML 1.0 rejects most control characters, noncharacters U+FFFE/U+FFFF,
        // and unpaired UTF-16 surrogates. Strip those before writing document.xml.
        val xmlSafe = buildString(value.length) {
            var index = 0
            while (index < value.length) {
                val codePoint = value.codePointAt(index)
                val allowed = codePoint == 0x09 || codePoint == 0x0A || codePoint == 0x0D ||
                    codePoint in 0x20..0xD7FF ||
                    codePoint in 0xE000..0xFFFD ||
                    codePoint in 0x10000..0x10FFFF
                if (allowed) append(String(Character.toChars(codePoint)))
                index += Character.charCount(codePoint)
            }
        }
        return xmlSafe
            .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("\"", "&quot;").replace("'", "&apos;")
            .replace("\r", "")
            .replace("\n", "</w:t></w:r></w:p><w:p><w:r><w:t xml:space=\"preserve\">")
    }

    private fun imageDimensions(file: File): Pair<Int, Int> {
        require(file.isFile && file.length() >= 24L) { "Selected question image is missing or incomplete." }
        val pngSignature = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
        )
        val signature = file.inputStream().use { input ->
            ByteArray(8).also { bytes ->
                var offset = 0
                while (offset < bytes.size) {
                    val read = input.read(bytes, offset, bytes.size - offset)
                    require(read > 0) { "Selected question image is truncated." }
                    offset += read
                }
            }
        }
        require(signature.contentEquals(pngSignature)) { "Selected question image is not a valid PNG." }

        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeFile(file.absolutePath, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Selected question image is invalid." }
        require(bounds.outWidth.toLong() * bounds.outHeight.toLong() <= 4_000_000L) {
            "Selected question image is too large. Re-select a smaller crop."
        }

        // Decode at a bounded sample size to verify the PNG pixel stream without
        // allocating a full-resolution bitmap on low-memory Android devices.
        val options = android.graphics.BitmapFactory.Options().apply {
            var sample = 1
            while ((bounds.outWidth.toLong() / sample) * (bounds.outHeight.toLong() / sample) > 1_000_000L) {
                sample *= 2
            }
            inSampleSize = sample
        }
        val decoded = android.graphics.BitmapFactory.decodeFile(file.absolutePath, options)
            ?: error("Selected question image data is damaged.")
        decoded.recycle()
        return bounds.outWidth to bounds.outHeight
    }
}
