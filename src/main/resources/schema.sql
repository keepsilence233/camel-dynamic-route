CREATE TABLE IF NOT EXISTS app_definition (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    app_code VARCHAR(64) NOT NULL,
    app_name VARCHAR(128) NOT NULL,
    status VARCHAR(16) NOT NULL,
    remark VARCHAR(500),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_app_definition_code UNIQUE (app_code)
);

CREATE TABLE IF NOT EXISTS route_target (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    target_code VARCHAR(64) NOT NULL,
    target_name VARCHAR(128),
    target_type VARCHAR(32) NOT NULL,
    endpoint_uri VARCHAR(512),
    component_name VARCHAR(64),
    datasource_name VARCHAR(64),
    operation_type VARCHAR(32),
    config_json CLOB NOT NULL,
    secret_ref VARCHAR(128),
    status VARCHAR(16) NOT NULL,
    version BIGINT NOT NULL,
    remark VARCHAR(500),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_route_target_code UNIQUE (target_code)
);

CREATE TABLE IF NOT EXISTS route_definition (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    route_code VARCHAR(64) NOT NULL,
    app_code VARCHAR(64) NOT NULL,
    route_name VARCHAR(128) NOT NULL,
    entry_protocol VARCHAR(32) NOT NULL,
    request_path VARCHAR(256) NOT NULL,
    path_match_type VARCHAR(32) NOT NULL,
    request_method VARCHAR(16),
    request_format VARCHAR(32),
    content_type VARCHAR(128),
    accept_type VARCHAR(128),
    route_order INT NOT NULL,
    target_code VARCHAR(64) NOT NULL,
    timeout_ms INT,
    retry_times INT NOT NULL,
    status VARCHAR(16) NOT NULL,
    version BIGINT NOT NULL,
    remark VARCHAR(500),
    created_by VARCHAR(64),
    updated_by VARCHAR(64),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_route_definition_code UNIQUE (route_code),
    CONSTRAINT fk_route_definition_app_code FOREIGN KEY (app_code) REFERENCES app_definition(app_code),
    CONSTRAINT fk_route_definition_target_code FOREIGN KEY (target_code) REFERENCES route_target(target_code)
);

CREATE TABLE IF NOT EXISTS plugin_definition (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    plugin_code VARCHAR(64) NOT NULL,
    plugin_name VARCHAR(128) NOT NULL,
    plugin_phase VARCHAR(32) NOT NULL,
    plugin_scope VARCHAR(32) NOT NULL,
    bean_name VARCHAR(128) NOT NULL,
    plugin_class VARCHAR(256),
    config_schema_json CLOB,
    status VARCHAR(16) NOT NULL,
    remark VARCHAR(500),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_plugin_definition_code UNIQUE (plugin_code)
);

CREATE TABLE IF NOT EXISTS route_plugin_binding (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    route_code VARCHAR(64) NOT NULL,
    plugin_code VARCHAR(64) NOT NULL,
    plugin_phase VARCHAR(32) NOT NULL,
    sort_order INT NOT NULL,
    enabled TINYINT NOT NULL,
    fail_strategy VARCHAR(32) NOT NULL,
    plugin_config_json CLOB,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_route_plugin_binding UNIQUE (route_code, plugin_code, plugin_phase, sort_order),
    CONSTRAINT fk_route_plugin_binding_route_code FOREIGN KEY (route_code) REFERENCES route_definition(route_code),
    CONSTRAINT fk_route_plugin_binding_plugin_code FOREIGN KEY (plugin_code) REFERENCES plugin_definition(plugin_code)
);

CREATE INDEX IF NOT EXISTS idx_route_definition_match
    ON route_definition(app_code, request_path, request_method, status);

CREATE INDEX IF NOT EXISTS idx_route_definition_order
    ON route_definition(app_code, status, route_order);

CREATE INDEX IF NOT EXISTS idx_route_target_type_status
    ON route_target(target_type, status);

CREATE INDEX IF NOT EXISTS idx_plugin_definition_phase_status
    ON plugin_definition(plugin_phase, status);

CREATE INDEX IF NOT EXISTS idx_route_plugin_binding_route_phase_enabled
    ON route_plugin_binding(route_code, plugin_phase, enabled);
