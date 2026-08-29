package com.rag.eval.service;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.eval.model.SearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Agent mode: hands the "retrieve knowledge base" + "search web" decisions to the LLM
 * via a tool-calling loop, instead of the fixed workflow pipeline in {@link ChatService}.
 * Safety (prompt injection / forbidden keywords) and PII redaction stay in code — they
 * are never exposed as tools.
 */
@Service
public class AgentService {

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);

    private static final String TOOL_KB = "search_knowledge_base";
    private static final String TOOL_WEB = "search_web";

    private static final String AGENT_TOOL_INSTRUCTIONS = """
        【工具使用说明】
        你拥有两个工具，请按需自主调用：
        1. search_knowledge_base(query)：检索知识库，返回与查询相关的文档片段。当用户问题可能涉及知识库内容时，优先调用此工具。
        2. search_web(query)：联网搜索最新信息。仅当知识库没有相关内容、或需要实时/最新信息时才调用。
        工作流程：先尝试检索知识库；若知识库内容不足以回答，再联网搜索；若两者都没有相关信息，请明确说明"该知识库中暂无相关信息"。
        最终回答必须严格基于工具返回的内容，并注明信息来源的文件名。
        """;

    private final DashScopeService dashScope;
    private final RetrievalService retrievalService;
    private final WebSearchService webSearchService;
    private final ConfigService config;
    private final ObjectMapper objectMapper;

    public AgentService(DashScopeService dashScope,
                        RetrievalService retrievalService,
                        WebSearchService webSearchService,
                        ConfigService config,
                        ObjectMapper objectMapper) {
        this.dashScope = dashScope;
        this.retrievalService = retrievalService;
        this.webSearchService = webSearchService;
        this.config = config;
        this.objectMapper = objectMapper;
    }

    public record AgentResult(String answer, List<SearchResult> chunks, int llmCallCount) {}
    public record ToolCallEvent(String tool, String query) {}
    public record ToolQuery(String query) {}

    public AgentResult run(String question, String retrievalMode, boolean webAllowed,
                           Consumer<ToolCallEvent> onToolCall) {
        List<SearchResult> chunks = new ArrayList<>();

        List<ToolCallback> tools = List.of(
            kbTool(retrievalMode, chunks),
            webTool(webAllowed, chunks));

        String basePrompt = config.get(ConfigService.KEY_SYSTEM_PROMPT, ConfigService.DEFAULT_SYSTEM_PROMPT);
        String systemPrompt = basePrompt + "\n\n" + AGENT_TOOL_INSTRUCTIONS;

        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(systemPrompt));
        messages.add(new UserMessage(question));

        int maxIterations = config.getInt("agent.max-iterations", 5);

        for (int i = 0; i < maxIterations; i++) {
            DashScopeChatOptions options = DashScopeChatOptions.builder()
                .withModel(config.get("agent.model", "qwen-plus"))
                .withTemperature(config.getDouble("generation.temperature", 0.3))
                .withToolCallbacks(tools)
                .withInternalToolExecutionEnabled(false)
                .build();

            ChatResponse response;
            try {
                response = dashScope.call(new Prompt(messages, options));
            } catch (Exception e) {
                log.warn("Agent LLM call failed: {}", e.getMessage());
                return new AgentResult("抱歉，我暂时无法回答这个问题，请稍后再试。", chunks, i);
            }

            AssistantMessage msg = response.getResult() != null ? response.getResult().getOutput() : null;
            if (msg == null) {
                return new AgentResult("抱歉，我暂时无法回答这个问题，请稍后再试。", chunks, i + 1);
            }

            if (msg.hasToolCalls()) {
                messages.add(msg);
                List<ToolResponseMessage.ToolResponse> responses = new ArrayList<>();
                for (AssistantMessage.ToolCall tc : msg.getToolCalls()) {
                    String query = extractQuery(tc.arguments());
                    onToolCall.accept(new ToolCallEvent(tc.name(), query));
                    String result = executeTool(tc.name(), query, retrievalMode, webAllowed, chunks);
                    responses.add(new ToolResponseMessage.ToolResponse(tc.id(), tc.name(), result));
                }
                messages.add(new ToolResponseMessage(responses));
                continue;
            }

            String answer = msg.getText();
            if (answer != null && !answer.isBlank()) {
                return new AgentResult(answer, chunks, i + 1);
            }
            return new AgentResult("抱歉，我暂时无法回答这个问题，请稍后再试。", chunks, i + 1);
        }

        log.warn("Agent loop exceeded maxIterations={}", maxIterations);
        return new AgentResult("抱歉，我暂时无法回答这个问题，请稍后再试。", chunks, maxIterations);
    }

    private ToolCallback kbTool(String retrievalMode, List<SearchResult> chunks) {
        return FunctionToolCallback.builder(TOOL_KB,
                (Function<ToolQuery, String>) q -> searchKnowledgeBase(q.query(), retrievalMode, chunks))
            .description("检索知识库，返回与查询相关的文档片段。当用户问题可能涉及知识库内容时优先调用。")
            .inputType(ToolQuery.class)
            .build();
    }

    private ToolCallback webTool(boolean webAllowed, List<SearchResult> chunks) {
        return FunctionToolCallback.builder(TOOL_WEB,
                (Function<ToolQuery, String>) q -> searchWeb(q.query(), webAllowed, chunks))
            .description("联网搜索最新信息，当知识库没有相关内容或需要实时信息时使用。")
            .inputType(ToolQuery.class)
            .build();
    }

    private String executeTool(String name, String query, String retrievalMode,
                               boolean webAllowed, List<SearchResult> chunks) {
        try {
            if (TOOL_KB.equals(name)) return searchKnowledgeBase(query, retrievalMode, chunks);
            if (TOOL_WEB.equals(name)) return searchWeb(query, webAllowed, chunks);
            return "未知工具: " + name;
        } catch (Exception e) {
            log.warn("Tool '{}' execution failed: {}", name, e.getMessage());
            return "工具调用失败: " + e.getMessage();
        }
    }

    private String searchKnowledgeBase(String query, String retrievalMode, List<SearchResult> chunks) {
        List<SearchResult> results = retrievalService.retrieve(query, retrievalMode).results();
        if (results.isEmpty()) return "知识库中没有检索到相关内容。";
        chunks.addAll(results);
        return formatChunks(results, config.getInt("agent.chunk-limit", 5));
    }

    private String searchWeb(String query, boolean webAllowed, List<SearchResult> chunks) {
        if (!webAllowed) return "无联网权限，无法联网搜索。";
        List<SearchResult> results = webSearchService.search(query);
        if (results.isEmpty()) return "联网搜索没有返回结果。";
        chunks.addAll(results);
        return formatChunks(results, config.getInt("agent.chunk-limit", 5));
    }

    private String formatChunks(List<SearchResult> results, int limit) {
        StringBuilder sb = new StringBuilder();
        int n = Math.min(limit, results.size());
        for (int i = 0; i < n; i++) {
            SearchResult r = results.get(i);
            String content = r.getContent() == null ? "" : r.getContent();
            if (content.length() > 500) content = content.substring(0, 500);
            sb.append("【来源: ").append(r.getFileName()).append("】\n").append(content).append("\n\n");
        }
        return sb.toString();
    }

    private String extractQuery(String argsJson) {
        if (argsJson == null || argsJson.isBlank()) return "";
        try {
            JsonNode node = objectMapper.readTree(argsJson);
            if (node.isObject() && node.has("query") && node.get("query").isTextual()) {
                return node.get("query").asText();
            }
            if (node.isTextual()) return node.asText();
            return argsJson;
        } catch (Exception e) {
            return argsJson;
        }
    }
}
