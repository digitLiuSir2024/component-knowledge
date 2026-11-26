# 会话组件快照

从用户会话和项目配置确认 `systemCode`、`projectCode`、`pagePath`。后端登记路径由项目或用户提供。在查询基准时核对该页面与组件。多个页面允许使用相同的组件 ID。

从项目清单或源码建立以下中立结构。任意框架、清单文件名和目录均可使用。覆盖整页时纳入已确认的全部组件。覆盖子树时可使用已确认的页面组件清单，再通过 root 选出子树。

```json
{
  "pageIdentity": {"systemCode": "S", "projectCode": "P", "pagePath": "/page.html"},
  "components": {
    "MetricsStrip": {
      "id": "MetricsStrip",
      "name": "核心指标总览",
      "source": "src/MetricsStrip.tsx",
      "assemblyParents": ["page"]
    }
  }
}
```

`source` 是取证位置。工具只读取请求中的快照数据。`assemblyParents` 支持共享装配，`page` 是保留的页面根标识。接口线索可通过 `apiDependencies` 附带，实际契约沿源码和知识服务核对。

将快照传给 `knowledge_coverage_scope` 的 `registrySnapshot`，并原样放进 `coveragePlan.registrySnapshot`。coveragePlan 包含页面身份、范围和快照；`coverageDigest` 是该计划的 SHA-256 摘要。仅兼容旧调用：在未传 `registrySnapshot` 时必须显式设置 `KNOWLEDGE_PAGE_BINDINGS`，不再读取用户目录下的默认绑定文件。

知识读取和草稿提交依赖实现知识维护契约的服务，由 MCP 运行环境配置 `KNOWLEDGE_API_BASE`。
