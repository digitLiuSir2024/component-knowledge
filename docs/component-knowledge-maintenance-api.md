# 组件知识维护接口契约

状态：实施中，接口未完成前不代表可调用。与 [设计](superpowers/specs/2026-11-17-component-knowledge-maintenance-design.md) 配套；2026-11-17 用户要求直接实施，第二次评审四项问题暂不修正，不擅自扩展重新比较流程。

## 通用约定

全部 POST JSON，前缀 `/api/v1/knowledge/maintenance`。浏览器经既有 `/zhituo` 同源代理。返回 Result：`code:integer`、`success:boolean`、`message:string`、`data:object|array|null`。应用可控制的响应 HTTP200；成功 code=200；校验10000；未知异常99999。

下文必填字符串必须非空白；ID 为 string，不解析成整数。所有对象身份受系统项目隔离约束；数据库主键与页面内 componentId 不混用。数组不允许 null 元素。结构载荷使用 JSON 对象传输、数据库 text 保存；禁止提交表名、SQL、代码执行指令。

Identity：systemCode、projectCode、pagePath、componentId 为必填 string，componentId 为页面内标识。ObjectIdentity：systemCode、projectCode、objectType、objectId 为必填 string。objectType 为 COMPONENT、PAGE、FLOW、KNOWLEDGE、INTERFACE、APPLICABILITY、PAGE_FLOW、INTERACTION、COMPONENT_RELATION。

`/interactions/graph` 只读：未指定 versionId 时读取当前版本；未纳管组件回退 legacy 快照。返回的 interaction 已按版本清单补齐 source/target 身份，关系带 action、triggerCondition、providedContent、response。悬空关系、自环交互与缺失证据在草稿提交前由后端和 MCP 双重拒绝。

发布交互或联动后，同一事务把版本对象回写到 `knowledge_component_interaction` / `knowledge_component_relation` 基础表；回写可用 `KNOWLEDGE_LEGACY_INTERACTION_SYNC_ENABLED=false` 关闭。INTERACTION 与 COMPONENT_RELATION 的 `content.sortOrder` 为可选整数，缺失时按清单顺序 ×10 生成。

## 端点清单

| 后缀 | 必填入参 | 可选入参 | 成功 data |
| --- | --- | --- | --- |
| `/context` | Identity | versionId:string（明确历史版本） | currentVersionId:string|null、currentVersion:ComponentVersionInfo|null、viewedVersion:ComponentVersionInfo|null、manifest:array、objects:array、context:object、legacyContext:object|null |
| `/objects/search` | systemCode、projectCode | objectType:string、keyword:string、offset:integer=0、limit:integer=20（1–100） | items:array、offset:integer、limit:integer、hasMore:boolean |
| `/objects/version` | ObjectIdentity、versionId:string | 无 | 单个完整 ObjectVersion |
| `/objects/history` | ObjectIdentity | offset、limit | items:array、hasMore:boolean |
| `/interactions/graph` | Identity | versionId:string（明确历史版本） | currentVersionId:string|null、viewedVersion:ComponentVersionInfo|null、interactions:array、collaboration:object、issues:array |
| `/drafts/list` | Identity | offset:integer=0、limit:integer=20（1–100） | items:array<DraftSummary>、total:integer、offset:integer、limit:integer、hasMore:boolean |
| `/drafts/create` | Identity、requestId:string、origin:string、changes:array | baseVersionId:string|null、objectOnly:boolean=false | Draft |
| `/drafts/update` | systemCode、projectCode、draftId:string、revision:integer、changes:array | 无 | Draft，revision递增 |
| `/drafts/read` | systemCode、projectCode、draftId | selectedChangeIds:array<string>（仅计算选择预览） | Draft |
| `/drafts/discard` | systemCode、projectCode、draftId、revision | 无 | Draft，DISCARDED |
| `/drafts/publish` | systemCode、projectCode、draftId、revision、selectedChangeIds:array<string> | 无 | PublishResult |
| `/versions` | Identity | offset、limit | items:array、hasMore:boolean |
| `/restore` | Identity、requestId、targetVersionId:string、expectedCurrentVersionId:string | confirmRestore:boolean，默认 false | RESTORE 预览或发布结果 |
| `/baseline` | Identity、requestId、sourceRevision:string、meta:ComponentDetails | changes:array | MIGRATION Draft |
| `/legacy/get` | entity:string、keys:object | 无 | 单行对象 |
| `/legacy/list` | entity:string | filters:object、offset:integer=0、limit:integer=20（1–100） | 对象数组 |
| `/legacy/upsert` | entity:string、row:object | 无 | 写入后的对象 |
| `/legacy/delete` | entity:string、keys:object | 无 | affected:integer |

