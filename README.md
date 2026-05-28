# Camel Dynamic Route

基于 Apache Camel + Spring Boot 构建的**数据库驱动动态路由网关**。路由规则、插件绑定、目标地址均存储于数据库，无需重启即可热更新；内置可插拔的 PRE/POST 插件流水线，支持请求/响应的透明转换。

---

## 整体架构

```
HTTP 客户端
     │
     ▼
┌─────────────────────────────────────────────────────────────────┐
│                    Camel Netty-HTTP 入口                         │
│  DynamicRouterRouteBuilder  ──→  DynamicRouteEngine.route()     │
└──────────────────────────┬──────────────────────────────────────┘
                           │
          ┌────────────────▼──────────────────┐
          │        DefaultDynamicRouteEngine   │
          │                                   │
          │  1. 从 Cache 获取当前快照           │
          │  2. RouteMatcher 匹配路由           │
          │  3. PluginPipeline.applyPrePlugins │ ←── PRE 插件链
          │  4. TargetExecutor.execute()       │ ←── HTTP / MQ / JDBC
          │  5. PluginPipeline.applyPostPlugins│ ←── POST 插件链
          │  6. 返回 RouteResponse              │
          └───────────────────────────────────┘
                           │
          ┌────────────────▼───────────────────┐
          │        RouteConfigurationCache      │
          │  AtomicReference<Snapshot>          │
          │  @Scheduled 定时从 DB 刷新           │
          └─────────────────────────────────────┘
                           │
          ┌────────────────▼───────────────────┐
          │     RouteConfigurationRepository   │
          │  JDBC 查询 5 张配置表               │
          └─────────────────────────────────────┘
```

---

## 模块说明

| 包 | 职责 |
|---|---|
| `camel/` | Camel 路由定义、全局异常处理器；与引擎解耦，仅做协议适配 |
| `engine/` | 核心引擎：路由匹配、插件流水线、快照缓存、领域接口 |
| `plugin/` | 内置转换插件：JSON↔JSON、JSON↔XML、XML↔XML |
| `executor/` | 目标执行器：HTTP、MQ、JDBC |
| `model/` | 不可变数据模型（Java Record） |
| `repository/` | 数据库访问层，负责加载配置快照 |
| `config/` | Spring Boot 配置属性绑定 |

---

## 请求处理流程（逐步说明）

```
客户端 POST /dynamic-router/user  X-App-Code: demo-app
        │
        ▼
[1] Camel Netty-HTTP 接收请求
    DynamicRouterRouteBuilder.handleRequest()
    - 提取 appCode (X-App-Code header)
    - 提取 path / method / contentType / body
    - 构造 RouteRequest，调用 engine.route()

[2] DefaultDynamicRouteEngine.route()
    - cache.currentSnapshot() → 获取当前内存快照（无锁读）
    - routeMatcher.match(request, snapshot)
      → 按 appCode → entryProtocol → path → method → format 依次过滤
      → 取第一个命中的 RouteDefinition（DB 中按 route_order 排序）

[3] 构造 RouteContext
    - 生成 traceId（UUID）
    - 解析 PRE 插件列表（phase=PRE，按 sort_order 排列）
    - 解析 POST 插件列表（phase=POST，按 sort_order 排列）

[4] PluginPipeline.applyPrePlugins()
    - 依次执行每个 PRE 插件
    - 插件可修改 requestBody、在 attributes 中写入上下文数据
    - 若插件抛异常且 failStrategy=CONTINUE → 跳过该插件继续
    - 若 failStrategy=FAIL_FAST → 向上抛出，触发 GlobalExceptionProcessor

[5] TargetExecutorRegistry.require(targetType).execute(context)
    - HTTP:  producerTemplate.requestBody(endpointUri, requestBody)
    - MQ:    producerTemplate.requestBody(endpointUri, requestBody)
    - JDBC:  NamedParameterJdbcTemplate.queryForList/update(sql, params)

[6] PluginPipeline.applyPostPlugins()
    - 依次执行每个 POST 插件
    - 插件可修改 responseBody（如 XML→JSON 格式转换）

[7] 返回 RouteResponse{success:true, data:..., traceId:...}
    Camel 将响应体写回 HTTP 响应（Content-Type: application/json）
```

---

## 数据模型

### `app_definition` — 应用注册表

| 字段 | 说明 |
|---|---|
| `app_code` | 应用唯一标识，客户端请求时通过 `X-App-Code` header 传入 |
| `status` | `ACTIVE` / `INACTIVE` |

### `route_definition` — 路由规则

