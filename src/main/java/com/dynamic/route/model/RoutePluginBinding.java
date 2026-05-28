package com.dynamic.route.model;

import java.time.Instant;

public record RoutePluginBinding(
    Long id,
    String routeCode,
    String pluginCode,
    PluginExecutionPhase pluginPhase,
    int sortOrder,
    boolean enabled,
    FailStrategy failStrategy,
    String pluginConfigJson,
    Instant createdAt,
    Instant updatedAt
) {
}
