package com.inkreader.kernel.api

data class ReaderTheme(
    val id: String,
    val name: String,
    val background: Int,
    val foreground: Int,
    val accent: Int,
    val dark: Boolean,
)

object BuiltInThemes {
    val Light = ReaderTheme(
        id = "builtin.light",
        name = "日间",
        background = 0xFFFAF7F0.toInt(),
        foreground = 0xFF2A2A2A.toInt(),
        accent = 0xFF2F5F9E.toInt(),
        dark = false,
    )

    val Dark = ReaderTheme(
        id = "builtin.dark",
        name = "夜间",
        background = 0xFF121212.toInt(),
        foreground = 0xFFC9C9C9.toInt(),
        accent = 0xFF8AB4F8.toInt(),
        dark = true,
    )
}

data class Locator(
    val chapterId: String,
    val charOffset: Int = 0,
)

data class PageInfo(
    val page: Int,
    val pageCount: Int,
    val charOffset: Int = 0,
)
