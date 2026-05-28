-- =============================================================================
-- camel-dynamic-route 数据库 Schema
-- 5 张表描述一套「应用 → 路由 → 目标 → 插件」的动态路由配置体系。
-- 引擎启动时全量加载并缓存，之后按 refresh-ms 周期热刷新，无需重启。
-- =============================================================================


-- -----------------------------------------------------------------------------
-- 1. app_definition  应用注册表
--    每个接入方（上游系统）注册一条记录。
--    客户端请求时通过 HTTP Header「X-App-Code」传入 app_code，
--    引擎据此隔离不同应用的路由命名空间，防止跨应用路由误匹配。
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS app_definition (
    id          BIGINT       PRIMARY KEY AUTO_INCREMENT,
    app_code    VARCHAR(64)  NOT NULL,                  -- 应用唯一标识，全局不重复
    app_name    VARCHAR(128) NOT NULL,                  -- 可读名称
    status      VARCHAR(16)  NOT NULL,                  -- ACTIVE / INACTIVE
    remark      VARCHAR(500),
    created_at  TIMESTAMP    NOT NULL,
    updated_at  TIMESTAMP    NOT NULL,
    CONSTRAINT uk_app_definition_code UNIQUE (app_code)
);


-- -----------------------------------------------------------------------------
-- 2. route_target  路由目标表
--    描述请求最终要发往哪里、用什么协议。
--    target_type 决定使用哪个 TargetExecutor：
--      HTTP  → HttpTargetExecutor（Camel ProducerTemplate 转发）
--      MQ    → MqTargetExecutor（Camel ProducerTemplate 发消息）
--      JDBC  → JdbcTargetExecutor（NamedParameterJdbcTemplate 执行 SQL）
--
--    字段与 target_type 的对应关系：
--      endpoint_uri    HTTP/MQ 目标地址（Camel URI 格式）
--      datasource_name JDBC 数据源名称（对应 route.datasources.{name}，空则用主库）
--      config_json     HTTP/MQ: 执行器附加配置；JDBC: 带命名参数的 SQL 模板
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS route_target (
    id               BIGINT       PRIMARY KEY AUTO_INCREMENT,
    target_code      VARCHAR(64)  NOT NULL,             -- 目标唯一标识
    target_name      VARCHAR(128),                      -- 可读名称
    target_type      VARCHAR(32)  NOT NULL,             -- HTTP / MQ / JDBC
    endpoint_uri     VARCHAR(512),                      -- HTTP/MQ: Camel 目标 URI
    component_name   VARCHAR(64),                       -- Camel 组件名（如 http、activemq）
    datasource_name  VARCHAR(64),                       -- JDBC 专用：数据源名，空=主库
    operation_type   VARCHAR(32),                       -- 操作语义标注（forward/query/insert 等，仅作元数据）
    config_json      CLOB         NOT NULL,             -- JDBC: SQL 模板；HTTP/MQ: 附加配置 JSON
    secret_ref       VARCHAR(128),                      -- 凭证引用（预留，如 Vault secret path）
    status           VARCHAR(16)  NOT NULL,             -- ACTIVE / INACTIVE
    version          BIGINT       NOT NULL,             -- 乐观锁版本号
    remark           VARCHAR(500),
    created_at       TIMESTAMP    NOT NULL,
    updated_at       TIMESTAMP    NOT NULL,
    CONSTRAINT uk_route_target_code UNIQUE (target_code)
);


