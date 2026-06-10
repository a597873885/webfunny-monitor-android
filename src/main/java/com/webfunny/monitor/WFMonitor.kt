package com.webfunny.monitor

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.util.Log
import com.webfunny.monitor.collector.*
import com.webfunny.monitor.core.WFConfig
import com.webfunny.monitor.core.WFDeviceInfo
import com.webfunny.monitor.core.WFSession
import com.webfunny.monitor.core.WFUploader
import com.webfunny.monitor.model.ErrorLog
import okhttp3.Interceptor

/**
 * Webfunny Android 监控 SDK 唯一对外入口
 *
 * ── 接入步骤 ──────────────────────────────────────────────────────
 *
 * 1. Application.onCreate() 最开始调用 markAppInit()，结束时调用 init()
 *
 *    class MyApp : Application() {
 *        override fun onCreate() {
 *            WFMonitor.markAppInit()         // ← 放最前面
 *            super.onCreate()
 *            WFMonitor.init(this, WFConfig(
 *                serverUrl    = "https://monitor.example.com",
 *                webMonitorId = "your_id"
 *            ))
 *        }
 *    }
 *
 * 2. OkHttpClient 添加拦截器
 *    val client = OkHttpClient.Builder()
 *        .addInterceptor(WFMonitor.httpInterceptor())
 *        .build()
 *
 * 3. 用户登录后设置 userId
 *    WFMonitor.setUserId("user_123")
 */
object WFMonitor {

    private lateinit var config: WFConfig
    private lateinit var appContext: Context
    private var anrCollector: AnrCollector? = null

    /** 在 Application.onCreate() 最开始调用，记录进程启动基准时间 */
    fun markAppInit() {
        StartupCollector.markAppInit()
    }

    /**
     * 初始化 SDK，在 Application.onCreate() 中调用
     * 调用后 markAppInit() 的时间点已记录，无需重复调用
     */
    fun init(application: Application, config: WFConfig) {
        this.config = config
        this.appContext = application.applicationContext

        StartupCollector.markAppInitEnd()

        WFDeviceInfo.init(appContext)
        WFSession.init(appContext)
        WFUploader.init(appContext, config)

        if (config.enableCrash) {
            CrashCollector.install(appContext, config)
        }
        if (config.enableAnr) {
            anrCollector = AnrCollector(appContext, config).also { it.start() }
        }

        StartupCollector.install(application, config)
        PageCollector.install(application, config)

        if (config.enableFps || config.enableMemory) {
            MemoryFpsCollector.start(appContext, config)
        }

        // App 退后台自动 flush，避免队列积压数据丢失
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            private var startedCount = 0
            override fun onActivityStarted(a: Activity) { startedCount++ }
            override fun onActivityStopped(a: Activity) {
                if (--startedCount == 0) WFUploader.flushNow()
            }
            override fun onActivityCreated(a: Activity, b: Bundle?) {}
            override fun onActivityResumed(a: Activity) {}
            override fun onActivityPaused(a: Activity) {}
            override fun onActivitySaveInstanceState(a: Activity, b: Bundle) {}
            override fun onActivityDestroyed(a: Activity) {}
        })

        if (config.debugLog) {
            Log.d("WFMonitor", "══════════════════════════════════")
            Log.d("WFMonitor", "SDK 初始化完成")
            Log.d("WFMonitor", "上报地址: ${config.uploadUrl}")
            Log.d("WFMonitor", "项目 ID:  ${config.webMonitorId}")
            Log.d("WFMonitor", "崩溃: ${if (config.enableCrash) "✓" else "✗"}  ANR: ${if (config.enableAnr) "✓" else "✗"}  HTTP: ${if (config.enableHttp) "✓" else "✗"}  FPS: ${if (config.enableFps) "✓" else "✗"}  内存: ${if (config.enableMemory) "✓" else "✗"}")
            Log.d("WFMonitor", "队列: ${config.maxQueueSize}条/批  刷新间隔: ${config.flushIntervalMs}ms")
            Log.d("WFMonitor", "══════════════════════════════════")
        }
    }

    /**
     * 获取 OkHttp 拦截器，加入到 OkHttpClient.Builder
     */
    fun httpInterceptor(): Interceptor {
        check(::config.isInitialized) { "WFMonitor.init() 尚未调用" }
        return HttpInterceptor(appContext, config)
    }

    /** 设置业务用户 ID（登录后调用，退出后传空字符串） */
    fun setUserId(userId: String) {
        WFSession.userId = userId
    }

    /** 设置自定义参数 */
    fun setUserParam(first: String = "", second: String = "") {
        WFSession.firstUserParam = first
        WFSession.secondUserParam = second
    }

    /**
     * 手动开始一个页面耗时追踪（不使用自动 Activity 采集时）
     *
     * val tracker = WFMonitor.startPageTrace("OrderDetailPage")
     * // 数据加载完成后
     * tracker.finish()
     */
    fun startPageTrace(pageName: String): PageTracker {
        check(::config.isInitialized) { "WFMonitor.init() 尚未调用" }
        return PageTracker(pageName, appContext, config)
    }

    /**
     * 上报业务层 catch 到的异常（不会导致崩溃的错误）
     *
     * try { ... } catch (e: Exception) {
     *     WFMonitor.reportError(e, "加载用户列表")
     * }
     *
     * @param throwable 被捕获的异常
     * @param errorScene 错误场景描述（可选），如 "支付接口调用"
     */
    fun reportError(throwable: Throwable, errorScene: String = "") {
        check(::config.isInitialized) { "WFMonitor.init() 尚未调用" }
        try {
            val log = ErrorLog(config, appContext, throwable, errorScene)
            WFUploader.enqueue(log)
            if (config.debugLog) Log.d("WFMonitor", "[错误] ${throwable.javaClass.simpleName}: ${throwable.message} → 已入队")
        } catch (e: Exception) { /* 静默 */ }
    }

    /** App 进入后台时主动刷新队列（在 onStop 或 onTrimMemory 中调用） */
    fun flush() {
        WFUploader.flushNow()
    }
}
