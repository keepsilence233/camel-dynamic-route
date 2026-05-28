package com.dynamic.route.transform;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 单条字段映射规则：重命名路径、类型转换、默认值、Groovy 脚本。
 *
 * 示例（JSON）：
 * {
 *   "from":    "orderLines[*].lineId",
 *   "to":      "orderLines[*].line_id",
 *   "type":    "string",
 *   "default": "UNKNOWN",
 *   "script":  "value?.toString()?.toUpperCase()"
 * }
 */
public record TransformMapping(
    @JsonProperty("from")    String from,
    @JsonProperty("to")      String to,
    @JsonProperty("type")    String type,
    @JsonProperty("default") String defaultValue,
    @JsonProperty("script")  String script
) {
}
