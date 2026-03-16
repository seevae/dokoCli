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
                // 打印模型思考过程（reasoning_content），帮助用户理解为什么要调用这些工具
                String reasoning = response.reasoningContent();
                if (reasoning != null && !reasoning.isEmpty()) {
                    String preview = reasoning.length() > 1000
                            ? reasoning.substring(0, 1000) + "... (思考内容已截断，仅展示前 1000 字符)"
                            : reasoning;
                    terminal.writer().println("[思考] " + preview);
                    terminal.writer().println();
                    terminal.flush();
                }

                // 添加助手消息（包含工具调用和推理内容，reasoning_content 在下一轮请求中必须回传）
                session.addMessage(new AssistantMessage(
                        response.content(),
                        response.toolCalls(),
                        response.reasoningContent()
                ));

                // 执行工具（同时在终端中展示更详细的执行过程）
                for (ToolCall tc : response.toolCalls()) {
                    // 显示工具名和参数概览
                    terminal.writer().println("[执行工具] " + tc.name() + " " + tc.arguments());
                    terminal.flush();

                    try {
                        String result = toolRegistry.executeTool(tc.name(), tc.arguments());

                        // 终端中展示一小段结果预览，方便用户理解工具做了什么
                        String preview = result;
                        if (preview.length() > 300) {
                            preview = preview.substring(0, 300) + "... (输出已截断，仅展示前 300 字符)";
                        }
                        terminal.writer().println("[工具结果] " + preview);
                        terminal.writer().println();
                        terminal.flush();

                        // 截断过长的结果后再写入对话上下文，避免上下文过大
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

                // 再次调用模型获取回复
                request = new ChatRequest(
                        session.getMessages(),
                        toolRegistry.getAllToolDefinitions()
                );
                response = modelClient.chat(request);
            }

            // 最终回复阶段：使用流式输出，让内容逐步显示
            if (response.finishReason() != null) {
                // 使用当前会话重新发起一次仅用于流式输出的请求
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

            // 简单的上下文管理
            session.trimMessages(20);

        } catch (Exception e) {
            terminal.writer().println("错误: " + e.getMessage());
            terminal.flush();
        }
    }
}

