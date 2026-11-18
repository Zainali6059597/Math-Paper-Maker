package com.nitroza.mathpapermaker.data

import android.content.Context
import java.io.File
import java.security.MessageDigest

/**
 * Offline repository for textbook questions explicitly confirmed by the teacher.
 * Detection alone never approves a candidate.
 */
object TextbookQuestionBank {
    suspend fun saveApprovedCandidates(
        context: Context,
        bookFile: String,
        chapterId: String,
        sourcePage: Int,
        candidates: List<QuestionTextSegmenter.Segment>,
        questionType: String,
        marks: Int,
        detectedAt: Long = System.currentTimeMillis()
    ): Int {
        require(bookFile.isNotBlank()) { "A textbook identity is required." }
        require(chapterId.isNotBlank()) { "A chapter must be assigned before saving questions." }
        require(sourcePage > 0) { "Source page must be positive." }
        require(questionType in setOf("MCQ", "SHORT", "LONG")) { "Invalid textbook question type." }
        require(marks > 0) { "Question marks must be positive." }
        if (candidates.isEmpty()) return 0

        val stableBookKey = bookFile.trim()
        val rows = candidates.map { candidate ->
            require(candidate.sourceNumber.isNotBlank() && candidate.text.isNotBlank()) {
                "A saved textbook question must have a source number and text."
            }
            val identity = listOf(
                stableBookKey, chapterId, sourcePage.toString(),
                candidate.sourceNumber.trim(), questionType, candidate.text.trim()
            ).joinToString("|")
            TextbookCandidateEntity(
                candidateId = sha256(identity),
                bookFile = stableBookKey,
                chapterId = chapterId,
                exerciseId = "PAGE",
                questionNumber = candidate.sourceNumber.trim(),
                questionText = candidate.text.trim(),
                sourcePage = sourcePage,
                questionType = questionType,
                marks = marks,
                reviewStatus = "APPROVED",
                reviewNote = "Teacher selected this detected question for a paper.",
                detectedAt = detectedAt
            )
        }.distinctBy { it.candidateId }

        val inserted = AppDatabase.get(context.applicationContext)
            .contentDao()
            .insertTextbookCandidates(rows)
        return inserted.count { it >= 0L }
    }

    /**
     * Saves one teacher-confirmed manual or scanned question for reuse.
     * Image paths must point to an existing crop owned by this app.
     */
    suspend fun saveApprovedQuestion(
        context: Context,
        bookFile: String,
        chapterId: String,
        sourcePage: Int,
        questionNumber: String,
        questionText: String,
        questionType: String,
        marks: Int,
        imageFile: String? = null,
        detectedAt: Long = System.currentTimeMillis()
    ): Boolean {
        require(bookFile.isNotBlank()) { "A textbook identity is required." }
        require(chapterId.isNotBlank()) { "A chapter must be assigned before saving questions." }
        require(sourcePage > 0) { "Source page must be positive." }
        require(questionNumber.isNotBlank()) { "Question numbering is required." }
        require(questionType in setOf("MCQ", "SHORT", "LONG")) { "Invalid textbook question type." }
        require(marks > 0) { "Question marks must be positive." }
        val cleanText = questionText.trim()
        val cleanImage = imageFile?.takeIf { it.isNotBlank() }?.let { path ->
            val root = File(context.filesDir, "question_images").canonicalFile
            val image = File(path).canonicalFile
            require(image.path.startsWith(root.path + File.separator) && image.isFile && image.length() > 0L) {
                "A saved scanned question must use an existing app-owned crop image."
            }
            image.absolutePath
        }
        require(cleanText.isNotBlank() || cleanImage != null) {
            "A saved question needs editable text or a scanned crop image."
        }
        val stableBookKey = bookFile.trim()
        val identity = listOf(
            stableBookKey, chapterId, sourcePage.toString(), questionNumber.trim(),
            questionType, cleanText, cleanImage.orEmpty()
        ).joinToString("|")
        val row = TextbookCandidateEntity(
            candidateId = sha256(identity),
            bookFile = stableBookKey,
            chapterId = chapterId,
            exerciseId = "MANUAL",
            questionNumber = questionNumber.trim(),
            questionText = cleanText,
            sourcePage = sourcePage,
            questionType = questionType,
            marks = marks,
            imageFile = cleanImage,
            reviewStatus = "APPROVED",
            reviewNote = "Teacher confirmed this manually selected question for reuse.",
            detectedAt = detectedAt
        )
        val inserted = AppDatabase.get(context.applicationContext)
            .contentDao()
            .insertTextbookCandidates(listOf(row))
        return inserted.firstOrNull()?.let { it >= 0L } == true
    }

    suspend fun approvedForBook(
        context: Context,
        bookFile: String
    ): List<TextbookCandidateEntity> {
        require(bookFile.isNotBlank()) { "A textbook identity is required." }
        return AppDatabase.get(context.applicationContext)
            .contentDao()
            .approvedTextbookCandidates(bookFile.trim())
    }

    suspend fun approvedCount(context: Context, bookFile: String): Int {
        if (bookFile.isBlank()) return 0
        return AppDatabase.get(context.applicationContext)
            .contentDao()
            .approvedTextbookCandidateCount(bookFile.trim())
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
}
