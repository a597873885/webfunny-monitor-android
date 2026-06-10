package com.webfunny.monitor.core

import android.content.Context
import android.content.SharedPreferences
import java.util.UUID

/**
 * 会话与设备指纹管理
 *
 * - customerKey：设备唯一指纹，首次生成后永久存储在 SharedPreferences
 * - sessionId：每次冷启动重新生成，会话维度的 ID
 * - userId / userParam：业务层设置，热更新无需重启
 */
object WFSession {

    private const val PREF_NAME = "wf_monitor_session"
    private const val KEY_CUSTOMER_KEY = "customer_key"
    private const val KEY_FIRST_LAUNCH = "first_launch_done"

    private lateinit var prefs: SharedPreferences

    /** 设备唯一指纹（持久化） */
    var customerKey: String = ""
        private set

    /** 会话 ID（每次冷启动重新生成） */
    val sessionId: String = UUID.randomUUID().toString()

    /** 业务用户 ID，由调用方设置 */
    var userId: String = ""

    /** 自定义参数 1 */
    var firstUserParam: String = ""

    /** 自定义参数 2 */
    var secondUserParam: String = ""

    /** 当前正在显示的页面名称 */
    var currentPage: String = ""

    /** 是否新用户（首次安装），只读 */
    var isNewUser: Boolean = false
        private set

    fun init(context: Context) {
        prefs = context.applicationContext
            .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        customerKey = prefs.getString(KEY_CUSTOMER_KEY, null)
            ?: UUID.randomUUID().toString().also { newKey ->
                prefs.edit().putString(KEY_CUSTOMER_KEY, newKey).apply()
            }
        // 首次启动标记：SP 中不存在该 key 视为新用户
        isNewUser = !prefs.contains(KEY_FIRST_LAUNCH)
        if (isNewUser) {
            prefs.edit().putBoolean(KEY_FIRST_LAUNCH, true).apply()
        }
    }
}
