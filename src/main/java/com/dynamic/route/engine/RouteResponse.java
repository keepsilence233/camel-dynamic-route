package com.dynamic.route.engine;

public record RouteResponse(
    boolean success,
    Object data,
    String errorCode,
    String errorMessage,
    String traceId
) {

    public static RouteResponse ok(String traceId, Object data) {
        return new RouteResponse(true, data, null, null, traceId);
    }

    public static RouteResponse error(String traceId, String errorCode, String errorMessage) {
        return new RouteResponse(false, null, errorCode, errorMessage, traceId);
    }
}
