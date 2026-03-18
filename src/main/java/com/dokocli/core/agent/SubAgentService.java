package com.dokocli.core.agent;

import com.dokocli.core.session.Session;
import com.dokocli.core.tool.ToolRegistry;
import com.dokocli.model.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 子代理执行器（对应 Python 的 run_subagent）。
 * 创建干净上下文的子会话，执行工具调用循环，最后只返回最终文本总结。
 * <p>
 * 只依赖 ModelClient；childTools 和 toolRegistry 由调用方（AgentService）传入，
 * 从而避免循环依赖，也与 Python 版本的设计保持一致。
 */
@Service
public class SubAgentService {

    private static final Logger log = LoggerFactory.getLogger(SubAgentService.class);

    private static final String SUBAGENT_SYSTEM_PROMPT = """
            你是 Doko CLI 的子代理（subagent）。

            重要约束：
            - 你不会看到父代理的对话历史；你只会收到当前这条子任务提示。
            - 你与父代理共享同一个工作区/文件系统；你可以调用工具完成探索与改动。
            - 你不能调用 task（不允许递归派发）。

            工作方式：
            - 优先用 read_file / edit_file / write_file 做精确文件操作；需要复杂操作再用 execute_bash。
            - 避免交互式命令（例如 vim），避免输出过长内容；必要时只摘录关键片段。

            输出要求（最终回复）：
            - 只输出"总结"，不要输出长篇思考。
            - 总结应包含：做了什么、关键发现、涉及的文件/命令（如有）、以及需要父代理继续推进的下一步（如有）。
            """;

    private static final int MAX_TOOL_LOOPS = 30;
    private static final int MAX_TOOL_RESULT_LENGTH = 8000;

    private final ModelClient modelClient;

    public SubAgentService(ModelClient modelClient) {
        this.modelClient = modelClient;
    }

    /**
     * run_subagent
     *
     * @param prompt       子任务提示词
     * @param description  子任务简短描述（可选，会拼入 prompt）
     * @param childTools   子代理可用的工具定义（不含 task）—— 对应 Python CHILD_TOOLS
     * @param toolRegistry 工具执行器 —— 对应 Python TOOL_HANDLERS
     */
    public String runSubAgent(String prompt, String description,
                              List<ToolDefinition> childTools,
                              ToolRegistry toolRegistry) {
        String effectivePrompt = prompt;
        if (description != null && !description.isBlank()) {
            effectivePrompt = "Task description: " + description.trim() + "\n\n" + prompt;
        }

        Session subSession = new Session();
        subSession.addMessage(new SystemMessage(SUBAGENT_SYSTEM_PROMPT));
        subSession.addMessage(new UserMessage(effectivePrompt));

        ChatRequest request = new ChatRequest(subSession.getMessages(), childTools);
        ChatResponse response = modelClient.chat(request);

        for (int i = 0; i < MAX_TOOL_LOOPS && response.hasToolCalls(); i++) {
            subSession.addMessage(new AssistantMessage(
                    response.content(),
                    response.toolCalls(),
                    response.reasoningContent()
            ));

            for (ToolCall tc : response.toolCalls()) {
                String result;
                try {
                    result = toolRegistry.executeTool(tc.name(), tc.arguments());
                } catch (Exception e) {
                    result = "执行失败: " + e.getMessage();
                }

                if (result != null && result.length() > MAX_TOOL_RESULT_LENGTH) {
                    result = result.substring(0, MAX_TOOL_RESULT_LENGTH) + "\n... (内容已截断)";
                }
                subSession.addMessage(new ToolMessage(tc.id(), result == null ? "" : result));
            }

            subSession.trimMessages(40);
            request = new ChatRequest(subSession.getMessages(), childTools);
            response = modelClient.chat(request);
        }

        String summary = response != null ? response.content() : null;
        return (summary == null || summary.isBlank()) ? "(no summary)" : "[--subagent tool--]" + summary;
    }
}
