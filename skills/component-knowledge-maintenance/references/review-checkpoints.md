# 结构化检查点

## 1. 记录待查线索

从注册表、组件装配、调用点和计算表达式先记录线索，再填写 review。coveragePlan.discovery 使用组件 ID 为键，每个组件下填 metrics、interactions、interfaces 三个字典，键为稳定盘点键，值为来源位置。登记接口键为 direct:接口 ID 或 upstream:接口 ID。新发现接口也使用对应角色键。即使某类无项目也填空字典。先运行只读提取器，再运行适用的项目自动观测。输入使用本次核实的快照 JSON。

```shell
python3 extract_review_clues.py --snapshot snapshot.json --project-root /path/to/frontend --root ComponentId --rate-source /path/to/MetricService.java
```

按语言和代码结构选择输入。提取器支持注册表接口、TypeScript 常量字段元组、Java 显式 rate 调用和访问器关联。传 `--page-source` 可追踪 React 本地状态、引用该状态的异步调用和组件属性传递。传可重复的 `--dto 接口键 请求DTO.java 响应DTO.java` 可提取同目录对象、列表及无前缀的 `@JsonUnwrapped`。接口响应数据默认从 `response.data` 起算，按实际服务确认外层结构。

对全局状态、自定义事件、动态路由或运行时序列化，优先使用项目已有的只读观测能力自动取证。实际运行得到的联动可通过 `--observations runtime-observations.json` 合入线索。每项包括稳定键 `key`、`sourceComponent`、`targetComponent`、`triggerEvidence`、`effectEvidence`。后两项引用自动记录的触发与响应位置，例如同次操作的事件和请求或状态记录。采集器按项目环境运行，本脚本负责合并、范围校验和防遗漏。空结果或解析失败不能证明无联动，须继续尝试可用观测；仍无法取得时在评审项记录路径、能力缺口和所需证据。

审批前使用同样的提取参数追加 `--check-plan coverage-plan.json`，从源码和实际观测重新生成线索并核对计划保留了这些键。覆盖校验随后检查这些线索都有处置结果。

## 2. 填写盘点结果和提交条件

在 coveragePlan.components 的每个组件内增加 review，与 items、request 同级。review 的 metrics、interactions、interfaces 以线索键索引，可补录取证时新发现的项。每条记录都填 evidence、item（关联 items 的键）和 gap（已核实填 null，有缺口填具体原因和补齐条件）。有缺口的记录关联 blocked 项。如同类还有可提交内容，使用独立的局部受阻项，保留对象类型汇总及候选关联。

- metrics 的 kind 为 derived 或 base。派生指标填 formula 和 dependencies（所引用指标的盘点键列表）。基础指标逐一填写 population、source、filter、window、grouping、deduplication、empty。明确不适用时说明依据。基础口径缺失时填写 gap。派生指标引用受阻口径时不可关联待提交候选。
- interactions 填 direction（inbound 或 outbound）以及 source、action、state、target、response。review.directions 须同时给出 inbound 和 outbound 巡查结论及依据。跨范围来源只取证，是否入稿按已经核实的服务归属及授权范围判定。入稿时 COMPONENT_RELATION.content.interactionId 必须等于同批 INTERACTION 候选的 objectId，不是 changeId。
- interfaces 的 item 与线索键一致。提取结果中的 interfaceFields 保留在 discovery，覆盖校验会与 fields 对照。identifiedFields 为实现中识别的请求及响应字段路径列表，fields 以同一路径为键存储 type、required、nullable、meaning，或具体 gap。路径应区分请求与响应并标记数组层级。未登记不影响梳理。无字段时交代 noFieldsEvidence，无法取得字段时说明 gap。校验、错误及示例依据取证规则记录在盘点及候选正文。

## 3. 对照脚本线索和代码

读取组件装配、回调、请求、服务逻辑、查询与序列化，逐项判断脚本输出，并独立找出只看脚本可能漏掉的计算、条件、联动或字段。每笔指标、联动、接口和接口字段记录都附 `codeCheck`，包含 `status`、`source`（实际阅读的代码位置）和 `finding`（代码证明了什么，或仍缺什么）。

| 状态 | 用法 |
| --- | --- |
| confirmed | 提取线索已由实际代码证实 |
| corrected | 线索存在，但以代码核实的条件、公式、路径或方向修正；finding 说明差异 |
| excluded | 代码证明提取误报；保留线索和排除依据，指标、联动或接口关联 inapplicable，字段不进入接口候选 |
| added | 脚本未提取但源码已证实；直接添加到 review，完整填写口径、联动链或字段 |
| unresolved | 已读源码且可用证据仍不足；填写 gap，进入评审缺口 |

保留原提取清单；用 review 记录纠正和代码补充。字段是否由提取器发现以 discovery.interfaceFields 为准。校验器检查每条记录都有对照结论及证据位置；评审核对证据是否支持结论。已有简式计划仍兼容，结构化计划按本节执行。

## 4. 校验、审批与回读

先运行覆盖校验，再按取证与草稿流程核对内容。脚本检查线索是否处置、公式依赖是否齐全且无环、双向联动是否有结论、已识别字段是否盘点，本批是否重复新增同方法和同地址的接口、是否由多个组件修改同一正式共享对象，以及引用是否提供正式版本 ID。工具只检查记录和已声明的依赖，仍需对照实现核对公式正文、字段发现和证据。保留已核实内容和独立候选；共享对象归属、字段 ID 和跨草稿引用按服务已确认契约核对。提交与回读仍执行草稿流程。
