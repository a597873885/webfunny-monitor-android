package com.webfunny.monitor.collector

import android.content.Context
import android.util.Log
import com.webfunny.monitor.core.WFConfig
import com.webfunny.monitor.core.WFUploader
import com.webfunny.monitor.model.HttpLog
import okhttp3.Interceptor
import okhttp3.Response
import java.util.UUID

/**
 * OkHttp 请求拦截器
 *
 * 接入方式（调用方 OkHttpClient 初始化时加入）：
 *   OkHttpClient.Builder().addInterceptor(HttpInterceptor(context, config))
 *
 * 采集：method / url / 耗时 / 状态码 / 请求响应大小
 * 注入：请求头 wf-trace-id（用于全链路 TraceId 串联）
 */
class HttpInterceptor(
    private val context: Context,
    private val config: WFConfig
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val traceId = UUID.randomUUID().toString().replace("-", "")

        val newRequest = request.newBuilder()
            .header("wf-trace-id", traceId)
            .build()

        val startMs = System.currentTimeMillis()
        var response: Response? = null
        var isTimeout = 0

        try {
            response = chain.proceed(newRequest)
        } catch (e: Exception) {
            isTimeout = 1
            throw e
        } finally {
            val duration = (System.currentTimeMillis() - startMs).toInt()
            val url = request.url.toString()
            val status = response?.code ?: 0
            val reqSize = request.body?.contentLength() ?: 0L
            val respSize = response?.body?.contentLength() ?: 0L

            try {
                val log = HttpLog(
                    config         = config,
                    context        = context,
                    method         = request.method,
                    httpUrl        = url,
                    simpleHttpUrl  = stripQueryParams(url),
                    loadTime       = duration,
                    status         = status,
                    requestSize    = reqSize,
                    responseSize   = respSize,
                    isTimeout      = isTimeout,
                    statusText     = response?.message ?: "",
                    traceId        = traceId
                )
                WFUploader.enqueue(log)
                Log.d("WFMonitor", "[HTTP] ${request.method} ${stripQueryParams(url)} 耗时 ${duration}ms → 已入队")
            } catch (e: Exception) { /* 静默 */ }
        }

        return response!!
    }

    /** 去掉查询参数，保留域名+路径，用于按接口聚合 */
    private fun stripQueryParams(url: String): String =
        url.substringBefore("?")
}
