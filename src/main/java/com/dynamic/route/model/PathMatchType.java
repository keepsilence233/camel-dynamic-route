package com.dynamic.route.model;

public enum PathMatchType {
    EXACT,
    PREFIX,
    ANT,
    REGEX;

    public static PathMatchType fromValue(String value) {
        return PathMatchType.valueOf(value.trim().toUpperCase());
    }
}
