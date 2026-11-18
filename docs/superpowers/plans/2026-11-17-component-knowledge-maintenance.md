# 组件知识维护与会话整理 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans or superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. 执行方式由用户选择；不得将本计划视为运行测试、操作外部数据库或提交 Git 的授权。

**Goal:** 主动触发会话模型整理，通过现有组件详情页维护和审核知识，确认后发布可恢复的版本。

**Architecture:** 四张新增表承载不可变对象版本、组件版本清单、可修订草稿及可重建当前引用索引。后端统一组织差异与事务发布，前端复用现有详情组件原位审核；会话技能经只读/草稿 MCP 工具接入，不能发布。逐组件确认原始基线后切换知识来源。

**Tech Stack:** 当前 Java 8、Spring Boot 2.7.2、MyBatis XML、Jackson、MySQL；React 18、TypeScript strict、Vite 6、Less；MCP stdio 适配器与 Markdown 技能，不新增后端模型调用。

**Spec:** [已批准设计](../specs/2026-11-17-component-knowledge-maintenance-design.md)。实施前完整阅读设计及两仓库 AGENTS.md。

## Global Constraints

- 后端根目录 B：`/Users/natasha/data/code/zhituo`；前端根目录 F：`/Users/natasha/data/code/智拓报表前端/PlanReport`。下文 B/、F/ 是路径缩写。
- 仅主动触发整理；模型不能发布；不自动读取整个会话或访问未授权来源。
- 现有组件全屏详情样式、布局、目录、表格、卡片和交互保持；不新增独立管理页面。
- 不新增登录权限、通用操作日志、导出导入、监控，不恢复代码。
- 主键 CUuid；结构载荷存 text；禁止 JSON 类型、外键、触发器、视图；DDL 不自动执行到外部库。
- Java 分层、字段注入、DTO/领域/PO 分离、手写 Mapper XML；最多六个方法参数。
- 不以构建通过代替静态检查或测试。单元/E2E 新增及执行均需先取得授权；本计划仅列验收场景，不预先新增测试文件。
- 不因文档阶段启动服务、安装依赖、创建工作树或提交；实施时只变更必要文件，不改报表稳定逻辑。

## Review Focus

1. 部分接受新增规则但拒绝适用关系：必须保持依赖组完整，不发布悬空引用（Task 4、6）。
2. 共享对象升级后恢复旧组件：恢复指定版本，其他组件不变；反向引用不得混用旧表（Task 3、7）。
3. 双击确认、重复请求、候选被另一页面修改：幂等且修订冲突不覆盖（Task 2、4、6）。
4. 组件/接口未知、空值、版本不可解析：显示缺口或失败，不静默读最新或旧正文（Task 1、3、5）。
5. 初次纳管与 meta/旧库冲突：明确审核基线，不批量删知识、不改变组件树（Task 8、10）。

这些场景纳入对应任务人工联调验收。若用户授权自动化测试，将其实现为针对行为的测试，再执行对应失败/通过循环；不得自行补测试或改规范降低门禁。

## 文件职责与共享契约

| 路径 | 职责 |
| --- | --- |
| B/docs/component-knowledge-maintenance-api.md | 新能力正式契约、载荷结构及错误码 |
| B/database/knowledge-version.sql | 四表 DDL、引用索引与唯一约束 |
| B/src/main/java/com/cmbchina/domain/knowledge/version/ | 对象、清单、草稿、差异与发布规则 |
| B/src/main/java/com/cmbchina/domain/knowledge/support/KnowledgeVersionSupport.java | 版本存取及锁的领域端口 |
| B/src/main/java/com/cmbchina/infrastructure/knowledge/dao/{po,mapper}/ | 版本 PO、Mapper 接口 |
| B/src/main/resources/mapper/knowledge/KnowledgeVersionMapper.xml | 查询、行锁及写入 SQL |
| B/src/main/java/com/cmbchina/infrastructure/knowledge/supportimpl/KnowledgeVersionSupportImpl.java | 领域与持久化转换 |
| B/src/main/java/com/cmbchina/application/knowledge/api/KnowledgeMaintenanceApplicationService.java | 维护用例接口 |
| B/src/main/java/com/cmbchina/application/knowledge/api/dto/maintenance/ | 独立请求与响应 DTO，每类单文件 |
| B/src/main/java/com/cmbchina/application/knowledge/service/KnowledgeMaintenanceApplicationServiceImpl.java | 草稿、发布、历史用例编排 |
| B/src/main/java/com/cmbchina/application/knowledge/support/VersionedKnowledgeLoader.java | 清单读取，适配现有展示和模型上下文 |
| B/src/main/java/com/cmbchina/adapter/knowledge/web/KnowledgeMaintenanceController.java | 页面及工具请求入口，不写业务逻辑 |
| F/src/pages/PlanReport/data/knowledgeMaintenance.ts | 维护 DTO 解析与 postJson 调用 |
| F/src/dev/DevWorkbench/ContractPanel/review/ | 差异上下文、字段高亮、表单、关联选择和历史入口 |
| B/tools/knowledge-mcp/ | 会话工具适配器、启动说明及依赖声明 |
| B/docs/skills/component-knowledge-maintenance/SKILL.md | 主动触发整理技能的可审阅源文件 |

