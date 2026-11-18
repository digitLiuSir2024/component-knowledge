# 组件知识草稿与恢复预览实施计划

> 执行使用 superpowers:executing-plans，按任务跟踪。单元测试及 E2E 按 AGENTS.md 的授权约束执行。

**目标**：完成单草稿覆盖、待确认和历史摘要、恢复差异定位及已安装技能联动。

**技术栈**：现有 Java 服务、MCP、React / TypeScript。

**设计**：[组件知识草稿与恢复预览](../specs/2026-11-17-knowledge-draft-and-restore-design.md)。

### 1. 草稿覆盖、摘要及接口

- [ ] 在 KnowledgeDraftCoordinator 的组件提交路径复用组件身份和事务，将查询待确认草稿、基准校验和写入放入同一组件锁。零条创建，一条覆盖并递增 revision，多条返回冲突并保留原记录。覆盖时重新计算候选、预览及选中项。重复 requestId 返回原提交结果。
- [ ] 草稿查询返回累计摘要 summary，限制 Unicode 字符数不超过 100。在 KnowledgeDraftPublisher 写历史版本前根据实际选中并应用的变更生成版本摘要。已有版本缺摘要由前端展示“暂无摘要”。
- [ ] 对照接口文档更新提交、列表和详情合同，并检查同组件首次并发提交、提交重放、旧预览确认、存量多草稿与部分确认。

### 2. 恢复预览与确认

- [ ] 在 KnowledgeMaintenanceApplicationServiceImpl 复用现有恢复差异计算，预览返回当前版本、目标版本和差异但不写待确认草稿。确认请求提供预览使用的当前版本 ID。在组件锁内重新校验当前版本和待确认草稿数量，存在草稿时拒绝恢复，版本变化时返回冲突供前端刷新预览。
- [ ] 更新 KnowledgeMaintenanceController 和 docs/component-knowledge-maintenance-api.md，记录预览与确认各自的请求、响应及冲突案例。

### 3. 前端

- [ ] 在 knowledgeMaintenance.ts 对齐接口类型。在 KnowledgeVersionStatus.tsx 的待确认与历史列表展示各自的摘要，接入恢复预览与确认。
- [ ] 在 KnowledgeMaintenancePanel.tsx 复用章节及正文渲染，仅在有差异的左侧章节标题后加红点。右侧差异项关联正文锚点，选择后展开对应章节再定位。删除项保留可定位的标记。
- [ ] 验收有草稿仍能预览、确认禁用并提供草稿入口。草稿应用后按新版本刷新差异并等待确认。检查新增、修改、删除都能跳转。

### 4. 已安装技能

- [ ] 对齐 MCP 的正式版、草稿列表、草稿详情和提交合同。直接修改 /Users/natasha/.codex/skills/component-knowledge-maintenance/SKILL.md 及 references/contracts 内对应文档。
- [ ] 明确读取当前正式版及待确认草稿、保留读到的人工修订、合并后提交完整累计差异及摘要、提交后读回核对。无正式版继续使用初始提交入口和最新 sourceRevision。多条草稿或读取失败时停止提交，基准过期时重新读取再合并。

### 交付检查

- [ ] 按后端规范执行跳过单元测试的质量 verify、目标编译及架构检查。按前端配置执行 lint 和类型检查。核对技能引用和 MCP 工具合同。报告实际命令、范围和结果。
