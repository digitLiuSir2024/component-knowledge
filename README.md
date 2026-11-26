# Component Knowledge

独立组件知识与模型上下文服务，Spring Boot 2.7.2、Java 8、Maven、MyBatis。默认端口 8081，统计服务仍为 8080。

## 启动

沿用原 MYSQL_HOST、MYSQL_PORT、MYSQL_DATABASE（默认 zhituo）、MYSQL_USER、MYSQL_PASSWORD，使用现有知识表，不自动执行 SQL。KNOWLEDGE_MAINTENANCE_ENABLED 默认 true。

```sh
export JAVA_HOME=/Users/natasha/data/code/api-config/.local-run/jdk8/zulu8.98.0.107-ca-jdk8.0.512-macosx_aarch64/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
mvn spring-boot:run
```

## 接口

契约保持不变，仅服务地址变为 http://127.0.0.1:8081：
- [组件知识](docs/component-knowledge-api.md)
- [模型上下文](docs/model-context-api.md)
- [知识维护](docs/component-knowledge-maintenance-api.md)

知识 SQL 与历史迁移说明位于 database，禁止在现有数据库重跑初始化脚本。

## 技能与 MCP

源码统一维护在 skills/component-knowledge-maintenance。Codex 全局同名技能目录通过符号链接引用此目录；MCP 使用 python3 启动该目录 server.py，环境变量 KNOWLEDGE_API_BASE=http://127.0.0.1:8081。修改技能后新会话读取新内容；修改 MCP 代码或配置后须重新连接 MCP，已有进程不会自动加载源码。

## 验证

```sh
bash scripts/java-quality.sh verify src/main/java/
bash scripts/backend-gate.sh src/main/java/
mvn test
python3 -m unittest discover -s scripts/tests
```

交互数据迁移：先 `python3 scripts/migrate-interaction-seed.py --dry-run` 检查计划，再不带 `--publish` 创建草稿，确认后加 `--publish` 发布。进度见 `scripts/interaction-migration-mapping.json`。

基础表运行时 CRUD：`POST /api/v1/knowledge/maintenance/legacy/{get,list,upsert,delete}`，entity 为 14 张基础表枚举名或表名，字段受白名单约束。交互发布会在同一事务回写两张交互基础表，可用 `KNOWLEDGE_LEGACY_INTERACTION_SYNC_ENABLED=false` 关闭。

前端设置 VITE_KNOWLEDGE_PROXY_TARGET=http://127.0.0.1:8081，统计地址仍通过 VITE_API_PROXY_TARGET 配置。生产部署分别转发 /zhituo/api/v1/knowledge/ 和 /zhituo/api/v1/model-context/ 至新服务，剥去 /zhituo 前缀。详见前端部署说明。

hook 配置已迁入，但新目录的信任与实际 hook 执行尚待验证，不以配置存在视为启用。
