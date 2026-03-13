package com.dokocli.model.kimi;

import com.dokocli.model.api.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Kimi K2.5 模型客户端实现
 */
@Component
public class KimiModelClient implements ModelClient {

    private static final Logger log = LoggerFactory.getLogger(KimiModelClient.class);
    private static final String KIMI_BASE_URL = "https://api.moonshot.cn/v1";

    private final String apiKey;
    private final String model;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public KimiModelClient(
            @Value("${kimi.api-key}") String apiKey,
            @Value("${kimi.model:kimi-k2.5}") String model) {
        this.apiKey = apiKey;
        this.model = model;
        this.objectMapper = new ObjectMapper();
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .build();
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        try {
            RequestBody body = buildRequestBody(request, false);
            Request httpRequest = new Request.Builder()
                    .url(KIMI_BASE_URL + "/chat/completions")
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .post(body)
                    .build();

            try (Response response = httpClient.newCall(httpRequest).execute()) {
                if (!response.isSuccessful()) {
                    String error = response.body() != null ? response.body().string() : "Unknown error";
                    throw new RuntimeException("Kimi API error: " + error);
                }

                String json = response.body().string();
                return parseResponse(json);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to call Kimi API", e);
        }
    }

    @Override
    public Stream<ChatChunk> stream(ChatRequest request) {
        try {
            RequestBody body = buildRequestBody(request, true);
            Request httpRequest = new Request.Builder()
                    .url(KIMI_BASE_URL + "/chat/completions")
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .post(body)
                    .build();

            Response response = httpClient.newCall(httpRequest).execute();
            if (!response.isSuccessful()) {
                String error = response.body() != null ? response.body().string() : "Unknown error";
                throw new RuntimeException("Kimi API error: " + error);
            }

            return parseStream(response);
        } catch (IOException e) {
            throw new RuntimeException("Failed to stream from Kimi API", e);
        }
    }

    @Override
    public ModelCapabilities getCapabilities() {
        return new ModelCapabilities(256000, true, false, true);
    }

    @Override
    public String getProvider() {
        return "kimi";
    }

    private RequestBody buildRequestBody(ChatRequest request, boolean stream) throws IOException {
        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("messages", convertMessages(request.messages()));
        body.put("stream", stream);

        if (request.tools() != null && !request.tools().isEmpty()) {
            body.put("tools", convertTools(request.tools()));
        }

        if (request.parameters().maxTokens() != null) {
            body.put("max_tokens", request.parameters().maxTokens());
        }

        String json = objectMapper.writeValueAsString(body);
        return RequestBody.create(json, MediaType.parse("application/json"));
    }

    private List<Map<String, Object>> convertMessages(List<Message> messages) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Message msg : messages) {
            Map<String, Object> map = new HashMap<>();
            map.put("role", msg.role());

            if (msg instanceof UserMessage m) {
                map.put("content", m.content());
            } else if (msg instanceof SystemMessage m) {
                map.put("content", m.content());
            } else if (msg instanceof AssistantMessage m) {
                map.put("content", m.content());
                // Kimi K2.5 thinking 模式下，带 tool_calls 的 assistant 消息必须包含 reasoning_content
                map.put("reasoning_content", m.reasoningContent() != null ? m.reasoningContent() : "");
                if (m.hasToolCalls()) {
                    List<Map<String, Object>> toolCalls = new ArrayList<>();
                    for (ToolCall tc : m.toolCalls()) {
                        Map<String, Object> tcMap = new HashMap<>();
                        tcMap.put("id", tc.id());
                        tcMap.put("type", "function");
                        Map<String, Object> funcMap = new HashMap<>();
                        funcMap.put("name", tc.name());
                        try {
                            funcMap.put("arguments", objectMapper.writeValueAsString(tc.arguments()));
                        } catch (Exception e) {
                            funcMap.put("arguments", "{}");
                        }
                        tcMap.put("function", funcMap);
                        toolCalls.add(tcMap);
                    }
                    map.put("tool_calls", toolCalls);
                }
            } else if (msg instanceof ToolMessage m) {
                map.put("content", m.content());
                map.put("tool_call_id", m.toolCallId());
            }
            result.add(map);
        }
        return result;
    }

    private List<Map<String, Object>> convertTools(List<ToolDefinition> tools) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (ToolDefinition tool : tools) {
            Map<String, Object> map = new HashMap<>();
            map.put("type", "function");
            Map<String, Object> funcMap = new HashMap<>();
            funcMap.put("name", tool.name());
            funcMap.put("description", tool.description());
            funcMap.put("parameters", tool.parameters());
            map.put("function", funcMap);
            result.add(map);
        }
        return result;
    }

    private ChatResponse parseResponse(String json) throws IOException {
        JsonNode root = objectMapper.readTree(json);
        JsonNode choice = root.path("choices").get(0);
        JsonNode message = choice.path("message");

        String content = message.path("content").asText("");
        String reasoningContent = message.has("reasoning_content") && !message.path("reasoning_content").isNull()
                ? message.path("reasoning_content").asText("")
                : null;
        String finishReason = choice.path("finish_reason").asText();

        List<ToolCall> toolCalls = new ArrayList<>();
        JsonNode toolCallsNode = message.path("tool_calls");
        if (toolCallsNode.isArray()) {
            for (JsonNode tc : toolCallsNode) {
                String id = tc.path("id").asText();
                String name = tc.path("function").path("name").asText();
                String argsStr = tc.path("function").path("arguments").asText("{}");
                Map<String, Object> args = objectMapper.readValue(argsStr, Map.class);
                toolCalls.add(new ToolCall(id, name, args));
            }
        }

        JsonNode usage = root.path("usage");
        ChatResponse.Usage usageInfo = new ChatResponse.Usage(
                usage.path("prompt_tokens").asInt(0),
                usage.path("completion_tokens").asInt(0),
                usage.path("total_tokens").asInt(0)
        );

        return new ChatResponse(content, toolCalls, reasoningContent, usageInfo, finishReason);
    }

    private Stream<ChatChunk> parseStream(Response response) throws IOException {
        return StreamSupport.stream(
                new Spliterators.AbstractSpliterator<ChatChunk>(Long.MAX_VALUE, Spliterator.ORDERED) {
                    private final okhttp3.ResponseBody body = response.body();

                    @Override
                    public boolean tryAdvance(java.util.function.Consumer<? super ChatChunk> action) {
                        try {
                            String line = body.source().readUtf8Line();
                            if (line == null) return false;

                            if (line.startsWith("data: ")) {
                                String data = line.substring(6);
                                if ("[DONE]".equals(data)) return false;

                                JsonNode node = objectMapper.readTree(data);
                                JsonNode delta = node.path("choices").get(0).path("delta");
                                String content = delta.path("content").asText("");
                                String finishReason = node.path("choices").get(0).path("finish_reason").asText(null);

                                action.accept(new ChatChunk(content, finishReason));
                                return true;
                            }
                            return true;
                        } catch (IOException e) {
                            return false;
                        }
                    }
                }, false);
    }
}
