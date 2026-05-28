package com.dynamic.route.repository;

import com.dynamic.route.model.AppDefinition;
import com.dynamic.route.model.FailStrategy;
import com.dynamic.route.model.PathMatchType;
import com.dynamic.route.model.PluginDefinition;
import com.dynamic.route.model.PluginExecutionPhase;
import com.dynamic.route.model.PluginPhase;
import com.dynamic.route.model.RouteDefinition;
import com.dynamic.route.model.RoutePluginBinding;
import com.dynamic.route.model.RouteTarget;
import com.dynamic.route.model.TargetType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class RouteConfigurationRepository {

    private final JdbcTemplate jdbcTemplate;

    public RouteConfigurationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<AppDefinition> findActiveApps() {
        return jdbcTemplate.query(
            """
                SELECT id, app_code, app_name, status, remark, created_at, updated_at
                FROM app_definition
                WHERE status = 'ACTIVE'
                """,
            appDefinitionRowMapper()
        );
    }

    public List<RouteTarget> findActiveTargets() {
        return jdbcTemplate.query(
            """
                SELECT id, target_code, target_name, target_type, endpoint_uri, component_name,
                       datasource_name, operation_type, config_json, secret_ref, status, version,
                       remark, created_at, updated_at
                FROM route_target
                WHERE status = 'ACTIVE'
                """,
            routeTargetRowMapper()
        );
    }

    public List<RouteDefinition> findActiveRoutes() {
        return jdbcTemplate.query(
            """
                SELECT id, route_code, app_code, route_name, entry_protocol, request_path,
                       path_match_type, request_method, request_format, content_type, accept_type,
                       route_order, target_code, timeout_ms, retry_times, status, version,
                       remark, created_by, updated_by, created_at, updated_at
                FROM route_definition
                WHERE status = 'ACTIVE'
                ORDER BY route_order ASC, id ASC
                """,
            routeDefinitionRowMapper()
        );
    }

    public List<PluginDefinition> findActivePlugins() {
        return jdbcTemplate.query(
            """
                SELECT id, plugin_code, plugin_name, plugin_phase, plugin_scope, bean_name,
                       plugin_class, config_schema_json, status, remark, created_at, updated_at
                FROM plugin_definition
                WHERE status = 'ACTIVE'
                """,
            pluginDefinitionRowMapper()
        );
    }

    public List<RoutePluginBinding> findEnabledBindings() {
        return jdbcTemplate.query(
            """
                SELECT id, route_code, plugin_code, plugin_phase, sort_order, enabled,
                       fail_strategy, plugin_config_json, created_at, updated_at
                FROM route_plugin_binding
                WHERE enabled = 1
                ORDER BY route_code ASC, plugin_phase ASC, sort_order ASC, id ASC
                """,
            routePluginBindingRowMapper()
        );
    }

    private RowMapper<AppDefinition> appDefinitionRowMapper() {
        return (resultSet, rowNum) -> new AppDefinition(
            resultSet.getLong("id"),
            resultSet.getString("app_code"),
            resultSet.getString("app_name"),
            resultSet.getString("status"),
            resultSet.getString("remark"),
            resultSet.getTimestamp("created_at").toInstant(),
            resultSet.getTimestamp("updated_at").toInstant()
        );
    }

    private RowMapper<RouteTarget> routeTargetRowMapper() {
        return (resultSet, rowNum) -> new RouteTarget(
            resultSet.getLong("id"),
            resultSet.getString("target_code"),
            resultSet.getString("target_name"),
            TargetType.fromValue(resultSet.getString("target_type")),
            resultSet.getString("endpoint_uri"),
            resultSet.getString("component_name"),
            resultSet.getString("datasource_name"),
            resultSet.getString("operation_type"),
            clobToString(resultSet, "config_json"),
            resultSet.getString("secret_ref"),
            resultSet.getString("status"),
            resultSet.getLong("version"),
            resultSet.getString("remark"),
            resultSet.getTimestamp("created_at").toInstant(),
            resultSet.getTimestamp("updated_at").toInstant()
        );
    }

    private RowMapper<RouteDefinition> routeDefinitionRowMapper() {
        return (resultSet, rowNum) -> new RouteDefinition(
            resultSet.getLong("id"),
            resultSet.getString("route_code"),
            resultSet.getString("app_code"),
            resultSet.getString("route_name"),
            resultSet.getString("entry_protocol"),
            resultSet.getString("request_path"),
            PathMatchType.fromValue(resultSet.getString("path_match_type")),
            resultSet.getString("request_method"),
            resultSet.getString("request_format"),
            resultSet.getString("content_type"),
            resultSet.getString("accept_type"),
            resultSet.getInt("route_order"),
            resultSet.getString("target_code"),
            resultSet.getObject("timeout_ms", Integer.class),
            resultSet.getInt("retry_times"),
            resultSet.getString("status"),
            resultSet.getLong("version"),
            resultSet.getString("remark"),
            resultSet.getString("created_by"),
            resultSet.getString("updated_by"),
            resultSet.getTimestamp("created_at").toInstant(),
            resultSet.getTimestamp("updated_at").toInstant()
        );
    }

    private RowMapper<PluginDefinition> pluginDefinitionRowMapper() {
        return (resultSet, rowNum) -> new PluginDefinition(
            resultSet.getLong("id"),
            resultSet.getString("plugin_code"),
            resultSet.getString("plugin_name"),
            PluginPhase.fromValue(resultSet.getString("plugin_phase")),
            resultSet.getString("plugin_scope"),
            resultSet.getString("bean_name"),
            resultSet.getString("plugin_class"),
            clobToString(resultSet, "config_schema_json"),
            resultSet.getString("status"),
            resultSet.getString("remark"),
            resultSet.getTimestamp("created_at").toInstant(),
            resultSet.getTimestamp("updated_at").toInstant()
        );
    }

    private RowMapper<RoutePluginBinding> routePluginBindingRowMapper() {
        return (resultSet, rowNum) -> new RoutePluginBinding(
            resultSet.getLong("id"),
            resultSet.getString("route_code"),
            resultSet.getString("plugin_code"),
            PluginExecutionPhase.fromValue(resultSet.getString("plugin_phase")),
            resultSet.getInt("sort_order"),
            resultSet.getBoolean("enabled"),
            FailStrategy.fromValue(resultSet.getString("fail_strategy")),
            clobToString(resultSet, "plugin_config_json"),
            resultSet.getTimestamp("created_at").toInstant(),
            resultSet.getTimestamp("updated_at").toInstant()
        );
    }

    private String clobToString(ResultSet resultSet, String columnName) throws SQLException {
        return resultSet.getString(columnName);
    }
}
