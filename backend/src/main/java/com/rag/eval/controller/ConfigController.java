package com.rag.eval.controller;

import com.rag.eval.model.SystemConfigDto;
import com.rag.eval.service.ConfigService;
import com.rag.eval.service.RebuildService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/config")
public class ConfigController {

    private static final String K_MODE = "retrieval.mode";
    private static final String K_TOP_K = "retrieval.top-k";
    private static final String K_RECALL = "retrieval.recall-size-multiplier";
    private static final String K_RRF_K = "retrieval.rrf-k";
    private static final String K_RERANK_CAND = "retrieval.rerank-candidates";
    private static final String K_SIM_THRESHOLD = "retrieval.similarity-threshold";
    private static final String K_CHAT_MODEL = "dashscope.chat-model";
    private static final String K_EMB_MODEL = "dashscope.embedding-model";
    private static final String K_RERANK_MODEL = "dashscope.rerank-model";
    private static final String K_MIN_SIM = "safety.min-similarity";
    private static final String K_OUT_OF_SCOPE_ENABLED = "safety.enable-out-of-scope-check";
    private static final String K_OUT_OF_SCOPE_THRESHOLD = "safety.out-of-scope-threshold";
    private static final String K_FORBIDDEN = "safety.forbidden-keywords";
    private static final String K_CACHE_ENABLED = "cache.semantic.enabled";
    private static final String K_CACHE_TTL = "cache.semantic.ttl-seconds";
    private static final String K_API_KEY = "dashscope.api-key";
    private static final String K_JUDGE_ENABLED = "evaluation.judge-enabled";
    private static final String K_JUDGE_MODEL = "dashscope.judge-model";
    private static final String K_JUDGE_TEMPERATURE = "evaluation.judge-temperature";
    private static final String K_GEN_TEMPERATURE = "generation.temperature";
    private static final String K_GEN_TOP_P = "generation.top-p";
    private static final String K_GEN_MAX_TOKENS = "generation.max-tokens";
    private static final String K_VECTOR_BACKEND = "vector.backend";
    private static final String K_PG_INDEX_TYPE = "vector.pgvector.index-type";
    private static final String K_PG_LISTS = "vector.pgvector.lists";
    private static final String K_PG_PROBES = "vector.pgvector.probes";
    private static final String K_PG_EF_SEARCH = "vector.pgvector.ef-search";
    private static final String K_ES_NUM_CANDIDATES = "vector.elasticsearch.num-candidates";
    private static final String K_WEB_ENABLED = "web.search.enabled";
    private static final String K_WEB_PROVIDER = "web.search.provider";
    private static final String K_WEB_MAX_RESULTS = "web.search.max-results";
    private static final String K_WEB_API_KEY = "web.search.api-key";
    private static final String K_CHAT_MODE = "chat.mode";
    private static final String K_AGENT_MODEL = "agent.model";

    private static final int EMBEDDING_DIMENSION = 1024;

    private static final List<SystemConfigDto.ModelOption> MODEL_OPTIONS = List.of(
        new SystemConfigDto.ModelOption("chat", "qwen-turbo", "qwen-turbo", null),
        new SystemConfigDto.ModelOption("chat", "qwen-plus", "qwen-plus", null),
        new SystemConfigDto.ModelOption("chat", "qwen-max", "qwen-max", null),
        new SystemConfigDto.ModelOption("chat", "qwen-max-longcontext", "qwen-max-longcontext", null),
        new SystemConfigDto.ModelOption("chat", "qwen2.5-72b-instruct", "qwen2.5-72b-instruct", null),
        new SystemConfigDto.ModelOption("chat", "deepseek-r1", "DeepSeek R1 (深度思考)", null),
        new SystemConfigDto.ModelOption("chat", "qwen3-235b-a22b-thinking-2507", "Qwen3 235B Thinking (深度思考)", null),
        new SystemConfigDto.ModelOption("embedding", "text-embedding-v3", "text-embedding-v3", 1024),
        new SystemConfigDto.ModelOption("rerank", "qwen3-rerank", "qwen3-rerank", null),
        new SystemConfigDto.ModelOption("rerank", "gte-rerank", "gte-rerank", null)
    );

    private static final Set<String> MODES = Set.of("vector", "hybrid", "hybrid-rerank");
    private static final Set<String> CHAT_MODES = Set.of("workflow", "agent");
    private static final Set<String> VECTOR_BACKENDS = Set.of("pgvector", "elasticsearch");
    private static final Set<String> PG_INDEX_TYPES = Set.of("ivfflat", "hnsw");
    private static final Set<String> WEB_PROVIDERS = Set.of("bocha");

    private final ConfigService config;
    private final RebuildService rebuildService;

