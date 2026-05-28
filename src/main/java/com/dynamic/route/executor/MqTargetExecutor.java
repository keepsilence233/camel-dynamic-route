package com.dynamic.route.executor;

import com.dynamic.route.engine.RouteContext;
import com.dynamic.route.util.JsonUtils;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Map;
import org.apache.camel.ProducerTemplate;
import org.springframework.stereotype.Component;

@Component
public class MqTargetExecutor implements TargetExecutor {

    private final ProducerTemplate producerTemplate;

    public MqTargetExecutor(ProducerTemplate producerTemplate) {
        this.producerTemplate = producerTemplate;
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
            return JsonUtils.toJson(body);
        }
        if (body instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        return body == null ? "" : body.toString();
    }
}
