# search_knowledge_objects

后端 POST `/api/v1/knowledge/maintenance/objects/search`。

## 请求

必填非空字符串：systemCode（系统编码）、projectCode（项目编码）。可选 objectType 和 keyword（字符串，按类型和关键词筛选）。可选 offset（非负整数，默认 0）、limit（1–100 的整数，默认 20）。返回 items（对象数组）、offset、limit、hasMore（布尔，是否有下一页）。

## 响应

成功时 MCP 返回 `isError: false`，`content[0].text` 是后端 Result.data 的 JSON 序列化文本。失败时 `isError: true` 且 text 为错误文字，无成功数据。未声明的参数、缺失必填参数、配置和连接失败由桥接层报错。后端失败时 text 含业务码（参数校验 10000，未知异常 99999）。

items 为完整 ObjectVersion 数组。按类型与对象 ID 排序。每个对象取最新 NORMAL 版本且该版本为 ACTIVE；keyword 匹配存储的正文。 列表只用于定位，引用仍需核对明确版本。

ObjectVersion：id:string、systemCode:string、projectCode:string、objectType:string、objectId:string、versionNo:integer、previousVersionId:string|null、schemaVersion:integer（当前1）、versionPurpose:string（NORMAL/BASELINE）、lifecycleState:string（ACTIVE/EXPIRED）、confirmationStatus:string|null（CONFIRMED/PENDING）、content:按类型对象、evidence:array、draftId:string、createdAt:string。全部字段返回，说明为 null 的字段可空。

## 请求示例

```json
{
  "systemCode": "S",
  "projectCode": "P",
  "keyword": "任务",
  "limit": 20
}
```

## 响应数据示例

```json
{"items": [], "offset": 0, "limit": 20, "hasMore": false}
```

## 业务错误码

| code | 标识 | 含义 |
| --- | --- | --- |
| 10100 | KNOWLEDGE_NOT_FOUND | 对象、草稿或版本不存在 |
| 10101 | KNOWLEDGE_SCOPE_MISMATCH | 对象不属于当前系统项目或组件 |
| 10102 | KNOWLEDGE_REVISION_CONFLICT | 草稿修订号已变化 |
| 10103 | KNOWLEDGE_BASE_CONFLICT | 组件或编辑对象基准已变化 |
| 10104 | KNOWLEDGE_REFERENCE_INVALID | 引用版本或完整性不合法 |
| 10105 | KNOWLEDGE_DEPENDENCY_INVALID | 关联选择依赖不完整 |
| 10106 | KNOWLEDGE_NO_CHANGE | 没有可发布的实质变更 |
| 10107 | KNOWLEDGE_DRAFT_CLOSED | 草稿已丢弃或不允许修改 |
| 10108 | KNOWLEDGE_REQUEST_CONFLICT | 幂等键被用于不同内容 |
| 10109 | KNOWLEDGE_OBJECT_INVALID | 类型、结构、状态或生命周期不合法 |
| 10110 | KNOWLEDGE_DISABLED | 维护功能尚未启用 |

