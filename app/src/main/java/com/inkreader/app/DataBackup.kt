// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 InkReader contributors

package com.inkreader.app

import android.content.Context
import android.net.Uri
import com.inkreader.kernel.core.ReadingStateStore
import com.inkreader.kernel.core.SavedReading
import com.inkreader.plugin.quickjs.InstalledPlugin
import com.inkreader.plugin.quickjs.PluginInstaller
import com.inkreader.plugin.quickjs.PluginManifestParser
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

data class RestoredBackup(
    val saved: SavedReading?,
    val settings: ReaderSettings,
    val pluginEnabled: Boolean,
    val pageNumberEnabled: Boolean,
    val installedPlugin: InstalledPlugin?,
    val bookRestored: Boolean,
)

/**
 * Export/import of the small amount of state InkReader owns.
 *
 * The book itself is copied into the backup because SAF URI permissions do not
 * survive an uninstall. A restored book is stored under app-private files and
 * opened through FileBookSource.
 */
object DataBackup {
    private const val SCHEMA = 1
    private const val MAX_BACKUP_BYTES = 512L * 1024 * 1024
    private const val MAX_META_BYTES = 2L * 1024 * 1024
    private const val MAX_BOOK_BYTES = 480L * 1024 * 1024
    private const val BACKUP_JSON = "backup.json"
    private const val BOOK_ENTRY = "book/"
    private const val PLUGIN_MANIFEST = "plugin/manifest.json"
    private const val PLUGIN_SCRIPT = "plugin/main.js"