`/legacy/*` 的 entity 只接受 14 张基础表的枚举名或表名；keys/filters/row 的字段名必须在对应表白名单内，其他字段一律 10000。upsert 未传主键时由服务端生成 CUuid。这套端点用于旧表运行时 CRUD；版本对象发布仍走草稿流程，交互发布会额外回写两张交互基础表。

读 ObjectVersion 必须校验版本的对象身份，不接受用其他对象的 versionId。搜索返回摘要与明确版本身份，不代表已读完整正文。初版全文接口完整返回，不做隐式截断；后续若引入分段必须固定 versionId 并明确完整性，不能默默截断。

## ObjectVersion 与清单

ObjectVersion：id:string、systemCode:string、projectCode:string、objectType:string、objectId:string、versionNo:integer、previousVersionId:string|null、schemaVersion:integer（当前1）、versionPurpose:string（NORMAL/BASELINE）、lifecycleState:string（ACTIVE/EXPIRED）、confirmationStatus:string|null（CONFIRMED/PENDING）、content:按类型对象、evidence:array、draftId:string、createdAt:string。全部字段返回，说明为 null 的字段可空。

ManifestEntry：objectType:string、objectId:string、versionId:string，均必填；同一清单对象身份唯一，引用必须同系统项目且可解析。顺序为展示顺序。恢复使用固定版本；当前代码位置不随知识恢复。

ComponentDetails：accepts:array<{label:string,detail:string|null}>|null，presents:array<{label:string,note:string|null,variants:array<string>|null}>|null，behaviors:array<string>|null，stateFlows:array<{label,managedBy,input,update,impact:string}>|null。对象中四字段必有；未登记 null，明确无内容 []。

对象 content 按现有知识契约结构校验，未知类型/未支持 schema 拒绝：

| 类型 | 必需字段 | 可空字段/规则 |
| --- | --- | --- |
| COMPONENT | name:string|null、description:string|null、details:ComponentDetails | name/description 可以空字符串，不改变身份 |
| PAGE / FLOW | name:string|null、description:string|null | 说明可为空字符串 |
| KNOWLEDGE | title:string、type:BUSINESS/TECHNICAL、kind:BUSINESS_RULE/METRIC_RULE/TECHNICAL_RULE/CONFLICT、content:string | source:string|null；状态单独存版本元数据 |
| INTERFACE | method:string、path:string、address:string、serviceCode:string、inputs:array、outputs:array、errors:array、examples:array、ruleIds:array<string> | name、description、requestType、responseType:string|null；字段树/错误/示例沿用组件知识接口定义，字段 ID 不因编辑改变 |
| APPLICABILITY | knowledgeId:string、targetType:COMPONENT/PAGE/FLOW/INTERFACE、targetId:string、unconditional:boolean | condition、exception、replacesKnowledgeId:string|null；未知条件不视为无条件 |
| PAGE_FLOW | pageId:string、flowId:string | 无 |
| INTERACTION | componentId:string、action:string | triggerCondition、providedContent、response:string|null |
| COMPONENT_RELATION | interactionId:string、targetComponentId:string、effect:string | condition:string|null |