JSON 载荷必须按对象类型分别定义 schema，不用任意 Map 代替结构校验。设计中的独立对象类型全部实现，接口契约整体版本化。

公共应用接口（请求/响应均在 dto/maintenance/ 定义；与领域模型隔离）：

- `readContext(MaintenanceContextRequest): MaintenanceContextResponse`
- `searchObjects(ObjectSearchRequest): ObjectSearchResponse`
- `readObjectVersion(ObjectVersionRequest): ObjectVersionResponse`
- `readObjectHistory(ObjectHistoryRequest): ObjectHistoryResponse`
- `createDraft(CreateDraftRequest): DraftResponse`
- `updateDraft(UpdateDraftRequest): DraftResponse`
- `readDraft(DraftRequest): DraftResponse`
- `discardDraft(DiscardDraftRequest): DraftResponse`
- `publishDraft(PublishDraftRequest): PublishResponse`
- `listComponentVersions(ComponentVersionsRequest): ComponentVersionsResponse`
- `createRestoreDraft(CreateRestoreDraftRequest): DraftResponse`
- `createBaselineDraft(BaselineDraftRequest): DraftResponse`

`DraftResponse` 含草稿 ID、revision、状态、基准、完整原内容、候选内容、稳定 changeId、字段差异、原子选择组、依据、问题及审核 URL。`PublishResponse` 含 draftId、新对象版本映射及可空 componentVersionId；独立对象发布允许没有组件版本。

迁移草稿另含 originalSnapshot、migrationSources，发布结果含 baselineVersionId；首次迁移有修改时先存 V1 再发布 V2。`ObjectVersionRequest` 必含系统项目、objectType、objectId、versionId；`ObjectVersionResponse` 返回完整类型载荷、适用条件与关联的明确版本、依据、schemaVersion、确认状态和当前生命周期。分页/分段返回完整性标志及固定版本续读位置，缺失版本不回退最新。未纳管对象读取使用独立 legacy identity/sourceRevision 形式，不能把摘要或伪造版本当正文。

---

### Task 1：确定维护契约与错误语义

**Files:** 新增 B/docs/component-knowledge-maintenance-api.md；修改 B/docs/component-knowledge-api.md 和对应模型上下文接口文档（先定位实际文件）；修改 B/src/main/java/com/cmbchina/infrastructure/common/ResponseEnum.java。

**Interfaces:** 消费设计各对象边界；产出上述用例的正式字段契约。

- [ ] 为上述十二个用例定义 POST `/api/v1/knowledge/maintenance/` 下的具体路径、分页、字段类型、必填/可空及示例；页面客户端沿用 `/zhituo` 代理前缀。
- [ ] 在 component-context 和独立模型上下文正式文档补齐 componentDetails：accepts、presents、behaviors、stateFlows 的字段与 null/空数组语义，保留其他既有字段含义；同步前端解析和复制消费约定。
- [ ] 固定 object identity、manifest、candidate operation、evidence、changeId、atomicGroupId 结构；明确无变化、PENDING、冲突与解除关联语义。
- [ ] 核对已有错误码，再为修订冲突、基准变更、引用无效、依赖不完整、幂等键冲突等分配未占用的集中错误码；HTTP200 / Result 保持一致。
- [ ] 将 readContext 的基准与候选接口地址、审核链接配置来源写入示例；未知对象/损坏历史不得回退正文。核对契约与设计。

