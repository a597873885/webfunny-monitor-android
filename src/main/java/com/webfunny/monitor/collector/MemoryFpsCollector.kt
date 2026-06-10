package com.webfunny.monitor.collector

import android.app.ActivityManager
import android.content.Context
import android.view.Choreographer
import com.webfunny.monitor.core.WFConfig
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * 内存 + FPS 周期采样
 *
 * FPS 原理：利用 Choreographer.FrameCallback 统计每帧间隔
 *   - 帧间隔 > 16ms（< 60fps）计为卡顿
 *   - 帧间隔 > 700ms 计为严重卡顿
 *
 * 内存原理：ActivityManager.getMemoryInfo() 系统 API，零风险
 *
 * 采集结果通过公开属性供 PageCollector 在页面维度读取
 */
object MemoryFpsCollector {

    var lastAvgFps: Int = 60
    var lastJankCount: Int = 0
    var lastSevereJankCount: Int = 0

    private val executor = Executors.newSingleThreadScheduledExecutor()
    private var scheduledFuture: ScheduledFuture<*>? = null

    // FPS 计数
    private var frameCount = 0
    private var lastFrameTimeNs = 0L
    private var jankCount = 0
    private var severeJankCount = 0
    private val JANK_THRESHOLD_NS = 16_000_000L      // 16ms
    private val SEVERE_JANK_THRESHOLD_NS = 700_000_000L // 700ms

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (lastFrameTimeNs != 0L) {
                val diff = frameTimeNanos - lastFrameTimeNs
                frameCount++
                if (diff > SEVERE_JANK_THRESHOLD_NS) severeJankCount++
                else if (diff > JANK_THRESHOLD_NS) jankCount++
            }
            lastFrameTimeNs = frameTimeNanos
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    fun start(context: Context, config: WFConfig) {
        // Choreographer 必须在主线程注册
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            Choreographer.getInstance().postFrameCallback(frameCallback)
        }

        // 定期快照 FPS/内存统计
        scheduledFuture = executor.scheduleWithFixedDelay({
            snapshot()
        }, config.samplingIntervalSec, config.samplingIntervalSec, TimeUnit.SECONDS)
    }

    fun stop() {
        scheduledFuture?.cancel(false)
    }

    private fun snapshot() {
        val fc = frameCount
        lastAvgFps = if (fc > 0) minOf(60, fc) else 60
        lastJankCount = jankCount
        lastSevereJankCount = severeJankCount
        // 重置
        frameCount = 0
        jankCount = 0
        severeJankCount = 0
    }

    fun currentMemoryMb(context: Context): Int = try {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        ((info.totalMem - info.availMem) / 1024 / 1024).toInt()
    } catch (e: Exception) { 0 }
}
