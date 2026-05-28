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
 * XML → JSON 转换插件。
 *
 * 执行流程：
 *   1. 将 XML 字符串解析为 Map（XmlMapper）
 *   2. 修正数组歧义（forceArrayElements）：XmlMapper 无法区分「单元素数组」和「对象」
 *   3. 通过 TransformEngine 做结构化转换（支持嵌套 + 数组）
 *   4. 将 Map 序列化为 JSON 字符串
 *
 * 数组歧义说明：
 *   若 XML 中 <items> 只出现一次，XmlMapper 会将其解析为 Map 而非 List。
 *   通过 forceArrayElements 指定哪些元素名应始终视为数组，插件会自动将单 Map 包装成 List。
 *
 * 详细配置说明见 TRANSFORM.md。
 */
@Component
public class XmlToJsonPlugin implements RoutePlugin {

    private final TransformEngine transformEngine;
    private final XmlMapper xmlMapper = new XmlMapper();

    public XmlToJsonPlugin(TransformEngine transformEngine) {
        this.transformEngine = transformEngine;
    }

    @Override
    public String pluginCode() {
        return "xml-to-json";
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
            throw new IllegalArgumentException("XmlToJsonPlugin: failed to parse XML body", e);
        }

        // 修正 XmlMapper 的单元素数组歧义
        Map<String, Object> fixed = StructuralNavigator.fixSingletonArrays(parsed, template.forceArrayElements());

        // 返回 Map 而非 JSON String（原因同 JsonToJsonPlugin）
        Map<String, Object> transformed = transformEngine.apply(fixed, template);
        return new PluginResult(Map.of("body", transformed), Map.of());
    }
}
