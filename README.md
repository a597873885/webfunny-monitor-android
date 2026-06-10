# Webfunny Monitor Android SDK

Android 全链路监控 SDK，对接 [Webfunny 监控平台](https://www.webfunny.com)，自动采集崩溃、ANR、HTTP 请求、页面 PV、性能数据。

## 快速开始

### 1. 添加依赖

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        maven { url = uri("https://jitpack.io") }
    }
}

// app/build.gradle.kts
dependencies {
    implementation("com.github.a597873885:webfunny-monitor-android:1.0.0")
}
```

### 2. Application 初始化

```kotlin
import com.webfunny.monitor.WFMonitor
import com.webfunny.monitor.core.WFConfig

class MyApp : Application() {

    override fun onCreate() {
        // ⚠️ markAppInit() 必须在 super.onCreate() 之前调用
        WFMonitor.markAppInit()
        super.onCreate()

        WFMonitor.init(this, WFConfig(
            serverUrl    = "https://your-server.com",   // 监控服务器地址（不含路径）
            webMonitorId = "your_monitor_id",           // Webfunny 控制台获取
            debugLog     = BuildConfig.DEBUG            // Debug 包打印日志，Release 关闭
        ))

        // 用户登录后设置（可选，便于排查具体用户的问题）
        WFMonitor.setUserId("user_123")
    }
}
```

### 3. OkHttp 拦截器（可选）

```kotlin
val client = OkHttpClient.Builder()
    .addInterceptor(WFMonitor.httpInterceptor())
    .build()
```

## WFConfig 参数说明

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `serverUrl` | String | **必填** | 监控服务器地址，如 `https://monitor.example.com` |
| `webMonitorId` | String | **必填** | 项目监控 ID，Webfunny 控制台创建项目后获取 |
| `enableCrash` | Boolean | `true` | 是否采集 Java 层崩溃 |
| `enableAnr` | Boolean | `true` | 是否检测 ANR（主线程卡死 > 5s） |
| `enableHttp` | Boolean | `true` | 是否拦截 OkHttp 请求 |
| `enableFps` | Boolean | `true` | 是否采样 FPS |
| `enableMemory` | Boolean | `true` | 是否采样内存 |
| `samplingIntervalSec` | Long | `2` | FPS / 内存采样间隔（秒） |
| `maxQueueSize` | Int | `20` | 队列最大条数，超出后强制上报 |
| `flushIntervalMs` | Long | `10000` | 队列定时刷新间隔（毫秒） |
| `anrThresholdMs` | Long | `5000` | ANR 检测阈值（毫秒） |
| `debugLog` | Boolean | `false` | 是否打印 SDK 调试日志 |
| `httpBodyMaxBytes` | Int | `8192` | HTTP 请求/响应体截取上限（字节），默认 8KB |

## API 参考

### WFMonitor.markAppInit()

记录进程启动基准时间，**必须在 `super.onCreate()` 之前调用**。

### WFMonitor.init(application, config)

初始化 SDK，在 `Application.onCreate()` 中调用。

### WFMonitor.httpInterceptor()

返回 OkHttp `Interceptor`，加入 `OkHttpClient.Builder` 即可自动拦截上报所有 HTTP 请求。

### WFMonitor.setUserId(userId: String)

设置当前用户 ID。登录后调用，退出后传空字符串。

### WFMonitor.setUserParam(first: String = "", second: String = "")

设置自定义参数（两个扩展字段），会上报到每条日志中。

### WFMonitor.startPageTrace(pageName: String): PageTracker

手动开始页面耗时追踪，返回 `PageTracker` 对象。数据加载完成后调用 `tracker.finish()` 上报 `MOBILE_PAGE_LOAD`。

```kotlin
val tracker = WFMonitor.startPageTrace("OrderDetailPage")
fetchData { data ->
    tracker.markDataLoaded()   // 可选：标记数据加载完成
    tracker.markViewBuilt()    // 可选：标记视图构建完成
    tracker.finish()
}
```