### Task 2：四表、领域载荷与版本持久化

**Files:** 新增文件职责表中的 DDL、domain/knowledge/version/、KnowledgeVersionSupport、版本 PO/Mapper/XML/SupportImpl；复用已有 CUuid。

**Interfaces:** `KnowledgeVersionSupport` 提供 `findCurrent(ComponentIdentity)`、`findVersion(String)`、`findObjectVersions(ObjectIdentity)`、`lockComponent(String)`、`lockObjects(List<ObjectIdentity>)`、`lockDraft(String)`、`insertObjectVersion(ObjectVersion)`、`insertComponentVersion(ComponentVersion)`、`saveDraft(ChangeDraft)`；读取结果缺失用项目明确的可空/集合约定。

- [ ] 按修订设计编写四表 DDL，补充 request_id 与请求内容摘要用于重试校验；全部列注释、索引与唯一约束，禁止自动执行外部 DDL。knowledge_component_reference 唯一到组件/对象，按项目及对象身份索引反向引用；只存当前引用。
- [ ] 对象版本增加生命周期、确认状态及 version_purpose（NORMAL / BASELINE）。头版本排除 BASELINE；仅有基线时使用其状态初始化。停用先判断头生命周期，不能跳过 EXPIRED 回找旧 CONFIRMED。UPDATE 不重新启用，历史恢复不改变生命周期。
- [ ] 定义 ObjectIdentity、ObjectVersion、ComponentIdentity、ComponentManifest、ComponentVersion、ChangeDraft、CandidateChange、Evidence、FieldChange、AtomicChangeGroup 及按类型的完整载荷，每类职责独立。
- [ ] 定义前驱、schemaVersion、对象内版本号、关系目标引用、草稿修订号及状态转换校验；范围不能跨系统项目。
- [ ] 实现批量按版本 ID 读取、当前引用反查及事务内替换投影、锁定已有身份行的 SQL。首次对象身份创建使用已有表唯一约束；按固定类型/ID 顺序获取锁。投影能从当前清单重建，不独立修改。
- [ ] 对幂等键相同内容、不同内容、版本号竞争、首次身份竞争列出联调输入/预期。取得数据库验证授权及可用环境后再验证真实锁和唯一约束；缺少环境不得宣称已验证。

### Task 3：清单读取与基线双路径查询

**Files:** 新增 VersionedKnowledgeLoader.java；按需修改 ComponentKnowledgeApplicationServiceImpl.java、application/knowledge/support/KnowledgeContextAssembler.java、application/modelcontext/support/ModelContextLoader.java、application/modelcontext/service/ModelContextApplicationServiceImpl.java。

**Interfaces:** `VersionedKnowledgeLoader.loadCurrent(ComponentIdentity): VersionedContext`、`loadVersion(String): VersionedContext`；VersionedContext 含清单、对象版本索引、关系及 issues，禁止依赖展示 DTO。

- [ ] 实现历史清单闭包解析与批量读取，缺版本明确失败；schema 不支持明确报错，不取最新版替代。
- [ ] 在已有查询入口按“存在当前清单”选择已纳管读取或原路径，新增 componentDetails 并保留其他原字段；纳管组件不读旧表/meta 正文。完整映射四类说明字段，明确 null 与空数组。
- [ ] 改造组件名称列表、接口匹配、共享知识反向引用和联动归组：先用引用索引定位纳管组件、合并未纳管旧关系，再按相应数据源解析；禁止全表清单反序列化。脱离组件的接口查询先校验头生命周期，再取 NORMAL 中最新已确认内容。
- [ ] 给出反向查询 SQL、索引及代表性数据 EXPLAIN 验证步骤；未有数据库执行计划不得宣称性能达标。历史正文固定，当前使用范围/停用提示独立标明。
- [ ] 用同一基线比较原查询与纳管投影的字段、顺序、条件及接口示例；覆盖组件不存在、共享对象后来更新、损坏版本和历史旧引用。仅核对相关上下文，不改报表统计。

### Task 4：草稿差异与事务发布

**Files:** 新增 KnowledgeMaintenanceApplicationService 及实现、maintenance DTO、KnowledgeMaintenanceController；新增 domain/knowledge/version/KnowledgeDraftService.java、KnowledgePublishService.java；按需新增用例辅助文件。

