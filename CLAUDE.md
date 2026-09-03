# CLAUDE.md

RAG 知识库问答 + 评测系统的项目级指南。改代码前先读这里，能省掉大量重复探索。

## 一句话定位

混合检索（ES BM25 + 向量语义 + RRF 融合 + qwen3-rerank 精排）的知识库问答系统，带 Agent/Workflow 双对话模式、四级安全闸门、PII 脱敏、RBAC 权限、语义缓存、一键三模式评测。

## 构建工具链（关键）

后端 **必须用 JDK 17**，不能用宿主机默认的 JDK 26（Lombok 注解处理会失败）。所有 Maven 命令统一加：

```bash
JAVA_HOME=/Users/zhanghaolin/Library/Java/JavaVirtualMachines/temurin-17.0.19/Contents/Home mvn ...
```

- 依赖已缓存时用 `-o`（离线）；首次拉新依赖（如新增 starter）时去掉 `-o`。

## 常用命令

```bash
# 后端
cd backend && JAVA_HOME=…/temurin-17.0.19/Contents/Home mvn -o compile        # 编译
cd backend && JAVA_HOME=…/temurin-17.0.19/Contents/Home mvn -o test           # 测试（集成测试需 Docker 运行）
cd backend && JAVA_HOME=…/temurin-17.0.19/Contents/Home mvn -o clean package -DskipTests  # 打包

# 前端
cd frontend && npm run build      # tsc -b && vite build（类型检查 + 打包）
cd frontend && npm run dev        # 开发模式

# Docker
docker compose up -d --build backend frontend   # 重建后端+前端
docker compose ps                                # 服务状态
docker compose logs -f backend                   # 后端日志
```

## 技术栈

- **后端**：Spring Boot 3.5.16 + Java 17 + Maven + JPA（`ddl-auto: update`）+ Spring Security + Redis + elasticsearch-java 8.13.4 + Spring AI Alibaba（DashScope）
- **前端**：React 18 + TypeScript + Vite + Ant Design 5 + react-resizable-panels（hash 路由）
- **容器**（docker-compose）：postgres(pgvector:pg16) / elasticsearch(8.13.4) / redis(7-alpine) / backend(:8080) / frontend(:3000，nginx 反代 `/api`)

## 架构

后端分层（`com.rag.eval`）：
- `config/` — 含 `DocumentIngestConfig`（worker 线程池 + embedding 信号量）、`DataInitializer`、`SecurityConfig`
- `controller/` — REST + SSE（chat / evaluation / demo / ops / config / auth / documents…）
- `service/` + `service/hybrid/` — 检索链；`DocumentService`、`ChatService`、`OpsService` 等
- `repository/`、`model/`

前端（`frontend/src`）：
- `App.tsx` — 面板布局 + hash 路由（`VIEW_HASHES`）
- `api.ts` — axios（baseURL `/api`，Bearer token 拦截器）、`types.ts`
- `components/` — 首页三面板是 `DocumentPanel`/`ChatPanel`/`MetricsPanel`+`LogPanel`；其余为独立路由页

关键机制：
- **文档入库**：多 worker 并发 + 持久化任务（状态机 QUEUED→PROCESSING→READY/FAILED，条件 UPDATE 原子 claim），worker 数由 `document.ingest.worker-count` 配置
- **检索**：hybrid / vector / hybrid-rerank 三模式
- **可观测性**：Actuator（`/actuator/prometheus`）+ `TraceIdFilter`（X-Request-Id 贯穿）
- **集成测试**：Testcontainers（`AbstractIntegrationTest` 基类 + 3 个测试），跑之前本地 Docker 必须运行

## 约定

**分支**：只在 `feat` 分支开发，push 到 `origin/feat`，PR 合入 `main`（仓库无 master）。

**提交信息风格**：`feat:` / `fix:` / `refactor:` / `test:` / `docs:` / `chore:` / `ci:` / `release: vX.Y.Z 版本升级`。Claude 生成的提交带 `Co-Authored-By: Claude Opus 4.7` 尾注。

**发布版本 bump（缺一不可）**：
- `backend/pom.xml` 的 `<version>`
- `frontend/package.json` 的 `version`
- `frontend/package-lock.json` —— **只改根包的 `version`（第 3 行 + `"":` 块内那处），勿动依赖里的 2.0.x**
- `README.md`（版本箭头行 + 「版本演进」新增一节）
- `frontend/src/components/AboutPage.tsx`（hero tag、`extra` tag、`版本` 描述、`changelog` 数组）

## 安全红线（必须遵守）

- API key 只存在于 DB `system_config` 表与 `.env`（已 gitignore），**严禁提交 / 打印完整 key**。
- `application.yml` / `application-test.yml` 只用 `${DASHSCOPE_API_KEY}` 占位或 `sk-test-dummy-key`。
- UI 对 key 做掩码显示。
- 不直接 commit / push 到 `main`。

## 环境变量（`application.yml` 读取）

`DASHSCOPE_API_KEY` / `DB_HOST`,`DB_USER`,`DB_PASSWORD` / `ES_HOST`,`ES_PORT` / `REDIS_HOST`,`REDIS_PORT` / `UPLOAD_DIR` / `CORPUS_DIR`

## 更多参考

`docs/architecture.md`、`docs/TROUBLESHOOTING.md`、`docs/LOG_FIELD_DICTIONARY.md`、`README.md`
