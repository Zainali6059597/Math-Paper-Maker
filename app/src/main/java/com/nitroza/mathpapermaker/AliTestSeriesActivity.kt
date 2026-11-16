package com.nitroza.mathpapermaker

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.drawWithContent
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.nitroza.mathpapermaker.docx.DocxPaperExporter
import com.nitroza.mathpapermaker.paper.PaperQuestion
import com.nitroza.mathpapermaker.paper.PunjabBoardPattern
import com.nitroza.mathpapermaker.ui.MathPaperMakerTheme
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min

class AliTestSeriesActivity : ComponentActivity() {
    private data class SelectedQuestion(
        val id: Long,
        val number: String,
        val text: String,
        val kind: String,
        val marks: Int,
        val sourcePage: Int,
        val imageFile: String? = null,
        val chapterId: String = ""
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MathPaperMakerTheme { AliTestSeriesApp() } }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun AliTestSeriesApp() {
        var grade by remember { mutableIntStateOf(9) }
        var pdfFile by remember { mutableStateOf<File?>(null) }
        var pdfName by remember { mutableStateOf("") }
        var pageIndex by remember { mutableIntStateOf(0) }
        var pageCount by remember { mutableIntStateOf(0) }
        var pageBitmap by remember { mutableStateOf<Bitmap?>(null) }
        var selected by remember { mutableStateOf<List<SelectedQuestion>>(emptyList()) }
        var nextId by remember { mutableLongStateOf(1L) }
        var notice by remember { mutableStateOf("Choose Class 9 or Class 10, then open its Mathematics PDF.") }
        var showQuestionEditor by remember { mutableStateOf(false) }
        var questionText by remember { mutableStateOf("") }
        var questionKind by remember { mutableStateOf("SHORT") }
        var questionMarks by remember { mutableStateOf("2") }
        var questionNumber by remember { mutableStateOf("") }
        var questionChapter by remember { mutableStateOf("CH1") }
        var pendingImageFile by remember { mutableStateOf<String?>(null) }
        var cropRect by remember { mutableStateOf<Rect?>(null) }
        var showQuestions by remember { mutableStateOf(false) }
        var examTitle by remember { mutableStateOf("Mathematics Examination") }
        var schoolName by remember { mutableStateOf("") }

        fun suggestedNumber(kind: String, current: List<SelectedQuestion>): String {
            return when (kind) {
                "MCQ" -> "Q1(${roman(current.count { it.kind == "MCQ" } + 1)})"
                "SHORT" -> {
                    val index = current.count { it.kind == "SHORT" }
                    "Q${2 + index / 9}(${roman(index % 9 + 1)})"
                }
                else -> {
                    val index = current.count { it.kind == "LONG" }
                    "Q${5 + index / 2}(${if (index % 2 == 0) "a" else "b"})"
                }
            }
        }

        val openPdf = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri != null) {
                lifecycleScope.launch {
                    runCatching {
                        runCatching {
                            contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        val file = withContext(Dispatchers.IO) {
                            val target = File(cacheDir, "ali-test-series-class$grade.pdf")
                            contentResolver.openInputStream(uri)?.use { input ->
                                FileOutputStream(target).use { output -> input.copyTo(output) }
                            } ?: error("PDF file could not be opened.")
                            target
                        }
                        val count = withContext(Dispatchers.IO) {
                            val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                            PdfRenderer(descriptor).use { it.pageCount }
                        }
                        pdfFile = file
                        pdfName = uri.lastPathSegment?.substringAfterLast('/') ?: "Mathematics.pdf"
                        pageCount = count
                        pageIndex = 0
                        cropRect = null
                        selected = emptyList()
                        notice = "PDF opened. Select question text or draw a box around a scanned question."
                    }.onFailure { notice = "Could not open PDF: ${it.message}" }
                }
            }
        }

