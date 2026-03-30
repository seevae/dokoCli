package com.dokocli.core.tool.impl;

import com.dokocli.core.background.BackgroundManager;
import com.dokocli.core.tool.AgentTool;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 后台执行工具 —— 让 Agent 可以将耗时命令放到后台线程执行，主线程不阻塞。
 */
@Component
public class BackgroundTools implements AgentTool {

    private final BackgroundManager backgroundManager;

    public BackgroundTools(BackgroundManager backgroundManager) {
        this.backgroundManager = backgroundManager;
    }

    @Tool(
            name = "background_run",
            description = """
                    在后台线程中执行一条耗时的 Shell 命令，立即返回 task_id 而不阻塞当前对话。
                    适合 npm install、docker build、mvn package 等长时间运行的命令。
                    命令完成后结果会自动注入到下一轮对话中。
                    也可以使用 check_background 主动查询任务状态。
                    """
    )
    public String backgroundRun(
            @ToolParam(description = "要在后台执行的 Shell 命令") String command
    ) {
        return backgroundManager.run(command);
    }

    @Tool(
            name = "check_background",
            description = """
                    查询后台任务的执行状态。
                    不提供 task_id 则列出所有后台任务；提供 task_id 则查看指定任务的详细状态和输出。
                    """
    )
    public String checkBackground(
            @ToolParam(description = "要查询的任务 ID（可选，不填则列出全部）", required = false) String taskId
    ) {
        return backgroundManager.check(taskId);
    }
}
