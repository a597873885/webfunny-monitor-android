package com.webfunny.monitor.model

import android.app.ActivityManager
import android.content.Context
import android.util.Base64
import com.webfunny.monitor.core.WFConfig

/** ANR → MobileErrorLog */
class AnrLog(
    config: WFConfig,
    context: Context,
    blockTimeMs: Long,
    mainThreadStack: String
) : BaseLog("ANR", config, context) {

    private val ctx = context.applicationContext

    val blockTime: Long = blockTimeMs
    val blockingMethod: String = mainThreadStack.lines().firstOrNull { it.isNotBlank() } ?: ""
    val cpuUsage: String = ""
    val mainThreadStackEncoded: String =
        Base64.encodeToString(mainThreadStack.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    val memoryUsed: Int = getMemoryUsedMb()
    val memoryAvailable: Int = getMemoryAvailableMb()

    override fun toMap(): Map<String, Any?> = super.toMap() + mapOf(
        "blockTime"       to blockTime,
        "blockingMethod"  to blockingMethod,
        "cpuUsage"        to cpuUsage,
        "mainThreadStack" to mainThreadStackEncoded,
        "memoryUsed"      to memoryUsed,
        "memoryAvailable" to memoryAvailable,
        "infoType"        to "on_error"
    )

    private fun getMemoryUsedMb(): Int = try {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        ((info.totalMem - info.availMem) / 1024 / 1024).toInt()
    } catch (e: Exception) { 0 }

    private fun getMemoryAvailableMb(): Int = try {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        (info.availMem / 1024 / 1024).toInt()
    } catch (e: Exception) { 0 }
}