**Interfaces:** 实现公共契约的 readContext/searchObjects/readObjectHistory 与 createDraft/updateDraft/readDraft/discardDraft/publishDraft 五个草稿用例。`KnowledgeDraftService.compare(ChangeDraft, VersionedContext): DraftComparison`；`KnowledgePublishService.prepare(ChangeDraft, PublishSelection): PublishPlan`。差异输出稳定 ID，不以标题和数组位置定位。

同时实现 readObjectVersion，严格按请求的身份和版本解析完整载荷；searchObjects 的摘要不可充当全文。

- [ ] 实现已知基准与候选字段级比较；正文高亮按段落/字段组织，模型语义匹配仅作为候选依据，不构建额外模型或规则引擎。
- [ ] 实现 requestId 幂等创建、revision 更新、丢弃、独立对象草稿以及类型/引用/项目校验；未提及条目自动保留。
- [ ] 计算接受后的完整预览及原子选择组，确保新增对象/关联、空值规则/条件等依赖完整；EXPIRE 不破坏旧清单。
- [ ] 在应用服务事务中锁草稿与身份，检查组件基准、编辑对象头及关联对象生命周期；创建变化版本、完整清单、切换当前、替换引用投影并记录发布结果；异常全部回滚。关联与停用并发必须在同一对象锁下校验。
- [ ] 覆盖重复确认、部分接受、无变化、待确认仍保留、草稿已丢弃、编辑共享旧版本、原子组残缺及双页面修订冲突；同步本任务实际接口契约。

### Task 5：前端维护数据与详情审核状态

**Files:** 新增 F/src/pages/PlanReport/data/knowledgeMaintenance.ts；新增 F/src/dev/DevWorkbench/ContractPanel/review/useKnowledgeReview.ts；修改 useKnowledge.ts、F/src/dev/DevWorkbench/index.tsx。

**Interfaces:** 客户端十二个方法对应应用契约；`useKnowledgeReview(componentIdentity, draftId)` 返回基准、草稿、选择、编辑、发布、错误状态；审核地址通过现有 URL 参数定位组件和 draftId，不引入路由依赖。

- [ ] 用 unknown 解析响应，复用 postJson 和错误封装；处理 AbortController、切换组件及过期响应。
- [ ] 接入草稿打开、保存、丢弃、重新比较及发布 loading；发布成功刷新当前详情和组件名称。
- [ ] 区分 normal/review/history 模式；正常详情没有草稿控件；未知草稿/版本冲突提示具体原因。
- [ ] 验证快速切组件不串草稿、刷新审核 URL 能恢复草稿、旧 revision 提交不覆盖页面修改。

### Task 6：在原有全屏详情中维护与高亮

**Files:** 修改 F/src/dev/DevWorkbench/ContractPanel/{index.tsx,BusinessSection.tsx,ApiCard.tsx,ContractActions.tsx,styles.less}；新增 review/{KnowledgeReviewContext.tsx,ReviewField.tsx,KnowledgeEditor.tsx,KnowledgeLinkPicker.tsx}，编辑复杂对象时在本目录按职责拆分必要表单。

**Interfaces:** `ReviewField({objectId,fieldPath,children})` 读取上下文差异及选择，不改变字段正常布局；编辑器输入对象类型/稳定 ID，保存调用 updateDraft；关联选择器检索对象和可选版本。

- [ ] 以已认可临时原型为交互参照，正式实现全部用 React 数据驱动，禁止复制其 DOM 改写方式。
- [ ] 在原表格/卡片/正文中显示前后变化、新增及拟移除；未变结构不动；依据可展开；依赖组同步选择有说明。
- [ ] 实现组件、页面/流程、知识、适用条件、接口完整字段树/错误/示例、交互及联动的新增编辑与关联入口；复用现有 Disclosure、帮助、复制及组件导航。
- [ ] 共享对象编辑展示引用范围，提供独立发布或同时升级当前组件；PENDING/CONFIRMED 由用户显式选择，不隐式提升。
- [ ] 验证拒绝变更后预览正确、确认结果等于预览、依赖组不悬空、键盘焦点与表单错误定位、正常模式布局不变。接口参数和表格中差异不破坏原折叠与复制。