-- -----------------------------------------------------------------------------
-- 3. route_definition  路由规则表
--    核心路由配置，描述「什么样的请求」→「发到哪个 target」。
--    引擎按 app_code + entry_protocol + path + method + format 依次过滤，
--    同一 app_code 下按 (route_order ASC, id ASC) 取第一条命中记录。
--
--    path_match_type 说明：
--      EXACT  精确匹配 request_path
--      PREFIX 前缀匹配（path.startsWith）
--      ANT    Ant 风格通配符（Spring AntPathMatcher）
--      REGEX  正则表达式（path.matches）
--
--    request_method / request_format 为空时表示匹配所有（通配）。
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS route_definition (
    id              BIGINT       PRIMARY KEY AUTO_INCREMENT,
    route_code      VARCHAR(64)  NOT NULL,              -- 路由唯一标识
    app_code        VARCHAR(64)  NOT NULL,              -- 所属应用 → FK app_definition
    route_name      VARCHAR(128) NOT NULL,              -- 可读名称
    entry_protocol  VARCHAR(32)  NOT NULL,              -- 入口协议，当前仅支持 netty-http
    request_path    VARCHAR(256) NOT NULL,              -- 匹配路径，格式由 path_match_type 决定
    path_match_type VARCHAR(32)  NOT NULL,              -- EXACT / PREFIX / ANT / REGEX
    request_method  VARCHAR(16),                        -- GET/POST/PUT/DELETE/PATCH，空=全匹配
    request_format  VARCHAR(32),                        -- json/xml/form，空=全匹配
    content_type    VARCHAR(128),                       -- 期望的请求 Content-Type（元数据）
    accept_type     VARCHAR(128),                       -- 期望的响应 Accept（元数据）
    route_order     INT          NOT NULL,              -- 优先级，越小越先匹配
    target_code     VARCHAR(64)  NOT NULL,              -- 目标 → FK route_target
    timeout_ms      INT,                                -- 超时毫秒（预留，引擎暂未应用）
    retry_times     INT          NOT NULL,              -- 重试次数（预留，引擎暂未应用）
    status          VARCHAR(16)  NOT NULL,              -- ACTIVE / INACTIVE
    version         BIGINT       NOT NULL,              -- 乐观锁版本号
    remark          VARCHAR(500),
    created_by      VARCHAR(64),
    updated_by      VARCHAR(64),
    created_at      TIMESTAMP    NOT NULL,
    updated_at      TIMESTAMP    NOT NULL,
    CONSTRAINT uk_route_definition_code UNIQUE (route_code),
    CONSTRAINT fk_route_definition_app_code    FOREIGN KEY (app_code)    REFERENCES app_definition(app_code),
    CONSTRAINT fk_route_definition_target_code FOREIGN KEY (target_code) REFERENCES route_target(target_code)
);


-- -----------------------------------------------------------------------------
-- 4. plugin_definition  插件注册表
--    描述系统中存在哪些可用插件及其元信息。
--    引擎通过 plugin_code 在 PluginRegistry 中查找对应的 Spring Bean
--    （Bean 实现 RoutePlugin 接口，pluginCode() 返回值须与此表 plugin_code 一致）。
--
--    plugin_phase（PluginPhase 枚举）是插件的能力声明，用于文档/管控：
--      PRE_TRANSFORM / PRE_ENCRYPT / PRE  → 设计用于请求处理阶段
--      POST_TRANSFORM / POST              → 设计用于响应处理阶段
--    注意：实际执行时机由 route_plugin_binding.plugin_phase（PRE/POST）决定，
--    与此字段无强绑定，plugin_phase 仅作元数据。
--
--    config_schema_json 存放该插件配置项的 JSON Schema，
--    供管理后台渲染动态表单使用，引擎不做校验。
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS plugin_definition (
    id                BIGINT       PRIMARY KEY AUTO_INCREMENT,
    plugin_code       VARCHAR(64)  NOT NULL,            -- 插件唯一标识，与 RoutePlugin.pluginCode() 对应
    plugin_name       VARCHAR(128) NOT NULL,            -- 可读名称
    plugin_phase      VARCHAR(32)  NOT NULL,            -- 能力声明：PRE_TRANSFORM/POST_TRANSFORM 等
    plugin_scope      VARCHAR(32)  NOT NULL,            -- 作用域（route/global，当前仅 route）
    bean_name         VARCHAR(128) NOT NULL,            -- Spring Bean 名（元数据，引擎按 plugin_code 查找）
    plugin_class      VARCHAR(256),                     -- 全限定类名（元数据）
    config_schema_json CLOB,                            -- 插件配置项 JSON Schema（管理后台使用）
    status            VARCHAR(16)  NOT NULL,            -- ACTIVE / INACTIVE
    remark            VARCHAR(500),
    created_at        TIMESTAMP    NOT NULL,
    updated_at        TIMESTAMP    NOT NULL,
    CONSTRAINT uk_plugin_definition_code UNIQUE (plugin_code)
);


