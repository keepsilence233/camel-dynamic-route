package com.dynamic.route.plugin;

import com.dynamic.route.engine.PluginInvocation;
import com.dynamic.route.engine.PluginResult;
import com.dynamic.route.engine.RoutePlugin;
import com.dynamic.route.transform.StructuralNavigator;
import com.dynamic.route.transform.TransformEngine;
import com.dynamic.route.transform.TransformTemplate;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * XML → XML 结构化转换插件。支持任意深度的嵌套和数组套数组。
 *
 * 执行流程：
 *   1. 将 XML 字符串解析为 Map（XmlMapper）
 *   2. 修正数组歧义（forceArrayElements）
 *   3. 通过 TransformEngine 做结构化转换
 *   4. 将 Map 序列化回 XML 字符串（根元素由 rootElement 决定）
 *
 * 详细配置说明见 TRANSFORM.md。
 */
@Component
public class XmlToXmlPlugin implements RoutePlugin {

    private final TransformEngine transformEngine;
    private final XmlMapper xmlMapper = new XmlMapper();

    public XmlToXmlPlugin(TransformEngine transformEngine) {
        this.transformEngine = transformEngine;
    }

    @Override
    public String pluginCode() {
        return "xml-to-xml-mapping";
    }

    @Override
    public PluginResult execute(PluginInvocation invocation) {
        Object rawBody = invocation.input().get("body");
        if (!(rawBody instanceof String xmlStr) || xmlStr.isBlank()) {
            return PluginResult.empty();
        }

        TransformTemplate template = transformEngine.parseTemplate(invocation.config());

        Map<String, Object> parsed;
        try {
            parsed = xmlMapper.readValue(xmlStr, new TypeReference<>() {});
        } catch (Exception e) {
            throw new IllegalArgumentException("XmlToXmlPlugin: failed to parse XML body", e);
        }

        Map<String, Object> fixed = StructuralNavigator.fixSingletonArrays(parsed, template.forceArrayElements());
        Map<String, Object> transformed = transformEngine.apply(fixed, template);

        String rootElement = template.rootElementOrDefault("root");
        try {
            String resultXml = xmlMapper.writer().withRootName(rootElement).writeValueAsString(transformed);
            return new PluginResult(Map.of("body", resultXml), Map.of());
        } catch (Exception e) {
            throw new IllegalStateException("XmlToXmlPlugin: failed to serialize XML", e);
        }
    }
}
