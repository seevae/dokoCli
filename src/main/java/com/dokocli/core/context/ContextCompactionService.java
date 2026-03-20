package com.dokocli.core.context;

import com.dokocli.core.session.Session;
import com.dokocli.model.api.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jline.terminal.Terminal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * 三层压缩中的两层由本服务在编排层驱动：
 * <ul>
 *   <li>微压缩（micro）：将较早的 tool 结果长文本替换为占位符，每轮模型调用前执行</li>
 *   <li>自动/手动全文摘要（auto / compact 工具）：超 token 阈值或模型调用 compact 时，落盘转录并调用模型摘要后替换历史</li>
 * </ul>
 * 与需求文档中的 Python 管道语义对齐。
 */
@Service
public class ContextCompactionService {

    private static final Logger log = LoggerFactory.getLogger(ContextCompactionService.class);

    public static final int DEFAULT_TOKEN_THRESHOLD = 50_000;
    public static final int KEEP_RECENT_TOOL_RESULTS = 3;
    private static final int CONVERSATION_SNIPPET_CHARS = 80_000;
    private static final int SUMMARY_MAX_TOKENS = 2_000;
    /** 与 Python 一致：短于该长度的 tool 结果不替换，避免丢细节 */
    private static final int MICRO_REPLACE_MIN_CONTENT_LENGTH = 100;

    private static final String SUMMARY_INSTRUCTION = """
            Summarize this conversation for continuity. Include:
            1) What was accomplished,
            2) Current state,
            3) Key decisions made.
            Be concise but preserve critical details.

            """;

    private final ModelClient modelClient;
    private final ObjectMapper objectMapper;
    private final Path workspaceRoot;
    private final int tokenThreshold;

    public ContextCompactionService(
            ModelClient modelClient,
            ObjectMapper objectMapper,
            @Value("${dokocli.workspace:}") String workspaceProperty,
            @Value("${dokocli.context.token-threshold:" + DEFAULT_TOKEN_THRESHOLD + "}") int tokenThreshold) {
        this.modelClient = modelClient;
        this.objectMapper = objectMapper;
        this.workspaceRoot = workspaceProperty == null || workspaceProperty.isBlank()
                ? Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().normalize()
                : Path.of(workspaceProperty).toAbsolutePath().normalize();
        this.tokenThreshold = tokenThreshold;
    }

    /** 子代理等场景只需微压缩，不做阈值摘要。 */
    public void applyMicroCompactOnly(Session session) {
        session.mutateMessages(ContextCompactionService::microCompact);
    }

    /**
     * 每次调用模型前：先微压缩，再按需全文摘要。
     */
    public void prepareForModelCall(Session session, Terminal terminal, boolean logEvents) {
        session.mutateMessages(ContextCompactionService::microCompact);
        int estimated = estimateTokens(session.getMessages());
        if (estimated > tokenThreshold) {
            if (logEvents && terminal != null) {
                terminal.writer().println("[auto_compact] 估计 tokens " + estimated + " 超过阈值 " + tokenThreshold);
                terminal.flush();
            }
            runFullCompaction(session, null, terminal);
        }
    }

    /**
     * 保存完整转录、请求摘要、用摘要消息对替换历史（保留开头的 {@link SystemMessage}）。
     */
    public void runFullCompaction(Session session, String focusHint, Terminal terminal) {
        List<Message> current = session.getMessages();
        if (current.isEmpty()) {
            return;
        }
        Path transcriptDir = workspaceRoot.resolve(".transcripts");
        try {
            Files.createDirectories(transcriptDir);
            String fileName = "transcript_" + System.currentTimeMillis() + ".jsonl";
            Path transcriptPath = transcriptDir.resolve(fileName);
            writeTranscript(transcriptPath, current);

            if (terminal != null) {
                terminal.writer().println("[transcript] 已保存: " + transcriptPath);
                terminal.flush();
            }

            String conversationJson = objectMapper.writeValueAsString(toTranscriptMaps(current));
            if (conversationJson.length() > CONVERSATION_SNIPPET_CHARS) {
                conversationJson = conversationJson.substring(0, CONVERSATION_SNIPPET_CHARS);
            }

            StringBuilder prompt = new StringBuilder(SUMMARY_INSTRUCTION);
            if (focusHint != null && !focusHint.isBlank()) {
                prompt.append("Pay special attention to preserving: ").append(focusHint.trim()).append("\n\n");
            }
            prompt.append(conversationJson);

            ChatRequest summaryRequest = new ChatRequest(
                    List.of(new UserMessage(prompt.toString())),
                    null,
                    new ModelParameters(null, 0.3, SUMMARY_MAX_TOKENS)
            );
            ChatResponse summaryResponse = modelClient.chat(summaryRequest);
            String summary = summaryResponse.content() != null ? summaryResponse.content().trim() : "";
            if (summary.isEmpty()) {
                summary = "(empty summary)";
            }

            String userBody = "[Conversation compressed. Transcript: " + transcriptPath + "]\n\n" + summary;
            List<Message> rebuilt = new ArrayList<>();
            if (!current.isEmpty() && current.get(0) instanceof SystemMessage sys) {
                rebuilt.add(sys);
            }
            rebuilt.add(new UserMessage(userBody));
            rebuilt.add(new AssistantMessage(
                    "Understood. I have the context from the summary. Continuing.",
                    null,
                    null
            ));
            session.replaceMessages(rebuilt);
        } catch (Exception e) {
            log.warn("Full context compaction failed: {}", e.toString());
            if (terminal != null) {
                terminal.writer().println("[compact] 摘要压缩失败，保留原历史: " + e.getMessage());
                terminal.flush();
            }
        }
    }

