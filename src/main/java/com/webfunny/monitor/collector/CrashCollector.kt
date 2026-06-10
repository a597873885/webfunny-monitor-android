package com.webfunny.monitor.collector

import android.app.ActivityManager
import android.content.Context
import android.util.Log
import com.webfunny.monitor.core.WFConfig
import com.webfunny.monitor.core.WFUploader
import com.webfunny.monitor.model.CrashLog
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Java/Kotlin 层崩溃捕获
 *
 * 原理：Thread.setDefaultUncaughtExceptionHandler
 *
 * 崩溃时进程即将终止，策略为「先落盘、后尝试上传」：
 *   1. 立即将 CrashLog 序列化写入磁盘（可靠，不受进程终止影响）
 *   2. 同时入队触发一次异步上传（尽力而为，可能来不及完成）
 *   3. 下次 App 启动时 WFUploader 自动重试磁盘残留数据
 */
object CrashCollector {

    private const val CRASH_FILE = "wf_crash_log.json"

    fun install(context: Context, config: WFConfig) {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val isFg = isAppForeground(context)
                val log = CrashLog(config, context, throwable, isFg)

                Log.w("WFMonitor", "[崩溃] 捕获到 ${throwable.javaClass.simpleName}: ${throwable.message}")

                // ① 第一步：立即落盘（可靠，进程被杀也不丢）
                saveCrashToDisk(context, log)

                // ② 第二步：尝试实时上传（失败也不影响，磁盘已有备份）
                WFUploader.enqueue(log)
                WFUploader.flushNow()
                Thread.sleep(300)
            } catch (e: Exception) {
                // 捕获失败静默处理，绝不让监控代码影响原有崩溃流程
            } finally {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    /** 直接将 CrashLog 写入磁盘，不依赖异步队列 */
    private fun saveCrashToDisk(context: Context, log: CrashLog) {
        try {
            val file = File(context.cacheDir, CRASH_FILE)
            // 合并已有崩溃记录，最多保留 5 条
            val existing = if (file.exists()) {
                try { JSONArray(file.readText()) } catch (_: Exception) { JSONArray() }
            } else {
                JSONArray()
            }
            existing.put(JSONObject(log.toMap()))
            // 只保留最近 5 条，防止文件膨胀
            while (existing.length() > 5) existing.remove(0)
            file.writeText(existing.toString())
            if (true) Log.d("WFMonitor", "[崩溃] 已落盘 → ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e("WFMonitor", "[崩溃] 落盘失败: ${e.message}")
        }
    }

    private fun isAppForeground(context: Context): Boolean = try {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        am.runningAppProcesses?.any {
            it.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND &&
                it.processName == context.packageName
        } ?: false
    } catch (e: Exception) { true }
}
