package com.dynamic.route.engine;

import com.dynamic.route.model.PluginDefinition;
import com.dynamic.route.model.RouteDefinition;
import com.dynamic.route.model.RoutePluginBinding;
import com.dynamic.route.model.RouteTarget;
import java.util.List;
import java.util.Map;

public record RouteConfigurationSnapshot(
    Map<String, RouteTarget> targetsByCode,
    Map<String, PluginDefinition> pluginsByCode,
    Map<String, List<RoutePluginBinding>> bindingsByRouteCode,
    List<RouteDefinition> routes
) {
}
