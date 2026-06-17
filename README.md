# Webfunny Monitor Android SDK

Android 全链路监控 SDK，对接 [Webfunny 监控平台](https://www.webfunny.com)，接入后自动采集崩溃、ANR、HTTP 请求、页面 PV、性能数据。

---

## 1. 添加依赖

### 方式一：JitPack 远程引用（推荐）

在以下两个文件中添加配置：

```kotlin
// settings.gradle.kts
// 在 dependencyResolutionManagement { repositories { ... } } 中添加 JitPack 仓库
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }   // ← 加这一行
    }
}
```

```kotlin
// app/build.gradle.kts
// 在 dependencies { ... } 中添加 SDK
dependencies {
    implementation("com.github.a597873885:webfunny-monitor-android:1.0.2")
    
    // ... 你已有的其他依赖 ...
}
```

改完后点一下 Android Studio 顶部的 **Sync Now**（或 File → Sync Project with Gradle Files）。

### 方式二：本地模块引用（源码调试）

如果你 clone 了 SDK 源码仓库，想在本地调试，用模块引用替代：

```kotlin
// settings.gradle.kts — 在最下方添加
include(":webfunny-monitor-android")
project(":webfunny-monitor-android").projectDir = File("../webfunny-monitor-android")
```

```kotlin
// app/build.gradle.kts
dependencies {
    implementation(project(":webfunny-monitor-android"))
    
    // ... 你已有的其他依赖 ...
}
```

---

## 2. 创建 Application 类并初始化 SDK

如果你还没有自定义 `Application` 类，需要创建一个。如果你已经有了，直接在现有的 Application 里加初始化代码即可。

### 2.1 创建 Application 类

在 `app/src/main/java/<你的包名>/` 目录下创建一个 Kotlin 文件（比如叫 `MyApp.kt`）：

```kotlin
package com.example.yourapp       // ← 替换成你的包名

import android.app.Application
import com.webfunny.monitor.WFMonitor
import com.webfunny.monitor.core.WFConfig

class MyApp : Application() {

    override fun onCreate() {
        // ⚠️ markAppInit() 必须在 super.onCreate() 之前调用
        WFMonitor.markAppInit()
        super.onCreate()

        WFMonitor.init(this, WFConfig(
            serverUrl    = "https://your-server.com",   // 你的监控服务器地址
            webMonitorId = "your_monitor_id",           // Webfunny 控制台的项目 ID
            debugLog     = true                         // 调试时打印日志，发布时改为 false
        ))

        // （可选）用户登录后调用，用于排查具体用户的问题
        // WFMonitor.setUserId("user_123")
    }
}
```

> **serverUrl 从哪里来？** 登录 Webfunny 控制台 → 创建/打开项目 → 查看「应用配置」。
> **webMonitorId 从哪里来？** 同上，控制台会显示一个类似 `webfunny_20251208_165649_pro` 的字符串。

### 2.2 在 AndroidManifest.xml 中注册

打开 `app/src/main/AndroidManifest.xml`，在 `<application>` 标签上加上 `android:name` 属性：

```xml
<application
    android:name=".MyApp"            <!-- ← 加这一行，指向你刚创建的 Application 类 -->
    android:allowBackup="true"
    android:icon="@mipmap/ic_launcher"
    android:label="@string/app_name"
    ...>
    
    <!-- 你的 Activity 等配置 ... -->
    
</application>
```

### 2.3 验证接入是否成功

运行 App 后，在 Android Studio 底部打开 **Logcat**，搜索 `WFMonitor`，如果看到类似以下日志说明接入成功：

```
WFMonitor  D  ══════════════════════════════════
WFMonitor  D  SDK 初始化完成
WFMonitor  D  上报地址: https://your-server.com/wfMonitor/upLogs
WFMonitor  D  项目 ID:  your_monitor_id
WFMonitor  D  崩溃: ✓  ANR: ✓  HTTP: ✓  FPS: ✓  内存: ✓
WFMonitor  D  ══════════════════════════════════
```

如果看不到，请回到 2.2 确认 `AndroidManifest.xml` 中是否注册了 Application 类。

---

## 3. OkHttp 拦截器（可选，但强烈推荐）

在构建 `OkHttpClient` 时加入一行拦截器，SDK 会自动拦截并上报所有 HTTP 请求：

```kotlin
import com.webfunny.monitor.WFMonitor        // ← 别忘了 import

val client = OkHttpClient.Builder()
    .addInterceptor(WFMonitor.httpInterceptor())   // ← 加这一行
    .build()
```

> **注意**：需要项目中已经集成了 `com.squareup.okhttp3:okhttp:4.x`。如果没有，在 `app/build.gradle.kts` 中添加：
> ```kotlin
> implementation("com.squareup.okhttp3:okhttp:4.12.0")
> ```

---

## 4. 网络配置（使用 HTTP 开发环境时需要）

SDK 上报数据默认走 HTTPS。如果你在本地开发，服务器地址是 `http://127.0.0.1:8023` 之类的 HTTP 协议，Android 高版本默认禁止明文 HTTP，需要配置白名单。

### 4.1 创建网络安全配置文件

新建 `app/src/main/res/xml/network_security_config.xml`：

```xml
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <domain-config cleartextTrafficPermitted="true">
        <domain includeSubdomains="false">127.0.0.1</domain>
        <domain includeSubdomains="false">10.0.2.2</domain>     <!-- 模拟器访问宿主机 -->
    </domain-config>
</network-security-config>
```

### 4.2 在 AndroidManifest.xml 中引用

```xml
<application
    android:name=".MyApp"
    android:networkSecurityConfig="@xml/network_security_config"   <!-- ← 加这一行 -->
    ...>
```

### 4.3 模拟器端口转发

如果使用 Android 模拟器，还需要执行一次端口转发（每次重启模拟器都需要）：

```bash
adb reverse tcp:8023 tcp:8023
```

> `10.0.2.2` 在新版模拟器上可能不可用，推荐用 `adb reverse` + `127.0.0.1` 方案。

---

## 5. API 参考

### WFMonitor.markAppInit()
记录进程启动基准时间。**必须在 `super.onCreate()` 之前调用**，只调用一次。

### WFMonitor.init(application, config)
初始化 SDK，在 `Application.onCreate()` 中调用，同样只调用一次。

### WFMonitor.httpInterceptor(): Interceptor
获取 OkHttp 拦截器。加到 `OkHttpClient.Builder` 后，SDK 会自动记录所有 HTTP 请求的耗时、状态码、请求/响应体等信息。

### WFMonitor.setUserId(userId: String)
设置当前用户 ID。登录成功后调用，退出登录后传空字符串 `""`。便于在监控平台上按用户排查问题。

### WFMonitor.setUserParam(first: String = "", second: String = "")
设置两个自定义参数，会附加到每一条上报日志中。可用于携带业务标识（如渠道、版本等）。

### WFMonitor.startPageTrace(pageName: String): PageTracker
手动开启一个页面的性能追踪，返回 `PageTracker` 对象。页面加载完成后调用 `tracker.finish()`，SDK 会上报该页面的加载耗时和 FPS、内存等数据。

```kotlin
val tracker = WFMonitor.startPageTrace("OrderDetailPage")

// 模拟异步加载数据
fetchData { data ->
    showData(data)
    tracker.finish()   // 数据加载并渲染完成后调用
}
```

### WFMonitor.reportError(throwable: Throwable, errorScene: String = "")
上报业务层捕获到的异常（不会导致 App 崩溃的错误），方便排查非崩溃类的业务问题。

```kotlin
try {
    riskOperation()
} catch (e: Exception) {
    WFMonitor.reportError(e, "支付接口调用")
    // App 正常继续运行，不会崩溃
}
```

### WFMonitor.flush()
手动刷新上报队列。SDK 在 App 退后台时会自动调用，一般不需要手动触发。

---

## 6. WFConfig 参数说明

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `serverUrl` | String | **必填** | 监控服务器地址，如 `https://monitor.example.com`（不含路径） |
| `webMonitorId` | String | **必填** | 项目监控 ID，Webfunny 控制台获取 |
| `enableCrash` | Boolean | `true` | 是否采集 Java 层崩溃 |
| `enableAnr` | Boolean | `true` | 是否检测 ANR（主线程卡顿 > 5s） |
| `enableHttp` | Boolean | `true` | 是否拦截 OkHttp 请求（需注入拦截器） |
| `enableFps` | Boolean | `true` | 是否采样 FPS |
| `enableMemory` | Boolean | `true` | 是否采样内存 |
| `samplingIntervalSec` | Long | `2` | FPS / 内存采样间隔（秒） |
| `maxQueueSize` | Int | `20` | 日志队列最大条数，超出后强制批量上报 |
| `flushIntervalMs` | Long | `10000` | 队列定时刷新间隔（毫秒） |
| `anrThresholdMs` | Long | `5000` | ANR 检测阈值（毫秒） |
| `debugLog` | Boolean | `false` | 是否在 Logcat 打印 SDK 调试日志 |
| `httpBodyMaxBytes` | Int | `8192` | HTTP 请求/响应体截取上限（字节），默认 8KB |

---

## 7. 自动采集的数据类型

| uploadType | 触发方式 | 说明 |
|------------|----------|------|
| `APP_START` | 自动 | 冷/温/热启动耗时 |
| `CUSTOMER_PV` | 自动 | 页面 PV（Activity / Fragment 切换时自动记录） |
| `HTTP_LOG` | 自动 | OkHttp 请求拦截（需注入拦截器） |
| `APP_CRASH` | 自动 | Java 层崩溃（含重启后补报上次崩溃） |
| `ANR` | 自动 | 主线程卡死超过阈值（默认 5 秒） |
| `JS_ERROR` | 调用 `reportError()` | 业务层 catch 到的异常 |
| `MOBILE_PAGE_LOAD` | 调用 `startPageTrace()` | 页面加载性能数据 |

---

## 8. 依赖说明

| 依赖 | 类型 | 说明 |
|------|------|------|
| `okhttp 4.x` | `compileOnly` | HTTP 拦截器需要，**你的 App 需要自行引入** |
| `androidx.fragment` | `compileOnly` | Fragment 页面追踪需要，**你的 App 需要自行引入** |

> `compileOnly` 的含义：SDK 不会把这些库打包进去，而是由你的 App 提供。如果你的 App 没有集成 OkHttp 或 Fragment，对应的 HTTP 拦截和 Fragment 追踪功能不会生效（其他功能不受影响）。

---

## 9. 环境要求

- **minSdk**: 21（Android 5.0）
- **compileSdk**: 34
- **Kotlin**: 1.9+
- 混淆：SDK 内置 `consumer-rules.pro`，App 开启混淆时自动生效，无需额外配置

---

## 10. 常见问题

**Q: 编译报错 `Unresolved reference: webfunny`？**
A: 你没有添加 SDK 依赖。回到第 1 步，检查 `app/build.gradle.kts` 中是否添加了 `implementation(...)`，然后点 Sync Now。

**Q: 能看到 SDK 初始化日志，但服务器收不到数据？**
A: 检查 `serverUrl` 是否正确——只需要填域名和端口（如 `https://monitor.example.com`），**不需要**加 `/wfMonitor/upLogs` 路径。同时确认 `webMonitorId` 与 Webfunny 控制台一致。

**Q: Logcat 显示 `Cleartext HTTP traffic not permitted`？**
A: 你的 serverUrl 用了 HTTP 协议（如 `http://127.0.0.1:8023`）。回到第 4 步，创建 `network_security_config.xml` 并注册。

**Q: 模拟器上报失败 `Failed to connect to 10.0.2.2`？**
A: 新版模拟器 `10.0.2.2` 可能不可用。改用 `adb reverse` 方案：执行 `adb reverse tcp:8023 tcp:8023`，然后 serverUrl 写成 `http://127.0.0.1:8023`。

**Q: 没有看到 Fragment 页面的 PV 数据？**
A: 检查你的 App 是否引入了 `androidx.fragment` 依赖（SDK 只是 `compileOnly`，不会自动带过来）。在 `app/build.gradle.kts` 中确认有 `implementation("androidx.fragment:fragment-ktx:1.6.0")` 之类的依赖。

**Q: 初始化代码中的 `BuildConfig.DEBUG` 报红？**
A: 文档已改用 `debugLog = true` 的写法，直接填 `true` 即可，不需要依赖 `BuildConfig`。发布时手动改为 `false`。
