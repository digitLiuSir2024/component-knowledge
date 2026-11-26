---
name: component-knowledge-maintenance
description: Use when the user explicitly asks to organize or update a component's stored knowledge by comparing the current conversation, stored knowledge, and verified frontend/backend code, then submit proposed changes for review in the component details page.
---

# 组件知识整理

仅在用户明确要求整理指定页面或组件的知识时使用。会话就是 AI 助手会话：整理从当前会话、项目组件清单、相关实现和 MCP 存量取证，先汇总候选供用户审批，经明确批准后才提交候选草稿。发布仍由用户在详情页确认。普通讨论或代码修改不会自动触发。工具不可用或身份不能唯一定位时说明缺口，不用数据库、任意 HTTP 或 shell 绕过 MCP。

向用户汇报时，用组件名称和业务用语说明查到了什么、建议改什么、还缺什么信息。工具名和参数用于执行，用户需要核对提交细节时再附上。

## 术语：三处 componentId

- 请求 Identity.componentId、coveragePlan.components 的键：页面内组件标识，例如 `MetricsStrip`。
- `COMPONENT.content.componentId`：页面内组件标识；提交场景必填，UPDATE 时后端用它核对身份一致性。
- `INTERACTION.content.componentId`：组件数据库 ID，不得为 `new:component`；首次登记须先取得登记 ID 再提交交互。

## 1. 定位并建立盘点清单

从当前会话、项目配置、组件清单和实际装配确认系统、项目、登记页面路径、页面内组件 ID 和源码位置。范围仍不明确时继续查配置与源码，确需用户提供信息才交回。按[会话组件快照](references/coverage-bindings.md)构建 registrySnapshot，调用 `knowledge_coverage_scope`。整页用 root=page，子树用组件 ID。按返回顺序处理，子组件优先；未登记的内部模块也作为实际行为证据。旧绑定仅作用户认可的备用来源。

开始取证前发出可见 TODO，包含定位、读取与取证、逐项判定与校验、审批、提交回读和汇报。后续更新实际进度，单项受阻注明原因并继续其他项。

## 2. 读取存量并沿实现取证

开始取证前必读[存量与取证规则](references/evidence.md)第 1–2 节，按其中的盘点口径和证据要求执行。

沿前端装配、状态、回调和请求追到后端路由、业务计算、查询、DTO、校验与序列化。分别列出直接接口、上游接口及新发现的接口。用已核实的有序方法和服务地址调用 [read_component_knowledge](references/contracts/read-component-knowledge.md)，不传 meta。涉及交互与联动时，用 [read_component_interactions](references/contracts/interaction-graph.md) 读取当前或指定版本交互图，以版本图而非旧表为取证基准。接口列表变化时，用更新后的有序清单重新读取，核对新接口及最新存量；首次基线使用新 sourceRevision。若候选或基准已经获批且发生变化，重新校验和审批。

优先运行适用的源码提取器和项目已有的只读运行观测，保留原始证据及来源。执行者补跑其他可用取证路径；工具仍无法证实的事项带着具体缺口进入评审，由用户确认业务解释或补充证据。

提取结果是待核线索。执行者读取相关实现，逐条核对脚本线索并独立寻找遗漏。源码足以证实的漏项直接补入盘点和候选，附代码位置。脚本误报保留排除依据，解释不准确时采用核实后的口径。只有源码和可用运行证据仍无法确认的事项进入评审缺口。

取证时建立指标依赖清单，覆盖直接展示的指标及公式引用的基础指标；沿状态与回调双向追踪组件联动。接口逐字段梳理与入稿条件分别判定，具体要求见取证规则。

读取失败不能视为无存量。复用对象时用 [search_knowledge_objects](references/contracts/search-knowledge-objects.md) 定位，再用 [read_knowledge_object_version](references/contracts/read-knowledge-object-version.md) 读取明确版本；[list_knowledge_object_versions](references/contracts/list-knowledge-object-versions.md) 仅用于定位历史。用 currentVersionId 判定提交工具，用实质存量判定全量或差量候选。用 [read_knowledge_draft](references/contracts/read-knowledge-draft.md) 检查唯一待审草稿及人工修订；遇到待审核草稿时，必读[草稿流程](references/draft-workflow.md)第 1 节，核对数量、类型、人工修订和基准冲突。

## 3. 逐项判定并验证覆盖