### WFMonitor.reportError(throwable: Throwable, errorScene: String = "")

上报业务层 catch 到的异常（不会导致崩溃的错误）。

```kotlin
try {
    riskOperation()
} catch (e: Exception) {
    WFMonitor.reportError(e, "支付接口调用")
}
```

### WFMonitor.flush()

手动刷新上报队列。SDK 已在 App 退后台时自动 flush，一般不需要手动调用。

## 上报数据类型

| uploadType | 触发方式 | 说明 |
|------------|----------|------|
| `APP_START` | 自动 | 冷/温/热启动耗时 |
| `CUSTOMER_PV` | 自动 | 页面 PV（Activity / Fragment 切换） |
| `HTTP_LOG` | 自动 | OkHttp 请求拦截（需注入拦截器） |
| `APP_CRASH` | 自动 | Java 层崩溃（含重启后补报） |
| `ANR` | 自动 | 主线程卡死 > 阈值（默认 5s） |
| `JS_ERROR` | 手动 `reportError()` | 业务层 catch 到的异常 |
| `MOBILE_PAGE_LOAD` | 手动 `startPageTrace()` | 页面加载性能数据 |

## 网络配置

### 开发环境（HTTP）

SDK 默认走 HTTPS，如果开发环境使用 HTTP，需要配置 `network_security_config.xml`：

```xml
<!-- res/xml/network_security_config.xml -->
<network-security-config>
    <domain-config cleartextTrafficPermitted="true">
        <domain includeSubdomains="false">127.0.0.1</domain>
        <domain includeSubdomains="false">10.0.2.2</domain>     <!-- 模拟器 → 宿主机 -->
        <domain includeSubdomains="false">192.168.x.x</domain>  <!-- 真机 → 局域网 -->
    </domain-config>
</network-security-config>
```

并在 `AndroidManifest.xml` 中引用：

```xml
<application android:networkSecurityConfig="@xml/network_security_config">
```

### 模拟器调试

```
# 模拟器端口转发到宿主机
adb reverse tcp:8023 tcp:8023

# serverUrl 使用 127.0.0.1
serverUrl = "http://127.0.0.1:8023"
```

> 10.0.2.2 在部分模拟器（如新版本）可能不可用，推荐使用 `adb reverse` 方案。

## ProGuard / R8

SDK 内置 `consumer-rules.pro`，客户混淆时自动生效，无需手动配置。

## 依赖说明

| 依赖 | 类型 | 说明 |
|------|------|------|
| `okhttp 4.x` | `compileOnly` | HTTP 拦截器需要，客户需自行依赖 |
| `androidx.fragment` | `compileOnly` | Fragment 页面追踪需要，客户需自行依赖 |

> `compileOnly` 意味着 SDK 不会重复打包这些库，但如果客户 App 中未集成 OkHttp / Fragment，对应的 HTTP 拦截和 Fragment 追踪功能将不生效。

## 最低版本

- `minSdk`: 21 (Android 5.0)
- `compileSdk`: 34
- Kotlin: 1.9+

## 常见问题

**Q: 数据上报了但服务端看不到？**
A: 检查 `serverUrl` 是否正确（不含 `/wfMonitor/upLogs` 路径），确认 `webMonitorId` 与 Webfunny 控制台一致。

**Q: HTTP 请求失败，日志显示 `Cleartext HTTP traffic not permitted`？**
A: 参考上方「网络配置」章节，添加 `network_security_config.xml`。

**Q: 模拟器 `Failed to connect to 10.0.2.2`？**
A: 执行 `adb reverse tcp:8023 tcp:8023`，serverUrl 改为 `http://127.0.0.1:8023`。

**Q: 没有看到 Fragment 页面的 PV 数据？**
A: 确认客户 App 已集成 `androidx.fragment` 依赖（`compileOnly` 不会自动引入）。
