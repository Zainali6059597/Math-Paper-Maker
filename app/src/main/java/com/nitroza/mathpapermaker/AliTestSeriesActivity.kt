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
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.lifecycleScope
import com.nitroza.mathpapermaker.docx.DocxPaperExporter
import com.nitroza.mathpapermaker.data.BundledBookManifestReader
import com.nitroza.mathpapermaker.paper.PaperQuestion
import com.nitroza.mathpapermaker.paper.PunjabBoardPattern
import com.nitroza.mathpapermaker.paper.PaperDraftStore
import com.nitroza.mathpapermaker.paper.PaperDraftSnapshot
import com.nitroza.mathpapermaker.paper.StoredPaperQuestion
import com.nitroza.mathpapermaker.ui.MathPaperMakerTheme
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class AliTestSeriesActivity : ComponentActivity() {
    companion object {
        const val MAX_TEXTBOOK_PDF_BYTES = 250L * 1024L * 1024L
    }
    private data class SelectedQuestion(
        val id: Long,
        val number: String,
        val text: String,
        val kind: String,
        val marks: Int,
        val sourcePage: Int,
        val imageFile: String? = null,
        val chapterId: String = "",
        val sectionId: String = ""
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
        var pageJumpText by remember { mutableStateOf("1") }
        var pageBitmap by remember { mutableStateOf<Bitmap?>(null) }
        var selected by remember { mutableStateOf<List<SelectedQuestion>>(emptyList()) }
        var nextId by remember { mutableLongStateOf(1L) }
        var notice by remember { mutableStateOf("Choose Class 9 or Class 10, then open its Mathematics PDF.") }
        var showQuestionEditor by remember { mutableStateOf(false) }
        var questionText by remember { mutableStateOf(TextFieldValue("")) }
        var questionKind by remember { mutableStateOf("SHORT") }
        var questionMarks by remember { mutableStateOf("2") }
        var questionNumber by remember { mutableStateOf("") }
        var questionChapter by remember { mutableStateOf("") }
        var questionSection by remember { mutableStateOf("Q2") }
        var chapterMenuExpanded by remember { mutableStateOf(false) }
        var pendingImageFile by remember { mutableStateOf<String?>(null) }
        var cropRect by remember { mutableStateOf<Rect?>(null) }
        var showQuestions by remember { mutableStateOf(false) }
        var showClearConfirm by remember { mutableStateOf(false) }
        var examTitle by remember { mutableStateOf("Mathematics Examination") }
        var schoolName by remember { mutableStateOf("") }
        var draftLoaded by remember { mutableStateOf(false) }
        var bundledBookReady by remember { mutableStateOf(false) }
        var bundledBookFile by remember { mutableStateOf("") }
        var chapterRanges by remember { mutableStateOf<List<BundledBookManifestReader.Chapter>>(emptyList()) }
        var useManifestChapters by remember { mutableStateOf(false) }

        // Release the previous rendered page only after Compose has switched away
        // from it; this prevents page navigation from retaining large bitmaps.
        DisposableEffect(pageBitmap) {
            val bitmapToRelease = pageBitmap
            onDispose {
                if (bitmapToRelease != null && !bitmapToRelease.isRecycled) {
                    bitmapToRelease.recycle()
                }
            }
        }

        LaunchedEffect(grade) {
            val expectedFile = "Punjab_Grade${grade}_Mathematics.pdf"
            val result = withContext(Dispatchers.IO) {
                runCatchingCancellable {
                    val available = assets.list("books").orEmpty().contains(expectedFile)
                    val chapters = BundledBookManifestReader.chaptersFor(
                        this@AliTestSeriesActivity, "Punjab", grade, "MATHEMATICS", "Mathematics", "2026-27"
                    )
                    available to chapters
                }.getOrDefault(false to emptyList())
            }
            bundledBookFile = expectedFile
            bundledBookReady = result.first && result.second.isNotEmpty()
            chapterRanges = if (bundledBookReady) result.second else emptyList()
        }

        fun openBundledBook() {
            lifecycleScope.launch {
                runCatchingCancellable {
                    val (file, count) = withContext(Dispatchers.IO) {
                        val target = File.createTempFile("ali-bundled-class$grade-", ".pdf", cacheDir)
                        try {
                            assets.open("books/$bundledBookFile").use { input ->
                                FileOutputStream(target).use { output -> copyPdfBounded(input, output) }
                            }
                            require(target.length() > 0L) { "The bundled textbook file is empty." }
                            val pageTotal = ParcelFileDescriptor.open(
                                target, ParcelFileDescriptor.MODE_READ_ONLY
                            ).use { descriptor ->
                                PdfRenderer(descriptor).use { it.pageCount }
                            }
                            require(pageTotal > 0) { "The bundled textbook has no readable pages." }
                            target to pageTotal
                        } catch (error: Throwable) {
                            target.delete()
                            throw error
                        }
                    }
                    require(chapterRanges.all { it.startPage >= 1 && it.endPage <= count }) {
                        "The bundled book manifest contains chapter pages outside the actual PDF."
                    }
                    pdfFile?.takeIf { it != file }?.delete()
                    pdfFile = file
                    pageBitmap = null
                    pdfName = bundledBookFile
                    useManifestChapters = true
                    pageCount = count
                    pageIndex = 0
                    cropRect = null
                    notice = "Bundled textbook opened. Chapter IDs are filled from the verified book manifest."
                }.onFailure { notice = "Could not open bundled textbook: ${it.message}" }
            }
        }

        fun chapterForPage(page: Int): String? = if (useManifestChapters) chapterRanges.firstOrNull { page in it.startPage..it.endPage }?.id else null

        LaunchedEffect(Unit) {
            // PDF imports are cache-only working copies. Remove leftovers from previous
            // process runs so repeated imports cannot quietly fill the app cache.
            withContext(Dispatchers.IO) {
                cacheDir.listFiles()
                    ?.filter { file ->
                        file.isFile &&
                            (
                                (file.name.startsWith("ali-import-class") ||
                                    file.name.startsWith("ali-bundled-class")) &&
                                    file.extension.equals("pdf", ignoreCase = true) ||
                                file.name.startsWith("ali-test-series-") &&
                                    file.extension.equals("docx", ignoreCase = true)
                            ) &&
                            System.currentTimeMillis() - file.lastModified() > 24L * 60L * 60L * 1000L
                    }
                    ?.forEach { it.delete() }
            }
            PaperDraftStore.load(this@AliTestSeriesActivity)?.let { saved ->
                grade = saved.grade
                examTitle = saved.examTitle
                schoolName = saved.schoolName
                selected = saved.questions.map { q ->
                    SelectedQuestion(q.id, q.number, q.text, q.kind, q.marks, q.sourcePage, q.imageFile, q.chapterId, q.sectionId)
                }
                nextId = (selected.maxOfOrNull { it.id } ?: 0L) + 1L
            }
            // A crop can be left behind if Android kills the app while the question editor is open.
            // Keep only images referenced by the restored draft; remove abandoned app-owned crops.
            withContext(Dispatchers.IO) {
                val imageDirectory = File(filesDir, "question_images")
                val referencedImages = selected.mapNotNull { it.imageFile }
                    .mapNotNull { path -> runCatching { File(path).canonicalPath }.getOrNull() }
                    .toSet()
                imageDirectory.listFiles()
                    ?.filter { file ->
                        file.isFile &&
                            file.name.startsWith("ali_question_") &&
                            file.extension.equals("png", ignoreCase = true) &&
                            runCatching { file.canonicalPath !in referencedImages }.getOrDefault(false)
                    }
                    ?.forEach { it.delete() }
            }
            draftLoaded = true
        }

        LaunchedEffect(grade, selected, examTitle, schoolName, draftLoaded) {
            if (draftLoaded) {
                PaperDraftStore.save(this@AliTestSeriesActivity, PaperDraftSnapshot(
                    grade, examTitle, schoolName,
                    selected.map { q -> StoredPaperQuestion(q.id, q.number, q.text, q.kind, q.marks, q.sourcePage, q.imageFile, q.chapterId, q.sectionId) }
                ))
            }
        }

        fun suggestedNumber(kind: String, current: List<SelectedQuestion>): String {
            val existing = current.map { it.number }.toSet()
            val limit = when (kind) { "MCQ" -> 15; "SHORT" -> 27; else -> 10 }
            return (1..limit).asSequence().map { index ->
                when (kind) {
                    "MCQ" -> "Q1(${roman(index)})"
                    "SHORT" -> "Q${2 + (index - 1) / 9}(${roman((index - 1) % 9 + 1)})"
                    else -> "Q${5 + (index - 1) / 2}(${if (index % 2 == 1) "a" else "b"})"
                }
            }.firstOrNull { it !in existing } ?: ""
        }

        val openPdf = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri != null) {
                lifecycleScope.launch {
                    runCatchingCancellable {
                        runCatching {
                            contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        val (file, count) = withContext(Dispatchers.IO) {
                            val target = File.createTempFile("ali-import-class$grade-", ".pdf", cacheDir)
                            try {
                                contentResolver.openInputStream(uri)?.use { input ->
                                    FileOutputStream(target).use { output -> copyPdfBounded(input, output) }
                                } ?: error("PDF file could not be opened.")
                                require(target.length() > 0L) { "The selected PDF is empty." }
                                val pageTotal = ParcelFileDescriptor.open(
                                    target, ParcelFileDescriptor.MODE_READ_ONLY
                                ).use { descriptor ->
                                    PdfRenderer(descriptor).use { it.pageCount }
                                }
                                require(pageTotal > 0) { "The selected PDF has no readable pages." }
                                target to pageTotal
                            } catch (error: Throwable) {
                                target.delete()
                                throw error
                            }
                        }
                        pdfFile?.takeIf { it != file }?.delete()
                        pdfFile = file
                        pageBitmap = null
                        pdfName = uri.lastPathSegment?.substringAfterLast('/') ?: "Mathematics.pdf"
                        useManifestChapters = false
                        pageCount = count
                        pageIndex = 0
                        cropRect = null
                        notice = "PDF opened. Select question text or draw a box around a scanned question."
                    }.onFailure { notice = "Could not open PDF: ${it.message}" }
                }
            }
        }

        val exportWord = rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
        ) { uri: Uri? ->
            if (uri != null) {
                lifecycleScope.launch {
                    runCatchingCancellable {
                        withContext(Dispatchers.IO) {
                            DocxPaperExporter.write(
                                this@AliTestSeriesActivity, uri, grade, examTitle, schoolName,
                                PunjabBoardPattern.DURATION_MINUTES,
                                selected.map { item ->
                                    PaperQuestion(item.number, item.text, item.kind, item.marks, item.sourcePage, item.imageFile, item.chapterId, item.sectionId)
                                }
                            )
                        }
                    }.onSuccess { notice = "Word paper exported. Open the DOCX in Word or another office app to review and print." }
                        .onFailure { notice = "Word export failed: ${it.message}" }
                }
            }
        }

        LaunchedEffect(pageIndex) {
            pageJumpText = (pageIndex + 1).toString()
        }

        LaunchedEffect(pdfFile, pageIndex) {
            val file = pdfFile ?: return@LaunchedEffect
            cropRect = null
            pageBitmap = null
            val renderedPage = withContext(Dispatchers.IO) {
                runCatchingCancellable {
                    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                        PdfRenderer(descriptor).use { renderer ->
                            require(pageIndex in 0 until renderer.pageCount) { "PDF page is out of range." }
                            renderer.openPage(pageIndex).use { page ->
                                // Bound both dimensions and total pixels so unusual PDF page
                                // sizes cannot allocate a huge bitmap on low-memory phones.
                                val pageWidth = page.width.toFloat()
                                val pageHeight = page.height.toFloat()
                                val dimensionScale = 1400f / pageWidth
                                val pixelScale = sqrt(1_500_000f / (pageWidth * pageHeight))
                                val scale = min(dimensionScale, pixelScale)
                                val width = (pageWidth * scale).toInt().coerceAtLeast(1)
                                val height = (pageHeight * scale).toInt().coerceAtLeast(1)
                                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                                try {
                                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                    bitmap
                                } catch (error: Throwable) {
                                    bitmap.recycle()
                                    throw error
                                }
                            }
                        }
                    }
                }
            }
            pageBitmap = renderedPage.getOrNull()
            renderedPage.exceptionOrNull()?.let { error ->
                notice = "Could not render PDF page: ${error.message ?: "unknown error"}"
            }
        }

        val paperQuestions = selected.map {
            PaperQuestion(it.number, it.text, it.kind, it.marks, it.sourcePage, it.imageFile, it.chapterId, it.sectionId)
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
                        FilterChip(selected = grade == 9, enabled = selected.isEmpty() || grade == 9, onClick = { grade = 9 }, label = { Text("CLASS 9") })
                        FilterChip(selected = grade == 10, enabled = selected.isEmpty() || grade == 10, onClick = { grade = 10 }, label = { Text("CLASS 10") })
                    }
                    Card {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Mathematics textbook PDF", style = MaterialTheme.typography.titleMedium)
                            if (bundledBookReady) {
                                Button(onClick = { openBundledBook() }, modifier = Modifier.fillMaxWidth()) {
                                    Text("OPEN BUNDLED CLASS $grade BOOK")
                                }
                                Text("Verified book manifest found · chapter labels will be filled automatically.", style = MaterialTheme.typography.bodySmall)
                            }
                            Text("You can also open a local PDF from your device.", style = MaterialTheme.typography.bodyMedium)
                            OutlinedButton(onClick = { openPdf.launch(arrayOf("application/pdf")) }, modifier = Modifier.fillMaxWidth()) {
                                Text("CHOOSE PDF FROM DEVICE")
                            }
                        }
                    }
                    if (selected.isNotEmpty()) {
                        OutlinedButton(onClick = { showQuestions = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("REVIEW SAVED PAPER (${selected.size} QUESTIONS)")
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
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = pageJumpText,
                            onValueChange = { pageJumpText = it.filter(Char::isDigit).take(9) },
                            modifier = Modifier.width(132.dp),
                            label = { Text("Go to page") },
                            singleLine = true
                        )
                        Button(
                            enabled = (pageJumpText.toIntOrNull() ?: 0) in 1..pageCount,
                            onClick = { pageIndex = (pageJumpText.toIntOrNull() ?: 1) - 1 }
                        ) { Text("GO") }
                        Text("of $pageCount", style = MaterialTheme.typography.labelSmall)
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
                                            val left = (min(origin.x, change.position.x) / size.width).coerceIn(0f, 1f)
                                            val top = (min(origin.y, change.position.y) / size.height).coerceIn(0f, 1f)
                                            val right = (max(origin.x, change.position.x) / size.width).coerceIn(0f, 1f)
                                            val bottom = (max(origin.y, change.position.y) / size.height).coerceIn(0f, 1f)
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
                                val extraction = withContext(Dispatchers.IO) {
                                    runCatchingCancellable {
                                        PDFBoxResourceLoader.init(applicationContext)
                                        // Keep PDFBox stream buffering on disk instead of retaining a large textbook in heap.
                                        PDDocument.load(file, MemoryUsageSetting.setupTempFileOnly()).use { document ->
                                            val extracted = PDFTextStripper().apply {
                                                sortByPosition = true
                                                startPage = pageIndex + 1
                                                endPage = pageIndex + 1
                                            }.getText(document)
                                            require(extracted.length <= 100_000) {
                                                "This page contains too much extractable text. Select a smaller page region or use the scanned-question crop."
                                            }
                                            extracted
                                        }
                                    }
                                }
                                extraction.onSuccess { text ->
                                    questionText = TextFieldValue(text)
                                    pendingImageFile = null
                                    questionNumber = suggestedNumber(questionKind, selected)
                                    questionSection = questionNumber.substringBefore("(")
                                    questionChapter = chapterForPage(pageIndex + 1).orEmpty()
                                    if (text.isBlank()) notice = "This page has no extractable text. Draw a box around the scanned question."
                                    else showQuestionEditor = true
                                }.onFailure { error ->
                                    notice = "Could not extract this PDF page: ${error.message ?: "unknown error"}"
                                }
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
                                    val imageDirectory = File(filesDir, "question_images").apply { mkdirs() }
                                    val image = File(imageDirectory, "ali_question_${nextId}.png")
                                    try {
                                        FileOutputStream(image).use { output ->
                                            check(crop.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                                                "The selected question image could not be encoded."
                                            }
                                            output.flush()
                                        }
                                        pendingImageFile = image.absolutePath
                                    } catch (error: Throwable) {
                                        image.delete()
                                        throw error
                                    } finally {
                                        crop.recycle()
                                    }
                                    questionText = TextFieldValue("")
                                    questionNumber = suggestedNumber(questionKind, selected)
                                    questionSection = questionNumber.substringBefore("(")
                                    questionChapter = chapterForPage(pageIndex + 1).orEmpty()
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
                onDismissRequest = {
                    pendingImageFile?.let { File(it).delete() }
                    pendingImageFile = null
                    showQuestionEditor = false
                },
                title = { Text(if (pendingImageFile == null) "SELECT QUESTION TEXT" else "ADD SCANNED QUESTION") },
                text = {
                    Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            if (pendingImageFile == null) "Long-press and drag the text selection handles over the exact question. If no text is selected, the full edited text will be used."
                            else "The selected page area will be embedded as an image in the Word document.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (pendingImageFile == null) {
                            OutlinedTextField(questionText, { questionText = it }, modifier = Modifier.fillMaxWidth(),
                                minLines = 8, label = { Text("Select the question text") })
                        } else {
                            Text("Scanned crop ready · PDF page ${pageIndex + 1}", color = MaterialTheme.colorScheme.primary)
                        }
                        OutlinedTextField(questionNumber, { value ->
                            questionNumber = value.take(16)
                            val candidateSection = value.trim().substringBefore("(").trim()
                            if (candidateSection in (1..9).map { "Q$it" }) questionSection = candidateSection
                        },
                            label = { Text("Paper numbering (editable)") }, singleLine = true)
                        Box {
                            OutlinedButton(onClick = { chapterMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                                Text(if (questionChapter.isBlank()) "SELECT CHAPTER" else "CHAPTER: $questionChapter")
                            }
                            DropdownMenu(expanded = chapterMenuExpanded, onDismissRequest = { chapterMenuExpanded = false }) {
                                (1..(if (grade == 9) 13 else 12)).forEach { chapter ->
                                    DropdownMenuItem(text = { Text("CH$chapter") }, onClick = {
                                        questionChapter = "CH$chapter"
                                        chapterMenuExpanded = false
                                    })
                                }
                            }
                        }
                        Text("Question type", style = MaterialTheme.typography.labelMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("MCQ", "SHORT", "LONG").forEach { kind ->
                                FilterChip(selected = questionKind == kind, onClick = {
                                    questionKind = kind
                                    questionMarks = when (kind) { "MCQ" -> "1"; "SHORT" -> "2"; else -> "4" }
                                    questionNumber = suggestedNumber(kind, selected)
                                    questionSection = questionNumber.substringBefore("(")
                                }, label = { Text(kind) })
                            }
                        }
                        OutlinedTextField(questionMarks, { questionMarks = it.filter(Char::isDigit).take(2) },
                            label = { Text("Marks") }, singleLine = true)
                        val kindLimit = when (questionKind) { "MCQ" -> 15; "SHORT" -> 27; else -> 10 }
                        val kindCount = selected.count { it.kind == questionKind }
                        Text(
                            "$questionKind questions selected: $kindCount / $kindLimit",
                            color = if (kindCount >= kindLimit) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                },
                confirmButton = {
                    TextButton(enabled = (questionText.text.isNotBlank() || pendingImageFile != null) &&
                        questionNumber.isNotBlank() && PunjabBoardPattern.isValidChapter(grade, questionChapter) && (questionMarks.toIntOrNull() ?: 0) > 0 &&
                        selected.count { it.kind == questionKind } < when (questionKind) {
                            "MCQ" -> 15
                            "SHORT" -> 27
                            else -> 10
                        }, onClick = {
                        selected = selected + SelectedQuestion(
                            nextId++, questionNumber.trim(), selectedQuestionText(questionText), questionKind,
                            questionMarks.toInt(), pageIndex + 1, pendingImageFile, questionChapter, questionSection
                        )
                        showQuestionEditor = false
                        pendingImageFile = null
                        cropRect = null
                        notice = "Question added to your manual selection."
                    }) { Text("ADD QUESTION") }
                },
                dismissButton = { TextButton(onClick = {
                    pendingImageFile?.let { File(it).delete() }
                    pendingImageFile = null
                    showQuestionEditor = false
                }) { Text("CANCEL") } }
            )
        }

        if (showQuestions) {
            val missingImageCount = selected.count { item ->
                item.imageFile != null && !File(item.imageFile).isFile
            }
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
                        patternErrors.forEach { error ->
                            Text("• $error", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                        if (missingImageCount > 0) {
                            Text(
                                "• $missingImageCount scanned question image(s) are missing from this device. Remove and re-add those questions before exporting.",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
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
                                                    val candidateSection = value.trim().substringBefore("(").trim()
                                                    selected = selected.map { question ->
                                                        if (question.id == item.id) question.copy(
                                                            number = value.take(16),
                                                            sectionId = if (candidateSection in (1..9).map { "Q$it" }) candidateSection else question.sectionId
                                                        ) else question
                                                    }
                                                },
                                                modifier = Modifier.width(132.dp),
                                                label = { Text("Number") },
                                                singleLine = true
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text("${item.sectionId} · ${item.kind} · ${item.marks} marks · page ${item.sourcePage}",
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
                                            TextButton(onClick = {
                                                item.imageFile?.let { File(it).delete() }
                                                selected = selected.filterNot { it.id == item.id }
                                            }) { Text("REMOVE") }
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
                        TextButton(onClick = { showClearConfirm = true }) { Text("NEW PAPER") }
                        TextButton(onClick = { showQuestions = false }) { Text("DONE") }
                        Button(enabled = PunjabBoardPattern.isComplete(grade, paperQuestions) && missingImageCount == 0,
                            onClick = { exportWord.launch("Ali-Test-Series-Class-$grade.docx") }) { Text("EXPORT WORD") }
                    }
                }
            )
        }

        if (showClearConfirm) {
            AlertDialog(
                onDismissRequest = { showClearConfirm = false },
                title = { Text("Start a new paper?") },
                text = { Text("This clears the current selected questions and paper details from this device.") },
                confirmButton = {
                    TextButton(onClick = {
                        selected.forEach { it.imageFile?.let { path -> File(path).delete() } }
                        selected = emptyList()
                        nextId = 1L
                        schoolName = ""
                        examTitle = "Mathematics Examination"
                        showQuestions = false
                        showClearConfirm = false
                    }) { Text("CLEAR AND START NEW") }
                },
                dismissButton = { TextButton(onClick = { showClearConfirm = false }) { Text("CANCEL") } }
            )
        }
    }
}

private suspend fun copyPdfBounded(input: java.io.InputStream, output: java.io.OutputStream) {
    val buffer = ByteArray(64 * 1024)
    var total = 0L
    while (true) {
        coroutineContext.ensureActive()
        val read = input.read(buffer)
        if (read < 0) break
        total += read.toLong()
        require(total <= AliTestSeriesActivity.MAX_TEXTBOOK_PDF_BYTES) {
            "This PDF is larger than the 250 MB import limit. Choose a smaller textbook file."
        }
        output.write(buffer, 0, read)
    }
    output.flush()
}

private fun roman(value: Int): String = when (value) {
    1 -> "i"; 2 -> "ii"; 3 -> "iii"; 4 -> "iv"; 5 -> "v"
    6 -> "vi"; 7 -> "vii"; 8 -> "viii"; 9 -> "ix"
    else -> value.toString()
}

private fun selectedQuestionText(value: TextFieldValue): String {
    val selection = value.selection
    return if (!selection.collapsed && selection.min >= 0 && selection.max <= value.text.length) {
        value.text.substring(selection.min, selection.max).trim()
    } else value.text.trim()
}
private suspend fun <T> runCatchingCancellable(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (error: Throwable) {
    Result.failure(error)
}
