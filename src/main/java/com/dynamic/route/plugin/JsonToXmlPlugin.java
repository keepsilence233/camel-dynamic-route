package com.dynamic.route.plugin;

import com.dynamic.route.engine.PluginInvocation;
import com.dynamic.route.engine.PluginResult;
import com.dynamic.route.engine.RoutePlugin;
import com.dynamic.route.transform.TransformEngine;
import com.dynamic.route.transform.TransformTemplate;
import com.dynamic.route.util.JsonUtils;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * JSON → XML 转换插件。
 *
 * 执行流程：
 *   1. 将 body 解析为 Map（接受 JSON 字符串或 Map）
 *   2. 通过 TransformEngine 对 Map 做结构化转换（支持嵌套 + 数组）
 *   3. 将转换后的 Map 序列化为 XML 字符串
 *
 * 数组序列化说明：
 *   List 字段会被序列化为同名重复元素，例如：
 *   {"items":[{"id":1},{"id":2}]} → <root><items><id>1</id></items><items><id>2</id></items></root>
 *
 * 详细配置说明见 TRANSFORM.md。
 */
@Component
public class JsonToXmlPlugin implements RoutePlugin {

    private final TransformEngine transformEngine;
    private final XmlMapper xmlMapper = new XmlMapper();

    public JsonToXmlPlugin(TransformEngine transformEngine) {
        this.transformEngine = transformEngine;
    }

    @Override
    public String pluginCode() {
        return "json-to-xml";
    }

    @Override
    public PluginResult execute(PluginInvocation invocation) {
        Map<String, Object> body = parseBody(invocation.input().get("body"));
        if (body == null) {
            return PluginResult.empty();
        }

        TransformTemplate template = transformEngine.parseTemplate(invocation.config());
        Map<String, Object> transformed = transformEngine.apply(body, template);

        String rootElement = template.rootElementOrDefault("root");
        try {
            String xml = xmlMapper.writer().withRootName(rootElement).writeValueAsString(transformed);
            return new PluginResult(Map.of("body", xml), Map.of());
        } catch (Exception e) {
            throw new IllegalStateException("JsonToXmlPlugin: failed to serialize XML", e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseBody(Object rawBody) {
        if (rawBody instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        if (rawBody instanceof String s && !s.isBlank()) {
            return JsonUtils.parseMap(s);
        }
        return null;
    }
}
