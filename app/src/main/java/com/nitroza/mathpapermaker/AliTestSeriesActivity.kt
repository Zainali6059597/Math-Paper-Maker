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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.nitroza.mathpapermaker.ui.MathPaperMakerTheme
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class AliTestSeriesActivity : ComponentActivity() {
    private data class SelectedQuestion(
        val id: Long,
        val text: String,
        val kind: String,
        val marks: Int,
        val sourcePage: Int
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
        var extractedText by remember { mutableStateOf("") }
        var selected by remember { mutableStateOf<List<SelectedQuestion>>(emptyList()) }
        var nextId by remember { mutableLongStateOf(1L) }
        var notice by remember { mutableStateOf("Choose Class 9 or Class 10, then open its Mathematics PDF.") }
        var showTextEditor by remember { mutableStateOf(false) }
        var questionText by remember { mutableStateOf("") }
        var questionKind by remember { mutableStateOf("SHORT") }
        var questionMarks by remember { mutableStateOf("2") }
        var showQuestions by remember { mutableStateOf(false) }

        val openPdf = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri != null) {
                lifecycleScope.launch {
                    runCatching {
                        contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    runCatching {
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
                        pdfName = file.name
                        pageCount = count
                        pageIndex = 0
                        selected = emptyList()
                        notice = "PDF opened. Browse pages and manually add questions."
                    }.onFailure { notice = "Could not open PDF: ${it.message}" }
                }
            }
        }

        LaunchedEffect(pdfFile, pageIndex) {
            val file = pdfFile ?: return@LaunchedEffect
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

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("ALI TEST SERIES", style = MaterialTheme.typography.titleMedium)
                            Text("PUNJAB BOARD · MATHEMATICS", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary)
                        }
                    }
                )
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
                            Text("Open a PDF stored on this device. Bundled Class 9 and Class 10 books can be added when the files are supplied.", style = MaterialTheme.typography.bodyMedium)
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
                            Image(bitmap.asImageBitmap(), contentDescription = "Textbook PDF page ${pageIndex + 1}",
                                modifier = Modifier.fillMaxWidth(), contentScale = ContentScale.Fit)
                        } ?: CircularProgressIndicator()
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = {
                            lifecycleScope.launch {
                                val file = pdfFile ?: return@launch
                                extractedText = withContext(Dispatchers.IO) {
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
                                questionText = extractedText
                                if (questionText.isBlank()) notice = "No selectable text found on this page. Scanned-page box selection is the next implementation step."
                                else showTextEditor = true
                            }
                        }, modifier = Modifier.weight(1f)) { Text("SELECT TEXT") }
                        Button(onClick = { showQuestions = true }, modifier = Modifier.weight(1f)) {
                            Text("SELECTED (${selected.size})")
                        }
                    }
                    Text(notice, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(top = 6.dp))
                }
            }
        }

        if (showTextEditor) {
            AlertDialog(
                onDismissRequest = { showTextEditor = false },
                title = { Text("SELECT QUESTION TEXT") },
                text = {
                    Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Edit the extracted page text so only your chosen question remains.", style = MaterialTheme.typography.bodySmall)
                        OutlinedTextField(questionText, { questionText = it }, modifier = Modifier.fillMaxWidth(), minLines = 8,
                            label = { Text("Question text") })
                        Text("Question type", style = MaterialTheme.typography.labelMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("MCQ", "SHORT", "LONG").forEach { kind ->
                                FilterChip(selected = questionKind == kind, onClick = {
                                    questionKind = kind
                                    questionMarks = when (kind) { "MCQ" -> "1"; "SHORT" -> "2"; else -> "4" }
                                }, label = { Text(kind) })
                            }
                        }
                        OutlinedTextField(questionMarks, { questionMarks = it.filter(Char::isDigit).take(2) },
                            label = { Text("Marks") }, singleLine = true)
                    }
                },
                confirmButton = {
                    TextButton(enabled = questionText.isNotBlank() && (questionMarks.toIntOrNull() ?: 0) > 0, onClick = {
                        selected = selected + SelectedQuestion(nextId++, questionText.trim(), questionKind,
                            questionMarks.toInt(), pageIndex + 1)
                        showTextEditor = false
                        notice = "Question added to your manual selection."
                    }) { Text("ADD QUESTION") }
                },
                dismissButton = { TextButton(onClick = { showTextEditor = false }) { Text("CANCEL") } }
            )
        }

        if (showQuestions) {
            AlertDialog(
                onDismissRequest = { showQuestions = false },
                title = { Text("SELECTED QUESTIONS (${selected.size})") },
                text = {
                    LazyColumn(Modifier.heightIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        itemsIndexed(selected, key = { _, item -> item.id }) { index, item ->
                            Card {
                                Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Text("Q${index + 1} · ${item.kind} · ${item.marks} marks · PDF page ${item.sourcePage}",
                                        style = MaterialTheme.typography.labelMedium)
                                    Text(item.text, maxLines = 5, style = MaterialTheme.typography.bodySmall)
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
                },
                confirmButton = { TextButton(onClick = { showQuestions = false }) { Text("DONE") } }
            )
        }
    }
}