content 不包含跨版本正文副本。关联 ID 在清单中解析相应版本。适用关系的版本绑定仍遵循设计，不将当前关系直接拼接到历史正文。

## 候选与草稿

Change：changeId:string（草稿内唯一）、operation:CREATE/UPDATE/LINK/UNLINK/UPGRADE_REFERENCE/EXPIRE、objectType:string、objectId:string（CREATE 可使用 `new:` 前缀草稿临时键）、baseObjectVersionId:string|null、targetVersionId:string|null、content:object|null、confirmationStatus:CONFIRMED/PENDING|null、evidence:array、atomicGroupId:string|null。字段均传入；LINK/UPGRADE_REFERENCE 用 targetVersionId，UPDATE/CREATE 用 content。UNLINK 只移除当前组件引用；EXPIRE 生成停用对象版本，不物理删除。

Evidence：kind:string（FACT/REQUIREMENT/INFERENCE）、source:string（文件/会话说明定位）、excerpt:string；三项必填非空。只提交必要依据，不上传整个会话。

Draft：id:string、systemCode:string、projectCode:string、componentId:string、origin:SESSION/MANUAL/RESTORE/MIGRATION、status:DRAFT/PUBLISHED/DISCARDED、revision:integer、baseVersionId:string|null、changes:array<Change>、selectedChangeIds:array<string>、before:object、after:object、beforeObjects:array、afterObjects:array、independent:boolean、issues:array<{code,message:string}>、reviewUrl:string、publishResult:object|null。before/after 为同样结构的完整版本上下文，选中项预览必须与发布结果一致。草稿仅保存不切换详情；有问题的操作不得发布，PENDING 内容不得自动变为 CONFIRMED。

PublishResult：draftId:string、baselineVersionId:string|null、componentVersionId:string|null、objectVersionIds:object<string,string>。重复发布返回首次成功结果；同 requestId 不同请求内容拒绝。objectOnly=true 时仍携带当前详情页 Identity 供审核定位，但草稿 component_id 为空，只发布对象版本，不更新任何组件清单或引用索引；不允许独立修改 COMPONENT。objectVersionIds 的键是 objectType:objectId，值为明确版本 ID。草稿修订冲突不修改草稿；基准变化不能直接发布。

恢复目标版本必须属于当前组件及项目；无实际差异不新建版本。首次迁移 sourceRevision 对应旧库/前端原始内容摘要；原始快照与候选分开。V1 保留原始内容，实质变化时 V2 成为当前。首版 BASELINE 可用性限制按已知评审问题记录，不暗中改变设计。

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

## 请求与响应示例

创建草稿（请求体节选，实际必须符合完整 Change 字段）：

```json
{"systemCode":"S","projectCode":"P","pagePath":"/report","componentId":"MetricsStrip","requestId":"session-001","origin":"SESSION","baseVersionId":"CV1","changes":[{"changeId":"c1","operation":"UPDATE","objectType":"KNOWLEDGE","objectId":"K1","baseObjectVersionId":"KV1","targetVersionId":null,"content":{"title":"有效任务","type":"BUSINESS","kind":"BUSINESS_RULE","content":"已取消任务不计入有效任务。","source":null},"confirmationStatus":"PENDING","evidence":[{"kind":"REQUIREMENT","source":"当前会话用户说明","excerpt":"不统计已取消任务"}],"atomicGroupId":null}]}
```

发布请求与成功响应：

```json
{"systemCode":"S","projectCode":"P","draftId":"D1","revision":1,"selectedChangeIds":["c1"]}
```

```json
{"code":200,"success":true,"message":"成功","data":{"draftId":"D1","baselineVersionId":null,"componentVersionId":"CV2","objectVersionIds":{"c1":"KV2"}}}
```

冲突响应：

```json
{"code":10103,"success":false,"message":"知识基准版本已变化，请重新读取后确认","data":null}
```