### Task 7：历史查询、比较与恢复

**Files:** 实现公共接口的 listComponentVersions/createRestoreDraft；新增 F/src/dev/DevWorkbench/ContractPanel/review/KnowledgeHistory.tsx，复用 Task 6 的差异渲染。

**Interfaces:** `createRestoreDraft({componentIdentity,targetVersionId,expectedCurrentVersionId,requestId})` 返回 DraftResponse；历史详情只读但可展开、复制和查看依据。

- [ ] 列出组件及对象历史，展示版本、摘要、时间及恢复来源；历史明确标记，不混入当前正文。
- [ ] 对目标历史清单创建 RESTORE 草稿，比较当前版本，发布复用 Task 4；全部接受标完整恢复，部分接受标部分恢复。
- [ ] 验证共享对象新版存在时恢复旧引用、其他组件不受影响、停用提示、恢复中基准变更及重复确认；不重写对象历史，不恢复代码。

### Task 8：基线草稿与逐组件纳管

**Files:** 在维护应用服务增加 MIGRATION 草稿生成编排及对应契约；新增 B/docs/component-knowledge-migration.md；复用旧 KnowledgeDataSupport 和现有前端 meta 读取。

**Interfaces:** `createBaselineDraft(BaselineDraftRequest): DraftResponse`，请求携带组件身份、旧库读取基准摘要及当前组件 meta 知识候选，不接受任意表名/SQL。

- [ ] 分开保存 originalSnapshot、migrationSources 及拟改操作：原始快照重现当前真实详情及 meta 消费值，冲突来源分别保存，模型候选不能覆盖基线。
- [ ] 首次发布时重核旧库和提交 meta 的摘要，变化则重新比较；同事务保存 V1 原始详情，再发布接受变更后的 V2，无变化仅 V1；保存两者发布结果，重复确认不重复生成。
- [ ] 共享原始内容只有一致才复用已有版本；否则保存 BASELINE 专用版本供恢复，不挤占 NORMAL 头或作为普通升级候选。索引仅对应最终当前 V1/V2。覆盖其他组件已把共享知识升级的场景。
- [ ] 确认纳管后启用 Task 3 路径，未纳管保持原查询；不得混用两个正文源。文档列出发布前、后核对和失败回退边界，已发布版本不能删。
- [ ] 在用户指定验证库获准后，用一个组件验证基线、共享对象复用、并发纳管、旧资料未提及保留；不自动全量迁移。

### Task 9：会话技能与 MCP 草稿接入

**Files:** 新增 B/tools/knowledge-mcp/server.py、README.md、依赖声明（仅选定实现确实需要时）；新增 B/docs/skills/component-knowledge-maintenance/SKILL.md 和结构化候选示例。

**Interfaces:** MCP 仅 `read_component_knowledge`、`search_knowledge_objects`、`read_knowledge_object_version`、`list_knowledge_object_versions`、`submit_knowledge_draft`、`read_knowledge_draft`；分别调用已实现 HTTP 能力，不注册 publish/delete/任意 SQL 工具。

- [ ] 编写技能：仅用户主动触发；先读基准和稳定 ID，再利用会话已读依据；不确定匹配/冲突需标记，无实质变化不提交，不以遗漏删除。
- [ ] 搜索定位候选后读取明确版本全文、条件、例外和来源，历史列表用于选版本；按固定版本续读至 complete 才判断语义。验证同标题不同知识、同 ID 不同版本、正文分段及版本不存在不回退场景。
- [ ] 在执行本任务前查阅当时官方 MCP 与宿主接入文档，选择兼容 stdio 实现；优先复用已具备运行时，不给 Spring 服务引入 MCP 依赖。需要新增外部包时按项目规则先确认，不以计划授权安装。
- [ ] 适配器从本机配置读取后端与前端审核基址，输出受控工具结果，stdout 仅协议；工具错误明确返回，不退回模拟知识。源文件只位于 tools/knowledge-mcp。
- [ ] README 给出启动、配置、健康核对及完整显式触发例子；技能源先交付仓库，个人安装/修改宿主配置单独执行授权流程。
- [ ] 在批准接入的会话中验证工具发现、读取基准、提交草稿、有效审核链接、页面确认及结果读取；检查工具清单无发布能力。未接宿主仅能报告适配器已实现，不能称会话链路完成。

