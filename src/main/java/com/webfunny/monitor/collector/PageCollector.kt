package com.webfunny.monitor.collector

import android.app.Activity
import android.app.Application
import android.app.Application.ActivityLifecycleCallbacks
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import com.webfunny.monitor.core.WFConfig
import com.webfunny.monitor.core.WFSession
import com.webfunny.monitor.core.WFUploader
import com.webfunny.monitor.model.PvLog

/**
 * 页面 PV 自动采集（CUSTOMER_PV）
 *
 * 双层追踪：
 *   - Activity 级别：ActivityLifecycleCallbacks（所有 App 通用）
 *   - Fragment 级别：FragmentLifecycleCallbacks（Fragment / Navigation 组件场景，对标 iOS UIViewController swizzling）
 *
 * loadType 判定（对标 iOS）：
 *   - 首次上报 → "initial"
 *   - 后续新创建页面（isNewLoad） → "navigation"
 *   - 后退返回已有页面（!isNewLoad） → "back"
 *
 * 如需页面加载性能数据（MOBILE_PAGE_LOAD），请使用 WFMonitor.startPageTrace() 手动打点
 */
object PageCollector {

    private data class PageRecord(
        val pageName: String,
        val isNewLoad: Boolean
    )

    private val activityStack = mutableMapOf<String, PageRecord>()
    private val fragmentStack = mutableMapOf<String, PageRecord>()
    private val registeredActivities = mutableSetOf<String>()

    /** 不应作为页面上报的系统 / 框架 Fragment */
    private val excludedFragmentNames = setOf(
        "NavHostFragment",
        "DialogFragment"
    )

    /** 不应作为页面上报的系统 Activity */
    private val excludedActivityNames = setOf<String>()

    private var config: WFConfig? = null
    private var firstPVReported = false

    fun install(app: Application, cfg: WFConfig) {
        config = cfg
        app.registerActivityLifecycleCallbacks(createActivityCallbacks())
    }

    // ── Activity 级别 ──────────────────────────────────────────

    private fun createActivityCallbacks() = object : ActivityLifecycleCallbacks {

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
            // Activity PV 始终记录，但上报时若已注册 Fragment 追踪则跳过
            recordPage(activityStack, activity.javaClass.simpleName, true)
            registerFragmentTrackingSafe(activity)
        }

        override fun onActivityResumed(activity: Activity) {
            // 若 Activity 已注册 Fragment 追踪（即内部页面由 Fragment 代表），
            // 则只上报 Fragment 级 PV，跳过 Activity 级 PV 避免重复
            if (registeredActivities.contains(activity.javaClass.simpleName)) return
            reportPage(activityStack, activity.javaClass.simpleName, activity)
        }

        override fun onActivityDestroyed(activity: Activity) {
            activityStack.remove(activity.javaClass.simpleName)
            registeredActivities.remove(activity.javaClass.simpleName)
        }

        override fun onActivityStarted(a: Activity) {}
        override fun onActivityPaused(a: Activity) {}
        override fun onActivityStopped(a: Activity) {}
        override fun onActivitySaveInstanceState(a: Activity, b: Bundle) {}
    }

    // ── Fragment 级别 ──────────────────────────────────────────

    private fun registerFragmentTrackingSafe(activity: Activity) {
        try {
            if (activity is FragmentActivity) {
                registerFragmentTracking(activity)
            }
        } catch (_: Exception) {}
        catch (_: Throwable) {}
    }

    private fun registerFragmentTracking(activity: FragmentActivity) {
        val key = activity.javaClass.simpleName
        if (!registeredActivities.add(key)) return

        try {
            activity.supportFragmentManager.registerFragmentLifecycleCallbacks(
                object : FragmentManager.FragmentLifecycleCallbacks() {

                    override fun onFragmentViewCreated(
                        fm: FragmentManager, f: Fragment, v: View,
                        savedInstanceState: Bundle?
                    ) {
                        val name = f.javaClass.simpleName
                        if (name in excludedFragmentNames) return
                        recordPage(fragmentStack, name, true)
                    }

                    override fun onFragmentResumed(fm: FragmentManager, f: Fragment) {
                        val name = f.javaClass.simpleName
                        if (name in excludedFragmentNames) return
                        reportPage(fragmentStack, name, f.requireContext())
                    }

                    override fun onFragmentDestroyed(fm: FragmentManager, f: Fragment) {
                        val name = f.javaClass.simpleName
                        fragmentStack.remove(name)
                    }
                },
                true
            )
        } catch (e: Exception) {
            if (config?.debugLog == true) Log.w("WFMonitor", "[PV] Fragment 追踪注册失败: ${e.message}")
        }
    }

    // ── 通用逻辑 ───────────────────────────────────────────────

    private fun recordPage(stack: MutableMap<String, PageRecord>, name: String, isNewLoad: Boolean) {
        stack[name] = PageRecord(pageName = name, isNewLoad = isNewLoad)
    }

    private fun reportPage(
        stack: MutableMap<String, PageRecord>,
        name: String,
        context: android.content.Context
    ) {
        val record = stack[name] ?: return

        // 确定 loadType
        val loadType: String
        if (record.isNewLoad) {
            loadType = if (!firstPVReported) {
                firstPVReported = true
                "initial"
            } else {
                "navigation"
            }
        } else {
            loadType = "back"
        }

        // referrer = 跳转前的页面
        val referrer = WFSession.currentPage
        WFSession.currentPage = name

        // 重置 isNewLoad，下次回到此页面时判定为 back
        stack[name] = record.copy(isNewLoad = false)

        reportPageView(name, loadType, referrer, context)
    }

    private fun reportPageView(
        pagePath: String,
        loadType: String,
        referrer: String,
        context: android.content.Context
    ) {
        val cfg = config ?: return
        try {
            val log = PvLog(
                config   = cfg,
                context  = context,
                pagePath = pagePath,
                loadType = loadType,
                referrer = referrer
            )
            WFUploader.enqueue(log)
            if (cfg.debugLog) {
                val refStr = if (referrer.isEmpty()) "无" else referrer
                Log.d("WFMonitor", "[PV] $pagePath loadType=$loadType referrer=$refStr → 已入队")
            }
        } catch (e: Exception) { /* 静默 */ }
    }
}
