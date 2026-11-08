// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 InkReader contributors

package com.inkreader.app

import android.content.Context
import com.inkreader.kernel.api.ChapterMark
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/**
 * Small per-book spine index persisted after a full parse.
 *
 * The map lets the next open jump straight to the chapter that contains the
 * saved position and preview only that neighbourhood, instead of parsing the
 * whole book before the first page can be painted. Entries are a few hundred
 * bytes per book; the book itself is never copied here.
 */
class BookIndexStore(context: Context) {
    private val directory = File(context.filesDir, "book-index").apply { mkdirs() }

    fun load(bookKey: String): List<ChapterMark> {
        val file = fileFor(bookKey)
        if (!file.isFile) return emptyList()
        return runCatching {
            val root = JSONObject(file.readText())
            val array = root.optJSONArray(KEY_CHAPTERS) ?: return emptyList()
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val path = item.optString(KEY_PATH)
                    if (path.isBlank()) continue
                    add(
                        ChapterMark(
                            path = path,
                            charOffset = item.optInt(KEY_OFFSET).coerceAtLeast(0),
                            length = item.optInt(KEY_LENGTH).coerceAtLeast(0),
                            spineIndex = item.optInt(KEY_SPINE).coerceAtLeast(0),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    /** Never replace a complete index with a shorter preview index. */
    fun save(bookKey: String, marks: List<ChapterMark>) {
        if (marks.isEmpty()) return
        if (load(bookKey).size > marks.size) return
        runCatching {
            val array = JSONArray()
            marks.forEach { mark ->
                array.put(
                    JSONObject()
                        .put(KEY_PATH, mark.path)
                        .put(KEY_OFFSET, mark.charOffset)
                        .put(KEY_LENGTH, mark.length)
                        .put(KEY_SPINE, mark.spineIndex),
                )
            }
            val root = JSONObject().put(KEY_CHAPTERS, array)
            val target = fileFor(bookKey)
            val temporary = File(target.parentFile, "${target.name}.tmp")
            temporary.writeText(root.toString())
            if (!temporary.renameTo(target)) {
                target.delete()
                temporary.renameTo(target)
            }
        }
    }

    private fun fileFor(bookKey: String): File {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(bookKey.toByteArray())
            .joinToString("") { byte -> (byte.toInt() and 0xFF).toString(16).padStart(2, '0') }
        return File(directory, "$digest.json")
    }

    private companion object {
        const val KEY_CHAPTERS = "chapters"
        const val KEY_PATH = "path"
        const val KEY_OFFSET = "offset"
        const val KEY_LENGTH = "length"
        const val KEY_SPINE = "spine"
    }
}
