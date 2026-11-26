# read_component_knowledge
通用字段、content 类型、Change、Draft 与错误码见 [common-schema.md](common-schema.md)。

后端 POST `/api/v1/knowledge/maintenance/context`。

## 请求

必填非空字符串：systemCode（系统编码）、projectCode（项目编码）、pagePath（登记页面路径）、componentId（页面内组件标识）。 可选 interfaces 为数组，每项只含必填非空字符串 method（HTTP 方法）和 address（服务标识地址）；明确无接口时传 []。 不接受 meta 或 versionId。

## 响应

成功时 MCP 返回 `isError: false`，`content[0].text` 是后端 Result.data 的 JSON 序列化文本。失败时 `isError: true` 且 text 为错误文字，无成功数据。未声明的参数、缺失必填参数、配置和连接失败由桥接层报错。后端失败时 text 含业务码（参数校验 10000，未知异常 99999）。

currentVersionId 为字符串或 null，currentVersion/viewedVersion 为当前版本信息对象或 null；manifest 是 {objectType, objectId, versionId} 清单，objects 是明确版本正文数组，context 是组装的知识上下文。无正式版本时另返回 legacyContext（原始知识快照，含 knowledgeSources 和 flowContexts）及 sourceRevision（快照摘要）。正式版本存在时 legacyContext 返回 null，sourceRevision 不返回。空身份骨架属于首次盘点；失败时停止判断。

页面已登记且组件未登记时，返回 `registrationObjectId: "new:component"`、空 `pendingDraftIds`、null 的版本信息和用于首次提交的 `sourceRevision`。`legacyContext` 可包含已有的共享页面和接口资料。该读取不创建登记。页面未登记返回 10100。

版本信息字段：id、componentId（数据库 ID）、draftId、createdAt 为字符串；previousVersionId、summary、restoredFromVersionId 可空；versionNo 为整数，current 为布尔值；有恢复来源时附 restoredFromVersionNo（整数或 null）。

pendingDraftIds 是最多包含两个待确认草稿 ID 的字符串数组。零条可新建，一条先读取完整草稿再合并，两条表示应先处理存量草稿。

## 请求示例

```json
{
  "systemCode": "S",
  "projectCode": "P",
  "pagePath": "/report",
  "componentId": "MetricsStrip",
  "interfaces": []
}
```

ObjectVersion：id:string、systemCode:string、projectCode:string、objectType:string、objectId:string、versionNo:integer、previousVersionId:string|null、schemaVersion:integer（当前1）、versionPurpose:string（NORMAL/BASELINE）、lifecycleState:string（ACTIVE/EXPIRED）、confirmationStatus:string|null（CONFIRMED/PENDING）、content:按类型对象、evidence:array、draftId:string、createdAt:string。全部字段返回，说明为 null 的字段可空。

ManifestEntry：objectType:string、objectId:string、versionId:string，均必填；同一清单对象身份唯一，引用必须同系统项目且可解析。顺序为展示顺序。恢复使用固定版本；当前代码位置不随知识恢复。

ComponentDetails：accepts:array<{label:string,detail:string|null}>|null，presents:array<{label:string,note:string|null,variants:array<string>|null}>|null，behaviors:array<string>|null，stateFlows:array<{label,managedBy,input,update,impact:string}>|null。对象中四字段必有；未登记 null，明确无内容 []。

对象 content 按现有知识契约结构校验，未知类型/未支持 schema 拒绝：

| 类型 | 必需字段 | 可空字段/规则 |
| --- | --- | --- |
| COMPONENT | componentId:string（页面内组件标识，读取时由后端写入）、name:string|null、description:string|null、details:ComponentDetails | name/description 可以空字符串，不改变身份 |
| PAGE / FLOW | name:string|null、description:string|null | 说明可为空字符串 |
| KNOWLEDGE | title:string、type:BUSINESS/TECHNICAL、kind:BUSINESS_RULE/METRIC_RULE/TECHNICAL_RULE/CONFLICT、content:string | source:string|null；状态单独存版本元数据 |
| INTERFACE | method:string、path:string、address:string、serviceCode:string、inputs:array、outputs:array、errors:array、examples:array、ruleIds:array<string> | name、description、requestType、responseType:string|null；字段树、错误和示例见下文，字段 ID 不因编辑改变 |
| APPLICABILITY | knowledgeId:string、targetType:COMPONENT/PAGE/FLOW/INTERFACE、targetId:string、unconditional:boolean | condition、exception、replacesKnowledgeId:string|null；未知条件不视为无条件 |
| PAGE_FLOW | pageId:string、flowId:string | 无 |
| INTERACTION | componentId:string（组件数据库 ID）、action:string | triggerCondition、providedContent、response:string|null |
| COMPONENT_RELATION | interactionId:string、targetComponentId:string、effect:string | condition:string|null |

content 不包含跨版本正文副本。关联 ID 在清单中解析相应版本。适用关系的版本绑定仍遵循设计，不将当前关系直接拼接到历史正文。


INTERFACE 嵌套结构：

- inputs 为按输入位置分组的数组，每项含非空字符串 location 和字段数组 fields；outputs 直接是字段数组。
- 每个字段含非空字符串 id（稳定标识）、name（字段名）、path（完整路径）、type（类型）、location（所在位置），以及布尔 required、nullable 和递归字段数组 children（叶子为 []）。meaning、detail、validation 是可空字符串，分别记录业务含义、补充说明和已核实的校验。布尔值缺证据且没有可保留的旧值时暂停候选。
- errors 每项含非空字符串 id、code、description、triggerCondition，以及整数 httpStatus。
- examples 每项含非空字符串 id、name、request、response，以及字符串 description；request 和 response 是可解析的 JSON 字符串。
- ruleIds 是规则对象稳定 ID 的字符串数组。

## 响应数据示例（必要时节选）

```json
{"currentVersionId": null, "currentVersion": null, "viewedVersion": null, "manifest": [], "objects": [], "context": {}, "legacyContext": {"component": {"id": "C1", "componentId": "MetricsStrip"}, "knowledgeSources": {}, "flowContexts": []}, "sourceRevision": "r1"}
```

## 业务错误码

完整错误码表见 [common-schema.md](common-schema.md#业务错误码)。
