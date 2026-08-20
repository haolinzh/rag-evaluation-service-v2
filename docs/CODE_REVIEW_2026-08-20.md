# 代码评审记录（向量后端可切换特性）

> 评审日期：2026-08-20
> 范围：向量库可切换（pgvector / Elasticsearch 双写 + 重建入口）+ 生成参数（temperature/top_p/max_tokens）
> 结论：共发现 10 个问题，本次修复 4 个（#1 / #2 / #3 / #5），其余留待后续评估。

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

### #4 维度三处不一致（换 embedding 模型会坏） —— 未修复

- 位置：`init-db.sql` / `ConfigController.EMBEDDING_DIMENSION` / `ElasticsearchService.embeddingDimension`
- 现象：pgvector 列 `vector(1024)` 固定、`ConfigController` 常量 1024、ES 侧按模型动态（v2=1536 / v4=2048）。切到 v2/v4 时 ES 建新维度索引、pgvector 仍 1024，`?::vector` 强转报错；且「重建」不改 pgvector 列类型，救不了。前端「切换后需重新入库」的警告对 pgvector 是误导。
- 状态：需产品决策是否支持多维度 embedding；否则应在配置层禁止选择维度 ≠ 1024 的模型。

### #5 `RebuildService` 无原子性、无错误处理 —— 已修复

- 位置：`RebuildService.rebuildVectorIndex`
- 现象：先 `truncate()` + `recreateIndex()` 清空两库，再逐文档重灌。中途异常（解析失败、embedding 失败）会中断且原数据已被 truncate 无法回滚；`fileStorage.load` 返回 null 时 `continue` 静默跳过该文档，重建完才发现少文档；`documentCount` 仍按 `docs.size()` 报。
- 修复：改为「先准备后重建」——先把所有文档 load + parse + split 全部在内存里跑一遍，任一失败（含文件缺失）立即抛异常、不破坏任何数据；全部就绪后才 truncate/recreate/重灌。

---

## 中优先级

### #6 `ensureVectorIndex` 并发竞态 —— 未修复

- 位置：`ElasticsearchService.ensureVectorIndex`
- 现象：「先 exists 再 create」两步非原子，首启并发上传时第二个 `createIndex` 抛 `resource_already_exists_exception` → 上传失败。
- 建议：捕获 already-exists 异常，或加锁/幂等建索引。

### #7 `SET LOCAL` + `@Transactional` 可能不生效 —— 未修复（需验证）

- 位置：`VectorChunkRepo.similaritySearch`
- 现象：项目为 JPA，默认事务管理器 `JpaTransactionManager` 不一定把 `JdbcTemplate` 的 DataSource 连接 enlist 进事务，`SET LOCAL ivfflat.probes / hnsw.ef_search` 可能跑在另一 autocommit 连接上（报错或静默不生效）。因只影响召回质量、不影响能否运行，功能测试难发现。
- 建议：改用显式 `TransactionTemplate` + `DataSourceTransactionManager`，或同一连接内 `SET LOCAL` + 查询。

### #8 `generate()` 里 temperature 与 top_p 互斥 —— 未修复

- 位置：`DashScopeService.generate`
- 现象：`if (topP < 1.0) 只发 top_p else 只发 temperature`。同时设 `temperature=0.3` + `top_p=0.8` 时 temperature 被丢弃、走 DashScope 默认。
- 建议：与 UI 文案确认意图；通常两者应可同时下发。

---

## 低优先级

### #9 `RebuildResult.chunkCount` 可能虚高 —— 未修复

- 位置：`RebuildService` / `IndexBuilder.buildIndex`
- 现象：`chunkCount` 按 `splitAndEnrich` 结果计数，而 `buildIndex` 实际可能少写（`embedBatch` 返回条数不足时 `j < embeddings.size()` 截断）。

### #10 `parseEmbedding` 空向量边界 —— 未修复

- 位置：`ElasticsearchVectorStore.parseEmbedding`
- 现象：embed 失败返回 `List.of()` 时走 `knnSearch(空)` → ES 报错 → 空列表，不崩但无提示。
