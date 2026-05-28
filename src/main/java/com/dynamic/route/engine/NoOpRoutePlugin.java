package com.dynamic.route.engine;

import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class NoOpRoutePlugin implements RoutePlugin {

    @Override
    public String pluginCode() {
        return "noop-plugin";
    }

    @Override
    public PluginResult execute(PluginInvocation invocation) {
        Object body = invocation.input().get("body");
        return new PluginResult(Map.of("body", body), Map.of());
    }
}
