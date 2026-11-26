# 通用契约定义

本文件是各工具契约的单一事实源。端点专属的请求与示例仍写在各自契约中；字段形状、content 类型、Change、Draft 和错误码只在此处维护。

## 工具清单与令牌

19 个 MCP 工具的必填矩阵见 [knowledge-ingestion.md](knowledge-ingestion.md) 与各端点契约。`approvalToken` 由 `validate_knowledge_coverage` 签发，绑定 coverageDigest、页面身份、componentId、requestId 与所选 changeIds；有效期 30 分钟，发布成功后按 `(componentId, draftId)` 消费，同一计划其他组件或新草稿仍可复用，业务失败不消费。令牌只防误用，不是用户身份认证，也不替代后端鉴权。

## Result 信封

所有后端返回 `Result`：`code:integer`、`success:boolean`、`message:string`、`data:object|array|null`。业务失败也用 HTTP 200；MCP 桥接层把非 200 业务码转成 `isError:true` 的文本。

## 身份与命名

- 页面身份：`systemCode`、`projectCode`、`pagePath`，均为非空字符串。
- Identity：页面身份 + `componentId`（页面内组件标识）。
- `COMPONENT.content.componentId`：页面内组件标识；提交场景必填，UPDATE 时后端核对身份一致性。
- `INTERACTION.content.componentId`：组件数据库 ID，不得为 `new:component`。
- `COMPONENT_RELATION.content.interactionId`：等于同批 INTERACTION 候选的 `objectId`（CREATE 为 `new:` 临时键），不是 `changeId`。

## ObjectVersion

`id`、`objectType`、`objectId`、`versionNo:integer`、`previousVersionId:string|null`、`schemaVersion:integer`、`versionPurpose:NORMAL|BASELINE`、`lifecycleState:ACTIVE|EXPIRED`、`confirmationStatus:CONFIRMED|PENDING`、`content:object`、`evidence:array<Evidence>`、`draftId:string|null`、`createdAt:string`。

`manifest` 元素是 `{objectType, objectId, versionId}`。读取明确版本时 `objects` 返回完整 ObjectVersion 数组，不回退最新正文。

## content 类型

| objectType | 必填字段 | 可选 / 说明 |
|---|---|---|
| COMPONENT | componentId（页面内标识）、name、description、details | name/description 可空字符串；details 含 accepts/presents/behaviors/stateFlows |
| PAGE / FLOW | name、description | 均可空字符串 |
| KNOWLEDGE | title、type、kind、content | type=BUSINESS/TECHNICAL；kind=BUSINESS_RULE/METRIC_RULE/TECHNICAL_RULE/CONFLICT |
| INTERFACE | method、path、address、serviceCode | inputs/outputs/errors/examples/ruleIds 为数组 |
| APPLICABILITY | knowledgeId、targetType、targetId、unconditional | targetType=COMPONENT/PAGE/FLOW/INTERFACE；另有 condition/exception/replacesKnowledgeId |
| PAGE_FLOW | pageId、flowId | — |
| INTERACTION | componentId（数据库 ID）、action | triggerCondition/providedContent/response 可为 null；sortOrder 可选整数 |
| COMPONENT_RELATION | interactionId、targetComponentId、effect | condition 可为 null；sortOrder 可选整数 |

## Evidence

数组元素：`kind`（FACT/REQUIREMENT/INFERENCE）、`source`、`excerpt`，三者均为非空字符串。每条交互与联动候选必须带非空 evidence。

## Change

`changeId`（草稿内唯一）、`operation`（CREATE/UPDATE/LINK/UNLINK/UPGRADE_REFERENCE/EXPIRE）、`objectType`、`objectId`、`baseObjectVersionId:string|null`、`targetVersionId:string|null`、`content:object|null`、`confirmationStatus:CONFIRMED|PENDING|null`、`evidence:array<Evidence>`、`atomicGroupId:string|null`。

- CREATE 的 `objectId` 必须使用 `new:` 前缀临时键。
- LINK / UPGRADE_REFERENCE 用 `targetVersionId`。
- UPDATE / CREATE 用 `content`。
- MIGRATION 基线草稿的 `baseObjectVersionId` 可为 null；其余模式 UPDATE / EXPIRE 必填非空。

## Draft

`id`、`systemCode`、`projectCode`、`componentId:string|null`（数据库 ID）、`origin`、`status`、`revision`、`baseVersionId`、`changes`、`selectedChangeIds`、`before`、`after`、`beforeObjects`、`afterObjects`、`independent`、`issues`、`reviewUrl`、`publishResult`、`identity`、`requestId`、`createdAt`、`updatedAt`。

`payload`、`requestDigest`、`objectBases`、`selectedChanges`、`publishedResult` 是内部存储字段，**不在响应中返回**。`identity` 是后端保存的业务请求，可用于核对 expectedPendingDraft、sourceRevision 与 interfaces。

## 业务错误码

| code | 标识 | 含义 |
|---|---|---|
| 10100 | KNOWLEDGE_NOT_FOUND | 对象、草稿或版本不存在 |
| 10101 | KNOWLEDGE_SCOPE_MISMATCH | 对象不属于当前系统项目或组件 |
| 10102 | KNOWLEDGE_REVISION_CONFLICT | 草稿修订号已变化，或 expectedPendingDraft 与实际草稿状态不符 |
| 10103 | KNOWLEDGE_BASE_CONFLICT | 组件或编辑对象基准已变化 |
| 10104 | KNOWLEDGE_REFERENCE_INVALID | 引用版本或完整性不合法 |
| 10105 | KNOWLEDGE_DEPENDENCY_INVALID | 关联选择依赖不完整 |
| 10106 | KNOWLEDGE_NO_CHANGE | 没有可发布的实质变更 |
| 10107 | KNOWLEDGE_DRAFT_CLOSED | 草稿已丢弃或不允许修改 |
| 10108 | KNOWLEDGE_REQUEST_CONFLICT | 幂等键被用于不同内容 |
| 10109 | KNOWLEDGE_OBJECT_INVALID | 类型、结构、状态或生命周期不合法 |
| 10110 | KNOWLEDGE_DISABLED | 维护功能尚未启用 |
| 10111 | KNOWLEDGE_PENDING_DRAFTS | 请先处理现有待确认更新 |
| 10000 | VALIDATION_ERROR | 参数校验失败 |
| 99999 | UNKNOWN_ERROR | 处理失败，请稍后重试 |
