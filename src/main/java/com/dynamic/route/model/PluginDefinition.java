package com.dynamic.route.model;

import java.time.Instant;

public record PluginDefinition(
    Long id,
    String pluginCode,
    String pluginName,
    PluginPhase pluginPhase,
    String pluginScope,
    String beanName,
    String pluginClass,
    String configSchemaJson,
    String status,
    String remark,
    Instant createdAt,
    Instant updatedAt
) {
}
