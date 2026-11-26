# 草稿准备、审批与回读

## 1. 草稿状态与提交方式

上下文读取结果中的 pendingDraftIds 为空时可新建；有一个 ID 时先用 read_knowledge_draft 读取完整候选、预览及人工修改，保留仍有效的候选并合并本次证据，以当前正式版为基准提交完整累计候选。旧基准上的人工修订必须核对到新基准，无法确认冲突时停止提交。两个及以上 ID 表示有多条存量草稿，提示用户先处理。仅有一份草稿时先读取并确认类型；恢复草稿不可按普通草稿替换。读取失败或缺少 pendingDraftIds 时暂停提交。无实质变更保留现有草稿。有正式版本时按 [submit_knowledge_draft](contracts/submit-knowledge-draft.md) 提交；无正式版本时按 [submit_initial_knowledge_draft](contracts/submit-initial-knowledge-draft.md) 提交，即使旧上下文已有知识也走基线草稿。首次读取与提交的接口列表须完全相同且顺序一致，使用读取返回的 `sourceRevision`；读和提交都**不传 meta**，meta 派生说明只放候选，避免后端将其作为已确认原始基线。重试相同内容沿用 `requestId`，改内容则换新 ID；基准/快照变化时重新读取和比较，不静默复用旧候选。

## 2. 未登记组件的首次提交

仅当 MCP 成功返回 `registrationObjectId: "new:component"`、无正式版本、`pendingDraftIds=[]` 且有 `sourceRevision` 时，使用未登记组件分支。页面或接口共享知识仍按已返回的身份和版本盘点。已有组件登记时继续使用数据库 ID。读取失败或缺少登记标识时，不自行构造临时组件身份。

用首次提交工具传原始 `sourceRevision` 和同一有序接口列表。组件正文候选使用 `COMPONENT CREATE`，`objectId` 为 `new:component`，两项版本字段均为 null，`content.componentId` 仍为页面内组件 ID。指向新组件的 `APPLICABILITY.content.targetId`（仅当 targetType 为 COMPONENT）和 `COMPONENT_RELATION.content.targetComponentId` 可用同一临时键。本组件的 INTERACTION.content.componentId 必须使用正式数据库 ID。首次登记前仍要查完交互证据，标为已取证、待正式 ID。登记及回读后，用正式 ID 重读基准和现有草稿，合并交互及其依赖候选，重新校验并取得审批。其他对象按自身契约填写。首次草稿保存时建立组件登记，草稿随后被丢弃时登记保留。遇基准冲突时重新读取，再按最新登记和草稿合并规则处理。

通过 MCP 回读草稿，核对请求的四项业务身份、草稿的正式组件数据库 ID 和 `beforeObjects` 中的 COMPONENT 身份。以该数据库 ID 对实际发送的候选副本应用契约映射，比较 `CREATE → UPDATE`、组件对象 ID 和上述目标引用，按 changeId 逐项核对其他字段。汇报登记结果及已验证的映射。

## 3. 提交前汇总与用户审批

准备完整候选并核对覆盖后，在当前会话向用户汇报组件、基准、拟新增或修改的对象、关联及实质内容、关键证据、缺口和现有草稿是否会被替换。未登记组件须说明提交会同步建立组件登记。待用户明确批准这份汇总后，才使用 `submit_initial_knowledge_draft` 或 `submit_knowledge_draft` 工具保存草稿。发起整理的指令、沉默或超时不构成审批。逐项标注 changeId、对象、操作、实质变化和证据。替换草稿时标明保留的人工修订和本次差异，复杂字段可附完整候选。仅部分有证据时，明确审批仅覆盖已核实候选，并列出未纳入项和原因。全部因缺证据无法提交时报告受阻。证据充分且无实质差异时直接汇报。

审批前逐项检查内容。派生指标有明确公式，直接展示及公式引用的基础指标都有可追溯的统计口径或具体缺口。联动盘点覆盖触发当前组件的来源和当前组件影响的目标。每个接口有逐字段梳理结果和独立的入稿判定。覆盖校验通过后仍须完成这些内容检查。

