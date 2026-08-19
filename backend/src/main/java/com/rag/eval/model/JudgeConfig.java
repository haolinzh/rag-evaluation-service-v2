package com.rag.eval.model;

/**
 * 一次评测运行使用的 LLM-as-Judge 配置。null 字段表示沿用全局默认值。
 */
public record JudgeConfig(Boolean enabled, String model) {
}
