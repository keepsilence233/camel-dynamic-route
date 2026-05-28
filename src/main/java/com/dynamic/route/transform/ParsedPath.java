package com.dynamic.route.transform;

import java.util.List;

/**
 * 已解析的路径：前缀段列表（用于导航到父节点）+ 叶字段名。
 * 例如 "orderLines[*].subItems[*].subItemId" → prefix=[orderLines[*], subItems[*]], leaf="subItemId"
 */
public record ParsedPath(List<PathSegment> prefix, String leaf) {

    /** 前缀中是否含有数组通配符（影响跨路径移动的合法性检查） */
    public boolean hasWildcardsInPrefix() {
        return prefix.stream().anyMatch(PathSegment::arrayWildcard);
    }
}
