package com.dynamic.route.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * 多数据源配置。
 *
 * 启动时读取 route.datasources，为每个条目创建独立的 HikariCP 连接池，
 * 并将它们（连同主数据源）一起注册到 DataSourceRegistry。
 * Spring 容器关闭时自动释放所有额外连接池。
 *
 * 配置示例（application.yml）：
 * <pre>
 * route:
 *   datasources:
 *     crm-db:
 *       url: jdbc:mysql://crm-host:3306/crm_db
 *       username: crm_user
 *       password: crm_pass
 *       driver-class-name: com.mysql.cj.jdbc.Driver
 *       maximum-pool-size: 10
 *     erp-db:
 *       url: jdbc:postgresql://erp-host:5432/erp_db
 *       username: erp_user
 *       password: erp_pass
 * </pre>
 *
 * route_target 中的 datasource_name 填写上述 key（如 "crm-db"），
 * 不填则走 Spring Boot 自动配置的主数据源（spring.datasource）。
 */
@Configuration
public class DatasourceConfiguration implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(DatasourceConfiguration.class);

    private final List<HikariDataSource> managed = new ArrayList<>();

    @Bean
    public DataSourceRegistry dataSourceRegistry(RouteProperties routeProperties, DataSource primaryDataSource) {
        Map<String, NamedParameterJdbcTemplate> templates = new LinkedHashMap<>();
        // "" key = 主数据源，datasourceName 为 null/空时的兜底
        templates.put("", new NamedParameterJdbcTemplate(primaryDataSource));

        routeProperties.datasources().forEach((name, config) -> {
            HikariDataSource ds = buildDataSource(name, config);
            managed.add(ds);
            templates.put(name, new NamedParameterJdbcTemplate(ds));
            log.info("DataSource registered: name={} url={}", name, config.url());
        });

        log.info("DataSourceRegistry ready: primary + {} additional datasource(s)",
            routeProperties.datasources().size());
        return new DataSourceRegistry(Map.copyOf(templates));
    }

    private HikariDataSource buildDataSource(String name, RouteProperties.DatasourceConfig config) {
        HikariConfig hc = new HikariConfig();
        hc.setPoolName("route-hikari-" + name);
        hc.setJdbcUrl(config.url());
        hc.setUsername(config.username());
        hc.setPassword(config.password());
        if (config.driverClassName() != null && !config.driverClassName().isBlank()) {
            hc.setDriverClassName(config.driverClassName());
        }
        hc.setMaximumPoolSize(config.maximumPoolSize() != null ? config.maximumPoolSize() : 10);
        return new HikariDataSource(hc);
    }

    @Override
    public void destroy() {
        for (HikariDataSource ds : managed) {
            if (!ds.isClosed()) {
                log.info("Closing datasource pool: {}", ds.getPoolName());
                ds.close();
            }
        }
    }
}
