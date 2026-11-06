package com.inkreader.kernel.core

import com.inkreader.kernel.api.BookDocument
import com.inkreader.kernel.api.BookSource
import com.inkreader.kernel.api.FormatProvider
import com.inkreader.kernel.api.OpenContext
import com.inkreader.kernel.api.UnsupportedBookFormatException

/**
 * 按注册顺序探测并打开书籍。
 *
 * M1 只注册 TXT；后续格式模块通过同一个入口接入。
 */
class BookOpener(
    private val providers: List<FormatProvider>,
) {
    fun open(source: BookSource): BookDocument {
        val candidates = providers.mapNotNull { provider ->
            provider.probe(source)?.let { match -> provider to match.confidence }
        }
        val selected = candidates.maxByOrNull { it.second }?.first
            ?: throw UnsupportedBookFormatException("暂不支持该文件格式：${source.displayName}")

        return selected.open(
            source,
            OpenContext(displayName = source.displayName, mimeType = source.mimeType),
        )
    }
}
