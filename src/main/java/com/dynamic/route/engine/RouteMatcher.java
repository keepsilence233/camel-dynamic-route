package com.dynamic.route.engine;

import com.dynamic.route.model.RouteDefinition;
import java.util.Optional;

public interface RouteMatcher {
    Optional<RouteDefinition> match(RouteRequest request, RouteConfigurationSnapshot snapshot);
}
