package com.dynamic.route.engine;

import com.dynamic.route.executor.TargetExecutorRegistry;
import com.dynamic.route.model.PluginExecutionPhase;
import com.dynamic.route.model.RouteDefinition;
import com.dynamic.route.model.RoutePluginBinding;
import com.dynamic.route.model.RouteTarget;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class DefaultDynamicRouteEngine implements DynamicRouteEngine {

    private static final Logger log = LoggerFactory.getLogger(DefaultDynamicRouteEngine.class);

    private final RouteConfigurationCache cache;
    private final RouteMatcher routeMatcher;
    private final PluginPipeline pluginPipeline;
    private final TargetExecutorRegistry targetExecutorRegistry;
    private final ObjectMapper objectMapper;

    public DefaultDynamicRouteEngine(
        RouteConfigurationCache cache,
        RouteMatcher routeMatcher,
        PluginPipeline pluginPipeline,
        TargetExecutorRegistry targetExecutorRegistry,
        ObjectMapper objectMapper
    ) {
        this.cache = cache;
        this.routeMatcher = routeMatcher;
        this.pluginPipeline = pluginPipeline;
        this.targetExecutorRegistry = targetExecutorRegistry;
        this.objectMapper = objectMapper;
    }

    @Override
    public RouteResponse route(RouteRequest request) {
        RouteConfigurationSnapshot snapshot = cache.currentSnapshot();
        RouteDefinition routeDefinition = routeMatcher.match(request, snapshot)
            .orElseThrow(() -> new RouteNotFoundException(request.appCode(), request.path(), request.method()));
        RouteTarget routeTarget = requireTarget(snapshot, routeDefinition.targetCode());
        RouteContext context = buildContext(request, routeDefinition, routeTarget, snapshot);
        log.debug("[{}] matched route={} target={} type={}",
            context.traceId(), routeDefinition.routeCode(),
            routeTarget.targetCode(), routeTarget.targetType());
        RouteContext preProcessed = pluginPipeline.applyPrePlugins(context);
        Object responseBody = targetExecutorRegistry.require(routeTarget.targetType().name().toLowerCase())
            .execute(preProcessed);
        RouteContext responded = preProcessed.withResponseBody(responseBody);
        RouteContext postProcessed = pluginPipeline.applyPostPlugins(responded);
        return RouteResponse.ok(postProcessed.traceId(), unwrapIfJsonString(postProcessed.responseBody()));
    }

    private RouteTarget requireTarget(RouteConfigurationSnapshot snapshot, String targetCode) {
        RouteTarget routeTarget = snapshot.targetsByCode().get(targetCode);
        if (routeTarget == null) {
            throw new IllegalStateException("Target not found: " + targetCode);
        }
        return routeTarget;
    }

    private RouteContext buildContext(
        RouteRequest request,
        RouteDefinition routeDefinition,
        RouteTarget routeTarget,
        RouteConfigurationSnapshot snapshot
    ) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("appCode", request.appCode());
        attributes.put("routeCode", routeDefinition.routeCode());
        return new RouteContext(
            UUID.randomUUID().toString(),
            request,
            routeDefinition,
            routeTarget,
            resolvePlugins(routeDefinition.routeCode(), PluginExecutionPhase.PRE, snapshot),
            resolvePlugins(routeDefinition.routeCode(), PluginExecutionPhase.POST, snapshot),
            Map.copyOf(attributes),
            request.body(),
            null
        );
    }

    private List<RouteContext.ResolvedPlugin> resolvePlugins(
        String routeCode,
        PluginExecutionPhase phase,
        RouteConfigurationSnapshot snapshot
    ) {
        return snapshot.bindingsByRouteCode().getOrDefault(routeCode, List.of()).stream()
            .filter(binding -> binding.pluginPhase() == phase)
            .map(binding -> new RouteContext.ResolvedPlugin(
                binding,
                requirePluginDefinition(snapshot, binding.pluginCode())
            ))
            .toList();
    }

    /**
     * 兜底解析：若 POST 插件返回的是 JSON String（未转成 Map），尝试反序列化为对象，
     * 避免 RouteResponse.data 被 Jackson 双重序列化为转义字符串。
     * XML String / 普通文本解析失败时保持原值不变。
     */
    private Object unwrapIfJsonString(Object body) {
        if (!(body instanceof String s) || s.isBlank()) {
            return body;
        }
        String t = s.stripLeading();
        if (!t.startsWith("{") && !t.startsWith("[")) {
            return body; // 非 JSON（如 XML），直接返回原 String
        }
        try {
            return objectMapper.readValue(s, Object.class);
        } catch (Exception ignored) {
            return body;
        }
    }

    private com.dynamic.route.model.PluginDefinition requirePluginDefinition(
        RouteConfigurationSnapshot snapshot,
        String pluginCode
    ) {
        com.dynamic.route.model.PluginDefinition pluginDefinition = snapshot.pluginsByCode().get(pluginCode);
        if (pluginDefinition == null) {
            throw new IllegalStateException("Plugin definition not found: " + pluginCode);
        }
        return pluginDefinition;
    }
}
