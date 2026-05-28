package com.dynamic.route.model;

// plugin_definition 中的能力声明，描述插件在扩展流水线中的位置；运行时绑定用 PluginExecutionPhase（仅 PRE/POST）
public enum PluginPhase {
    PRE,
    POST,
    PRE_TRANSFORM,
    PRE_ENCRYPT,
    POST_TRANSFORM;

    public static PluginPhase fromValue(String value) {
        return PluginPhase.valueOf(value.trim().toUpperCase().replace('-', '_'));
    }
}
