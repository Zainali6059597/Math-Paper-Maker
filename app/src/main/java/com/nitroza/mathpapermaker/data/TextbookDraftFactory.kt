package com.nitroza.mathpapermaker.data

import java.security.MessageDigest

/**
 * Converts a human-classified textbook candidate into a quarantined question draft.
 * This deliberately does not approve the source or question for paper generation.
 */
object TextbookDraftFactory {
    data class Classification(
        val board: String,
        val grade: Int,
        val stream: String,
        val subject: String,
        val session: String,
        val chapterId: String,
        val topic: String,
        val questionType: String,
        val marks: Int,
        val cognitiveLevel: String,
        val difficulty: String,
        val sloId: String = ""
    )

    data class Draft(val source: QuestionSourceEntity, val question: QuestionEntity)

    fun create(candidate: TextbookCandidateEntity, classification: Classification): Draft {
        require(candidate.reviewStatus == "ACCEPTED_FOR_CLASSIFICATION") {
            "Only candidates accepted for classification can become draft questions."
        }
        require(candidate.bookFile.isNotBlank() && candidate.questionText.isNotBlank() &&
            candidate.sourcePage > 0 && candidate.chapterId.isNotBlank()) {
            "Textbook candidate is missing text or source provenance."
        }
        require(classification.board.isNotBlank() && classification.subject.isNotBlank() &&
            classification.session.isNotBlank() && classification.chapterId.isNotBlank() &&
            classification.topic.isNotBlank()) { "Board, subject, session, chapter and topic are required." }
        require(classification.grade in 9..10) { "Only supported grades 9 and 10 can be classified." }
        require(classification.stream in setOf("MATHEMATICS", "GENERAL_MATHEMATICS")) {
            "Unsupported mathematics stream."
        }
        require(classification.questionType in setOf("MCQ", "SHORT", "LONG")) {
            "Choose an explicit question type."
        }
        require(classification.marks > 0) { "Marks must be positive." }
        require(classification.cognitiveLevel in setOf("KNOWLEDGE", "UNDERSTANDING", "APPLICATION", "ANALYSIS")) {
            "Choose an explicit cognitive level."
        }
        require(classification.difficulty in setOf("EASY", "MEDIUM", "HARD")) {
            "Choose an explicit difficulty."
        }

        val sourceId = "textbook-" + sha256(candidate.bookFile).take(24)
        val questionId = "textbook-question-" + sha256(candidate.candidateId + "|" +
            classification.board + "|" + classification.grade + "|" + classification.stream +
            "|" + classification.subject + "|" + classification.session).take(32)
        val source = QuestionSourceEntity(
            sourceId = sourceId,
            issuer = "Textbook publisher not independently verified",
            title = candidate.bookFile,
            sourceUrl = null,
            sourceType = "TEXTBOOK_PDF_UNVERIFIED",
            session = classification.session,
            pageReference = "Page ${candidate.sourcePage}; Chapter ${candidate.chapterId}; Exercise ${candidate.exerciseId}; Question ${candidate.questionNumber}",
            verificationStatus = "NEEDS_REVIEW"
        )
        val fingerprint = sha256(listOf(
            classification.board, classification.grade.toString(), classification.stream,
            classification.subject, classification.session, candidate.questionText.trim().lowercase()
        ).joinToString("|"))
        val question = QuestionEntity(
            questionId = questionId,
            board = classification.board,
            grade = classification.grade,
            stream = classification.stream,
            subject = classification.subject,
            session = classification.session,
            chapterId = classification.chapterId,
            topic = classification.topic,
            sloId = classification.sloId,
            cognitiveLevel = classification.cognitiveLevel,
            difficulty = classification.difficulty,
            questionType = classification.questionType,
            marks = classification.marks,
            questionText = candidate.questionText,
            answerText = null,
            summative = false,
            verificationStatus = "NEEDS_REVIEW",
            copyrightStatus = "UNREVIEWED",
            sourceId = sourceId,
            sourcePage = source.pageReference,
            semanticFingerprint = fingerprint,
            selectionWeight = 1.0
        )
        return Draft(source, question)
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}
