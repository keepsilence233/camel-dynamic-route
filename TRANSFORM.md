# 转换插件配置手册

本文档描述 4 个内置转换插件（`json-to-json-mapping`、`json-to-xml`、`xml-to-json`、`xml-to-xml-mapping`）共用的 **TransformTemplate** 配置 DSL，以及如何通过它完成复杂嵌套结构（包括数组套数组）的字段映射、类型转换和自定义脚本。

---

## 1. 总体结构

转换配置写在 `route_plugin_binding.plugin_config_json` 字段里，是一个 JSON 对象：

```json
{
  "mappings": [ ...每条字段映射规则... ],
  "addFields": [ ...新增字段... ],
  "removeFields": [ ...要删除的路径... ],
  "rootElement": "order",
  "forceArrayElements": ["orderLines", "items"]
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `mappings` | Array | 字段重命名 / 类型转换 / 脚本变换规则，有序执行 |
| `addFields` | Array | 向指定路径写入固定值 |
| `removeFields` | Array&lt;String&gt; | 删除指定路径的字段 |
| `rootElement` | String | XML 输出时的根元素名（`json-to-xml` / `xml-to-xml-mapping` 使用） |
| `forceArrayElements` | Array&lt;String&gt; | 强制视为数组的 XML 元素名（`xml-to-json` / `xml-to-xml-mapping` 使用） |

---

## 2. 路径语法

路径用于定位嵌套结构中的字段，支持任意深度的对象嵌套和多层数组展开。

### 2.1 基本规则

```
path    = segment ('.' segment)*
segment = fieldName ('[*]')?
```

- `.` 分隔层级（对象）
- `[*]` 表示「展开该字段对应的数组，对每个元素执行操作」
- 路径的最后一段（叶字段）**不允许** 带 `[*]`

### 2.2 示例对照

| 路径 | 含义 |
|---|---|
| `orderId` | 顶层字段 `orderId` |
| `customer.userId` | `customer` 对象内的 `userId` |
| `orderLines[*].lineId` | `orderLines` 数组每个元素中的 `lineId` |
| `orderLines[*].subItems[*].subItemId` | `orderLines` 每个元素的 `subItems` 数组每个元素中的 `subItemId` |
| `meta.tags[*].tagName` | `meta.tags` 数组每个元素中的 `tagName` |

---

## 3. mappings — 字段映射

### 3.1 字段说明

```json
{
  "from":    "orderLines[*].lineId",
  "to":      "orderLines[*].line_id",
  "type":    "string",
  "default": "UNKNOWN",
  "script":  "value?.toString()?.toUpperCase()"
}
```

| 字段 | 必填 | 说明 |
|---|---|---|
| `from` | ✓ | 源字段路径 |
| `to` | ✓ | 目标字段路径（可以和 `from` 不同层级，实现字段移动） |
| `type` | ✗ | 类型转换，见 §3.2 |
| `default` | ✗ | 当字段不存在或值为 null 时使用的默认值（字符串） |
| `script` | ✗ | Groovy 脚本，见 §5 |

**执行顺序**：先填 `default`，再做 `type` 转换，最后执行 `script`。

### 3.2 type 可选值

| 值 | 说明 |
|---|---|
| `string` | 转为字符串（toString） |
| `integer` / `int` | 解析为整数 |
| `long` | 解析为长整数 |
| `double` / `float` | 解析为浮点数 |
| `boolean` / `bool` | 解析为布尔值 |
| `number` | 自动推断为 Integer / Long / Double |
| （不填） | 保持原类型不变 |

### 3.3 from/to 路径规则

- **同前缀**：原地重命名，最常见场景
  ```json
  {"from": "orderLines[*].lineId", "to": "orderLines[*].line_id"}
  ```

- **不同前缀（无通配符）**：字段在对象层级间移动，引擎会自动创建中间节点
  ```json
  {"from": "userId", "to": "user.id"}
  ```

- **不同前缀（含通配符）**：**不支持**，需要用 Groovy 脚本处理

---

## 4. addFields — 新增字段

向指定路径注入固定值，支持嵌套路径和数组展开。

```json
{
  "path":  "orderLines[*].source",
  "value": "gateway",
  "type":  "string"
}
```

| 字段 | 说明 |
|---|---|
| `path` | 目标路径（叶字段为新字段名） |
| `value` | 要写入的值（字符串、数字、布尔均可） |
| `type` | 可选，对 value 进行类型转换 |

**示例**：对 `orderLines` 数组每个元素注入 `processed=false`：
```json
{"path": "orderLines[*].processed", "value": "false", "type": "boolean"}
```

---

## 5. removeFields — 删除字段

填路径字符串列表，同样支持嵌套和数组展开。

```json
"removeFields": [
  "internalFlag",
  "customer.internalId",
  "orderLines[*].debugInfo",
  "orderLines[*].subItems[*].rawPayload"
]
```

---

## 6. Groovy 脚本

当 `type` 和 `default` 不够用时，通过 `script` 字段写 Groovy 表达式。

### 6.1 可用变量

| 变量 | 类型 | 说明 |
|---|---|---|
| `value` | Object | 当前字段值（已完成 type 转换和 default 填充） |
| `ctx` | `Map<String, Object>` | 该字段所在的父对象（可读取同级字段） |

### 6.2 示例

```groovy
// 字符串大写
value?.toString()?.toUpperCase()

