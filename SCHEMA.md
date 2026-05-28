# 数据库表结构说明

## ER 关系图

```
app_definition
    │ app_code (PK)
    │
    │ 1 : N
    ▼
route_definition ──────────────────────► route_target
    │ route_code (PK)                        target_code (PK)
    │ app_code (FK)                          target_type
    │ target_code (FK)                       endpoint_uri
    │ path / method / format                 datasource_name
    │ route_order                            config_json (SQL 模板)
    │
    │ 1 : N
    ▼
route_plugin_binding ──────────────────► plugin_definition
    route_code (FK)                          plugin_code (PK)
    plugin_code (FK)                         bean_name
    plugin_phase (PRE/POST)                  plugin_phase (能力声明)
    sort_order                               config_schema_json
    fail_strategy
    plugin_config_json (运行时配置)
```

---

## 表说明

### 1. `app_definition` — 应用注册表

注册接入方（上游系统），是所有路由的命名空间隔离基础。客户端每次请求须在 Header 中携带 `X-App-Code`，引擎据此将请求限定在对应应用的路由范围内。

| 字段 | 类型 | 说明 |
|---|---|---|
| `app_code` | VARCHAR(64) | 应用唯一标识，全局不重复，路由匹配的第一过滤条件 |
| `app_name` | VARCHAR(128) | 可读名称 |
| `status` | VARCHAR(16) | `ACTIVE` 启用 / `INACTIVE` 停用 |

---

### 2. `route_target` — 路由目标表

描述请求最终发往哪里、用什么协议执行。`target_type` 决定使用哪个 `TargetExecutor`，字段的有效含义随类型变化：

| target_type | endpoint_uri | datasource_name | config_json |
|---|---|---|---|
| `HTTP` | 后端 HTTP 地址（Camel URI） | 不使用 | 附加配置，如 `{}` |
| `MQ` | 消息队列地址（Camel URI） | 不使用 | 附加配置 |
| `JDBC` | 不使用 | 数据源名（空=主库） | **SQL 模板**（含 `:param` 命名参数） |

| 字段 | 类型 | 说明 |
|---|---|---|
| `target_code` | VARCHAR(64) | 目标唯一标识 |
| `target_type` | VARCHAR(32) | `HTTP` / `MQ` / `JDBC` |
| `endpoint_uri` | VARCHAR(512) | HTTP/MQ: Camel 组件 URI |
| `datasource_name` | VARCHAR(64) | JDBC: 对应 `route.datasources.{name}`，空则使用 `spring.datasource` 主库 |
| `config_json` | CLOB | JDBC: SQL 模板；HTTP/MQ: 执行器附加配置 |
| `secret_ref` | VARCHAR(128) | 凭证引用（预留，如 Vault path） |
| `version` | BIGINT | 乐观锁版本号 |

---

### 3. `route_definition` — 路由规则表

核心配置，描述「什么样的请求」应路由到「哪个 target」。引擎按以下顺序依次过滤，取第一条命中记录：

```
app_code → entry_protocol → path（按 path_match_type）→ method → format
```

同一 `app_code` 下有多条候选路由时，按 `(route_order ASC, id ASC)` 排序，`route_order` 越小优先级越高。

| 字段 | 类型 | 说明 |
|---|---|---|
| `route_code` | VARCHAR(64) | 路由唯一标识 |
| `app_code` | VARCHAR(64) | FK → `app_definition.app_code` |
| `entry_protocol` | VARCHAR(32) | 当前仅支持 `netty-http` |
| `request_path` | VARCHAR(256) | 匹配路径，格式由 `path_match_type` 决定 |
| `path_match_type` | VARCHAR(32) | `EXACT` / `PREFIX` / `ANT` / `REGEX` |
| `request_method` | VARCHAR(16) | `GET`/`POST` 等，**空 = 匹配所有方法** |
| `request_format` | VARCHAR(32) | `json`/`xml`/`form`，**空 = 匹配所有格式** |
| `route_order` | INT | 优先级，越小越先 |
| `target_code` | VARCHAR(64) | FK → `route_target.target_code` |
| `timeout_ms` | INT | 超时毫秒（预留字段，引擎暂未应用） |
| `retry_times` | INT | 重试次数（预留字段，引擎暂未应用） |
| `version` | BIGINT | 乐观锁版本号 |

