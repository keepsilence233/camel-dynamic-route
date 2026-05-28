package com.dynamic.route.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Static JSON utility backed by Spring's managed ObjectMapper.
 * Initialized at startup; safe to call from any runtime path.
 */
@Component
public class JsonUtils {

    private static ObjectMapper MAPPER;

    public JsonUtils(ObjectMapper objectMapper) {
        MAPPER = objectMapper;
    }

    public static String toJson(Object obj) {
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (Exception e) {
            throw new IllegalStateException("JSON serialization failed", e);
        }
    }

    public static Map<String, Object> parseMap(String json) {
        try {
            return MAPPER.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid JSON object: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> deepCopyMap(Map<String, Object> data) {
        try {
            byte[] bytes = MAPPER.writeValueAsBytes(data);
            return MAPPER.readValue(bytes, new TypeReference<>() {});
        } catch (Exception e) {
            throw new IllegalStateException("Deep copy failed", e);
        }
    }

    public static <T> T convertValue(Object value, Class<T> type) {
        return MAPPER.convertValue(value, type);
    }

    /** Parses a JSON string loosely; returns the original string if parsing fails. */
    public static Object parseLoose(String json) {
        if (json == null || json.isBlank()) return json;
        try {
            return MAPPER.readValue(json, Object.class);
        } catch (Exception ignored) {
            return json;
        }
    }
}
