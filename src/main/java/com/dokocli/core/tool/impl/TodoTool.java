package com.dokocli.core.tool.impl;

import com.dokocli.core.plan.PlanState;
import com.dokocli.core.plan.TodoItemInput;
import com.dokocli.core.session.SessionContextHolder;
import com.dokocli.core.tool.AgentTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 规划任务列表工具：更新并展示多步骤任务进度。
 * 依赖当前会话上下文（SessionContextHolder），由 AgentService 在调用前设置。
 */
@Component
public class TodoTool implements AgentTool {

    private static final Logger log = LoggerFactory.getLogger(TodoTool.class);

    @Tool(
            name = "todo",
            description = """
                    更新任务规划列表，追踪多步骤任务进度。
                    收到复杂任务时，第一步应调用此工具列出计划；开始某步前将其设为 in_progress，完成后设为 completed。
                    每条需包含 id（字符串）、text（任务描述）、status（pending / in_progress / completed 三选一）。
                    同时最多一条 in_progress，最多 20 条。
                    """
    )
    public String updateTodos(
            @ToolParam(description = "任务列表，每条包含 id、text、status（pending|in_progress|completed）") List<TodoItemInput> items
    ) {
        if (items == null) {
            return "Error: items required.";
        }
        var session = SessionContextHolder.getSession();
        if (session == null) {
            log.warn("todo tool called without current session");
            return "Error: No session context.";
        }
        try {
            PlanState planState = session.getPlanState();
            return planState.update(items);
        } catch (IllegalArgumentException e) {
            log.debug("todo update validation failed: {}", e.getMessage());
            return "Error: " + e.getMessage();
        }
    }
}