| 字段 | 说明 |
|---|---|
| `route_code` | 路由唯一标识 |
| `app_code` | 所属应用（外键） |
| `entry_protocol` | 入口协议，目前仅支持 `netty-http` |
| `request_path` | 匹配路径，格式由 `path_match_type` 决定 |
| `path_match_type` | `EXACT` / `PREFIX` / `ANT` / `REGEX` |
| `request_method` | HTTP 方法，留空则匹配所有 |
| `request_format` | `json` / `xml` / `form`，留空则匹配所有 |
| `route_order` | 优先级，越小越优先（同 appCode 内有序） |
| `target_code` | 指向 `route_target.target_code`（外键） |
| `timeout_ms` | 超时毫秒（当前引擎尚未应用，预留字段） |
| `retry_times` | 重试次数（当前引擎尚未应用，预留字段） |

### `route_target` — 目标地址

| 字段 | 说明 |
|---|---|
| `target_type` | `HTTP` / `MQ` / `JDBC` |
| `endpoint_uri` | HTTP/MQ 目标地址，格式遵循 Camel URI |
| `config_json` | 执行器私有配置；JDBC 类型中存放 SQL 模板（含命名参数） |

### `plugin_definition` — 插件注册表

| 字段 | 说明 |
|---|---|
| `plugin_code` | 插件唯一标识，与 `RoutePlugin.pluginCode()` 对应 |
| `bean_name` | Spring Bean 名称（仅作元数据，引擎按 `plugin_code` 查找） |
| `plugin_phase` | 插件能力声明（`PluginPhase` 枚举，见下方说明） |
| `config_schema_json` | 配置项的 JSON Schema 定义（文档用途，引擎不校验） |

### `route_plugin_binding` — 路由-插件绑定

| 字段 | 说明 |
|---|---|
| `route_code` | 绑定到的路由 |
| `plugin_code` | 使用的插件 |
| `plugin_phase` | **执行阶段**：`PRE`（调用目标前）/ `POST`（调用目标后） |
| `sort_order` | 同一 phase 内的执行顺序，越小越先 |
| `enabled` | `1` 启用 / `0` 禁用 |
| `fail_strategy` | `FAIL_FAST`（抛出异常）/ `CONTINUE`（跳过继续） |
| `plugin_config_json` | 此次绑定的插件运行时配置（JSON 对象，传给 `PluginInvocation.config()`） |

---

## 插件系统详解

### PluginPhase vs PluginExecutionPhase

这两个枚举经常令人困惑，区别如下：

| | `PluginPhase` | `PluginExecutionPhase` |
|---|---|---|
| 所在表 | `plugin_definition.plugin_phase` | `route_plugin_binding.plugin_phase` |
| 含义 | **插件的能力声明**，描述该插件设计用于哪个阶段（包含更细粒度的 `PRE_TRANSFORM`, `PRE_ENCRYPT`, `POST_TRANSFORM` 等） | **运行时执行门控**，只有 `PRE` / `POST` 两个值，决定插件在调用目标前还是后运行 |
| 引擎使用 | 元数据，引擎不参与逻辑 | 直接决定执行时机 |

简单说：`PluginPhase` 是插件"自我介绍"的标签，`PluginExecutionPhase` 是绑定时"何时运行"的指令。

### 实现一个插件

```java
@Component
public class MyPlugin implements RoutePlugin {

    @Override
    public String pluginCode() {
        return "my-plugin";  // 与 plugin_definition.plugin_code 对应
    }

    @Override
    public PluginResult execute(PluginInvocation invocation) {
        // invocation.input()   → 包含 headers / attributes / body
        // invocation.config()  → 来自 route_plugin_binding.plugin_config_json
        Object body = invocation.input().get("body");
        // ... 处理 ...
        return new PluginResult(
            Map.of("body", transformedBody),   // 输出体，覆盖 body
            Map.of("myKey", "myValue")          // 写入 RouteContext.attributes
        );
    }
}
```

注册插件（在数据库写入即可，无需改代码）：

```sql
INSERT INTO plugin_definition (plugin_code, plugin_name, plugin_phase, plugin_scope,
    bean_name, plugin_class, status, created_at, updated_at)
VALUES ('my-plugin', '我的插件', 'PRE_TRANSFORM', 'route',
    'myPlugin', 'com.example.MyPlugin', 'ACTIVE', NOW(), NOW());
```

绑定到路由：

```sql
INSERT INTO route_plugin_binding (route_code, plugin_code, plugin_phase, sort_order,
    enabled, fail_strategy, plugin_config_json, created_at, updated_at)
VALUES ('my-route', 'my-plugin', 'PRE', 1, 1, 'FAIL_FAST',
    '{"key":"value"}', NOW(), NOW());
```

### `PluginResult` 说明

```
PluginResult(
    output     → Map，必须包含 "body" 键；若缺失则沿用上游 body
    attributes → Map，合并进 RouteContext.attributes，供后续插件/执行器读取
)
```

