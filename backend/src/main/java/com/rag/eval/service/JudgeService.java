package com.rag.eval.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LLM-as-Judge：用 DashScope 对话模型对单次问答做忠实度 / 上下文精确度 / 回答相关性打分。
 * 一次调用返回三个指标；任何异常（无 key / 超时 / JSON 解析失败）都向上抛出，由调用方回退到规则评测。
 */
@Service
public class JudgeService {

    private static final String SYSTEM_PROMPT = """
        你是 RAG（检索增强生成）系统的评测专家。你的任务是基于给定的【问题】【检索到的上下文】和【待评测回答】，对回答质量进行客观打分。

        打分规则：
        1. faithfulness（忠实度，0~1）：把回答拆解成原子陈述，逐条判断是否被上下文支持（supported / unsupported / partial）。faithfulness = (supported 数 + 0.5 * partial 数) / 总陈述数。任何无法在上下文中找到依据的事实都属于 unsupported。
        2. context_precision（上下文精确度）：对每条上下文判断它是否真的有助于回答问题（relevant: true/false），逐条输出 verdict。
        3. answer_relevancy（回答相关性，0~1）：回答是否直接、完整地切题，有无大量无关内容或车轱辘话。

        只输出一个 JSON 对象，不要输出任何解释文字、不要用 markdown 代码块包裹。""";

    private static final Pattern JSON_BLOCK = Pattern.compile("\\{[\\s\\S]*\\}");

    private final DashScopeService dashScope;
    private final ObjectMapper objectMapper;

    public JudgeService(DashScopeService dashScope, ObjectMapper objectMapper) {
        this.dashScope = dashScope;
        this.objectMapper = objectMapper;
    }

    public record JudgeResult(double faithfulness, double contextPrecision, Double answerRelevancy, String reason) {}

    public JudgeResult judge(String model, String question, String answer, List<String> contexts) {
        String userPrompt = buildUserPrompt(question, answer, contexts);
        DashScopeService.ChatResult chat = dashScope.chatWithModel(model, 0.0, SYSTEM_PROMPT, userPrompt);
        JsonNode root = parseJson(chat.content());

        double faithfulness = clamp(root.path("faithfulness").asDouble(-1), -1, 0.0, 1.0);
        double answerRelevancy = clamp(root.path("answer_relevancy").asDouble(-1), -1, 0.0, 1.0);
        double contextPrecision = computeContextPrecision(root.path("context_verdicts"));

        String faithfulnessReason = root.path("faithfulness_reason").asText("");
        String relevancyReason = root.path("answer_relevancy_reason").asText("");
        StringBuilder reason = new StringBuilder();
        if (!faithfulnessReason.isBlank()) reason.append("忠实度：").append(faithfulnessReason);
        if (!relevancyReason.isBlank()) {
            if (reason.length() > 0) reason.append("　");
            reason.append("相关性：").append(relevancyReason);
        }

        return new JudgeResult(
            faithfulness >= 0 ? faithfulness : 0.0,
            contextPrecision,
            answerRelevancy >= 0 ? answerRelevancy : null,
            reason.toString());
    }

    private String buildUserPrompt(String question, String answer, List<String> contexts) {
        StringBuilder sb = new StringBuilder();
        sb.append("【问题】\n").append(question).append("\n\n");
        sb.append("【检索到的上下文】\n");
        for (int i = 0; i < contexts.size(); i++) {
            sb.append('[').append(i + 1).append("] ").append(contexts.get(i)).append('\n');
        }
        sb.append("\n【待评测回答】\n").append(answer == null || answer.isBlank() ? "(空)" : answer);
        sb.append("\n\n请按上述规则输出 JSON。");
        return sb.toString();
    }

    private JsonNode parseJson(String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalStateException("judge 返回空内容");
        }
        // 1) 直接解析
        try {
            return objectMapper.readTree(content);
        } catch (Exception ignored) {
        }
        // 2) 剥掉可能的 markdown 围栏后解析
        String stripped = content.trim()
            .replaceFirst("^```(?:json)?\\s*", "")
            .replaceFirst("\\s*```$", "");
        try {
            return objectMapper.readTree(stripped);
        } catch (Exception ignored) {
        }
        // 3) 正则提取第一个 JSON 对象
        Matcher m = JSON_BLOCK.matcher(content);
        if (m.find()) {
            try {
                return objectMapper.readTree(m.group());
            } catch (Exception ignored) {
            }
        }
        throw new IllegalStateException("无法解析 judge 返回的 JSON");
    }

    private double computeContextPrecision(JsonNode verdicts) {
        if (verdicts == null || !verdicts.isArray() || verdicts.isEmpty()) return 0.0;
        double relevant = 0;
        double ap = 0;
        int k = 0;
        for (JsonNode v : verdicts) {
            k++;
            if (v.path("relevant").asBoolean(false)) {
                relevant++;
                ap += relevant / k;
            }
        }
        return relevant == 0 ? 0.0 : ap / relevant;
    }

    private static double clamp(double value, double invalid, double min, double max) {
        if (value == invalid) return invalid;
        return Math.max(min, Math.min(max, value));
    }
}
