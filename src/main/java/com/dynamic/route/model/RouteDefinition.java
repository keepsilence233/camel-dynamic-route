package com.dynamic.route.model;

import java.time.Instant;

public record RouteDefinition(
    Long id,
    String routeCode,
    String appCode,
    String routeName,
    String entryProtocol,
    String requestPath,
    PathMatchType pathMatchType,
    String requestMethod,
    String requestFormat,
    String contentType,
    String acceptType,
    int routeOrder,
    String targetCode,
    Integer timeoutMs,
    int retryTimes,
    String status,
    long version,
    String remark,
    String createdBy,
    String updatedBy,
    Instant createdAt,
    Instant updatedAt
) {
}