返回 `PluginResult.empty()` 表示不修改 body，也不写入 attributes。

### `FAIL_FAST` vs `CONTINUE`

- **`FAIL_FAST`**（默认）：插件抛异常时，整条请求链中断，返回 500 错误响应。
- **`CONTINUE`**：插件抛异常时，丢弃该插件的输出，以**未修改**的上下文继续执行后续流程。适合非关键性插件（如日志记录、审计）。

---

## 目标执行器

### HTTP 执行器（`target_type = HTTP`）

```sql
INSERT INTO route_target (..., target_type, endpoint_uri, config_json, ...)
VALUES (..., 'http', 'http://backend-service/api/v1/users', '{}', ...);
```

使用 Camel `ProducerTemplate.requestBody(uri, body)` 转发请求，body 透传。

### MQ 执行器（`target_type = MQ`）

```sql
INSERT INTO route_target (..., target_type, endpoint_uri, config_json, ...)
VALUES (..., 'mq', 'activemq:queue:order.created', '{}', ...);
```

使用 Camel ProducerTemplate 发送消息，`endpointUri` 即 Camel 组件 URI。

### JDBC 执行器（`target_type = JDBC`）

```sql
INSERT INTO route_target (..., target_type, config_json, ...)
VALUES (..., 'jdbc',
    'SELECT id, name FROM user WHERE user_id = :userId AND status = :status', ...);
```

- `config_json` 字段存放**带命名参数的 SQL 模板**（`:paramName` 格式）。
- 参数值来自请求体（Map 或 JSON 字符串）。
- 以 `SELECT` 开头 → 执行 `queryForList`，返回 `List<Map>`。
- 其他（`INSERT` / `UPDATE` / `DELETE`）→ 执行 `update`，返回 `{"affectedRows": N}`。

### 实现一个新执行器

```java
@Component
public class GrpcTargetExecutor implements TargetExecutor {

    @Override
    public String supportType() {
        return "grpc";  // 与 route_target.target_type 对应（不区分大小写）
    }

    @Override
    public Object execute(RouteContext context) {
        // context.routeTarget().endpointUri()   → gRPC 地址
        // context.routeTarget().configJson()    → 执行器私有配置（如 method 名）
        // context.requestBody()                 → 请求体
        // ...
    }
}
```

Spring 启动时自动收集所有 `TargetExecutor` bean，以 `supportType()` 建索引，无需任何额外配置。

---

## 缓存机制

```
RouteConfigurationCache
│
├── AtomicReference<RouteConfigurationSnapshot>
│   └── 读操作：currentSnapshot()，无锁，高并发友好
│
├── @Scheduled(fixedDelay = ${route.cache.refresh-ms})
│   └── 定时从 DB 重新加载，替换引用（旧快照由 GC 回收）
│       fixedDelay 保证不并发刷新（上次完成后再计时）
│
└── RouteConfigurationSnapshot（不可变快照）
    ├── targetsByCode:     Map<String, RouteTarget>
    ├── pluginsByCode:     Map<String, PluginDefinition>
    ├── bindingsByRouteCode: Map<String, List<RoutePluginBinding>>
    └── routes:            List<RouteDefinition>（已按 route_order 排序）
```

快照是不可变的值对象（Java Record），并发读取无需任何同步。刷新时整体替换引用，读者要么拿到旧快照要么拿到新快照，不会看到中间状态。

---

## 配置参考

```yaml
route:
  ingress:
    protocol: netty-http   # 入口协议（目前固定 netty-http）
    host: 127.0.0.1        # 监听地址
    port: 8080             # 监听端口
    path: /dynamic-router  # 基础路径，所有动态路由都挂在此路径下
  cache:
    refresh-ms: 30000      # 路由配置缓存刷新间隔（毫秒），建议 10000~60000
```

---

## 快速开始

### 环境要求

- JDK 17+
- Maven 3.8+

### 启动

```bash
mvn spring-boot:run
```

启动后监听 `http://127.0.0.1:8080/dynamic-router/*`。

内置 H2 内存数据库会自动执行 `schema.sql` + `data.sql`，预置以下演示数据：

| 路由 | 路径 | 描述 |
|---|---|---|
| `demo-route` | `POST /dynamic-router/demo` | noop 透传，转发到 `localhost:18080/mock` |
| `demo-json-mapping-route` | `POST /dynamic-router/user` | PRE 阶段 JSON 字段重命名 |
| `demo-xml-adapt-route` | `POST /dynamic-router/order` | PRE: JSON→XML；POST: XML→JSON |

### 验证

