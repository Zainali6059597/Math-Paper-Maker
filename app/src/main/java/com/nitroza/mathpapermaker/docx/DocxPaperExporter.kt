package com.nitroza.mathpapermaker.docx

import android.content.Context
import android.net.Uri
import com.nitroza.mathpapermaker.paper.PaperQuestion
import com.nitroza.mathpapermaker.paper.PunjabBoardPattern
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.min

/**
 * Creates a compact OOXML Word file. Extracted PDF text remains editable;
 * scanned question crops are embedded as PNGs.
 */
object DocxPaperExporter {
    fun write(
        context: Context,
        destination: Uri,
        grade: Int,
        examTitle: String,
        schoolName: String,
        durationMinutes: Int?,
        questions: List<PaperQuestion>
    ) {
        require(grade == 9 || grade == 10) { "Choose Class 9 or Class 10." }
        require(PunjabBoardPattern.isComplete(questions)) {
            "The Punjab Board pattern needs 15 one-mark MCQs, 27 two-mark short questions and 10 four-mark long-question parts, with unique numbering."
        }
        questions.filter { it.imageFile != null }.forEach {
            require(File(it.imageFile!!).isFile) { "A selected scanned-question image is missing. Re-select that question." }
        }

        val imageEntries = questions.mapIndexedNotNull { index, q ->
            q.imageFile?.let { index to File(it) }
        }
        val imageRelIds = imageEntries.associate { (index, _) -> index to "rIdImage${index + 1}" }
        val sections = PunjabBoardPattern.sections(questions)
        val document = buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            append("""<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture"><w:body>""")
            paragraph(escape(schoolName.ifBlank { "ALI TEST SERIES" }), bold = true, centered = true, size = 30)
            paragraph("CLASS $grade · MATHEMATICS", bold = true, centered = true, size = 26)
            paragraph(escape(examTitle.ifBlank { "Mathematics Examination" }), bold = true, centered = true, size = 24)
            paragraph("Student Name: __________________________________    Roll No: ______________", size = 20)
            paragraph("Time: ${durationMinutes?.let { "$it minutes" } ?: "150 minutes"}     Total Marks: ${PunjabBoardPattern.TOTAL_ATTEMPTED_MARKS}", size = 20)
            paragraph("Instructions: Follow the attempt choices printed with each section. Show necessary working.", size = 19)
            paragraph("", size = 16)
            sections.forEach { section ->
                paragraph(escape(section.heading), bold = true, size = 23)
                paragraph(escape(section.instruction), size = 19)
                section.questions.forEach { q ->
                    val index = questions.indexOfFirst { it.number == q.number && it.kind == q.kind && it.sourcePage == q.sourcePage && it.text == q.text }
                    val questionText = q.text.ifBlank { "[Scanned question]" }
                    paragraph(escape("${q.number}. ${questionText} [${q.marks} marks]"), size = 21)
                    val relId = imageRelIds[index]
                    if (relId != null) {
                        val imageFile = imageEntries.first { it.first == index }.second
                        val dimensions = imageDimensions(imageFile)
                        val maxWidth = 5_800_000L
                        val maxHeight = 6_800_000L
                        val scale = min(maxWidth.toDouble() / dimensions.first, maxHeight.toDouble() / dimensions.second).coerceAtMost(1.0)
                        val cx = (dimensions.first * scale).toLong().coerceAtLeast(1L)
                        val cy = (dimensions.second * scale).toLong().coerceAtLeast(1L)
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

        context.contentResolver.openOutputStream(destination, "w")?.use { raw ->
            ZipOutputStream(raw).use { zip ->
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
        } ?: error("Cannot open the selected Word document destination.")
    }

    private fun ZipOutputStream.putText(path: String, text: String) {
        putNextEntry(ZipEntry(path))
        write(text.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private fun paragraph(text: String, bold: Boolean = false, centered: Boolean = false, size: Int = 22): String {
        val alignment = if (centered) """<w:jc w:val="center"/>""" else ""
        val boldTag = if (bold) "<w:b/>" else ""
        return """<w:p><w:pPr>$alignment</w:pPr><w:r><w:rPr>$boldTag<w:sz w:val="$size"/></w:rPr><w:t xml:space="preserve">$text</w:t></w:r></w:p>"""
    }

    private fun imageParagraph(relId: String, id: Int, cx: Long, cy: Long): String =
        """<w:p><w:r><w:drawing><wp:inline distT="0" distB="0" distL="0" distR="0"><wp:extent cx="$cx" cy="$cy"/><wp:docPr id="$id" name="Question image $id"/><wp:cNvGraphicFramePr><a:graphicFrameLocks noChangeAspect="1"/></wp:cNvGraphicFramePr><a:graphic><a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture"><pic:pic><pic:nvPicPr><pic:cNvPr id="$id" name="Question image $id"/><pic:cNvPicPr/></pic:nvPicPr><pic:blipFill><a:blip r:embed="$relId"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill><pic:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="$cx" cy="$cy"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom></pic:spPr></pic:pic></a:graphicData></a:graphic></wp:inline></w:drawing></w:r></w:p>"""

    private fun escape(value: String): String = value
        .filter { it == '\n' || it == '\t' || it >= ' ' }
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&apos;")
        .replace("\r", "")
        .replace("\n", "</w:t></w:r></w:p><w:p><w:r><w:t xml:space=\"preserve\">")

    private fun imageDimensions(file: File): Pair<Int, Int> {
        val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeFile(file.absolutePath, options)
        require(options.outWidth > 0 && options.outHeight > 0) { "Selected question image is invalid." }
        return options.outWidth to options.outHeight
    }
}
