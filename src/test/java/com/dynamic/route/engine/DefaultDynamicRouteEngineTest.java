package com.dynamic.route.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.dynamic.route.executor.TargetExecutor;
import com.dynamic.route.executor.TargetExecutorRegistry;
import com.dynamic.route.model.PathMatchType;
import com.dynamic.route.model.RouteDefinition;
import com.dynamic.route.model.RouteTarget;
import com.dynamic.route.model.TargetType;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DefaultDynamicRouteEngineTest {

    @Test
    @DisplayName("route returns executor response when route is matched")
    void route_whenMatched_returnsExecutorResponse() {
        RouteDefinition route = new RouteDefinition(
            1L, "demo-route", "demo-app", "Demo", "netty-http", "/dynamic-router/demo",
            PathMatchType.EXACT, "POST", "json", null, null, 1, "target-1", 1000, 0,
            "ACTIVE", 1, null, null, null, Instant.now(), Instant.now()
        );
        RouteTarget target = new RouteTarget(
            1L, "target-1", "Target", TargetType.HTTP, "direct:test-http", "http", null,
            "forward", "{}", null, "ACTIVE", 1, null, Instant.now(), Instant.now()
        );
        RouteConfigurationCache cache = new RouteConfigurationCacheStub(
            new RouteConfigurationSnapshot(Map.of("target-1", target), Map.of(), Map.of(), List.of(route))
        );
        TargetExecutorRegistry executorRegistry = new TargetExecutorRegistry(Map.of("testExecutor", new TestExecutor()));
        DefaultDynamicRouteEngine engine = new DefaultDynamicRouteEngine(
            cache,
            new DefaultRouteMatcher(),
            new PluginPipeline(new PluginRegistry(Map.of("noopRoutePlugin", new NoOpRoutePlugin())), new com.fasterxml.jackson.databind.ObjectMapper()),
            executorRegistry
        );

        RouteResponse response = engine.route(new RouteRequest(
            "demo-app", "/dynamic-router/demo", "POST", "json", null, null, Map.of(), Map.of("hello", "world")
        ));

        assertThat(response.success()).isTrue();
        assertThat(response.data()).isEqualTo(Map.of("status", "ok"));
    }

    private static final class TestExecutor implements TargetExecutor {
        @Override
        public String supportType() {
            return "http";
        }

        @Override
        public Object execute(RouteContext context) {
            return Map.of("status", "ok");
        }
    }

    private static final class RouteConfigurationCacheStub extends RouteConfigurationCache {
        private final RouteConfigurationSnapshot snapshot;

        private RouteConfigurationCacheStub(RouteConfigurationSnapshot snapshot) {
            super(null);
            this.snapshot = snapshot;
        }

        @Override
        public RouteConfigurationSnapshot currentSnapshot() {
            return snapshot;
        }

        @Override
        public void refresh() {
        }
    }
}
