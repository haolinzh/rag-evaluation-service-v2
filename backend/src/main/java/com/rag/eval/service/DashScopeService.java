package com.rag.eval.service;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.dashscope.embedding.DashScopeEmbeddingModel;
import com.alibaba.cloud.ai.dashscope.embedding.DashScopeEmbeddingOptions;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@Service
public class DashScopeService {

    private static final String DEFAULT_BASE_URL = "https://dashscope.aliyuncs.com";
    private static final String METADATA_REASONING_CONTENT = "reasoningContent";

    private final ConfigService config;
    private final DashScopeChatModel baseChatModel;
    private final DashScopeEmbeddingModel baseEmbeddingModel;
    private final RestClient.Builder restClientBuilder;
    private final WebClient.Builder webClientBuilder;

    private volatile String cachedApiKey;
    private volatile DashScopeChatModel chatModel;
    private volatile DashScopeEmbeddingModel embeddingModel;

    public DashScopeService(ConfigService config,
                            DashScopeChatModel baseChatModel,
                            DashScopeEmbeddingModel baseEmbeddingModel,
                            RestClient.Builder restClientBuilder,
                            WebClient.Builder webClientBuilder) {
        this.config = config;
        this.baseChatModel = baseChatModel;
        this.baseEmbeddingModel = baseEmbeddingModel;
        this.restClientBuilder = restClientBuilder;
        this.webClientBuilder = webClientBuilder;
        this.chatModel = baseChatModel;
        this.embeddingModel = baseEmbeddingModel;
    }

    public record ChatResult(String content, String thinking, int promptTokens, int completionTokens) {}

    public String getChatModel() {
        return config.get("dashscope.chat-model", "qwen-turbo");
    }

    public ChatResult chat(String systemPrompt, String userMessage) {
        return generate(getChatModel(),
            config.getDouble("generation.temperature", 0.3),
            config.getDouble("generation.top-p", 1.0),
            config.getInt("generation.max-tokens", 0),
            systemPrompt, userMessage);
    }

    public ChatResult chatWithModel(String model, double temperature, String systemPrompt, String userMessage) {
        return generate(model, temperature, 1.0, 0, systemPrompt, userMessage);
    }

    public ChatResult chatForJudge(String model, String systemPrompt, String userMessage) {
        return chatWithModel(model, config.getDouble("evaluation.judge-temperature", 0.0), systemPrompt, userMessage);
    }

    private ChatResult generate(String model, double temperature, double topP, int maxTokens,
                                String systemPrompt, String userMessage) {
        try {
            refreshModels(config.resolveDashScopeApiKey());
            DashScopeChatOptions options = chatOptions(model, temperature, topP, maxTokens);
            Prompt prompt = new Prompt(List.of(new SystemMessage(systemPrompt), new UserMessage(userMessage)), options);

            ChatResponse response = chatModel.call(prompt);
            AssistantMessage message = response.getResult() != null ? response.getResult().getOutput() : null;
            String content = message != null && message.getText() != null ? message.getText() : "";
            String thinking = metadataReasoning(message);

            Usage usage = response.getMetadata() != null ? response.getMetadata().getUsage() : null;
            int promptTokens = usage != null && usage.getPromptTokens() != null ? usage.getPromptTokens() : 0;
            int completionTokens = usage != null && usage.getCompletionTokens() != null ? usage.getCompletionTokens() : 0;

            return new ChatResult(content, thinking, promptTokens, completionTokens);
        } catch (Exception e) {
            throw new RuntimeException("DashScope chat failed: " + e.getMessage(), e);
        }
    }

    /** Raw model access for the agent loop: reuses the runtime hot-swapped model
     *  (via {@link #refreshModels}) and delegates the full prompt (system/user/tools). */
    public ChatResponse call(Prompt prompt) {
        refreshModels(config.resolveDashScopeApiKey());
        return chatModel.call(prompt);
    }