-- -----------------------------------------------------------------------------
-- 5. route_plugin_binding  路由-插件绑定表
--    将插件挂载到具体路由上，形成 PRE/POST 两条执行链。
--    同一路由、同一 phase 可绑定多个插件，按 sort_order ASC 顺序依次执行。
--
--    plugin_phase（PluginExecutionPhase 枚举）决定实际执行时机：
--      PRE  → 在调用 target 之前执行（处理请求体/请求头）
--      POST → 在调用 target 之后执行（处理响应体）
--
--    fail_strategy 决定插件异常时的行为：
--      FAIL_FAST → 中断整条链路，返回 500
--      CONTINUE  → 跳过该插件，以未修改的上下文继续后续执行
--
--    plugin_config_json 是本次绑定的运行时配置，传给 PluginInvocation.config()，
--    同一插件在不同路由上可以有不同的配置（如不同的字段映射规则）。
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS route_plugin_binding (
    id                BIGINT      PRIMARY KEY AUTO_INCREMENT,
    route_code        VARCHAR(64) NOT NULL,             -- 绑定的路由 → FK route_definition
    plugin_code       VARCHAR(64) NOT NULL,             -- 使用的插件 → FK plugin_definition
    plugin_phase      VARCHAR(32) NOT NULL,             -- 执行阶段：PRE / POST
    sort_order        INT         NOT NULL,             -- 同 phase 内执行顺序，越小越先
    enabled           TINYINT     NOT NULL,             -- 1=启用 / 0=禁用（不删除只禁用）
    fail_strategy     VARCHAR(32) NOT NULL,             -- FAIL_FAST / CONTINUE
    plugin_config_json CLOB,                            -- 本次绑定的插件实例配置（TransformTemplate JSON 等）
    created_at        TIMESTAMP   NOT NULL,
    updated_at        TIMESTAMP   NOT NULL,
    CONSTRAINT uk_route_plugin_binding       UNIQUE (route_code, plugin_code, plugin_phase, sort_order),
    CONSTRAINT fk_route_plugin_binding_route  FOREIGN KEY (route_code)  REFERENCES route_definition(route_code),
    CONSTRAINT fk_route_plugin_binding_plugin FOREIGN KEY (plugin_code) REFERENCES plugin_definition(plugin_code)
);


-- =============================================================================
-- 索引
-- =============================================================================

-- 路由匹配主索引：引擎按 app_code + path + method + status 过滤
CREATE INDEX IF NOT EXISTS idx_route_definition_match
    ON route_definition(app_code, request_path, request_method, status);

-- 路由排序索引：同 app_code 下按 route_order 取优先级最高的命中路由
CREATE INDEX IF NOT EXISTS idx_route_definition_order
    ON route_definition(app_code, status, route_order);

-- 目标查询索引：按类型批量加载 target（缓存刷新时使用）
CREATE INDEX IF NOT EXISTS idx_route_target_type_status
    ON route_target(target_type, status);

-- 插件查询索引：按 phase 批量加载活跃插件
CREATE INDEX IF NOT EXISTS idx_plugin_definition_phase_status
    ON plugin_definition(plugin_phase, status);

-- 绑定查询索引：按路由 + phase + enabled 快速拉取插件链
CREATE INDEX IF NOT EXISTS idx_route_plugin_binding_route_phase_enabled
    ON route_plugin_binding(route_code, plugin_phase, enabled);
