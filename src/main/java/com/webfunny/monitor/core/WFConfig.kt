package com.webfunny.monitor.core

/**
 * SDK 初始化配置
 *
 * 使用示例：
 * WFConfig(
 *     serverUrl    = "https://your-server.com",
 *     webMonitorId = "abc123",
 *     enableCrash  = true
 * )
 */
data class WFConfig(
    /** 监控服务器地址，不含路径，如 https://monitor.example.com */
    val serverUrl: String,

    /** 项目监控 ID，在 Webfunny 控制台创建项目后获取 */
    val webMonitorId: String,

    /** 是否开启 Java 层崩溃采集（默认 true） */
    val enableCrash: Boolean = true,

    /** 是否开启 ANR 检测（默认 true） */
    val enableAnr: Boolean = true,

    /** 是否开启 OkHttp 网络请求拦截（默认 true） */
    val enableHttp: Boolean = true,

    /** 是否开启 FPS 采样（默认 true） */
    val enableFps: Boolean = true,

    /** 是否开启内存采样（默认 true） */
    val enableMemory: Boolean = true,

    /** FPS / 内存采样间隔，单位秒（默认 2s） */
    val samplingIntervalSec: Long = 2L,

    /** 日志队列最大条数，超出后强制上报（默认 20） */
    val maxQueueSize: Int = 20,

    /** 队列定时刷新间隔，单位毫秒（默认 10s） */
    val flushIntervalMs: Long = 10_000L,

    /** ANR 检测阈值，单位毫秒（默认 5s） */
    val anrThresholdMs: Long = 5_000L,

    /** 是否在 Debug 模式下打印 SDK 日志（默认 false） */
    val debugLog: Boolean = false,

    /** HTTP 请求/响应体截取上限，单位字节（默认 8KB） */
    val httpBodyMaxBytes: Int = 8192
) {
    /** 完整上报地址 */
    internal val uploadUrl: String
        get() = "$serverUrl/wfMonitor/upLogs"
}
