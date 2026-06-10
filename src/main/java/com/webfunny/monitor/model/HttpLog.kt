package com.webfunny.monitor.model

import android.content.Context
import android.util.Base64
import com.webfunny.monitor.core.WFConfig

/** HTTP_LOG → 与 H5 SDK / iOS 字段对齐 */
class HttpLog(
    config: WFConfig,
    context: Context,
    val method: String,
    val httpUrl: String,
    val simpleHttpUrl: String,
    val loadTime: Int,
    val dnsTime: Int = 0,
    val tcpTime: Int = 0,
    val sslTime: Int = 0,
    val ttfbTime: Int = 0,
    val downloadTime: Int = 0,
    val status: Int,
    val requestSize: Long = 0L,
    val responseSize: Long = 0L,
    val isTimeout: Int = 0,
    val statusText: String = "",
    val traceId: String = "",
    val headerText: String = "",
    val requestText: String = "",
    val responseText: String = ""
) : BaseLog("HTTP_LOG", config, context) {

    val totalDuration: Int = loadTime
    val statusResult: String = if (status in 200..299) "response" else "request"
    private val segment: String = ""

    override fun toMap(): Map<String, Any?> = super.toMap() + mapOf<String, Any?>(
        "method"        to method,
        "httpUrl"       to b64(httpUrl),
        "simpleHttpUrl" to b64(simpleHttpUrl),
        "loadTime"      to loadTime,
        "dnsTime"       to dnsTime,
        "tcpTime"       to tcpTime,
        "sslTime"       to sslTime,
        "ttfbTime"      to ttfbTime,
        "downloadTime"  to downloadTime,
        "status"        to status,
        "requestSize"   to requestSize,
        "responseSize"  to responseSize,
        "isTimeout"     to isTimeout,
        "statusText"    to statusText,
        "statusResult"  to statusResult,
        "traceId"       to traceId,
        "totalDuration" to totalDuration,
        "headerText"    to b64(headerText),
        "requestText"   to b64(requestText),
        "responseText"  to b64(responseText),
        "segment"       to segment
    )

    private fun b64(str: String): String =
        Base64.encodeToString(str.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
}