    /**
     * 将较早的 tool 结果（保留最近 {@value #KEEP_RECENT_TOOL_RESULTS} 条）替换为短占位符。
     */
    public static void microCompact(List<Message> messages) {
        Map<String, String> toolCallIdToName = new HashMap<>();
        for (Message msg : messages) {
            if (msg instanceof AssistantMessage am && am.hasToolCalls()) {
                for (ToolCall tc : am.toolCalls()) {
                    toolCallIdToName.put(tc.id(), tc.name());
                }
            }
        }

        List<Integer> toolMessageIndices = new ArrayList<>();
        for (int i = 0; i < messages.size(); i++) {
            if (messages.get(i) instanceof ToolMessage) {
                toolMessageIndices.add(i);
            }
        }
        if (toolMessageIndices.size() <= KEEP_RECENT_TOOL_RESULTS) {
            return;
        }

        int toClear = toolMessageIndices.size() - KEEP_RECENT_TOOL_RESULTS;
        for (int j = 0; j < toClear; j++) {
            int idx = toolMessageIndices.get(j);
            ToolMessage tm = (ToolMessage) messages.get(idx);
            String content = tm.content();
            if (content != null && content.length() > MICRO_REPLACE_MIN_CONTENT_LENGTH) {
                String name = toolCallIdToName.getOrDefault(tm.toolCallId(), "unknown");
                messages.set(idx, new ToolMessage(tm.toolCallId(), "[Previous: used " + name + "]"));
            }
        }
    }

    public int estimateTokens(List<Message> messages) {
        try {
            String json = objectMapper.writeValueAsString(toTranscriptMaps(messages));
            return Math.max(0, json.length() / 4);
        } catch (Exception e) {
            return messages.stream().mapToInt(m -> m.toString().length()).sum() / 4;
        }
    }

    private void writeTranscript(Path path, List<Message> messages) throws Exception {
        List<Map<String, Object>> rows = toTranscriptMaps(messages);
        try (var writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            for (Map<String, Object> row : rows) {
                writer.write(objectMapper.writeValueAsString(row));
                writer.write('\n');
            }
        }
    }

    private List<Map<String, Object>> toTranscriptMaps(List<Message> messages) {
        List<Map<String, Object>> out = new ArrayList<>(messages.size());
        for (Message msg : messages) {
            out.add(messageToMap(msg));
        }
        return out;
    }

    private Map<String, Object> messageToMap(Message msg) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("role", msg.role());
        if (msg instanceof UserMessage m) {
            map.put("content", m.content());
        } else if (msg instanceof SystemMessage m) {
            map.put("content", m.content());
        } else if (msg instanceof ToolMessage m) {
            map.put("content", m.content());
            map.put("tool_call_id", m.toolCallId());
        } else if (msg instanceof AssistantMessage m) {
            map.put("content", m.content() != null ? m.content() : "");
            if (m.reasoningContent() != null && !m.reasoningContent().isEmpty()) {
                map.put("reasoning_content", m.reasoningContent());
            }
            if (m.hasToolCalls()) {
                List<Map<String, Object>> toolCalls = new ArrayList<>();
                for (ToolCall tc : m.toolCalls()) {
                    Map<String, Object> tcMap = new LinkedHashMap<>();
                    tcMap.put("id", tc.id());
                    tcMap.put("type", "function");
                    Map<String, Object> fn = new LinkedHashMap<>();
                    fn.put("name", tc.name());
                    try {
                        fn.put("arguments", objectMapper.writeValueAsString(tc.arguments()));
                    } catch (Exception e) {
                        fn.put("arguments", "{}");
                    }
                    tcMap.put("function", fn);
                    toolCalls.add(tcMap);
                }
                map.put("tool_calls", toolCalls);
            }
        } else {
            map.put("content", msg.toString());
        }
        return map;
    }
}
