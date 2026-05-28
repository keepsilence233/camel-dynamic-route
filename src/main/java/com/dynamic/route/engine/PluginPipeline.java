package com.dynamic.route.engine;

import com.dynamic.route.model.FailStrategy;
import com.dynamic.route.util.JsonUtils;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class PluginPipeline {

    private static final Logger log = LoggerFactory.getLogger(PluginPipeline.class);

    private final PluginRegistry pluginRegistry;

    public PluginPipeline(PluginRegistry pluginRegistry) {
        this.pluginRegistry = pluginRegistry;
    }

    public RouteContext applyPrePlugins(RouteContext context) {
        return applyPlugins(context, context.prePlugins(), true);
    }

    public RouteContext applyPostPlugins(RouteContext context) {
        return applyPlugins(context, context.postPlugins(), false);
    }

    private RouteContext applyPlugins(RouteContext context, List<RouteContext.ResolvedPlugin> plugins, boolean requestPhase) {
        RouteContext current = context;
        for (RouteContext.ResolvedPlugin resolvedPlugin : plugins) {
            current = applyPlugin(current, resolvedPlugin, requestPhase);
        }
        return current;
    }

    private RouteContext applyPlugin(RouteContext context, RouteContext.ResolvedPlugin resolvedPlugin, boolean requestPhase) {
        String pluginCode = resolvedPlugin.definition().pluginCode();
        String phase = requestPhase ? "PRE" : "POST";
        log.debug("[{}] plugin={} phase={}", context.traceId(), pluginCode, phase);
        try {
            Map<String, Object> input = buildInput(context, requestPhase);
            Map<String, Object> config = parseConfig(resolvedPlugin.binding().pluginConfigJson());
            PluginResult result = pluginRegistry.requirePlugin(pluginCode)
                .execute(new PluginInvocation(context.traceId(), context.routeDefinition().routeCode(), pluginCode, input, config));
            return mergeResult(context, result, requestPhase);
        } catch (Exception exception) {
            if (resolvedPlugin.binding().failStrategy() == FailStrategy.CONTINUE) {
                // 插件异常时丢弃其输出，以未修改的上下文继续后续流程
                log.warn("[{}] plugin={} phase={} failed, CONTINUE: {}",
                    context.traceId(), pluginCode, phase, exception.getMessage());
                return context;
            }
            log.error("[{}] plugin={} phase={} failed, FAIL_FAST",
                context.traceId(), pluginCode, phase, exception);
            if (exception instanceof RuntimeException re) {
                throw re;
            }
            throw new RuntimeException(exception);
        }
    }

    private Map<String, Object> buildInput(RouteContext context, boolean requestPhase) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("headers", context.request().headers());
        input.put("attributes", context.attributes());
        input.put("body", requestPhase ? context.requestBody() : context.responseBody());
        return Map.copyOf(input);
    }

    private RouteContext mergeResult(RouteContext context, PluginResult result, boolean requestPhase) {
        Map<String, Object> mergedAttributes = new LinkedHashMap<>(context.attributes());
        mergedAttributes.putAll(result.attributes());
        Object body = result.output().getOrDefault("body", requestPhase ? context.requestBody() : context.responseBody());
        RouteContext next = new RouteContext(
            context.traceId(),
            context.request(),
            context.routeDefinition(),
            context.routeTarget(),
            context.prePlugins(),
            context.postPlugins(),
            Map.copyOf(mergedAttributes),
            context.requestBody(),
            context.responseBody()
        );
        return requestPhase ? next.withRequestBody(body) : next.withResponseBody(body);
    }

    private Map<String, Object> parseConfig(String configJson) {
        if (configJson == null || configJson.isBlank()) {
            return Map.of();
        }
        return JsonUtils.parseMap(configJson);
    }
}
