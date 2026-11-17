package com.nitroza.mathpapermaker

import android.content.Intent
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
import androidx.compose.ui.draw.drawWithContent
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
import com.nitroza.mathpapermaker.data.QuestionTextSegmenter
import com.nitroza.mathpapermaker.data.OfflinePdfOcr
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
        var pendingGrade by remember { mutableIntStateOf(9) }
        var showGradeSwitchConfirm by remember { mutableStateOf(false) }
        var selectedChapters by remember { mutableStateOf((1..13).map { "CH$it" }.toSet()) }
        var showChapterPicker by remember { mutableStateOf(false) }
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
        var showManualAddPrompt by remember { mutableStateOf(false) }
        var showManualQuestionEditor by remember { mutableStateOf(false) }
        var detectedQuestions by remember { mutableStateOf<List<QuestionTextSegmenter.Segment>>(emptyList()) }
        var checkedDetectedQuestions by remember { mutableStateOf<Set<Int>>(emptySet()) }
        var detectedQuestionPage by remember { mutableIntStateOf(1) }
        var detectedQuestionKind by remember { mutableStateOf("SHORT") }
        var detectedQuestionChapter by remember { mutableStateOf("") }
        var detectedChapterMenuExpanded by remember { mutableStateOf(false) }
        var showDetectedQuestions by remember { mutableStateOf(false) }
        var isOcrRunning by remember { mutableStateOf(false) }
        var manualQuestionText by remember { mutableStateOf(TextFieldValue("")) }
        var manualQuestionNumber by remember { mutableStateOf("") }
        var manualQuestionMarks by remember { mutableStateOf("2") }
        var manualQuestionKind by remember { mutableStateOf("SHORT") }
        var manualQuestionChapter by remember { mutableStateOf("") }
        var manualChapterMenuExpanded by remember { mutableStateOf(false) }
        var examTitle by remember { mutableStateOf("Mathematics Examination") }
        var paperMode by remember { mutableStateOf("BOARD") }
        var schoolName by remember { mutableStateOf("") }
        var draftLoaded by remember { mutableStateOf(false) }
        var bundledBookReady by remember { mutableStateOf(false) }
        var bundledBookFile by remember { mutableStateOf("") }
        var savedBookPath by remember { mutableStateOf("") }
        var pdfIsPersistent by remember { mutableStateOf(false) }
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
                    val savedPath = File(filesDir, "books/Punjab_Grade${grade}_Mathematics.pdf")
                    Triple(available, chapters, savedPath.takeIf { it.isFile }?.absolutePath.orEmpty())
                }.getOrDefault(Triple(false, emptyList(), ""))
            }
            bundledBookFile = expectedFile
            bundledBookReady = result.first && result.second.isNotEmpty()
            chapterRanges = if (bundledBookReady) result.second else emptyList()
            savedBookPath = result.third
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
                    pdfFile?.takeIf { it != file && !pdfIsPersistent }?.delete()
                    pdfFile = file
                    pdfIsPersistent = false
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

        fun openOfficialBookSource() {
            val sourceUrl = when (grade) {
                9 -> "https://drive.google.com/file/d/1qJF9YsdVpOKk-HzRUwftZZzINLmVkJv3/view?usp=sharing"
                10 -> "https://drive.google.com/file/d/1MKYcHg2WdNDIjqeD2JFKpcs_79oZ_3fq/view?usp=drive_link"
                else -> return
            }
            runCatching {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(sourceUrl)))
            }.onFailure {
                notice = "Could not open the official textbook source. Check that a browser or PDF app is installed."
            }
        }

        fun openSavedBook() {
            val path = savedBookPath
            if (path.isBlank()) return
            lifecycleScope.launch {
                runCatchingCancellable {
                    val file = File(path)
                    require(file.isFile && file.length() > 0L) { "The saved textbook is missing. Import it again from your device." }
                    val count = withContext(Dispatchers.IO) {
                        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                            PdfRenderer(descriptor).use { it.pageCount }
                        }
                    }
                    require(count > 0) { "The saved textbook has no readable pages." }
                    pdfFile?.takeIf { it != file && !pdfIsPersistent }?.delete()
                    pdfFile = file
                    pdfIsPersistent = true
                    pageBitmap = null
                    pdfName = file.name
                    useManifestChapters = false
                    pageCount = count
                    pageIndex = 0
                    cropRect = null
                    notice = "Saved local textbook opened. It is available offline; confirm its edition before using Board Pattern mode."
                }.onFailure { notice = "Could not open saved textbook: ${it.message}" }
            }
        }

        fun chapterForPage(page: Int): String? = if (useManifestChapters) chapterRanges.firstOrNull { page in it.startPage..it.endPage }?.id else null

        LaunchedEffect(Unit) {
            // Keep completed textbook imports in the offline Book Library. Only remove
            // abandoned partial imports and stale rendered/export working files.
            withContext(Dispatchers.IO) {
                val libraryDirectory = File(filesDir, "books")
                libraryDirectory.listFiles()
                    ?.filter { file ->
                        file.isFile && file.name.startsWith("ali-book-import-class") &&
                            System.currentTimeMillis() - file.lastModified() > 24L * 60L * 60L * 1000L
                    }
                    ?.forEach { it.delete() }
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
                paperMode = saved.paperMode
                selectedChapters = saved.selectedChapters
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

        LaunchedEffect(grade, selected, examTitle, schoolName, paperMode, selectedChapters, draftLoaded) {
            if (draftLoaded) {
                PaperDraftStore.save(this@AliTestSeriesActivity, PaperDraftSnapshot(
                    grade, examTitle, schoolName,
                    selected.map { q -> StoredPaperQuestion(q.id, q.number, q.text, q.kind, q.marks, q.sourcePage, q.imageFile, q.chapterId, q.sectionId) },
                    paperMode,
                    selectedChapters
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
                            val bookDirectory = File(filesDir, "books").apply { mkdirs() }
                            val target = File.createTempFile("ali-book-import-class$grade-", ".pdf", bookDirectory)
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
                                val savedFile = File(bookDirectory, "Punjab_Grade${grade}_Mathematics.pdf")
                                if (savedFile.exists()) check(savedFile.delete()) { "The previous saved textbook could not be replaced." }
                                if (!target.renameTo(savedFile)) {
                                    target.copyTo(savedFile, overwrite = true)
                                    target.delete()
                                }
                                savedFile to pageTotal
                            } catch (error: Throwable) {
                                target.delete()
                                throw error
                            }
                        }
                        pdfFile?.takeIf { it != file && !pdfIsPersistent }?.delete()
                        pdfFile = file
                        pdfIsPersistent = true
                        savedBookPath = file.absolutePath
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
                                },
                                paperMode
                            )
                        }
                    }.onSuccess { notice = "Editable Word DOCX exported. Text questions can be edited in Word; scanned question crops remain images." }
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

        fun insertMathToken(token: String) {
            val start = manualQuestionText.selection.min.coerceIn(0, manualQuestionText.text.length)
            val end = manualQuestionText.selection.max.coerceIn(start, manualQuestionText.text.length)
            val updated = manualQuestionText.text.replaceRange(start, end, token)
            val cursor = start + token.length
            manualQuestionText = TextFieldValue(updated, androidx.compose.ui.text.TextRange(cursor))
        }

        fun insertMathTemplate(prefix: String, suffix: String) {
            val start = manualQuestionText.selection.min.coerceIn(0, manualQuestionText.text.length)
            val end = manualQuestionText.selection.max.coerceIn(start, manualQuestionText.text.length)
            val updated = manualQuestionText.text.replaceRange(start, end, prefix + suffix)
            manualQuestionText = TextFieldValue(updated, androidx.compose.ui.text.TextRange(start + prefix.length))
        }

        fun findQuestionsOnCurrentPage() {
            lifecycleScope.launch {
                val file = pdfFile ?: return@launch
                val extraction = withContext(Dispatchers.IO) {
                    runCatchingCancellable {
                        PDFBoxResourceLoader.init(applicationContext)
                        PDDocument.load(file, MemoryUsageSetting.setupTempFileOnly()).use { document ->
                            PDFTextStripper().apply {
                                sortByPosition = true
                                startPage = pageIndex + 1
                                endPage = pageIndex + 1
                            }.getText(document)
                        }
                    }
                }
                extraction.onSuccess { pageText ->
                    val found = QuestionTextSegmenter.segment(pageText)
                    detectedQuestions = found
                    checkedDetectedQuestions = emptySet()
                    detectedQuestionPage = pageIndex + 1
                    detectedQuestionChapter = chapterForPage(pageIndex + 1).orEmpty()
                    if (found.isEmpty()) {
                        notice = "No clearly numbered text questions were detected on this page. Use SELECT TEXT, ADD BOX CROP, or the manual editor."
                    } else {
                        showDetectedQuestions = true
                        notice = "Detected ${found.size} numbered question candidates. Tick only the questions you want."
                    }
                }.onFailure { error ->
                    notice = "Could not scan this page: ${error.message ?: "unknown error"}"
                }
            }
        }

        fun recognizeScannedQuestionsOnCurrentPage() {
            val file = pdfFile ?: run {
                notice = "Open a Mathematics PDF first."
                return
            }
            if (isOcrRunning) return
            isOcrRunning = true
            lifecycleScope.launch {
                val extraction = try {
                    runCatchingCancellable {
                        OfflinePdfOcr.readPage(file, pageIndex)
                    }
                } finally {
                    isOcrRunning = false
                }
                extraction.onSuccess { recognizedText ->
                    val found = QuestionTextSegmenter.segment(recognizedText)
                    detectedQuestions = found
                    checkedDetectedQuestions = emptySet()
                    detectedQuestionPage = pageIndex + 1
                    detectedQuestionChapter = chapterForPage(pageIndex + 1).orEmpty()
                    if (found.isEmpty()) {
                        notice = if (recognizedText.isBlank()) {
                            "OCR could not read text on this page. Try a clearer scan or add the question manually."
                        } else {
                            "OCR read the page, but no clearly numbered questions were found. Use the crop or manual editor."
                        }
                    } else {
                        showDetectedQuestions = true
                        notice = "Offline OCR found ${found.size} question candidates. Review and tick only the required questions."
                    }
                }.onFailure { error ->
                    notice = "Offline OCR failed: ${error.message ?: "unknown error"}"
                }
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
                        FilterChip(selected = grade == 9, enabled = true, onClick = {
                            if (grade != 9 && selected.isNotEmpty()) {
                                pendingGrade = 9
                                showGradeSwitchConfirm = true
                            } else {
                                grade = 9
                                selectedChapters = (1..13).map { "CH$it" }.toSet()
                            }
                        }, label = { Text("CLASS 9") })
                        FilterChip(selected = grade == 10, enabled = true, onClick = {
                            if (grade != 10 && selected.isNotEmpty()) {
                                pendingGrade = 10
                                showGradeSwitchConfirm = true
                            } else {
                                grade = 10
                                selectedChapters = (1..12).map { "CH$it" }.toSet()
                            }
                        }, label = { Text("CLASS 10") })
                    }
                    Card {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Mathematics textbook PDF", style = MaterialTheme.typography.titleMedium)
                            if (savedBookPath.isNotBlank()) {
                                Button(onClick = { openSavedBook() }, modifier = Modifier.fillMaxWidth()) {
                                    Text("OPEN SAVED CLASS $grade BOOK · OFFLINE")
                                }
                                Text("A local copy is stored inside the app and remains available offline.", style = MaterialTheme.typography.bodySmall)
                            }
                            if (bundledBookReady) {
                                Button(onClick = { openBundledBook() }, modifier = Modifier.fillMaxWidth()) {
                                    Text("OPEN BUNDLED CLASS $grade BOOK")
                                }
                                Text("Verified book manifest found · chapter labels will be filled automatically.", style = MaterialTheme.typography.bodySmall)
                            }
                            Text("Official textbook downloads open in your browser. Download the PDF, return here, and import it for offline use.", style = MaterialTheme.typography.bodyMedium)
                            OutlinedButton(onClick = { openOfficialBookSource() }, modifier = Modifier.fillMaxWidth()) {
                                Text("GET OFFICIAL CLASS $grade TEXTBOOK")
                            }
                            OutlinedButton(onClick = { openPdf.launch(arrayOf("application/pdf")) }, modifier = Modifier.fillMaxWidth()) {
                                Text("IMPORT PDF TO BOOK LIBRARY")
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
                        TextButton(onClick = {
                            // Release the previous imported/bundled PDF immediately.
                            // Textbooks can be hundreds of megabytes; waiting for the
                            // next app launch's stale-cache cleanup wastes device storage.
                            if (!pdfIsPersistent) pdfFile?.delete()
                            pdfFile = null
                            pdfIsPersistent = false
                            pageBitmap = null
                            pdfName = ""
                            pageCount = 0
                            pageIndex = 0
                            pageJumpText = "1"
                            cropRect = null
                            useManifestChapters = false
                        }) { Text("CHANGE PDF") }
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
                    OutlinedButton(onClick = { findQuestionsOnCurrentPage() }, modifier = Modifier.fillMaxWidth()) {
                        Text("FIND NUMBERED QUESTIONS ON PAGE")
                    }
                    OutlinedButton(
                        onClick = { recognizeScannedQuestionsOnCurrentPage() },
                        enabled = pdfFile != null && !isOcrRunning,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isOcrRunning) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("READING SCANNED PAGE…")
                        } else Text("OCR SCANNED PAGE · OFFLINE")
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
                                selectedChapters.sortedBy { it.removePrefix("CH").toIntOrNull() ?: 0 }.forEach { chapterId ->
                                    DropdownMenuItem(text = { Text(chapterId) }, onClick = {
                                        questionChapter = chapterId
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
                    val requiredMarks = when (questionKind) { "MCQ" -> 1; "SHORT" -> 2; else -> 4 }
                    val normalizedNumber = questionNumber.trim()
                    val duplicateNumber = selected.any { it.number.equals(normalizedNumber, ignoreCase = true) }
                    val atKindLimit = selected.count { it.kind == questionKind } >= when (questionKind) {
                        "MCQ" -> 15
                        "SHORT" -> 27
                        else -> 10
                    }
                    TextButton(enabled = (questionText.text.isNotBlank() || pendingImageFile != null) &&
                        normalizedNumber.isNotBlank() && !duplicateNumber &&
                        PunjabBoardPattern.isValidChapter(grade, questionChapter) && questionChapter in selectedChapters &&
                        questionMarks.toIntOrNull() == requiredMarks && !atKindLimit, onClick = {
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

        if (showDetectedQuestions) {
            val maxForKind = when (detectedQuestionKind) { "MCQ" -> 15; "SHORT" -> 27; else -> 10 }
            val alreadySelected = selected.count { it.kind == detectedQuestionKind }
            val allChaptersSelected = selectedChapters.size == (if (grade == 9) 13 else 12)
            val chapterReady = if (allChaptersSelected && paperMode == "CUSTOM") true
                else PunjabBoardPattern.isValidChapter(grade, detectedQuestionChapter) && detectedQuestionChapter in selectedChapters
            val canAddChecked = checkedDetectedQuestions.isNotEmpty() && chapterReady &&
                (paperMode == "CUSTOM" || alreadySelected + checkedDetectedQuestions.size <= maxForKind)
            AlertDialog(
                onDismissRequest = { showDetectedQuestions = false },
                title = { Text("TICK QUESTIONS TO ADD (${checkedDetectedQuestions.size}/${detectedQuestions.size})") },
                text = {
                    Column(Modifier.heightIn(max = 560.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Page $detectedQuestionPage · Select the exact questions. Wrapped lines are grouped with their numbered question. Scanned pages still need a crop.", style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { checkedDetectedQuestions = detectedQuestions.indices.toSet() }) { Text("SELECT ALL") }
                            TextButton(onClick = { checkedDetectedQuestions = emptySet() }) { Text("CLEAR") }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("MCQ", "SHORT", "LONG").forEach { kind ->
                                FilterChip(selected = detectedQuestionKind == kind, onClick = { detectedQuestionKind = kind }, label = { Text(kind) })
                            }
                        }
                        Box {
                            OutlinedButton(onClick = { detectedChapterMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                                Text(if (detectedQuestionChapter.isBlank()) "SELECT CHAPTER" else "CHAPTER: $detectedQuestionChapter")
                            }
                            DropdownMenu(expanded = detectedChapterMenuExpanded, onDismissRequest = { detectedChapterMenuExpanded = false }) {
                                (1..(if (grade == 9) 13 else 12)).map { "CH$it" }.filter { it in selectedChapters }.forEach { chapterId ->
                                    DropdownMenuItem(text = { Text(chapterId) }, onClick = {
                                        detectedQuestionChapter = chapterId
                                        detectedChapterMenuExpanded = false
                                    })
                                }
                                if (paperMode == "CUSTOM" && selectedChapters.size == (if (grade == 9) 13 else 12)) {
                                    DropdownMenuItem(text = { Text("No chapter (Custom only)") }, onClick = {
                                        detectedQuestionChapter = ""
                                        detectedChapterMenuExpanded = false
                                    })
                                }
                            }
                        }
                        Text("They will be added as $detectedQuestionKind questions (${when (detectedQuestionKind) { "MCQ" -> 1; "SHORT" -> 2; else -> 4 }} marks each). Edit numbering and marks in Review.", style = MaterialTheme.typography.bodySmall)
                        LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            itemsIndexed(detectedQuestions) { index, candidate ->
                                Card {
                                    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.Top) {
                                        Checkbox(
                                            checked = index in checkedDetectedQuestions,
                                            onCheckedChange = { checked ->
                                                checkedDetectedQuestions = if (checked) checkedDetectedQuestions + index else checkedDetectedQuestions - index
                                            }
                                        )
                                        Column(Modifier.weight(1f).padding(top = 10.dp)) {
                                            Text("PDF Q${candidate.sourceNumber}", style = MaterialTheme.typography.labelLarge)
                                            OutlinedTextField(
                                                value = candidate.text,
                                                onValueChange = { edited ->
                                                    detectedQuestions = detectedQuestions.toMutableList().also { list ->
                                                        list[index] = candidate.copy(text = edited)
                                                    }
                                                },
                                                modifier = Modifier.fillMaxWidth(),
                                                minLines = 2,
                                                maxLines = 5,
                                                label = { Text("Review / correct question text") }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        if (!chapterReady && checkedDetectedQuestions.isNotEmpty() &&
                            !PunjabBoardPattern.isValidChapter(grade, detectedQuestionChapter)) {
                            Text("Select a valid chapter before adding these questions.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        } else if (!chapterReady && checkedDetectedQuestions.isNotEmpty() &&
                            detectedQuestionChapter !in selectedChapters) {
                            Text("This page belongs to a chapter outside your selected scope. Choose an included chapter or update the scope.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        } else if (alreadySelected + checkedDetectedQuestions.size > maxForKind && paperMode == "BOARD") {
                            Text("This selection exceeds the remaining $detectedQuestionKind slots in Board Pattern mode.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                },
                confirmButton = {
                    Button(enabled = canAddChecked, onClick = {
                        val newQuestions = selected.toMutableList()
                        val marks = when (detectedQuestionKind) { "MCQ" -> 1; "SHORT" -> 2; else -> 4 }
                        checkedDetectedQuestions.sorted().forEach { index ->
                            val candidate = detectedQuestions[index]
                            var number = suggestedNumber(detectedQuestionKind, newQuestions)
                            if (number.isBlank() && paperMode == "CUSTOM") {
                                var suffix = newQuestions.size + 1
                                do { number = "Q$suffix"; suffix++ }
                                while (newQuestions.any { it.number.equals(number, true) })
                            }
                            if (number.isNotBlank()) {
                                newQuestions += SelectedQuestion(
                                    id = nextId++, number = number, text = candidate.text,
                                    kind = detectedQuestionKind, marks = marks,
                                    sourcePage = detectedQuestionPage, imageFile = null,
                                    chapterId = detectedQuestionChapter,
                                    sectionId = number.substringBefore("(")
                                )
                            }
                        }
                        val addedCount = newQuestions.size - selected.size
                        selected = newQuestions
                        showDetectedQuestions = false
                        showQuestions = true
                        notice = "Added $addedCount checked question(s). Review their chapters and numbering before export."
                    }) { Text("ADD CHECKED QUESTIONS") }
                },
                dismissButton = { TextButton(onClick = { showDetectedQuestions = false }) { Text("CANCEL") } }
            )
        }

        if (showChapterPicker) {
            val chapterCount = if (grade == 9) 13 else 12
            AlertDialog(
                onDismissRequest = { showChapterPicker = false },
                title = { Text("CHOOSE CHAPTERS FOR THIS PAPER") },
                text = {
                    Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
                        Text("Select one or more chapters. Questions added to this paper must belong to the included chapters.", style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { selectedChapters = (1..chapterCount).map { "CH$it" }.toSet() }) { Text("SELECT ALL") }
                            TextButton(onClick = { selectedChapters = emptySet() }) { Text("CLEAR") }
                        }
                        (1..chapterCount).forEach { chapter ->
                            val chapterId = "CH$chapter"
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = chapterId in selectedChapters,
                                    onCheckedChange = { checked ->
                                        selectedChapters = if (checked) selectedChapters + chapterId else selectedChapters - chapterId
                                    }
                                )
                                Text("Chapter $chapter")
                            }
                        }
                        if (selectedChapters.isEmpty()) {
                            Text("Select at least one chapter to continue.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                },
                confirmButton = {
                    Button(enabled = selectedChapters.isNotEmpty(), onClick = { showChapterPicker = false }) { Text("APPLY CHAPTERS") }
                },
                dismissButton = { TextButton(onClick = {
                    selectedChapters = (1..chapterCount).map { "CH$it" }.toSet()
                    showChapterPicker = false
                }) { Text("RESET ALL") } }
            )
        }

        if (showQuestions) {
            val missingImageCount = selected.count { item ->
                item.imageFile != null && !File(item.imageFile).isFile
            }
            val allChaptersSelected = selectedChapters.size == (if (grade == 9) 13 else 12)
            val questionsInChapterScope = allChaptersSelected || selected.all { it.chapterId in selectedChapters }
            val boardReady = paperMode != "BOARD" || (allChaptersSelected && PunjabBoardPattern.isComplete(grade, paperQuestions))
            AlertDialog(
                onDismissRequest = { showQuestions = false },
                title = { Text("SELECTED QUESTIONS (${selected.size})") },
                text = {
                    Column(Modifier.heightIn(max = 560.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(schoolName, { schoolName = it }, modifier = Modifier.fillMaxWidth(),
                            label = { Text("School name (optional)") }, singleLine = true)
                        OutlinedTextField(examTitle, { examTitle = it }, modifier = Modifier.fillMaxWidth(),
                            label = { Text("Paper title") }, singleLine = true)
                        Text("CHAPTER SCOPE", style = MaterialTheme.typography.labelLarge)
                        OutlinedButton(onClick = { showChapterPicker = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("INCLUDE CHAPTERS: ${selectedChapters.size} / ${if (grade == 9) 13 else 12}")
                        }
                        if (!allChaptersSelected && paperMode == "BOARD") {
                            Text("A full Punjab Board pattern uses chapter distribution across the complete syllabus. Switch to CUSTOM for a selected-chapter paper.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                        Text("PAPER FORMAT", style = MaterialTheme.typography.labelLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = paperMode == "BOARD", onClick = { paperMode = "BOARD" }, label = { Text("BOARD PATTERN") })
                            FilterChip(selected = paperMode == "CUSTOM", onClick = { paperMode = "CUSTOM" }, label = { Text("CUSTOM") })
                        }
                        if (paperMode == "BOARD") {
                            Text("Punjab Board · " + PunjabBoardPattern.status(paperQuestions), style = MaterialTheme.typography.labelMedium)
                            PunjabBoardPattern.validationErrors(grade, paperQuestions).forEach { error ->
                                Text("• " + error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            }
                        } else {
                            Text("Custom paper · no fixed question-count or board-distribution restriction. Edit question numbers, sections, types and marks below.", style = MaterialTheme.typography.bodySmall)
                            Text("Total marks: " + paperQuestions.sumOf { it.marks }, style = MaterialTheme.typography.labelMedium)
                        }
                        if (!questionsInChapterScope) {
                            Text("Some selected questions are outside the included chapters. Change their chapter or update the chapter scope before export.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
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
                                        val itemKindMenu = remember(item.id) { mutableStateOf(false) }
                                        Box {
                                            OutlinedButton(onClick = { itemKindMenu.value = true }) {
                                                Text("TYPE: ${item.kind}")
                                            }
                                            DropdownMenu(
                                                expanded = itemKindMenu.value,
                                                onDismissRequest = { itemKindMenu.value = false }
                                            ) {
                                                listOf("MCQ", "SHORT", "LONG").forEach { kind ->
                                                    DropdownMenuItem(
                                                        text = { Text(kind) },
                                                        onClick = {
                                                            val remaining = selected.filterNot { it.id == item.id }
                                                            val newNumber = suggestedNumber(kind, remaining)
                                                            selected = selected.map { question ->
                                                                if (question.id == item.id) question.copy(
                                                                    kind = kind,
                                                                    marks = when (kind) { "MCQ" -> 1; "SHORT" -> 2; else -> 4 },
                                                                    number = newNumber,
                                                                    sectionId = newNumber.substringBefore("(")
                                                                ) else question
                                                            }
                                                            itemKindMenu.value = false
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                        OutlinedTextField(
                                            value = item.marks.toString(),
                                            onValueChange = { value ->
                                                val parsed = value.filter(Char::isDigit).take(2).toIntOrNull() ?: 0
                                                selected = selected.map { question ->
                                                    if (question.id == item.id) question.copy(marks = parsed) else question
                                                }
                                            },
                                            modifier = Modifier.width(112.dp),
                                            label = { Text("Marks") },
                                            singleLine = true
                                        )
                                        val itemChapterMenu = remember(item.id) { mutableStateOf(false) }
                                        Box {
                                            OutlinedButton(onClick = { itemChapterMenu.value = true }) {
                                                Text("CHAPTER: ${item.chapterId}")
                                            }
                                            DropdownMenu(
                                                expanded = itemChapterMenu.value,
                                                onDismissRequest = { itemChapterMenu.value = false }
                                            ) {
                                                selectedChapters.sortedBy { it.removePrefix("CH").toIntOrNull() ?: 0 }.forEach { chapterId ->
                                                    DropdownMenuItem(
                                                        text = { Text(chapterId) },
                                                        onClick = {
                                                            selected = selected.map { question ->
                                                                if (question.id == item.id) question.copy(chapterId = chapterId) else question
                                                            }
                                                            itemChapterMenu.value = false
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                        if (item.imageFile != null) {
                                            Text("Scanned crop is retained as a reference. Type an editable version below to avoid printing the picture.", style = MaterialTheme.typography.bodySmall)
                                        }
                                        OutlinedTextField(
                                            value = item.text,
                                            onValueChange = { value ->
                                                selected = selected.map { question ->
                                                    if (question.id == item.id) question.copy(text = value.take(100_000)) else question
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            label = { Text("Editable question text") },
                                            minLines = 2,
                                            maxLines = 5
                                        )
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
                        if (!boardReady) {
                            Text("Board-pattern export requires the official counts, numbering, marks and chapter distribution. Switch to Custom to export a manually arranged paper.",
                                color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                },
                confirmButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { showClearConfirm = true }) { Text("NEW PAPER") }
                        TextButton(onClick = { showQuestions = false }) { Text("DONE") }
                        Button(enabled = boardReady && questionsInChapterScope && missingImageCount == 0 && selected.isNotEmpty(),
                            onClick = { showManualAddPrompt = true }) { Text("EXPORT EDITABLE DOCX") }
                    }
                }
            )
        }

        if (showManualAddPrompt) {
            AlertDialog(
                onDismissRequest = { showManualAddPrompt = false },
                title = { Text("ADD A QUESTION MANUALLY?") },
                text = { Text("If a question was missed during selection, you can add it now using the Maths Question Editor before creating the Word file.") },
                confirmButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = {
                            showManualAddPrompt = false
                            exportWord.launch("Ali-Test-Series-Class-$grade.docx")
                        }) { Text("NO, EXPORT") }
                        Button(onClick = {
                            manualQuestionText = TextFieldValue("")
                            manualQuestionKind = "SHORT"
                            manualQuestionMarks = "2"
                            manualQuestionNumber = suggestedNumber("SHORT", selected)
                            manualQuestionChapter = ""
                            showManualAddPrompt = false
                            showManualQuestionEditor = true
                        }) { Text("YES, ADD QUESTION") }
                    }
                }
            )
        }

        if (showManualQuestionEditor) {
            AlertDialog(
                onDismissRequest = { showManualQuestionEditor = false },
                title = { Text("MATHS QUESTION EDITOR") },
                text = {
                    Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Write the missed question. Use the maths symbol pad for common mathematical notation; the question remains editable in Word.", style = MaterialTheme.typography.bodySmall)
                        OutlinedTextField(manualQuestionText, { manualQuestionText = it }, modifier = Modifier.fillMaxWidth(), minLines = 5, label = { Text("Question") })
                        Text("MATHS EQUATION TEMPLATES", style = MaterialTheme.typography.labelLarge)
                        val templates = listOf(
                            "FRACTION" to ("(" to ")/( )"),
                            "MATRIX 2×2" to ("matrix(" to " , ; , )"),
                            "ROOT" to ("√(" to ")"),
                            "CUBE ROOT" to ("∛(" to ")"),
                            "POWER" to ("x^(" to ")"),
                            "SUBSCRIPT" to ("x_(" to ")"),
                            "ABSOLUTE" to ("|" to "|"),
                            "LOG" to ("log(" to ")"),
                            "SIN" to ("sin(" to ")"),
                            "COS" to ("cos(" to ")"),
                            "TAN" to ("tan(" to ")")
                        )
                        templates.chunked(2).forEach { line ->
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                                line.forEach { (label, parts) ->
                                    OutlinedButton(onClick = { insertMathTemplate(parts.first, parts.second) }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(4.dp)) { Text(label) }
                                }
                                if (line.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                        Text("MATHS SYMBOL PAD", style = MaterialTheme.typography.labelLarge)
                        val symbols = listOf("√", "∛", "π", "θ", "α", "β", "γ", "Δ", "∞", "≤", "≥", "≠", "≈", "∝", "±", "×", "÷", "∑", "∫", "∂", "∠", "°", "²", "³", "⁴", "½", "¼", "⅓", "⅔", "∈", "∉", "∩", "∪", "⊥", "∥", "∴", "∵", "↔", "′", "″", "(", ")", "^", "_", "=", "→")
                        symbols.chunked(4).forEach { line ->
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                                line.forEach { symbol ->
                                    OutlinedButton(onClick = { insertMathToken(symbol) }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(2.dp)) { Text(symbol) }
                                }
                                repeat(4 - line.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("MCQ", "SHORT", "LONG").forEach { kind ->
                                FilterChip(selected = manualQuestionKind == kind, onClick = {
                                    manualQuestionKind = kind
                                    manualQuestionMarks = when (kind) { "MCQ" -> "1"; "SHORT" -> "2"; else -> "4" }
                                    manualQuestionNumber = suggestedNumber(kind, selected)
                                }, label = { Text(kind) })
                            }
                        }
                        OutlinedTextField(manualQuestionNumber, { manualQuestionNumber = it.take(16) }, label = { Text("Question number") }, singleLine = true)
                        OutlinedTextField(manualQuestionMarks, { manualQuestionMarks = it.filter(Char::isDigit).take(2) }, label = { Text("Marks") }, singleLine = true)
                        Box {
                            OutlinedButton(onClick = { manualChapterMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                                Text(if (manualQuestionChapter.isBlank()) "SELECT CHAPTER (REQUIRED FOR BOARD)" else "CHAPTER: $manualQuestionChapter")
                            }
                            DropdownMenu(expanded = manualChapterMenuExpanded, onDismissRequest = { manualChapterMenuExpanded = false }) {
                                selectedChapters.sortedBy { it.removePrefix("CH").toIntOrNull() ?: 0 }.forEach { chapterId ->
                                    DropdownMenuItem(text = { Text(chapterId) }, onClick = {
                                        manualQuestionChapter = chapterId
                                        manualChapterMenuExpanded = false
                                    })
                                }
                                if (paperMode == "CUSTOM" && selectedChapters.size == (if (grade == 9) 13 else 12)) {
                                    DropdownMenuItem(text = { Text("No chapter (Custom only)") }, onClick = {
                                        manualQuestionChapter = ""
                                        manualChapterMenuExpanded = false
                                    })
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    val manualMarks = manualQuestionMarks.toIntOrNull()
                    val manualAllChaptersSelected = selectedChapters.size == (if (grade == 9) 13 else 12)
                    val manualChapterInScope = (paperMode == "CUSTOM" && manualAllChaptersSelected) ||
                        (manualQuestionChapter in selectedChapters && PunjabBoardPattern.isValidChapter(grade, manualQuestionChapter))
                    val boardManualMarksValid = paperMode == "CUSTOM" || (
                        manualMarks == when (manualQuestionKind) { "MCQ" -> 1; "SHORT" -> 2; else -> 4 } &&
                            PunjabBoardPattern.isValidChapter(grade, manualQuestionChapter)
                    )
                    Button(enabled = manualQuestionText.text.isNotBlank() && manualQuestionNumber.isNotBlank() &&
                        (manualMarks ?: 0) in 1..100 && boardManualMarksValid && manualChapterInScope,
                        onClick = {
                            val number = manualQuestionNumber.trim()
                            if (selected.any { it.number.trim().equals(number, ignoreCase = true) }) {
                                notice = "That question number already exists. Choose a unique number."
                            } else {
                                selected = selected + SelectedQuestion(
                                    id = nextId++, number = number, text = manualQuestionText.text.trim(),
                                    kind = manualQuestionKind, marks = manualQuestionMarks.toInt(),
                                    sourcePage = 0, imageFile = null, chapterId = manualQuestionChapter.trim(),
                                    sectionId = number.substringBefore("(").ifBlank { "CUSTOM" }
                                )
                                showManualQuestionEditor = false
                                showQuestions = true
                                notice = "Manual question added as editable text."
                            }
                        }) { Text("ADD TO PAPER") }
                },
                dismissButton = { TextButton(onClick = { showManualQuestionEditor = false }) { Text("CANCEL") } }
            )
        }

        if (showGradeSwitchConfirm) {
            AlertDialog(
                onDismissRequest = { showGradeSwitchConfirm = false },
                title = { Text("START A NEW CLASS PAPER?") },
                text = { Text("Switching classes will clear the current paper questions so Class 9 and Class 10 questions are not mixed. Your imported textbook files remain saved.") },
                confirmButton = {
                    Button(onClick = {
                        selected.forEach { it.imageFile?.let { path -> File(path).delete() } }
                        selected = emptyList()
                        nextId = 1L
                        grade = pendingGrade
                        selectedChapters = (1..if (pendingGrade == 9) 13 else 12).map { "CH$it" }.toSet()
                        showGradeSwitchConfirm = false
                    }) { Text("SWITCH CLASS") }
                },
                dismissButton = {
                    TextButton(onClick = { showGradeSwitchConfirm = false }) { Text("CANCEL") }
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
