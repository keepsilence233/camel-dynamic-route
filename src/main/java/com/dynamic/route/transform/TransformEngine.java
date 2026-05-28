package com.dynamic.route.transform;

import com.dynamic.route.util.JsonUtils;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 转换引擎：将 TransformTemplate 应用到结构化数据（Map）上。
 *
 * 执行顺序：mappings → addFields → removeFields
 * 输入 Map 不会被修改；引擎内部先深拷贝再处理。
 */
@Component
public class TransformEngine {

    private final GroovyScriptExecutor scriptExecutor;

    public TransformEngine(GroovyScriptExecutor scriptExecutor) {
        this.scriptExecutor = scriptExecutor;
    }

    /** 将 config Map 转换为 TransformTemplate 对象 */
    public TransformTemplate parseTemplate(Map<String, Object> config) {
        try {
            return JsonUtils.convertValue(config, TransformTemplate.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid transform template config", e);
        }
    }

    /**
     * 执行转换，返回变换后的新 Map。
     * 若 template 中所有列表均为空，则直接返回深拷贝（等价于透传）。
     */
    public Map<String, Object> apply(Map<String, Object> data, TransformTemplate template) {
        Map<String, Object> result = deepCopy(data);

        for (TransformMapping mapping : template.mappings()) {
            applyMapping(result, mapping);
        }
        for (AddFieldSpec spec : template.addFields()) {
            applyAddField(result, spec);
        }
        for (String removePath : template.removeFields()) {
            applyRemoveField(result, removePath);
        }

        return result;
    }

    // -----------------------------------------------------------------------
    // mapping：重命名字段 + 类型转换 + 脚本
    // -----------------------------------------------------------------------

    private void applyMapping(Map<String, Object> root, TransformMapping mapping) {
        ParsedPath from = PathParser.splitLeaf(mapping.from());
        ParsedPath to   = PathParser.splitLeaf(mapping.to());

        List<Map<String, Object>> fromParents = StructuralNavigator.navigateToParents(root, from.prefix());

        for (Map<String, Object> fromParent : fromParents) {
            boolean fieldExists = fromParent.containsKey(from.leaf());
            if (!fieldExists && mapping.defaultValue() == null) {
                continue;
            }

            Object value = fieldExists ? fromParent.get(from.leaf()) : null;
            if (value == null) {
                value = mapping.defaultValue();
            }

            value = FieldTypeConverter.convert(value, mapping.type());
            value = scriptExecutor.maybeExecute(mapping.script(), value, fromParent);

            Map<String, Object> toParent = resolveToParent(root, from, to, fromParent);

            // 如果 from 和 to 不是同一字段/位置，先移除旧字段
            if (!from.leaf().equals(to.leaf()) || fromParent != toParent) {
                fromParent.remove(from.leaf());
            }

            toParent.put(to.leaf(), value);
        }
    }

    /**
     * 确定目标父节点：
     *   - 同前缀 → 就是 fromParent 本身（最常见的原地重命名）
     *   - 不同前缀且无通配符 → 从根导航（按需创建中间节点），支持对象层级移动
     *   - 不同前缀且有通配符 → 无法建立 1:1 对应，抛出异常（请改用 Groovy 脚本）
     */
    private Map<String, Object> resolveToParent(
        Map<String, Object> root, ParsedPath from, ParsedPath to, Map<String, Object> fromParent
    ) {
        if (from.prefix().equals(to.prefix())) {
            return fromParent;
        }
        if (to.hasWildcardsInPrefix()) {
            throw new IllegalArgumentException(
                "Cross-array-boundary field move is not supported: "
                + "from=[" + from.prefix() + "]." + from.leaf()
                + " → to=[" + to.prefix() + "]." + to.leaf()
                + ". Use a Groovy script for complex structural moves.");
        }
        return StructuralNavigator.navigateOrCreate(root, to.prefix());
    }

    // -----------------------------------------------------------------------
    // addField：在目标路径写入固定值
    // -----------------------------------------------------------------------

    private void applyAddField(Map<String, Object> root, AddFieldSpec spec) {
        ParsedPath path = PathParser.splitLeaf(spec.path());
        Object value = FieldTypeConverter.convert(spec.value(), spec.type());
        List<Map<String, Object>> parents = StructuralNavigator.navigateToParents(root, path.prefix());
        if (parents.isEmpty() && !path.hasWildcardsInPrefix()) {
            // 无通配符且父节点不存在：自动创建
            Map<String, Object> parent = StructuralNavigator.navigateOrCreate(root, path.prefix());
            parent.put(path.leaf(), value);
        } else {
            for (Map<String, Object> parent : parents) {
                parent.put(path.leaf(), value);
            }
        }
    }

    // -----------------------------------------------------------------------
    // removeField：从目标路径删除字段
    // -----------------------------------------------------------------------

    private void applyRemoveField(Map<String, Object> root, String removePath) {
        ParsedPath path = PathParser.splitLeaf(removePath);
        List<Map<String, Object>> parents = StructuralNavigator.navigateToParents(root, path.prefix());
        for (Map<String, Object> parent : parents) {
            parent.remove(path.leaf());
        }
    }

    // -----------------------------------------------------------------------
    // 深拷贝（JSON 序列化/反序列化，确保 Map/List 全部独立）
    // -----------------------------------------------------------------------

    private Map<String, Object> deepCopy(Map<String, Object> data) {
        return JsonUtils.deepCopyMap(data);
    }
}
