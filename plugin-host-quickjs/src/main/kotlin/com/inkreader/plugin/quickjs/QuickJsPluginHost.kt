package com.inkreader.plugin.quickjs

import android.graphics.Color
import com.dokar.quickjs.QuickJs
import com.inkreader.kernel.api.ReaderTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class PluginSnapshot(
    val theme: ReaderTheme,
    val commandResult: String,
)

/**
 * M1b 的最小 QuickJS 宿主：执行 bundled 示例插件，返回一个主题和一个命令结果。
 *
 * 这里验证的是“脚本运行 + 结果进入阅读器”，完整的 .inkp 安装、能力授权、
 * 超时/内存限制和插件进程隔离在后续阶段补。
 */
class QuickJsPluginHost {

    suspend fun load(script: String): PluginSnapshot = withContext(Dispatchers.Default) {
        val quickJs = QuickJs.create(Dispatchers.Default)
        try {
            val themeJson = quickJs.evaluate<String>(
                "$script\nJSON.stringify(inkPlugin.theme);",
            )
            val commandResult = quickJs.evaluate<String>(
                "$script\ninkPlugin.command.render(0.42);",
            )
            PluginSnapshot(parseTheme(themeJson), commandResult)
        } finally {
            quickJs.close()
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
