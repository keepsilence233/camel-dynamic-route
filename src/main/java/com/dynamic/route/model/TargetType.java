package com.dynamic.route.model;

public enum TargetType {
    HTTP,
    MQ,
    JDBC;

    public static TargetType fromValue(String value) {
        return TargetType.valueOf(value.trim().toUpperCase());
    }
}
