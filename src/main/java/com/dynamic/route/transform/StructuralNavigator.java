package com.dynamic.route.transform;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 嵌套 Map/List 结构的导航与变更工具。
 *
 * 所有方法均为静态，对传入的 Map 进行原地修改（调用方负责传入副本）。
 *
 * 核心方法：
 *   navigateToParents  — 按前缀路径收集所有匹配的父 Map（[*] 时展开数组）
 *   navigateOrCreate   — 无通配符路径，导航并按需创建中间节点
 */
public final class StructuralNavigator {

    private StructuralNavigator() {}

    /**
     * 按前缀路径收集所有匹配的父 Map。
     *
     * 示例（prefix = [orderLines[*], subItems[*]]）：
     *   root.orderLines 是数组 → 展开每个 orderLine
     *   每个 orderLine.subItems 是数组 → 再展开每个 subItem
     *   返回所有 subItem Map
     */
    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> navigateToParents(Object current, List<PathSegment> prefix) {
        if (prefix.isEmpty()) {
            if (current instanceof Map<?, ?> map) {
                return List.of((Map<String, Object>) map);
            }
            return List.of();
        }

        if (!(current instanceof Map<?, ?> map)) {
            return List.of();
        }
        Map<String, Object> typedMap = (Map<String, Object>) map;

        PathSegment head = prefix.get(0);
        List<PathSegment> rest = prefix.subList(1, prefix.size());
        Object fieldValue = typedMap.get(head.field());

        if (head.arrayWildcard()) {
            if (!(fieldValue instanceof List<?> list)) {
                return List.of();
            }
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object element : list) {
                result.addAll(navigateToParents(element, rest));
            }
            return result;
        } else {
            return navigateToParents(fieldValue, rest);
        }
    }

    /**
     * 无通配符路径：沿路径导航，途中的缺失节点自动创建为空 Map。
     * 用于「跨对象层级移动字段」的目标节点定位（如 userId → user.id）。
     *
     * @throws IllegalArgumentException 若前缀中含有 [*]
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> navigateOrCreate(Map<String, Object> root, List<PathSegment> prefix) {
        Map<String, Object> current = root;
        for (PathSegment segment : prefix) {
            if (segment.arrayWildcard()) {
                throw new IllegalArgumentException(
                    "navigateOrCreate does not support array wildcards: " + segment.field() + "[*]");
            }
            Object child = current.get(segment.field());
            if (child instanceof Map<?, ?> childMap) {
                current = (Map<String, Object>) childMap;
            } else {
                Map<String, Object> newNode = new LinkedHashMap<>();
                current.put(segment.field(), newNode);
                current = newNode;
            }
        }
        return current;
    }

    /**
     * 递归地将 Map/List 结构中所有名称在 forceSet 中的字段，
     * 若其当前值为 Map（即 XML 解析出的单元素），强制包装成 List。
     * 用于修正 XmlMapper 无法区分「单元素数组」与「对象」的问题。
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> fixSingletonArrays(Map<String, Object> data, List<String> forceSet) {
        if (forceSet.isEmpty()) {
            return data;
        }
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            String key = entry.getKey();
            Object val = entry.getValue();
            if (forceSet.contains(key) && val instanceof Map<?, ?> mapVal) {
                // 单元素：将 Map 包装成 List
                entry.setValue(new ArrayList<>(List.of((Map<String, Object>) mapVal)));
            } else if (val instanceof Map<?, ?> nested) {
                entry.setValue(fixSingletonArrays((Map<String, Object>) nested, forceSet));
            } else if (val instanceof List<?> list) {
                List<Object> fixed = new ArrayList<>();
                for (Object element : list) {
                    if (element instanceof Map<?, ?> elemMap) {
                        fixed.add(fixSingletonArrays((Map<String, Object>) elemMap, forceSet));
                    } else {
                        fixed.add(element);
                    }
                }
                entry.setValue(fixed);
            }
        }
        return data;
    }
}
