# Webfunny Monitor SDK Consumer ProGuard Rules
# 接入方在混淆自己的 App 时，会同时应用此文件中的规则

# 保留 SDK 公开 API（防止混淆后调用方无法使用）
-keep class com.webfunny.monitor.WFMonitor { public *; }
-keep class com.webfunny.monitor.PageTracker { public *; }
-keep class com.webfunny.monitor.core.WFConfig { public *; }

# 保留 OkHttp Interceptor（反射调用）
-keep class com.webfunny.monitor.collector.HttpInterceptor { *; }
