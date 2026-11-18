# SDD ledger — plan: docs/superpowers/plans/2026-11-17-component-knowledge-maintenance.md

## 执行边界

- 2026-11-17 用户指示：先不修正第二次评审四项问题，直接实施。设计与计划保留，已知限制不得隐瞒。
- Ruling: 用户 AGENTS 禁止未授权新增/运行单元和 E2E，优先于 TDD 技能；不写测试代码，不运行测试，执行必要静态检查、编译及授权内浏览器核对。未验证范围交付时明确。
- Ruling: 不自动提交 Git、不执行外部 DDL；旧工作区改动不清理或覆盖。
- 用户已确认直接在当前工作区实施；后端 codex/component-knowledge，前端 codex/component-detail-api-integration，保持原有未提交改动。

## 进度

- Task 1: 接口契约及错误枚举已写，后续随实现保持一致；已执行本批 Java verify。
- Task 2: 四表 DDL、领域/PO/Mapper/XML/转换已写，Java verify 与 Java17编译通过；DDL未执行，锁与SQL行为未验证。
- Task 3–4: 固定清单读取、共享引用索引、模型上下文、草稿/发布/恢复服务已写，最终门禁中；数据库行为未验证。
- Task 5–7: 现有全屏详情维护抽屉、表单、原位高亮、选择预览、历史恢复入口已写，typecheck/lint通过；真实发布未联调。
- Task 8: 基线快照与V1/V2发布代码已写；实际迁移未执行。
- Task 9: MCP标准库适配器与技能源已写；--describe成功，py_compile通过；宿主未接入，未做真实会话提交。
- Task 10: 纳管后读取componentDetails与复制消费已接入；尚未迁移，不移除旧meta字段及生成规则。
- Task 11: 执行中；静态检查、构建、架构和浏览器结果待最终记录。

## 执行裁决补充

- Ruling: 主动触发技能先交付仓库源，不自动安装到个人目录或修改宿主设置；成本是工具尚未自动出现在会话中。
- Ruling: 维护默认关闭，配置 knowledge.maintenance.enabled 后才切换正常查询；未建表时保持既有只读路径，避免影响现有应用。
- Ruling: 现有持久化类字段按同一显式MaintenanceRequest组织，各用例独立校验；输出由受校验JSON树承载，以避免为首版大量同字段DTO；正式线协议仍由接口文档固定。成本是后续字段变化需同步显式解析。
- Ruling: Python工具未发现已配置ruff/pylint，只执行语法和工具声明检查，不将其称为静态检查通过。


## 已知未修正评审项

1. BASELINE-only 对象的检索/编辑可用性。
2. 历史接口展示仍受当前前端 meta 过滤。
3. 独立对象全文读取与适用关系版本的上下文界限。
4. 基准变化后的完整三方重新比较契约。

## 组件详情版本可见性收尾（2026-11-17）

- 详情顶部将版本状态和操作合并为一栏。历史、待确认及知识维护复用既有详情和抽屉。
- 真实验证库中 MetricsStrip 当前 V3。确认复制 V2 时剪贴板包含该版本专有验证文字，刷新草稿 URL 后进入全屏审核，维护入口可打开。1920×1080、1440×960 均为单行，无按钮溢出。控制台无错误，未发布草稿。
- 前端 format:check、typecheck、lint、现有单测 4/4、meta:check、build 均通过。Chromium 108 未实测。
