package com.webfunny.monitor.model

import android.content.Context
import com.webfunny.monitor.core.WFConfig

/** APP_START → MobilePerformanceLog */
class StartupLog(
    config: WFConfig,
    context: Context,
    val startType: String,           // cold / warm / hot
    val processStartTime: Int,       // 进程创建到 Application.onCreate 开始，ms
    val applicationInitTime: Int,    // Application.onCreate 耗时，ms
    val splashScreenTime: Int,       // 闪屏耗时，ms
    val mainPageCreateTime: Int,     // 主页面 onCreate 耗时，ms
    val firstFrameRenderTime: Int    // 首帧渲染耗时（reportFullyDrawn），ms
) : BaseLog("APP_START", config, context) {

    val totalDuration: Int =
        processStartTime + applicationInitTime + splashScreenTime + firstFrameRenderTime

    override fun toMap(): Map<String, Any?> = super.toMap() + mapOf(
        "startType"           to startType,
        "processStartTime"    to processStartTime,
        "applicationInitTime" to applicationInitTime,
        "splashScreenTime"    to splashScreenTime,
        "mainPageCreateTime"  to mainPageCreateTime,
        "firstFrameRenderTime" to firstFrameRenderTime,
        "totalDuration"       to totalDuration
    )
}