按[取证与候选规则](references/evidence.md)核对 COMPONENT、PAGE、FLOW、KNOWLEDGE、INTERFACE、APPLICABILITY、PAGE_FLOW、INTERACTION、COMPONENT_RELATION，以及工具返回的所有其他必查项。每个接口分别核对。INTERACTION 与 COMPONENT_RELATION 必须进入同一份 coveragePlan 的 items、discovery 和逐组件 review；交互候选导致计划变化时须重算 coverageDigest 并重新审批，不得在校验后单独追加。用同一份盘点记录串联存量身份及版本、证据、实质差异、处置、changeId 和缺口。组件无正式数据库 ID 时仍要查完本组件交互，已核实的交互标为待正式 ID。

用原 registrySnapshot 构造 coveragePlan。每个必查项和新增接口均列项，候选列出对应 changeIds，无提交请求的组件填 request=null，其余放完整提交参数（含 requestId 与 expectedPendingDraft），省略 coveragePlan 和 coverageDigest。可直接对照 [coveragePlan 示例](references/coverage-plan-example.json)：该片段已通过 `validate_knowledge_coverage`，其中标识与证据为占位值，实际使用时必须替换。

本技能生成的新计划须按[结构化检查点](references/review-checkpoints.md)提供 discovery 和逐组件 review，兼容旧客户端的简式计划仅用于已有调用。

候选提交前调用 `validate_knowledge_coverage`，保留返回的 coverageDigest 与 approvalToken。校验通过只证明计划通过工具校验；逐项核对已查位置、具体缺口、剩余取证手段与补齐条件。有可行手段时继续查，不能用 blocked 跳过核对。

## 4. 连续推进至交回节点

准备审批汇总前必读[草稿流程](references/draft-workflow.md)第 3 节。涉及首次登记时，必读第 2 节的临时 ID 映射与交互限制。

只读调研在同一任务内连续执行，无需用户回复“继续”。中间发现用进度消息汇报。交回用户前完成所有可查项，并按实际结果执行：

- **完整候选待审批**。展示逐项结论、基准、changeId、操作、实质内容和证据、覆盖校验码，以及是否建立登记或替换草稿。
- **部分候选待审批**。附同样的汇总，逐项列出受阻证据、具体缺口和补齐条件，明确要求批准部分提交。
- **无实质差异**。逐项给出依据，记录零候选，保留已有草稿。
- **受阻需补充**。用尽可行的只读取证路径后，说明具体需要用户补充的信息或外部条件和已完成的盘点。

## 5. 获批后提交与回读

提交和回读前必读[草稿流程](references/draft-workflow.md)第 3–4 节及对应工具契约。首次和增量示例见第 5–6 节。按草稿流程重新读取并核对获批基准、接口列表及待审草稿。基准和候选均未变化时无需重复确认。无正式版本用 [submit_initial_knowledge_draft](references/contracts/submit-initial-knowledge-draft.md)，已有正式版本用 [submit_knowledge_draft](references/contracts/submit-knowledge-draft.md)。交互与联动单独取证时先用 [read_component_interactions](references/contracts/interaction-graph.md) 读取交互图，再用 [submit_interaction_candidates](references/contracts/interaction-graph.md) 提交同组候选；交互归属必须写成正式组件数据库 ID，联动的 interactionId 必须等于同批 INTERACTION 候选的 objectId（CREATE 用 `new:` 临时键），禁止自环。提交同一份 coveragePlan、coverageDigest、approvalToken 和 expectedPendingDraft；令牌与计划不匹配时桥接层拒绝。按依赖顺序逐组件提交和回读。

用 `read_knowledge_draft` 回读并逐字段对照实际发送的 Change，核实身份、基准、服务允许的 ID 映射和问题列表。正式 ID 使已取证的交互可以入稿时，继续只读准备后续候选，更新基准和覆盖校验后进入审批。最终汇报逐项结论、已提交变更、草稿状态与审核链接，以及遗留事项。发布必须由用户明确批准：可在详情页确认，也可在用户明确要求发布后用 [publish_approved_draft](references/contracts/knowledge-ingestion.md) 执行，工具要求 `confirmPublish=true` 与校验令牌。入库链路、18 张表写入路径与基础表核验工具见 [knowledge-ingestion](references/contracts/knowledge-ingestion.md)。交互回写后仍以交互图版本对象为验证基准；legacy 只读工具用于核验，写工具只用于基础数据准备且需管理开关，禁止任意 HTTP 绕过。
