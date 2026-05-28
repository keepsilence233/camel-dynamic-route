package com.dynamic.route.model;

public enum FailStrategy {
    FAIL_FAST,
    CONTINUE;

    public static FailStrategy fromValue(String value) {
        return FailStrategy.valueOf(value.trim().toUpperCase().replace('-', '_'));
    }
}
