package com.dynamic.route.config;

import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * 命名数据源注册表。持有「空字符串 key → 主数据源」以及所有在 route.datasources 中声明的额外数据源。
 * 实例由 {@link DatasourceConfiguration} 作为 @Bean 创建，不是 @Component。
 */
public class DataSourceRegistry {

    /** key="" 代表主数据源（datasourceName 为 null/空时的兜底） */
    private final Map<String, NamedParameterJdbcTemplate> templates;

    public DataSourceRegistry(Map<String, NamedParameterJdbcTemplate> templates) {
        this.templates = templates;
    }

    /**
     * 按 datasourceName 获取对应 JdbcTemplate。
     * datasourceName 为 null/空 → 返回主数据源。
     *
     * @throws IllegalStateException 名字存在但未在 route.datasources 中配置
     */
    public NamedParameterJdbcTemplate require(String datasourceName) {
        String key = (datasourceName == null || datasourceName.isBlank()) ? "" : datasourceName.trim();
        NamedParameterJdbcTemplate template = templates.get(key);
        if (template == null) {
            throw new IllegalStateException(
                "DataSource not configured: '" + datasourceName + "'. " +
                "Add it under route.datasources in application.yml. Available: " + availableNames());
        }
        return template;
    }

    /** 所有已注册数据源名称（"" 代表主数据源） */
    public Set<String> availableNames() {
        return templates.keySet();
    }
}
