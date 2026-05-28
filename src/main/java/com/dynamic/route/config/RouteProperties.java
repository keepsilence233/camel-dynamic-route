package com.dynamic.route.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "route")
public record RouteProperties(Ingress ingress, Cache cache, Map<String, DatasourceConfig> datasources) {

    public RouteProperties {
        datasources = datasources != null ? Map.copyOf(datasources) : Map.of();
    }

    public record Ingress(
        @NotBlank String protocol,
        @NotBlank String host,
        @Min(1) int port,
        @NotBlank String path
    ) {
    }

    public record Cache(@Min(1000) long refreshMs) {
    }

    /**
     * 额外数据源配置，对应 route.datasources.{name}。
     * 配置后可在 route_target.datasource_name 中按名引用，不填则使用主数据库。
     */
    public record DatasourceConfig(
        @NotBlank String url,
        String username,
        String password,
        String driverClassName,
        Integer maximumPoolSize
    ) {
    }
}