### Task 10：清理 meta 的重复知识来源

**Files:** 修改 F/src/pages/PlanReport/componentMeta.ts、已纳管组件 component.meta.ts、F/scripts/{generate-registry.mjs,generate-component-meta.mjs,check-component-meta.mjs}、F/scripts/lib/businessCollaboration.mjs、F/src/dev/DevWorkbench/ContractPanel/ContractActions.tsx、清单规范/README/AGENTS.md；注册表仅通过生成脚本更新。

**Interfaces:** 精简 meta 保留 ID、source、styles、装配、apiDependencies；前端业务输入输出与 stateFlows 等说明从已确认组件版本读取。

- [ ] 逐字段列出保留、迁移及消费方；先确保 Task 8 确认后查询与复制完整，再移除该组件重复内容。
- [ ] 修改 F/src/pages/PlanReport/data/contractKnowledge.ts、componentMeta.ts、详情前端状态流和 ContractActions.tsx，纳管后统一消费 componentDetails；验证四类字段和空值完整往返，未登记不得自动回读 meta。
- [ ] 删除生成脚本里已迁移的硬编码业务描述，仅保留真实代码依赖提取；动态或识别不可靠的关系不能猜测。
- [ ] 更新生成/校验规则支持分批迁移，未纳管组件仍按原格式工作；完成全部纳管后再收敛格式，不预先批量清空。
- [ ] 执行 `npm run meta:registry`、`npm run meta:check`，核对 ID、树、代码路径、接口实际调用映射不变；复制上下文与页面同版本。不得手改 registry。

### Task 11：集成质量门禁与交付

**Files:** 同步所有相关接口文档、迁移说明、MCP README、前端 README；只修正本任务引入的问题。

- [ ] 后端对实际 Java 改动清单执行 `bash scripts/java-quality.sh verify <实际文件列表>`；随后 `bash scripts/backend-gate.sh <实际Java文件或目录列表>`，该脚本包含静态检查、编译和架构检查；报告真实命令与结果。
- [ ] 前端执行 `npm run format:check`、`npm run typecheck`、`npm run lint`、适用的 `npm run meta:check`、`npm run build`。若项目要求 npm test 但尚未获运行授权，明确请求授权，不能跳过并称交付完成。
- [ ] 对工具执行适用语法和已配置静态检查；未配置静态工具时明确未验证范围，不能将语法或人工审阅称为静态检查通过。
- [ ] 以 1920×1080 和 1440×960 手工浏览器验证现有详情、知识维护、候选选择、冲突、历史恢复和会话审核链接；检查控制台与网络，不额外运行单元/E2E。Chromium 108 未实测则明确报告。
- [ ] 停止仅为验证启动的服务，核对没有未修正违规、不一致契约或未授权数据库操作；按文件清单、检查范围、结果和剩余事项交付。不自动提交 Git。

## 依赖与执行顺序

Task 1 → 2 → 3 → 4 → 5 → 6 → 7 → 8 → 9 → 10 → 11。这是同一发布链路，首版按顺序落地，不拆成独立产品或额外管理页面。

Task 1–6 先完成可联调的维护、审核与发布能力；Task 8 的基线确认完成后才在真实组件形成可用闭环。此前不能将演示基线当成生产已纳管。Task 7–10 补齐恢复、真实迁移、主动会话入口及 meta 收敛；不得以闭环演示替代完整范围交付。

## 计划自检与当前状态

设计覆盖：版本模型/存储 → Task 2；查询一致性 → Task 3；草稿/发布 → Task 4；详情知识维护/原位审核 → Task 5–6；恢复 → Task 7；基线 → Task 8；主动会话整理 → Task 9；meta → Task 10；门禁与契约 → Task 1、11。

本计划只写文档，未执行任何实施步骤、项目测试、DDL、安装或 Git 提交。用户选择执行方式并确认计划后再开始。

2026-11-17 复审修订：五项问题分别落到 Task 1/3/10（字段契约）、Task 2/3/4（可索引引用）、Task 2/3/4（停用规则）、Task 8（原始基线）、Task 1/4/9（会话完整读取）。新增第四张表是为当前引用查询服务，不增加新的业务模块。
