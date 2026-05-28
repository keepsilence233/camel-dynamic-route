package com.dynamic.route.camel;

import com.dynamic.route.config.RouteProperties;
import com.dynamic.route.engine.DynamicRouteEngine;
import com.dynamic.route.engine.RouteRequest;
import com.dynamic.route.engine.RouteResponse;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class DynamicRouterRouteBuilder extends RouteBuilder {

    private static final Logger log = LoggerFactory.getLogger(DynamicRouterRouteBuilder.class);

    private final RouteProperties routeProperties;
    private final DynamicRouteEngine dynamicRouteEngine;

    public DynamicRouterRouteBuilder(RouteProperties routeProperties, DynamicRouteEngine dynamicRouteEngine) {
        this.routeProperties = routeProperties;
        this.dynamicRouteEngine = dynamicRouteEngine;
    }

    @Override
    public void configure() {
        from(nettyHttpUri())
            .routeId("dynamic-router-entry")
            // Camel Simple 表达式：在请求进入处理器之前打印方法 + 路径（最轻量的入口 trace）
            .log(LoggingLevel.INFO, log.getName(),
                 "→ [${header[X-App-Code]}] ${header.CamelHttpMethod} ${header.CamelHttpPath}")
            .process(this::handleRequest);
    }

    private String nettyHttpUri() {
        RouteProperties.Ingress ingress = routeProperties.ingress();
        // matchOnUriPrefix=true 使单条 Camel 路由能接管 basePath 下的所有子路径，否则只匹配精确路径
        return "%s://%s:%d%s?matchOnUriPrefix=true&httpMethodRestrict=GET,POST,PUT,DELETE,PATCH"
            .formatted(ingress.protocol(), ingress.host(), ingress.port(), ingress.path());
    }

    private void handleRequest(Exchange exchange) {
        Map<String, Object> headers = new LinkedHashMap<>(exchange.getIn().getHeaders());
        String requestPath = firstNonBlank(
            exchange.getIn().getHeader(Exchange.HTTP_PATH, String.class),
            exchange.getIn().getHeader(Exchange.HTTP_URI, String.class)
        );
        String method = exchange.getIn().getHeader(Exchange.HTTP_METHOD, String.class);
        String contentType = exchange.getIn().getHeader(Exchange.CONTENT_TYPE, String.class);
        String acceptType = exchange.getIn().getHeader("Accept", String.class);
        String appCode = exchange.getIn().getHeader("X-App-Code", String.class);
        Object body = exchange.getIn().getBody();

        long start = System.currentTimeMillis();
        RouteResponse response = dynamicRouteEngine.route(
            new RouteRequest(
                appCode,
                requestPath,
                method,
                inferFormat(contentType),
                contentType,
                acceptType,
                Map.copyOf(headers),
                body
            )
        );
        log.info("← [{}] {} {} success={} {}ms",
            response.traceId(), method, requestPath, response.success(), System.currentTimeMillis() - start);

        exchange.getMessage().setBody(response);
        exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, "application/json");
    }

    private String inferFormat(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return null;
        }
        String normalized = contentType.toLowerCase(Locale.ROOT);
        if (normalized.contains("json")) {
            return "json";
        }
        if (normalized.contains("xml")) {
            return "xml";
        }
        if (normalized.contains("form")) {
            return "form";
        }
        return normalized;
    }

    private String firstNonBlank(String first, String second) {
        // Netty 在 prefix 匹配时会填充 CamelHttpPath（仅路径），否则降级到 CamelHttpUri（含 query string）
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second;
    }
}