审批绑定已展示的候选和基准。等待期间只开展只读核对。提交前重新读取基准和待审核草稿，核对版本、sourceRevision（首次基线）以及适用时的草稿 ID、修订号和内容。若基准、已有草稿或拟提交内容发生变化，重新核对、汇总并取得审批。提交时携带获批的 expectedPendingDraft。无待审核草稿时传 {}，否则传 {id, revision}。当前 MCP 工具未暴露该字段时暂停提交并报告连接器版本缺口。遇 10102 或 10111 时重新读取并评估审批是否仍有效。

将新读取的成功 MCP 上下文保存为 JSON。每次提交前使用本轮获批时保存的计划和摘要运行：

```shell
python3 check_submission_baseline.py --plan coverage-plan.json --digest 已获批摘要 --component TrendChart --context fresh-context.json
```

首次基线重读须沿用获批计划中的有序接口。若有唯一待审草稿，加传 `--approved-pending` 与 `--pending`，分别对应获批时和提交前的草稿回读 JSON。脚本核对摘要、正式或首次基准及待审草稿快照。MCP 读取、接口清单核对和审批仍由执行者完成。

整页整理在第一次审批前给每个共享对象指定唯一候选归属。其他组件只引用已取得的正式对象版本。同批尚未发布的草稿不能作为 LINK 或 UPGRADE_REFERENCE 的目标。可以先完成独立草稿，再等待依赖对象发布；发现任一受影响请求的身份、版本或内容变化后，重建剩余计划并重新校验审批。组件装配顺序只用于盘点，提交顺序按可用的对象依赖确定。

可用 `--batch-context batch-context.json` 替代单组件参数。该文件按组件 ID 保存 `context`，若存在待审草稿，再附 `pending` 和 `approvedPending`。脚本输出可继续复核的 ready 组件和 blocked 原因，任一组件受阻则返回非零。受阻项不能提交。脚本不会发送 MCP 请求，提交每一项前仍须重新取得其当前基准。已在审批计划内标记等待发布的对象依赖，取得正式版本后纳入新的审批计划。

提交超时或响应不明时，先通过 MCP 查询并回读可能已保存的草稿，按 requestId 和完整候选确认结果。若仍无法确认，才以同一个 requestId 和完全相同的请求幂等重试。已由本次请求建立的登记按首次登记规则核对。草稿保存后仍需回读校验，发布仍由用户在详情页确认。

## 4. 核对草稿并告知用户

提交前逐项对照 TODO 和盘点记录。每个对象类型、接口和已发现的实质差异都要有证据及处置结果：纳入候选、有依据的无变化或不适用、冲突待核实、缺证据受阻。发现漏项就回到对应代码或契约补查；有证据支持的候选全部纳入。个别项仍受阻时继续交付已核实的候选，并记录缺失证据、影响范围及续办所需条件。

提交后用 [read_knowledge_draft](contracts/read-knowledge-draft.md) 核对服务保存的状态、候选及问题，不绕过页面确认。提交会整份替换唯一待确认草稿，返回替代草稿的 ID。以最近读取的人工修订为合并输入。若遇 10111，提示先处理现有草稿；基准发生变化时重新读取正式版和待确认草稿后再合并。

宣布整理完成前必须校验完整证据链。按对象和接口逐项串起：盘点范围和定位依据 → MCP 存量身份及固定版本（无登记时记录查询结果）→ 已核对的会话、meta、前后端代码或契约及具体位置 → 实质差异或无变化的判断 → 候选操作或无需提交的依据 → 提交后的草稿回读结果（无变更时记录零候选核对结果）。证据需能支持对应结论，标明 FACT、INFERENCE 或 REQUIREMENT。接口证据须覆盖实际调用到后端处理及逐层字段；规则证据须追到实际计算或状态来源。提交后以实际发送的 Change 清单为预期，通过 MCP 按草稿 ID 重新读取知识库中保存的草稿。核对组件身份和基准版本，按 changeId 逐项检查数量、操作、对象 ID、基准及目标版本、完整 content（含嵌套字段）、证据、分组和确认状态，并检查服务返回的问题。仅接受契约明确规定的服务端标识映射或规范化，记录差异及其依据。提交接口返回成功只表明服务已受理，回读一致才可报告草稿保存校验通过。

将实际发送的 MCP 请求参数和 `read_knowledge_draft` 返回的 `content[0].text` 解码后分别保存为 JSON，使用提交响应确认的草稿 ID 运行：

```shell
python3 check_draft_readback.py --request sent-request.json --readback readback.json --draft-id D1
```

