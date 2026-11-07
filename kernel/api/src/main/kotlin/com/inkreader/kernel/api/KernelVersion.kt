// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 InkReader contributors

package com.inkreader.kernel.api

/**
 * 公开内核 API 的版本信息。
 *
 * 该模块保持纯 Kotlin/JVM，不依赖 Android，便于单元测试和未来跨宿主复用。
 */
object KernelVersion {
    const val API: Int = 1
    const val NAME: String = "PageKernel"
}
