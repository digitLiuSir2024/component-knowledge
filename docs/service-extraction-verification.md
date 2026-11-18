# 服务剥离验收

已完成迁移：各层 knowledge/modelcontext、知识 Mapper、SQL、知识接口文档与历史验证材料、维护技能和 MCP。原统计服务保留统计实现；共享响应与异常工具复制到新服务。

## 实际检查

- 新服务：`bash scripts/java-quality.sh verify src/main/java/`，118 个 Java 文件；退出码 0。
- 新服务：`bash scripts/backend-gate.sh src/main/java/`，静态检查、格式检查、Maven clean compile、架构检查全部通过。
- 统计服务：`bash scripts/java-quality.sh verify src/main/java/com/cmbchina/infrastructure/common/ResponseEnum.java`；`bash scripts/backend-gate.sh src/main/java/`，退出码均为 0。
- 前端：`npm run lint`、`npm run build`（含 typecheck）通过。构建存在已有的包大小提示，未放宽阈值。
- `python3 scripts/service-extraction-validation/verify.py`：新服务直连及前端代理的组件知识、模型上下文、维护上下文均返回 code=200；统计 filters 返回 code=200。
- 原服务知识接口返回 10002，确认知识路由已移除。
- 浏览器实际打开前端组件树并选中“核心指标总览”，组件关联、接口与代码位置可见。
- 项目 MCP 子进程初始化成功，发现 9 个工具，真实只读知识调用成功，覆盖清单与摘要校验成功；全局技能符号链接及 Markdown 相对引用有效，Python AST 检查通过。

- 前端 `npm test`：4 项通过；`npm run format:check`：27 个本次未修改的 TSX 文件格式检查失败，未自动格式化无关代码。因此全项检查尚未全绿。

结果见 [连通性记录](../scripts/service-extraction-validation/results.json)。验收未执行业务数据写入或发布。

## 运行与生效

知识服务 8081、统计服务 8080、前端 5173。Codex 配置及原技能已保留 pre-extraction 备份。新建 MCP 连接使用项目源码；当前聊天已建立的旧 MCP 连接不会因配置修改自动重连，需要在 Codex 重连 MCP 或开启新会话。已使用新配置等效启动的真实 MCP 进程完成协议与后端调用验证。

生产 nginx 已更新部署示例，未操作远程部署。hook 配置已复制，未自动信任；本次质量门禁手动执行。
