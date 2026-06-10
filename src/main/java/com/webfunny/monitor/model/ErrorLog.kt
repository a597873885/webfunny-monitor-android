package com.webfunny.monitor.model

import android.content.Context
import android.util.Base64
import com.webfunny.monitor.core.WFConfig
import com.webfunny.monitor.core.WFSession
import java.util.UUID

/**
 * JS_ERROR → MobileErrorLog（业务层 catch 到的异常）
 *
 * 数据结构对齐 H5 SDK jsError.js：
 *   - simpleErrorMessage: base64("ErrorType: 短消息(≤80字)")
 *   - errorMessage:       base64("ErrorType: 完整消息")
 *   - errorStack:         base64(堆栈)
 *   - pageKey:            UUID（对标 H5）
 *   - monitorIp/country/province/city: 服务端填充，上报空字符串
 */
class ErrorLog(
    config: WFConfig,
    context: Context,
    throwable: Throwable,
    errorScene: String = ""
) : BaseLog("JS_ERROR", config, context) {

    private val rawType: String = throwable.javaClass.simpleName
    private val rawMsg: String = throwable.message ?: rawType
    private val rawStack: String = throwable.stackTraceToString()

    // 对齐 H5：simpleErrorMessage = base64(errorType + ': ' + 消息前80字)
    val simpleErrorMessage: String = base64("$rawType: ${rawMsg.take(80)}")

    // 对齐 H5：errorMessage = base64(errorType + ': ' + 完整消息，≤1000字)
    val errorMessage: String = base64("$rawType: ${rawMsg.take(999)}")

    // 对齐 H5：errorStack = base64(堆栈，≤3000字)
    val errorStack: String = base64(rawStack.take(2999))

    val pageKey: String = UUID.randomUUID().toString().replace("-", "")
    val errorPage: String = WFSession.currentPage.ifEmpty { "unknown" }
    val errorScene: String = errorScene

    // 对齐 H5：monitorIp/country/province/city 服务端填充
    val monitorIp: String = ""
    val country: String = ""
    val province: String = ""
    val city: String = ""

    override fun toMap(): Map<String, Any?> = super.toMap() + mapOf(
        "pageKey"            to pageKey,
        "simpleErrorMessage" to simpleErrorMessage,
        "errorMessage"       to errorMessage,
        "errorStack"         to errorStack,
        "errorPage"          to errorPage,
        "errorScene"         to errorScene,
        "monitorIp"          to monitorIp,
        "country"            to country,
        "province"           to province,
        "city"               to city,
        "infoType"           to "on_error"
    )

    private fun base64(str: String): String =
        Base64.encodeToString(str.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
}
