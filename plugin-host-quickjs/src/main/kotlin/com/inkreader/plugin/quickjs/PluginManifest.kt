package com.inkreader.plugin.quickjs

import org.json.JSONObject

data class PluginManifest(
    val schemaVersion: Int,
    val id: String,
    val name: String,
    val version: String,
    val entry: String,
    val minHostApi: Int,
    val targetHostApi: Int,
    val capabilities: Set<String>,
)

class PluginManifestException(message: String) : Exception(message)

object PluginManifestParser {
    const val HOST_API = 1
    const val MAX_PLUGIN_BYTES = 5 * 1024 * 1024
    const val MAX_SCRIPT_BYTES = 1024 * 1024

    val allowedCapabilities: Set<String> = setOf(
        "reader.state.read",
        "theme.contribute",
        "command.contribute",
        "settings.readwrite",
        "storage.plugin",
        "log.write",
    )

    fun parse(json: String): PluginManifest {
        val root = try {
            JSONObject(json)
        } catch (failure: Exception) {
            throw PluginManifestException("manifest.json 不是合法 JSON")
        }
        val schemaVersion = root.optInt("schemaVersion", -1)
        if (schemaVersion != 1) {
            throw PluginManifestException("不支持的 manifest schemaVersion")
        }
        val id = root.optString("id").trim()
        if (!id.matches(Regex("[a-z][a-z0-9._-]{1,80}"))) {
            throw PluginManifestException("插件 id 不合法")
        }
        val name = root.optString("name").trim().takeIf { it.isNotBlank() }
            ?: throw PluginManifestException("缺少插件 name")
        val version = root.optString("version").trim().takeIf { it.isNotBlank() }
            ?: throw PluginManifestException("缺少插件 version")
        val entry = root.optString("entry").trim()
        if (entry.isBlank() ||
            entry.startsWith("/") ||
            entry.contains("..") ||
            !entry.endsWith(".js")
        ) {
            throw PluginManifestException("插件 entry 必须是容器内 .js 文件")
        }
        val api = root.optJSONObject("api")
            ?: throw PluginManifestException("缺少 api")
        val minHostApi = api.optInt("minHostApi", -1)
        val targetHostApi = api.optInt("targetHostApi", -1)
        if (minHostApi != HOST_API || targetHostApi != HOST_API) {
            throw PluginManifestException("插件 API 版本不匹配（host $HOST_API）")
        }
        val capabilitiesJson = root.optJSONArray("capabilities")
            ?: throw PluginManifestException("缺少 capabilities")
        val capabilities = buildSet {
            for (index in 0 until capabilitiesJson.length()) {
                add(capabilitiesJson.optString(index))
            }
        }
        if (capabilities.isEmpty()) {
            throw PluginManifestException("插件没有声明能力")
        }
        val unknown = capabilities - allowedCapabilities
        if (unknown.isNotEmpty()) {
            throw PluginManifestException("未知能力：${unknown.joinToString()}")
        }
        return PluginManifest(
            schemaVersion = schemaVersion,
            id = id,
            name = name,
            version = version,
            entry = entry,
            minHostApi = minHostApi,
            targetHostApi = targetHostApi,
            capabilities = capabilities,
        )
    }
}
