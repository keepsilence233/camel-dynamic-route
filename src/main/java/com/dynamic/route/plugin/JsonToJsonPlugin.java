package com.dynamic.route.plugin;

import com.dynamic.route.engine.PluginInvocation;
import com.dynamic.route.engine.PluginResult;
import com.dynamic.route.engine.RoutePlugin;
import com.dynamic.route.transform.TransformEngine;
import com.dynamic.route.transform.TransformTemplate;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * JSON → JSON 结构化转换插件。
 *
 * 支持任意深度的嵌套对象和多层数组（数组套数组），通过 TransformTemplate 统一驱动。
 * 接受 JSON 字符串或已解析的 Map 作为 body 输入。
 *
 * 详细配置说明见 TRANSFORM.md。
 */
@Component
public class JsonToJsonPlugin implements RoutePlugin {

    private final TransformEngine transformEngine;
    private final ObjectMapper objectMapper;

    public JsonToJsonPlugin(TransformEngine transformEngine, ObjectMapper objectMapper) {
        this.transformEngine = transformEngine;
        this.objectMapper = objectMapper;
    }

    @Override
    public String pluginCode() {
        return "json-to-json-mapping";
    }

    @Override
    public PluginResult execute(PluginInvocation invocation) {
        Map<String, Object> body = parseBody(invocation.input().get("body"));
        if (body == null) {
            return PluginResult.empty();
        }

        TransformTemplate template = transformEngine.parseTemplate(invocation.config());
        // 返回 Map 而非 JSON String：
        //   PRE 阶段 → HttpTargetExecutor 负责序列化后发送
        //   POST 阶段 → RouteResponse.data 直接持有 Map，Jackson 一次序列化为正确 JSON 对象
        Map<String, Object> result = transformEngine.apply(body, template);
        return new PluginResult(Map.of("body", result), Map.of());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseBody(Object rawBody) {
        if (rawBody instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        if (rawBody instanceof String s && !s.isBlank()) {
            try {
                return objectMapper.readValue(s, new TypeReference<>() {});
            } catch (Exception e) {
                throw new IllegalArgumentException("JsonToJsonMappingPlugin: body is not valid JSON", e);
            }
        }
        return null;
    }
}
