package com.nitroza.mathpapermaker.pdf

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import java.io.IOException

/** Local A4 PDF renderer. It paginates both header material and question bodies. */
object PaperPdfExporter {
    data class Item(val number: Int, val text: String, val marks: Int, val questionId: String = "", val displayLabel: String? = null)
    data class Paper(
        val school: String,
        val title: String,
        val board: String,
        val grade: Int,
        val subject: String,
        val session: String,
        val instructions: String,
        val items: List<Item>,
        val totalMarks: Int,
        val choiceInstructions: List<String> = emptyList(),
        val isCustom: Boolean = false,
        val durationMinutes: Int? = null
    )

    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val LEFT = 48f
    private const val RIGHT = 547f
    private const val CONTENT_WIDTH = RIGHT - LEFT
    private const val TOP = 46f
    private const val BOTTOM = 785f
    private const val FOOTER_Y = 815f

    @Throws(IOException::class)
    fun write(context: Context, uri: Uri, paper: Paper) {
        require(paper.items.isNotEmpty()) { "There are no questions to export." }
        require(paper.items.all { it.marks > 0 }) { "Every question must have positive marks." }

        val document = PdfDocument()
        val body = paint(11f)
        val small = paint(9f, Color.DKGRAY)
        val heading = paint(17f, Color.BLACK, true)
        val subheading = paint(12f, Color.BLACK, true)
        val warning = paint(9f, Color.rgb(155, 35, 25), true)

        var pageNumber = 0
        var page: PdfDocument.Page? = null
        var canvas: Canvas? = null
        var y = TOP

        fun finishPage() {
            val activeCanvas = canvas ?: return
            activeCanvas.drawLine(LEFT, 800f, RIGHT, 800f, small)
            activeCanvas.drawText("Maths Paper Maker", LEFT, FOOTER_Y, small)
            val pageLabel = "Page $pageNumber"
            activeCanvas.drawText(pageLabel, RIGHT - small.measureText(pageLabel), FOOTER_Y, small)
            page?.let(document::finishPage)
            page = null
            canvas = null
        }

        lateinit var startPage: (Boolean) -> Unit
        lateinit var nextPage: () -> Unit
        lateinit var ensureRoom: (Float) -> Unit
        lateinit var drawLine: (String, Float, Paint, Float) -> Unit

        drawLine = { text, x, p, maxWidth ->
            PdfTextLayout.wrap(text, maxWidth, p::measureText).forEach { line ->
                ensureRoom(16f)
                canvas!!.drawText(line, x, y, p)
                y += 15f
            }
        }
        startPage = { continued ->
            pageNumber++
            page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
            canvas = page!!.canvas
            y = TOP
            if (continued) {
                drawLine("${paper.title.ifBlank { "Mathematics Examination" }} — continued", LEFT, subheading, CONTENT_WIDTH)
                y += 7f
            }
        }
        nextPage = {
            finishPage()
            startPage(true)
        }
        ensureRoom = { height -> if (y + height > BOTTOM) nextPage() }

        try {
            startPage(false)

            if (paper.school.isNotBlank()) {
                drawLine(paper.school.trim(), LEFT, heading, CONTENT_WIDTH)
                y += 3f
            }
            drawLine(paper.title.ifBlank { "Mathematics Examination" }, LEFT, heading, CONTENT_WIDTH)
            y += 2f
            val metadata = "${paper.board}  •  Grade ${paper.grade}  •  ${paper.subject}  •  ${paper.session}"
            drawLine(metadata, LEFT, small, CONTENT_WIDTH)
            ensureRoom(28f)
            canvas!!.drawLine(LEFT, y + 2f, RIGHT, y + 2f, body)
            y += 23f

            ensureRoom(20f)
            val timeLabel = paper.durationMinutes?.takeIf { it > 0 }?.let { "Time: $it minutes" } ?: "Time: ____________"
            canvas!!.drawText(timeLabel, LEFT, y, body)
            val marksLabel = "Maximum marks: ${paper.totalMarks}"
            canvas!!.drawText(marksLabel, RIGHT - body.measureText(marksLabel), y, body)
            y += 23f

            if (paper.isCustom) {
                drawLine("CUSTOM / NON-STANDARD PAPER — VERIFY PATTERN AND MARKS", LEFT, warning, CONTENT_WIDTH)
                y += 3f
            }

            ensureRoom(22f)
            canvas!!.drawText("Name: __________________________________", LEFT, y, body)
            canvas!!.drawText("Roll No: __________________", 355f, y, body)
            y += 24f

            if (paper.instructions.isNotBlank()) {
                drawLine("Instructions", LEFT, subheading, CONTENT_WIDTH)
                drawParagraph(paper.instructions, LEFT, body, CONTENT_WIDTH) { text, x, p, width -> drawLine(text, x, p, width) }
                y += 6f
            }
            if (paper.choiceInstructions.isNotEmpty()) {
                drawLine("Section choices", LEFT, subheading, CONTENT_WIDTH)
                paper.choiceInstructions.forEachIndexed { index, instruction ->
                    drawParagraph("${index + 1}. $instruction", LEFT, body, CONTENT_WIDTH) { text, x, p, width -> drawLine(text, x, p, width) }
                }
                y += 6f
            }

            paper.items.forEach { item ->
                val prefix = "${item.displayLabel ?: item.number.toString()}.  "
                val markLabel = "[${item.marks}]"
                val markWidth = subheading.measureText(markLabel) + 4f
                val firstWidth = CONTENT_WIDTH - markWidth - body.measureText(prefix)
                val continuationWidth = CONTENT_WIDTH - markWidth - 18f
                val paragraphs = item.text.split('\n')
                val lines = mutableListOf<String>()
                paragraphs.forEachIndexed { paragraphIndex, paragraph ->
                    val available = if (lines.isEmpty()) firstWidth else continuationWidth
                    val wrapped = PdfTextLayout.wrap(paragraph, available, body::measureText)
                    if (paragraphIndex > 0 && lines.isNotEmpty()) lines += ""
                    lines += wrapped
                }
                if (lines.isEmpty()) lines += ""

                lines.forEachIndexed { lineIndex, line ->
                    ensureRoom(17f)
                    if (lineIndex == 0) {
                        canvas!!.drawText(prefix, LEFT, y, body)
                        canvas!!.drawText(line, LEFT + body.measureText(prefix), y, body)
                        canvas!!.drawText(markLabel, RIGHT - markWidth, y, subheading)
                    } else {
                        canvas!!.drawText(line, LEFT + 18f, y, body)
                    }
                    y += 15f
                }
                y += 8f
            }

            finishPage()
            val output = context.contentResolver.openOutputStream(uri, "w")
                ?: throw IOException("Unable to open the selected PDF destination.")
            output.use { document.writeTo(it) }
        } finally {
            page?.let(document::finishPage)
            document.close()
        }
    }

    private fun drawParagraph(
        text: String,
        x: Float,
        paint: Paint,
        width: Float,
        draw: (String, Float, Paint, Float) -> Unit
    ) {
        text.split('\n').forEach { paragraph -> draw(paragraph, x, paint, width) }
    }


    private fun paint(size: Float, color: Int = Color.BLACK, bold: Boolean = false) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            if (bold) typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
}
