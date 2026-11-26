# list_knowledge_object_versions
通用字段、content 类型、Change、Draft 与错误码见 [common-schema.md](common-schema.md)。

后端 POST `/api/v1/knowledge/maintenance/objects/history`。

## 请求

必填非空字符串：systemCode（系统编码）、projectCode（项目编码）、objectType（对象类型）、objectId（对象稳定 ID）。 可选 offset（非负整数，默认 0）、limit（1–100 的整数，默认 20）。返回 items（对象数组）、offset、limit、hasMore（布尔，是否有下一页）。

## 响应

成功时 MCP 返回 `isError: false`，`content[0].text` 是后端 Result.data 的 JSON 序列化文本。失败时 `isError: true` 且 text 为错误文字，无成功数据。未声明的参数、缺失必填参数、配置和连接失败由桥接层报错。后端失败时 text 含业务码（参数校验 10000，未知异常 99999）。

items 为完整 ObjectVersion 数组。按 versionNo 降序，可包含 BASELINE 和 EXPIRED 版本。 列表只用于定位，引用仍需核对明确版本。

ObjectVersion：id:string、systemCode:string、projectCode:string、objectType:string、objectId:string、versionNo:integer、previousVersionId:string|null、schemaVersion:integer（当前1）、versionPurpose:string（NORMAL/BASELINE）、lifecycleState:string（ACTIVE/EXPIRED）、confirmationStatus:string|null（CONFIRMED/PENDING）、content:按类型对象、evidence:array、draftId:string、createdAt:string。全部字段返回，说明为 null 的字段可空。

## 请求示例

```json
{
  "systemCode": "S",
  "projectCode": "P",
  "objectType": "KNOWLEDGE",
  "objectId": "K1",
  "offset": 0
}
```

## 响应数据示例

```json
{"items": [], "offset": 0, "limit": 20, "hasMore": false}
```

## 业务错误码

完整错误码表见 [common-schema.md](common-schema.md#业务错误码)。
