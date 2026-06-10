package com.webfunny.monitor.model

import android.content.Context
import com.webfunny.monitor.core.WFConfig

/** MOBILE_PAGE_LOAD → MobilePerformanceLog */
class PageLoadLog(
    config: WFConfig,
    context: Context,
    val pagePath: String,
    val loadType: String,         // initial / navigation / back
    val firstRenderTime: Int,     // ms
    val onCreateTime: Int,        // ms
    val dataFetchTime: Int,       // ms
    val viewBuildTime: Int,       // ms
    val memoryBefore: Int,        // MB
    val memoryAfter: Int,         // MB
    val avgFPS: Int,
    val jankCount: Int,
    val severeJankCount: Int
) : BaseLog("MOBILE_PAGE_LOAD", config, context) {

    val totalDuration: Int = firstRenderTime

    override fun toMap(): Map<String, Any?> = super.toMap() + mapOf(
        "pagePath"        to pagePath,
        "loadType"        to loadType,
        "firstRenderTime" to firstRenderTime,
        "onCreateTime"    to onCreateTime,
        "dataFetchTime"   to dataFetchTime,
        "viewBuildTime"   to viewBuildTime,
        "memoryBefore"    to memoryBefore,
        "memoryAfter"     to memoryAfter,
        "avgFPS"          to avgFPS,
        "jankCount"       to jankCount,
        "severeJankCount" to severeJankCount,
        "totalDuration"   to totalDuration
    )
}
