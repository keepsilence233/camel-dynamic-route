-- 种子数据，INSERT IGNORE 保证 MySQL 重启不报主键/唯一键冲突

-- 应用
INSERT IGNORE INTO app_definition (app_code, app_name, status, remark, created_at, updated_at)
VALUES ('demo-app', 'Demo App', 'ACTIVE', '演示应用', NOW(), NOW());

-- 目标：HTTP 透传
INSERT IGNORE INTO route_target (
    target_code, target_name, target_type, endpoint_uri, component_name, datasource_name,
    operation_type, config_json, secret_ref, status, version, remark, created_at, updated_at
) VALUES (
    'demo-http-target', 'Demo HTTP Target', 'HTTP', 'http://localhost:18080/mock', 'http', NULL,
    'forward', '{}', NULL, 'ACTIVE', 1, '演示 HTTP 目标', NOW(), NOW()
);

-- 插件定义：内置插件（与 Spring Bean pluginCode() 一一对应）
INSERT IGNORE INTO plugin_definition (
    plugin_code, plugin_name, plugin_phase, plugin_scope, bean_name, plugin_class,
    config_schema_json, status, remark, created_at, updated_at
) VALUES
('noop-plugin',       'Noop 透传',       'PRE_TRANSFORM',  'route', 'noOpRoutePlugin',       'com.dynamic.route.engine.NoOpRoutePlugin',       NULL,                                                                                     'ACTIVE', '原样透传，不做任何修改', NOW(), NOW()),
('json-to-json',      'JSON 字段映射',   'PRE_TRANSFORM',  'route', 'jsonToJsonPlugin',      'com.dynamic.route.plugin.JsonToJsonPlugin',      '{"type":"object","properties":{"mappings":{"type":"array"},"addFields":{"type":"array"},"removeFields":{"type":"array"}}}', 'ACTIVE', '对 JSON 体做字段重命名/新增/删除，支持嵌套和数组', NOW(), NOW()),
('json-to-xml',       'JSON 转 XML',     'PRE_TRANSFORM',  'route', 'jsonToXmlPlugin',       'com.dynamic.route.plugin.JsonToXmlPlugin',       '{"type":"object","properties":{"rootElement":{"type":"string"}}}',                       'ACTIVE', '将 JSON 请求体转为 XML，用于对接只接受 XML 的后端', NOW(), NOW()),
('xml-to-json',       'XML 转 JSON',     'POST_TRANSFORM', 'route', 'xmlToJsonPlugin',       'com.dynamic.route.plugin.XmlToJsonPlugin',       '{"type":"object","properties":{"forceArrayElements":{"type":"array"}}}',                  'ACTIVE', '将 XML 响应体转为 JSON，用于统一响应格式', NOW(), NOW()),
('xml-to-xml','XML 字段映射',    'PRE_TRANSFORM',  'route', 'xmlToXmlPlugin',        'com.dynamic.route.plugin.XmlToXmlPlugin',        '{"type":"object","properties":{"rootElement":{"type":"string"},"mappings":{"type":"array"}}}', 'ACTIVE', '对 XML 体做顶层元素重命名', NOW(), NOW());

-- 路由：最简透传示例
INSERT IGNORE INTO route_definition (
    route_code, app_code, route_name, entry_protocol, request_path, path_match_type,
    request_method, request_format, content_type, accept_type, route_order, target_code,
    timeout_ms, retry_times, status, version, remark, created_by, updated_by, created_at, updated_at
) VALUES (
    'demo-route', 'demo-app', 'Demo 透传路由', 'netty-http', '/dynamic-router/demo', 'EXACT',
    'POST', 'json', 'application/json', 'application/json',
    1, 'demo-http-target', 3000, 0, 'ACTIVE', 1,
    '演示：直接透传到 mock 后端', 'system', 'system', NOW(), NOW()
);

