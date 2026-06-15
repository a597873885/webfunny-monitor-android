package com.webfunny.monitor.collector

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.SystemClock
import com.webfunny.monitor.core.WFConfig
import com.webfunny.monitor.core.WFSession
import com.webfunny.monitor.core.WFUploader
import com.webfunny.monitor.model.PvLog
import com.webfunny.monitor.model.StartupLog

/**
 * 应用启动耗时采集，支持冷/温/热三种启动类型
 *
 * 检测原理（通过 ActivityLifecycleCallbacks + 前台 Activity 计数）：
 *   冷启动：进程首次启动 → Application.onCreate → Activity.onCreate → onResume
 *   温启动：进程存活，Activity.onCreate 被再次调用（如 Back 退出后重新进入）
 *   热启动：进程存活，Activity 直接 onResume（如 Home 键切回 / 多任务切回）
 *
 * 时间测量：
 *   冷启动：processStartMs → appInitEndMs → firstActivityResumeMs
 *   温启动：onActivityCreated → onActivityResumed
 *   热启动：app 回到前台时刻 → onActivityResumed
 */
object StartupCollector {

    // ── 冷启动时间点 ───────────────────────────────────────────
    private var processStartMs: Long = SystemClock.elapsedRealtime()
    private var appInitStartMs: Long = 0L
    private var appInitEndMs: Long = 0L

    // ── 前/后台状态 ────────────────────────────────────────────
    private var startedActivityCount = 0
    private var pendingHotStart = false

    // ── 温/热启动追踪 ──────────────────────────────────────────
    private var firstLaunchDone = false
    private var warmActivityName: String? = null
    private var warmActivityCreateMs: Long = 0L

    /** 在 Application.onCreate() 最开始调用 */
    fun markAppInit() {
        appInitStartMs = SystemClock.elapsedRealtime()
    }

    /** 在 Application.onCreate() 结束时调用 */
    fun markAppInitEnd() {
        appInitEndMs = SystemClock.elapsedRealtime()
    }

    fun install(app: Application, config: WFConfig) {
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                // 记录所有 Activity 创建（首启除外），是否为温启动由 onResume 中的 pendingHotStart 决定
                if (firstLaunchDone) {
                    warmActivityName = activity.javaClass.simpleName
                    warmActivityCreateMs = SystemClock.elapsedRealtime()
                }
            }

            override fun onActivityStarted(activity: Activity) {
                if (startedActivityCount == 0) {
                    pendingHotStart = true  // app 从后台回到前台，允许一次热/温启动检测
                }
                startedActivityCount++
            }

            override fun onActivityResumed(activity: Activity) {
                if (!firstLaunchDone) {
                    // ── 冷启动 ──────────────────────────────
                    firstLaunchDone = true
                    pendingHotStart = false
                    val firstActivityResumeMs = SystemClock.elapsedRealtime()
                    try {
                        val log = StartupLog(
                            config               = config,
                            context              = activity,
                            startType            = "cold",
                            processStartTime     = (appInitStartMs - processStartMs).toInt().coerceAtLeast(0),
                            applicationInitTime  = (appInitEndMs - appInitStartMs).toInt().coerceAtLeast(0),
                            splashScreenTime     = 0,
                            mainPageCreateTime   = 0,
                            firstFrameRenderTime = (firstActivityResumeMs - appInitEndMs).toInt().coerceAtLeast(0)
                        )
                        WFUploader.enqueue(log)

                        // 冷启动也产生一条 CUSTOMER_PV，pagePath 用 app_start 标识
                        // ★ 必须先更新 currentPage，因为 PvLog 构造时会将其写入 simpleUrl
                        WFSession.currentPage = "app_start"
                        val pvLog = PvLog(
                            config   = config,
                            context  = activity,
                            pagePath = "app_start",
                            loadType = "initial",
                            referrer = ""
                        )
                        WFUploader.enqueue(pvLog)
                        if (config.debugLog) android.util.Log.d("WFMonitor", "[PV] app_start loadType=initial → 已入队")
                    } catch (e: Exception) { /* 静默 */ }

                } else if (pendingHotStart && warmActivityName == activity.javaClass.simpleName) {
                    // ── 温启动：Activity 被销毁后重新创建 ────
                    pendingHotStart = false
                    warmActivityName = null
                    val duration = (SystemClock.elapsedRealtime() - warmActivityCreateMs).toInt().coerceAtLeast(0)
                    try {
                        val log = StartupLog(
                            config               = config,
                            context              = activity,
                            startType            = "warm",
                            processStartTime     = 0,
                            applicationInitTime  = 0,
                            splashScreenTime     = 0,
                            mainPageCreateTime   = duration,
                            firstFrameRenderTime = duration
                        )
                        WFUploader.enqueue(log)

                        // 温启动也产生一条 CUSTOMER_PV
                        WFSession.currentPage = activity.javaClass.simpleName
                        val pvLog = PvLog(
                            config   = config,
                            context  = activity,
                            pagePath = activity.javaClass.simpleName,
                            loadType = "navigation",
                            referrer = ""
                        )
                        WFUploader.enqueue(pvLog)
                    } catch (e: Exception) { /* 静默 */ }

                } else if (pendingHotStart) {
                    // ── 热启动：app 从后台切回，Activity 未重建 ──
                    pendingHotStart = false
                    try {
                        val log = StartupLog(
                            config               = config,
                            context              = activity,
                            startType            = "hot",
                            processStartTime     = 0,
                            applicationInitTime  = 0,
                            splashScreenTime     = 0,
                            mainPageCreateTime   = 0,
                            firstFrameRenderTime = 0
                        )
                        WFUploader.enqueue(log)

                        // 热启动也产生一条 CUSTOMER_PV
                        WFSession.currentPage = activity.javaClass.simpleName
                        val pvLog = PvLog(
                            config   = config,
                            context  = activity,
                            pagePath = activity.javaClass.simpleName,
                            loadType = "navigation",
                            referrer = ""
                        )
                        WFUploader.enqueue(pvLog)
                    } catch (e: Exception) { /* 静默 */ }
                }
            }

            override fun onActivityStopped(activity: Activity) {
                startedActivityCount--
                // 所有 Activity stopped → app 进入后台，不重置 pendingHotStart
                // （由下一次 onActivityStarted 的 0→1 转换重新设置）
            }

            override fun onActivityPaused(a: Activity) {}
            override fun onActivityDestroyed(a: Activity) {}
            override fun onActivitySaveInstanceState(a: Activity, b: Bundle) {}
        })
    }
}
