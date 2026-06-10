package com.webfunny.monitor.collector

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.webfunny.monitor.core.WFConfig
import com.webfunny.monitor.core.WFUploader
import com.webfunny.monitor.model.AnrLog

/**
 * ANR 检测（WatchDog 模式）
 *
 * 原理：
 *   1. 子线程每隔 [anrThresholdMs] 向主线程 Handler 发送一个 tick
 *   2. 若在阈值时间内主线程没有处理这个 tick，说明主线程被阻塞
 *   3. 此时采集主线程堆栈，上报 ANR 日志
 *
 * 风险：纯系统 API，无第三方依赖，Matrix/BlockCanary 均采用相同核心原理
 */
class AnrCollector(
    private val context: Context,
    private val config: WFConfig
) : Thread("wf-anr-watchdog") {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val mainThread = Looper.getMainLooper().thread

    @Volatile private var tickCounter = 0   // 看门狗线程递增
    @Volatile private var confirmedTick = 0 // 主线程确认值
    @Volatile private var reportedTick = -1 // 上次已上报的 tick，防止重复

    init {
        isDaemon = true
    }

    override fun run() {
        while (!isInterrupted) {
            tickCounter++
            val currentTick = tickCounter
            // 主线程收到 post 后更新 confirmedTick，表示 tick 被消费
            mainHandler.post { confirmedTick = currentTick }

            try {
                sleep(config.anrThresholdMs)
            } catch (e: InterruptedException) {
                return
            }

            // 主线程未消费 tick → 判定为 ANR
            if (confirmedTick != currentTick && reportedTick != currentTick) {
                reportedTick = currentTick
                try {
                    val stack = mainThread.stackTrace
                        .joinToString("\n") { "\tat ${it.className}.${it.methodName}(${it.fileName}:${it.lineNumber})" }
                    val log = AnrLog(config, context, config.anrThresholdMs, stack)
                    WFUploader.enqueue(log)
                    if (config.debugLog) Log.w("WFMonitor", "[ANR] 主线程阻塞 ${config.anrThresholdMs}ms，已上报")
                } catch (e: Exception) {
                    // 静默失败
                }
            }
        }
    }
}
