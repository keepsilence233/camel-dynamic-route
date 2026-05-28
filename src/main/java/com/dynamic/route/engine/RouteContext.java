package com.dynamic.route.engine;

import com.dynamic.route.model.PluginDefinition;
import com.dynamic.route.model.RouteDefinition;
import com.dynamic.route.model.RoutePluginBinding;
import com.dynamic.route.model.RouteTarget;
import java.util.List;
import java.util.Map;

public record RouteContext(
    String traceId,
    RouteRequest request,
    RouteDefinition routeDefinition,
    RouteTarget routeTarget,
    List<ResolvedPlugin> prePlugins,
    List<ResolvedPlugin> postPlugins,
    Map<String, Object> attributes,
    Object requestBody,
    Object responseBody
) {

    public RouteContext withRequestBody(Object newRequestBody) {
        return new RouteContext(
            traceId,
            request,
            routeDefinition,
            routeTarget,
            prePlugins,
            postPlugins,
            attributes,
            newRequestBody,
            responseBody
        );
    }

    public RouteContext withResponseBody(Object newResponseBody) {
        return new RouteContext(
            traceId,
            request,
            routeDefinition,
            routeTarget,
            prePlugins,
            postPlugins,
            attributes,
            requestBody,
            newResponseBody
        );
    }

    public record ResolvedPlugin(RoutePluginBinding binding, PluginDefinition definition) {
    }
}