    public ConfigController(ConfigService config, RebuildService rebuildService) {
        this.config = config;
        this.rebuildService = rebuildService;
    }

    @GetMapping
    public SystemConfigDto get() {
        return buildDto();
    }

    @PreAuthorize("hasAuthority('config:edit')")
    @PutMapping
    public ResponseEntity<?> update(@RequestBody SystemConfigDto dto) {
        String error = validate(dto);
        if (error != null) {
            return ResponseEntity.badRequest().body(Map.of("error", error));
        }

        SystemConfigDto.Retrieval r = dto.retrieval();
        SystemConfigDto.Models m = dto.models();
        SystemConfigDto.Safety s = dto.safety();
        SystemConfigDto.Cache c = dto.cache();
        SystemConfigDto.Generation g = dto.generation();
        SystemConfigDto.Vector v = dto.vector();

        Map<String, String> changes = new LinkedHashMap<>();
        changes.put(K_MODE, r.mode());
        changes.put(K_TOP_K, String.valueOf(r.topK()));
        changes.put(K_RECALL, String.valueOf(r.recallSizeMultiplier()));
        changes.put(K_RRF_K, String.valueOf(r.rrfK()));
        changes.put(K_RERANK_CAND, String.valueOf(r.rerankCandidates()));
        changes.put(K_SIM_THRESHOLD, String.valueOf(r.similarityThreshold()));
        changes.put(K_CHAT_MODEL, m.chat());
        changes.put(K_EMB_MODEL, m.embedding());
        changes.put(K_RERANK_MODEL, m.rerank());
        changes.put(K_MIN_SIM, String.valueOf(s.minSimilarity()));
        changes.put(K_OUT_OF_SCOPE_ENABLED, String.valueOf(s.enableOutOfScopeCheck()));
        changes.put(K_OUT_OF_SCOPE_THRESHOLD, String.valueOf(s.outOfScopeThreshold()));
        changes.put(K_FORBIDDEN, s.forbiddenKeywords());
        changes.put(K_CACHE_ENABLED, String.valueOf(c.enabled()));
        changes.put(K_CACHE_TTL, String.valueOf(c.ttlSeconds()));
        changes.put(K_GEN_TEMPERATURE, String.valueOf(g.temperature()));
        changes.put(K_GEN_TOP_P, String.valueOf(g.topP()));
        changes.put(K_GEN_MAX_TOKENS, String.valueOf(g.maxTokens()));
        String sysPrompt = g.systemPrompt();
        changes.put(ConfigService.KEY_SYSTEM_PROMPT,
            (sysPrompt == null || sysPrompt.isBlank()) ? ConfigService.DEFAULT_SYSTEM_PROMPT : sysPrompt);
        if (v != null) {
            changes.put(K_VECTOR_BACKEND, v.backend());
            changes.put(K_PG_INDEX_TYPE, v.pgvector().indexType());
            changes.put(K_PG_LISTS, String.valueOf(v.pgvector().lists()));
            changes.put(K_PG_PROBES, String.valueOf(v.pgvector().probes()));
            changes.put(K_PG_EF_SEARCH, String.valueOf(v.pgvector().efSearch()));
            changes.put(K_ES_NUM_CANDIDATES, String.valueOf(v.elasticsearch().numCandidates()));
        }
        if (dto.judge() != null) {
            changes.put(K_JUDGE_ENABLED, String.valueOf(dto.judge().enabled()));
            changes.put(K_JUDGE_MODEL, dto.judge().model());
            changes.put(K_JUDGE_TEMPERATURE, String.valueOf(dto.judge().temperature()));
        }
        if (dto.webSearch() != null) {
            changes.put(K_WEB_ENABLED, String.valueOf(dto.webSearch().enabled()));
            changes.put(K_WEB_PROVIDER, dto.webSearch().provider());
            changes.put(K_WEB_MAX_RESULTS, String.valueOf(dto.webSearch().maxResults()));
        }
        if (dto.chatMode() != null && !dto.chatMode().isBlank()) {
            changes.put(K_CHAT_MODE, dto.chatMode());
        }
        if (dto.agentModel() != null && !dto.agentModel().isBlank()) {
            changes.put(K_AGENT_MODEL, dto.agentModel());
        }

        config.putAll(changes);
        return ResponseEntity.ok(buildDto());
    }

    @PreAuthorize("hasAuthority('config:edit')")
    @PutMapping("/mode")
    public ResponseEntity<?> updateMode(@RequestBody Map<String, String> body) {
        String mode = body == null ? null : body.get("mode");
        if (mode == null || !MODES.contains(mode)) {
            return ResponseEntity.badRequest().body(Map.of("error", "非法检索模式: " + mode));
        }
        config.put(K_MODE, mode);
        return ResponseEntity.ok(buildDto());
    }

