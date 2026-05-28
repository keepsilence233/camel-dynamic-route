package com.dynamic.route.transform;

/**
 * 路径中的一段：field 是字段名，arrayWildcard=true 表示该字段是数组且需要展开所有元素。
 * 例如 "orderLines[*]" 解析为 PathSegment("orderLines", true)。
 */
public record PathSegment(String field, boolean arrayWildcard) {
}
