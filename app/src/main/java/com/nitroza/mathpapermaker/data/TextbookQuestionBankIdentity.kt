package com.nitroza.mathpapermaker.data

import java.security.MessageDigest

/** Stable IDs keep a teacher-approved source question from being inserted twice. */
internal object TextbookQuestionBankIdentity {
    fun create(
        bookFile: String,
        chapterId: String,
        sourcePage: Int,
        questionNumber: String,
        questionType: String,
        questionText: String,
        imageFile: String? = null
    ): String {
        val identity = listOf(
            bookFile.trim(),
            chapterId.trim(),
            sourcePage.toString(),
            questionNumber.trim(),
            questionType.trim(),
            questionText.trim(),
            imageFile.orEmpty()
        ).joinToString("|")
        return MessageDigest.getInstance("SHA-256")
            .digest(identity.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
    }
}
