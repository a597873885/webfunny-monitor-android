package com.webfunny.monitor.core

import android.content.Context
import android.util.Log
import com.webfunny.monitor.model.BaseLog
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.net.HttpURLConnection
import java.net.URL

/**
 * 批量队列上报核心
 *
 * 策略：
 *   1. 日志先入内存队列
 *   2. 满 maxQueueSize 条，或定时器到期（flushIntervalMs），触发批量上报
 *   3. 上报失败写入本地文件，下次启动重试一次
 *   4. 所有操作在独立线程池执行，不阻塞主线程
 */
object WFUploader {

    private const val TAG = "WFUploader"
    private const val RETRY_FILE = "wf_retry_queue.json"
    private const val CRASH_FILE = "wf_crash_log.json"

    private lateinit var config: WFConfig
    private lateinit var appContext: Context

    private val queue = CopyOnWriteArrayList<Map<String, Any?>>()
    private val executor = Executors.newSingleThreadScheduledExecutor()

    fun init(context: Context, cfg: WFConfig) {
        appContext = context.applicationContext
        config = cfg

        // 定时刷新
        executor.scheduleWithFixedDelay(
            { flush() },
            cfg.flushIntervalMs,
            cfg.flushIntervalMs,
            TimeUnit.MILLISECONDS
        )

        // 启动时重试上次失败的数据
        executor.execute { retryPendingLogs() }
    }

    /** 加入队列，超出阈值立即触发上报 */
    fun enqueue(log: Map<String, Any?>) {
        queue.add(log)
        if (config.debugLog) {
            val type = log["uploadType"] ?: "?"
            Log.d(TAG, "[入队] $type → 队列 ${queue.size}/${config.maxQueueSize}")
        }
        if (queue.size >= config.maxQueueSize) {
            executor.execute { flush() }
        }
    }

    /** 将 BaseLog 对象转 Map 后入队 */
    fun enqueue(log: BaseLog) {
        enqueue(log.toMap())
    }

    /** 立即刷新队列（App 退到后台时调用） */
    fun flushNow() {
        executor.execute { flush() }
    }

    // ─── 内部实现 ────────────────────────────────────────────────

    @Synchronized
    private fun flush() {
        if (queue.isEmpty()) return
        val batch = queue.toList()
        queue.clear()
        if (config.debugLog) Log.d(TAG, "[刷新] 开始上报 ${batch.size} 条 → ${config.uploadUrl}")
        upload(batch)
    }

    private fun upload(logs: List<Map<String, Any?>>): Boolean {
        try {
            val jsonArray = JSONArray()
            logs.forEach { jsonArray.put(JSONObject(it)) }
            val body = jsonArray.toString()

            if (config.debugLog) {
                val types = logs.map { it["uploadType"] ?: "?" }.distinct()
                Log.d(TAG, "[上报] ${logs.size} 条, 类型=${types}, URL=${config.uploadUrl}")
                // 打印完整 JSON（太长则截断）
                val preview = if (body.length > 2000) body.take(2000) + "..." else body
                Log.d(TAG, "[上报] Body:\n$preview")
            }

            val conn = (URL(config.uploadUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
                connectTimeout = 10_000
                readTimeout = 10_000
            }
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val respBody = try {
                conn.inputStream.bufferedReader().readText()
            } catch (_: Exception) {
                try { conn.errorStream.bufferedReader().readText() } catch (_: Exception) { "" }
            }
            conn.disconnect()

            if (config.debugLog) {
                Log.d(TAG, "[上报] 状态码=$code 条数=${logs.size}")
                if (respBody.isNotEmpty()) Log.d(TAG, "[上报] 响应: $respBody")
            }

            if (code !in 200..299) {
                if (config.debugLog) Log.w(TAG, "[上报] 服务器返回非 2xx，持久化到磁盘")
                saveToDisk(logs)
                return false
            }
            return true
        } catch (e: Exception) {
            if (config.debugLog) Log.e(TAG, "上报失败：${e.message}")
            saveToDisk(logs)
            return false
        }
    }

    /** 失败时持久化到本地文件，合并已有记录（不覆盖） */
    private fun saveToDisk(logs: List<Map<String, Any?>>) {
        try {
            val file = File(appContext.cacheDir, RETRY_FILE)
            val arr = if (file.exists()) {
                try { JSONArray(file.readText()) } catch (_: Exception) { JSONArray() }
            } else {
                JSONArray()
            }
            logs.forEach { arr.put(JSONObject(it)) }
            // 最多保留 50 条，防止文件膨胀
            while (arr.length() > 50) arr.remove(0)
            file.writeText(arr.toString())
        } catch (e: Exception) {
            if (config.debugLog) Log.e(TAG, "持久化失败：${e.message}")
        }
    }

    /** App 启动时重试上次失败的数据（仅上传成功后删除） */
    private fun retryPendingLogs() {
        // 重试崩溃日志（独立文件，CrashCollector 写入）
        retryFile(CRASH_FILE)
        // 重试上传队列残留
        retryFile(RETRY_FILE)
    }

    private fun retryFile(fileName: String) {
        try {
            val file = File(appContext.cacheDir, fileName)
            if (!file.exists()) return
            val content = file.readText()
            val arr = JSONArray(content)
            val logs = (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                obj.keys().asSequence().associateWith { key -> obj.get(key) }
            }
            if (logs.isEmpty()) {
                file.delete()
                return
            }
            // 上传成功后才删除文件
            if (upload(logs)) {
                file.delete()
                if (config.debugLog) Log.d(TAG, "[重试] $fileName 上传成功，已删除")
            } else {
                if (config.debugLog) Log.w(TAG, "[重试] $fileName 上传失败，保留磁盘备份")
            }
        } catch (e: Exception) {
            if (config.debugLog) Log.e(TAG, "[重试] $fileName 解析失败：${e.message}")
        }
    }
}
