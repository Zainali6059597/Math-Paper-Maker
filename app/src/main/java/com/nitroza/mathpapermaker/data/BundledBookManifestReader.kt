package com.nitroza.mathpapermaker.data

import android.content.Context
import org.json.JSONObject

/**
 * Reads the optional, offline books/book-manifest.json file.
 * A chapter is offered only when its referenced PDF is actually bundled and its
 * board/class/subject/session identity matches the current paper configuration.
 */
object BundledBookManifestReader {
    data class Chapter(val id: String, val title: String, val startPage: Int, val endPage: Int)
    data class Book(
        val file: String,
        val board: String,
        val grade: Int,
        val stream: String,
        val subject: String,
        val session: String,
        val chapters: List<Chapter>
    )

    fun chaptersFor(
        context: Context,
        board: String,
        grade: Int,
        stream: String,
        subject: String,
        session: String
    ): List<Chapter> {
        val assetManager = context.applicationContext.assets
        val bundledPdfs = assetManager.list("books").orEmpty()
            .filter { it.endsWith(".pdf", ignoreCase = true) }.toSet()
        if ("book-manifest.json" !in assetManager.list("books").orEmpty()) return emptyList()

        val root = assetManager.open("books/book-manifest.json").bufferedReader().use { it.readText() }
        return parseManifest(root, bundledPdfs, board, grade, stream, subject, session)
    }

    internal fun parseManifest(
        rawJson: String,
        bundledPdfs: Set<String>,
        board: String,
        grade: Int,
        stream: String,
        subject: String,
        session: String
    ): List<Chapter> {
        val json = JSONObject(rawJson)
        require(json.optInt("manifestVersion", -1) == 1) { "Unsupported textbook manifest version." }
        val books = json.optJSONArray("books") ?: return emptyList()
        val chapters = mutableListOf<Chapter>()

        for (index in 0 until books.length()) {
            val item = books.optJSONObject(index) ?: continue
            val file = item.optString("file")
            if (file !in bundledPdfs) continue
            if (item.optString("board") != board ||
                item.optInt("grade", -1) != grade ||
                item.optString("stream") != stream ||
                item.optString("subject") != subject ||
                item.optString("session") != session
            ) continue

            val chapterArray = item.optJSONArray("chapters") ?: continue
            for (chapterIndex in 0 until chapterArray.length()) {
                val chapter = chapterArray.optJSONObject(chapterIndex) ?: continue
                val id = chapter.optString("chapterId").trim()
                val title = chapter.optString("title").trim()
                val start = chapter.optInt("startPage", -1)
                val end = chapter.optInt("endPage", -1)
                if (id.isBlank() || title.isBlank() || start < 1 || end < start) continue
                chapters += Chapter(id, title, start, end)
            }
        }
        return chapters.distinctBy { it.id }.sortedBy { it.id.toIntOrNull() ?: Int.MAX_VALUE }
    }
}
