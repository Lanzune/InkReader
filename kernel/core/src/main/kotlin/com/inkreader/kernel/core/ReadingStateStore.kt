// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 InkReader contributors

package com.inkreader.kernel.core

import android.content.Context

data class SavedReading(
    val uri: String,
    val title: String,
    val charOffset: Int,
    val encoding: String?,
)

/**
 * M2 的最小阅读状态：最后打开的书、进度和编码。
 *
 * 只保存 URI/标题/字符偏移，不复制书籍内容；使用 SharedPreferences 足够小。
 * 更大规模的最近阅读列表在后续替换为 SQLite。
 */
class ReadingStateStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("inkreader.reading", Context.MODE_PRIVATE)

    fun load(): SavedReading? {
        val uri = prefs.getString(KEY_URI, null) ?: return null
        val title = prefs.getString(KEY_TITLE, null) ?: return null
        val offset = prefs.getInt(KEY_OFFSET, 0)
        val encoding = prefs.getString(KEY_ENCODING, null)
        return SavedReading(uri, title, offset, encoding)
    }

    fun save(uri: String, title: String, charOffset: Int, encoding: String?) {
        prefs.edit()
            .putString(KEY_URI, uri)
            .putString(KEY_TITLE, title)
            .putInt(KEY_OFFSET, charOffset.coerceAtLeast(0))
            .putString(KEY_ENCODING, encoding)
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val KEY_URI = "uri"
        const val KEY_TITLE = "title"
        const val KEY_OFFSET = "offset"
        const val KEY_ENCODING = "encoding"
    }
}