## 详情页版本识别与待确认更新（2026-11-17）

`POST /context` 的 `versionId` 省略时读取当前生效正文，提供时必须属于 Identity 对应组件；不存在或跨组件返回 10100（KNOWLEDGE_NOT_FOUND），不回退到当前正文。currentVersionId 始终指向当前生效版本；manifest、objects、context 属于 viewedVersion。读取历史不改变当前指针。无正式版本时两份元信息均为 null，并保留旧数据及 sourceRevision 处理。

ComponentVersionInfo 字段：id:string、componentId:string、versionNo:integer、previousVersionId:string|null、draftId:string、summary:string|null、restoredFromVersionId:string|null、current:boolean、createdAt:string（确认时间）。currentVersion/viewedVersion 不含内部 manifest；存在恢复来源时附加 restoredFromVersionNo:integer|null，未找到来源为 null；没有恢复来源时该字段可缺省。`/versions` 列表沿用原结构，含 current、恢复来源 ID 和清单，按 versionNo 降序分页。

`POST /drafts/list` 仅返回指定系统、项目、页面和组件数据库身份下 status=DRAFT 的草稿；PUBLISHED、DISCARDED 与 componentId=null 的独立共享对象草稿不算组件待确认更新。总数由数据库统计，列表按 updatedAt DESC、id DESC 分页，分页默认/上限与 objects/search 一致；total 为非负整数，空列表 items=[]、total=0、hasMore=false。列表与总数是即时读取，并发提交后需刷新。身份/分页校验10000、组件不存在10100、功能关闭10110；信封遵守通用约定。DraftSummary 所有字段均返回：id:string、origin:SESSION/MANUAL/RESTORE/MIGRATION、revision:integer、baseVersionId:string|null、createdAt:string、updatedAt:string、changeCount:integer、summary:string（最多 100 字，无候选时为空字符串）。例如 `"summary":"修改有效任务；停用旧口径"`。列表不返回原文快照、内部压缩数据或请求摘要；changeCount 是候选项数，尚未确认，不是版本号。零候选草稿仍列出，基线可能为零项。

请求示例（两个端点均支持此 Identity）：
```json
{"systemCode":"example-system","projectCode":"example-project","pagePath":"/report","componentId":"MetricsStrip","offset":0,"limit":20}
```
`/drafts/list` 响应示例：
```json
{"code":200,"success":true,"message":"成功","data":{"items":[{"id":"draft-1","origin":"SESSION","revision":1,"baseVersionId":"version-3","createdAt":"2026-11-17T03:00:00Z","updatedAt":"2026-11-17T03:10:00Z","changeCount":2}],"total":1,"offset":0,"limit":20,"hasMore":false}}
```
查看历史请求在 Identity 上添加 `"versionId":"version-2"`。响应 data 示例（正文集合此处用空值演示字段形状）：
```json
{"currentVersionId":"version-3","currentVersion":{"id":"version-3","componentId":"component-db-id","versionNo":3,"previousVersionId":"version-2","draftId":"draft-3","summary":"恢复知识","restoredFromVersionId":"version-1","restoredFromVersionNo":1,"current":true,"createdAt":"2026-11-17T03:00:00Z"},"viewedVersion":{"id":"version-2","componentId":"component-db-id","versionNo":2,"previousVersionId":"version-1","draftId":"draft-2","summary":"更新说明","restoredFromVersionId":null,"current":false,"createdAt":"2026-11-17T02:00:00Z"},"manifest":[],"objects":[],"context":{},"legacyContext":null}
```

页面在进入全屏、草稿改变、返回当前、窗口重新获得焦点、点击“刷新版本信息”时更新版本状态与待确认数量。无后台轮询。审核旧基准时标明过期，发布仍由原有乐观并发校验拒绝，不实施自动重整。历史完整详情复用现有详情布局；代码定位及相关组件导航沿用当前上下文，不属于历史知识快照。

