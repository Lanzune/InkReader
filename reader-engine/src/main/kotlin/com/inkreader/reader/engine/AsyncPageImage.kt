// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 InkReader contributors

package com.inkreader.reader.engine

/**
 * A drawable that can decode its bitmap before the reader first paints the page.
 * Implementations must be safe to call from the main thread; decoding happens
 * on an internal worker.
 */
interface AsyncPageImage {
    val isReady: Boolean
    fun preload(onReady: () -> Unit)
}
