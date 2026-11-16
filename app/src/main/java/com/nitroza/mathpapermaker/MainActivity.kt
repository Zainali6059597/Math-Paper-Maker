package com.nitroza.mathpapermaker

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.room.withTransaction
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.nitroza.mathpapermaker.data.AppDatabase
import com.nitroza.mathpapermaker.data.PackageImporter
import com.nitroza.mathpapermaker.data.SavedPaperDraftEntity
import com.nitroza.mathpapermaker.data.QuestionSourceEntity
import com.nitroza.mathpapermaker.data.PaperPatternEntity
import com.nitroza.mathpapermaker.data.QuestionEntity
import com.nitroza.mathpapermaker.data.ReviewAuditEntity
import com.nitroza.mathpapermaker.data.importPackage
import com.nitroza.mathpapermaker.domain.*
import com.nitroza.mathpapermaker.pdf.PaperPdfExporter
import com.nitroza.mathpapermaker.ui.MathPaperMakerTheme
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class MainActivity : ComponentActivity() {
    private val database by lazy { AppDatabase.get(this) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MathPaperMakerTheme { PaperMakerApp() } }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun PaperMakerApp() {
        val boards = listOf("Punjab", "Punjab (Matric-Tech)", "FBISE", "Sindh", "KPK", "Balochistan", "AJK")
        var board by remember { mutableStateOf(boards[0]) }
        var grade by remember { mutableStateOf(9) }
        var stream by remember { mutableStateOf("MATHEMATICS") }
        var subject by remember { mutableStateOf("Mathematics") }
        var session by remember { mutableStateOf("2026-27") }
        var mode by remember { mutableStateOf(GenerationMode.PRACTICE) }
        var availableChapters by remember { mutableStateOf<List<String>>(emptyList()) }
        var selectedChapters by remember { mutableStateOf<Set<String>>(emptySet()) }
        var notice by remember { mutableStateOf("Loading bundled offline content…") }
        var questionCount by remember { mutableIntStateOf(0) }
        var patternCount by remember { mutableIntStateOf(0) }
        var draft by remember { mutableStateOf<List<PaperPdfExporter.Item>>(emptyList()) }
        var draftSlots by remember { mutableStateOf<List<OfferedQuestionSlot>>(emptyList()) }
        var draftQuestionRows by remember { mutableStateOf<List<com.nitroza.mathpapermaker.data.QuestionEntity>>(emptyList()) }
        var replacementTarget by remember { mutableIntStateOf(-1) }
        var replacementOptions by remember { mutableStateOf<List<com.nitroza.mathpapermaker.data.QuestionEntity>>(emptyList()) }
        var originalDraft by remember { mutableStateOf<List<PaperPdfExporter.Item>>(emptyList()) }
        var originalDraftSlots by remember { mutableStateOf<List<OfferedQuestionSlot>>(emptyList()) }
        var school by remember { mutableStateOf("") }
        var title by remember { mutableStateOf("Mathematics Examination") }
        var instructions by remember { mutableStateOf("Attempt the required number of questions. Show all necessary working.") }
        var draftIdentity by remember { mutableStateOf(PaperIdentity(board, grade, stream, subject, session)) }
        var totalMarks by remember { mutableIntStateOf(0) }
        var offeredMarks by remember { mutableIntStateOf(0) }
        var originalAttemptedMarks by remember { mutableIntStateOf(0) }
        var choiceInstructions by remember { mutableStateOf<List<String>>(emptyList()) }
        var customDraft by remember { mutableStateOf(false) }
        var savedDrafts by remember { mutableStateOf<List<SavedPaperDraftEntity>>(emptyList()) }
        var activeDraftId by remember { mutableStateOf<String?>(null) }
        var activePatternId by remember { mutableStateOf<String?>(null) }
        var activePatternVersion by remember { mutableStateOf<Int?>(null) }
        var activePatternSourceId by remember { mutableStateOf<String?>(null) }
        var activeDurationMinutes by remember { mutableStateOf<Int?>(null) }
        var pendingSources by remember { mutableStateOf<List<QuestionSourceEntity>>(emptyList()) }
        var pendingPatterns by remember { mutableStateOf<List<PaperPatternEntity>>(emptyList()) }
        var pendingQuestions by remember { mutableStateOf<List<QuestionEntity>>(emptyList()) }
        var reviewHistory by remember { mutableStateOf<List<ReviewAuditEntity>>(emptyList()) }

        LaunchedEffect(board, grade, stream, subject, session) {
            val rows = runCatching { database.contentDao().findQuestions(board, grade, stream, subject, session) }.getOrDefault(emptyList())
            val chapters = rows.map { it.chapterId }.filter { it.isNotBlank() }.distinct().sorted()
            availableChapters = chapters
            selectedChapters = chapters.toSet()
        }

        val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri != null) lifecycleScope.launch {
                runCatching {
                    val raw = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                        ?: error("Could not read the selected file.")
                    val parsed = PackageImporter.parse(raw)
                    database.importPackage(parsed.source, parsed.pattern, parsed.slots, parsed.questions, parsed.choiceGroups, parsed.quotas, parsed.exclusions)
                    questionCount = database.contentDao().questionCount()
                    patternCount = database.contentDao().patternCount()
                    val refreshedRows = database.contentDao().findQuestions(board, grade, stream, subject, session)
                    availableChapters = refreshedRows.map { it.chapterId }.filter { it.isNotBlank() }.distinct().sorted()
                    selectedChapters = availableChapters.toSet()
                    "Imported ${parsed.questions.size} questions. Package content is marked NEEDS_REVIEW; it cannot be used for an official paper until independently verified." +
                        if (parsed.warnings.isEmpty()) "" else " Review warnings: ${parsed.warnings.joinToString(" ")}"
                }.onSuccess { notice = it }.onFailure { notice = "Import failed: ${it.message}" }
            }
        }
        val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri: Uri? ->
            if (uri != null && draft.isNotEmpty()) {
                runCatching {
                    val pdfInstructions = buildString {
                        if (mode == GenerationMode.PRACTICE) append("PRACTICE COPY — NOT AN OFFICIAL BOARD PAPER. Source, pattern and questions remain NEEDS_REVIEW until independently approved.\\n\\n")
                        if (customDraft) append("CUSTOM DRAFT — verify all edits, question order and marks before use.\\n\\n")
                        append(instructions)
                    }
                    PaperPdfExporter.write(this@MainActivity, uri, PaperPdfExporter.Paper(
                        school, title, draftIdentity.board, draftIdentity.grade, draftIdentity.subject,
                        draftIdentity.session, pdfInstructions, draft, totalMarks, choiceInstructions, customDraft, activeDurationMinutes
                    ))
                }.onSuccess { notice = "PDF exported successfully. Check your selected destination." }
                    .onFailure { notice = "PDF export failed: ${it.message}" }
            }
        }

        val answerKeyLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri: Uri? ->
            if (uri != null && draft.isNotEmpty()) {
                runCatching {
                    val questionsById = draftQuestionRows.associateBy { it.questionId }
                    val answerItems = draft.mapIndexed { index, item ->
                        val question = questionsById[item.questionId]
                            ?: error("The original question record for ${item.number} is missing.")
                        require(item.text.trim() == question.questionText.trim()) {
                            "Question ${item.number} was edited. Restore its original text before exporting a matching answer key."
                        }
                        val answer = question.answerText?.takeIf { it.isNotBlank() }
                            ?: error("No answer is available for question ${item.number}.")
                        PaperPdfExporter.Item(index + 1, answer, item.marks, item.questionId, item.displayLabel)
                    }
                    PaperPdfExporter.write(this@MainActivity, uri, PaperPdfExporter.Paper(
                        school, "Answer Key — ${title.ifBlank { "Mathematics Examination" }}",
                        draftIdentity.board, draftIdentity.grade, draftIdentity.subject, draftIdentity.session,
                        when {
                            mode == GenerationMode.PRACTICE -> "PRACTICE / UNVERIFIED CONTENT — TEACHER COPY. Suggested answers; verify against the current official textbook and marking guidance."
                            customDraft -> "CUSTOM DRAFT — TEACHER COPY. Verify edited questions and suggested answers before use."
                            else -> "TEACHER COPY — Suggested answers for the generated paper. Check working and marking against the applicable board guidance."
                        },
                        answerItems, totalMarks, choiceInstructions, customDraft, activeDurationMinutes
                    ))
                }.onSuccess { notice = "Answer-key PDF exported. Check the selected destination." }
                    .onFailure { notice = "Answer-key export failed: ${it.message}" }
            }
        }

        LaunchedEffect(Unit) {
            runCatching {
                // Bundled JSON packs are imported on first launch, so the core
                // question bank can ship inside the APK without network access.
                val bundledFiles = assets.list("content-packs")
                    ?.filter { it.endsWith(".json", ignoreCase = true) }
                    .orEmpty()
                var loadedPackages = 0
                var loadedQuestions = 0
                for (fileName in bundledFiles) {
                    val raw = assets.open("content-packs/$fileName")
                        .bufferedReader()
                        .use { it.readText() }
                    val parsed = PackageImporter.parse(raw)
                    // Avoid duplicate-key failures when the app is opened again.
                    if (database.contentDao().findPatternById(parsed.pattern.patternId) == null) {
                        database.importPackage(
                            parsed.source, parsed.pattern, parsed.slots, parsed.questions,
                            parsed.choiceGroups, parsed.quotas, parsed.exclusions
                        )
                        loadedPackages++
                        loadedQuestions += parsed.questions.size
                    }
                }
                questionCount = database.contentDao().questionCount()
                patternCount = database.contentDao().patternCount()
                savedDrafts = database.contentDao().listDrafts()
                notice = when {
                    loadedPackages > 0 -> "Loaded $loadedPackages bundled offline package(s) and $loadedQuestions questions. Content is NEEDS_REVIEW until independently checked."
                    bundledFiles.isNotEmpty() -> "Bundled offline content is already loaded. Content remains local; no internet is used."
                    questionCount > 0 -> "Local question bank ready. No internet connection is used."
                    else -> "No bundled content packs are present yet. The app is offline, but its question bank is empty; do not generate a paper until verified packs are bundled."
                }
            }.onFailure { notice = "Local database or bundled content error: ${it.message}" }
        }

        var screen by remember { mutableStateOf("home") }
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Column { Text("MATHS PAPER MAKER", style = MaterialTheme.typography.titleMedium); Text("OFFLINE EXAM STUDIO", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) } },
                    actions = { TextButton(onClick = { screen = "home" }) { Text("HOME") } }
                )
            }
        ) { padding ->
            when (screen) {
                "review" -> ContentReviewScreen(
                    modifier = Modifier.padding(padding), sources = pendingSources, patterns = pendingPatterns, questions = pendingQuestions, history = reviewHistory,
                    onRefresh = { lifecycleScope.launch {
                        pendingSources = database.contentDao().pendingSources()
                        pendingPatterns = database.contentDao().pendingPatterns()
                        pendingQuestions = database.contentDao().pendingQuestions()
                        reviewHistory = database.contentDao().recentReviewAudit()
                    } },
                    onApprove = { itemType, itemId, checklistJson, note -> lifecycleScope.launch {
                        runCatching {
                            database.withTransaction {
                            val dao = database.contentDao()
                            val now = System.currentTimeMillis()
                            when (itemType) {
                                "SOURCE" -> {
                                    val item = dao.sourceById(itemId) ?: error("Source no longer exists.")
                                    require(item.sourceUrl?.isNotBlank() == true) { "A primary source URL is required." }
                                    require(item.issuer.isNotBlank() && item.title.isNotBlank()) { "Issuer and document title are required." }
                                    require(item.sourceType.uppercase().contains("OFFICIAL")) { "Source type must identify an official primary source." }
                                    dao.setSourceStatus(itemId, "VERIFIED_OFFICIAL")
                                    dao.insertReviewAudit(ReviewAuditEntity(UUID.randomUUID().toString(), itemType, itemId, item.verificationStatus, "VERIFIED_OFFICIAL", checklistJson, note, now))
                                }
                                "PATTERN" -> {
                                    val item = pendingPatterns.firstOrNull { it.patternId == itemId } ?: error("Pattern no longer exists.")
                                    val source = dao.sourceById(item.sourceId) ?: error("Pattern source is missing.")
                                    require(source.verificationStatus == "VERIFIED_OFFICIAL") { "Verify its source first." }
                                    require(item.totalMarks > 0 && item.version > 0) { "Pattern total/version is invalid." }
                                    val slots = dao.findSlots(item.patternId)
                                    require(slots.isNotEmpty() && slots.all { it.marks > 0 }) { "Pattern slots are missing or invalid." }
                                    val groups = dao.findChoiceGroups(item.patternId)
                                    if (groups.isEmpty()) require(slots.sumOf { it.marks } == item.totalMarks) { "Required-slot marks do not match total marks." }
                                    else require(groups.sumOf { it.attemptCount * it.marksPerQuestion } == item.totalMarks) { "Attempted choice marks do not match total marks." }
                                    dao.setPatternStatus(itemId, "VERIFIED_OFFICIAL")
                                    dao.insertReviewAudit(ReviewAuditEntity(UUID.randomUUID().toString(), itemType, itemId, item.verificationStatus, "VERIFIED_OFFICIAL", checklistJson, note, now))
                                }
                                "QUESTION" -> {
                                    val item = pendingQuestions.firstOrNull { it.questionId == itemId } ?: error("Question no longer exists.")
                                    val source = dao.sourceById(item.sourceId) ?: error("Question source is missing.")
                                    require(source.verificationStatus == "VERIFIED_OFFICIAL") { "Verify its source first." }
                                    require(item.questionText.isNotBlank() && item.chapterId.isNotBlank() && item.marks > 0) { "Question text, chapter and marks must be valid." }
                                    require(item.summative) { "Only summative questions can be approved for official paper generation." }
                                    dao.setQuestionStatus(itemId, "VERIFIED_OFFICIAL")
                                    dao.insertReviewAudit(ReviewAuditEntity(UUID.randomUUID().toString(), itemType, itemId, item.verificationStatus, "VERIFIED_OFFICIAL", checklistJson, note, now))
                                }
                                else -> error("Unknown review item type.")
                            }
                            }
                        }.onSuccess {
                            pendingSources = database.contentDao().pendingSources()
                            pendingPatterns = database.contentDao().pendingPatterns()
                            pendingQuestions = database.contentDao().pendingQuestions()
                            reviewHistory = database.contentDao().recentReviewAudit()
                            notice = "$itemType approved after manual review."
                        }.onFailure { notice = "Approval blocked: ${it.message}" }
                    } },
                    onBack = { screen = "home" }
                )
                "saved" -> SavedDraftsScreen(
                    modifier = Modifier.padding(padding), drafts = savedDrafts,
                    onLoad = { saved -> lifecycleScope.launch {
                        runCatching {
                            val items = decodeItems(saved.itemsJson)
                            val slots = decodeSlots(saved.slotsJson)
                            val identity = PaperIdentity(saved.board, saved.grade, saved.stream, saved.subject, saved.session)
                            draft = items; originalDraft = items; draftSlots = slots; originalDraftSlots = slots
                            draftIdentity = identity; school = saved.schoolName; title = saved.examTitle
                            instructions = saved.instructions; totalMarks = saved.totalAttemptedMarks
                            originalAttemptedMarks = saved.totalAttemptedMarks; offeredMarks = saved.offeredMarks
                            customDraft = saved.customDraft; choiceInstructions = decodeStrings(saved.choiceInstructionsJson)
                            draftQuestionRows = database.contentDao().findQuestions(saved.board, saved.grade, saved.stream, saved.subject, saved.session)
                            mode = runCatching { GenerationMode.valueOf(saved.generationMode) }.getOrDefault(GenerationMode.PRACTICE)
                            activeDraftId = saved.draftId
                            activePatternId = saved.patternId; activePatternVersion = saved.patternVersion; activePatternSourceId = saved.patternSourceId
                            activeDurationMinutes = saved.patternId?.let { database.contentDao().findPatternById(it)?.durationMinutes }
                            replacementOptions = emptyList(); replacementTarget = -1
                            screen = "editor"; notice = if (saved.patternId == null) "Saved draft reopened. It predates pattern pinning; its original pattern reference is unavailable." else "Saved draft reopened with pinned pattern ${saved.patternId} v${saved.patternVersion}."
                        }.onFailure { notice = "Could not open saved draft: ${it.message}" }
                    } },
                    onDelete = { id -> lifecycleScope.launch { database.contentDao().deleteDraft(id); savedDrafts = database.contentDao().listDrafts() } },
                    onBack = { screen = "home" }
                )
                "editor" -> EditorScreen(
                    modifier = Modifier.padding(padding), draft = draft, school = school, onSchool = { school = it },
                    title = title, onTitle = { title = it }, instructions = instructions, onInstructions = { instructions = it },
                    totalMarks = totalMarks, offeredMarks = offeredMarks, custom = customDraft,
                    replacementTarget = replacementTarget,
                    replacementOptions = replacementOptions,
                    onRequestReplacement = { index ->
                        if (index in draft.indices && index in draftSlots.indices) {
                            val current = draft[index]
                            val currentCandidate = draftQuestionRows.firstOrNull { it.questionId == current.questionId }
                            if (currentCandidate != null) {
                                val identity = draftIdentity
                                val bank = draftQuestionRows.map { q -> QuestionCandidate(q.questionId, identity, q.chapterId, q.sloId, q.questionType, q.marks, q.summative,
                                    runCatching { ContentStatus.valueOf(q.verificationStatus) }.getOrDefault(ContentStatus.NEEDS_REVIEW), q.cognitiveLevel, q.difficulty, q.selectionWeight) }
                                val request = ReplacementRequest(identity, draftSlots[index], mode, current.questionId,
                                    draft.mapIndexedNotNull { i, item -> if (i == index) null else item.questionId }.toSet())
                                val eligibleIds = QuestionReplacementEngine.findOptions(request, bank).candidates.map { it.id }.toSet()
                                replacementOptions = draftQuestionRows.filter { it.questionId in eligibleIds }
                                replacementTarget = index
                            } else { replacementOptions = emptyList(); replacementTarget = index }
                        }
                    },
                    onChooseReplacement = { question ->
                        val index = replacementTarget
                        if (index in draft.indices) {
                            draft = draft.toMutableList().also { it[index] = it[index].copy(text = question.questionText, marks = question.marks, questionId = question.questionId) }
                            customDraft = true
                        }
                        replacementTarget = -1
                        replacementOptions = emptyList()
                    },
                    onDismissReplacement = { replacementTarget = -1; replacementOptions = emptyList() },
                    onExportAnswerKey = { answerKeyLauncher.launch("Maths-Answer-Key-Class-${draftIdentity.grade}.pdf") },
                    onMove = { from, to ->
                        if (to in draft.indices && from in draft.indices) {
                            draft = draft.toMutableList().also { list ->
                                val item = list.removeAt(from); list.add(to, item)
                                for (i in list.indices) list[i] = list[i].copy(number = i + 1)
                            }
                            draftSlots = draftSlots.toMutableList().also { list ->
                                val slot = list.removeAt(from); list.add(to, slot)
                            }
                            customDraft = true
                        }
                    },
                    onRemove = { index ->
                        val updated = draft.toMutableList().also { list ->
                            list.removeAt(index)
                            for (i in list.indices) list[i] = list[i].copy(number = i + 1)
                        }
                        draft = updated
                        if (index in draftSlots.indices) draftSlots = draftSlots.toMutableList().also { it.removeAt(index) }
                        customDraft = true
                        offeredMarks = updated.sumOf { it.marks }
                        totalMarks = offeredMarks
                    },
                    onEdit = { index, text -> draft = draft.toMutableList().also { it[index] = it[index].copy(text = text) }; customDraft = true },
                    onRevert = {
                        draft = originalDraft
                        draftSlots = originalDraftSlots
                        totalMarks = originalAttemptedMarks
                        offeredMarks = originalDraft.sumOf { it.marks }
                        customDraft = false
                    },
                    onSave = {
                        lifecycleScope.launch {
                            runCatching {
                                val id = activeDraftId ?: UUID.randomUUID().toString()
                                val now = System.currentTimeMillis()
                                val saved = SavedPaperDraftEntity(
                                    draftId = id, displayName = "${title.ifBlank { "Untitled paper" }} · Class ${draftIdentity.grade}",
                                    board = draftIdentity.board, grade = draftIdentity.grade, stream = draftIdentity.stream,
                                    subject = draftIdentity.subject, session = draftIdentity.session, schoolName = school,
                                    examTitle = title, instructions = instructions, totalAttemptedMarks = totalMarks,
                                    offeredMarks = offeredMarks, customDraft = customDraft, itemsJson = encodeItems(draft),
                                    slotsJson = encodeSlots(draftSlots), choiceInstructionsJson = encodeStrings(choiceInstructions),
                                    generationMode = mode.name, updatedAt = now,
                                    patternId = activePatternId, patternVersion = activePatternVersion, patternSourceId = activePatternSourceId
                                )
                                database.contentDao().saveDraft(saved)
                                activeDraftId = id; savedDrafts = database.contentDao().listDrafts()
                                notice = "Draft saved on this device."
                            }.onFailure { notice = "Draft save failed: ${it.message}" }
                        }
                    },
                    onExport = {
                        lifecycleScope.launch {
                            runCatching {
                                val patternId = activePatternId ?: error("This draft has no pinned paper pattern. Import/regenerate it from a local content package before exporting.")
                                val dao = database.contentDao()
                                val pattern = dao.findPatternById(patternId)
                                    ?: error("The pinned paper pattern is no longer available.")
                                if (pattern.patternId != patternId || pattern.version != activePatternVersion || pattern.sourceId != activePatternSourceId) {
                                    error("The paper pattern/source changed after this draft was generated. Reopen or regenerate the draft to avoid exporting against a different pattern.")
                                }
                                val source = dao.findSource(pattern.sourceId) ?: error("The pattern's primary source record is missing.")
                                val groups = dao.findChoiceGroups(pattern.patternId)
                                val slots = dao.findSlots(pattern.patternId)
                                val exclusions = dao.findSyllabusExclusions(pattern.patternId).mapNotNull { row ->
                                    when (row.exclusionType) {
                                        "CHAPTER" -> SyllabusExclusion(chapterId = row.value)
                                        "SLO" -> SyllabusExclusion(sloId = row.value)
                                        else -> null
                                    }
                                }.toSet()
                                val sections = if (groups.isNotEmpty()) {
                                    groups.map { ComplianceSection(it.groupId, it.offeredCount, it.attemptCount, it.marksPerQuestion) } +
                                        slots.filter { it.groupId == null && it.bundleId == null }
                                            .map { ComplianceSection(it.slotId, 1, 1, it.marks) }
                                } else {
                                    slots.map { ComplianceSection(it.slotId, 1, 1, it.marks) }
                                }
                                val questionMap = draftQuestionRows.associateBy { it.questionId }
                                val complianceQuestions = draft.mapIndexed { index, item ->
                                    val row = questionMap[item.questionId] ?: error("Question ${item.questionId} is missing from the local question bank.")
                                    val slotId = draftSlots.getOrNull(index)?.slotId
                                    val sectionId = if (slotId == null) {
                                        if (!customDraft) error("Question ${item.number} has no matching pattern slot.") else "__CUSTOM_UNASSIGNED__"
                                    } else if (groups.isNotEmpty()) {
                                        val matchedSlot = slots.firstOrNull { it.slotId == slotId }
                                        matchedSlot?.groupId ?: matchedSlot?.slotId
                                            ?: if (customDraft) "__CUSTOM_UNASSIGNED__" else error("Question ${item.number} has no valid pattern-slot assignment.")
                                    } else slotId
                                    ComplianceQuestion(
                                        id = row.questionId,
                                        identity = PaperIdentity(row.board, row.grade, row.stream, row.subject, row.session),
                                        sectionId = sectionId, chapterId = row.chapterId, sloId = row.sloId,
                                        marks = item.marks,
                                        status = runCatching { ContentStatus.valueOf(row.verificationStatus) }.getOrDefault(ContentStatus.NEEDS_REVIEW),
                                        summative = row.summative
                                    )
                                }
                                val report = BoardPatternCompliance.audit(
                                    CompliancePattern(
                                        identity = draftIdentity, declaredAttemptedMarks = pattern.totalMarks, sections = sections,
                                        sourceStatus = runCatching { ContentStatus.valueOf(source.verificationStatus) }.getOrDefault(ContentStatus.NEEDS_REVIEW),
                                        patternStatus = runCatching { ContentStatus.valueOf(pattern.verificationStatus) }.getOrDefault(ContentStatus.NEEDS_REVIEW),
                                        hasUnresolvedSourceConflict = source.verificationStatus == "CONFLICTED" || pattern.verificationStatus == "CONFLICTED"
                                    ), complianceQuestions, exclusions,
                                    officialMode = mode == GenerationMode.OFFICIAL && !customDraft
                                )
                                val criticalCustomErrors = report.errors.filter { issue ->
                                    issue.contains("conflict", ignoreCase = true) ||
                                        issue.contains("excluded syllabus", ignoreCase = true) ||
                                        issue.contains("identity mismatch", ignoreCase = true) ||
                                        issue.contains("board/grade/stream/subject/session mismatch", ignoreCase = true) ||
                                        issue.contains("duplicate question IDs", ignoreCase = true)
                                }
                                if (criticalCustomErrors.isNotEmpty()) error("Export blocked by critical content-integrity issue(s): ${criticalCustomErrors.joinToString(" ")}")
                                if (!customDraft && !report.passed) error("Pre-export compliance failed: ${report.errors.joinToString(" ")}")
                                notice = when {
                                    customDraft -> "Custom/non-standard draft: pattern audit ${if (report.passed) "passed" else "found issues"}. PDF will be labelled CUSTOM. ${report.errors.joinToString(" ")}".take(1200)
                                    mode == GenerationMode.PRACTICE -> "Practice audit passed: ${report.offeredMarks} offered marks; ${report.attemptedMarks} attempted marks. PDF will be marked PRACTICE; content remains NEEDS_REVIEW."
                                    else -> "Pre-export audit passed: ${report.offeredMarks} offered marks; ${report.attemptedMarks} attempted marks. Preparing PDF…"
                                }
                                exportLauncher.launch("Maths-Paper-${draftIdentity.grade}.pdf")
                            }.onFailure { notice = "PDF export blocked: ${it.message}" }
                        }
                    }, exportLabel = when {
                        customDraft -> "EXPORT CUSTOM PRACTICE PDF"
                        mode == GenerationMode.OFFICIAL -> "APPROVE & EXPORT A4 PDF"
                        else -> "EXPORT PRACTICE A4 PDF"
                    }, onBack = { screen = "home" }
                )
                else -> Column(
                    Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedButton(onClick = { lifecycleScope.launch {
                        pendingSources = database.contentDao().pendingSources()
                        pendingPatterns = database.contentDao().pendingPatterns()
                        pendingQuestions = database.contentDao().pendingQuestions()
                        reviewHistory = database.contentDao().recentReviewAudit()
                        screen = "review"
                    } }, modifier = Modifier.fillMaxWidth()) { Text("CONTENT REVIEW & APPROVAL") }
                    OutlinedButton(onClick = { lifecycleScope.launch { savedDrafts = database.contentDao().listDrafts(); screen = "saved" } }, modifier = Modifier.fillMaxWidth()) { Text("SAVED DRAFTS (${savedDrafts.size})") }
                    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Build a better paper.", style = MaterialTheme.typography.headlineMedium)
                            Text("Create, review and export printable mathematics examinations — directly on this device.", style = MaterialTheme.typography.bodyMedium)
                            Text("100% LOCAL     •     A4 PDF     •     NO SIGN-IN", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Text("PAPER CONFIGURATION", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    ChoiceField("Board", boards, board) { board = it }
                    ChoiceField("Class", listOf("9", "10"), grade.toString()) { grade = it.toInt() }
                    ChoiceField("Stream", listOf("MATHEMATICS", "GENERAL_MATHEMATICS"), stream) { stream = it }
                    OutlinedTextField(subject, { subject = it }, label = { Text("Subject name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(session, { session = it }, label = { Text("Exam session (e.g. 2026-27)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    ChoiceField("Generation mode", listOf("PRACTICE", "OFFICIAL"), mode.name) { mode = GenerationMode.valueOf(it) }
                    Card {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("CHAPTERS TO INCLUDE", style = MaterialTheme.typography.titleMedium)
                            Text("Only questions from checked chapters can be selected. The chosen board's pattern and marks distribution stay unchanged.", style = MaterialTheme.typography.bodySmall)
                            if (availableChapters.isEmpty()) {
                                Text("No chapter data is available for this exact board, class, subject and session yet.", style = MaterialTheme.typography.bodySmall)
                            } else {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("${selectedChapters.size} of ${availableChapters.size} selected", style = MaterialTheme.typography.labelMedium)
                                    TextButton(onClick = { selectedChapters = availableChapters.toSet() }) { Text("ALL") }
                                    TextButton(onClick = { selectedChapters = emptySet() }) { Text("NONE") }
                                }
                                availableChapters.forEach { chapterId ->
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(
                                            checked = chapterId in selectedChapters,
                                            onCheckedChange = { checked ->
                                                selectedChapters = if (checked) selectedChapters + chapterId else selectedChapters - chapterId
                                            }
                                        )
                                        Text(chapterId, modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                    Card {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("ON-DEVICE CONTENT", style = MaterialTheme.typography.titleMedium)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Question records"); Text(questionCount.toString(), color = MaterialTheme.colorScheme.primary) }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Paper patterns"); Text(patternCount.toString(), color = MaterialTheme.colorScheme.primary) }
                            Text("Imported packages are held for review. This app never upgrades imported content to official status automatically.", style = MaterialTheme.typography.bodySmall)
                            OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/json", "text/plain")) }, modifier = Modifier.fillMaxWidth()) { Text("IMPORT LOCAL CONTENT PACKAGE") }
                        }
                    }
                    Button(onClick = {
                        lifecycleScope.launch {
                            val identity = PaperIdentity(board, grade, stream, subject, session)
                            val dao = database.contentDao()
                            val pattern = dao.findPattern(board, grade, stream, subject, session)
                            val source = pattern?.let { dao.findSource(it.sourceId) }
                            if (pattern == null) { notice = "No matching local paper pattern. Import a content package for this exact board, class, stream, subject and session."; return@launch }
                            if (mode == GenerationMode.OFFICIAL && (pattern.verificationStatus != "VERIFIED_OFFICIAL" || source?.verificationStatus != "VERIFIED_OFFICIAL")) {
                                notice = "Official generation blocked: pattern/source has not been independently verified. Choose Practice mode or complete content review."; return@launch
                            }
                            if (selectedChapters.isEmpty()) { notice = "Select at least one chapter before generating the paper."; return@launch }
                            val questionRows = dao.findQuestions(board, grade, stream, subject, session)
                            if (questionRows.isEmpty()) { notice = "No questions exist for this exact board, class, stream, subject and session."; return@launch }
                            val syllabusExclusions = dao.findSyllabusExclusions(pattern.patternId)
                            val excludedChapters = syllabusExclusions.filter { it.exclusionType == "CHAPTER" }.map { it.value }.toSet()
                            val excludedSlos = syllabusExclusions.filter { it.exclusionType == "SLO" }.map { it.value }.toSet()
                            val eligibleQuestionRows = questionRows.filter { q ->
                                q.chapterId in selectedChapters && q.chapterId !in excludedChapters && q.sloId !in excludedSlos
                            }
                            val excludedQuestionCount = questionRows.size - eligibleQuestionRows.size
                            val candidates = eligibleQuestionRows.map { q ->
                                QuestionCandidate(q.questionId, identity, q.chapterId, q.sloId, q.questionType, q.marks, q.summative,
                                    runCatching { ContentStatus.valueOf(q.verificationStatus) }.getOrDefault(ContentStatus.NEEDS_REVIEW), q.cognitiveLevel, q.difficulty)
                            }
                            if (eligibleQuestionRows.isEmpty()) { notice = "Generation blocked: all local questions are excluded by this pattern's syllabus rules."; return@launch }
                            val storedSlots = dao.findSlots(pattern.patternId)
                            if (storedSlots.isEmpty()) { notice = "The selected board pattern has no configured question slots."; return@launch }
                            val storedGroups = dao.findChoiceGroups(pattern.patternId)
                            val storedQuotas = dao.findPatternQuotas(pattern.patternId)
                            val seed = System.currentTimeMillis()
                            var generatedSlotOrder: List<OfferedQuestionSlot> = emptyList()
                            val generatedItems: List<PaperPdfExporter.Item>? = if (storedGroups.isNotEmpty() &&
                                (storedSlots.any { it.bundleId != null } || storedSlots.any { it.groupId == null })) {
                                // Mixed pattern: one assignment covers fixed slots, paired parts and choice slots.
                                val compositeSlots = storedSlots.sortedBy { it.displayOrder }.map { slot ->
                                    OfferedQuestionSlot(slot.slotId, slot.marks, slot.chapterId, slot.sloId,
                                        slot.questionType, slot.bundleId, slot.groupId)
                                }
                                val compositeGroups = storedGroups.map { group ->
                                    ChoiceGroupRule(group.groupId, group.offeredCount, group.attemptCount,
                                        group.marksPerQuestion, compositeSlots.filter { it.groupId == group.groupId })
                                }
                                val compositePattern = CompositePaperPattern(identity, pattern.totalMarks, compositeSlots, compositeGroups)
                                generatedSlotOrder = compositeSlots
                                val selectedBySlot: Map<String, QuestionCandidate>? = if (storedQuotas.isNotEmpty()) {
                                    val quotaRules = storedQuotas.mapNotNull { quota -> runCatching {
                                        QuotaDimension.valueOf(quota.dimension)
                                    }.getOrNull()?.let { MetadataQuota(it, quota.value, quota.minimum, quota.maximum) } }
                                    val quotaSlots = compositeSlots.map { slot ->
                                        QuestionSlot(slot.slotId, slot.chapterId, slot.sloId, slot.questionType, slot.marks)
                                    }
                                    when (val result = QuotaAwareSelectionEngine.select(
                                        QuotaSelectionRequest(identity, quotaSlots, quotaRules, mode, seed), candidates)) {
                                        is QuotaSelectionResult.Blocked -> {
                                            notice = "Mixed-pattern quota generation blocked (${result.code}): ${result.message}"; null
                                        }
                                        is QuotaSelectionResult.Success -> result.selected.associate { it.first.slotId to it.second }
                                            .also { notice = "Mixed-pattern draft generated with all declared quotas satisfied." }
                                    }
                                } else when (val result = CompositePatternEngine.generate(compositePattern, candidates, mode, seed)) {
                                    is CompositeSelectionResult.Blocked -> {
                                        notice = "Mixed-pattern generation blocked (${result.code}): ${result.message}"; null
                                    }
                                    is CompositeSelectionResult.Success -> result.paper.selectedBySlot
                                        .also { notice = "Mixed draft generated: ${result.paper.offeredMarks} marks offered, ${result.paper.attemptedMarks} marks to attempt. Review before export." }
                                }
                                if (selectedBySlot == null) null else {
                                    choiceInstructions = compositeGroups.map { group ->
                                        "${group.groupId}: Attempt any ${group.attemptCount} of ${group.offeredCount} questions. Each question carries ${group.marksPerQuestion} marks."
                                    } + storedSlots.filter { it.bundleId != null }.groupBy { it.bundleId!! }.values
                                        .filter { it.size > 1 }.map { parts ->
                                            "${parts.first().bundleId}: Answer all ${parts.size} paired parts."
                                        }
                                    val bundlePartIndexes = storedSlots.filter { it.bundleId != null }
                                        .groupBy { it.bundleId!! }.mapValues { (_, parts) -> parts.sortedBy { it.displayOrder } }
                                    storedSlots.sortedBy { it.displayOrder }.mapIndexed { index, slot ->
                                        val candidate = selectedBySlot.getValue(slot.slotId)
                                        val parts = slot.bundleId?.let { bundlePartIndexes[it] }.orEmpty()
                                        val suffix = parts.indexOfFirst { it.slotId == slot.slotId }
                                        val label = if (parts.size > 1 && suffix >= 0) "${slot.bundleId}(${('a'.code + suffix).toChar()})" else null
                                        PaperPdfExporter.Item(index + 1, questionRows.first { it.questionId == candidate.id }.questionText,
                                            candidate.marks, candidate.id, label)
                                    }
                                }
                            } else if (storedGroups.isNotEmpty()) {
                                val slotByGroup = storedSlots.groupBy { it.groupId }
                                val choicePattern = ChoicePaperPattern(
                                    identity = identity,
                                    expectedAttemptedMarks = pattern.totalMarks,
                                    groups = storedGroups.map { group ->
                                        ChoiceGroupRule(
                                            groupId = group.groupId,
                                            offeredCount = group.offeredCount,
                                            attemptCount = group.attemptCount,
                                            marksPerQuestion = group.marksPerQuestion,
                                            slots = slotByGroup[group.groupId].orEmpty().map { slot ->
                                                OfferedQuestionSlot(slot.slotId, slot.marks, slot.chapterId, slot.sloId, slot.questionType)
                                            }
                                        )
                                    }
                                )
                                generatedSlotOrder = choicePattern.groups.flatMap { it.slots }
                                val quotaRules = storedQuotas.mapNotNull { quota -> runCatching { QuotaDimension.valueOf(quota.dimension) }.getOrNull()?.let { MetadataQuota(it, quota.value, quota.minimum, quota.maximum) } }
                                if (storedQuotas.isNotEmpty()) {
                                    val quotaSlots = choicePattern.groups.flatMap { it.slots }.map { slot -> QuestionSlot(slot.slotId, slot.chapterId, slot.sloId, slot.questionType, slot.marks) }
                                    when (val result = QuotaAwareSelectionEngine.select(QuotaSelectionRequest(identity, quotaSlots, quotaRules, mode, seed), candidates)) {
                                        is QuotaSelectionResult.Blocked -> { notice = "Quota generation blocked (${result.code}): ${result.message}"; null }
                                        is QuotaSelectionResult.Success -> {
                                            choiceInstructions = choicePattern.groups.map { group -> "${group.groupId}: Attempt any ${group.attemptCount} of ${group.offeredCount} questions. Each question carries ${group.marksPerQuestion} marks." }
                                            result.selected.mapIndexed { index, pair -> PaperPdfExporter.Item(index + 1, questionRows.first { it.questionId == pair.second.id }.questionText, pair.second.marks, pair.second.id) }
                                                .also { notice = "Quota-aware choice draft generated: ${result.selected.size} offered questions; ${pattern.totalMarks} marks to attempt." }
                                        }
                                    }
                                } else when (val result = ChoicePatternEngine.generate(choicePattern, candidates, mode, seed)) {
                                    is ChoiceSelectionResult.Blocked -> { notice = "Generation blocked (${result.code}): ${result.message}"; null }
                                    is ChoiceSelectionResult.Success -> {
                                        choiceInstructions = result.paper.groups.map { group ->
                                            "${group.groupId}: Attempt any ${group.attemptCount} of ${group.offered.size} questions. Each question carries ${group.marksPerQuestion} marks."
                                        }
                                        result.paper.groups.flatMap { it.offered }.mapIndexed { index, pair ->
                                            PaperPdfExporter.Item(index + 1, questionRows.first { it.questionId == pair.second.id }.questionText, pair.second.marks, pair.second.id)
                                        }.also {
                                            notice = "Choice-based draft generated: ${result.paper.offeredMarks} marks offered, ${result.paper.attemptedMarks} marks to attempt. Review before export."
                                        }
                                    }
                                }
                            } else {
                                val slots = storedSlots.map { s -> QuestionSlot(s.slotId, s.chapterId, s.sloId, s.questionType, s.marks) }
                                generatedSlotOrder = storedSlots.map { s -> OfferedQuestionSlot(s.slotId, s.marks, s.chapterId, s.sloId, s.questionType, s.bundleId) }
                                if (storedSlots.any { it.bundleId != null }) {
                                    val bundles = storedSlots.groupBy { it.bundleId ?: "SINGLE_${it.slotId}" }
                                        .map { (bundleId, parts) ->
                                            PairedQuestionBundleRule(
                                                bundleId = bundleId,
                                                displayOrder = parts.minOf { it.displayOrder },
                                                parts = parts.sortedBy { it.displayOrder }.map { slot ->
                                                    OfferedQuestionSlot(slot.slotId, slot.marks, slot.chapterId, slot.sloId, slot.questionType, slot.bundleId)
                                                }
                                            )
                                        }
                                    generatedSlotOrder = bundles.sortedBy { it.displayOrder }.flatMap { it.parts }
                                    if (storedQuotas.isNotEmpty()) {
                                        val quotaRules = storedQuotas.mapNotNull { quota -> runCatching { QuotaDimension.valueOf(quota.dimension) }.getOrNull()?.let { MetadataQuota(it, quota.value, quota.minimum, quota.maximum) } }
                                        val quotaSlots = bundles.flatMap { it.parts }.map { part -> QuestionSlot(part.slotId, part.chapterId, part.sloId, part.questionType, part.marks) }
                                        when (val result = QuotaAwareSelectionEngine.select(QuotaSelectionRequest(identity, quotaSlots, quotaRules, mode, seed), candidates)) {
                                            is QuotaSelectionResult.Blocked -> { notice = "Paired-bundle quota generation blocked (${result.code}): ${result.message}"; null }
                                            is QuotaSelectionResult.Success -> {
                                                val selectedBySlot = result.selected.associate { it.first.slotId to it.second }
                                                val renderedBundles = bundles.sortedBy { it.displayOrder }.map { bundle ->
                                                    PairedQuestionBundleSelection(bundle.bundleId, bundle.parts.map { part -> part to selectedBySlot.getValue(part.slotId) })
                                                }
                                                choiceInstructions = renderedBundles.filter { it.parts.size > 1 }.map { PairedQuestionBundleEngine.instruction(it) }
                                                renderedBundles.flatMap { bundle -> bundle.parts.mapIndexed { partIndex, pair ->
                                                    val suffix = ('a'.code + partIndex).toChar()
                                                    PaperPdfExporter.Item(0, questionRows.first { it.questionId == pair.second.id }.questionText,
                                                        pair.first.marks, pair.second.id, if (bundle.parts.size > 1) "${bundle.bundleId}($suffix)" else null)
                                                } }.mapIndexed { index, item -> item.copy(number = index + 1) }
                                                    .also { notice = "Paired-bundle draft generated with all metadata quotas satisfied: ${renderedBundles.size} bundles, ${pattern.totalMarks} marks." }
                                            }
                                        }
                                    } else when (val result = PairedQuestionBundleEngine.generate(identity, pattern.totalMarks, bundles, candidates, mode, seed)) {
                                        is PairedBundleSelectionResult.Blocked -> { notice = "Paired-question generation blocked (${result.code}): ${result.message}"; null }
                                        is PairedBundleSelectionResult.Success -> {
                                            choiceInstructions = result.paper.bundles.filter { bundle -> bundle.parts.size > 1 }
                                                .map { bundle -> PairedQuestionBundleEngine.instruction(bundle) }
                                            result.paper.bundles.flatMap { bundle ->
                                                bundle.parts.mapIndexed { partIndex, pair ->
                                                    val suffix = ('a'.code + partIndex).toChar()
                                                    val label = if (bundle.parts.size > 1) "${bundle.bundleId}($suffix)" else null
                                                    PaperPdfExporter.Item(
                                                        number = 0,
                                                        text = questionRows.first { it.questionId == pair.second.id }.questionText,
                                                        marks = pair.first.marks,
                                                        questionId = pair.second.id,
                                                        displayLabel = label
                                                    )
                                                }
                                            }.mapIndexed { index, item -> item.copy(number = index + 1) }
                                                .also { notice = "Required paired-bundle draft generated: ${result.paper.bundles.size} bundles, ${result.paper.totalMarks} marks. Review before export." }
                                        }
                                    }
                                } else if (storedQuotas.isNotEmpty()) {
                                    val quotaRules = storedQuotas.mapNotNull { quota -> runCatching { QuotaDimension.valueOf(quota.dimension) }.getOrNull()?.let { MetadataQuota(it, quota.value, quota.minimum, quota.maximum) } }
                                    when (val result = QuotaAwareSelectionEngine.select(QuotaSelectionRequest(identity, slots, quotaRules, mode, seed), candidates)) {
                                        is QuotaSelectionResult.Blocked -> { notice = "Quota generation blocked (${result.code}): ${result.message}"; null }
                                        is QuotaSelectionResult.Success -> result.selected.mapIndexed { index, pair -> PaperPdfExporter.Item(index + 1, questionRows.first { it.questionId == pair.second.id }.questionText, pair.second.marks, pair.second.id) }
                                            .also { choiceInstructions = emptyList(); notice = "Quota-aware draft generated. All declared metadata quotas passed." }
                                    }
                                } else when (val result = LocalSelectionEngine.select(SelectionRequest(identity, slots, mode, seed), candidates)) {
                                    is SelectionResult.Blocked -> { notice = "Generation blocked (${result.code}): ${result.message}"; null }
                                    is SelectionResult.Success -> result.selected.mapIndexed { index, pair ->
                                        PaperPdfExporter.Item(index + 1, questionRows.first { it.questionId == pair.second.id }.questionText, pair.second.marks, pair.second.id)
                                    }.also {
                                        choiceInstructions = emptyList()
                                        notice = "Required-slot draft generated. Review and customize every question before exporting."
                                    }
                                }
                            }
                            if (generatedItems != null) {
                                if (excludedQuestionCount > 0) notice += " $excludedQuestionCount excluded question(s) were removed from the selection pool."
                                activeDraftId = null
                                activePatternId = pattern.patternId
                                activePatternVersion = pattern.version
                                activePatternSourceId = pattern.sourceId
                                activeDurationMinutes = pattern.durationMinutes
                                draft = generatedItems
                                originalDraft = generatedItems
                                originalDraftSlots = generatedSlotOrder
                                draftSlots = generatedSlotOrder
                                draftQuestionRows = questionRows
                                draftIdentity = identity
                                totalMarks = pattern.totalMarks
                                originalAttemptedMarks = pattern.totalMarks
                                offeredMarks = generatedItems.sumOf { it.marks }
                                customDraft = false
                                screen = "editor"
                            }
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text("GENERATE PAPER DRAFT") }
                    Text(notice, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                    Text("The app shell and local workflow are implemented; authoritative board content packs still need independent source verification and import.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun ChoiceField(label: String, options: List<String>, selected: String, onSelected: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            options.forEach { option -> Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected == option, onClick = { onSelected(option) })
                Text(option, style = MaterialTheme.typography.bodyMedium)
            } }
        }
    }
}

@Composable
private fun EditorScreen(
    modifier: Modifier, draft: List<PaperPdfExporter.Item>, school: String, onSchool: (String) -> Unit,
    title: String, onTitle: (String) -> Unit, instructions: String, onInstructions: (String) -> Unit,
    totalMarks: Int, offeredMarks: Int, custom: Boolean, replacementTarget: Int,
    replacementOptions: List<com.nitroza.mathpapermaker.data.QuestionEntity>,
    onRequestReplacement: (Int) -> Unit,
    onChooseReplacement: (com.nitroza.mathpapermaker.data.QuestionEntity) -> Unit,
    onDismissReplacement: () -> Unit,
    onMove: (Int, Int) -> Unit, onRemove: (Int) -> Unit,
    onEdit: (Int, String) -> Unit, onRevert: () -> Unit, onSave: () -> Unit, onExport: () -> Unit,
    onExportAnswerKey: () -> Unit, exportLabel: String, onBack: () -> Unit
) {
    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("FINALIZE YOUR PAPER", style = MaterialTheme.typography.titleLarge)
        Text("${draft.size} offered questions • $offeredMarks offered marks • $totalMarks attempted marks", color = MaterialTheme.colorScheme.primary)
        if (custom) {
            Text("Custom draft — review pattern compliance before use.", color = MaterialTheme.colorScheme.error)
            OutlinedButton(onClick = onRevert, modifier = Modifier.fillMaxWidth()) { Text("REVERT TO ORIGINAL GENERATED DRAFT") }
        }
        OutlinedTextField(school, onSchool, label = { Text("School / institution") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(title, onTitle, label = { Text("Exam title") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(instructions, onInstructions, label = { Text("Instructions") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(draft, key = { _, item -> item.number }) { index, item ->
                Card {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${item.displayLabel ?: "Question ${index + 1}"} • ${item.marks} marks", style = MaterialTheme.typography.titleSmall)
                            Row {
                                TextButton(onClick = { onMove(index, index - 1) }, enabled = index > 0) { Text("↑") }
                                TextButton(onClick = { onMove(index, index + 1) }, enabled = index < draft.lastIndex) { Text("↓") }
                                TextButton(onClick = { onRequestReplacement(index) }) { Text("Replace") }
                                TextButton(onClick = { onRemove(index) }) { Text("Remove") }
                            }
                        }
                        OutlinedTextField(
                            value = item.text,
                            onValueChange = { onEdit(index, it) },
                            label = { Text("Question text") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2
                        )
                    }
                }
            }
        }
        OutlinedButton(onClick = onSave, enabled = draft.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text("SAVE DRAFT ON THIS DEVICE") }
        Button(onClick = onExport, enabled = draft.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text(exportLabel) }
        OutlinedButton(onClick = onExportAnswerKey, enabled = draft.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text("EXPORT ANSWER KEY PDF") }
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("BACK TO SETUP") }
        if (replacementTarget >= 0) {
            AlertDialog(
                onDismissRequest = onDismissReplacement,
                title = { Text("Choose a compatible replacement") },
                text = {
                    if (replacementOptions.isEmpty()) {
                        Text("No unused question matches this exact board, class, stream, session, chapter/SLO and marks requirement. Import or review more local questions.")
                    } else {
                        LazyColumn(Modifier.heightIn(max = 360.dp)) {
                            items(replacementOptions, key = { it.questionId }) { question ->
                                TextButton(onClick = { onChooseReplacement(question) }, modifier = Modifier.fillMaxWidth()) {
                                    Column(Modifier.fillMaxWidth()) {
                                        Text("${question.chapterId} • ${question.marks} marks", style = MaterialTheme.typography.labelSmall)
                                        Text(question.questionText, maxLines = 3)
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = onDismissReplacement) { Text("CLOSE") } }
            )
        }
    }
}


@Composable
private fun ContentReviewScreen(
    modifier: Modifier,
    sources: List<QuestionSourceEntity>,
    patterns: List<PaperPatternEntity>,
    questions: List<QuestionEntity>,
    history: List<ReviewAuditEntity>,
    onRefresh: () -> Unit,
    onApprove: (String, String, String, String) -> Unit,
    onBack: () -> Unit
) {
    var targetType by remember { mutableStateOf<String?>(null) }
    var targetId by remember { mutableStateOf("") }
    var targetTitle by remember { mutableStateOf("") }
    var targetSourceId by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    val checks = remember(targetType, targetId) {
        mutableStateListOf(false, false, false, false, false)
    }
    val checklist = when (targetType) {
        "SOURCE" -> listOf(
            "Original issuing authority document inspected",
            "Issuer and document title match the original",
            "Session/date, grade and stream checked",
            "Source URL and page reference checked",
            "This is an official primary source, not a mirror"
        )
        "PATTERN" -> listOf(
            "Pattern checked against original model paper / ToS",
            "Offered and attempted question counts checked",
            "Marks and attempted total independently recalculated",
            "Board, grade, stream and session match",
            "No unresolved conflict remains in the source documents"
        )
        else -> listOf(
            "Question text checked against its source page",
            "Chapter and SLO mapping checked",
            "Question type and marks checked",
            "Summative assessment status confirmed",
            "Copyright and permitted-use status reviewed"
        )
    }
    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("CONTENT REVIEW & APPROVAL", style = MaterialTheme.typography.titleLarge)
        Text("Imported records remain unverified until manually checked. Verify the source first, then its patterns and questions.", style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(onClick = onRefresh, label = { Text("REFRESH QUEUE") })
            AssistChip(onClick = onBack, label = { Text("BACK") })
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Text("SOURCES (${sources.size})", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary) }
            items(sources, key = { "S-${it.sourceId}" }) { item ->
                Card { Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(item.title, style = MaterialTheme.typography.titleSmall)
                    Text("${item.issuer} · ${item.sourceType} · ${item.session ?: "Session unspecified"}", style = MaterialTheme.typography.bodySmall)
                    Text(item.sourceUrl ?: "No source URL", style = MaterialTheme.typography.bodySmall)
                    Button(onClick = { targetType = "SOURCE"; targetId = item.sourceId; targetTitle = item.title; targetSourceId = item.sourceId; note = "" }) { Text("REVIEW SOURCE") }
                } }
            }
            item { Text("PAPER PATTERNS (${patterns.size})", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary) }
            items(patterns, key = { "P-${it.patternId}" }) { item ->
                Card { Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${item.board} · Class ${item.grade} · ${item.subject}", style = MaterialTheme.typography.titleSmall)
                    Text("${item.stream} · ${item.session} · v${item.version} · ${item.totalMarks} attempted marks", style = MaterialTheme.typography.bodySmall)
                    Text("Source: ${item.sourceId}", style = MaterialTheme.typography.bodySmall)
                    Button(onClick = { targetType = "PATTERN"; targetId = item.patternId; targetTitle = "${item.board} Class ${item.grade} ${item.subject}"; targetSourceId = item.sourceId; note = "" }) { Text("REVIEW PATTERN") }
                } }
            }
            item { Text("QUESTIONS (${questions.size})", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary) }
            items(questions, key = { "Q-${it.questionId}" }) { item ->
                Card { Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${item.board} · Class ${item.grade} · ${item.chapterId} · ${item.marks} marks", style = MaterialTheme.typography.titleSmall)
                    Text(item.questionText, maxLines = 4, style = MaterialTheme.typography.bodyMedium)
                    Text("${item.stream} · ${item.questionType} · SLO ${item.sloId.ifBlank { "missing" }} · ${if (item.summative) "Summative" else "Not summative"} · Selection weight ×${item.selectionWeight}", style = MaterialTheme.typography.bodySmall)
                    Button(onClick = { targetType = "QUESTION"; targetId = item.questionId; targetTitle = "Question ${item.questionId}"; targetSourceId = item.sourceId; note = "" }) { Text("REVIEW QUESTION") }
                } }
            }
            item { Text("RECENT REVIEW HISTORY (${history.size})", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary) }
            items(history, key = { it.auditId }) { audit ->
                Text("${audit.itemType} · ${audit.itemId} · ${audit.newStatus} · ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(audit.reviewedAt))}", style = MaterialTheme.typography.bodySmall)
            }
            if (sources.isEmpty() && patterns.isEmpty() && questions.isEmpty()) item { Text("No pending content. Import a local package to populate the review queue.") }
        }
    }
    if (targetType != null) {
        AlertDialog(
            onDismissRequest = { targetType = null },
            title = { Text("Manual ${targetType!!.lowercase()} review") },
            text = {
                Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(targetTitle, style = MaterialTheme.typography.titleSmall)
                    Text("Source ID: $targetSourceId", style = MaterialTheme.typography.bodySmall)
                    Text("Confirm every item only after checking the original source. This action records your local approval.", style = MaterialTheme.typography.bodySmall)
                    checklist.forEachIndexed { index, label ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = checks[index], onCheckedChange = { checks[index] = it })
                            Text(label, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    OutlinedTextField(note, { note = it }, label = { Text("Review note (optional)") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                }
            },
            confirmButton = {
                TextButton(enabled = checks.all { it }, onClick = {
                    val json = JSONArray().apply { checklist.forEachIndexed { index, label -> put(JSONObject().put("check", label).put("confirmed", checks[index])) } }.toString()
                    onApprove(targetType!!, targetId, json, note)
                    targetType = null
                }) { Text("CONFIRM VERIFIED") }
            },
            dismissButton = { TextButton(onClick = { targetType = null }) { Text("CANCEL") } }
        )
    }
}

@Composable
private fun SavedDraftsScreen(
    modifier: Modifier, drafts: List<SavedPaperDraftEntity>,
    onLoad: (SavedPaperDraftEntity) -> Unit, onDelete: (String) -> Unit, onBack: () -> Unit
) {
    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("SAVED PAPERS", style = MaterialTheme.typography.titleLarge)
        Text("Drafts are stored locally on this device. No account or internet is required.", style = MaterialTheme.typography.bodySmall)
        if (drafts.isEmpty()) Text("No saved drafts yet. Open a generated paper and tap SAVE DRAFT.")
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(drafts, key = { it.draftId }) { saved ->
                Card { Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(saved.displayName, style = MaterialTheme.typography.titleSmall)
                    Text("${saved.board} · Class ${saved.grade} · ${saved.stream} · ${saved.session}", style = MaterialTheme.typography.bodySmall)
                    Text("${saved.offeredMarks} offered marks · ${saved.totalAttemptedMarks} attempted marks${if (saved.customDraft) " · CUSTOM" else ""}", style = MaterialTheme.typography.bodySmall)
                    Text(if (saved.patternId == null) "Pattern reference unavailable (legacy draft)" else "Pinned pattern: ${saved.patternId} · v${saved.patternVersion} · source ${saved.patternSourceId}", style = MaterialTheme.typography.labelSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onLoad(saved) }) { Text("OPEN") }
                        TextButton(onClick = { onDelete(saved.draftId) }) { Text("DELETE") }
                    }
                } }
            }
        }
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("BACK") }
    }
}

private fun encodeItems(items: List<PaperPdfExporter.Item>): String = JSONArray().apply {
    items.forEach { item -> put(JSONObject().put("number", item.number).put("text", item.text).put("marks", item.marks).put("questionId", item.questionId).put("displayLabel", item.displayLabel)) }
}.toString()

private fun decodeItems(raw: String): List<PaperPdfExporter.Item> {
    val array = JSONArray(raw)
    return (0 until array.length()).map { i -> array.getJSONObject(i).let {
        PaperPdfExporter.Item(it.getInt("number"), it.getString("text"), it.getInt("marks"), it.optString("questionId"), it.optString("displayLabel").takeIf(String::isNotBlank))
    } }
}

private fun encodeSlots(slots: List<OfferedQuestionSlot>): String = JSONArray().apply {
    slots.forEach { slot -> put(JSONObject().put("slotId", slot.slotId).put("marks", slot.marks)
        .put("chapterId", slot.chapterId).put("sloId", slot.sloId).put("questionType", slot.questionType).put("bundleId", slot.bundleId)) }
}.toString()

private fun decodeSlots(raw: String): List<OfferedQuestionSlot> {
    val array = JSONArray(raw)
    return (0 until array.length()).map { i -> array.getJSONObject(i).let { obj ->
        OfferedQuestionSlot(obj.getString("slotId"), obj.getInt("marks"), obj.optString("chapterId").takeIf(String::isNotBlank),
            obj.optString("sloId").takeIf(String::isNotBlank), obj.optString("questionType").takeIf(String::isNotBlank), obj.optString("bundleId").takeIf(String::isNotBlank))
    } }
}

private fun encodeStrings(values: List<String>): String = JSONArray(values).toString()
private fun decodeStrings(raw: String): List<String> {
    val array = JSONArray(raw); return (0 until array.length()).map { array.getString(it) }
}
