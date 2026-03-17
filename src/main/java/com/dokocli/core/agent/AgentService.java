package com.dokocli.core.agent;

import com.dokocli.core.session.Session;
import com.dokocli.core.session.SessionContextHolder;
import com.dokocli.core.tool.ToolRegistry;
import com.dokocli.model.api.*;
import org.jline.terminal.Terminal;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 核心 Agent 编排逻辑（agent loop）。
 * 负责：维护会话消息、调用大模型、处理工具调用并回填结果。
 * CLI、HTTP 等上层只需要调用此服务。
 */
@Service
public class AgentService {

    private static final int TOOL_RESULT_MAX_LENGTH = 8000;
    private static final int TERMINAL_PREVIEW_LENGTH = 300;
    private static final int REASONING_PREVIEW_LENGTH = 1000;
    private static final int NAG_REMINDER_THRESHOLD = 3;

    private final ModelClient modelClient;
    private final ToolRegistry toolRegistry;

    public AgentService(ModelClient modelClient, ToolRegistry toolRegistry) {
        this.modelClient = modelClient;
        this.toolRegistry = toolRegistry;
    }

    public void processUserInput(String input, Session session, Terminal terminal) {
        SessionContextHolder.setSession(session);
        try {
            session.addMessage(new UserMessage(input));
            terminal.writer().print("\n");
            terminal.flush();

            ChatResponse response = callModel(session);
            int roundsSinceTodo = 0;

            while (response.hasToolCalls()) {
                printReasoning(response, terminal);
                session.addMessage(new AssistantMessage(
                        response.content(),
                        response.toolCalls(),
                        response.reasoningContent()
                ));

                boolean usedTodo = executeToolCalls(response.toolCalls(), session, terminal);
                roundsSinceTodo = usedTodo ? 0 : roundsSinceTodo + 1;

                if (roundsSinceTodo >= NAG_REMINDER_THRESHOLD) {
                    session.addMessage(new UserMessage(
                            "<reminder>Update your todos to reflect current progress.</reminder>"));
                }

                response = callModel(session);
            }

            streamFinalResponse(session, terminal);
            session.trimMessages(20);

        } catch (Exception e) {
            terminal.writer().println("错误: " + e.getMessage());
            terminal.flush();
        } finally {
            SessionContextHolder.clear();
        }
    }

    private ChatResponse callModel(Session session) {
        ChatRequest request = new ChatRequest(
                session.getMessages(),
                toolRegistry.getAllToolDefinitions()
        );
        return modelClient.chat(request);
    }

    /**
     * 执行本轮所有工具调用，写入 ToolMessage，返回本轮是否调用了 todo 工具。
     */
    private boolean executeToolCalls(List<ToolCall> toolCalls, Session session, Terminal terminal) {
        boolean usedTodo = false;
        for (ToolCall tc : toolCalls) {
            if ("todo".equals(tc.name())) {
                usedTodo = true;
            }
            terminal.writer().println("[执行工具] " + tc.name() + " " + tc.arguments());
            terminal.flush();

            try {
                String result = toolRegistry.executeTool(tc.name(), tc.arguments());
                printToolPreview(terminal, result);
                if (result.length() > TOOL_RESULT_MAX_LENGTH) {
                    result = result.substring(0, TOOL_RESULT_MAX_LENGTH) + "\n... (内容已截断)";
                }
                session.addMessage(new ToolMessage(tc.id(), result));
            } catch (Exception e) {
                String error = "执行失败: " + e.getMessage();
                terminal.writer().println("[工具错误] " + error);
                terminal.writer().println();
                terminal.flush();
                session.addMessage(new ToolMessage(tc.id(), error));
            }
        }
        return usedTodo;
    }

    private void printReasoning(ChatResponse response, Terminal terminal) {
        String reasoning = response.reasoningContent();
        if (reasoning == null || reasoning.isEmpty()) {
            return;
        }
        String preview = reasoning.length() > REASONING_PREVIEW_LENGTH
                ? reasoning.substring(0, REASONING_PREVIEW_LENGTH) + "... (思考内容已截断)"
                : reasoning;
        terminal.writer().println("[思考] " + preview);
        terminal.writer().println();
        terminal.flush();
    }

    private void printToolPreview(Terminal terminal, String result) {
        String readable = unescapeForDisplay(result);
        String preview = readable.length() > TERMINAL_PREVIEW_LENGTH
                ? readable.substring(0, TERMINAL_PREVIEW_LENGTH) + "... (输出已截断)"
                : readable;
        terminal.writer().println("[工具结果]");
        terminal.writer().println(preview);
        terminal.writer().println();
        terminal.flush();
    }

    /**
     * 将 Spring AI ToolCallback 返回的 JSON 编码字符串还原为人类可读格式：
     * 去掉外层引号，将 \n \t \" \\\\ 等转义还原为真实字符。
     */
    private static String unescapeForDisplay(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw;
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
            s = s.substring(1, s.length() - 1);
        }
        return s.replace("\\n", "\n")
                .replace("\\t", "\t")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
    }

    private void streamFinalResponse(Session session, Terminal terminal) {
        ChatRequest finalRequest = new ChatRequest(
                session.getMessages(),
                toolRegistry.getAllToolDefinitions()
        );
        StringBuilder fullContent = new StringBuilder();
        modelClient.stream(finalRequest).forEach(chunk -> {
            String delta = chunk.content();
            if (delta != null && !delta.isEmpty()) {
                fullContent.append(delta);
                terminal.writer().print(delta);
                terminal.writer().flush();
            }
        });
        String finalContent = fullContent.toString();
        if (!finalContent.isEmpty()) {
            terminal.writer().println();
            terminal.writer().println();
            terminal.flush();
            session.addMessage(new AssistantMessage(finalContent, null, null));
        }
    }
}
