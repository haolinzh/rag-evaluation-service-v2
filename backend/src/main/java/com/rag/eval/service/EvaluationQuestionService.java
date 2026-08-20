package com.rag.eval.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.eval.model.EvaluationQuestion;
import com.rag.eval.repository.EvaluationQuestionRepo;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 测试集题目管理：启动时从 classpath JSON 幂等导入种子题目（表空时），
 * 之后所有读操作走 DB，增删改直接落库。id 沿用 q001..qNNN 字符串格式以兼容历史报告。
 */
@Service
public class EvaluationQuestionService implements ApplicationRunner {

    private static final Pattern ID_PATTERN = Pattern.compile("^q(\\d+)$");

    private final EvaluationQuestionRepo repo;
    private final ObjectMapper objectMapper;

    public EvaluationQuestionService(EvaluationQuestionRepo repo, ObjectMapper objectMapper) {
        this.repo = repo;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(ApplicationArguments args) {
        seedIfEmpty();
    }

    public void seedIfEmpty() {
        if (repo.count() > 0) return;
        List<EvaluationQuestion> seeds = loadSeeds();
        repo.saveAll(seeds);
    }

    public List<EvaluationQuestion> list() {
        return repo.findAllByOrderByIdAsc();
    }

    public EvaluationQuestion create(EvaluationQuestion input) {
        if (input.getQuestion() == null || input.getQuestion().isBlank()) {
            throw new IllegalArgumentException("题目内容不能为空");
        }
        EvaluationQuestion q = new EvaluationQuestion();
        q.setId(nextId());
        q.setQuestion(input.getQuestion());
        q.setLanguage(normalizeLanguage(input.getLanguage()));
        q.setExpectedType(normalizeType(input.getExpectedType()));
        q.setDifficulty(normalizeDifficulty(input.getDifficulty()));
        return repo.save(q);
    }

    public EvaluationQuestion update(String id, EvaluationQuestion input) {
        EvaluationQuestion q = repo.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("题目不存在: " + id));
        if (input.getQuestion() != null && !input.getQuestion().isBlank()) {
            q.setQuestion(input.getQuestion());
        }
        if (input.getLanguage() != null) q.setLanguage(normalizeLanguage(input.getLanguage()));
        if (input.getExpectedType() != null) q.setExpectedType(normalizeType(input.getExpectedType()));
        if (input.getDifficulty() != null) q.setDifficulty(normalizeDifficulty(input.getDifficulty()));
        return repo.save(q);
    }

    public void delete(String id) {
        if (!repo.existsById(id)) {
            throw new IllegalArgumentException("题目不存在: " + id);
        }
        repo.deleteById(id);
    }

    private List<EvaluationQuestion> loadSeeds() {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("evaluation-questions.json")) {
            if (in == null) {
                throw new IllegalStateException("evaluation-questions.json not found on classpath");
            }
            return objectMapper.readValue(in, new TypeReference<List<EvaluationQuestion>>() {});
        } catch (IOException e) {
            throw new RuntimeException("Failed to load seed evaluation questions", e);
        }
    }

    private String nextId() {
        int max = 0;
        for (EvaluationQuestion q : repo.findAll()) {
            Matcher m = ID_PATTERN.matcher(q.getId() == null ? "" : q.getId());
            if (m.matches()) {
                max = Math.max(max, Integer.parseInt(m.group(1)));
            }
        }
        return String.format("q%03d", max + 1);
    }

    private static String normalizeLanguage(String v) {
        if (v == null || v.isBlank()) return "zh";
        return "en".equalsIgnoreCase(v.trim()) ? "en" : "zh";
    }

    private static String normalizeType(String v) {
        if (v == null || v.isBlank()) return "factual";
        String t = v.trim().toLowerCase();
        return switch (t) {
            case "explanatory", "comparison", "safety_refusal" -> t;
            default -> "factual";
        };
    }

    private static String normalizeDifficulty(String v) {
        if (v == null || v.isBlank()) return "basic";
        String d = v.trim().toLowerCase();
        return "intermediate".equals(d) ? "intermediate" : "basic";
    }
}
