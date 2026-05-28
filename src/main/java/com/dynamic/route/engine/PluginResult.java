package com.dynamic.route.engine;

import java.util.Map;

public record PluginResult(
    Map<String, Object> output,
    Map<String, Object> attributes
) {

    public static PluginResult empty() {
        return new PluginResult(Map.of(), Map.of());
    }
}
