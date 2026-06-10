package com.webfunny.monitor.model

import com.webfunny.monitor.core.WFConfig
import com.webfunny.monitor.core.WFDeviceInfo
import com.webfunny.monitor.core.WFSession

/**
 * 所有日志类型的公共基础字段
 * 对应服务端 BaseInfoSchema + 移动端设备字段
 */
abstract class BaseLog(
    val uploadType: String,
    config: WFConfig,
    context: android.content.Context
) {
    val webMonitorId: String = config.webMonitorId
    val happenTime: String = System.currentTimeMillis().toString()
    val customerKey: String = WFSession.customerKey
    val sessionId: String = WFSession.sessionId
    val userId: String = WFSession.userId
    val simpleUrl: String = WFSession.currentPage
    val completeUrl: String = WFSession.currentPage
    val projectVersion: String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
    }.getOrDefault("")
    val firstUserParam: String = WFSession.firstUserParam
    val secondUserParam: String = WFSession.secondUserParam

    // 设备信息
    val platform: String = WFDeviceInfo.platform
    val os: String = WFDeviceInfo.os
    val osVersion: String = WFDeviceInfo.osVersion
    val deviceName: String = WFDeviceInfo.deviceName
    val deviceBrand: String = WFDeviceInfo.deviceBrand
    val deviceSize: String = WFDeviceInfo.deviceSize
    val appVersion: String = projectVersion
    val networkType: String = WFDeviceInfo.networkType(context)

    /** 子类调用此方法，并追加自己的字段 */
    open fun toMap(): Map<String, Any?> = mapOf(
        "uploadType"      to uploadType,
        "webMonitorId"    to webMonitorId,
        "happenTime"      to happenTime,
        "customerKey"     to customerKey,
        "sessionId"       to sessionId,
        "userId"          to userId,
        "simpleUrl"       to simpleUrl,
        "completeUrl"     to completeUrl,
        "projectVersion"  to projectVersion,
        "firstUserParam"  to firstUserParam,
        "secondUserParam" to secondUserParam,
        "platform"        to platform,
        "os"              to os,
        "osVersion"       to osVersion,
        "deviceName"      to deviceName,
        "deviceBrand"     to deviceBrand,
        "deviceSize"      to deviceSize,
        "appVersion"      to appVersion,
        "networkType"     to networkType
    )
}
