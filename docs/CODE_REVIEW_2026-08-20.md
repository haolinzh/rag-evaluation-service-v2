# 代码评审记录（向量后端可切换特性）

> 评审日期：2026-08-20
> 范围：向量库可切换（pgvector / Elasticsearch 双写 + 重建入口）+ 生成参数（temperature/top_p/max_tokens）
> 结论：共发现 10 个问题，已全部修复（#1–#10）。

---

## 高优先级（会导致静默出错 / 数据丢失）

### #1 ES kNN `num_candidates < k` 时静默返回空结果 —— 已修复

- 位置：`ElasticsearchService.knnSearch` + `RetrievalService.retrieve`
- 现象：ES 要求 `num_candidates ≥ k`。hybrid 召回 `recallSize = max(topK*3, 30)`，`num_candidates` 默认 100。当 `topK ≥ 34`（34×3=102>100，前端允许到 50）时，ES kNN 抛 `num_candidates must be >= k`，被 catch 后返回空列表——切到 ES 后端 + 高 topK 静默退化为无向量召回，pgvector 无此约束。
- 修复：`knnSearch` 内部 `effectiveNumCandidates = max(numCandidates, topK)`。

### #2 `indexToES` 吞掉所有 ES 异常（双写不对称） —— 已修复

- 位置：`IndexBuilder.indexToES`
- 现象：ES 侧写入失败（维度不匹配、映射冲突、ES 宕机）时仅 `System.err.println`，不抛。pgvector 照常写成功、上传报成功，ES 却缺数据——之后切到 ES 才发现内容不全，且无告警。
- 修复：改为 `log.error` + 抛出 `RuntimeException`，使 ES 写入失败可被上游感知，不再静默丢 ES 数据。

### #3 `knnSearch` 吞掉所有异常 → 缺 dense_vector 映射时静默空 —— 已修复

- 位置：`ElasticsearchService.knnSearch`
- 现象：存量部署（旧动态映射索引没有 `embedding` 字段）切到 `elasticsearch` 时，kNN 抛 400，被吞成空列表：`vector` 模式直接无结果，hybrid 退化成纯关键词，运行时无任何报错信号。
- 修复：改为 `log.error`（含异常明细与「需重建」提示），仍返回空列表以保持检索链路不硬崩，但问题可诊断。

### #4 维度三处不一致（换 embedding 模型会坏） —— 已修复

- 位置：`init-db.sql` / `ConfigController.EMBEDDING_DIMENSION` / `ElasticsearchService.embeddingDimension`
- 现象：pgvector 列 `vector(1024)` 固定、`ConfigController` 常量 1024、ES 侧按模型动态（v2=1536 / v4=2048）。切到 v2/v4 时 ES 建新维度索引、pgvector 仍 1024，`?::vector` 强转报错；且「重建」不改 pgvector 列类型，救不了。前端「切换后需重新入库」的警告对 pgvector 是误导。
- 修复：决策为**不支持多维度 embedding**。`ConfigController.MODEL_OPTIONS` 移除 v2（1536）/v4（2048），仅保留 1024 维的 v1/v3；`ElasticsearchService.embeddingDimension()` 固定返回 1024，不再按模型 switch。`validate` 里 `isAllowedModel` 自动拒绝 v2/v4，封死 API 侧多维度入口。

### #5 `RebuildService` 无原子性、无错误处理 —— 已修复

- 位置：`RebuildService.rebuildVectorIndex`
- 现象：先 `truncate()` + `recreateIndex()` 清空两库，再逐文档重灌。中途异常（解析失败、embedding 失败）会中断且原数据已被 truncate 无法回滚；`fileStorage.load` 返回 null 时 `continue` 静默跳过该文档，重建完才发现少文档；`documentCount` 仍按 `docs.size()` 报。
- 修复：改为「先准备后重建」——先把所有文档 load + parse + split 全部在内存里跑一遍，任一失败（含文件缺失）立即抛异常、不破坏任何数据；全部就绪后才 truncate/recreate/重灌。

---

## 中优先级

### #6 `ensureVectorIndex` 并发竞态 —— 已修复

- 位置：`ElasticsearchService.ensureVectorIndex`
- 现象：「先 exists 再 create」两步非原子，首启并发上传时第二个 `createIndex` 抛 `resource_already_exists_exception` → 上传失败。
- 修复：捕获 `ElasticsearchException`，当 `error().type()` 为 `resource_already_exists_exception` 时视为索引已建好、直接返回；其余异常照常抛出。

### #7 `SET LOCAL` + `@Transactional` 可能不生效 —— 已修复

- 位置：`VectorChunkRepo.similaritySearch`
- 现象：项目为 JPA，默认事务管理器 `JpaTransactionManager` 不一定把 `JdbcTemplate` 的 DataSource 连接 enlist 进事务，`SET LOCAL ivfflat.probes / hnsw.ef_search` 可能跑在另一 autocommit 连接上（报错或静默不生效）。因只影响召回质量、不影响能否运行，功能测试难发现。
- 修复：移除 `@Transactional`，注入 `DataSource` 构造 `TransactionTemplate(new DataSourceTransactionManager(dataSource))`，把 `SET LOCAL` 与查询包进同一事务、同一连接；事务结束连接归还，`SET LOCAL` 不泄漏到连接池。

### #8 `generate()` 里 temperature 与 top_p 互斥 —— 已修复

- 位置：`DashScopeService.generate` / `chatStream`
- 现象：`if (topP < 1.0) 只发 top_p else 只发 temperature`。同时设 `temperature=0.3` + `top_p=0.8` 时 temperature 被丢弃、走 DashScope 默认。
- 修复：两处均改为无条件下发 `temperature`，`top_p` 仅在 `< 1.0` 时额外下发，二者可同时生效。

---

## 低优先级

### #9 `RebuildResult.chunkCount` 可能虚高 —— 已修复

- 位置：`RebuildService` / `IndexBuilder.embed`
- 现象：`chunkCount` 按 `splitAndEnrich` 结果计数，而 `buildIndex` 实际可能少写（`embedBatch` 返回条数不足时 `j < embeddings.size()` 截断）。
- 修复：`IndexBuilder.embed` 末尾校验 `embeddings.size() == chunks.size()`，不一致即抛异常（杜绝静默丢数据）；`RebuildService` 改按 `doc.embeddings().size()` 统计，与实际写入数一致。

### #10 `parseEmbedding` 空向量边界 —— 已修复

- 位置：`ElasticsearchVectorStore.search`
- 现象：embed 失败返回 `List.of()` 时走 `knnSearch(空)` → ES 报错 → 空列表，不崩但无提示。
- 修复：`search` 里解析向量后判空，为空则 `log.warn` 并直接返回空列表，避免无意义的 ES kNN 调用与报错。
