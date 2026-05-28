package com.dynamic.route.engine;

import java.util.Map;

public record PluginInvocation(
    String traceId,
    String routeCode,
    String pluginCode,
    Map<String, Object> input,
    Map<String, Object> config
) {
}
