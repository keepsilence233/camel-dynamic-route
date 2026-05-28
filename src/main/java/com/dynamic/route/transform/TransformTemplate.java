package com.dynamic.route.transform;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * 转换模板：描述一次完整的结构化数据转换。
 * 对应插件绑定中的 plugin_config_json。
 *
 * 字段说明：
 *   mappings           — 字段映射（重命名 + 类型转换 + 脚本）
 *   addFields          — 新增固定字段
 *   removeFields       — 删除字段（支持嵌套路径）
 *   rootElement        — XML 输出时的根元素名（JsonToXml / XmlToXml 使用）
 *   forceArrayElements — XML 解析时强制视为数组的元素名（XmlToJson / XmlToXml 使用）
 */
public record TransformTemplate(
    @JsonProperty("mappings")            List<TransformMapping> mappings,
    @JsonProperty("addFields")           List<AddFieldSpec>     addFields,
    @JsonProperty("removeFields")        List<String>           removeFields,
    @JsonProperty("rootElement")         String                 rootElement,
    @JsonProperty("forceArrayElements")  List<String>           forceArrayElements
) {

    public TransformTemplate {
        mappings           = mappings           != null ? List.copyOf(mappings)           : List.of();
        addFields          = addFields          != null ? List.copyOf(addFields)          : List.of();
        removeFields       = removeFields       != null ? List.copyOf(removeFields)       : List.of();
        forceArrayElements = forceArrayElements != null ? List.copyOf(forceArrayElements) : List.of();
    }

    public String rootElementOrDefault(String fallback) {
        return (rootElement != null && !rootElement.isBlank()) ? rootElement : fallback;
    }
}
