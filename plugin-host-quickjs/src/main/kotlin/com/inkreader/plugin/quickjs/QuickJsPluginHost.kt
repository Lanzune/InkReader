package com.inkreader.plugin.quickjs

import android.graphics.Color
import com.dokar.quickjs.QuickJs
import com.inkreader.kernel.api.ReaderTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

data class PluginSnapshot(
    val theme: ReaderTheme,
    val commandResult: String,
    val manifest: PluginManifest? = null,
)

/**
 * QuickJS plugin host.
 *
 * M6 adds manifest validation, a 2 second activation timeout and an explicit
 * capability whitelist. Plugins still run in-process on a dedicated dispatcher;
 * process isolation is deferred to v1.1.
 */
class QuickJsPluginHost {

    suspend fun load(
        script: String,
        manifest: PluginManifest? = null,
    ): PluginSnapshot = withContext(Dispatchers.Default) {
        if (script.toByteArray(Charsets.UTF_8).size > PluginManifestParser.MAX_SCRIPT_BYTES) {
            throw IllegalStateException("插件脚本超过 1 MB")
        }
        if (manifest != null) {
            if ("theme.contribute" !in manifest.capabilities) {
                throw IllegalStateException("插件未声明 theme.contribute")
            }
            if ("command.contribute" !in manifest.capabilities) {
                throw IllegalStateException("插件未声明 command.contribute")
            }
        }
        withTimeout(2_000) {
            val quickJs = QuickJs.create(Dispatchers.Default)
            try {
                val themeJson = quickJs.evaluate<String>(
                    "$script\nJSON.stringify(inkPlugin.theme);",
                )
                val commandResult = quickJs.evaluate<String>(
                    "$script\ninkPlugin.command.render(0.42);",
                )
                PluginSnapshot(parseTheme(themeJson), commandResult, manifest)
            } finally {
                quickJs.close()
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