        val exportWord = rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
        ) { uri: Uri? ->
            if (uri != null) {
                runCatching {
                    DocxPaperExporter.write(
                        this@AliTestSeriesActivity, uri, grade, examTitle, schoolName,
                        PunjabBoardPattern.DURATION_MINUTES,
                        selected.map { item ->
                            PaperQuestion(item.number, item.text, item.kind, item.marks, item.sourcePage, item.imageFile, item.chapterId)
                        }
                    )
                }.onSuccess { notice = "Word paper exported. Open the DOCX in Word or another office app to review and print." }
                    .onFailure { notice = "Word export failed: ${it.message}" }
            }
        }

        LaunchedEffect(pdfFile, pageIndex) {
            val file = pdfFile ?: return@LaunchedEffect
            cropRect = null
            pageBitmap = withContext(Dispatchers.IO) {
                runCatching {
                    val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                    PdfRenderer(descriptor).use { renderer ->
                        require(pageIndex in 0 until renderer.pageCount)
                        renderer.openPage(pageIndex).use { page ->
                            val width = 1400
                            val height = (width.toFloat() * page.height / page.width).toInt().coerceAtLeast(1)
                            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
                                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            }
                        }
                    }
                }.getOrNull()
            }
        }

        val paperQuestions = selected.map {
            PaperQuestion(it.number, it.text, it.kind, it.marks, it.sourcePage, it.imageFile, it.chapterId)
        }

        Scaffold(
            topBar = {
                TopAppBar(title = {
                    Column {
                        Text("ALI TEST SERIES", style = MaterialTheme.typography.titleMedium)
                        Text("PUNJAB BOARD · MATHEMATICS", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary)
                    }
                })
            }
        ) { padding ->
            if (pdfFile == null) {
                Column(
                    Modifier.fillMaxSize().padding(padding).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    Text("Prepare a paper. Choose every question yourself.", style = MaterialTheme.typography.headlineSmall)
                    Text("SELECT CLASS", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FilterChip(selected = grade == 9, onClick = { grade = 9 }, label = { Text("CLASS 9") })
                        FilterChip(selected = grade == 10, onClick = { grade = 10 }, label = { Text("CLASS 10") })
                    }
                    Card {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Mathematics textbook PDF", style = MaterialTheme.typography.titleMedium)
                            Text("Open a PDF stored on this device. The supplied Class 9 and Class 10 books can later be bundled offline.", style = MaterialTheme.typography.bodyMedium)
                            Button(onClick = { openPdf.launch(arrayOf("application/pdf")) }, modifier = Modifier.fillMaxWidth()) {
                                Text("OPEN CLASS $grade PDF")
                            }
                        }
                    }
                    Text(notice, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.weight(1f))
                    Text("Manual selection only · No AI · No automatic paper generation", style = MaterialTheme.typography.labelSmall)
                }
            } else {
                Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            Text("CLASS $grade MATHEMATICS", style = MaterialTheme.typography.titleSmall)
                            Text(pdfName, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        }
                        TextButton(onClick = { pdfFile = null; pageBitmap = null }) { Text("CHANGE PDF") }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        TextButton(enabled = pageIndex > 0, onClick = { pageIndex-- }) { Text("PREVIOUS") }
                        Text("PAGE ${pageIndex + 1} / $pageCount", style = MaterialTheme.typography.labelLarge)
                        TextButton(enabled = pageIndex < pageCount - 1, onClick = { pageIndex++ }) { Text("NEXT") }
                    }
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        pageBitmap?.let { bitmap ->
                            Box(
                                Modifier.fillMaxWidth()
                                    .aspectRatio(bitmap.width.toFloat() / bitmap.height.toFloat())
                                    .pointerInput(bitmap) {
                                        var start: Offset? = null
                                        detectDragGestures(
                                            onDragStart = { start = it },
                                            onDragEnd = { start = null },
                                            onDragCancel = { start = null }
                                        ) { change, _ ->
                                            val origin = start ?: change.position
                                            val left = min(origin.x, change.position.x) / size.width
                                            val top = min(origin.y, change.position.y) / size.height
                                            val right = max(origin.x, change.position.x) / size.width
                                            val bottom = max(origin.y, change.position.y) / size.height
                                            cropRect = Rect(left, top, right, bottom)
                                            change.consume()
                                        }
                                    }
                                    .drawWithContent {
                                        drawContent()
                                        cropRect?.let { r ->
                                            drawRect(
                                                Color.Red,
                                                topLeft = Offset(r.left * size.width, r.top * size.height),
                                                size = androidx.compose.ui.geometry.Size(r.width * size.width, r.height * size.height),
                                                style = Stroke(width = 3.dp.toPx())
                                            )
                                        }
                                    }
                            ) {
                                Image(bitmap.asImageBitmap(), contentDescription = "Textbook page ${pageIndex + 1}",
                                    modifier = Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
                            }
                        } ?: CircularProgressIndicator()
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = {
                            lifecycleScope.launch {
                                val file = pdfFile ?: return@launch
                                val text = withContext(Dispatchers.IO) {
                                    runCatching {
                                        PDFBoxResourceLoader.init(applicationContext)
                                        PDDocument.load(file).use { document ->
                                            PDFTextStripper().apply {
                                                startPage = pageIndex + 1
                                                endPage = pageIndex + 1
                                            }.getText(document)
                                        }
                                    }.getOrElse { "" }
                                }
                                questionText = text
                                pendingImageFile = null
                                questionNumber = suggestedNumber(questionKind, selected)
                                questionChapter = "CH1"
                                if (text.isBlank()) notice = "This page has no extractable text. Draw a box around the scanned question."
                                else showQuestionEditor = true
                            }
                        }, modifier = Modifier.weight(1f)) { Text("SELECT TEXT") }
                        Button(
                            enabled = cropRect != null && pageBitmap != null,
                            onClick = {
                                val bitmap = pageBitmap ?: return@Button
                                val rect = cropRect ?: return@Button
                                runCatching {
                                    val left = (rect.left * bitmap.width).toInt().coerceIn(0, bitmap.width - 1)
                                    val top = (rect.top * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
                                    val right = (rect.right * bitmap.width).toInt().coerceIn(left + 1, bitmap.width)
                                    val bottom = (rect.bottom * bitmap.height).toInt().coerceIn(top + 1, bitmap.height)
                                    val crop = Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
                                    val image = File(cacheDir, "ali_question_${nextId}.png")
                                    FileOutputStream(image).use { output -> crop.compress(Bitmap.CompressFormat.PNG, 100, output) }
                                    pendingImageFile = image.absolutePath
                                    questionText = ""
                                    questionNumber = suggestedNumber(questionKind, selected)
                                    questionChapter = "CH1"
                                    showQuestionEditor = true
                                }.onFailure { notice = "Could not crop selection: ${it.message}" }
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("ADD BOX CROP") }
                    }
                    Button(onClick = { showQuestions = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("REVIEW SELECTED QUESTIONS (${selected.size})")
                    }
                    Text(notice, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(top = 4.dp))
                }
            }
        }

        if (showQuestionEditor) {
            AlertDialog(
                onDismissRequest = { showQuestionEditor = false },
                title = { Text(if (pendingImageFile == null) "SELECT QUESTION TEXT" else "ADD SCANNED QUESTION") },
                text = {
                    Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            if (pendingImageFile == null) "Trim the extracted page text so only your chosen question remains."
                            else "The selected page area will be embedded as an image in the Word document.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (pendingImageFile == null) {
                            OutlinedTextField(questionText, { questionText = it }, modifier = Modifier.fillMaxWidth(),
                                minLines = 8, label = { Text("Question text") })
                        } else {
                            Text("Scanned crop ready · PDF page ${pageIndex + 1}", color = MaterialTheme.colorScheme.primary)
                        }
                        OutlinedTextField(questionNumber, { questionNumber = it.take(16) },
                            label = { Text("Paper numbering (editable)") }, singleLine = true)
                        OutlinedTextField(questionChapter, { questionChapter = it.trim().uppercase().take(4) },
                            label = { Text("Chapter ID (e.g. CH1)") }, singleLine = true)
                        Text("Question type", style = MaterialTheme.typography.labelMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("MCQ", "SHORT", "LONG").forEach { kind ->
                                FilterChip(selected = questionKind == kind, onClick = {
                                    questionKind = kind
                                    questionMarks = when (kind) { "MCQ" -> "1"; "SHORT" -> "2"; else -> "4" }
                                    questionNumber = suggestedNumber(kind, selected)
                                }, label = { Text(kind) })
                            }
                        }
                        OutlinedTextField(questionMarks, { questionMarks = it.filter(Char::isDigit).take(2) },
                            label = { Text("Marks") }, singleLine = true)
                    }
                },
                confirmButton = {
                    TextButton(enabled = (questionText.isNotBlank() || pendingImageFile != null) &&
                        questionNumber.isNotBlank() && questionChapter.matches(Regex("CH([1-9]|1[0-3])")) && (questionMarks.toIntOrNull() ?: 0) > 0, onClick = {
                        selected = selected + SelectedQuestion(
                            nextId++, questionNumber.trim(), questionText.trim(), questionKind,
                            questionMarks.toInt(), pageIndex + 1, pendingImageFile, questionChapter
                        )
                        showQuestionEditor = false
                        cropRect = null
                        notice = "Question added to your manual selection."
                    }) { Text("ADD QUESTION") }
                },
                dismissButton = { TextButton(onClick = { showQuestionEditor = false }) { Text("CANCEL") } }
            )
        }

        if (showQuestions) {
            AlertDialog(
                onDismissRequest = { showQuestions = false },
                title = { Text("SELECTED QUESTIONS (${selected.size})") },
                text = {
                    Column(Modifier.heightIn(max = 560.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(schoolName, { schoolName = it }, modifier = Modifier.fillMaxWidth(),
                            label = { Text("School name (optional)") }, singleLine = true)
                        OutlinedTextField(examTitle, { examTitle = it }, modifier = Modifier.fillMaxWidth(),
                            label = { Text("Paper title") }, singleLine = true)
                        Text(PunjabBoardPattern.status(paperQuestions), style = MaterialTheme.typography.labelMedium)
                        val patternErrors = PunjabBoardPattern.validationErrors(grade, paperQuestions)
                        if (patternErrors.isNotEmpty()) Text(patternErrors.first(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        Text("Offered marks: ${selected.sumOf { it.marks }} / ${PunjabBoardPattern.TOTAL_OFFERED_MARKS} · Board attempted marks: ${PunjabBoardPattern.TOTAL_ATTEMPTED_MARKS}",
                            style = MaterialTheme.typography.labelSmall)
                        LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            itemsIndexed(selected, key = { _, item -> item.id }) { index, item ->
                                Card {
                                    Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            OutlinedTextField(
                                                value = item.number,
                                                onValueChange = { value ->
                                                    selected = selected.map { if (it.id == item.id) it.copy(number = value.take(16)) else it }
                                                },
                                                modifier = Modifier.width(132.dp),
                                                label = { Text("Number") },
                                                singleLine = true
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text("${item.kind} · ${item.marks} marks · page ${item.sourcePage}",
                                                style = MaterialTheme.typography.labelMedium)
                                        }
                                        Text("${item.chapterId} · " + if (item.imageFile != null) "Scanned question image" else item.text,
                                            maxLines = 4, style = MaterialTheme.typography.bodySmall)
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            TextButton(enabled = index > 0, onClick = {
                                                val copy = selected.toMutableList()
                                                val previous = copy[index - 1]
                                                copy[index - 1] = copy[index]
                                                copy[index] = previous
                                                selected = copy
                                            }) { Text("UP") }
                                            TextButton(enabled = index < selected.lastIndex, onClick = {
                                                val copy = selected.toMutableList()
                                                val next = copy[index + 1]
                                                copy[index + 1] = copy[index]
                                                copy[index] = next
                                                selected = copy
                                            }) { Text("DOWN") }
                                            TextButton(onClick = { selected = selected.filterNot { it.id == item.id } }) { Text("REMOVE") }
                                        }
                                    }
                                }
                            }
                        }
                        if (!PunjabBoardPattern.isComplete(grade, paperQuestions)) {
                            Text("Word export unlocks when the selected questions match the Punjab Board counts and marks.",
                                color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                },
                confirmButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { showQuestions = false }) { Text("DONE") }
                        Button(enabled = PunjabBoardPattern.isComplete(grade, paperQuestions),
                            onClick = { exportWord.launch("Ali-Test-Series-Class-$grade.docx") }) { Text("EXPORT WORD") }
                    }
                }
            )
        }
    }
}

private fun roman(value: Int): String = when (value) {
    1 -> "i"; 2 -> "ii"; 3 -> "iii"; 4 -> "iv"; 5 -> "v"
    6 -> "vi"; 7 -> "vii"; 8 -> "viii"; 9 -> "ix"
    else -> value.toString()
}