// 为空时用 ctx 中的其他字段兜底
value ?: ctx.orderId

// 数值计算
(value as Double) * 1.1

// 拼接多个字段
"${ctx.firstName} ${ctx.lastName}"

// 条件转换
value == "Y" ? true : false

// 格式化日期字符串
new java.text.SimpleDateFormat("yyyy-MM-dd").format(
    new java.text.SimpleDateFormat("yyyyMMdd").parse(value as String)
)
```

### 6.3 性能说明

脚本在首次执行时编译为 Groovy Class 并缓存；后续调用直接 new 实例执行，无编译开销。

---

## 7. forceArrayElements（XmlToJson / XmlToXml 专用）

XmlMapper 解析 XML 时存在歧义：若某个元素只出现一次，解析结果是 `Map` 而非 `List`；出现多次才是 `List`。`forceArrayElements` 解决这个问题：

```json
"forceArrayElements": ["orderLines", "items", "tags"]
```

插件会在 XML 解析完成后，递归遍历整个 Map 树，将所有名称在列表中的字段值：
- 若已是 `List` → 不变
- 若是 `Map`（单元素被解析成对象）→ 包装成 `List`

**原则：只要该字段在业务上是数组，就把它加进 `forceArrayElements`，无论 XML 里是否只有一条记录。**

---

## 8. rootElement（JsonToXml / XmlToXml 专用）

XML 输出时的根标签名：

```json
"rootElement": "orderRequest"
```

不填时默认为 `"root"`。

---

## 9. 完整示例：订单数据双向转换

### 数据结构

```json
{
  "orderId": "ORD-20240101-001",
  "customerId": 10086,
  "internalFlag": true,
  "customer": {
    "userId": 10086,
    "userName": "Alice",
    "email": "alice@example.com"
  },
  "orderLines": [
    {
      "lineId": 1,
      "productCode": "P-001",
      "unitPrice": "99.9",
      "qty": 2,
      "debugInfo": "raw-debug",
      "subItems": [
        { "subItemId": "SI-001", "skuCode": "SKU-A", "allocated": 2 },
        { "subItemId": "SI-002", "skuCode": "SKU-B", "allocated": 0 }
      ]
    },
    {
      "lineId": 2,
      "productCode": "P-002",
      "unitPrice": "199.0",
      "qty": 1,
      "debugInfo": "raw-debug",
      "subItems": [
        { "subItemId": "SI-003", "skuCode": "SKU-C", "allocated": 1 }
      ]
    }
  ]
}
```

### 9.1 JSON → JSON（PRE 阶段，发送给后端前处理）

```json
{
  "mappings": [
    { "from": "orderId",              "to": "order_id"              },
    { "from": "customerId",           "to": "customer_id",   "type": "string" },
    { "from": "customer.userId",      "to": "customer.user_id"      },
    { "from": "customer.userName",    "to": "customer.user_name"    },
    {
      "from": "orderLines[*].lineId",
      "to":   "orderLines[*].line_id",
      "type": "string"
    },
    { "from": "orderLines[*].productCode", "to": "orderLines[*].product_code" },
    {
      "from":   "orderLines[*].unitPrice",
      "to":     "orderLines[*].unit_price",
      "type":   "double",
      "script": "value * ctx.qty"
    },
    { "from": "orderLines[*].subItems[*].subItemId", "to": "orderLines[*].subItems[*].sub_item_id" },
    { "from": "orderLines[*].subItems[*].skuCode",   "to": "orderLines[*].subItems[*].sku_code"   }
  ],
  "addFields": [
    { "path": "source",                         "value": "gateway" },
    { "path": "orderLines[*].processed",        "value": "false",  "type": "boolean" }
  ],
  "removeFields": [
    "internalFlag",
    "orderLines[*].debugInfo",
    "orderLines[*].subItems[*].allocated"
  ]
}
```

**转换结果：**

```json
{
  "order_id": "ORD-20240101-001",
  "customer_id": "10086",
  "customer": {
    "user_id": 10086,
    "user_name": "Alice",
    "email": "alice@example.com"
  },
  "orderLines": [
    {
      "line_id": "1",
      "product_code": "P-001",
      "unit_price": 199.8,
      "qty": 2,
      "processed": false,
      "subItems": [
        { "sub_item_id": "SI-001", "sku_code": "SKU-A" },
        { "sub_item_id": "SI-002", "sku_code": "SKU-B" }
      ]
    },
    {
      "line_id": "2",
      "product_code": "P-002",
      "unit_price": 199.0,
      "qty": 1,
      "processed": false,
      "subItems": [
        { "sub_item_id": "SI-003", "sku_code": "SKU-C" }
      ]
    }
  ],
  "source": "gateway"
}
```

---

### 9.2 JSON → XML（PRE 阶段，对接只接受 XML 的 legacy 系统）

```json
{
  "rootElement": "orderRequest",
  "mappings": [
    { "from": "orderId",           "to": "OrderId"           },
    { "from": "orderLines[*].lineId",      "to": "orderLines[*].LineId"      },
    { "from": "orderLines[*].productCode", "to": "orderLines[*].ProductCode" },
    { "from": "orderLines[*].subItems[*].subItemId", "to": "orderLines[*].subItems[*].SubItemId" }
  ],
  "removeFields": ["internalFlag"]
}
```

**输出 XML（节选）：**

```xml
<orderRequest>
  <OrderId>ORD-20240101-001</OrderId>
  <customerId>10086</customerId>
  <orderLines>
    <LineId>1</LineId>
    <ProductCode>P-001</ProductCode>
    <subItems><SubItemId>SI-001</SubItemId><skuCode>SKU-A</skuCode></subItems>
    <subItems><SubItemId>SI-002</SubItemId><skuCode>SKU-B</skuCode></subItems>
  </orderLines>
  <orderLines>
    <LineId>2</LineId>
    <ProductCode>P-002</ProductCode>
    <subItems><SubItemId>SI-003</SubItemId><skuCode>SKU-C</skuCode></subItems>
  </orderLines>
