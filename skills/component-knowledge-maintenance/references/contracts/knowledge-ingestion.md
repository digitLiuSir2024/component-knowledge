# 知识入库与全流程核验工具

本契约说明知识如何从候选进入正式库，以及补齐的 MCP 工具。字段通用定义见 [common-schema.md](common-schema.md)。

## 入库链路

| 阶段 | 写入表 | 触发 |
|---|---|---|
| 候选提交 | `knowledge_change_draft`；首次登记另写 `knowledge_component` | `submit_initial_knowledge_draft` / `submit_knowledge_draft` / `submit_interaction_candidates` |
| 发布 | `knowledge_object_version`、`knowledge_component_version`、`knowledge_component_reference`、`knowledge_change_draft`（置 PUBLISHED） | `publish_approved_draft` 或详情页发布 |
| 交互回写 | `knowledge_component_interaction`、`knowledge_component_relation` | 发布事务内，由 `InteractionSyncSupport` 同事务执行 |
| 基础表准备 | 其余 11 张 legacy 表 | `upsert_legacy_record` / `delete_legacy_record`（需管理开关） |

所有写入都在应用服务事务内；发布失败时版本写入与交互回写一起回滚。

## 工具清单

| 工具 | 必填 | 可选 | 后端端点 | 用途 |
|---|---|---|---|---|
| `knowledge_coverage_scope` | systemCode, projectCode, pagePath, root | registrySnapshot | 本地 | 生成整理清单 |
| `validate_knowledge_coverage` | systemCode, projectCode, pagePath, coveragePlan | — | 本地 | 返回 coverageDigest 与 approvalToken |
| `read_component_knowledge` | systemCode, projectCode, pagePath, componentId | interfaces | `/context` | 读当前组件基准 |
| `search_knowledge_objects` | systemCode, projectCode | objectType, keyword, offset, limit | `/objects/search` | 搜索对象 |
| `read_component_interactions` | systemCode, projectCode, pagePath, componentId | versionId | `/interactions/graph` | 读交互图 |
| `read_knowledge_object_version` | systemCode, projectCode, objectType, objectId, versionId | — | `/objects/version` | 读明确版本正文 |
| `list_knowledge_object_versions` | systemCode, projectCode, objectType, objectId | offset, limit | `/objects/history` | 列对象历史 |
| `submit_knowledge_draft` | systemCode, projectCode, pagePath, componentId, requestId, baseVersionId, changes, coveragePlan, coverageDigest, expectedPendingDraft, approvalToken | — | `/drafts/create` | 提交会话候选 |
| `submit_interaction_candidates` | systemCode, projectCode, pagePath, componentId, requestId, baseVersionId, changes, coveragePlan, coverageDigest, expectedPendingDraft, approvalToken | — | `/drafts/create` | 提交交互候选 |
| `submit_initial_knowledge_draft` | systemCode, projectCode, pagePath, componentId, requestId, sourceRevision, interfaces, changes, coveragePlan, coverageDigest, expectedPendingDraft, approvalToken | — | `/baseline` | 提交首次基线 |
| `read_knowledge_draft` | systemCode, projectCode, draftId | — | `/drafts/read` | 读草稿与审核地址 |
| `list_knowledge_drafts` | systemCode, projectCode, pagePath, componentId | offset, limit | `/drafts/list` | 发现待确认草稿 |
| `list_component_versions` | systemCode, projectCode, pagePath, componentId | offset, limit | `/versions` | 核验正式版本 |
| `read_legacy_record` | entity, keys | — | `/legacy/get` | 只读核验基础表单行 |
| `list_legacy_records` | entity | filters, offset, limit | `/legacy/list` | 只读核验基础表多行 |
| `upsert_legacy_record` | entity, row, confirmWrite=true | — | `/legacy/upsert` | 写入基础表（管理开关） |
| `delete_legacy_record` | entity, keys, confirmWrite=true | — | `/legacy/delete` | 删除基础表行（管理开关） |
| `publish_approved_draft` | systemCode, projectCode, pagePath, componentId, requestId, draftId, revision, selectedChangeIds, confirmPublish=true, approvalToken | — | `/drafts/publish` | 发布已审批草稿；令牌绑定组件、requestId 与所选候选，按 (componentId, draftId) 一次性消费 |
| `discard_knowledge_draft` | systemCode, projectCode, draftId, revision, confirmDiscard=true | — | `/drafts/discard` | 丢弃待审草稿，带修订保护 |

`entity` 只接受 `LegacyEntity` 的 14 个枚举名；`keys` / `filters` / `row` 的字段名必须在对应表白名单内，否则桥接层或后端返回 10000。`COMPONENT_INTERACTION` 与 `COMPONENT_RELATION` 由发布事务维护，禁止通过 legacy 写工具修改；只读核验仍允许。

## 守卫与确认

- 基础表写工具默认关闭。必须设置 `KNOWLEDGE_MCP_ENABLE_ADMIN_WRITES=true` 才能调用；`confirmWrite` 必须显式为 `true`。
- `publish_approved_draft` 的 `confirmPublish` 必须显式为 `true`；`approvalToken` 为必填，且必须绑定同一组件的页面身份、`requestId` 与所选 `changeIds`。令牌有效期 30 分钟；发布成功后按 `(componentId, draftId)` 消费，同一计划的其他组件或新草稿仍可复用同一令牌，业务失败不消费；进程重启后需重新校验。
- 发布仍需用户明确批准。用户可以在详情页确认，也可以在会话中明确要求发布后由工具执行；发起整理的指令、沉默或超时不构成发布批准。
- `approvalToken` 只防误用，不是用户身份认证；后端 `/drafts/publish` 仍需鉴权才能构成安全边界。
- `discard_knowledge_draft` 必须携带当前 `revision`，防止丢弃已变化的草稿。
- 版本表不开放通用 CRUD；正式内容只能通过草稿发布产生，避免绕过版本链。

## 核验顺序

1. `read_knowledge_draft` 看草稿状态、审核地址与发布结果。
2. `publish_approved_draft` 后再次 `read_component_knowledge`，确认 `currentVersionId` 已更新。
3. `list_component_versions` 核验正式版本清单与版本号。
4. `read_legacy_record` / `list_legacy_records` 核验交互回写结果与基础表准备数据。
5. `list_knowledge_drafts` 确认没有遗留待确认草稿。