    public ChatResult chatStream(String systemPrompt, String userMessage,
                                 Consumer<String> onThinking, Consumer<String> onContent) {
        try {
            refreshModels(config.resolveDashScopeApiKey());
            DashScopeChatOptions options = chatOptions(getChatModel(),
                config.getDouble("generation.temperature", 0.3),
                config.getDouble("generation.top-p", 1.0),
                config.getInt("generation.max-tokens", 0));
            options.setIncrementalOutput(true);

            Prompt prompt = new Prompt(List.of(new SystemMessage(systemPrompt), new UserMessage(userMessage)), options);

            StringBuilder thinkingBuf = new StringBuilder();
            StringBuilder contentBuf = new StringBuilder();
            int[] tokens = {0, 0};

            chatModel.stream(prompt).toStream().forEach(chunk -> {
                if (chunk.getResult() == null) return;
                AssistantMessage message = chunk.getResult().getOutput();
                if (message == null) return;
                String thinking = metadataReasoning(message);
                String text = message.getText();
                if (thinking != null && !thinking.isEmpty()) {
                    thinkingBuf.append(thinking);
                    onThinking.accept(thinking);
                }
                if (text != null && !text.isEmpty()) {
                    contentBuf.append(text);
                    onContent.accept(text);
                }
                Usage usage = chunk.getMetadata() != null ? chunk.getMetadata().getUsage() : null;
                if (usage != null) {
                    if (usage.getPromptTokens() != null) tokens[0] = usage.getPromptTokens();
                    if (usage.getCompletionTokens() != null) tokens[1] = usage.getCompletionTokens();
                }
            });

            return new ChatResult(contentBuf.toString(), thinkingBuf.toString(), tokens[0], tokens[1]);
        } catch (Exception e) {
            throw new RuntimeException("DashScope stream failed: " + e.getMessage(), e);
        }
    }

    private DashScopeChatOptions chatOptions(String model, double temperature, double topP, int maxTokens) {
        DashScopeChatOptions.DashscopeChatOptionsBuilder builder =
            DashScopeChatOptions.builder().withModel(model).withTemperature(temperature);
        if (topP < 1.0) {
            builder.withTopP(topP);
        }
        if (maxTokens > 0) {
            builder.withMaxToken(maxTokens);
        }
        return builder.build();
    }

    private String metadataReasoning(AssistantMessage message) {
        if (message == null || message.getMetadata() == null) return "";
        Object reasoning = message.getMetadata().get(METADATA_REASONING_CONTENT);
        return reasoning != null ? reasoning.toString() : "";
    }

    public List<Double> embed(String text) {
        try {
            refreshModels(config.resolveDashScopeApiKey());
            EmbeddingResponse response = embeddingModel.call(
                new EmbeddingRequest(List.of(text), embeddingOptions()));
            List<Embedding> embeddings = response.getResults();
            if (embeddings == null || embeddings.isEmpty()) return List.of();
            return toDoubles(embeddings.get(0).getOutput());
        } catch (Exception e) {
            throw new RuntimeException("DashScope embedding failed: " + e.getMessage(), e);
        }
    }

    public List<List<Double>> embedBatch(List<String> texts) {
        try {
            refreshModels(config.resolveDashScopeApiKey());
            List<List<Double>> all = new ArrayList<>();
            // DashScope text-embedding-v3 rejects batches larger than 10
            int batchSize = 10;
            for (int i = 0; i < texts.size(); i += batchSize) {
                int end = Math.min(i + batchSize, texts.size());
                List<String> batch = texts.subList(i, end);
                EmbeddingResponse response = embeddingModel.call(
                    new EmbeddingRequest(batch, embeddingOptions()));
                List<Embedding> embeddings = response.getResults();
                if (embeddings == null) continue;
                for (Embedding e : embeddings) {
                    all.add(toDoubles(e.getOutput()));
                }
            }
            return all;
        } catch (Exception e) {
            throw new RuntimeException("DashScope embedding failed: " + e.getMessage(), e);
        }
    }

    private DashScopeEmbeddingOptions embeddingOptions() {
        DashScopeEmbeddingOptions options = new DashScopeEmbeddingOptions();
        options.setModel(config.get("dashscope.embedding-model", "text-embedding-v3"));
        options.setDimensions(1024);
        return options;
    }

    private List<Double> toDoubles(float[] vector) {
        if (vector == null) return List.of();
        List<Double> result = new ArrayList<>(vector.length);
        for (float f : vector) result.add((double) f);
        return result;
    }

    /** Rebuild the DashScope models when the resolved API key changes at runtime,
     *  preserving the original behaviour of reading {@code dashscope.api-key} from the
     *  config page / {@code system_config} table. Model/temperature/top-p/max-tokens are
     *  still overridden per-request via options, so only the key triggers a rebuild. */
    private synchronized void refreshModels(String apiKey) {
        if (apiKey.equals(cachedApiKey)) return;
        DashScopeApi api = DashScopeApi.builder()
            .apiKey(apiKey)
            .baseUrl(DEFAULT_BASE_URL)
            .restClientBuilder(restClientBuilder)
            .webClientBuilder(webClientBuilder)
            .build();
        this.chatModel = baseChatModel.mutate().dashScopeApi(api).build();
        this.embeddingModel = new DashScopeEmbeddingModel(api, MetadataMode.NONE, embeddingOptions());
        this.cachedApiKey = apiKey;
    }

    public static String embeddingToString(List<Double> embedding) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(embedding.get(i));
        }
        sb.append("]");
        return sb.toString();
    }
}