**`path_match_type` 详解：**

| 值 | 匹配方式 | 示例 path | 匹配 `/api/user/123` |
|---|---|---|---|
| `EXACT` | 字符串完全相等 | `/api/user/123` | ✓ |
| `PREFIX` | `startsWith` | `/api/user` | ✓ |
| `ANT` | Ant 通配符 | `/api/user/**` | ✓ |
| `REGEX` | 正则表达式 | `/api/user/\d+` | ✓ |

---

### 4. `plugin_definition` — 插件注册表

描述系统中可用的插件及其元信息。引擎通过 `plugin_code` 在 `PluginRegistry` 中查找对应的 Spring Bean（Bean 需实现 `RoutePlugin` 接口，`pluginCode()` 返回值须与此表一致）。

| 字段 | 类型 | 说明 |
|---|---|---|
| `plugin_code` | VARCHAR(64) | 插件唯一标识，与 `RoutePlugin.pluginCode()` 对应 |
| `plugin_phase` | VARCHAR(32) | 插件**能力声明**（见下方说明），仅作元数据 |
| `plugin_scope` | VARCHAR(32) | 作用域，当前固定为 `route` |
| `bean_name` | VARCHAR(128) | Spring Bean 名（元数据，引擎实际按 `plugin_code` 查找） |
| `plugin_class` | VARCHAR(256) | 全限定类名（元数据） |
| `config_schema_json` | CLOB | 插件配置项的 JSON Schema，供管理后台渲染表单，引擎不校验 |

**`plugin_phase` 与 `route_plugin_binding.plugin_phase` 的区别：**

| | `plugin_definition.plugin_phase` | `route_plugin_binding.plugin_phase` |
|---|---|---|
| 枚举类型 | `PluginPhase`（细粒度） | `PluginExecutionPhase`（仅 PRE/POST） |
| 含义 | 插件的**能力声明**，描述其设计用途 | **实际执行时机**，决定何时调用 |
| 示例值 | `PRE_TRANSFORM`, `POST_TRANSFORM`, `PRE_ENCRYPT` | `PRE`, `POST` |
| 引擎使用 | 不参与路由逻辑，仅作元数据 | 直接决定执行时机 |

---

### 5. `route_plugin_binding` — 路由-插件绑定表

将插件挂载到路由上，形成执行链。同一路由、同一 `plugin_phase` 下可绑定多个插件，按 `sort_order ASC` 顺序执行。

| 字段 | 类型 | 说明 |
|---|---|---|
| `route_code` | VARCHAR(64) | FK → `route_definition.route_code` |
| `plugin_code` | VARCHAR(64) | FK → `plugin_definition.plugin_code` |
| `plugin_phase` | VARCHAR(32) | `PRE`（target 调用前）/ `POST`（target 调用后） |
| `sort_order` | INT | 同 phase 内执行顺序，越小越先 |
| `enabled` | TINYINT | `1` 启用 / `0` 禁用（软禁用，不删记录） |
| `fail_strategy` | VARCHAR(32) | `FAIL_FAST` 异常时中断 / `CONTINUE` 异常时跳过继续 |
| `plugin_config_json` | CLOB | **本次绑定的运行时配置**，传给 `PluginInvocation.config()`（如 TransformTemplate JSON） |

> 同一个插件在不同路由上可以有**不同的 `plugin_config_json`**，实现一插件多配置。

---

## 完整数据流示意

以一次 `POST /dynamic-router/order X-App-Code: shop` 请求为例：

