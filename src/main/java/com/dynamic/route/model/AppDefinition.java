package com.dynamic.route.model;

import java.time.Instant;

public record AppDefinition(
    Long id,
    String appCode,
    String appName,
    String status,
    String remark,
    Instant createdAt,
    Instant updatedAt
) {
}
