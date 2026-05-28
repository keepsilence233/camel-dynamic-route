INSERT INTO app_definition (app_code, app_name, status, remark, created_at, updated_at)
VALUES ('demo-app', 'Demo App', 'ACTIVE', 'seed app', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO route_target (
    target_code, target_name, target_type, endpoint_uri, component_name, datasource_name,
    operation_type, config_json, secret_ref, status, version, remark, created_at, updated_at
) VALUES (
    'demo-http-target', 'Demo HTTP Target', 'http', 'http://localhost:18080/mock', 'http', NULL,
    'forward', '{"method":"POST"}', NULL, 'ACTIVE', 1, 'seed target', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
);

INSERT INTO plugin_definition (
    plugin_code, plugin_name, plugin_phase, plugin_scope, bean_name, plugin_class,
    config_schema_json, status, remark, created_at, updated_at
) VALUES (
    'noop-plugin', 'Noop Plugin', 'PRE_TRANSFORM', 'route', 'noopRoutePlugin',
    'com.dynamic.route.engine.NoOpRoutePlugin', NULL, 'ACTIVE', 'seed plugin', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
);

INSERT INTO route_definition (
    route_code, app_code, route_name, entry_protocol, request_path, path_match_type,
    request_method, request_format, content_type, accept_type, route_order, target_code,
    timeout_ms, retry_times, status, version, remark, created_by, updated_by, created_at, updated_at
) VALUES (
    'demo-route', 'demo-app', 'Demo Route', 'netty-http', '/dynamic-router/demo', 'exact',
    'POST', 'json', 'application/json', 'application/json', 1, 'demo-http-target',
    3000, 0, 'ACTIVE', 1, 'seed route', 'system', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
);

INSERT INTO route_plugin_binding (
    route_code, plugin_code, plugin_phase, sort_order, enabled, fail_strategy,
    plugin_config_json, created_at, updated_at
) VALUES (
    'demo-route', 'noop-plugin', 'PRE', 1, 1, 'FAIL_FAST', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
);

-- ============================================================
-- 转换插件定义
-- ============================================================

INSERT INTO plugin_definition (
    plugin_code, plugin_name, plugin_phase, plugin_scope, bean_name, plugin_class,
    config_schema_json, status, remark, created_at, updated_at
) VALUES (
    'json-to-json-mapping', 'JSON 字段映射', 'PRE_TRANSFORM', 'route',
    'jsonToJsonMappingPlugin', 'com.dynamic.route.plugin.JsonToJsonMappingPlugin',
    '{"mappings":[{"from":"string","to":"string"}],"addFields":{},"removeFields":[]}',
    'ACTIVE', '对 JSON 体做字段重命名/新增/删除', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
);

INSERT INTO plugin_definition (
    plugin_code, plugin_name, plugin_phase, plugin_scope, bean_name, plugin_class,
    config_schema_json, status, remark, created_at, updated_at
) VALUES (
    'json-to-xml', 'JSON 转 XML', 'PRE_TRANSFORM', 'route',
    'jsonToXmlPlugin', 'com.dynamic.route.plugin.JsonToXmlPlugin',
    '{"rootElement":"root"}',
    'ACTIVE', '将 JSON 请求体转为 XML，用于对接只接受 XML 的后端', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
);

INSERT INTO plugin_definition (
    plugin_code, plugin_name, plugin_phase, plugin_scope, bean_name, plugin_class,
    config_schema_json, status, remark, created_at, updated_at
) VALUES (
    'xml-to-json', 'XML 转 JSON', 'POST_TRANSFORM', 'route',
    'xmlToJsonPlugin', 'com.dynamic.route.plugin.XmlToJsonPlugin',
    NULL,
    'ACTIVE', '将 XML 响应体转为 JSON，用于统一响应格式', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
);

INSERT INTO plugin_definition (
    plugin_code, plugin_name, plugin_phase, plugin_scope, bean_name, plugin_class,
    config_schema_json, status, remark, created_at, updated_at
) VALUES (
    'xml-to-xml-mapping', 'XML 字段映射', 'PRE_TRANSFORM', 'route',
    'xmlToXmlPlugin', 'com.dynamic.route.plugin.XmlToXmlPlugin',
    '{"rootElement":"root","mappings":[{"from":"string","to":"string"}]}',
    'ACTIVE', '对 XML 体做顶层元素重命名', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
);

-- ============================================================
-- 示例：JSON 字段映射路由（PRE 阶段将 camelCase → snake_case）
-- ============================================================

INSERT INTO route_target (
    target_code, target_name, target_type, endpoint_uri, component_name, datasource_name,
    operation_type, config_json, secret_ref, status, version, remark, created_at, updated_at
) VALUES (
    'demo-user-target', 'Demo User API Target', 'http', 'http://localhost:18080/api/user', 'http', NULL,
    'forward', '{}', NULL, 'ACTIVE', 1, 'json-to-json 演示目标', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
);

INSERT INTO route_definition (
    route_code, app_code, route_name, entry_protocol, request_path, path_match_type,
    request_method, request_format, content_type, accept_type, route_order, target_code,
    timeout_ms, retry_times, status, version, remark, created_by, updated_by, created_at, updated_at
) VALUES (
    'demo-json-mapping-route', 'demo-app', '用户接口 JSON 字段映射', 'netty-http',
    '/dynamic-router/user', 'exact', 'POST', 'json', 'application/json', 'application/json',
    2, 'demo-user-target', 3000, 0, 'ACTIVE', 1,
    'PRE 阶段将前端 camelCase 字段重命名为后端 snake_case，并注入 source 标识',
    'system', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
);

INSERT INTO route_plugin_binding (
    route_code, plugin_code, plugin_phase, sort_order, enabled, fail_strategy,
    plugin_config_json, created_at, updated_at
) VALUES (
    'demo-json-mapping-route', 'json-to-json-mapping', 'PRE', 1, 1, 'FAIL_FAST',
    '{"mappings":[{"from":"userId","to":"user_id"},{"from":"userName","to":"user_name"},{"from":"userEmail","to":"email"}],"addFields":{"source":"gateway"},"removeFields":["internalFlag"]}',
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
);

-- ============================================================
-- 示例：XML 协议适配路由（PRE: JSON→XML 发给后端；POST: XML→JSON 回给客户端）
-- ============================================================

INSERT INTO route_target (
    target_code, target_name, target_type, endpoint_uri, component_name, datasource_name,
    operation_type, config_json, secret_ref, status, version, remark, created_at, updated_at
) VALUES (
    'demo-xml-target', 'Demo XML Legacy Target', 'http', 'http://localhost:18080/legacy/order', 'http', NULL,
    'forward', '{}', NULL, 'ACTIVE', 1, 'xml 协议转换演示目标', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
);

INSERT INTO route_definition (
    route_code, app_code, route_name, entry_protocol, request_path, path_match_type,
    request_method, request_format, content_type, accept_type, route_order, target_code,
    timeout_ms, retry_times, status, version, remark, created_by, updated_by, created_at, updated_at
) VALUES (
    'demo-xml-adapt-route', 'demo-app', '订单接口 XML 协议适配', 'netty-http',
    '/dynamic-router/order', 'exact', 'POST', 'json', 'application/json', 'application/json',
    3, 'demo-xml-target', 5000, 0, 'ACTIVE', 1,
    'PRE: JSON→XML 发给 legacy 后端；POST: XML→JSON 返回给客户端',
    'system', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
);

INSERT INTO route_plugin_binding (
    route_code, plugin_code, plugin_phase, sort_order, enabled, fail_strategy,
    plugin_config_json, created_at, updated_at
) VALUES (
    'demo-xml-adapt-route', 'json-to-xml', 'PRE', 1, 1, 'FAIL_FAST',
    '{"rootElement":"order"}',
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
);

INSERT INTO route_plugin_binding (
    route_code, plugin_code, plugin_phase, sort_order, enabled, fail_strategy,
    plugin_config_json, created_at, updated_at
) VALUES (
    'demo-xml-adapt-route', 'xml-to-json', 'POST', 1, 1, 'CONTINUE',
    NULL,
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
);