    @PreAuthorize("hasAuthority('config:edit')")
    @PutMapping("/apikey")
    public ResponseEntity<?> updateApiKey(@RequestBody(required = false) Map<String, String> body) {
        String apiKey = body == null ? null : body.get("apiKey");
        if (apiKey == null || apiKey.isBlank()) {
            config.reset(K_API_KEY);
        } else {
            config.put(K_API_KEY, apiKey.trim());
        }
        return ResponseEntity.ok(buildDto());
    }

    @PreAuthorize("hasAuthority('config:edit')")
    @PutMapping("/websearch/enabled")
    public ResponseEntity<?> updateWebSearchEnabled(@RequestBody(required = false) Map<String, Object> body) {
        boolean enabled = body != null && Boolean.TRUE.equals(body.get("enabled"));
        config.put(K_WEB_ENABLED, String.valueOf(enabled));
        return ResponseEntity.ok(buildDto());
    }

    @PreAuthorize("hasAuthority('config:edit')")
    @PutMapping("/websearch/apikey")
    public ResponseEntity<?> updateWebApiKey(@RequestBody(required = false) Map<String, String> body) {
        String apiKey = body == null ? null : body.get("apiKey");
        if (apiKey == null || apiKey.isBlank()) {
            config.reset(K_WEB_API_KEY);
        } else {
            config.put(K_WEB_API_KEY, apiKey.trim());
        }
        return ResponseEntity.ok(buildDto());
    }

    @PreAuthorize("hasAuthority('config:edit')")
    @PostMapping("/rebuild-vector-index")
    public ResponseEntity<?> rebuildVectorIndex() {
        try {
            return ResponseEntity.ok(rebuildService.startRebuild());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", e.getMessage()));
        }
    }