脚本核对请求身份、草稿状态、正式或首次基准、首次登记 ID 映射和完整候选。输出嵌套字段差异时按上述规则复核。审核链接及用户选择仍在详情页核对。

每次处理一个页面或组件子树，逐组件提交和回读，在本次任务内保留实际发送的 Change 清单直至完成回读比对。校验以本次提交后通过草稿查询接口读到的持久化内容为准；发现漏项、多项或字段差异时，列出预期值与实际值并继续核查。

任一应盘点事项缺少证据、候选遗漏、草稿回读未验证或校验有未解决问题时，继续补查和复核。仍无法闭合时汇报已验证成果及具体缺口，状态标为部分完成或受阻。最终汇报引用关键证据位置，使用户可以复核结论。

每次结束都汇报结果，包括无变更和受阻的情况。说明组件、首次基线或增量模式、基准版本、逐类盘点结论、实际提交的变更和服务返回的审核链接及状态。逐项列出未提交的内容及具体原因；还有漏项或未验证项时明确说明未完成。

## 5. 供执行者参考的首次提交示例

假设 meta 及代码核实 `DemoMetric` 展示有效任务数；MCP 返回 `currentVersionId=null`、`legacyContext.component.id=c1`、只有空身份骨架和 `sourceRevision=r1`。盘点结论：COMPONENT 修改身份骨架；代码中 `status != CANCELLED` 可推断 KNOWLEDGE 指标口径，APPLICABILITY 指向组件数据库 ID `c1`；其余类型逐项记录证据不足或不适用。候选示意：

```json
[
  {"changeId":"c1","operation":"UPDATE","objectType":"COMPONENT","objectId":"c1","baseObjectVersionId":null,"targetVersionId":null,"content":{"componentId":"DemoMetric","name":"有效任务数","description":"展示按当前筛选条件计算的任务数","details":{"accepts":null,"presents":[{"label":"有效任务数","note":null,"variants":null}],"behaviors":null,"stateFlows":null}},"confirmationStatus":"PENDING","evidence":[{"kind":"FACT","source":"组件源码入口（示意）","excerpt":"展示有效任务数"}],"atomicGroupId":null},
  {"changeId":"c2","operation":"CREATE","objectType":"KNOWLEDGE","objectId":"new:k1","baseObjectVersionId":null,"targetVersionId":null,"content":{"title":"有效任务口径","type":"BUSINESS","kind":"METRIC_RULE","content":"当前实现排除已取消任务。","source":null},"confirmationStatus":"PENDING","evidence":[{"kind":"INFERENCE","source":"任务汇总过滤代码（示意）","excerpt":"status != CANCELLED"}],"atomicGroupId":"g1"},
  {"changeId":"c3","operation":"CREATE","objectType":"APPLICABILITY","objectId":"new:a1","baseObjectVersionId":null,"targetVersionId":null,"content":{"knowledgeId":"new:k1","targetType":"COMPONENT","targetId":"c1","unconditional":true,"condition":null,"exception":null,"replacesKnowledgeId":null},"confirmationStatus":"PENDING","evidence":[{"kind":"FACT","source":"组件取数路径（示意）","excerpt":"指标仅由该组件消费"}],"atomicGroupId":"g1"}
]
```

上述内容为结构示意，不是该项目的真实知识；只有核实目标规则无附加适用条件时才可设 `unconditional=true`。提交时向首次工具传 `r1`、读取时的同一接口列表、这些 changes 和新的 `requestId`；审核仍在详情页。

## 6. 供执行者参考的更新示例

假设当前版本 `v2` 引用 KNOWLEDGE 对象 `K1` 的明确版本 `kv2`，已有“统计所有任务”；前后端代码可核实排除取消状态，且会话未确认业务目标。盘点其他类型无变化或有明确缺口，针对 `K1` 仅提一条 `UPDATE`：`baseObjectVersionId=kv2`，完整 `content` 保留旧 title/type/kind/source，只将口径改为“当前实现排除取消状态”，证据分别指向代码 FACT 与模型口径归纳 INFERENCE，`confirmationStatus=PENDING`。使用 `submit_knowledge_draft` 工具时 `baseVersionId=v2`；如果存量口径与当前实现冲突且无法确定应遵循哪个，不写成已确认结论，只报告冲突或提交清楚标识的待确认候选。
