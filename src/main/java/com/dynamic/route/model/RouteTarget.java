package com.dynamic.route.model;

import java.time.Instant;

public record RouteTarget(
    Long id,
    String targetCode,
    String targetName,
    TargetType targetType,
    String endpointUri,
    String componentName,
    String datasourceName,
    String operationType,
    String configJson,
    String secretRef,
    String status,
    long version,
    String remark,
    Instant createdAt,
    Instant updatedAt
) {
}
