// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 InkReader contributors

package com.inkreader.app

import android.content.Context
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.security.MessageDigest

/**
 * Persists the full-book page starts for one layout signature.
 *
 * The array holds global character offsets, so it can be applied to a preview
 * window that starts at a later chapter. A layout change (viewport, font size,
 * line spacing) produces a different signature and therefore a different file.
 *
 * Size is about 17 KB for a 4,000-page book; files are pruned to a small cap.
 */
class PageIndexStore(context: Context) {
    private val directory = File(context.filesDir, "page-index").apply { mkdirs() }

    fun load(bookKey: String, signature: String): IntArray? {
        val file = fileFor(bookKey, signature)
        if (!file.isFile) return null
        return runCatching {
            DataInputStream(file.inputStream().buffered()).use { input ->
                if (input.readInt() != MAGIC) return null
                if (input.readInt() != VERSION) return null
                val storedSignature = input.readUTF()
                if (storedSignature != signature) return null
                val count = input.readInt()
                if (count <= 0 || count > MAX_PAGES) return null
                IntArray(count) { input.readInt() }
            }
        }.getOrNull()
    }

    /** Never let a small preview index replace a complete book index. */
    fun save(bookKey: String, signature: String, starts: IntArray) {
        if (starts.isEmpty() || starts.size > MAX_PAGES) return
        val target = fileFor(bookKey, signature)
        val existing = load(bookKey, signature)
        if (existing != null && existing.size >= starts.size) return

        runCatching {
            val temporary = File(target.parentFile, "${target.name}.tmp")
            DataOutputStream(temporary.outputStream().buffered()).use { output ->
                output.writeInt(MAGIC)
                output.writeInt(VERSION)
                output.writeUTF(signature)
                output.writeInt(starts.size)
                starts.forEach(output::writeInt)
            }
            if (!temporary.renameTo(target)) {
                target.delete()
                temporary.renameTo(target)
            }
            prune()
        }
    }

    private fun fileFor(bookKey: String, signature: String): File {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$bookKey\u0000$signature".toByteArray())
            .joinToString("") { byte -> (byte.toInt() and 0xFF).toString(16).padStart(2, '0') }
        return File(directory, "$digest.bin")
    }

    private fun prune() {
        val files = directory.listFiles { file -> file.extension == "bin" }
            ?.sortedByDescending { it.lastModified() }
            ?: return
        var total = 0L
        files.forEachIndexed { index, file ->
            total += file.length()
            if (index >= MAX_FILES || total > MAX_BYTES) file.delete()
        }
    }

    private companion object {
        const val MAGIC = 0x49525049 // IRPI
        const val VERSION = 1
        const val MAX_PAGES = 20_000
        const val MAX_FILES = 20
        const val MAX_BYTES = 2L * 1024 * 1024
    }
}
