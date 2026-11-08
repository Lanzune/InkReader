// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 InkReader contributors

package com.inkreader.plugin.quickjs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginManifestParserTest {
    @Test
    fun parsesValidManifest() {
        val manifest = PluginManifestParser.parse(
            """
            {
              "schemaVersion": 1,
              "id": "dev.example.paper",
              "name": "Paper",
              "version": "1.0.0",
              "entry": "main.js",
              "api": {"minHostApi": 1, "targetHostApi": 1},
              "capabilities": ["theme.contribute", "command.contribute"]
            }
            """.trimIndent(),
        )
        assertEquals("dev.example.paper", manifest.id)
        assertEquals("main.js", manifest.entry)
        assertEquals(2, manifest.capabilities.size)
    }

    @Test
    fun acceptsProgressContribution() {
        val manifest = PluginManifestParser.parse(
            """
            {
              "schemaVersion": 1,
              "id": "dev.inkreader.page-number",
              "name": "Page Number",
              "version": "1.0.0",
              "entry": "page-number.js",
              "api": {"minHostApi": 1, "targetHostApi": 1},
              "capabilities": ["progress.contribute", "reader.state.read", "reader.pagination"]
            }
            """.trimIndent(),
        )
        assertTrue("progress.contribute" in manifest.capabilities)
        assertTrue("reader.pagination" in manifest.capabilities)
    }

    @Test
    fun rejectsUnknownCapability() {
        val failure = runCatching {
            PluginManifestParser.parse(
                """
                {
                  "schemaVersion": 1,
                  "id": "dev.example.paper",
                  "name": "Paper",
                  "version": "1.0.0",
                  "entry": "main.js",
                  "api": {"minHostApi": 1, "targetHostApi": 1},
                  "capabilities": ["android.intent"]
                }
                """.trimIndent(),
            )
        }.exceptionOrNull()
        assertTrue(failure is PluginManifestException)
    }

    @Test
    fun rejectsPathTraversalEntry() {
        val failure = runCatching {
            PluginManifestParser.parse(
                """
                {
                  "schemaVersion": 1,
                  "id": "dev.example.paper",
                  "name": "Paper",
                  "version": "1.0.0",
                  "entry": "../main.js",
                  "api": {"minHostApi": 1, "targetHostApi": 1},
                  "capabilities": ["theme.contribute"]
                }
                """.trimIndent(),
            )
        }.exceptionOrNull()
        assertTrue(failure is PluginManifestException)
    }
}
