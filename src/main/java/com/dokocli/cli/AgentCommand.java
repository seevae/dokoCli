package com.dokocli.cli;

import com.dokocli.core.session.Session;
import com.dokocli.core.session.SessionManager;
import com.dokocli.core.tool.ToolRegistry;
import com.dokocli.model.api.*;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * CLI 主命令，处理交互式对话
 */
@Component
public class AgentCommand implements CommandLineRunner {

    private final ModelClient modelClient;
    private final SessionManager sessionManager;
    private final ToolRegistry toolRegistry;

    public AgentCommand(ModelClient modelClient, SessionManager sessionManager, ToolRegistry toolRegistry) {
        this.modelClient = modelClient;
        this.sessionManager = sessionManager;
        this.toolRegistry = toolRegistry;
    }

    @Override
    public void run(String... args) throws Exception {
        Terminal terminal = TerminalBuilder.builder()
                .system(true)
                .build();

        LineReader reader = LineReaderBuilder.builder()
                .terminal(terminal)
                .build();

        printBanner(terminal);

        // 创建新会话
        Session session = sessionManager.createSession();

        // 添加系统提示词
        session.addMessage(new SystemMessage(getSystemPrompt()));

        terminal.writer().println("会话 ID: " + session.getId());
        terminal.writer().println("输入 /help 查看命令，输入 /exit 退出\n");
        terminal.flush();

        while (true) {
            try {
                String input = reader.readLine("doko> ");

                if (input == null || input.trim().isEmpty()) {
                    continue;
                }

                input = input.trim();

                // 处理命令
                if (input.startsWith("/")) {
                    if (handleCommand(input, session, terminal)) {
                        break;
                    }
                    continue;
                }

                // 处理用户输入
                processUserInput(input, session, terminal);

            } catch (UserInterruptException e) {
                terminal.writer().println("\n输入 Ctrl+D 退出");
            } catch (EndOfFileException e) {
                break;
            }
        }

        terminal.writer().println("\n再见！");
    }

    private void processUserInput(String input, Session session, Terminal terminal) {
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

    private boolean handleCommand(String cmd, Session session, Terminal terminal) {
        return switch (cmd) {
            case "/exit", "/quit" -> true;
            case "/help" -> {
                printHelp(terminal);
                yield false;
            }
            case "/clear" -> {
                session.clearMessages();
                session.addMessage(new SystemMessage(getSystemPrompt()));
                terminal.writer().println("对话历史已清空");
                terminal.flush();
                yield false;
            }
            case "/session" -> {
                terminal.writer().println("当前会话 ID: " + session.getId());
                terminal.writer().println("消息数量: " + session.getMessages().size());
                terminal.flush();
                yield false;
            }
            case "/tools" -> {
                terminal.writer().println("可用工具:");
                toolRegistry.getAllToolDefinitions().forEach(t ->
                    terminal.writer().println("  - " + t.name() + ": " + t.description())
                );
                terminal.flush();
                yield false;
            }
            default -> {
                terminal.writer().println("未知命令: " + cmd);
                terminal.writer().println("输入 /help 查看可用命令");
                terminal.flush();
                yield false;
            }
        };
    }

    private void printBanner(Terminal terminal) {
        terminal.writer().println();
        terminal.writer().println("╔═══════════════════════════════════╗");
        terminal.writer().println("║          Doko CLI                 ║");
        terminal.writer().println("║     AI Powered Code Assistant     ║");
        terminal.writer().println("╚═══════════════════════════════════╝");
        terminal.writer().println("Model: " + modelClient.getProvider());
        terminal.writer().println();
        terminal.flush();
    }

    private void printHelp(Terminal terminal) {
        terminal.writer().println();
        terminal.writer().println("可用命令:");
        terminal.writer().println("  /help    - 显示帮助");
        terminal.writer().println("  /clear   - 清空对话历史");
        terminal.writer().println("  /session - 显示会话信息");
        terminal.writer().println("  /tools   - 显示可用工具");
        terminal.writer().println("  /exit    - 退出程序");
        terminal.writer().println();
        terminal.flush();
    }

    private String getSystemPrompt() {
        return """
            你是一个名为 Doko 的 AI 编程助手。你可以帮助用户完成各种编程任务。

            你只有一个工具可用：
            - execute_bash: 执行任意 Bash 命令

            通过 Bash 命令你可以完成所有操作：
            - 读文件: cat /path/to/file
            - 写文件: echo 'content' > /path/to/file
            - 编辑文件: sed -i 's/old/new/' /path/to/file
            - 浏览目录: ls -la /path
            - 创建目录: mkdir -p /path/to/dir
            - 文件搜索: grep -r 'keyword' /path
            - Git 操作: git status, git diff, git log, git add, git commit
            - 运行代码: python, node, mvn, gradle 等

            注意：
            1. 使用绝对路径操作文件
            2. 敏感操作（rm -rf）前请确认
            3. 避免交互式命令（如 vim）
            """;
    }
}