    fun export(
        context: Context,
        target: Uri,
        saved: SavedReading?,
        settings: ReaderSettings,
        pluginEnabled: Boolean,
        pageNumberEnabled: Boolean,
        installedPlugin: InstalledPlugin?,
    ) {
        val root = JSONObject()
        root.put("schema", SCHEMA)
        root.put("createdAt", System.currentTimeMillis())
        root.put("pluginEnabled", pluginEnabled)
        root.put("pageNumberEnabled", pageNumberEnabled)
        root.put("settings", settingsJson(settings))
        if (saved != null) {
            root.put(
                "saved",
                JSONObject()
                    .put("uri", saved.uri)
                    .put("title", saved.title)
                    .put("charOffset", saved.charOffset)
                    .put("encoding", saved.encoding),
            )
            root.put("bookName", bookFileName(saved))
        }
        if (installedPlugin != null) {
            root.put("hasPlugin", true)
            root.put(
                "plugin",
                JSONObject()
                    .put("id", installedPlugin.manifest.id)
                    .put("name", installedPlugin.manifest.name)
                    .put("version", installedPlugin.manifest.version),
            )
        }

        val output = context.contentResolver.openOutputStream(target, "w")
            ?: throw IllegalStateException("无法创建备份文件")
        output.use { raw ->
            ZipOutputStream(BufferedOutputStream(raw)).use { zip ->
                zip.putNextEntry(ZipEntry(BACKUP_JSON))
                zip.write(root.toString().toByteArray(Charsets.UTF_8))
                zip.closeEntry()

                if (saved != null) {
                    openBookInput(context, saved.uri)?.use { input ->
                        zip.putNextEntry(ZipEntry(BOOK_ENTRY + bookFileName(saved)))
                        copyLimited(input, zip, MAX_BOOK_BYTES)
                        zip.closeEntry()
                    }
                }

                if (installedPlugin != null) {
                    zip.putNextEntry(ZipEntry(PLUGIN_MANIFEST))
                    zip.write(manifestJson(installedPlugin).toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                    zip.putNextEntry(ZipEntry(PLUGIN_SCRIPT))
                    zip.write(installedPlugin.script.toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                }
            }
        }
    }

    fun import(context: Context, source: Uri): RestoredBackup {
        val temp = File.createTempFile("inkreader-import-", ".zip", context.cacheDir)
        try {
            val input = context.contentResolver.openInputStream(source)
                ?: throw IllegalStateException("无法读取备份文件")
            input.use { raw ->
                FileOutputStream(temp).use { output ->
                    copyLimited(raw, output, MAX_BACKUP_BYTES)
                }
            }
            ZipFile(temp).use { zip ->
                val meta = zip.getEntry(BACKUP_JSON)
                    ?: throw IllegalStateException("备份缺少 backup.json")
                val root = JSONObject(readEntry(zip, meta, MAX_META_BYTES).toString(Charsets.UTF_8))
                if (root.optInt("schema", 0) != SCHEMA) {
                    throw IllegalStateException("不支持的备份版本")
                }
                val settings = settingsFromJson(root.optJSONObject("settings"))
                val pluginEnabled = root.optBoolean("pluginEnabled", true)
                val pageNumberEnabled = root.optBoolean("pageNumberEnabled", false)

                var saved: SavedReading? = null
                var bookRestored = false
                val bookName = root.optString("bookName", "")
                val savedJson = root.optJSONObject("saved")
                if (bookName.isNotBlank() && savedJson != null) {
                    val entry = zip.getEntry(BOOK_ENTRY + bookName)
                    if (entry != null) {
                        val dir = File(context.filesDir, "restored")
                        dir.mkdirs()
                        val file = File(dir, bookName.substringAfterLast('/'))
                        zip.getInputStream(entry).use { raw ->
                            FileOutputStream(file).use { output ->
                                copyLimited(raw, output, MAX_BOOK_BYTES)
                            }
                        }
                        saved = SavedReading(
                            uri = Uri.fromFile(file).toString(),
                            title = savedJson.optString("title", file.name),
                            charOffset = savedJson.optInt("charOffset", 0).coerceAtLeast(0),
                            encoding = savedJson.optString("encoding").ifBlank { null },
                        )
                        bookRestored = true
                    }
                }

                val installedPlugin = restorePlugin(context, zip)
                return RestoredBackup(
                    saved,
                    settings,
                    pluginEnabled,
                    pageNumberEnabled,
                    installedPlugin,
                    bookRestored,
                )
            }
        } finally {
            temp.delete()
        }
    }

    private fun restorePlugin(context: Context, zip: ZipFile): InstalledPlugin? {
        val manifestEntry = zip.getEntry(PLUGIN_MANIFEST) ?: return null
        val scriptEntry = zip.getEntry(PLUGIN_SCRIPT) ?: return null
        val manifest = PluginManifestParser.parse(
            readEntry(zip, manifestEntry, MAX_META_BYTES).toString(Charsets.UTF_8),
        )
        val scriptBytes = readEntry(zip, scriptEntry, PluginManifestParser.MAX_SCRIPT_BYTES.toLong())
        val pluginsDir = File(context.filesDir, "plugins")
        val target = File(pluginsDir, manifest.id)
        if (target.exists()) target.deleteRecursively()
        if (!target.mkdirs()) throw IllegalStateException("无法恢复插件目录")
        File(target, "manifest.json").writeBytes(
            readEntry(zip, manifestEntry, MAX_META_BYTES),
        )
        File(target, "main.js").writeBytes(scriptBytes)
        return PluginInstaller.findInstalled(pluginsDir)
    }

    private fun openBookInput(context: Context, uriString: String): InputStream? {
        return runCatching {
            val uri = Uri.parse(uriString)
            if (uri.scheme == "file") {
                FileInputStream(File(uri.path ?: return null))
            } else {
                context.contentResolver.openInputStream(uri)
            }
        }.getOrNull()
    }

    private fun readEntry(zip: ZipFile, entry: ZipEntry, limit: Long): ByteArray {
        if (entry.size > limit) throw IllegalStateException("备份条目过大")
        return zip.getInputStream(entry).use { readLimited(it, limit) }
    }

    private fun readLimited(input: InputStream, limit: Long): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            total += read
            if (total > limit) throw IllegalStateException("备份条目超过限制")
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private fun copyLimited(input: InputStream, output: OutputStream, limit: Long): Long {
        val buffer = ByteArray(32 * 1024)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            total += read
            if (total > limit) throw IllegalStateException("备份文件超过限制")
            output.write(buffer, 0, read)
        }
        return total
    }

    private fun bookFileName(saved: SavedReading): String {
        val raw = "${saved.title} ${saved.uri}".lowercase()
        val extension = listOf("epub", "pdf", "txt", "md", "markdown", "xhtml", "html", "htm")
            .firstOrNull { raw.contains(".$it") }
            ?: "bin"
        return "book.$extension"
    }

    private fun settingsJson(settings: ReaderSettings): JSONObject = JSONObject()
        .put("themeMode", settings.themeMode.name)
        .put("fontSizeSp", settings.fontSizeSp.toDouble())
        .put("lineSpacing", settings.lineSpacing.toDouble())
        .put("horizontalPaddingDp", settings.horizontalPaddingDp)
        .put("brightness", settings.brightness?.toDouble() ?: JSONObject.NULL)
        .put("pageTurnAnimation", settings.pageTurnAnimation)

    private fun settingsFromJson(json: JSONObject?): ReaderSettings {
        if (json == null) return ReaderSettings()
        val mode = runCatching {
            ReaderThemeMode.valueOf(json.optString("themeMode", ReaderThemeMode.SYSTEM.name))
        }.getOrDefault(ReaderThemeMode.SYSTEM)
        return ReaderSettings(
            themeMode = mode,
            fontSizeSp = json.optDouble("fontSizeSp", 20.0).toFloat().coerceIn(14f, 30f),
            lineSpacing = json.optDouble("lineSpacing", 1.5).toFloat().coerceIn(1.2f, 2.0f),
            horizontalPaddingDp = json.optInt("horizontalPaddingDp", 24).coerceIn(8, 40),
            brightness = if (json.isNull("brightness")) {
                null
            } else {
                json.optDouble("brightness", 1.0).toFloat().coerceIn(0.01f, 1f)
            },
            pageTurnAnimation = json.optBoolean("pageTurnAnimation", true),
        )
    }

    private fun manifestJson(plugin: InstalledPlugin): String {
        val capabilities = JSONArray()
        plugin.manifest.capabilities.forEach { capabilities.put(it) }
        return JSONObject()
            .put("schemaVersion", plugin.manifest.schemaVersion)
            .put("id", plugin.manifest.id)
            .put("name", plugin.manifest.name)
            .put("version", plugin.manifest.version)
            .put("entry", plugin.manifest.entry)
            .put(
                "api",
                JSONObject()
                    .put("minHostApi", plugin.manifest.minHostApi)
                    .put("targetHostApi", plugin.manifest.targetHostApi),
            )
            .put("capabilities", capabilities)
            .toString()
    }
}
