package com.dynamic.route.transform;

import java.util.ArrayList;
import java.util.List;

/**
 * 路径字符串解析器。
 *
 * 语法规则：
 *   path    = segment ('.' segment)*
 *   segment = fieldName ('[*]')?
 *
 * 示例：
 *   "orderId"                              → [{orderId, false}]
 *   "customer.userId"                      → [{customer, false}, {userId, false}]
 *   "orderLines[*].lineId"                 → [{orderLines, true}, {lineId, false}]
 *   "orderLines[*].subItems[*].subItemId"  → [{orderLines, true}, {subItems, true}, {subItemId, false}]
 */
public final class PathParser {

    private PathParser() {}

    public static List<PathSegment> parse(String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("Path must not be blank");
        }
        List<PathSegment> segments = new ArrayList<>();
        for (String part : path.split("\\.")) {
            if (part.endsWith("[*]")) {
                segments.add(new PathSegment(part.substring(0, part.length() - 3), true));
            } else {
                segments.add(new PathSegment(part, false));
            }
        }
        return segments;
    }

    /**
     * 将路径拆分为「前缀段列表 + 叶字段名」。
     * 叶字段（最后一段）不允许带 [*]，因为不能把一个数组整体作为修改目标。
     */
    public static ParsedPath splitLeaf(String path) {
        List<PathSegment> segments = parse(path);
        PathSegment last = segments.get(segments.size() - 1);
        if (last.arrayWildcard()) {
            throw new IllegalArgumentException(
                "Path leaf must not be an array wildcard (remove '[*]' from the last segment): " + path);
        }
        return new ParsedPath(
            List.copyOf(segments.subList(0, segments.size() - 1)),
            last.field()
        );
    }
}