-- 绑定：demo-route 使用 noop-plugin（PRE 阶段）
INSERT IGNORE INTO route_plugin_binding (
    route_code, plugin_code, plugin_phase, sort_order, enabled, fail_strategy,
    plugin_config_json, created_at, updated_at
) VALUES (
    'demo-route', 'noop-plugin', 'PRE', 1, 1, 'FAIL_FAST', NULL, NOW(), NOW()
);

-- 路由：JSON 字段映射示例（PRE: camelCase → snake_case）
INSERT IGNORE INTO route_target (
    target_code, target_name, target_type, endpoint_uri, component_name, datasource_name,
    operation_type, config_json, secret_ref, status, version, remark, created_at, updated_at
) VALUES (
    'demo-user-target', 'Demo User API', 'HTTP', 'http://localhost:18080/api/user', 'http', NULL,
    'forward', '{}', NULL, 'ACTIVE', 1, 'JSON 映射演示目标', NOW(), NOW()
);

INSERT IGNORE INTO route_definition (
    route_code, app_code, route_name, entry_protocol, request_path, path_match_type,
    request_method, request_format, content_type, accept_type, route_order, target_code,
    timeout_ms, retry_times, status, version, remark, created_by, updated_by, created_at, updated_at
) VALUES (
    'demo-json-mapping-route', 'demo-app', '用户接口 JSON 字段映射', 'netty-http',
    '/dynamic-router/user', 'EXACT', 'POST', 'json', 'application/json', 'application/json',
    2, 'demo-user-target', 3000, 0, 'ACTIVE', 1,
    'PRE 将 camelCase 字段重命名为 snake_case 并注入 source 标识',
    'system', 'system', NOW(), NOW()
);

INSERT IGNORE INTO route_plugin_binding (
    route_code, plugin_code, plugin_phase, sort_order, enabled, fail_strategy,
    plugin_config_json, created_at, updated_at
) VALUES (
    'demo-json-mapping-route', 'json-to-json', 'PRE', 1, 1, 'FAIL_FAST',
    '{"mappings":[{"from":"userId","to":"user_id"},{"from":"userName","to":"user_name"},{"from":"userEmail","to":"email"}],"addFields":[{"path":"source","value":"gateway"}],"removeFields":["internalFlag"]}',
    NOW(), NOW()
);

-- 路由：XML 协议适配示例（PRE: JSON→XML；POST: XML→JSON）
INSERT IGNORE INTO route_target (
    target_code, target_name, target_type, endpoint_uri, component_name, datasource_name,
    operation_type, config_json, secret_ref, status, version, remark, created_at, updated_at
) VALUES (
    'demo-xml-target', 'Demo XML Legacy', 'HTTP', 'http://localhost:18080/legacy/order', 'http', NULL,
    'forward', '{}', NULL, 'ACTIVE', 1, 'XML 协议适配演示目标', NOW(), NOW()
);

INSERT IGNORE INTO route_definition (
    route_code, app_code, route_name, entry_protocol, request_path, path_match_type,
    request_method, request_format, content_type, accept_type, route_order, target_code,
    timeout_ms, retry_times, status, version, remark, created_by, updated_by, created_at, updated_at
) VALUES (
    'demo-xml-adapt-route', 'demo-app', '订单接口 XML 协议适配', 'netty-http',
    '/dynamic-router/order', 'EXACT', 'POST', 'json', 'application/json', 'application/json',
    3, 'demo-xml-target', 5000, 0, 'ACTIVE', 1,
    'PRE: JSON→XML 发给 legacy 后端；POST: XML→JSON 返回客户端',
    'system', 'system', NOW(), NOW()
);

INSERT IGNORE INTO route_plugin_binding (
    route_code, plugin_code, plugin_phase, sort_order, enabled, fail_strategy,
    plugin_config_json, created_at, updated_at
) VALUES
('demo-xml-adapt-route', 'json-to-xml',  'PRE',  1, 1, 'FAIL_FAST', '{"rootElement":"order"}', NOW(), NOW()),
('demo-xml-adapt-route', 'xml-to-json',  'POST', 1, 1, 'CONTINUE',  NULL,                      NOW(), NOW());
