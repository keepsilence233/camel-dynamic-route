package com.dynamic.route.transform;

/**
 * 字段类型转换器。支持将任意值强制转为指定基础类型。
 * 转换规则：先 toString()，再按目标类型解析。
 */
public final class FieldTypeConverter {

    private FieldTypeConverter() {}

    /**
     * 将 value 转换为 type 指定的类型。
     * type 为 null 或 value 为 null 时直接返回原值。
     *
     * 支持的 type 值：string, integer(int), long, double(float), boolean(bool), number
     */
    public static Object convert(Object value, String type) {
        if (type == null || value == null) {
            return value;
        }
        String s = value.toString().trim();
        return switch (type.trim().toLowerCase()) {
            case "string"          -> s;
            case "integer", "int"  -> Integer.parseInt(s);
            case "long"            -> Long.parseLong(s);
            case "double", "float" -> Double.parseDouble(s);
            case "boolean", "bool" -> Boolean.parseBoolean(s);
            case "number"          -> parseNumber(s);
            default                -> value;
        };
    }

    private static Number parseNumber(String s) {
        try { return Integer.parseInt(s); } catch (NumberFormatException ignored) {}
        try { return Long.parseLong(s); } catch (NumberFormatException ignored) {}
        try { return Double.parseDouble(s); } catch (NumberFormatException ignored) {}
        throw new IllegalArgumentException("Cannot parse as number: " + s);
    }
}
