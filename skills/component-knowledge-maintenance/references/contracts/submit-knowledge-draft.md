# submit_knowledge_draft

后端 POST `/api/v1/knowledge/maintenance/drafts/create`。

## 请求

必填非空字符串：systemCode（系统编码）、projectCode（项目编码）、pagePath（登记页面路径）、componentId（页面内组件标识）。 必填 requestId（非空字符串，幂等键）和 changes（Change 数组）。 必填非空字符串 baseVersionId（当前正式组件版本）。桥接层添加 origin=SESSION。 相同请求重试沿用 requestId；改变内容时换新 ID。基准或快照冲突须重新读取。推断标记 INFERENCE 和 PENDING。

## 响应

成功时 MCP 返回 `isError: false`，`content[0].text` 是后端 Result.data 的 JSON 序列化文本。失败时 `isError: true` 且 text 为错误文字，无成功数据。未声明的参数、缺失必填参数、配置和连接失败由桥接层报错。后端失败时 text 含业务码（参数校验 10000，未知异常 99999）。

返回 Draft。使用服务提供的 reviewUrl 进入审核；提交只创建候选草稿。

## 请求示例

```json
{
  "systemCode": "S",
  "projectCode": "P",
  "pagePath": "/report",
  "componentId": "MetricsStrip",
  "requestId": "session-001",
  "changes": [
    {
      "changeId": "c1",
      "operation": "UPDATE",
      "objectType": "COMPONENT",
      "objectId": "C1",
      "baseObjectVersionId": "COV1",
      "targetVersionId": null,
      "content": {
        "componentId": "MetricsStrip",
        "name": "任务指标",
        "description": "展示有效任务数",
        "details": {
          "accepts": null,
          "presents": null,
          "behaviors": null,
          "stateFlows": null
        }
      },
      "confirmationStatus": "PENDING",
      "evidence": [
        {
          "kind": "FACT",
          "source": "组件源码（示意）",
          "excerpt": "显示有效任务数"
        }
      ],
      "atomicGroupId": null
    }
  ],
  "baseVersionId": "CV1"
}
```

Change：changeId:string（草稿内唯一）、operation:CREATE/UPDATE/LINK/UNLINK/UPGRADE_REFERENCE/EXPIRE、objectType:string、objectId:string（CREATE 必须使用 `new:` 前缀草稿临时键）、baseObjectVersionId:string|null、targetVersionId:string|null、content:object|null、confirmationStatus:CONFIRMED/PENDING|null、evidence:array、atomicGroupId:string|null。字段均传入；LINK/UPGRADE_REFERENCE 用 targetVersionId，UPDATE/CREATE 用 content。UNLINK 只移除当前组件引用；EXPIRE 生成停用对象版本，不物理删除。

Evidence：kind:string（FACT/REQUIREMENT/INFERENCE）、source:string（文件/会话说明定位）、excerpt:string；三项必填非空。只提交必要依据，不上传整个会话。

ObjectVersion：id:string、systemCode:string、projectCode:string、objectType:string、objectId:string、versionNo:integer、previousVersionId:string|null、schemaVersion:integer（当前1）、versionPurpose:string（NORMAL/BASELINE）、lifecycleState:string（ACTIVE/EXPIRED）、confirmationStatus:string|null（CONFIRMED/PENDING）、content:按类型对象、evidence:array、draftId:string、createdAt:string。全部字段返回，说明为 null 的字段可空。

ManifestEntry：objectType:string、objectId:string、versionId:string，均必填；同一清单对象身份唯一，引用必须同系统项目且可解析。顺序为展示顺序。恢复使用固定版本；当前代码位置不随知识恢复。

ComponentDetails：accepts:array<{label:string,detail:string|null}>|null，presents:array<{label:string,note:string|null,variants:array<string>|null}>|null，behaviors:array<string>|null，stateFlows:array<{label,managedBy,input,update,impact:string}>|null。对象中四字段必有；未登记 null，明确无内容 []。

对象 content 按现有知识契约结构校验，未知类型/未支持 schema 拒绝：

| 类型 | 必需字段 | 可空字段/规则 |
| --- | --- | --- |
| COMPONENT | name:string|null、description:string|null、details:ComponentDetails | name/description 可以空字符串，不改变身份 |
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

Draft：id:string、systemCode:string、projectCode:string、componentId:string|null（数据库 ID）、origin:SESSION/MANUAL/RESTORE/MIGRATION、status:DRAFT/PUBLISHED/DISCARDED、revision:integer、baseVersionId:string|null、changes:array<Change>、selectedChangeIds:array<string>、before:object、after:object、beforeObjects:array、afterObjects:array、independent:boolean、issues:array（当前返回 []）、reviewUrl:string、publishResult:object|null。before/after 为组装的知识上下文，beforeObjects/afterObjects 为用于预览的对象数组。另返回 identity（后端保存的请求）、payload（内部快照载荷）、objectBases（对象基准）、selectedChanges（持久化选择）、publishedResult（持久化的发布结果）为内部字符串字段，可能为空；requestId、requestDigest、createdAt、updatedAt 为字符串。草稿仅保存不切换详情；有问题的操作不得发布，PENDING 内容不得自动变为 CONFIRMED。

PublishResult：draftId:string、baselineVersionId:string|null、componentVersionId:string|null、objectVersionIds:object<string,string>。
## 响应数据示例（必要时节选）

```json
{"id": "D1", "origin": "SESSION", "status": "DRAFT", "revision": 1, "selectedChangeIds": ["c1"], "reviewUrl": "/component-detail?draftId=D1", "publishResult": null}
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


组件提交使用完整累计候选。存在唯一普通待确认草稿时整份替换并返回替代草稿；有多条或已有恢复草稿时返回 10111。

审批后的提交携带 `expectedPendingDraft`。空对象 `{}` 表示没有待审核草稿。存在时传 `{"id":"草稿 ID","revision":1}`，revision 为正整数。服务在事务内比较草稿及修订，不匹配返回 10102，存在多条则返回 10111。
