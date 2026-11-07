package com.inkreader.plugin.quickjs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class PluginInstallerTest {
    @Test
    fun installsValidInkp() {
        val root = Files.createTempDirectory("inkp-test").toFile()
        val installed = PluginInstaller.installBytes(
            zipOf(
                "manifest.json" to """
                    {
                      "schemaVersion": 1,
                      "id": "dev.example.test",
                      "name": "Test",
                      "version": "1.0.0",
                      "entry": "main.js",
                      "api": {"minHostApi": 1, "targetHostApi": 1},
                      "capabilities": ["theme.contribute", "command.contribute"]
                    }
                """.trimIndent(),
                "main.js" to "const inkPlugin = {};",
            ),
            root,
        )
        assertEquals("dev.example.test", installed.manifest.id)
        assertTrue(installed.directory.isDirectory)
        assertNotNull(PluginInstaller.findInstalled(root))
    }

    @Test
    fun rejectsPathTraversal() {
        val root = Files.createTempDirectory("inkp-test").toFile()
        val failure = runCatching {
            PluginInstaller.installBytes(zipOf("../evil.js" to "x"), root)
        }.exceptionOrNull()
        assertTrue(failure is PluginManifestException)
    }

    @Test
    fun rejectsNativeCode() {
        val root = Files.createTempDirectory("inkp-test").toFile()
        val failure = runCatching {
            PluginInstaller.installBytes(
                zipOf(
                    "manifest.json" to """
                        {
                          "schemaVersion": 1,
                          "id": "dev.example.native",
                          "name": "Native",
                          "version": "1.0.0",
                          "entry": "main.js",
                          "api": {"minHostApi": 1, "targetHostApi": 1},
                          "capabilities": ["theme.contribute", "command.contribute"]
                        }
                    """.trimIndent(),
                    "main.js" to "x",
                    "lib/evil.so" to "x",
                ),
                root,
            )
        }.exceptionOrNull()
        assertTrue(failure is PluginManifestException)
    }

    private fun zipOf(vararg entries: Pair<String, String>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            for ((name, value) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(value.toByteArray())
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }
}
