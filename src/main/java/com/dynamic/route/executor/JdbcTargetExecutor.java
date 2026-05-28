package com.dynamic.route.executor;

import com.dynamic.route.config.DataSourceRegistry;
import com.dynamic.route.engine.RouteContext;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Component;

@Component
public class JdbcTargetExecutor implements TargetExecutor {

    private static final Logger log = LoggerFactory.getLogger(JdbcTargetExecutor.class);

    private final DataSourceRegistry dataSourceRegistry;
    private final ObjectMapper objectMapper;

    public JdbcTargetExecutor(DataSourceRegistry dataSourceRegistry, ObjectMapper objectMapper) {
        this.dataSourceRegistry = dataSourceRegistry;
        this.objectMapper = objectMapper;
    }

    @Override
    public String supportType() {
        return "jdbc";
    }

    /**
     * 执行 SQL，按语句类型返回不同结构：
     *   SELECT → List&lt;Map&gt;（查询结果集）
     *   INSERT → {"affectedRows":N, "generatedKey":K}（有自增主键时含 generatedKey）
     *   UPDATE → {"affectedRows":N}
     *   DELETE → {"affectedRows":N}
     */
    @Override
    public Object execute(RouteContext context) {
        String datasourceName = context.routeTarget().datasourceName();
        String sql = context.routeTarget().configJson(); // SQL 模板（带 :param 命名参数）

        NamedParameterJdbcTemplate jdbcTemplate = dataSourceRegistry.require(datasourceName);
        String keyword = extractKeyword(sql);
        log.debug("[{}] → JDBC datasource={} type={} sql={}",
            context.traceId(),
            datasourceName == null || datasourceName.isBlank() ? "(primary)" : datasourceName,
            keyword, abbreviate(sql));

        Map<String, Object> parameters = resolveParameters(context.requestBody());
        return switch (keyword) {
            case "SELECT" -> jdbcTemplate.queryForList(sql, parameters);
            case "INSERT" -> executeInsert(jdbcTemplate, sql, parameters);
            case "UPDATE" -> executeUpdate(jdbcTemplate, sql, parameters);
            case "DELETE" -> executeDelete(jdbcTemplate, sql, parameters);
            default -> throw new IllegalArgumentException(
                "Unsupported SQL keyword: '" + keyword + "'. Only SELECT/INSERT/UPDATE/DELETE are allowed.");
        };
    }

    // -----------------------------------------------------------------------

    /**
     * INSERT：通过 GeneratedKeyHolder 捕获自增主键。
     * 返回：{"affectedRows":1, "generatedKey":42}  （单列主键）
     *        {"affectedRows":1, "generatedKeys":[{"ID":42}]}  （多列复合键时）
     *        {"affectedRows":1}  （无自增主键的表）
     */
    private Map<String, Object> executeInsert(NamedParameterJdbcTemplate tpl,
                                               String sql, Map<String, Object> params) {
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        int affected = tpl.update(sql, new MapSqlParameterSource(params), keyHolder);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("affectedRows", affected);

        List<Map<String, Object>> keyList = keyHolder.getKeyList();
        if (!keyList.isEmpty()) {
            if (keyList.size() == 1 && keyList.get(0).size() == 1) {
                // 最常见：单行单列自增键，直接拍平为 generatedKey
                result.put("generatedKey", keyList.get(0).values().iterator().next());
            } else {
                result.put("generatedKeys", keyList);
            }
        }
        return result;
    }

    /** UPDATE：返回 {"affectedRows":N} */
    private Map<String, Object> executeUpdate(NamedParameterJdbcTemplate tpl,
                                               String sql, Map<String, Object> params) {
        return Map.of("affectedRows", tpl.update(sql, params));
    }

    /** DELETE：返回 {"affectedRows":N} */
    private Map<String, Object> executeDelete(NamedParameterJdbcTemplate tpl,
                                               String sql, Map<String, Object> params) {
        return Map.of("affectedRows", tpl.update(sql, params));
    }

    // -----------------------------------------------------------------------

    /**
     * 提取 SQL 首个关键字（SELECT / INSERT / UPDATE / DELETE），
     * 用于分支到不同执行策略。
     */
    private String extractKeyword(String sql) {
        if (sql == null || sql.isBlank()) {
            return "OTHER";
        }
        String upper = sql.stripLeading().toUpperCase(Locale.ROOT);
        for (String kw : List.of("SELECT", "INSERT", "UPDATE", "DELETE")) {
            if (upper.startsWith(kw)) {
                return kw;
            }
        }
        return "OTHER";
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> resolveParameters(Object body) {
        if (body instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        if (body instanceof String jsonStr) {
            try {
                return objectMapper.readValue(jsonStr, new TypeReference<>() {});
            } catch (Exception e) {
                throw new IllegalArgumentException("JDBC body is not a valid JSON object", e);
            }
        }
        throw new IllegalArgumentException("JDBC request body must be a Map or a JSON string");
    }

    private String abbreviate(String s) {
        return (s != null && s.length() > 80) ? s.substring(0, 80) + "..." : s;
    }
}
