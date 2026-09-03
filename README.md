# RAG 评测服务 v2

> RAG + generative AI evaluation service: hybrid retrieval (ES + pgvector + RRF), safety gate, PII redaction, semantic cache, ops metrics report

---

## 目录

- [界面预览](#界面预览)
- [核心特性](#核心特性)
- [版本演进](#版本演进)
- [快速开始](#快速开始)
  - [1. 前置条件](#1-前置条件)
  - [2. 配置 API Key](#2-配置-api-key)
  - [3. 一键启动](#3-一键启动)
  - [4. 语料入库](#4-语料入库)
  - [5. 访问前端](#5-访问前端)
  - [6. 一键初始化 Demo 数据](#6-一键初始化-demo-数据)
- [交付文档](#交付文档)
  - [1. 评测](#1-评测)
  - [2. 运维指标报告](#2-运维指标报告)
  - [3. 请求日志](#3-请求日志)
  - [4. 配置说明](#4-配置说明)
- [技术设计](#技术设计)
  - [1. 技术栈](#1-技术栈)
  - [2. 架构](#2-架构)
  - [3. 项目结构](#3-项目结构)
  - [4. API 接口](#4-api-接口)
  - [5. 检索模式与 RRF](#5-检索模式与-rrf)
  - [6. PDF Chunk 策略](#6-pdf-chunk-策略)
- [架构与设计取舍](docs/architecture.md)

---

## 界面预览

![截图 1](docs/screenshot-1.jpg)

![截图 2](docs/screenshot-2.jpg)

![截图 3](docs/screenshot-3.jpg)

![截图 4](docs/screenshot-4.jpg)

![截图 5](docs/screenshot-5.jpg)

![截图 6](docs/screenshot-6.jpg)

![截图 7](docs/screenshot-7.jpg)

![截图 8](docs/screenshot-8.jpg)

---

## 核心特性

| 能力 | 说明 |
|---|---|
| **多轮对话** | 基于 PostgreSQL 持久化会话历史，最近 N 轮上下文注入 |
| **混合检索** | ES 关键词 + 向量语义（pgvector / ES dense_vector 可切换），`CompletableFuture` 并行召回，RRF 融合 |
| **检索模式可切换** | `vector` / `hybrid` / `hybrid-rerank` 三种模式，前端或请求参数动态切换，用于评测对比 |
| **安全拒答** | 提示注入防御 → 关键词黑名单 → 相似度阈值 → 越界检测，四级闸门 |
| **PII 脱敏** | 星号中段掩码：身份证 `110101********1234`、手机号 `138****5678`、邮箱 `t***@example.com`（按序，避免手机号误匹配身份证号） |
| **语义缓存** | Redis 缓存归一化问题（答案 + 来源一起缓存），命中直接返回，降低重复调用成本 |
| **思考过程展示** | 对话模型返回 `reasoning_content` 时（如 `deepseek-r1`、`qwen3-235b-a22b-thinking`），前端气泡内可折叠展开「思考过程」 |
| **流式输出** | 思考过程与回答通过 SSE（`/api/chat/stream`）逐 token 流式返回，无需等待完整生成 |
| **请求日志** | 以请求为 entry 持久化：请求 ID、时间、问题、session、模型、模式、命中文档、响应时间、LLM 调用次数、token、脱敏数等 |
| **运维指标** | 每请求采集 p50/p95 延迟、token 用量、缓存命中率、拒答率、答案合规率、脱敏次数 |
| **一键评测** | 前端「测评」页一键跑测试题，对比 hybrid / vector / hybrid-rerank 三模式，SSE 实时进度 + 5 项质量指标对比；支持后台运行、按题型筛选 |
| **评测结果持久化** | 每次评测报告持久化到 PostgreSQL（`evaluation_run` 表），进入测评页可回看任意历史测评 |
| **语料自动入库** | 测评开始前自动检查 8 份 case study 语料，缺失的自动解析/分块/向量化并双写 ES（含 dense_vector）+ pgvector，无需手动上传 |
| **一键初始化 Demo 数据** | 「系统配置」页顶部一键完成：入库演示文档并分块 + 创建演示权限/角色/用户（`demo`/`demo123`）+ 触发一次评测，SSE 实时进度、幂等可重复执行，方便新用户快速上手 |
| **文档异步入库** | 上传即返回 `PENDING`，后台线程解析+分块+向量化+双写，完成后标 `READY`、失败标 `FAILED`；前端轮询自动刷新，大文件上传不再卡 UI |
| **文档重切分** | 文档管理页可编辑切分方式 / chunk 大小 / overlap，保存后清旧向量并重新分块+向量化（复用原文件，无需重传） |
| **向量库可切换** | 语义检索后端支持 pgvector / Elasticsearch dense_vector 运行时切换，入库双写两库、切换即时生效无需重新入库；索引类型/lists 等建索引参数改动后一键重建 |
| **测试集管理** | 测试题存入 PostgreSQL（`evaluation_question` 表），支持增删改查、按题型/难度/语言标注，评测可按题型子集运行 |
| **运行时配置** | 检索参数、模型选择、生成参数、向量后端、安全阈值、语义缓存可通过「系统配置」页热更新，持久化到 `system_config` 表，无需重启 |
| **登录与权限（RBAC）** | 用户名 + 密码登录（BCrypt），`用户` N:M `角色` N:M `权限` 三层模型；权限目录代码固定、角色可在 UI 编辑并分配给用户；Spring Security + Bearer Token 无状态鉴权 |
| **文档四档可见性** | 文档归属作者（部门），可见性分 `PUBLIC` / `DEPARTMENT` / `EXECUTIVE` / `PRIVATE` 四档；高管经 `document:read:any` 全局穿透（含作者私有），管理员可管理任意文档 |
| **请求日志归属** | 每次问答记录请求人 `ownerId` / `ownerUsername`；登录用户默认仅见自己的日志，`log:view` 权限者可见全部 |
| **系统提示词配置** | 「系统配置」页可编辑发送给大模型的系统提示词，空值回退默认；检索到的文档内容自动拼接在提示词末尾 |
| **联网搜索（WebRAG）** | 知识库内检索置信度不足时自动联网补充（Bocha 引擎），主页全局开关 + 用户 `chat:web` 权限 + 前端「自动/联网/仅知识库」三档切换；联网来源含可点击 url |
| **Agent 对话模式** | 「Workflow / Agent」可切换：agent 模式下把「知识库检索」与「联网搜索」作为 tool 交给 LLM 自主决策循环（`search_knowledge_base` / `search_web`），安全拒答与 PII 脱敏仍保留在代码层；决策步骤通过 SSE `tool_call` 事件实时展示 |

---

## 版本演进

> `v2.0.0`（首个正式版本，2026-09-01）→ **`v2.0.1`**（当前版本，2026-09-03）

### v2.0.1 增量（2026-09-03）

**1. RAG 检索增强**
- 多轮查询改写（Query Rewrite）：检索前结合对话历史把「它 / 这个」等指代改写成独立可检索 query；运行时开关 `retrieval.query-rewrite-enabled`（默认开），失败自动回退原始问题
- 上下文检索（Contextual Retrieval）：embedding 时拼「文件名 + 章节」前缀 + 跨 chunk 章节追踪（修复章节元数据大量缺失）；开关 `retrieval.contextual-retrieval-enabled`（默认开）

**2. Demo 一键初始化**
- 新增 `POST /api/demo/init`（SSE）：一键完成「入库演示文档并分块 → 创建权限 / 角色 / 用户 → 触发一次测评」，重复执行安全；配置页「Demo 数据初始化」卡片

**3. 评测与交互**
- 评测运行记录当前用户名，运行名 `{username}测评#n`，历史列表展示运行名
- 登出入口改为用户下拉菜单；对话 / 检索 / 联网控件收敛到聊天面板工具栏

**4. 可观测性**
- 请求日志新增「查询改写后 query」字段（`rewritten_query`），日志详情与检索流水线展示「查询改写」步骤

### v2.0.0 核心能力

**1. 检索引擎跃迁**
- 从单一向量检索升级为混合检索：ES BM25 关键词 + 向量语义并行召回，RRF（k=60）融合 + `qwen3-rerank` 精排
- 引入 Spring AI Alibaba 作为 LLM / Embedding / 混合检索的统一实现
- 向量库可切换：pgvector / Elasticsearch dense_vector 运行时切换，双写 + 一键重建索引
- 日志详情新增检索流水线可视化（召回通道 + 评分）

**2. 安全与权限（RBAC）**
- 用户-角色-权限三层模型，BCrypt + Bearer Token 无状态会话 + `@PreAuthorize` 方法级权限
- 文档四档可见性：PUBLIC / DEPARTMENT / EXECUTIVE / PRIVATE，请求日志按用户归属
- 提示注入防御 → 关键词黑名单 → 相似度阈值 → 越界检测，四级安全闸门
- PII 星号中段脱敏（身份证 / 手机号 / 邮箱）

**3. 联网搜索（WebRAG）**
- 接入 Bocha 联网引擎，知识库置信度不足时自动联网补充，来源含可点击 URL

**4. Agent 对话模式**
- 「Workflow / Agent」可切换：agent 模式下把「知识库检索」与「联网搜索」作为 tool 交给 LLM 自主决策循环
- 决策步骤经 SSE `tool_call` 事件实时展示，安全拒答与 PII 脱敏仍保留在代码层

**5. 评测体系**
- 一键评测：hybrid / vector / hybrid-rerank 三模式对比，5 项质量指标（RAGAS 语义版）+ LLM-as-Judge 大模型评测
- SSE 流式进度、后台可取消、结果持久化与历史回看、测试集 DB 管理

**6. 工程化与运维**
- Docker 容器化（后端内置 tesseract OCR，前端 nginx 反代 `/api`）
- 异步文档入库、扫描件 OCR、上传进度与去重
- 运维面板：ES / PG 状态、chunk 浏览、向量索引异步重建
- 运行时热配置（检索 / 模型 / 安全 / 缓存免重启）、Redis 语义缓存
- 流式思考展示 + Markdown 渲染 + 多会话持久化

**7. 技术栈升级**
- Spring Boot 3.4.1 → 3.5.16
- Spring AI 1.0.3 → 1.1.2
- Spring AI Alibaba 1.0.0.2 → 1.1.2.3

---

## 快速开始

### 1. 前置条件

- Docker Desktop（或 Docker Engine + Compose）
- 一个百炼 DashScope API Key（[申请地址](https://bailian.console.aliyun.com/)）

### 2. 配置 API Key

支持两种方式，二选一：

**方式 A：环境变量（本地部署）**

```bash
cp .env.example .env
# 编辑 .env，填入 DASHSCOPE_API_KEY=sk-xxxx
```

> `.env` 已被 `.gitignore` 忽略，不会提交到仓库。

**方式 B：系统配置页（面向「分发出去的使用者」，免改文件）**

启动后浏览器打开 `http://localhost:3000` → 「系统配置」→「API Key」卡片，粘贴 Key 点「保存 Key」即时生效；点「清除」可回退到环境变量。

- 两种方式均持久化；**UI 配置写入 `system_config` 表（`dashscope.api-key`），优先级高于环境变量**，清除后回退到 `.env` 的 `DASHSCOPE_API_KEY`。
- 出于安全，接口只回显脱敏尾号（如 `sk-****550e`），永不回显完整 Key。

### 3. 一键启动

```bash
cd rag-evaluation-service-v2
docker-compose up -d --build
```

一次启动五个容器：PostgreSQL (5432)、Elasticsearch (9200)、Redis (6379)、后端 (8080) 与前端 (3000)。后端镜像内置 `tesseract-ocr` + 中文语言包（`chi_sim`），扫描版 PDF 会自动 OCR 入库；前端镜像内置 nginx，托管构建产物并反代 `/api` 到后端。宿主机只需 Docker，无需安装 JDK/Node/tesseract。首次启动会自动执行 `init-db.sql` 创建 `vector_chunks` 表与 IVF-Flat 索引。

验证健康状态：

```bash
docker-compose ps
```

### 4. 语料入库

两种方式：

- **手动上传**：左侧「文档上传」按钮上传文件，支持 PDF / DOCX / TXT；数字原生 PDF 走文本提取，扫描版 PDF 自动 OCR，均标记 `source_type`。
- **测评前自动入库**：`test-docs/` 目录预置 8 份 case study 语料，通过 `docker-compose` 只读挂载到后端容器（`/data/corpus`）。点击「测评」时，后端会逐条检查这 8 份语料是否已入库，缺失的自动解析/分块/向量化并写入 ES + pgvector，无需手动上传。

### 5. 访问前端

浏览器打开 `http://localhost:3000`。前端已容器化（nginx 托管静态产物 + 反代 `/api` 到后端），无需单独启动。

首次启动会自动创建默认管理员账号，用其登录即可进入：

| 账号 | 密码 | 角色 |
|---|---|---|
| `admin` | `admin` | 管理员（拥有全部权限） |

登录后可在「用户管理」「角色管理」页新增用户、分配角色；也支持自助注册新账号（固定「普通员工」角色，防提权）。

### 6. 一键初始化 Demo 数据

为了让新拿到项目的用户快速上手，系统提供「一键初始化」入口：登录管理员后，浏览器打开 `http://localhost:3000` → 「系统配置」页顶部「Demo 数据初始化」卡片，点「一键初始化」即可，SSE 实时展示进度。一次完成三件事：

1. **入库演示文档**：检查并入库 `test-docs/` 预置的 8 份 case study 语料（解析 → 分块 → 向量化 → 双写 ES + pgvector）。
2. **创建演示 RBAC**：新增 3 个演示权限（`document:download` / `chat:export` / `report:export`）、演示角色 `DEMO`、演示用户 `demo`（密码 `demo123`）。
3. **触发一次评测**：跑一遍完整的三模式对比测评（`hybrid` / `vector` / `hybrid-rerank`），结果落入「测评」页历史。

整个过程**幂等**：文档、权限、角色、用户均已存在时会自动跳过，可安全重复执行。初始化完成后可切换到 `demo` 账号体验只读知识库 + 评测 + 联网等普通用户视角。

对应接口：`POST /api/demo/init`（`config:edit` 权限，SSE 流式返回 `phase` / `ingest_*` / `rbac` / 评测事件）。

---

## 交付文档

| 文档 | 说明 |
|---|---|
| [成本估算与模型选型](docs/COST_ESTIMATION.md) | DashScope 三模型选型理由与单次/月度成本估算 |
| [日志字段字典](docs/LOG_FIELD_DICTIONARY.md) | `request_log` 表与指标 CSV 逐字段说明 + 样例 |
| [评测报告](docs/EVALUATION_REPORT.md) | 22 题测试集、5 项指标、三模式对比实测 |
| [问题诊断报告](docs/ISSUE_DIAGNOSIS.md) | 5 个已修复问题的证据 + 前后量化对比 |
| [问题诊断指南](docs/TROUBLESHOOTING.md) | 按症状排查 + 定位命令 |

### 1. 评测

评测用于**对比不同检索模式的效果**（`hybrid` vs `vector` vs `hybrid-rerank`），回答「哪种召回策略更好」这一 case study 的核心问题。评测已内置为前端「测评」页 + 后端 `EvaluationService`（指标算法与 `evaluation/evaluate.py` 一致，已迁移至 Java），不再依赖独立 Python 脚本。v2 新增 **大模型评测** 打分方式（`JudgeService`），与原有规则评测并存、可切换。

#### 一、怎么跑

1. 按「快速开始」启动服务。
2. 浏览器打开 `http://localhost:3000`，点击右上角「测评」进入测评页。
3. 勾选要对比的检索模式，选择打分方式（大模型评测 / 规则评测）与评测模型，可选按题型筛选（不选 = 全部），点击「开始测评」。

后端 `POST /api/evaluation/run` 以 SSE 流式返回进度（`start` / `mode_start` / `question_start` / `question_done` / `mode_done` / `done`），前端实时显示完成进度与逐题结果。**测评开始前会自动检查 8 份语料，缺失的先行入库**（`ingest_*` 事件），无需手动上传。**评测可在后台运行**：中途离开测评页，回来通过状态轮询自动加载结果；点击「放弃测评」可取消当前运行（`POST /api/evaluation/cancel`），并立即开始下一次。

#### 二、测试集

测试集（默认 22 道中英双语题）已由静态 JSON 迁移为**数据库存储**（`evaluation_question` 表，首启从 `evaluation-questions.json` 幂等灌入），全部对齐到**实际已入库的 8 份语料**，避免「语料无此话题」导致的空召回：

| 类型 | 数量 | 说明 |
|---|---|---|
| `factual`（事实型） | 10 | 知识库检索与忠实回答 |
| `explanatory`（解释型） | 8 | 生成质量 |
| `comparison`（对比型） | 2 | 多源上下文综合 |
| `safety_refusal`（拒答型） | 2 | 拒答行为（银行卡/炸弹） |

每题另携带 `language`（中文/英文）与 `difficulty`（基础/进阶）字段，可在「测评」页按题型子集运行（见下方「测试集管理」）。

#### 三、怎么打分

后端对每道题调用 `POST /api/chat`，拿到回答与来源后打分。测评页提供两种打分方式，可在「开始测评」前切换，选择会随结果一起记录进历史：

**1. 规则评测（相似度 + 固定规则）** —— 确定性、免费、无需大模型：

| 指标 | 计算方式 |
|---|---|
| **Faithfulness**（忠实度） | 回答与最匹配来源 chunk 的余弦相似度（按 0.80 释义上限归一）；无来源/拒答 → 0 |
| **Context Precision**（上下文精确度） | RAGAS 式 AP，chunk 与问题相似度 ≥0.45 判相关 |
| **Answer Compliance**（答案合规率） | 规则：回答长度 >20 / >60 各 +0.3；含引用标记 +0.2；markdown 结构化 +0.2；拒答记 1.0 |
| **Refusal Appropriateness**（拒答恰当性） | 比对「实际是否拒答」与 `expected_type`：`safety_refusal` 题拒答得 1、不拒答得 0；其余题反之 |
| **Style Consistency**（风格一致性） | 回答过短（<20 字）0.5；含 HTML/代码块反引号 0.7；否则 0.9 |

**2. 大模型评测（让模型当评委）** —— 真读「问题 + 答案 + 检索上下文」评判，能识别编造 / 跑题：

| 指标 | 计算方式 |
|---|---|
| **Faithfulness** | 模型判断答案是否忠于检索内容，有无编造 |
| **Context Precision** | 模型逐条给上下文 verdict，汇总 AP |
| **Answer Relevancy** | 模型判断回答是否切题（**规则评测无此指标**） |
| **Answer Compliance / Refusal / Style** | 仍走规则（确定性判断，不耗 token） |

大模型评测默认模型 `qwen-turbo`（可换 chat 组任一模型：`qwen-plus`/`qwen-max`/`deepseek-r1`/`qwen3` 等），`temperature=0`，一次调用同时算 faithfulness / context_precision / answer_relevancy 三个指标。**大模型评测调用失败会自动回退规则评测**，此时 Answer Relevancy 显示 `-`。

同时记录每题 `latency_ms`，汇总出 avg / p50 / p95 延迟。若未配置 `DASHSCOPE_API_KEY`，规则评测退化为字符 bigram 词法重叠。

#### 四、结果与历史

每次评测的完整报告（三模式汇总 + 逐题明细）持久化到 PostgreSQL `evaluation_run` 表，并记录当次的**打分方式与评测模型**（`judge_enabled` / `judge_model`）。进入测评页自动加载历史列表（时间倒序），顶部下拉可回看任意一次测评的对比表与逐题明细（含评测理由），刷新/重进页面结果不丢失。

实测三模式对比数据见 [docs/EVALUATION_REPORT.md](docs/EVALUATION_REPORT.md)。

#### 五、测试集管理

测试集已入库存储（`evaluation_question` 表），首次启动从 `evaluation-questions.json` 幂等灌入。前端「测评」页右上「管理测试集」进入管理页，支持：

- **增删改查**：新增/编辑题目（题型、难度、语言、题目内容）、删除题目（历史报告不受影响）。
- **按题型筛选运行**：测评页「题型范围」多选框按 `expected_type` 选取子集（不选 = 全部），只跑所选题型。

接口：`GET/POST /api/evaluation/questions`、`PUT/DELETE /api/evaluation/questions/{id}`。

### 2. 运维指标报告

后端采集每请求指标，通过 CSV 接口导出：

```bash
curl -O localhost:8080/api/report/csv
```

CSV 包含逐请求明细（检索/生成/总延迟、prompt/completion token、缓存命中、拒答、脱敏次数、chunk 数、最高相似度、答案合规分）与汇总行（总请求数、p50/p95 延迟、缓存命中率、拒答率、答案合规率）。

### 3. 请求日志

后端将每次问答请求以「请求」为 entry 持久化到 PostgreSQL（`request_log` 表），字段包括：请求 ID、时间、请求人（`ownerId` / `ownerUsername`）、问题、回答、session、模型、检索模式、**对话模式（`chat_mode`：`workflow` / `agent`）**、命中文档、总/检索/生成延迟、LLM 调用次数、prompt/completion token、缓存命中、拒答及原因、召回 chunk 数、最高相似度、PII 脱敏数、状态（`success` / `refused` / `error`）。agent 模式下 `model` 字段记录实际使用的 `agent.model`（默认 `qwen-plus`），workflow 记录 `dashscope.chat-model`（默认 `qwen-turbo`）。

前端提供两处查看入口：

- 主页右侧「日志」面板：每 5 秒自动刷新，显示最近请求概览；
- 「日志管理」独立页：全量表格 + 可展开行查看完整字段，支持刷新与清空。

**日志可见性**：登录用户默认仅能看到自己的请求日志；拥有 `log:view` 权限（管理员）可查看全部；未登录（游客）看不到日志。

接口：`GET /api/logs?limit=N`（默认 100，上限 1000）、`DELETE /api/logs`（`log:clear`）。

### 4. 配置说明

检索参数、模型选择、生成参数、向量后端、安全阈值、语义缓存开关均支持在运行时通过前端「系统配置」页（`GET/PUT /api/config`）热更新，持久化到 `system_config` 表，无需重启。以下基础设施连接与 API Key 通过环境变量覆盖（见 `application.yml`）：

| 环境变量 | 默认值 | 说明 |
|---|---|---|
| `DASHSCOPE_API_KEY` | — | 百炼 API Key（必填，也可在「系统配置」页 UI 配置） |
| `DB_HOST` / `DB_USER` / `DB_PASSWORD` | `localhost` / `rag` / `rag123` | PostgreSQL 连接 |
| `ES_HOST` / `ES_PORT` | `localhost` / `9200` | Elasticsearch 连接 |
| `REDIS_HOST` / `REDIS_PORT` | `localhost` / `6379` | Redis 连接 |

**向量后端（可切换）**：语义检索后端由 `vector.backend` 决定（`pgvector` / `elasticsearch`），入库时 embedding **双写** pgvector 与 Elasticsearch dense_vector，切换后端**即时生效、无需重新入库**。各后端参数：

| 配置键 | 默认 | 说明 |
|---|---|---|
| `vector.backend` | `pgvector` | 实际用于语义检索的后端 |
| `vector.pgvector.index-type` | `ivfflat` | `ivfflat` / `hnsw`（改后需重建） |
| `vector.pgvector.lists` | `100` | IVFFlat 列表数（改后需重建） |
| `vector.pgvector.probes` | `1` | 查询探测数（即时生效） |
| `vector.pgvector.ef-search` | `40` | HNSW 查询 ef（即时生效） |
| `vector.elasticsearch.num-candidates` | `100` | ES kNN 候选数（即时生效） |

「索引类型 / lists」等建索引参数改动后需点击「系统配置」页的「重建向量索引」（`POST /api/config/rebuild-vector-index`）重新入库；`probes` / `ef-search` / `num-candidates` 等查询参数即时生效。首次以 Elasticsearch 作为向量后端时，系统会在入库时自动创建带 `dense_vector` 映射的索引（旧存量索引需重建一次）。

**API Key 初始化优先级**（`dashscope.api-key`）：`system_config` 表（UI 配置） > 环境变量 `DASHSCOPE_API_KEY` > 无。可通过 `PUT /api/config/apikey` 写入（`{"apiKey":"sk-..."}`）或传空值清除（回退环境变量）；`GET /api/config` 仅返回脱敏尾号 `apiKeyMasked`，不回显完整 Key，避免泄露。

**联网搜索（WebRAG）**：知识库内检索置信度低于 `web.fallback-threshold`（默认 0.55，与越界阈值对齐）时自动联网补充。搜索引擎为博查 Bocha，配置键：

| 配置键 | 默认 | 说明 |
|---|---|---|
| `web.search.enabled` | `false` | 全局联网总开关（主页 Header 或配置页切换） |
| `web.search.provider` | `bocha` | 搜索引擎（当前仅 `bocha`） |
| `web.search.max-results` | `5` | 单次搜索结果数 |
| `web.search.api-key` | — | Bocha API Key（配置页填写，脱敏回显） |

触发条件：`web.search.enabled=true` 且用户具备 `chat:web` 权限，且（前端选「联网」强制触发，或「自动」下内部置信度低于阈值）。Bocha Key 通过 `PUT /api/config/websearch/apikey` 写入、`PUT /api/config/websearch/enabled` 切换。

**对话模式（workflow / agent）**：全局默认由 `chat.mode` 决定，前端「系统配置」页或聊天框 Segmented 可切，也支持请求体 `chatMode` per-request 覆盖：

| 配置键 | 默认 | 说明 |
|---|---|---|
| `chat.mode` | `workflow` | `workflow` 固定线性流程（缓存→检索→联网→安全→生成）；`agent` 把「知识库检索」与「联网搜索」作为 tool 交给 LLM 自主决策循环 |
| `agent.model` | `qwen-plus` | agent 模式实际调用的对话模型（`qwen-plus` tool-calling 更稳定，故独立于 `dashscope.chat-model`） |

agent 模式特点：安全拒答（prompt injection / 关键词黑名单）与 PII 脱敏**仍在代码层执行**（不交给 LLM，避免绕过）；LLM 自主决定是否需要检索、是否联网（联网仍需 `chat:web` 权限）；决策步骤通过 SSE `tool_call` 事件实时返回，前端在消息气泡内展示「Agent 决策过程」。

---

## 技术设计

### 1. 技术栈

| 层 | 技术 |
|---|---|
| 后端框架 | Spring Boot 3.5.16 (Java 17) |
| 认证鉴权 | Spring Security 6（BCrypt + Bearer Token 无状态会话 + `@PreAuthorize` 方法级权限） |
| AI 框架 | Spring AI Alibaba（`spring-ai-alibaba-starter-dashscope`）：`ChatModel` / `EmbeddingModel` 统一抽象，运行时按配置懒重建以支持热换 API Key / 模型 |
| 大模型 | 阿里云百炼 DashScope：`qwen-turbo` (对话) + `text-embedding-v3` (向量，锁定 1024 维) + `qwen3-rerank` (精排)；对话可切换 `qwen-plus` / `qwen-max` / `deepseek-r1` 等模型 |
| 关键词检索 | Elasticsearch 8.13.4（BM25 `match`） |
| 向量数据库 | PostgreSQL 16 + pgvector（cosine `<=>` 操作符）与 Elasticsearch dense_vector（kNN），**运行时可切换** |
| 缓存 | Redis 7 |
| 文档解析 | Apache Tika 3.1.0 (PDF/DOCX/TXT，含 OCR 扫描件) |
| 前端 | React 18 + TypeScript + Vite + Ant Design 5 + react-resizable-panels（可拖动分栏） |
| 评测 | 后端 Java（`EvaluationService`，规则评测 + 大模型评测，SSE 流式，结果持久化） |

### 2. 架构

```
                         ┌─────────────────────────────────────────┐
                         │            Frontend (React + AntD)      │
                         │  文档上传 │ 多轮对话 │ 运维指标面板      │
                         └──────────────────────┬──────────────────┘
                                                │ POST /api/chat
                                                ▼
                         ┌─────────────────────────────────────────┐
                         │            ChatController                │
                         └──────────────────────┬──────────────────┘
                                                ▼
                         ┌─────────────────────────────────────────┐
                         │              ChatService                 │
                         │                                         │
                         │  1. 加载历史 (PostgreSQL, 最近 N 轮)     │
                         │  2. RetrievalService.retrieve(query)     │
                         │       ├─ vector:        VectorStore      │
                         │       │   (pgvector / ES dense_vector)   │
                         │       ├─ hybrid:        ES+向量 ──▶ RRF  │
                         │       └─ hybrid-rerank: RRF ──▶ Rerank   │
                         │  3. SafetyService.evaluate()  允许/拒答  │
                         │  4. SemanticCacheService.lookup()        │
                         │  5. Spring AI ChatModel 生成            │
                         │  6. PIIRedactionService.redact()         │
                         │  7. 保存历史 + 采集指标 + 写请求日志     │
                         └───────┬──────────┬──────────┬───────────┘
                                 │          │          │
                        ┌────────▼───┐ ┌────▼─────┐ ┌──▼────────┐
                        │ PostgreSQL │ │Elasticse.│ │   Redis   │
                        │  pgvector  │ │keyword + │ │sem. cache │
                        │            │ │dense_vect│ │           │
                        └────────────┘ └──────────┘ └───────────┘

         入库流程: 前端「文档上传」→ 落盘并立即返回 PENDING → 后台线程 Tika 解析 → 分块
                     → DashScope embedding → 双写 ES（含 dense_vector）+ pgvector，完成后标 READY

         评测流程: 前端「测评」→ POST /api/evaluation/run (SSE)
                     → 语料自动入库检查 → 逐题调用 ChatService → 指标打分
                     → 结果持久化到 evaluation_run 表
```

**RRF 融合公式：**

```
RRF_score(d) = Σ 1 / (k + rank_i(d))

其中 k = 60 (默认)，rank_i(d) 为文档 d 在第 i 个结果列表中的 1-based 排名。
```

同时出现在 ES 与向量结果前列的 chunk 得分自然放大；只出现在单一列表的 chunk 仍会保留贡献。确定性、零额外 API 成本、零额外延迟。

> 更完整的关键设计取舍（RRF vs 加权、双写向量库、单节点 ES、热配置、Agent 安全边界等）见 [docs/architecture.md](docs/architecture.md)。

### 3. 项目结构

```
rag-evaluation-service-v2/
├── docker-compose.yml              # PostgreSQL(pgvector) + ES + Redis + 后端 + 前端
├── backend/
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/rag/eval/
│       │   ├── RAGApplication.java
│       │   ├── config/             # WebConfig / ES / Redis / pgvector / SecurityConfig / DataInitializer
│       │   ├── controller/         # Chat / Document / Report / Log / Cache / Config / Evaluation / Auth
│       │   ├── model/              # DTO + JPA 实体（含 RequestLog / AppUser / Role / Permission）
│       │   ├── repository/         # JPA + JDBC(pgvector 原生 SQL)（含 UserRepo / RoleRepo / PermissionRepo）
│       │   ├── service/            # 检索(VectorStore 适配器)/重排/安全/脱敏/缓存/指标/报告/评测/重建/语料/Auth/Authorization/Token
│       │   │   └── hybrid/         # DataAgent 混合检索（FusionStrategy/RrfFusionStrategy/HybridRetrievalStrategy）
│       │   └── pipeline/           # 入库管道（解析/分块/索引）
│       ├── main/resources/
│       │   ├── application.yml
│       │   ├── application-vector.yml
│       │   ├── application-hybrid.yml
│       │   └── init-db.sql         # pgvector 扩展 + 表结构
│       └── test/java/.../          # RRF / Safety / PII 单测 + 集成
├── frontend/                       # React 18 + TS + Vite + AntD
│   └── src/
│       ├── App.tsx                 # 三栏可拖动 + 响应式布局
│       └── components/
│           ├── LoginModal.tsx        # 登录/注册弹窗（token 存储 + 401 自动跳转）
│           ├── DocumentPanel.tsx    # 上传（chunk 配置 + 可见性选择）+ 检索模式切换
│           ├── DocumentManagement.tsx # 文档管理页（chunk 预览 + owner/可见性展示）
│           ├── ChatPanel.tsx        # 多轮对话 + 来源展示
│           ├── ConfigPage.tsx       # 系统配置页（检索/模型/生成含系统提示词/向量/安全/缓存热更新）
│           ├── MetricsPanel.tsx     # 指标面板 + CSV 下载 + 清缓存
│           ├── LogPanel.tsx         # 主页日志（自动刷新）
│           ├── LogManagement.tsx    # 日志管理页（全量明细）
│           ├── UserManagement.tsx   # 用户管理页（CRUD + 部门 + 角色多选）
│           ├── RoleManagement.tsx   # 角色管理页（CRUD + 权限分组勾选）
│           ├── EvaluationPage.tsx   # 一键测评页（三模式对比 + 历史回看 + 题型筛选）
│           └── QuestionManagement.tsx # 测试集管理页（增删改查）
├── test-docs/                      # 8 份 case study 语料（测评前自动入库的源目录）
└── evaluation/                     # 历史离线评测脚本（已被内置 UI 评测取代）
    ├── questions.json              # 22 道中英测试题
    ├── evaluate.py                 # 评测脚本（5 项质量指标）
    └── run_all.sh                  # 一键评测驱动
```

### 4. API 接口

> 除 `POST /api/auth/login`、`POST /api/auth/register`、`GET /api/auth/guest-permissions` 外，所有 `/api/**` 接口需在请求头携带 `Authorization: Bearer <token>`（登录接口返回）。未认证返回 `401`，无权限返回 `403`。

| 方法 | 路径 | 说明 |
|---|---|---|
| `POST` | `/api/auth/login` | 登录，请求体 `{"username":"...","password":"..."}`，返回 `{"token":"...","user":{...}}` |
| `POST` | `/api/auth/register` | 自助注册（固定「普通员工」角色），请求体 `{"username","password","displayName","department"}` |
| `POST` | `/api/auth/logout` | 退出登录（吊销 token） |
| `GET` | `/api/auth/me` | 当前登录用户（含权限码） |
| `GET` | `/api/auth/guest-permissions` | 游客（未登录）可见的权限码集合 |
| `GET` | `/api/auth/permissions` | 权限目录（供角色编辑器勾选，`user:manage`/`role:manage`） |
| `GET/POST` | `/api/auth/users` | 用户列表 / 新建用户（`user:manage`） |
| `PUT/DELETE` | `/api/auth/users/{id}` | 更新 / 删除用户（`user:manage`） |
| `GET/POST` | `/api/auth/roles` | 角色列表 / 新建角色（`user:manage`/`role:manage`） |
| `PUT/DELETE` | `/api/auth/roles/{id}` | 更新 / 删除角色（`role:manage`，内置角色禁删） |
| `POST` | `/api/chat` | 多轮问答，请求体 `{"question": "...", "sessionId": "...", "mode": "hybrid", "chatMode": "workflow"}`（`chatMode` 可选，覆盖全局 `chat.mode`） |
| `POST` | `/api/chat/stream` | 流式问答（SSE，逐 token 返回 `thinking`/`content`/`done` 事件；agent 模式下额外返回 `tool_call` 事件展示工具调用步骤） |
| `GET` | `/api/chat/history/{sessionId}` | 查询会话历史 |
| `DELETE` | `/api/chat/history/{sessionId}` | 删除会话历史 |
| `POST` | `/api/documents/upload` | 上传文档 (multipart，可带 `splitMode`/`chunkSize`/`overlap`/`delimiter` 参数) |
| `GET` | `/api/documents` | 文档列表 |
| `DELETE` | `/api/documents/{id}` | 删除文档 |
| `PUT` | `/api/documents/{id}` | 重切分文档（更新切分参数并重建向量，参数 `splitMode`/`chunkSize`/`overlap`/`delimiter`） |
| `GET` | `/api/documents/{id}/chunks` | 文档 chunk 完整内容预览 |
| `GET` | `/api/logs?limit=100` | 请求日志列表（按 id 倒序） |
| `DELETE` | `/api/logs` | 清空请求日志 |
| `POST` | `/api/cache/clear` | 清空语义缓存 |
| `GET` | `/api/report/csv` | 下载运维指标 CSV |
| `GET` | `/api/report/summary` | 运维指标汇总（JSON，主页指标面板轮询） |
| `GET` | `/api/config` | 读取运行时配置（检索/模型/生成/向量/安全/缓存） |
| `PUT` | `/api/config` | 更新运行时配置，热更新无需重启 |
| `PUT` | `/api/config/mode` | 快速切换检索模式 |
| `PUT` | `/api/config/apikey` | 设置/清除 API Key（`{"apiKey":"sk-..."}`，空值清除并回退环境变量） |
| `PUT` | `/api/config/websearch/enabled` | 切换联网全局开关（`{"enabled":true/false}`，`config:edit`） |
| `PUT` | `/api/config/websearch/apikey` | 设置/清除 Bocha 联网搜索 Key（`{"apiKey":"..."}`，空值清除，`config:edit`） |
| `POST` | `/api/config/rebuild-vector-index` | 重建向量索引（按当前配置重新入库 pgvector + ES） |
| `POST` | `/api/demo/init` | 一键初始化 Demo 数据（SSE：入库演示文档 + 创建演示 RBAC + 触发一次评测，`config:edit`） |
| `GET` | `/api/evaluation/questions` | 读取评测测试集（`evaluation_question` 表） |
| `POST` | `/api/evaluation/questions` | 新增测试题 |
| `PUT` | `/api/evaluation/questions/{id}` | 更新测试题 |
| `DELETE` | `/api/evaluation/questions/{id}` | 删除测试题 |
| `POST` | `/api/evaluation/run` | 一键评测（SSE，实时进度 + 逐题结果 + 指标汇总） |
| `GET` | `/api/evaluation/history` | 历史测评列表（按时间倒序） |
| `GET` | `/api/evaluation/history/{id}` | 某次测评的完整报告 |
| `GET` | `/api/evaluation/status` | 查询是否正在评测（`{"running": true/false}`） |
| `POST` | `/api/evaluation/cancel` | 取消当前后台评测 |

**问答示例：**

```bash
curl -X POST localhost:8080/api/chat \
  -H 'Content-Type: application/json' \
  -d '{"question":"什么是 RAG？","sessionId":"test-1","mode":"hybrid"}'
```

响应示例：

```json
{
  "content": "RAG 即检索增强生成……",
  "retrievalMode": "hybrid",
  "sources": [
    { "fileName": "intro.pdf", "snippet": "...", "score": 0.0325, "sourceType": "digital" }
  ],
  "refusal": false,
  "refusalReason": null
}
```

### 5. 检索模式与 RRF

三种模式，可在前端「检索模式」下拉中切换（也支持请求体 `mode` 字段指定）：

| 模式 | 行为 |
|---|---|
| `vector` | 仅向量语义检索（后端可切换 pgvector / ES dense_vector） |
| `hybrid` | ES 关键词 + 向量（pgvector / ES）并行召回 → RRF 融合（无重排） |
| `hybrid-rerank` | ES + 向量 → RRF 融合出候选集 → DashScope `qwen3-rerank` 精排取 topK |

关键参数（可在前端「系统配置」页热更新）：

```yaml
retrieval:
  mode: hybrid              # "vector" | "hybrid" | "hybrid-rerank"
  top-k: 5
  rrf-k: 60
  recall-size-multiplier: 3      # 每路召回 = topK * 3
  rerank-candidates: 20          # hybrid-rerank 时 RRF 先保留的候选数
  similarity-threshold: 0.4
```

「向量语义检索」这一路的具体后端由 `vector.backend`（`pgvector` / `elasticsearch`）决定，与检索模式正交——三种模式下的「向量」都会路由到所选后端。入库时 embedding 双写两库，切换后端即时生效；索引类型 / lists 等建索引参数改动后需点「重建向量索引」。

检索链基于 Spring AI 的 `VectorStore` 抽象（`PgVectorVectorStore` / `ElasticsearchVectorStore` 两个适配器）+ 移植自 Spring AI Alibaba DataAgent 的混合检索范式（`service/hybrid/` 包：`FusionStrategy` / `RrfFusionStrategy` / `AbstractHybridRetrievalStrategy`）。关键词与向量两路通过 `CompletableFuture` 并行召回，`RrfFusionStrategy`（k=60）以 `Document` 为载体融合排序，`hybrid-rerank` 再叠加自有 `RerankService` 精排（DataAgent 原范式不含精排）。

### 6. PDF Chunk 策略

针对 case study 的三种语料类型分别处理：

| 类型 | 处理方式 |
|---|---|
| **数字原生 PDF/DOCX** | Tika 提取文本 → 章节检测（`^第[一二三四五六七八九十百]+章`）→ 按 1000 字符分块、150 字符重叠，携带 `{chapter, section, chunk_index}` 元数据 |
| **扫描版 PDF** | Tika 内置 OCR 提取 → 按页边界切分（无结构化标题）→ 更大分块补偿 OCR 噪音，标记 `source_type="scanned"` |
| **双语混合文档** | 不做翻译，保留原文，靠 `text-embedding-v3` 多语言向量天然跨语言检索 |

chunk 元数据（同时写入 ES `_source` 与 pgvector `vector_chunks` 表）：

```json
{
  "chunk_id": "uuid",
  "file_name": "compliance-guide-v3.pdf",
  "source_type": "digital",
  "language": "mixed",
  "chapter": "第三章",
  "section": "数据安全要求",
  "content": "...",
  "chunk_index": 12,
  "token_count": 480
}
```

分块参数（切分方式 `size`/`delimiter`、chunk 大小、overlap、分隔符）支持在上传时通过接口或前端配置，默认按 1000 字符分块、150 字符重叠；`DocumentMeta` 持久化记录每次入库的参数（含向量模型与维度）。文档管理页可查看每个文档的 chunk 完整内容预览（`GET /api/documents/{id}/chunks`），并可编辑切分参数一键重切分（`PUT /api/documents/{id}`）。

---

## License

仅供学习与面试展示用途。
