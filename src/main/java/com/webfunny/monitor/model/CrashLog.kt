package com.webfunny.monitor.model

import android.app.ActivityManager
import android.content.Context
import android.util.Base64
import com.webfunny.monitor.core.WFConfig

/** APP_CRASH → MobileErrorLog */
class CrashLog(
    config: WFConfig,
    context: Context,
    throwable: Throwable,
    isForeground: Boolean
) : BaseLog("APP_CRASH", config, context) {

    private val ctx = context.applicationContext

    val errorType: String = throwable.javaClass.simpleName
    val simpleErrorMessage: String = throwable.message?.take(200) ?: errorType
    val errorMessage: String = base64(throwable.message ?: errorType)
    val errorStack: String = base64(throwable.stackTraceToString())
    val crashReason: String = throwable.message ?: ""
    val crashThread: String = Thread.currentThread().name
    val isForeground: Int = if (isForeground) 1 else 0
    val crashMemory: Int = getMemoryMb()
    val crashCpuUsage: String = ""

    override fun toMap(): Map<String, Any?> = super.toMap() + mapOf(
        "errorType"          to errorType,
        "simpleErrorMessage" to simpleErrorMessage,
        "errorMessage"       to errorMessage,
        "errorStack"         to errorStack,
        "crashReason"        to crashReason,
        "crashThread"        to crashThread,
        "isForeground"       to isForeground,
        "crashMemory"        to crashMemory,
        "crashCpuUsage"      to crashCpuUsage,
        "infoType"           to "on_error"
    )

    private fun base64(str: String): String =
        Base64.encodeToString(str.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

    private fun getMemoryMb(): Int = try {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        ((info.totalMem - info.availMem) / 1024 / 1024).toInt()
    } catch (e: Exception) { 0 }
}
