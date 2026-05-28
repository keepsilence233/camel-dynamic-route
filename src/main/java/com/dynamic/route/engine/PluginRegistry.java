package com.dynamic.route.engine;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class PluginRegistry {

    private final Map<String, RoutePlugin> pluginsByCode;

    // Spring 自动注入全部 RoutePlugin bean；以 pluginCode() 重新建索引，与 Spring bean 名解耦
    public PluginRegistry(Map<String, RoutePlugin> pluginsByBeanName) {
        this.pluginsByCode = pluginsByBeanName.values().stream()
            .collect(Collectors.toUnmodifiableMap(RoutePlugin::pluginCode, Function.identity()));
    }

    public RoutePlugin requirePlugin(String pluginCode) {
        RoutePlugin plugin = pluginsByCode.get(pluginCode);
        if (plugin == null) {
            throw new IllegalStateException("Plugin not registered: " + pluginCode);
        }
        return plugin;
    }
}
