package com.dokocli.core.agent;

import com.dokocli.core.session.Session;
import com.dokocli.core.tool.ToolRegistry;
import com.dokocli.model.api.*;
import org.jline.terminal.Terminal;
import org.springframework.stereotype.Service;

/**
 * 核心 Agent 编排逻辑（agent loop）。
 * 负责：
 * - 维护会话消息
 * - 调用大模型
 * - 处理工具调用并回填结果
 * CLI、HTTP 等上层只需要调用此服务。
 */
@Service
public class AgentService {

    private final ModelClient modelClient;
    private final ToolRegistry toolRegistry;

    public AgentService(ModelClient modelClient, ToolRegistry toolRegistry) {
        this.modelClient = modelClient;
        this.toolRegistry = toolRegistry;
    }

    /**
     * 处理用户一次输入，完成完整的 agent loop（包括工具调用）。
     */
    public void processUserInput(String input, Session session, Terminal terminal) {
        // 添加用户消息
        session.addMessage(new UserMessage(input));

        // 调用模型
        ChatRequest request = new ChatRequest(
                session.getMessages(),
                toolRegistry.getAllToolDefinitions()
        );

        terminal.writer().print("\n");
        terminal.flush();

        try {
            ChatResponse response = modelClient.chat(request);

            // 处理工具调用
            while (response.hasToolCalls()) {
                // 添加助手消息（包含工具调用和推理内容，reasoning_content 在下一轮请求中必须回传）
                session.addMessage(new AssistantMessage(
                        response.content(),
                        response.toolCalls(),
                        response.reasoningContent()
                ));

                // 执行工具
                for (ToolCall tc : response.toolCalls()) {
                    terminal.writer().println("[执行工具] " + tc.name());
                    terminal.flush();

                    try {
                        String result = toolRegistry.executeTool(tc.name(), tc.arguments());

                        // 截断过长的结果
                        if (result.length() > 8000) {
                            result = result.substring(0, 8000) + "\n... (内容已截断)";
                        }

                        session.addMessage(new ToolMessage(tc.id(), result));
                    } catch (Exception e) {
                        String error = "执行失败: " + e.getMessage();
                        session.addMessage(new ToolMessage(tc.id(), error));
                    }
                }

                // 再次调用模型获取回复
                request = new ChatRequest(
                        session.getMessages(),
                        toolRegistry.getAllToolDefinitions()
                );
                response = modelClient.chat(request);
            }

            // 输出最终回复
            String content = response.content();
            if (content != null && !content.isEmpty()) {
                terminal.writer().println(content);
                terminal.writer().println();
                terminal.flush();

                session.addMessage(new AssistantMessage(content, null, null));
            }

            // 简单的上下文管理
            session.trimMessages(20);

        } catch (Exception e) {
            terminal.writer().println("错误: " + e.getMessage());
            terminal.flush();
        }
    }
}

