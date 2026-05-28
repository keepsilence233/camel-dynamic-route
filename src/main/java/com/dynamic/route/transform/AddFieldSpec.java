package com.dynamic.route.transform;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 新增字段规格：在目标路径处写入固定值（支持嵌套路径和数组展开）。
 *
 * 示例（JSON）：
 * { "path": "orderLines[*].source", "value": "gateway", "type": "string" }
 */
public record AddFieldSpec(
    @JsonProperty("path")  String path,
    @JsonProperty("value") Object value,
    @JsonProperty("type")  String type
) {
}
