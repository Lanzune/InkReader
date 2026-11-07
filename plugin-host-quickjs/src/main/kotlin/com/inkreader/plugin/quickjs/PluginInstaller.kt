package com.inkreader.plugin.quickjs

import android.content.Context
import android.net.Uri
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

data class InstalledPlugin(
    val manifest: PluginManifest,
    val directory: File,
    val script: String,
)

/**
 * Installs a .inkp ZIP into the app's private plugin directory.
 *
 * The installer is deliberately strict: no DEX/JAR/SO/APK, no path traversal,
 * 5 MB total uncompressed limit, 1 MB script limit, manifest validated before
 * anything is written.
 */
object PluginInstaller {
    private const val MAX_TOTAL_BYTES = 5 * 1024 * 1024
    private const val MAX_ENTRIES = 200
    private const val MANIFEST = "manifest.json"

    fun install(context: Context, uri: Uri): InstalledPlugin {
        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
            readLimited(input, MAX_TOTAL_BYTES)
        } ?: throw PluginManifestException("无法读取插件文件")
        return installBytes(bytes, File(context.filesDir, "plugins"))
    }

    fun installBytes(bytes: ByteArray, pluginsDir: File): InstalledPlugin {
        if (bytes.size > MAX_TOTAL_BYTES) throw PluginManifestException("插件包超过 5 MB")
        val entries = readEntries(bytes)
        val manifestBytes = entries[MANIFEST]
            ?: throw PluginManifestException("插件缺少 manifest.json")
        val manifest = PluginManifestParser.parse(String(manifestBytes, Charsets.UTF_8))
        if (manifest.entry == MANIFEST) throw PluginManifestException("entry 不能是 manifest")
        val scriptBytes = entries[manifest.entry]
            ?: throw PluginManifestException("插件缺少 entry：${manifest.entry}")
        if (scriptBytes.size > PluginManifestParser.MAX_SCRIPT_BYTES) {
            throw PluginManifestException("插件脚本超过 1 MB")
        }
        val script = String(scriptBytes, Charsets.UTF_8)

        pluginsDir.mkdirs()
        val target = File(pluginsDir, manifest.id)
        if (target.exists()) target.deleteRecursively()
        if (!target.mkdirs()) throw PluginManifestException("无法创建插件目录")
        File(target, MANIFEST).writeBytes(manifestBytes)
        val entryFile = File(target, manifest.entry)
        entryFile.parentFile?.mkdirs()
        entryFile.writeBytes(scriptBytes)
        return InstalledPlugin(manifest, target, script)
    }

    fun findInstalled(pluginsDir: File): InstalledPlugin? {
        if (!pluginsDir.isDirectory) return null
        val candidates = pluginsDir.listFiles()
            ?.filter { it.isDirectory }
            ?.sortedBy { it.name }
            .orEmpty()
        for (directory in candidates) {
            val manifestFile = File(directory, MANIFEST)
            if (!manifestFile.isFile || manifestFile.length() > MAX_TOTAL_BYTES) continue
            val manifest = runCatching {
                PluginManifestParser.parse(manifestFile.readText())
            }.getOrNull() ?: continue
            val entryFile = File(directory, manifest.entry)
            if (!entryFile.isFile || entryFile.length() > PluginManifestParser.MAX_SCRIPT_BYTES) {
                continue
            }
            return InstalledPlugin(manifest, directory, entryFile.readText())
        }
        return null
    }

    fun uninstall(pluginsDir: File, id: String) {
        File(pluginsDir, id).deleteRecursively()
    }

    private fun readEntries(bytes: ByteArray): Map<String, ByteArray> {
        val entries = LinkedHashMap<String, ByteArray>()
        var total = 0
        var count = 0
        try {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    count += 1
                    if (count > MAX_ENTRIES) throw PluginManifestException("插件条目过多")
                    if (entry.isDirectory) {
                        zip.closeEntry()
                        continue
                    }
                    val name = normalize(entry.name)
                        ?: throw PluginManifestException("插件包含非法路径")
                    val lower = name.lowercase()
                    if (lower.endsWith(".dex") || lower.endsWith(".jar") ||
                        lower.endsWith(".so") || lower.endsWith(".apk")
                    ) {
                        throw PluginManifestException("插件不能包含 DEX/JAR/SO/APK")
                    }
                    val data = readLimited(zip, MAX_TOTAL_BYTES - total)
                    total += data.size
                    if (total > MAX_TOTAL_BYTES) {
                        throw PluginManifestException("插件解压后超过 5 MB")
                    }
                    entries[name] = data
                    zip.closeEntry()
                }
            }
        } catch (failure: PluginManifestException) {
            throw failure
        } catch (failure: Exception) {
            throw PluginManifestException("插件 ZIP 解析失败：${failure.message ?: "损坏"}")
        }
        return entries
    }

    private fun readLimited(input: InputStream, limit: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            total += read
            if (total > limit) throw PluginManifestException("插件条目超过限制")
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private fun normalize(name: String): String? {
        val parts = name.replace('\\', '/').split('/')
        val stack = ArrayDeque<String>()
        for (part in parts) {
            when (part) {
                "", "." -> Unit
                ".." -> {
                    if (stack.isEmpty()) return null
                    stack.removeLast()
                }
                else -> stack.addLast(part)
            }
        }
        return stack.joinToString("/").ifBlank { null }
    }
}
