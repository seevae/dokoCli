package com.dokocli.core.compact;

import com.dokocli.core.session.Session;
import com.dokocli.core.tool.FileToolUtils;
import com.dokocli.model.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 三层上下文压缩管道，让 Agent 可以无限运行。
 *
 * <pre>
 * Layer 1 - microCompact:  每轮静默执行，将旧工具返回替换为占位符
 * Layer 2 - autoCompact:   token 数超阈值时自动触发，LLM 摘要压缩
 * Layer 3 - compact 工具:  模型主动调用，立即触发摘要压缩（逻辑同 Layer 2）
 * </pre>
 *
 * 工具定义由 {@link com.dokocli.core.tool.impl.CompactTool} 提供（真实工具，自动注册），
 * 本类只负责压缩逻辑，由 AgentService 在检测到 compact 调用后触发。
 */
@Component
public class ContextCompactor {

    private static final Logger log = LoggerFactory.getLogger(ContextCompactor.class);

    private static final int TOKEN_THRESHOLD = 50_000;
    private static final int KEEP_RECENT_TOOL_RESULTS = 3;
    private static final int MAX_SUMMARY_INPUT_CHARS = 80_000;
    private static final int SUMMARY_MAX_TOKENS = 2000;
    private static final Path TRANSCRIPT_DIR = FileToolUtils.WORKDIR.resolve(".transcripts");

    private final ModelClient modelClient;

    public ContextCompactor(ModelClient modelClient) {
        this.modelClient = modelClient;
    }

    // ======================== Layer 1: micro_compact ========================

    /**
     * 将最近 {@value KEEP_RECENT_TOOL_RESULTS} 次之前的旧工具返回内容替换为占位符。
     * 静默执行，每轮 LLM 调用前触发。
     */
    public void microCompact(Session session) {
        List<Integer> toolMsgIndices = collectToolMessageIndices(session);
        if (toolMsgIndices.size() <= KEEP_RECENT_TOOL_RESULTS) {
            return;
        }

        Map<String, String> toolNameMap = buildToolNameMap(session);
        int cutoff = toolMsgIndices.size() - KEEP_RECENT_TOOL_RESULTS;

        for (int i = 0; i < cutoff; i++) {
            int idx = toolMsgIndices.get(i);
            ToolMessage tm = (ToolMessage) session.getMessage(idx);
            if (tm.content() != null && tm.content().length() > 100) {
                String toolName = toolNameMap.getOrDefault(tm.toolCallId(), "unknown");
                session.replaceMessage(idx,
                        new ToolMessage(tm.toolCallId(), "[Previous: used " + toolName + "]"));
            }
        }
        log.debug("microCompact: compressed {} old tool results", cutoff);
    }

    // ======================== Layer 2 & 3: auto_compact ========================

    /**
     * 完整对话压缩：保存 transcript → 调用 LLM 生成摘要 → 用摘要替换全部历史。
     * 由 autoCompact 阈值触发（Layer 2）或 compact 工具触发（Layer 3）。
     */
    public void autoCompact(Session session) {
        saveTranscript(session);
        String summary = generateSummary(session);

        List<Message> compressed = new ArrayList<>();
        if (session.messageCount() > 0 && session.getMessage(0) instanceof SystemMessage sysMsg) {
            compressed.add(sysMsg);
        }
        compressed.add(new UserMessage("[Conversation compressed]\n\n" + summary));
        compressed.add(new AssistantMessage(
                "Understood. I have the context from the summary. Continuing.", null, null));

        session.replaceAllMessages(compressed);
        log.info("autoCompact: conversation compressed to {} messages", compressed.size());
    }

    /**
     * 估算 token 数是否超过阈值，决定是否需要自动压缩。
     */
    public boolean shouldAutoCompact(Session session) {
        return estimateTokens(session) > TOKEN_THRESHOLD;
    }

    // ======================== Internal helpers ========================

    private int estimateTokens(Session session) {
        int totalChars = 0;
        for (int i = 0; i < session.messageCount(); i++) {
            totalChars += messageToString(session.getMessage(i)).length();
        }
        return totalChars / 4;
    }

    private List<Integer> collectToolMessageIndices(Session session) {
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < session.messageCount(); i++) {
            if (session.getMessage(i) instanceof ToolMessage) {
                indices.add(i);
            }
        }
        return indices;
    }

    private Map<String, String> buildToolNameMap(Session session) {
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < session.messageCount(); i++) {
            if (session.getMessage(i) instanceof AssistantMessage am && am.hasToolCalls()) {
                for (ToolCall tc : am.toolCalls()) {
                    map.put(tc.id(), tc.name());
                }
            }
        }
        return map;
    }

    private String generateSummary(Session session) {
        String conversationText = buildConversationText(session);
        if (conversationText.length() > MAX_SUMMARY_INPUT_CHARS) {
            conversationText = conversationText.substring(0, MAX_SUMMARY_INPUT_CHARS);
        }

        try {
            ChatRequest request = new ChatRequest(
                    List.of(new UserMessage(
                            "Summarize this conversation for continuity. Include: "
                                    + "1) What was accomplished, 2) Current state, 3) Key decisions made. "
                                    + "Be concise but preserve critical details.\n\n" + conversationText)),
                    null,
                    new ModelParameters(null, 0.3, SUMMARY_MAX_TOKENS)
            );
            ChatResponse response = modelClient.chat(request);
            return response.content() != null ? response.content() : "(summary unavailable)";
        } catch (Exception e) {
            log.error("Failed to generate summary", e);
            return "(summary generation failed: " + e.getMessage() + ")";
        }
    }

    private void saveTranscript(Session session) {
        try {
            Files.createDirectories(TRANSCRIPT_DIR);
            String filename = "transcript_"
                    + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))
                    + ".txt";
            Path path = TRANSCRIPT_DIR.resolve(filename);

            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < session.messageCount(); i++) {
                sb.append(messageToString(session.getMessage(i))).append("\n---\n");
            }
            Files.writeString(path, sb.toString());
            log.info("Transcript saved: {}", path);
        } catch (IOException e) {
            log.warn("Failed to save transcript: {}", e.getMessage());
        }
    }

    private String buildConversationText(Session session) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < session.messageCount(); i++) {
            sb.append(messageToString(session.getMessage(i))).append("\n");
        }
        return sb.toString();
    }

    private String messageToString(Message msg) {
        if (msg instanceof SystemMessage sm) {
            return "[system] " + sm.content();
        } else if (msg instanceof UserMessage um) {
            return "[user] " + um.content();
        } else if (msg instanceof AssistantMessage am) {
            StringBuilder sb = new StringBuilder("[assistant] ");
            if (am.content() != null) sb.append(am.content());
            if (am.hasToolCalls()) {
                for (ToolCall tc : am.toolCalls()) {
                    sb.append("\n  [tool_call] ").append(tc.name())
                            .append("(").append(tc.arguments()).append(")");
                }
            }
            return sb.toString();
        } else if (msg instanceof ToolMessage tm) {
            return "[tool:" + tm.toolCallId() + "] " + tm.content();
        }
        return msg.toString();
    }
}
