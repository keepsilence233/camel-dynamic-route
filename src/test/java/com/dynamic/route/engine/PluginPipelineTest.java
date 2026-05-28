package com.dynamic.route.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.dynamic.route.model.FailStrategy;
import com.dynamic.route.model.PathMatchType;
import com.dynamic.route.model.PluginDefinition;
import com.dynamic.route.model.PluginExecutionPhase;
import com.dynamic.route.model.PluginPhase;
import com.dynamic.route.model.RouteDefinition;
import com.dynamic.route.model.RoutePluginBinding;
import com.dynamic.route.model.RouteTarget;
import com.dynamic.route.model.TargetType;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PluginPipelineTest {

    @Test
    @DisplayName("applyPrePlugins keeps body when noop plugin is bound")
    void applyPrePlugins_whenNoOpPluginBound_keepsBody() {
        PluginRegistry pluginRegistry = new PluginRegistry(Map.of("noopRoutePlugin", new NoOpRoutePlugin()));
        PluginPipeline pipeline = new PluginPipeline(pluginRegistry);
        RouteContext context = new RouteContext(
            "trace-1",
            new RouteRequest("demo-app", "/dynamic-router/demo", "POST", "json", null, null, Map.of(), Map.of("value", 1)),
            new RouteDefinition(
                1L, "demo-route", "demo-app", "Demo", "netty-http", "/dynamic-router/demo",
                PathMatchType.EXACT, "POST", "json", null, null, 1, "target-1", 1000, 0,
                "ACTIVE", 1, null, null, null, Instant.now(), Instant.now()
            ),
            new RouteTarget(
                1L, "target-1", "Target", TargetType.HTTP, "direct:test-http", "http", null,
                "forward", "{}", null, "ACTIVE", 1, null, Instant.now(), Instant.now()
            ),
            List.of(new RouteContext.ResolvedPlugin(
                new RoutePluginBinding(1L, "demo-route", "noop-plugin", PluginExecutionPhase.PRE, 1, true, FailStrategy.FAIL_FAST, null, Instant.now(), Instant.now()),
                new PluginDefinition(1L, "noop-plugin", "Noop", PluginPhase.PRE_TRANSFORM, "route", "noopRoutePlugin", null, null, "ACTIVE", null, Instant.now(), Instant.now())
            )),
            List.of(),
            Map.of(),
            Map.of("value", 1),
            null
        );

        RouteContext result = pipeline.applyPrePlugins(context);

        assertThat(result.requestBody()).isEqualTo(Map.of("value", 1));
    }
}