```
① 请求到达，提取 app_code="shop"

② 查 route_definition（已缓存）
   WHERE app_code='shop' AND status='ACTIVE'
   ORDER BY route_order ASC
   → 命中 route_code='order-route', target_code='legacy-xml-target'

③ 查 route_target（已缓存）
   WHERE target_code='legacy-xml-target'
   → target_type=HTTP, endpoint_uri='http://legacy/order'

④ 查 route_plugin_binding（已缓存）
   WHERE route_code='order-route' AND enabled=1
   → PRE  sort=1: plugin_code='json-to-xml'  config='{"rootElement":"order"}'
   → POST sort=1: plugin_code='xml-to-json'  config=null

⑤ 执行 PRE 插件链
   json-to-xml: 请求 JSON → XML（rootElement=order）

⑥ 调用 target
   HTTP POST http://legacy/order  body=<order>...</order>
   Content-Type 自动推断为 application/xml

⑦ 执行 POST 插件链
   xml-to-json: 响应 XML → JSON Map

⑧ 返回 RouteResponse{success:true, data:{...}, traceId:"..."}
```

---

## 索引设计

| 索引名 | 表 | 字段 | 用途 |
|---|---|---|---|
| `idx_route_definition_match` | `route_definition` | `app_code, request_path, request_method, status` | 路由匹配主查询 |
| `idx_route_definition_order` | `route_definition` | `app_code, status, route_order` | 优先级排序 |
| `idx_route_target_type_status` | `route_target` | `target_type, status` | 按类型批量加载 target |
| `idx_plugin_definition_phase_status` | `plugin_definition` | `plugin_phase, status` | 按 phase 加载插件 |
| `idx_route_plugin_binding_route_phase_enabled` | `route_plugin_binding` | `route_code, plugin_phase, enabled` | 拉取某路由的插件链 |

> 所有索引服务于**缓存加载**（启动 + 定时刷新），单次请求直接命中内存快照，不走数据库查询。

---

## 新增路由的完整操作步骤

```sql
-- 1. 确认应用已注册（如无则插入）
INSERT INTO app_definition (app_code, app_name, status, remark, created_at, updated_at)
VALUES ('my-app', '我的应用', 'ACTIVE', '', NOW(), NOW());

-- 2. 注册目标（HTTP 示例）
INSERT INTO route_target (
    target_code, target_name, target_type,
    endpoint_uri, component_name, datasource_name,
    operation_type, config_json, secret_ref,
    status, version, remark, created_at, updated_at
) VALUES (
    'my-target', '我的后端', 'HTTP',
    'http://backend-service/api/v1/orders', 'http', NULL,
    'forward', '{}', NULL,
    'ACTIVE', 1, '', NOW(), NOW()
);

-- 3. 定义路由规则
INSERT INTO route_definition (
    route_code, app_code, route_name,
    entry_protocol, request_path, path_match_type,
    request_method, request_format,
    content_type, accept_type,
    route_order, target_code,
    timeout_ms, retry_times,
    status, version, remark,
    created_by, updated_by, created_at, updated_at
) VALUES (
    'my-route', 'my-app', '订单查询',
    'netty-http', '/dynamic-router/orders', 'PREFIX',
    'GET', 'json',
    'application/json', 'application/json',
    1, 'my-target',
    5000, 0,
    'ACTIVE', 1, '',
    'system', 'system', NOW(), NOW()
);

-- 4. 绑定插件（可选，不绑则透传）
INSERT INTO route_plugin_binding (
    route_code, plugin_code, plugin_phase,
    sort_order, enabled, fail_strategy,
    plugin_config_json, created_at, updated_at
) VALUES (
    'my-route', 'json-to-json', 'PRE',
    1, 1, 'FAIL_FAST',
    '{"mappings":[{"from":"orderId","to":"order_id"}]}',
    NOW(), NOW()
);

-- 5. 等待下次缓存刷新（最多 refresh-ms 毫秒）或重启服务立即生效
```
