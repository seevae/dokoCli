package com.dokocli.cli;

import com.dokocli.core.agent.AgentService;
import com.dokocli.core.session.Session;
import com.dokocli.core.session.SessionManager;
import com.dokocli.core.skill.SkillLoader;
import com.dokocli.core.tool.ToolRegistry;
import com.dokocli.model.api.SystemMessage;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class AgentCommand implements CommandLineRunner {

    private final AgentService agentService;
    private final SessionManager sessionManager;
    private final ToolRegistry toolRegistry;
    private final SkillLoader skillLoader;

    public AgentCommand(AgentService agentService, SessionManager sessionManager,
                        ToolRegistry toolRegistry, SkillLoader skillLoader) {
        this.agentService = agentService;
        this.sessionManager = sessionManager;
        this.toolRegistry = toolRegistry;
        this.skillLoader = skillLoader;
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

        Session session = sessionManager.createSession();
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

                if (input.startsWith("/")) {
                    if (handleCommand(input, session, terminal)) {
                        break;
                    }
                    continue;
                }

                agentService.processUserInput(input, session, terminal);

            } catch (UserInterruptException e) {
                terminal.writer().println("\n输入 Ctrl+D 退出");
            } catch (EndOfFileException e) {
                break;
            }
        }

        terminal.writer().println("\n再见！");
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
            case "/skills" -> {
                terminal.writer().println("可用技能 (Skills):");
                if (skillLoader.hasSkills()) {
                    terminal.writer().println(skillLoader.getDescriptions());
                } else {
                    terminal.writer().println("  (暂无可用技能，请在 skills/ 目录下添加 SKILL.md)");
                }
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
        terminal.writer().println("Model: " + toolRegistry.getClass().getSimpleName());
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
        terminal.writer().println("  /skills  - 显示可用技能");
        terminal.writer().println("  /exit    - 退出程序");
        terminal.writer().println();
        terminal.flush();
    }

    private String getSystemPrompt() {
        String skillSection = skillLoader.hasSkills()
                ? "\n\nSkills available (use load_skill to access detailed instructions):\n"
                  + skillLoader.getDescriptions()
                  + "\nUse load_skill to access specialized knowledge before tackling unfamiliar topics."
                : "";

        return "你是一个名为 Doko 的 CLI 编程助手，在当前仓库中协助完成开发/排障/改造任务。\n"
                + "\n"
                + "可用工具（function tools）：\n"
                + "- execute_bash: 执行 Bash 命令（适合复杂操作；避免交互式命令）\n"
                + "- read_file: 读取工作目录中的文件内容（推荐优先使用）\n"
                + "- write_file: 写入文件（覆盖原内容；必要时自动创建父目录）\n"
                + "- edit_file: 精确替换文件中第一次出现的指定文本\n"
                + "- task: 派发子任务给子代理（子代理不继承当前对话历史，仅返回总结）\n"
                + "- load_skill: 按名称加载专项知识/指令（遇到不熟悉的领域时优先使用）\n"
                + "- compact: 主动触发对话压缩（当你觉得上下文过长时调用，以摘要形式保留关键信息）\n"
                + "\n"
                + "工具使用原则：\n"
                + "- 优先用 read_file / edit_file / write_file 做可控、可回溯的改动；需要批量/复杂操作再用 execute_bash。\n"
                + "- 路径优先使用工作目录下相对路径；输出过长时要截断并说明。\n"
                + "- 避免交互式命令（如 vim），避免危险命令（如 rm -rf）与不可逆操作。\n"
                + "\n"
                + "task（子代理）使用建议：\n"
                + "- 适合：并行探索、定位代码入口、验证假设、快速梳理实现步骤、生成修改摘要。\n"
                + "- 用法：在 prompt 中明确\u201c目标 / 范围 / 期望输出格式 / 禁止事项\u201d。\n"
                + "- 你需要把子代理的总结整合回主答案，并给出下一步行动。\n"
                + skillSection;
    }
}
