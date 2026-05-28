package com.dynamic.route.model;

// route_plugin_binding 的执行门控：PRE 在调用 target 前运行，POST 在拿到 target 响应后运行
public enum PluginExecutionPhase {
    PRE,
    POST;

    public static PluginExecutionPhase fromValue(String value) {
        return PluginExecutionPhase.valueOf(value.trim().toUpperCase());
    }
}
