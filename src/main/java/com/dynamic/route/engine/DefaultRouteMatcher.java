package com.dynamic.route.engine;

import com.dynamic.route.model.PathMatchType;
import com.dynamic.route.model.RouteDefinition;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

@Component
public class DefaultRouteMatcher implements RouteMatcher {

    private final AntPathMatcher antPathMatcher = new AntPathMatcher();

    @Override
    public Optional<RouteDefinition> match(RouteRequest request, RouteConfigurationSnapshot snapshot) {
        return snapshot.routes().stream()
            .filter(route -> route.appCode().equals(request.appCode()))
            .filter(route -> route.entryProtocol().equalsIgnoreCase("netty-http"))
            .filter(route -> matchesPath(route, request.path()))
            .filter(route -> matchesNullable(route.requestMethod(), request.method()))
            .filter(route -> matchesNullable(route.requestFormat(), request.requestFormat()))
            .findFirst(); // 优先级由 DB 加载顺序决定：route_order ASC, id ASC，首个匹配即为最高优先级路由
    }

    private boolean matchesPath(RouteDefinition route, String path) {
        return switch (route.pathMatchType()) {
            case EXACT -> route.requestPath().equals(path);
            case PREFIX -> path.startsWith(route.requestPath());
            case ANT -> antPathMatcher.match(route.requestPath(), path);
            case REGEX -> path.matches(route.requestPath());
        };
    }

    private boolean matchesNullable(String expected, String actual) {
        if (expected == null || expected.isBlank()) {
            return true;
        }
        return expected.toLowerCase(Locale.ROOT).equals(normalize(actual));
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
