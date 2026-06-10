package com.webfunny.monitor.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.DisplayMetrics

/**
 * 设备信息采集（系统 API，零风险）
 *
 * 对应 ClickHouse 字段：platform / os / osVersion / deviceName /
 *                        deviceBrand / deviceSize / networkType
 */
object WFDeviceInfo {

    val platform: String = "android"

    val os: String = "Android ${Build.VERSION.RELEASE}"

    val osVersion: String = Build.VERSION.RELEASE

    /** 设备型号，如 Pixel 8 Pro */
    val deviceName: String = Build.MODEL

    /** 设备品牌，如 Google / HUAWEI */
    val deviceBrand: String = Build.BRAND

    private var _deviceSize: String = ""
    val deviceSize: String get() = _deviceSize

    fun init(context: Context) {
        val dm: DisplayMetrics = context.resources.displayMetrics
        _deviceSize = "${dm.widthPixels}x${dm.heightPixels}"
    }

    /**
     * 获取当前网络类型
     * 返回值：wifi / 5g / 4g / 3g / unknown
     */
    fun networkType(context: Context): String {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE)
            as? ConnectivityManager ?: return "unknown"
        val network = cm.activeNetwork ?: return "unknown"
        val caps = cm.getNetworkCapabilities(network) ?: return "unknown"
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
                ) "5g" else "4g"
            }
            else -> "unknown"
        }
    }
}
