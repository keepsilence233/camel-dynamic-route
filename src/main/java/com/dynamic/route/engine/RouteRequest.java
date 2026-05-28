package com.dynamic.route.engine;

import java.util.Map;

public record RouteRequest(
    String appCode,
    String path,
    String method,
    String requestFormat,
    String contentType,
    String acceptType,
    Map<String, Object> headers,
    Object body
) {
}
