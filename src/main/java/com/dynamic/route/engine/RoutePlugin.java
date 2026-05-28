package com.dynamic.route.engine;

public interface RoutePlugin {
    String pluginCode();

    PluginResult execute(PluginInvocation invocation);
}
