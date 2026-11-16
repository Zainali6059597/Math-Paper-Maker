package com.nitroza.mathpapermaker.data

import android.content.Context
import org.json.JSONObject
import java.security.MessageDigest

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
        val actualHashes = bundledPdfs.associateWith { file ->
            val digest = MessageDigest.getInstance("SHA-256")
            assetManager.open("books/$file").use { input ->
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
            digest.digest().joinToString("") { byte -> (byte.toInt() and 0xff).toString(16).padStart(2, '0') }
        }
        return parseManifest(root, bundledPdfs, board, grade, stream, subject, session, actualHashes)
    }

    internal fun parseManifest(
        rawJson: String,
        bundledPdfs: Set<String>,
        board: String,
        grade: Int,
        stream: String,
        subject: String,
        session: String,
        actualSha256: Map<String, String> = emptyMap()
    ): List<Chapter> {
        val json = JSONObject(rawJson)
        require(json.optInt("manifestVersion", -1) == 1) { "Unsupported textbook manifest version." }
        val books = json.optJSONArray("books") ?: return emptyList()
        // A single board/class/subject/session must resolve to exactly one PDF.
        // Merging two matching records can silently combine unrelated chapter maps.
        val matchingBooks = (0 until books.length()).mapNotNull { books.optJSONObject(it) }
            .filter { item ->
                item.optString("board") == board &&
                    item.optInt("grade", -1) == grade &&
                    item.optString("stream") == stream &&
                    item.optString("subject") == subject &&
                    item.optString("session") == session
            }
        if (matchingBooks.size != 1) return emptyList()
        val item = matchingBooks.single()
        val file = item.optString("file")
        if (file !in bundledPdfs) return emptyList()

        // A publicly downloadable PDF is not automatically licensed for redistribution.
        if (item.optString("licenseOrPermission") != "REDISTRIBUTION_CONFIRMED") return emptyList()
        if (item.optString("edition").isBlank() ||
            item.optString("language").isBlank() ||
            item.optString("publisher").isBlank() ||
            !item.optString("sourceUrl").startsWith("https://")
        ) return emptyList()
        val declaredPageCount = item.optInt("pageCount", -1)
        if (declaredPageCount < 1) return emptyList()
        val declaredHash = item.optString("sha256").lowercase()
        if (!declaredHash.matches(Regex("[0-9a-f]{64}")) ||
            actualSha256[file]?.lowercase() != declaredHash
        ) return emptyList()

        val chapterArray = item.optJSONArray("chapters") ?: return emptyList()
        val bookChapters = mutableListOf<Chapter>()
        for (chapterIndex in 0 until chapterArray.length()) {
            val chapter = chapterArray.optJSONObject(chapterIndex) ?: continue
            val id = chapter.optString("chapterId").trim()
            val title = chapter.optString("title").trim()
            val start = chapter.optInt("startPage", -1)
            val end = chapter.optInt("endPage", -1)
            if (id.isBlank() || title.isBlank() || start < 1 || end < start || end > declaredPageCount) continue
            bookChapters += Chapter(id, title, start, end)
        }

        // Ambiguous ranges can silently assign a question to the wrong chapter.
        // Reject the chapter map instead of choosing whichever range appears first.
        val orderedRanges = bookChapters.sortedBy { it.startPage }
        val hasDuplicateIds = bookChapters.map { it.id }.distinct().size != bookChapters.size
        val hasOverlaps = orderedRanges.zipWithNext().any { (first, second) ->
            second.startPage <= first.endPage
        }
        if (hasDuplicateIds || hasOverlaps || bookChapters.isEmpty()) return emptyList()
        return bookChapters.sortedBy { it.id.toIntOrNull() ?: Int.MAX_VALUE }
    }
}
