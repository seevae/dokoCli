package com.dokocli.core.tool.impl;

import com.dokocli.core.task.TaskManager;
import com.dokocli.core.tool.AgentTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 任务规划工具集：task_create / task_update / task_list / task_get。
 * 任务以 JSON 文件持久化到 .tasks/ 目录，支持依赖图。
 */
@Component
public class TaskTools implements AgentTool {

    private static final Logger log = LoggerFactory.getLogger(TaskTools.class);

    private final TaskManager taskManager;

    public TaskTools(TaskManager taskManager) {
        this.taskManager = taskManager;
    }

    @Tool(
            name = "task_create",
            description = "Create a new task. Tasks persist as JSON files in .tasks/ directory."
    )
    public String taskCreate(
            @ToolParam(description = "任务主题/标题") String subject,
            @ToolParam(description = "任务详细描述（可选）", required = false) String description
    ) {
        try {
            return taskManager.create(subject, description);
        } catch (Exception e) {
            log.warn("task_create failed: {}", e.toString());
            return "Error: " + e.getMessage();
        }
    }

    @Tool(
            name = "task_update",
            description = "Update a task's status or dependencies. When status is set to 'completed', the task is automatically removed from all other tasks' blockedBy lists."
    )
    public String taskUpdate(
            @ToolParam(description = "任务 ID") Integer taskId,
            @ToolParam(description = "新状态: pending / in_progress / completed（可选）", required = false) String status,
            @ToolParam(description = "新增前置依赖任务 ID 列表（可选）", required = false) List<Integer> addBlockedBy,
            @ToolParam(description = "新增后置依赖任务 ID 列表（可选）", required = false) List<Integer> addBlocks
    ) {
        try {
            return taskManager.update(taskId, status, addBlockedBy, addBlocks);
        } catch (Exception e) {
            log.warn("task_update failed for task {}: {}", taskId, e.toString());
            return "Error: " + e.getMessage();
        }
    }

    @Tool(
            name = "task_list",
            description = "List all tasks with status summary. Shows pending ([ ]), in_progress ([>]), completed ([x]) markers and blocked-by info."
    )
    public String taskList() {
        try {
            return taskManager.listAll();
        } catch (Exception e) {
            log.warn("task_list failed: {}", e.toString());
            return "Error: " + e.getMessage();
        }
    }

    @Tool(
            name = "task_get",
            description = "Get full details of a task by ID, including dependencies and status."
    )
    public String taskGet(
            @ToolParam(description = "任务 ID") Integer taskId
    ) {
        try {
            return taskManager.get(taskId);
        } catch (Exception e) {
            log.warn("task_get failed for task {}: {}", taskId, e.toString());
            return "Error: " + e.getMessage();
        }
    }
}
