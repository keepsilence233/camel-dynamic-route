package com.dynamic.route.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.dynamic.route.model.PathMatchType;
import com.dynamic.route.model.RouteDefinition;
import com.dynamic.route.model.RouteTarget;
import com.dynamic.route.model.TargetType;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DefaultRouteMatcherTest {

    private final DefaultRouteMatcher matcher = new DefaultRouteMatcher();

    @Test
    @DisplayName("match returns route when app path method and format match")
    void match_whenRequestMatches_returnsRoute() {
        RouteDefinition route = new RouteDefinition(
            1L,
            "demo-route",
            "demo-app",
            "Demo Route",
            "netty-http",
            "/dynamic-router/demo",
            PathMatchType.EXACT,
            "POST",
            "json",
            "application/json",
            "application/json",
            1,
            "demo-http-target",
            3000,
            0,
            "ACTIVE",
            1,
            null,
            null,
            null,
            Instant.now(),
            Instant.now()
        );
        RouteConfigurationSnapshot snapshot = new RouteConfigurationSnapshot(Map.of(), Map.of(), Map.of(), List.of(route));

        var result = matcher.match(
            new RouteRequest(
                "demo-app",
                "/dynamic-router/demo",
                "POST",
                "json",
                "application/json",
                "application/json",
                Map.of(),
                Map.of("name", "value")
            ),
            snapshot
        );

        assertThat(result).contains(route);
    }
}
