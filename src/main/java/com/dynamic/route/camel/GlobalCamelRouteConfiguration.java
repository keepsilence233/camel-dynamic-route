package com.dynamic.route.camel;

import org.apache.camel.builder.RouteConfigurationBuilder;
import org.springframework.stereotype.Component;

@Component
public class GlobalCamelRouteConfiguration extends RouteConfigurationBuilder {

    private final GlobalExceptionProcessor globalExceptionProcessor;

    public GlobalCamelRouteConfiguration(GlobalExceptionProcessor globalExceptionProcessor) {
        this.globalExceptionProcessor = globalExceptionProcessor;
    }

    @Override
    public void configuration() {
        // handled(true) 阻止 Camel 在 processor 执行后重新抛出异常；若缺少则格式化的错误响应体会被框架覆盖
        onException(Exception.class)
            .handled(true)
            .process(globalExceptionProcessor);
    }
}
