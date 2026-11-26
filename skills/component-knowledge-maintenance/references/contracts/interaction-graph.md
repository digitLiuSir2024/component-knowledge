# 交互图读取与候选提交
通用字段、content 类型、Change、Draft 与错误码见 [common-schema.md](common-schema.md)。

- `read_component_interactions`：入参 `systemCode`、`projectCode`、`pagePath`、`componentId`，可选 `versionId`。返回当前版本或明确历史版本的 `currentVersionId`、`viewedVersion`、`interactions`、`collaboration`、`issues`，不写任何对象。`currentVersionId` 是当前正式版本，`viewedVersion` 是本次读取的版本；提交时用前者判定 `baseVersionId`。versionId 不存在时返回 10100。
- `submit_interaction_candidates`：入参 `systemCode`、`projectCode`、`pagePath`、`componentId`、`requestId`、`baseVersionId`、`changes`、`coveragePlan`、`coverageDigest`、`expectedPendingDraft`、`approvalToken`（均必填）。令牌来自 `validate_knowledge_coverage`，只防误用，不替代用户审批。只创建 SESSION 草稿，发布仍由用户在详情页确认。

`submit_interaction_candidates` 与 `submit_knowledge_draft` 使用同一后端端点 `/drafts/create`。差异仅在桥接层预校验 objectType 与引用完整性；审批、幂等、回读和发布语义完全相同。

## 候选约束

- `objectType` 只允许 `INTERACTION` 与 `COMPONENT_RELATION`。
- `INTERACTION.content.componentId` 必须是正式组件数据库 ID，不得为 `new:component`；`action` 非空；`triggerCondition`、`providedContent`、`response` 只能是字符串或 null；可选 `sortOrder` 必须是整数。
- `COMPONENT_RELATION.content.interactionId` 必须等于同批 INTERACTION 候选的 `objectId`（CREATE 时为 `new:` 临时键），不是 `changeId`；`targetComponentId` 与 `effect` 非空；目标不得等于交互归属组件；可选 `sortOrder` 必须是整数。
- 发布成功后交互与联动会同步到基础表，旧表主键沿用版本对象的 objectId；新建对象使用服务端生成的主键。验证以本工具的交互图版本对象为准，不通过 MCP 核验基础表；legacy CRUD 写端点不暴露给模型，禁止用任意 HTTP 绕过 MCP 直接调用。
- 每条候选必须带非空 `evidence` 数组，元素含 `kind`、`source`、`excerpt`。
- 不满足上述约束时在 MCP 层拒绝，不会抵达后端草稿端点。