```bash
# 测试 JSON 字段映射路由（需要 localhost:18080 有监听）
curl -X POST http://127.0.0.1:8080/dynamic-router/user \
  -H "Content-Type: application/json" \
  -H "X-App-Code: demo-app" \
  -d '{"userId":1,"userName":"Alice","userEmail":"alice@example.com","internalFlag":true}'

# 实际发往后端的 body 经过 PRE 插件转换后变为：
# {"user_id":1,"user_name":"Alice","email":"alice@example.com","source":"gateway"}
# (internalFlag 被删除，source 被注入)
```

---

## 内置插件说明

### `noop-plugin` — 透传插件

原样透传 body，不做任何修改。用于占位或测试流水线是否正常工作。

无配置项。

---

### `json-to-json` — JSON 字段映射

将 JSON 体中的字段做重命名、新增、删除。

```json
{
  "mappings": [
    { "from": "userId",    "to": "user_id"   },
    { "from": "userName",  "to": "user_name" },
    { "from": "userEmail", "to": "email"     }
  ],
  "addFields": {
    "source":  "gateway",
    "version": "v2"
  },
  "removeFields": ["internalFlag", "debugInfo"]
}
```

输入 → 输出示例：

```json
// 输入
{"userId":1,"userName":"Alice","userEmail":"a@b.com","internalFlag":true}

// 输出
{"user_id":1,"user_name":"Alice","email":"a@b.com","source":"gateway","version":"v2"}
```

---

### `json-to-xml` — JSON 转 XML

将 JSON 字符串体转换为 XML 字符串，适合向只接受 XML 的 legacy 系统转发（PRE 阶段）。

```json
{ "rootElement": "order" }
```

输入 → 输出示例：

```
// 输入 JSON
{"orderId":"ORD-001","amount":99.5,"currency":"CNY"}

// 输出 XML
<order><orderId>ORD-001</orderId><amount>99.5</amount><currency>CNY</currency></order>
```

---

### `xml-to-json` — XML 转 JSON

将 XML 字符串体转换为 JSON 字符串，适合 legacy 后端返回 XML 而客户端期望 JSON 的场景（POST 阶段）。

无配置项。

输入 → 输出示例：

```
// 输入 XML
<response><code>0</code><message>success</message><orderId>ORD-001</orderId></response>

// 输出 JSON
{"code":"0","message":"success","orderId":"ORD-001"}
```

---

### `xml-to-xml` — XML 字段映射

解析 XML 后对**顶层元素**做重命名，再序列化回 XML（PRE 或 POST 阶段均可）。

```json
{
  "rootElement": "order",
  "mappings": [
    { "from": "UserId",   "to": "user_id"   },
    { "from": "UserName", "to": "user_name" }
  ]
}
```

输入 → 输出示例：

```
// 输入 XML
<root><UserId>1</UserId><UserName>Alice</UserName><Amount>100</Amount></root>

// 输出 XML（根元素改为 order，顶层字段重命名）
<order><user_id>1</user_id><user_name>Alice</user_name><Amount>100</Amount></order>
```

> 当前仅支持顶层元素重命名；嵌套结构透传不变。

---

## 组合插件示例

多个插件可绑定到同一路由，按 `sort_order` 顺序执行：

```sql
-- 路由 xml-transform-route
-- PRE-1: XML 字段重命名（重命名后发给后端）
-- PRE-2: 此处无（也可再加其他插件）
-- 目标：HTTP legacy 系统（接受 XML）
-- POST-1: XML→JSON（返回 JSON 给客户端）

INSERT INTO route_plugin_binding VALUES
  ('xml-transform-route', 'xml-to-xml', 'PRE',  1, 1, 'FAIL_FAST',
   '{"rootElement":"req","mappings":[{"from":"OrderId","to":"order_id"}]}', NOW(), NOW()),
  ('xml-transform-route', 'xml-to-json',         'POST', 1, 1, 'CONTINUE',
   NULL, NOW(), NOW());
```

执行时序：

```
请求 XML → [PRE-1: xml-to-xml] → 重命名后的 XML → [HTTP 执行器] → 后端 XML 响应
                                                                              │
客户端 ← JSON ← [POST-1: xml-to-json] ←──────────────────────────────────────┘
```

---

## 已知限制

| 限制 | 说明 |
|---|---|
| `timeout_ms` / `retry_times` 未生效 | `RouteDefinition` 中的字段已持久化，但引擎尚未应用超时/重试逻辑 |
| 请求体未自动反序列化 | Netty 接收到的 body 为原始字节/字符串，JDBC 执行器和转换插件在内部各自解析 |
| XML 插件只支持顶层字段映射 | `xml-to-xml` 不处理嵌套元素重命名 |
| 单 Camel 入口 | 目前只有 `netty-http` 一种入口协议；支持其他入口（如 Kafka、定时触发）需扩展 `DynamicRouterRouteBuilder` |
| 无管理 API | 路由配置变更只能直接操作数据库，缓存在下次定时刷新后生效 |
