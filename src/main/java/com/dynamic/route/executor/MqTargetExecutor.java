package com.dynamic.route.executor;

import com.dynamic.route.engine.RouteContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Map;
import org.apache.camel.ProducerTemplate;
import org.springframework.stereotype.Component;

@Component
public class MqTargetExecutor implements TargetExecutor {

    private final ProducerTemplate producerTemplate;
    private final ObjectMapper objectMapper;

    public MqTargetExecutor(ProducerTemplate producerTemplate, ObjectMapper objectMapper) {
        this.producerTemplate = producerTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public String supportType() {
        return "mq";
    }

    @Override
    public Object execute(RouteContext context) {
        String body = serialize(context.requestBody());
        Object response = producerTemplate.requestBody(context.routeTarget().endpointUri(), body);
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
                throw new IllegalStateException("MqTargetExecutor: failed to serialize body", e);
            }
        }
        if (body instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        return body == null ? "" : body.toString();
    }
}
