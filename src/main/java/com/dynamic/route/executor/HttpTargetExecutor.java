package com.dynamic.route.executor;

import com.dynamic.route.engine.RouteContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Map;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class HttpTargetExecutor implements TargetExecutor {

    private static final Logger log = LoggerFactory.getLogger(HttpTargetExecutor.class);

    private final ProducerTemplate producerTemplate;
    private final ObjectMapper objectMapper;

    public HttpTargetExecutor(ProducerTemplate producerTemplate, ObjectMapper objectMapper) {
        this.producerTemplate = producerTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public String supportType() {
        return "http";
    }

    @Override
    public Object execute(RouteContext context) {
        // 1. 若 body 是 Map/List（由 json-to-json 等插件输出），序列化为 JSON String
        String body = serialize(context.requestBody());

        // 2. 按 body 内容推断 Content-Type，确保后端能正确解析
        //    json-to-xml 转换后 body 以 < 开头 → application/xml
        //    json-to-json 转换后 body 以 { 开头 → application/json
        String contentType = detectContentType(body);
        log.debug("[{}] → HTTP {} content-type={}", context.traceId(), context.routeTarget().endpointUri(), contentType);
        Map<String, Object> headers = Map.of(Exchange.CONTENT_TYPE, contentType);

        Object response = producerTemplate.requestBodyAndHeaders(
            context.routeTarget().endpointUri(), body, headers);

        // 3. Camel HTTP 组件有时返回 byte[]，统一转为 String 供后续 POST 插件使用
        if (response instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        return response;
    }

    private String serialize(Object body) {
        if (body instanceof Map<?, ?> || body instanceof Collection<?>) {
            try {
                return objectMapper.writeValueAsString(body);
            } catch (Exception e) {
                throw new IllegalStateException("HttpTargetExecutor: failed to serialize body", e);
            }
        }
        if (body instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        return body == null ? "" : body.toString();
    }

    private String detectContentType(String body) {
        if (body == null || body.isBlank()) {
            return "application/octet-stream";
        }
        String t = body.stripLeading();
        if (t.startsWith("<")) {
            return "application/xml";
        }
        if (t.startsWith("{") || t.startsWith("[")) {
            return "application/json";
        }
        return "text/plain";
    }
}
