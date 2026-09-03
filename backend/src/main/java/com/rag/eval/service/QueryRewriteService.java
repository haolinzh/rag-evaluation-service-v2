package com.rag.eval.service;

import com.rag.eval.model.ChatMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 多轮 Query Rewrite：检索前把当前问题结合历史改写成独立可检索的 query，
 * 补全「它 / 这个」等指代与省略的上下文。仅在有历史且开关开启时触发；
 * 任何异常都回退到原始问题，绝不阻断主流程。
 */
@Service
public class QueryRewriteService {

    private static final Logger log = LoggerFactory.getLogger(QueryRewriteService.class);

    private static final String SYSTEM_PROMPT = """
        你是查询改写助手。根据对话历史和用户当前的问题，把问题改写成独立、完整、可用于检索的查询。
        要求：
        - 补全指代（如「它」「这个」「那个」）与省略的上下文，使查询脱离历史也能被理解。
        - 保留原问题的核心意图，不要添加无关信息。
        - 只输出改写后的查询本身，不要解释、不要加引号、不要用 markdown。""";

    private final DashScopeService dashScope;
    private final ConfigService config;

    public QueryRewriteService(DashScopeService dashScope, ConfigService config) {
        this.dashScope = dashScope;
        this.config = config;
    }

    public record RewriteResult(String query, boolean rewritten, int promptTokens, int completionTokens) {}

    public RewriteResult rewrite(String question, List<ChatMessage> history) {
        if (!config.getBool("retrieval.query-rewrite-enabled", true)
            || history == null || history.isEmpty()) {
            return new RewriteResult(question, false, 0, 0);
        }

        String historyText = history.stream()
            .limit(10)
            .map(m -> ("user".equals(m.getRole()) ? "用户: " : "助手: ")
                + (m.getContent() == null ? "" : m.getContent()))
            .collect(Collectors.joining("\n"));

        String userPrompt = "=== 对话历史 ===\n" + historyText + "\n\n=== 当前问题 ===\n" + question;

        try {
            String model = config.get("dashscope.chat-model", "qwen-turbo");
            DashScopeService.ChatResult chat = dashScope.chatWithModel(model, 0.1, SYSTEM_PROMPT, userPrompt);
            String rewritten = chat.content() == null ? "" : chat.content().trim();
            if (rewritten.isEmpty() || rewritten.equals(question.trim())) {
                return new RewriteResult(question, false, chat.promptTokens(), chat.completionTokens());
            }
            log.info("Query rewritten: {} -> {}", question, rewritten);
            return new RewriteResult(rewritten, true, chat.promptTokens(), chat.completionTokens());
        } catch (Exception e) {
            log.warn("Query rewrite failed, fallback to original: {}", e.getMessage());
            return new RewriteResult(question, false, 0, 0);
        }
    }
}
