// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 InkReader contributors

package com.inkreader.plugin.quickjs

import android.graphics.Color
import com.dokar.quickjs.QuickJs
import com.inkreader.kernel.api.ReaderTheme
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.util.concurrent.Executors

/** Real reader state passed to a progress-label plugin. */
data class ReaderProgressState(
    val page: Int,
    val pageCount: Int,
    val percent: Double,
    val charOffset: Int,
    val totalChars: Int,
) {
    fun toJson(): String = JSONObject()
        .put("page", page)
        .put("pageCount", pageCount)
        .put("percent", percent)
        .put("charOffset", charOffset)
        .put("totalChars", totalChars)
        .toString()
}

class PluginSnapshot internal constructor(
    val theme: ReaderTheme?,
    val commandResult: String?,
    val manifest: PluginManifest?,
    val hasProgress: Boolean,
    private val renderer: ProgressRenderer? = null,
) : AutoCloseable {
    suspend fun renderProgress(state: ReaderProgressState): String? =
        renderer?.render(state)

    override fun close() {
        renderer?.close()
    }
}

/**
 * QuickJS plugin host.
 *
 * M6 adds manifest validation, a 2 second activation timeout and an explicit
 * capability whitelist. M9 adds a live progress-label contribution: the plugin
 * runtime stays alive so the reader can ask it for a label on every page.
 */
class QuickJsPluginHost {

    suspend fun load(
        script: String,
        manifest: PluginManifest? = null,
    ): PluginSnapshot {
        if (script.toByteArray(Charsets.UTF_8).size > PluginManifestParser.MAX_SCRIPT_BYTES) {
            throw IllegalStateException("插件脚本超过 1 MB")
        }
        val hasTheme = manifest?.capabilities?.contains("theme.contribute") ?: true
        val hasCommand = manifest?.capabilities?.contains("command.contribute") ?: true
        val hasProgress = manifest?.capabilities?.contains("progress.contribute") ?: false
        if (!hasTheme && !hasCommand && !hasProgress) {
            throw IllegalStateException("插件没有可执行的贡献能力")
        }

        val dispatcher = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "inkreader-plugin").apply { isDaemon = true }
        }.asCoroutineDispatcher()

        return withContext(dispatcher) {
            val quickJs = QuickJs.create(dispatcher)
            try {
                val snapshot = withTimeout(2_000) {
                    val theme = if (hasTheme) {
                        val themeJson = quickJs.evaluate<String>(
                            "$script\nJSON.stringify(inkPlugin.theme);",
                        )
                        parseTheme(themeJson)
                    } else {
                        null
                    }
                    val commandResult = if (hasCommand) {
                        quickJs.evaluate<String>(
                            "$script\ninkPlugin.command.render(0.42);",
                        )
                    } else {
                        null
                    }
                    if (hasProgress) {
                        val hasRenderer = quickJs.evaluate<String>(
                            "$script\n(typeof inkPlugin.progress === 'object' " +
                                "&& typeof inkPlugin.progress.render === 'function') " +
                                "? 'true' : 'false';",
                        ) == "true"
                        if (!hasRenderer) {
                            throw IllegalStateException("插件缺少 inkPlugin.progress.render")
                        }
                    }
                    PluginSnapshot(
                        theme = theme,
                        commandResult = commandResult,
                        manifest = manifest,
                        hasProgress = hasProgress,
                        renderer = if (hasProgress) {
                            ProgressRenderer(quickJs, dispatcher)
                        } else {
                            null
                        },
                    )
                }
                if (!hasProgress) {
                    quickJs.close()
                    dispatcher.close()
                }
                snapshot
            } catch (failure: Throwable) {
                runCatching { quickJs.close() }
                dispatcher.close()
                throw failure
            }
        }
    }

    private fun parseTheme(json: String): ReaderTheme {
        val root = JSONObject(json)
        return ReaderTheme(
            id = root.optString("id", "plugin.unknown"),
            name = root.optString("name", "插件主题"),
            background = parseColor(root.getString("background")),
            foreground = parseColor(root.getString("foreground")),
            accent = parseColor(root.optString("accent", "#2F5F9E")),
            dark = root.optBoolean("dark", false),
        )
    }

    private fun parseColor(value: String): Int = try {
        Color.parseColor(value)
    } catch (_: IllegalArgumentException) {
        Color.BLACK
    }
}

internal class ProgressRenderer(
    private val quickJs: QuickJs,
    private val dispatcher: ExecutorCoroutineDispatcher,
) {
    private val mutex = Mutex()

    suspend fun render(state: ReaderProgressState): String? = withContext(dispatcher) {
        mutex.withLock {
            withTimeoutOrNull(500) {
                quickJs.evaluate<String>(
                    "inkPlugin.progress.render(${state.toJson()});",
                )
            }?.takeIf { it.isNotBlank() }
        }
    }

    fun close() {
        runCatching { quickJs.close() }
        dispatcher.close()
    }
}
