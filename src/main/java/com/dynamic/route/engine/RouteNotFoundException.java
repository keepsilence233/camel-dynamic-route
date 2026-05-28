package com.dynamic.route.engine;

public class RouteNotFoundException extends RuntimeException {

    public RouteNotFoundException(String appCode, String path, String method) {
        super("Route not found: appCode=%s, path=%s, method=%s".formatted(appCode, path, method));
    }
}