组件普通提交（SESSION、MANUAL 和 MIGRATION）在同一组件锁下处理。没有待确认草稿时新建；已有一条普通草稿时，完整提交替换待确认内容，旧草稿标为 DISCARDED，返回新草稿 ID 和递增后的 revision。提交应包含希望保留的所有候选项。重放旧 requestId 不替换当前草稿。若已有多条待确认草稿，或唯一的草稿为 RESTORE，返回 10111，请先处理现有待确认更新。

新发布版本的 summary 依据本次选中的变更生成，最多 100 字。首次基线的摘要为“初始化组件知识”。

`POST /context` 增加必返 `pendingDraftIds:string[]`，按待确认更新时间降序最多返回两个 ID。空数组表示无草稿，两个 ID 表示存在多条，应先处理而非覆盖。示例 `"pendingDraftIds":["draft-1"]`。

`POST /restore` 增加可选布尔参数 `confirmRestore`，缺省或 false 返回仅供预览的 RESTORE Draft 和 `pendingCount:integer`，预览 ID 不可用于草稿读取或发布。true 在校验预览基准与无待确认草稿后直接创建并发布，响应同 `/drafts/publish`。例如 `{"systemCode":"S","projectCode":"P","pagePath":"/report","componentId":"MetricsStrip","requestId":"restore-1","targetVersionId":"V3","expectedCurrentVersionId":"V5","confirmRestore":true}`。存在待确认草稿返回 10111，当前版本已变化返回 10103，需重新预览。

### 首次草稿登记组件

对于已登记的页面，`POST /context` 可读取尚未登记的组件。响应的 `currentVersionId`、`currentVersion`、`viewedVersion` 为 null，`pendingDraftIds` 为空数组，`registrationObjectId` 为字符串 `new:component`，`sourceRevision` 为读取时的基准摘要。此时 `legacyContext` 仍可包含页面和接口共享知识。查询不会写入登记。页面不存在或请求了不存在的历史版本时返回 10100。

`POST /baseline` 沿用 Identity、interfaces、sourceRevision、requestId 和 changes。首次提交基准有效时，在同一事务内建立组件登记并保存草稿，草稿被丢弃时保留登记。新组件的 COMPONENT 候选使用 `CREATE` 和 `objectId: "new:component"`，组件适用关系的 `targetId` 或联动的 `targetComponentId` 可引用该临时键。草稿保存及回读时，组件候选转为对已登记组件的 `UPDATE`，相关临时键替换为数据库 ID。基准变化返回 10103，失败时事务回滚。

### 永久清理组件知识

`POST /api/v1/knowledge/maintenance/components/purge` 接收四个必填且不可空的非空字符串 `systemCode`、`projectCode`、`pagePath`、`componentId`。一次事务移除组件登记、专属知识及联动、组件版本及引用索引和该组件的全部草稿。共享页面、流程、接口及其他组件仍引用的知识对象版本保留。已登记页面内组件缺失时，重复请求成功。

请求示例

```json
{"systemCode":"LQ13.03","projectCode":"zhituo","pagePath":"/report","componentId":"TrendChart"}
```

成功响应的 `data` 为不可空布尔值 true。身份参数校验失败返回 10000，页面未登记返回 10100，功能关闭返回 10110。失败响应的 `data` 为 null。

```json
{"code":200,"success":true,"message":"成功","data":true}
```

`POST /drafts/create` 和 `POST /baseline` 的组件草稿请求可携带 `expectedPendingDraft`。传空对象 `{}` 表示审批时没有待审核草稿。传 `{"id":"draft-id","revision":1}` 表示允许替换该草稿的指定修订，id 为非空字符串，revision 为正整数。服务在组件加锁后核对预期，并以旧修订号为条件更新被替换的草稿。草稿或修订不匹配返回 10102，多个待审核草稿仍返回 10111，非法结构返回 10000。省略该字段沿用既有提交行为。完全相同的 requestId 和请求体重放返回原草稿。
