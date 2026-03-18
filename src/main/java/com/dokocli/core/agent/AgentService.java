package com.dokocli.core.agent;

import com.dokocli.core.session.Session;
import com.dokocli.core.tool.ToolRegistry;
import com.dokocli.model.api.*;
import org.jline.terminal.Terminal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 核心 Agent 编排逻辑（对应 Python 的 agent_loop）。
 * 负责：
 * - 维护会话消息
 * - 调用大模型
 * - 处理工具调用并回填结果
 * - 拦截 task 工具调用，委派给 SubAgentService
 * CLI、HTTP 等上层只需要调用此服务。
 */
@Service
public class AgentService {

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);

    /**
     * task 工具定义（对应 Python PARENT_TOOLS 里的 task schema）。
     * task 不是普通工具，而是编排动作，由 AgentService 拦截处理。
     */
    private static final ToolDefinition TASK_TOOL_DEFINITION = new ToolDefinition(
            "task",
            """
            派发子任务给子代理（subagent）执行，并把总结返回给当前对话。
            子代理不会继承当前对话历史，适合做独立探索、验证假设、检索代码、拟定方案等。
            子代理与父代理共享同一个工作区/文件系统，可使用基础工具（read_file/write_file/edit_file/execute_bash）。
            子代理完成后只返回一段简洁总结。
            prompt 建议：写清目标、范围、期望输出格式、禁止事项。
            """,
            Map.of(
                    "type", "object",
                    "properties", Map.of(
                            "prompt", Map.of(
                                    "type", "string",
                                    "description", "子任务提示词（要求清晰、可执行）"
                            ),
                            "description", Map.of(
                                    "type", "string",
                                    "description", "子任务简短描述（可选）"
                            )
                    ),
                    "required", List.of("prompt")
            )
    );

    private final ModelClient modelClient;
    private final ToolRegistry toolRegistry;
    private final SubAgentService subAgentService;

    public AgentService(ModelClient modelClient, ToolRegistry toolRegistry, SubAgentService subAgentService) {
        this.modelClient = modelClient;
        this.toolRegistry = toolRegistry;
        this.subAgentService = subAgentService;
    }

    private List<ToolDefinition> getParentTools() {
        List<ToolDefinition> tools = new ArrayList<>(toolRegistry.getAllToolDefinitions());
        tools.add(TASK_TOOL_DEFINITION);
        return tools;
    }

    public void processUserInput(String input, Session session, Terminal terminal) {
        session.addMessage(new UserMessage(input));

        List<ToolDefinition> parentTools = getParentTools();
        ChatRequest request = new ChatRequest(session.getMessages(), parentTools);

        terminal.writer().print("\n");
        terminal.flush();

        try {
            ChatResponse response = modelClient.chat(request);

            while (response.hasToolCalls()) {
                String reasoning = response.reasoningContent();
                if (reasoning != null && !reasoning.isEmpty()) {
                    String preview = reasoning.length() > 1000
                            ? reasoning.substring(0, 1000) + "... (思考内容已截断，仅展示前 1000 字符)"
                            : reasoning;
                    terminal.writer().println("[思考] " + preview);
                    terminal.writer().println();
                    terminal.flush();
                }

                session.addMessage(new AssistantMessage(
                        response.content(),
                        response.toolCalls(),
                        response.reasoningContent()
                ));

                for (ToolCall tc : response.toolCalls()) {
                    terminal.writer().println("[执行工具] " + tc.name() + " " + tc.arguments());
                    terminal.flush();

                    try {
                        String result;

                        if ("task".equals(tc.name())) {
                            String taskPrompt = (String) tc.arguments().get("prompt");
                            String desc = (String) tc.arguments().getOrDefault("description", "subtask");
                            terminal.writer().println("[子代理] (" + desc + "): "
                                    + taskPrompt.substring(0, Math.min(80, taskPrompt.length())) + "...");
                            terminal.flush();

                            List<ToolDefinition> childTools = toolRegistry.getAllToolDefinitions();
                            result = subAgentService.runSubAgent(taskPrompt, desc, childTools, toolRegistry);
                        } else {
                            result = toolRegistry.executeTool(tc.name(), tc.arguments());
                        }

                        String preview = result;
                        if (preview.length() > 300) {
                            preview = preview.substring(0, 300) + "... (输出已截断，仅展示前 300 字符)";
                        }
                        terminal.writer().println("[工具结果] " + preview);
                        terminal.writer().println();
                        terminal.flush();

                        if (result.length() > 8000) {
                            result = result.substring(0, 8000) + "\n... (内容已截断)";
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

                request = new ChatRequest(session.getMessages(), parentTools);
                response = modelClient.chat(request);
            }

            if (response.finishReason() != null) {
                ChatRequest finalRequest = new ChatRequest(session.getMessages(), parentTools);

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

            session.trimMessages(20);

        } catch (Exception e) {
            terminal.writer().println("错误: " + e.getMessage());
            terminal.flush();
        }
    }
}
