package com.webfunny.monitor.model

import android.content.Context
import com.webfunny.monitor.core.WFConfig
import com.webfunny.monitor.core.WFSession
import java.util.UUID

/**
 * CUSTOMER_PV（页面 PV）→ MobilePerformanceLog
 *
 * 与 iOS WFPVLog / H5 SDK pageView.js 字段对齐：
 *   - pageKey   : 本次 PV 的唯一标识（UUID）
 *   - pagePath  : 当前页面名称（对标 simpleUrl/completeUrl）
 *   - loadType  : initial（首次）/ navigation（跳转）/ back（返回）
 *   - newStatus : 1=新用户（首次安装）/ 0=回访
 *   - referrer  : 上一个页面名称（base64），首页为空
 */
class PvLog(
    config: WFConfig,
    context: Context,
    val pagePath: String,
    val loadType: String,      // initial / navigation / back
    val referrer: String = ""  // previous page name
) : BaseLog("CUSTOMER_PV", config, context) {

    val pageKey: String = UUID.randomUUID().toString().replace("-", "")
    val newStatus: Int = if (WFSession.isNewUser) 1 else 0

    override fun toMap(): Map<String, Any?> = super.toMap() + mapOf(
        "pageKey"   to pageKey,
        "pagePath"  to pagePath,
        "loadType"  to loadType,
        "newStatus" to newStatus,
        "referrer"  to referrer,
        "monitorIp" to "",
        "country"   to "",
        "province"  to "",
        "city"      to ""
    )
}
