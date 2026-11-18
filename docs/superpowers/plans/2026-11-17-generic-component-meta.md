# 通用前端组件元数据实施计划

> 执行使用 superpowers:executing-plans，或按用户选择使用 superpowers:subagent-driven-development。

**目标**　通过统一源码标记生成组件注册表，由组件知识技能生成待审核知识。

**架构**　TypeScript 语法树提取 JSX/TSX 标记，生成与校验共用相同的序列化逻辑。PlanReport 工作台和组件知识技能读取同一注册表。

**技术栈**　Node.js、TypeScript、React JSX/TSX。

**设计**　[通用前端组件元数据](../specs/2026-11-17-generic-component-meta-design.md)

## 约束与核对重点

统一使用 data-component-id 和 data-component-name。保留现有组件 ID。检查动态和单边声明、空值、跨文件重复 ID、注册表过期、抽屉和逻辑组件的知识定位。按项目规则执行静态检查和编译，单元测试或 E2E 以用户另行授权为准。

## 1. 生成和校验

- [ ] 复用前端 scripts/generate-registry.mjs 入口，接收扫描根目录和输出路径。用项目已有 TypeScript 解析器从同一元素读取静态属性，并报告异常声明所在文件和位置。
- [ ] 输出 version 和 components，components 以 ID 为键，值为 id、name、source。按 ID 排序，路径相对仓库。让 scripts/check-component-meta.mjs 共用生成逻辑并比较现有 JSON。
- [ ] 用临时目录的最小 JSX/TSX 源码验证不同扫描路径、稳定输出、无效声明及过期注册表诊断。

## 2. PlanReport 迁移

- [ ] 逐个核对旧清单 ID、显示名和源码入口。迁移源码标记，给动态抽屉的具体区块声明静态 ID 和名称，核对布局的辅助区块及无 DOM 逻辑组件的定位方式。
- [ ] 修改 package.json 的生成和校验入口，并迁移 src/dev/DevWorkbench 的选择器、注册表读取和展示。核对树、样式和状态字段的消费方，保留有来源可验证的能力。
- [ ] 重新生成 src/pages/PlanReport/components.registry.json，核对现有 ID 的覆盖及源码路径。确认无消费后删除旧清单及报表专属协作生成链路，同步更新组件清单文档。
- [ ] 执行 npm run meta:check、npm run lint、npm run build，核对组件选择、名称和抽屉定位。

## 3. 组件知识技能

- [ ] 更新 component-knowledge-maintenance/SKILL.md 及实际引用的说明。用 components 中的 id、name、source 读取源码，移除对 metaPath 和旧业务字段的依赖。
- [ ] 候选知识基于源码与装配位置、会话和存量知识，沿用 ID 与审核流程。对照一份生成后的注册表核对技能中的定位步骤和字段引用。
