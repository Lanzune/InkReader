package com.inkreader.kernel.core

import com.inkreader.kernel.api.KernelVersion

/**
 * M0 的最小内核占位实现。
 *
 * 后续会在这里加入 ReaderSession、插件注册表、能力代理和存储适配；
 * 现在只用于验证模块依赖和构建链路。
 */
class KernelEngine {
    fun versionLabel(): String = "${KernelVersion.NAME} API ${KernelVersion.API}"
}