    @PreAuthorize("hasAuthority('config:edit')")
    @PostMapping("/rebuild-pg-index")
    public ResponseEntity<?> rebuildPgIndex() {
        try {
            return ResponseEntity.ok(rebuildService.rebuildPgIndex());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "重建 PG 索引失败: " + e.getMessage()));
        }
    }

    @GetMapping("/rebuild-vector-index/status")
    public RebuildService.RebuildStatus rebuildStatus() {
        return rebuildService.getStatus();
    }

    private SystemConfigDto buildDto() {
        return new SystemConfigDto(
            new SystemConfigDto.Retrieval(
                config.get(K_MODE, "hybrid"),
                config.getInt(K_TOP_K, 5),
                config.getInt(K_RECALL, 3),
                config.getInt(K_RRF_K, 60),
                config.getInt(K_RERANK_CAND, 20),
                config.getDouble(K_SIM_THRESHOLD, 0.4)),
            new SystemConfigDto.Models(
                config.get(K_CHAT_MODEL, "qwen-turbo"),
                config.get(K_EMB_MODEL, "text-embedding-v3"),
                config.get(K_RERANK_MODEL, "qwen3-rerank")),
            new SystemConfigDto.Safety(
                config.getDouble(K_MIN_SIM, 0.4),
                config.getBool(K_OUT_OF_SCOPE_ENABLED, true),
                config.getDouble(K_OUT_OF_SCOPE_THRESHOLD, 0.55),
                config.get(K_FORBIDDEN, "")),
            new SystemConfigDto.Cache(
                config.getBool(K_CACHE_ENABLED, true),
                config.getInt(K_CACHE_TTL, 3600)),
            new SystemConfigDto.Judge(
                config.getBool(K_JUDGE_ENABLED, true),
                config.get(K_JUDGE_MODEL, "qwen-turbo"),
                config.getDouble(K_JUDGE_TEMPERATURE, 0.0)),
            new SystemConfigDto.Generation(
                config.getDouble(K_GEN_TEMPERATURE, 0.3),
                config.getDouble(K_GEN_TOP_P, 1.0),
                config.getInt(K_GEN_MAX_TOKENS, 0),
                config.get(ConfigService.KEY_SYSTEM_PROMPT, ConfigService.DEFAULT_SYSTEM_PROMPT)),
            new SystemConfigDto.Vector(
                config.get(K_VECTOR_BACKEND, "pgvector"),
                new SystemConfigDto.Pgvector(
                    config.get(K_PG_INDEX_TYPE, "ivfflat"),
                    config.getInt(K_PG_LISTS, 100),
                    config.getInt(K_PG_PROBES, 1),
                    config.getInt(K_PG_EF_SEARCH, 40)),
                new SystemConfigDto.Elasticsearch(
                    config.getInt(K_ES_NUM_CANDIDATES, 100))),
            MODEL_OPTIONS,
            EMBEDDING_DIMENSION,
            maskApiKey(config.get(K_API_KEY, "")),
            new SystemConfigDto.WebSearch(
                config.getBool(K_WEB_ENABLED, false),
                config.get(K_WEB_PROVIDER, "bocha"),
                config.getInt(K_WEB_MAX_RESULTS, 5)),
            maskApiKey(config.get(K_WEB_API_KEY, "")),
            config.get(K_CHAT_MODE, "workflow"),
            config.get(K_AGENT_MODEL, "qwen-plus"));
    }

    private String maskApiKey(String key) {
        if (key == null || key.isBlank()) return null;
        if (key.length() <= 8) return key.substring(0, 1) + "****";
        return key.substring(0, 3) + "****" + key.substring(key.length() - 4);
    }

    private String validate(SystemConfigDto dto) {
        if (dto == null || dto.retrieval() == null || dto.models() == null
                || dto.safety() == null || dto.cache() == null || dto.judge() == null
                || dto.generation() == null) {
            return "配置不完整";
        }
        SystemConfigDto.Retrieval r = dto.retrieval();
        if (!MODES.contains(r.mode())) return "非法检索模式: " + r.mode();
        if (r.topK() <= 0 || r.recallSizeMultiplier() <= 0 || r.rrfK() <= 0 || r.rerankCandidates() <= 0) {
            return "topK / recallMultiplier / rrfK / rerankCandidates 必须为正数";
        }
        if (!inRange(r.similarityThreshold())) return "similarityThreshold 必须在 [0,1]";

        SystemConfigDto.Safety s = dto.safety();
        if (!inRange(s.minSimilarity()) || !inRange(s.outOfScopeThreshold())) {
            return "安全阈值必须在 [0,1]";
        }

        SystemConfigDto.Cache c = dto.cache();
        if (c.ttlSeconds() <= 0) return "缓存 TTL 必须为正数";

        SystemConfigDto.Generation g = dto.generation();
        if (g.temperature() < 0 || g.temperature() > 2) return "temperature 必须在 [0,2]";
        if (g.topP() <= 0 || g.topP() > 1) return "topP 必须在 (0,1]";
        if (g.maxTokens() < 0) return "maxTokens 必须 >= 0（0 表示不限制）";
        if (dto.judge().temperature() < 0 || dto.judge().temperature() > 2) return "评测 temperature 必须在 [0,2]";

        SystemConfigDto.Vector v = dto.vector();
        if (v != null) {
            if (!VECTOR_BACKENDS.contains(v.backend())) return "非法向量后端: " + v.backend();
            if (v.pgvector() == null || v.elasticsearch() == null) return "向量配置不完整";
            if (!PG_INDEX_TYPES.contains(v.pgvector().indexType())) return "非法 pgvector 索引类型: " + v.pgvector().indexType();
            if (v.pgvector().lists() <= 0) return "pgvector lists 必须为正数";
            if (v.pgvector().probes() <= 0) return "pgvector probes 必须为正数";
            if (v.pgvector().efSearch() <= 0) return "pgvector efSearch 必须为正数";
            if (v.elasticsearch().numCandidates() <= 0) return "ES numCandidates 必须为正数";
        }

        if (!isAllowedModel("chat", dto.models().chat())) return "不支持的对话模型: " + dto.models().chat();
        if (!isAllowedModel("embedding", dto.models().embedding())) return "不支持的向量模型: " + dto.models().embedding();
        if (!isAllowedModel("rerank", dto.models().rerank())) return "不支持的重排模型: " + dto.models().rerank();
        if (!isAllowedModel("chat", dto.judge().model())) return "不支持的评测模型: " + dto.judge().model();
        if (dto.webSearch() != null) {
            if (!WEB_PROVIDERS.contains(dto.webSearch().provider())) {
                return "非法联网搜索引擎: " + dto.webSearch().provider();
            }
            if (dto.webSearch().maxResults() <= 0) return "联网搜索结果数必须为正数";
        }
        if (dto.chatMode() != null && !dto.chatMode().isBlank() && !CHAT_MODES.contains(dto.chatMode())) {
            return "非法对话模式: " + dto.chatMode();
        }
        if (dto.agentModel() != null && !dto.agentModel().isBlank() && !isAllowedModel("chat", dto.agentModel())) {
            return "不支持的 Agent 模型: " + dto.agentModel();
        }
        return null;
    }

    private boolean inRange(double v) {
        return v >= 0.0 && v <= 1.0;
    }

    private boolean isAllowedModel(String group, String id) {
        return MODEL_OPTIONS.stream()
            .anyMatch(o -> o.group().equals(group) && o.id().equals(id));
    }
}
