package com.webfunny.monitor

import android.content.Context
import android.os.SystemClock
import com.webfunny.monitor.collector.MemoryFpsCollector
import com.webfunny.monitor.core.WFConfig
import com.webfunny.monitor.core.WFUploader
import com.webfunny.monitor.model.PageLoadLog

/**
 * 手动页面耗时追踪（适用于 Fragment / Compose / 自定义页面）
 *
 * val tracker = WFMonitor.startPageTrace("HomeFragment")
 * tracker.finish()                        // 在数据加载并渲染完成后调用
 * tracker.finish(dataFetchTime = 200)     // 可传入分段耗时
 */
class PageTracker internal constructor(
    private val pageName: String,
    private val context: Context,
    private val config: WFConfig
) {
    private val startMs = SystemClock.elapsedRealtime()
    private val memoryBefore = MemoryFpsCollector.currentMemoryMb(context)
    private var finished = false

    fun finish(
        loadType: String = "navigation",
        dataFetchTime: Int = 0,
        viewBuildTime: Int = 0
    ) {
        if (finished) return
        finished = true

        try {
            val duration = (SystemClock.elapsedRealtime() - startMs).toInt()
            val log = PageLoadLog(
                config          = config,
                context         = context,
                pagePath        = pageName,
                loadType        = loadType,
                firstRenderTime = duration,
                onCreateTime    = duration,
                dataFetchTime   = dataFetchTime,
                viewBuildTime   = viewBuildTime,
                memoryBefore    = memoryBefore,
                memoryAfter     = MemoryFpsCollector.currentMemoryMb(context),
                avgFPS          = MemoryFpsCollector.lastAvgFps,
                jankCount       = MemoryFpsCollector.lastJankCount,
                severeJankCount = MemoryFpsCollector.lastSevereJankCount
            )
            WFUploader.enqueue(log)
        } catch (e: Exception) { /* 静默 */ }
    }
}