</orderRequest>
```

---

### 9.3 XML → JSON（POST 阶段，legacy 后端返回 XML，转为 JSON 给客户端）

```json
{
  "forceArrayElements": ["orderLines", "subItems"],
  "mappings": [
    { "from": "OrderId",           "to": "orderId"           },
    { "from": "orderLines[*].LineId",      "to": "orderLines[*].lineId",      "type": "integer" },
    { "from": "orderLines[*].subItems[*].SubItemId", "to": "orderLines[*].subItems[*].subItemId" }
  ]
}
```

`forceArrayElements` 保证即使响应中只有一条 `orderLines`，也始终是数组而不是对象。

---

### 9.4 XML → XML（两个 XML 系统之间的字段标准化）

```json
{
  "rootElement": "StandardOrder",
  "forceArrayElements": ["Line", "SubLine"],
  "mappings": [
    { "from": "OrderNo",          "to": "OrderId"           },
    { "from": "Line[*].No",       "to": "Line[*].LineId"    },
    { "from": "Line[*].SubLine[*].No", "to": "Line[*].SubLine[*].SubItemId" }
  ],
  "addFields": [
    { "path": "SysSource", "value": "GATEWAY" }
  ]
}
```

---

## 10. 各插件支持矩阵

| 能力 | json-to-json | json-to-xml | xml-to-json | xml-to-xml |
|---|:---:|:---:|:---:|:---:|
| 嵌套对象路径 | ✓ | ✓ | ✓ | ✓ |
| 多层数组 `[*]` 展开 | ✓ | ✓ | ✓ | ✓ |
| 类型转换 (`type`) | ✓ | ✓ | ✓ | ✓ |
| 默认值 (`default`) | ✓ | ✓ | ✓ | ✓ |
| Groovy 脚本 | ✓ | ✓ | ✓ | ✓ |
| 对象层级移动 | ✓ | ✓ | ✓ | ✓ |
| 新增字段 (`addFields`) | ✓ | ✓ | ✓ | ✓ |
| 删除字段 (`removeFields`) | ✓ | ✓ | ✓ | ✓ |
| XML 根元素控制 | — | ✓ | — | ✓ |
| XML 数组歧义修复 | — | — | ✓ | ✓ |
| 跨数组边界移动 | ✗ | ✗ | ✗ | ✗ |

> **跨数组边界移动**（如 `items[*].id` → `details[*].itemId` 且两者不是同一数组）需要用 Groovy script 处理。

---

## 11. 数据库配置示例

```sql
-- 注册插件（一次性操作，已在 data.sql 中预置）
-- plugin_definition 中的 config_schema_json 是文档字段，引擎不校验，可写入 JSON Schema 供前端渲染表单

-- 绑定到路由（每条路由独立配置）
INSERT INTO route_plugin_binding (
    route_code, plugin_code, plugin_phase, sort_order, enabled, fail_strategy,
    plugin_config_json, created_at, updated_at
) VALUES (
    'your-route-code',
    'json-to-json-mapping',   -- 插件名
    'PRE',                    -- PRE：发往后端前执行；POST：收到后端响应后执行
    1,                        -- 同一 phase 内的执行顺序
    1,
    'FAIL_FAST',
    '{
      "mappings": [
        {"from": "orderLines[*].lineId", "to": "orderLines[*].line_id"}
      ],
      "addFields": [{"path": "source", "value": "gateway"}],
      "removeFields": ["internalFlag"]
    }',
    NOW(), NOW()
);
```
