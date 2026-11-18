package com.nitroza.mathpapermaker

import android.content.Context
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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import com.nitroza.mathpapermaker.data.PdfTextDocumentCache
import com.nitroza.mathpapermaker.data.TextbookQuestionBank
import com.nitroza.mathpapermaker.data.TextbookQuestionBankMapper
import com.nitroza.mathpapermaker.data.CuratedQuestionBankRepository
import com.nitroza.mathpapermaker.data.TextbookCandidateEntity
import com.nitroza.mathpapermaker.paper.PaperQuestion
import com.nitroza.mathpapermaker.paper.AutoPaperGenerator
import com.nitroza.mathpapermaker.paper.PunjabBoardPattern
import com.nitroza.mathpapermaker.paper.PaperDraftStore
import com.nitroza.mathpapermaker.paper.PaperDraftSnapshot
import com.nitroza.mathpapermaker.paper.StoredPaperQuestion
import com.nitroza.mathpapermaker.ui.MathPaperMakerTheme
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
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

    override fun onDestroy() {
        PdfTextDocumentCache.close()
        super.onDestroy()
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
        var savedQuestionBankCount by remember { mutableIntStateOf(0) }
        var showBankManager by remember { mutableStateOf(false) }
        var savedBankQuestions by remember { mutableStateOf<List<TextbookCandidateEntity>>(emptyList()) }
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
        var cropSelectionMode by remember { mutableStateOf(false) }
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
        var isRegionTextExtracting by remember { mutableStateOf(false) }
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
        var workflowMode by remember { mutableStateOf("CHOICE") }
        var showAutoSetup by remember { mutableStateOf(false) }
        var includeCurrentQuestions by remember { mutableStateOf(true) }
        var autoProgress by remember { mutableFloatStateOf(0f) }
        var autoStage by remember { mutableStateOf("Preparing your paper…") }
        var autoFailureMessage by remember { mutableStateOf("") }
        val animatedAutoProgress by animateFloatAsState(targetValue = autoProgress, animationSpec = tween(durationMillis = 650), label = "auto-paper-progress")

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

        LaunchedEffect(grade, pdfName) {
            savedQuestionBankCount = if (pdfName.isBlank()) 0 else withContext(Dispatchers.IO) {
                runCatching {
                    TextbookQuestionBank.approvedCount(
                        applicationContext,
                        "CLASS$grade|$pdfName"
                    )
                }.getOrDefault(0)
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
                val bankReferencedImages = runCatching {
                    TextbookQuestionBank.referencedImagePaths(applicationContext)
                }.getOrDefault(emptySet())
                val referencedImages = (selected.mapNotNull { it.imageFile } + bankReferencedImages)
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
                                    // PdfRenderer may leave transparent page areas untouched.
                                    // Initialize to paper white so they never appear black in dark UI.
                                    bitmap.eraseColor(android.graphics.Color.WHITE)
                                    bitmap.setHasAlpha(false)
                                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
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
                        PdfTextDocumentCache.withDocument(applicationContext, file) { document ->
                            PDFTextStripper().apply {
                                sortByPosition = true
                                startPage = pageIndex + 1
                                endPage = pageIndex + 1
                            }.getText(document)
                        }
                    }
                }
                extraction.onSuccess { rawPageText ->
                    val pageText = removePctbWatermarkLines(rawPageText)
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
                        OfflinePdfOcr.readPage(
                            file,
                            pageIndex,
                            cropRect?.let { box ->
                                android.graphics.RectF(box.left, box.top, box.right, box.bottom)
                            }
                        )
                    }
                } finally {
                    isOcrRunning = false
                }
                extraction.onSuccess { rawRecognizedText ->
                    val recognizedText = removePctbWatermarkLines(rawRecognizedText)
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

        fun autoGenerateFromCollectedQuestions() {
            workflowMode = "AUTO"
            autoProgress = 0.08f
            autoFailureMessage = ""
            autoStage = "Loading approved textbook questions…"
            if (paperMode != "BOARD") {
                workflowMode = "CHOICE"
                autoProgress = 0f
                notice = "Automatic generation always uses the Punjab Board pattern."
                return
            }
            lifecycleScope.launch {
                notice = "Loading verified research questions, saved textbook questions and current selections..."
                val bookKey = if (pdfName.isBlank()) "" else "CLASS$grade|$pdfName"
                autoProgress = 0.25f
                val savedBank = withContext(Dispatchers.IO) {
                    if (bookKey.isBlank()) Result.success(emptyList())
                    else runCatching { TextbookQuestionBank.approvedForBook(applicationContext, bookKey) }
                }
                val curatedBank = withContext(Dispatchers.IO) {
                    runCatching {
                        CuratedQuestionBankRepository.approvedPunjabMaths(
                            applicationContext, grade, selectedChapters
                        )
                    }
                }
                curatedBank.onFailure { error ->
                    notice = "Could not load the verified research bank: " + (error.message ?: "unknown error")
                }
                savedBank.onFailure { error ->
                    notice = "Could not load the offline question bank: " + (error.message ?: "unknown error")
                }
                autoProgress = 0.48f
                autoStage = "Preparing selected chapters…"
                val savedQuestions = TextbookQuestionBankMapper.toPaperQuestions(
                    savedBank.getOrNull().orEmpty(),
                    selectedChapters
                ).map { item ->
                    SelectedQuestion(
                        id = nextId++,
                        number = item.number,
                        text = item.text,
                        kind = item.kind,
                        marks = item.marks,
                        sourcePage = item.sourcePage,
                        imageFile = item.imageFile,
                        chapterId = item.chapterId,
                        sectionId = item.sectionId
                    )
                }
                val curatedQuestions = curatedBank.getOrNull().orEmpty().map { item ->
                    SelectedQuestion(
                        id = nextId++,
                        number = item.number,
                        text = item.text,
                        kind = item.kind,
                        marks = item.marks,
                        sourcePage = item.sourcePage,
                        imageFile = item.imageFile,
                        chapterId = item.chapterId,
                        sectionId = item.sectionId
                    )
                }
                autoProgress = 0.68f
                autoStage = "Building Punjab Board paper pattern…"
                val sourceItems = ((if (includeCurrentQuestions) selected else emptyList()) + savedQuestions + curatedQuestions)
                    .filter { it.chapterId in selectedChapters }
                    .distinctBy {
                        listOf(it.kind.uppercase(), it.chapterId, it.sourcePage, it.text, it.imageFile).joinToString("|")
                    }
                if (sourceItems.isEmpty()) {
                    autoProgress = 1f
                    autoStage = "Question bank is empty for these chapters"
                    autoFailureMessage = "No approved questions were found in the selected chapters. Add questions from the PDF in Manual mode, mark important questions, then return here. Auto cannot create questions that are not in your textbook bank."
                    notice = autoFailureMessage
                    return@launch
                }
                val sourcePool = sourceItems.map {
                    PaperQuestion(it.number, it.text, it.kind, it.marks, it.sourcePage, it.imageFile, it.chapterId, it.sectionId)
                }
                autoProgress = 0.86f
                when (val generated = AutoPaperGenerator.generate(grade, selectedChapters, sourcePool)) {
                    is AutoPaperGenerator.Result.Success -> {
                        val rebuilt = generated.questions.mapNotNull { generatedQuestion ->
                            sourceItems.firstOrNull { candidate ->
                                candidate.kind.equals(generatedQuestion.kind, true) &&
                                    candidate.chapterId == generatedQuestion.chapterId &&
                                    candidate.sourcePage == generatedQuestion.sourcePage &&
                                    candidate.text == generatedQuestion.text &&
                                    candidate.imageFile == generatedQuestion.imageFile
                            }?.copy(
                                id = nextId++,
                                number = generatedQuestion.number,
                                kind = generatedQuestion.kind,
                                marks = generatedQuestion.marks,
                                sectionId = generatedQuestion.sectionId
                            )
                        }
                        if (rebuilt.size == generated.questions.size) {
                            selected = rebuilt
                            autoProgress = 1f
                            autoStage = "Paper ready. Opening review…"
                            showQuestions = true
                            notice = "Board paper generated using " + sourceItems.size +
                                " available question(s): saved bank + current draft. Review, edit, replace, reorder or remove before export."
                        } else {
                            autoProgress = 1f
                            autoStage = "Paper could not be assembled"
                            autoFailureMessage = "Some generated questions could not be matched to their original textbook entries. Your current selection has been kept unchanged. Please try again or use Manual mode."
                            notice = autoFailureMessage
                        }
                    }
                    is AutoPaperGenerator.Result.NeedsMoreQuestions -> {
                        autoProgress = 1f
                        autoStage = "More textbook questions are needed"
                        autoFailureMessage = generated.message + " Add more approved textbook questions from the selected chapters in Manual mode, then try Auto again."
                        notice = autoFailureMessage
                    }
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
                    Text("Prepare a paper. Build your question pool, then generate or select questions.", style = MaterialTheme.typography.headlineSmall)
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
                    Text("Manual selection + automatic board-pattern selection · Offline · No AI question invention", style = MaterialTheme.typography.labelSmall)
                }
            } else if (workflowMode == "AUTO") {
                Column(Modifier.fillMaxSize().padding(padding).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    CircularProgressIndicator(progress = { animatedAutoProgress.coerceIn(0f, 1f) }, modifier = Modifier.size(112.dp), strokeWidth = 9.dp)
                    Spacer(Modifier.height(24.dp))
                    Text("PLEASE BE PATIENT…", style = MaterialTheme.typography.titleLarge)
                    Text(autoStage, style = MaterialTheme.typography.bodyMedium)
                    Text((animatedAutoProgress * 100).toInt().toString() + "%", style = MaterialTheme.typography.headlineMedium)
                    if (autoFailureMessage.isNotBlank()) {
                        Spacer(Modifier.height(12.dp))
                        Text(autoFailureMessage, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { showAutoSetup = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("CHANGE CHAPTERS / TRY AGAIN")
                        }
                        OutlinedButton(onClick = {
                            workflowMode = "MANUAL"
                            autoFailureMessage = ""
                        }, modifier = Modifier.fillMaxWidth()) {
                            Text("GO TO MANUAL MODE")
                        }
                    }
                }
            } else if (workflowMode == "CHOICE") {
                Column(Modifier.fillMaxSize().padding(padding).padding(22.dp), verticalArrangement = Arrangement.spacedBy(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(12.dp))
                    Text("HOW WOULD YOU LIKE TO CREATE YOUR PAPER?", style = MaterialTheme.typography.headlineSmall)
                    Text("Class $grade · $pdfName", style = MaterialTheme.typography.bodySmall)
                    Card(onClick = { showAutoSetup = true }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("⚡ AUTO PAPER", style = MaterialTheme.typography.titleLarge)
                            Text("Create a Punjab Board-pattern paper. Choose chapters and include important questions.", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Card(onClick = { workflowMode = "MANUAL" }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("✍ MANUAL PAPER", style = MaterialTheme.typography.titleLarge)
                            Text("Select PDF text, crop scanned questions, write your own questions and arrange the paper.", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    TextButton(onClick = { if (!pdfIsPersistent) pdfFile?.delete(); pdfFile = null; pdfName = ""; pageCount = 0 }) { Text("CHANGE PDF") }
                }
            } else {
                Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            Text("CLASS $grade MATHEMATICS", style = MaterialTheme.typography.titleSmall)
                            Text(pdfName, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        }
                        TextButton(onClick = { workflowMode = "CHOICE" }) { Text("BACK TO MODES") }
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
                            cropSelectionMode = false
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        if (cropSelectionMode) {
                            TextButton(onClick = { cropSelectionMode = false }) { Text("DONE SELECTING") }
                        } else {
                            TextButton(
                                enabled = pageBitmap != null,
                                onClick = { cropRect = null; cropSelectionMode = true }
                            ) { Text("START CROP SELECTION") }
                        }
                    }
                    Box(
                        Modifier.weight(1f).fillMaxWidth()
                            .verticalScroll(rememberScrollState(), enabled = !cropSelectionMode),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        pageBitmap?.let { bitmap ->
                            val latestCropRect = rememberUpdatedState(cropRect)
                            Box(
                                Modifier.fillMaxWidth()
                                    .aspectRatio(bitmap.width.toFloat() / bitmap.height.toFloat())
                                    .pointerInput(bitmap, cropSelectionMode) {
                                        if (cropSelectionMode) {
                                            var dragStart = Offset.Zero
                                            var originalRect: Rect? = null
                                            var dragMode = "new"
                                            detectDragGestures(
                                                onDragStart = { point ->
                                                    dragStart = point
                                                    originalRect = latestCropRect.value
                                                    val current = latestCropRect.value
                                                    val handleRadius = 30.dp.toPx()
                                                    if (current == null) {
                                                        dragMode = "new"
                                                    } else {
                                                        val l = current.left * size.width
                                                        val t = current.top * size.height
                                                        val r = current.right * size.width
                                                        val b = current.bottom * size.height
                                                        val handles = listOf(
                                                            "tl" to Offset(l, t), "tm" to Offset((l+r)/2f, t), "tr" to Offset(r, t),
                                                            "mr" to Offset(r, (t+b)/2f), "br" to Offset(r, b), "bm" to Offset((l+r)/2f, b),
                                                            "bl" to Offset(l, b), "ml" to Offset(l, (t+b)/2f)
                                                        )
                                                        val nearest = handles.minByOrNull { (_, p) ->
                                                            val dx = p.x - point.x
                                                            val dy = p.y - point.y
                                                            dx * dx + dy * dy
                                                        }
                                                        val dx = (nearest?.second?.x ?: 0f) - point.x
                                                        val dy = (nearest?.second?.y ?: 0f) - point.y
                                                        dragMode = when {
                                                            nearest != null && dx * dx + dy * dy <= handleRadius * handleRadius -> nearest.first
                                                            point.x in l..r && point.y in t..b -> "move"
                                                            else -> "new"
                                                        }
                                                        if (dragMode == "new") originalRect = null
                                                    }
                                                },
                                                onDragEnd = { originalRect = null },
                                                onDragCancel = { originalRect = null }
                                            ) { change, dragAmount ->
                                                val dx = (change.position.x - dragStart.x) / size.width
                                                val dy = (change.position.y - dragStart.y) / size.height
                                                val base = originalRect
                                                val updated = when {
                                                    dragMode == "new" -> {
                                                        val left = (min(dragStart.x, change.position.x) / size.width).coerceIn(0f, 1f)
                                                        val top = (min(dragStart.y, change.position.y) / size.height).coerceIn(0f, 1f)
                                                        val right = (max(dragStart.x, change.position.x) / size.width).coerceIn(0f, 1f)
                                                        val bottom = (max(dragStart.y, change.position.y) / size.height).coerceIn(0f, 1f)
                                                        Rect(left, top, right, bottom)
                                                    }
                                                    base == null -> null
                                                    dragMode == "move" -> {
                                                        val left = (base.left + dx).coerceIn(0f, 1f - base.width)
                                                        val top = (base.top + dy).coerceIn(0f, 1f - base.height)
                                                        Rect(left, top, left + base.width, top + base.height)
                                                    }
                                                    else -> {
                                                        var left = base.left
                                                        var top = base.top
                                                        var right = base.right
                                                        var bottom = base.bottom
                                                        if (dragMode in listOf("tl", "ml", "bl")) left = (left + dx).coerceIn(0f, right - 0.015f)
                                                        if (dragMode in listOf("tr", "mr", "br")) right = (right + dx).coerceIn(left + 0.015f, 1f)
                                                        if (dragMode in listOf("tl", "tm", "tr")) top = (top + dy).coerceIn(0f, bottom - 0.015f)
                                                        if (dragMode in listOf("bl", "bm", "br")) bottom = (bottom + dy).coerceIn(top + 0.015f, 1f)
                                                        Rect(left, top, right, bottom)
                                                    }
                                                }
                                                if (updated != null) cropRect = updated
                                                change.consume()
                                            }
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
                                            val handleRadius = 7.dp.toPx()
                                            val handleStroke = 2.dp.toPx()
                                            val l = r.left * size.width
                                            val t = r.top * size.height
                                            val rr = r.right * size.width
                                            val b = r.bottom * size.height
                                            val handles = listOf(
                                                Offset(l, t), Offset((l + rr) / 2f, t), Offset(rr, t),
                                                Offset(rr, (t + b) / 2f), Offset(rr, b), Offset((l + rr) / 2f, b),
                                                Offset(l, b), Offset(l, (t + b) / 2f)
                                            )
                                            handles.forEach { point ->
                                                drawCircle(Color.White, radius = handleRadius, center = point)
                                                drawCircle(Color.Red, radius = handleRadius, center = point, style = Stroke(width = handleStroke))
                                            }
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
                                        PdfTextDocumentCache.withDocument(applicationContext, file) { document ->
                                            val extracted = PDFTextStripper().apply {
                                                sortByPosition = true
                                                startPage = pageIndex + 1
                                                endPage = pageIndex + 1
                                            }.getText(document)
                                            require(extracted.length <= 100_000) {
                                                "This page contains too much extractable text. Select a smaller page region or use the scanned-question crop."
                                            }
                                            removePctbWatermarkLines(extracted)
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
                            enabled = cropRect != null && pageBitmap != null && !isRegionTextExtracting,
                            onClick = {
                                if (isRegionTextExtracting) return@Button
                                val rect = cropRect ?: return@Button
                                val file = pdfFile ?: return@Button
                                val pageNumber = pageIndex + 1
                                isRegionTextExtracting = true
                                notice = "Reading the selected PDF area… Please wait; the app is extracting editable text, not using OCR."
                                lifecycleScope.launch {
                                    val extraction = try {
                                        withContext(Dispatchers.IO) {
                                            runCatchingCancellable {
                                                extractPdfTextInRegion(applicationContext, file, pageNumber, rect)
                                            }
                                        }
                                    } finally {
                                        isRegionTextExtracting = false
                                    }
                                    extraction.onSuccess { text ->
                                        val cleaned = removePctbWatermarkLines(text)
                                        if (cleaned.isBlank()) {
                                            notice = "No selectable text was found in this area. It may be a scanned/image-only page; use OCR SCANNED PAGE or ADD SCANNED IMAGE CROP."
                                        } else {
                                            questionText = TextFieldValue(cleaned, androidx.compose.ui.text.TextRange(0, cleaned.length))
                                            pendingImageFile = null
                                            questionNumber = suggestedNumber(questionKind, selected)
                                            questionSection = questionNumber.substringBefore("(")
                                            questionChapter = chapterForPage(pageNumber).orEmpty()
                                            cropSelectionMode = false
                                            showQuestionEditor = true
                                            notice = "Text extracted in reading order. Review it before adding the question."
                                        }
                                    }.onFailure { error ->
                                        notice = "Could not extract text from selected area: ${error.message ?: "unknown error"}"
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            if (isRegionTextExtracting) {
                                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(6.dp))
                                Text("EXTRACTING…")
                            } else Text("ADD SELECTED TEXT")
                        }
                    }
                    OutlinedButton(
                        enabled = cropRect != null && pageBitmap != null,
                        onClick = {
                            val rect = cropRect ?: return@OutlinedButton
                            val source = pageBitmap ?: return@OutlinedButton
                            val sourcePage = pageIndex + 1
                            val left = (rect.left * source.width).toInt().coerceIn(0, source.width - 1)
                            val top = (rect.top * source.height).toInt().coerceIn(0, source.height - 1)
                            val right = (rect.right * source.width).toInt().coerceIn(left + 1, source.width)
                            val bottom = (rect.bottom * source.height).toInt().coerceIn(top + 1, source.height)
                            if (right - left < 8 || bottom - top < 8) {
                                notice = "The crop is too small. Draw a larger box around the complete question."
                            } else {
                                val cropBitmap = Bitmap.createBitmap(source, left, top, right - left, bottom - top)
                                lifecycleScope.launch {
                                    val savedCrop = withContext(Dispatchers.IO) {
                                        runCatchingCancellable {
                                            val directory = File(filesDir, "question_images").apply { mkdirs() }
                                            val target = File(directory, "ali_question_${System.currentTimeMillis()}_${nextId}.png")
                                            try {
                                                FileOutputStream(target).use { output ->
                                                    check(cropBitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                                                        "The scanned crop could not be encoded."
                                                    }
                                                }
                                                require(target.isFile && target.length() > 0L) {
                                                    "The scanned crop file is empty."
                                                }
                                                target.absolutePath
                                            } catch (error: Throwable) {
                                                target.delete()
                                                throw error
                                            } finally {
                                                cropBitmap.recycle()
                                            }
                                        }
                                    }
                                    savedCrop.onSuccess { imagePath ->
                                        questionText = TextFieldValue("")
                                        pendingImageFile = imagePath
                                        questionNumber = suggestedNumber(questionKind, selected)
                                        questionSection = questionNumber.substringBefore("(")
                                        questionChapter = chapterForPage(sourcePage).orEmpty()
                                        cropSelectionMode = false
                                        showQuestionEditor = true
                                        notice = "Scanned crop saved locally. Assign its chapter and question details, then add it."
                                    }.onFailure { error ->
                                        if (!cropBitmap.isRecycled) cropBitmap.recycle()
                                        notice = "Could not save scanned crop: ${error.message ?: "unknown error"}"
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("ADD SCANNED IMAGE CROP") }
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
                            if (pendingImageFile == null) "Long-press and drag the selection handles over the exact question. Only highlighted text is added; the whole PDF page is never added by default."
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
                    TextButton(enabled = (selectedQuestionText(questionText).isNotBlank() || pendingImageFile != null) &&
                        normalizedNumber.isNotBlank() && !duplicateNumber &&
                        PunjabBoardPattern.isValidChapter(grade, questionChapter) && questionChapter in selectedChapters &&
                        questionMarks.toIntOrNull() == requiredMarks && !atKindLimit, onClick = {
                        val confirmedText = selectedQuestionText(questionText)
                        val confirmedImage = pendingImageFile
                        val confirmedNumber = questionNumber.trim()
                        val confirmedType = questionKind
                        val confirmedMarks = questionMarks.toInt()
                        val confirmedChapter = questionChapter
                        val confirmedPage = pageIndex + 1
                        val bankBookKey = "CLASS$grade|$pdfName"
                        selected = selected + SelectedQuestion(
                            nextId++, confirmedNumber, confirmedText, confirmedType,
                            confirmedMarks, confirmedPage, confirmedImage, confirmedChapter, questionSection
                        )
                        showQuestionEditor = false
                        pendingImageFile = null
                        cropRect = null
                        notice = "Question added. Saving your teacher-confirmed question to the offline textbook bank..."
                        lifecycleScope.launch {
                            val saved = withContext(Dispatchers.IO) {
                                runCatching {
                                    TextbookQuestionBank.saveApprovedQuestion(
                                        applicationContext, bankBookKey, confirmedChapter, confirmedPage,
                                        confirmedNumber, confirmedText, confirmedType, confirmedMarks, confirmedImage
                                    )
                                }
                            }
                            savedQuestionBankCount = withContext(Dispatchers.IO) {
                                runCatching {
                                    TextbookQuestionBank.approvedCount(applicationContext, bankBookKey)
                                }.getOrDefault(savedQuestionBankCount)
                            }
                            notice = saved.fold(
                                onSuccess = { inserted ->
                                    if (inserted) "Question added and saved to the offline textbook bank."
                                    else "Question added. It was already present in the offline textbook bank."
                                },
                                onFailure = { error ->
                                    "Question added to this paper, but bank saving failed: " + (error.message ?: "unknown error")
                                }
                            )
                        }
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
                        val approvedCandidates = checkedDetectedQuestions.sorted().map { detectedQuestions[it] }
                        val bankBookKey = "CLASS$grade|$pdfName"
                        val bankChapter = detectedQuestionChapter
                        val bankPage = detectedQuestionPage
                        selected = newQuestions
                        showDetectedQuestions = false
                        showQuestions = true
                        notice = "Added $addedCount checked question(s). Saving teacher-approved questions to the offline textbook bank..."
                        lifecycleScope.launch {
                            if (!PunjabBoardPattern.isValidChapter(grade, bankChapter)) {
                                notice = "Added $addedCount question(s). Assign a valid chapter before saving them to the reusable textbook bank."
                                return@launch
                            }
                            val saved = withContext(Dispatchers.IO) {
                                runCatching {
                                    TextbookQuestionBank.saveApprovedCandidates(
                                        applicationContext, bankBookKey, bankChapter, bankPage, approvedCandidates,
                                        detectedQuestionKind, marks
                                    )
                                }
                            }
                            savedQuestionBankCount = withContext(Dispatchers.IO) {
                                runCatching {
                                    TextbookQuestionBank.approvedCount(applicationContext, bankBookKey)
                                }.getOrDefault(savedQuestionBankCount)
                            }
                            notice = saved.fold(
                                onSuccess = { count -> "Added $addedCount question(s); $count new teacher-approved question(s) saved in the offline textbook bank." },
                                onFailure = { error -> "Added $addedCount question(s), but the offline bank save failed: " + (error.message ?: "unknown error") }
                            )
                        }
                    }) { Text("ADD CHECKED QUESTIONS") }
                },
                dismissButton = { TextButton(onClick = { showDetectedQuestions = false }) { Text("CANCEL") } }
            )
        }

        if (showAutoSetup) {
            AlertDialog(
                onDismissRequest = { showAutoSetup = false },
                title = { Text("SET UP YOUR BOARD PAPER") },
                text = {
                    Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
                        Text("Select units / chapters for this paper.")
                        Row {
                            TextButton(onClick = { selectedChapters = (1..if (grade == 9) 13 else 12).map { "CH$it" }.toSet() }) { Text("SELECT ALL") }
                            TextButton(onClick = { selectedChapters = emptySet() }) { Text("CLEAR") }
                        }
                        (1..if (grade == 9) 13 else 12).forEach { n ->
                            val chapter = "CH$n"
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = chapter in selectedChapters, onCheckedChange = { checked -> selectedChapters = if (checked) selectedChapters + chapter else selectedChapters - chapter })
                                Text("Chapter $n")
                            }
                        }
                        Divider()
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = includeCurrentQuestions, onCheckedChange = { includeCurrentQuestions = it })
                            Text("Include questions I have already selected as important")
                        }
                        Text("The app builds the Punjab Board paper structure (15 MCQs, 27 short questions and 5 two-part long questions) using only the chapters you select. Full-syllabus papers also follow the official chapter-wise allocation. Questions must already be in your approved offline bank or current selection; missing question types are reported clearly.", style = MaterialTheme.typography.bodySmall)
                    }
                },
                confirmButton = {
                    Button(enabled = selectedChapters.isNotEmpty(), onClick = { showAutoSetup = false; autoGenerateFromCollectedQuestions() }) { Text("GENERATE BOARD PAPER") }
                },
                dismissButton = { TextButton(onClick = { showAutoSetup = false }) { Text("CANCEL") } }
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
                        Text("AUTOMATIC PAPER GENERATION", style = MaterialTheme.typography.labelLarge)
                        Text(
                            "The generator uses teacher-approved questions saved on this device plus the current draft. It never invents question text.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            "OFFLINE BANK: $savedQuestionBankCount approved question(s) for this textbook",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        OutlinedButton(
                            onClick = {
                                val bookKey = "CLASS$grade|$pdfName"
                                lifecycleScope.launch {
                                    val loaded = withContext(Dispatchers.IO) {
                                        runCatching { TextbookQuestionBank.approvedForBook(applicationContext, bookKey) }
                                    }
                                    loaded.onSuccess {
                                        savedBankQuestions = it
                                        showBankManager = true
                                    }.onFailure { error ->
                                        notice = "Could not open the offline bank: ${error.message ?: "unknown error"}"
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("MANAGE SAVED TEXTBOOK QUESTIONS") }
                        Button(
                            onClick = { autoGenerateFromCollectedQuestions() },
                            enabled = paperMode == "BOARD" && (selected.isNotEmpty() || savedQuestionBankCount > 0),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("AUTO-GENERATE FROM TEXTBOOK QUESTION BANK") }
                        Text("CHAPTER SCOPE", style = MaterialTheme.typography.labelLarge)
                        OutlinedButton(onClick = { showChapterPicker = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("INCLUDE CHAPTERS: ${selectedChapters.size} / ${if (grade == 9) 13 else 12}")
                        }
                        if (!allChaptersSelected && paperMode == "BOARD") {
                            Text("Selected-unit Board paper: the same 15 MCQs, 27 short questions and 10 long-question parts are drawn only from your chosen chapters. Full-syllabus selection uses the official chapter-wise allocation.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
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
                                val manualText = manualQuestionText.text.trim()
                                val manualType = manualQuestionKind
                                val manualMarksValue = manualQuestionMarks.toInt()
                                val manualChapter = manualQuestionChapter.trim()
                                val manualBookKey = "CLASS$grade|$pdfName"
                                selected = selected + SelectedQuestion(
                                    id = nextId++, number = number, text = manualText,
                                    kind = manualType, marks = manualMarksValue,
                                    sourcePage = 0, imageFile = null, chapterId = manualChapter,
                                    sectionId = number.substringBefore("(").ifBlank { "CUSTOM" }
                                )
                                showManualQuestionEditor = false
                                showQuestions = true
                                if (manualBookKey.isNotBlank() && PunjabBoardPattern.isValidChapter(grade, manualChapter)) {
                                    notice = "Manual question added. Saving it to the offline textbook bank..."
                                    lifecycleScope.launch {
                                        val saved = withContext(Dispatchers.IO) {
                                            runCatching {
                                                TextbookQuestionBank.saveApprovedQuestion(
                                                    applicationContext, manualBookKey, manualChapter, 0,
                                                    number, manualText, manualType, manualMarksValue
                                                )
                                            }
                                        }
                                        savedQuestionBankCount = withContext(Dispatchers.IO) {
                                            runCatching {
                                                TextbookQuestionBank.approvedCount(applicationContext, manualBookKey)
                                            }.getOrDefault(savedQuestionBankCount)
                                        }
                                        notice = saved.fold(
                                            onSuccess = { inserted ->
                                                if (inserted) "Manual question added and saved to the offline textbook bank."
                                                else "Manual question added. It was already in the offline textbook bank."
                                            },
                                            onFailure = { error ->
                                                "Manual question added to this paper, but bank saving failed: ${error.message ?: "unknown error"}"
                                            }
                                        )
                                    }
                                } else {
                                    notice = "Manual question added as editable text. Assign a valid chapter while reviewing if you want it reusable in the textbook bank."
                                }
                            }
                        }) { Text("ADD TO PAPER") }
                },
                dismissButton = { TextButton(onClick = { showManualQuestionEditor = false }) { Text("CANCEL") } }
            )
        }

        if (showBankManager) {
            AlertDialog(
                onDismissRequest = { showBankManager = false },
                title = { Text("OFFLINE TEXTBOOK BANK · ${savedBankQuestions.size}") },
                text = {
                    if (savedBankQuestions.isEmpty()) {
                        Text("No approved questions are saved for this textbook yet.")
                    } else {
                        LazyColumn(
                            Modifier.heightIn(max = 520.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            itemsIndexed(savedBankQuestions, key = { _, item -> item.candidateId }) { _, item ->
                                Card {
                                    Column(
                                        Modifier.fillMaxWidth().padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Text("${item.questionNumber} · ${item.chapterId} · ${item.questionType} · ${item.marks} marks",
                                            style = MaterialTheme.typography.labelLarge)
                                        Text(
                                            item.questionText.ifBlank { "Scanned image question · page ${item.sourcePage}" },
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 4
                                        )
                                        Text("Source page: ${item.sourcePage} · ${item.exerciseId}",
                                            style = MaterialTheme.typography.labelSmall)
                                        TextButton(onClick = {
                                            lifecycleScope.launch {
                                                val removed = withContext(Dispatchers.IO) {
                                                    runCatching {
                                                        TextbookQuestionBank.removeQuestion(
                                                            applicationContext, item.candidateId
                                                        )
                                                    }
                                                }
                                                removed.onSuccess { didRemove ->
                                                    if (didRemove) {
                                                        savedBankQuestions = savedBankQuestions.filterNot {
                                                            it.candidateId == item.candidateId
                                                        }
                                                        savedQuestionBankCount = (savedQuestionBankCount - 1).coerceAtLeast(0)
                                                        notice = "Question removed from the reusable offline bank."
                                                    }
                                                }.onFailure { error ->
                                                    notice = "Could not remove bank question: ${error.message ?: "unknown error"}"
                                                }
                                            }
                                        }) { Text("REMOVE FROM BANK") }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showBankManager = false }) { Text("DONE") }
                }
            )
        }

        if (showGradeSwitchConfirm) {
            AlertDialog(
                onDismissRequest = { showGradeSwitchConfirm = false },
                title = { Text("START A NEW CLASS PAPER?") },
                text = { Text("Switching classes will clear the current paper questions so Class 9 and Class 10 questions are not mixed. Your imported textbook files remain saved.") },
                confirmButton = {
                    Button(onClick = {
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
    } else ""
}
private suspend fun <T> runCatchingCancellable(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (error: Throwable) {
    Result.failure(error)
}

 

private fun extractPdfTextInRegion(context: Context, file: File, pageNumber: Int, region: Rect): String {
    return PdfTextDocumentCache.withDocument(context, file) { document ->
        require(pageNumber in 1..document.numberOfPages) { "Invalid PDF page." }
        val page = document.getPage(pageNumber - 1)
        val cropBox = page.cropBox
        val rotation = ((page.rotation % 360) + 360) % 360
        val displayedWidth = if (rotation == 90 || rotation == 270) cropBox.height else cropBox.width
        val displayedHeight = if (rotation == 90 || rotation == 270) cropBox.width else cropBox.height
        val left = region.left * displayedWidth
        val top = region.top * displayedHeight
        val right = region.right * displayedWidth
        val bottom = region.bottom * displayedHeight

        val stripper = object : PDFTextStripper() {
            val selectedLines = StringBuilder()

            override fun writeString(text: String, textPositions: MutableList<TextPosition>) {
                // Preserve italic/rotated maths glyphs; only filter by the selected page area.
                val selectedChars = textPositions
                    .filter { position ->
                        val x = position.xDirAdj
                        val y = position.yDirAdj
                        val w = position.widthDirAdj
                        val h = position.heightDir
                        val centerX = x + w / 2f
                        val centerY = y - h / 2f
                        centerX >= left && centerX <= right && centerY >= top && centerY <= bottom
                    }
                    // Keep each extracted line left-to-right even when the PDF's
                    // internal glyph order is unusual.
                    .sortedBy { it.xDirAdj }
                if (selectedChars.isNotEmpty()) {
                    var previousRight: Float? = null
                    selectedChars.forEach { position ->
                        val currentLeft = position.xDirAdj
                        val previous = previousRight
                        // PDFBox reports glyph positions individually; restore word gaps
                        // instead of gluing every selected character into one long token.
                        if (previous != null && currentLeft - previous > max(1.5f, position.widthDirAdj * 0.28f)) {
                            selectedLines.append(' ')
                        }
                        selectedLines.append(position.unicode ?: "")
                        previousRight = currentLeft + position.widthDirAdj
                    }
                    selectedLines.append('\n')
                }
            }
        }.apply {
            // PDF content streams often store glyphs in drawing order, not reading order.
            // Sorting by position is essential here; otherwise selected math text can
            // arrive as scrambled character sequences.
            sortByPosition = true
            startPage = pageNumber
            endPage = pageNumber
        }
        stripper.getText(document)
        stripper.selectedLines.toString().trim().take(100_000)
    }
}

private fun removePctbWatermarkLines(text: String): String {
    // PDF watermark glyphs can be interleaved with question text on the same
    // extracted line. Remove only the watermark phrase, not the whole line.
    val watermarkPatterns = listOf(
        // Actual textbook overlay: "Web Version Of PECTAA Textbook"
        Regex("(?i)web\\s*version\\s*of\\s*pectaa\\s*textbook"),
        // Some PDF extractors may split or omit the word "Textbook".
        Regex("(?i)web\\s*version\\s*of\\s*pectaa"),
        // Footer printed separately at the bottom of the page.
        Regex("(?i)not\\s*for\\s*sale")
    )
    return text.lineSequence()
        .map { line ->
            watermarkPatterns.fold(line) { cleaned, pattern -> pattern.replace(cleaned, "") }
                .replace(Regex("[ \\t]{2,}"), " ")
                .trim()
        }
        .filter { line ->
            val normalized = line.lowercase().filter(Char::isLetterOrDigit)
            normalized.isNotBlank() &&
                !normalized.contains("webversionofpectaa") &&
                !normalized.contains("webversionofpctb") &&
                !normalized.contains("pctbwebversion")
        }
        .joinToString("\n")
        .trim()
}
