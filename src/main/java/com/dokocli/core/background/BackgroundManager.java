package com.dokocli.core.background;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 后台任务管理器：在独立线程中运行耗时命令，主线程不阻塞。
 * <p>
 * 完成的任务结果会被推入通知队列，由 AgentService 在每次 LLM 调用前排空并注入到对话上下文。
 *
 * <pre>
 *   Main thread                Background thread
 *   +-----------------+        +-----------------+
 *   | agent loop      |        | task executes   |
 *   | ...             |        | ...             |
 *   | [LLM call] <---+------- | enqueue(result) |
 *   |  ^drain queue   |        +-----------------+
 *   +-----------------+
 * </pre>
 */
@Component
public class BackgroundManager {

    private static final Logger log = LoggerFactory.getLogger(BackgroundManager.class);

    private static final int DEFAULT_TIMEOUT_SECONDS = 300;
    private static final int MAX_OUTPUT_LENGTH = 50000;
    private static final int NOTIFICATION_RESULT_PREVIEW = 500;

    private final ConcurrentHashMap<String, TaskInfo> tasks = new ConcurrentHashMap<>();
    private final List<TaskNotification> notificationQueue = new ArrayList<>();
    private final Object lock = new Object();

    public String run(String command) {
        String taskId = UUID.randomUUID().toString().substring(0, 8);
        tasks.put(taskId, new TaskInfo("running", null, command));

        Thread thread = new Thread(() -> execute(taskId, command));
        thread.setDaemon(true);
        thread.setName("bg-task-" + taskId);
        thread.start();

        return "后台任务 " + taskId + " 已启动: " + truncate(command, 80);
    }

    private void execute(String taskId, String command) {
        String status;
        String output;
        try {
            ProcessBuilder pb = new ProcessBuilder("/bin/bash", "-c", command);
            pb.redirectErrorStream(true);

            Process process = pb.start();

            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
            }

            boolean finished = process.waitFor(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                output = "错误: 超时（" + DEFAULT_TIMEOUT_SECONDS + "s）\n部分输出:\n" + sb;
                status = "timeout";
            } else {
                output = sb.toString().trim();
                if (output.length() > MAX_OUTPUT_LENGTH) {
                    output = output.substring(0, MAX_OUTPUT_LENGTH) + "\n... (输出已截断)";
                }
                status = process.exitValue() == 0 ? "completed" : "completed(exit=" + process.exitValue() + ")";
            }
        } catch (Exception e) {
            output = "错误: " + e.getMessage();
            status = "error";
            log.error("后台任务 {} 执行异常", taskId, e);
        }

        if (output == null || output.isEmpty()) {
            output = "(无输出)";
        }

        tasks.put(taskId, new TaskInfo(status, output, command));

        synchronized (lock) {
            notificationQueue.add(new TaskNotification(
                    taskId, status,
                    truncate(command, 80),
                    truncate(output, NOTIFICATION_RESULT_PREVIEW)
            ));
        }
        log.info("后台任务 {} 完成, status={}", taskId, status);
    }

    public String check(String taskId) {
        if (taskId != null && !taskId.isBlank()) {
            TaskInfo info = tasks.get(taskId);
            if (info == null) {
                return "错误: 未知任务 " + taskId;
            }
            String result = info.result() != null ? info.result() : "(运行中)";
            return "[" + info.status() + "] " + truncate(info.command(), 60) + "\n" + result;
        }
        if (tasks.isEmpty()) {
            return "当前没有后台任务。";
        }
        StringBuilder sb = new StringBuilder();
        tasks.forEach((id, info) ->
                sb.append(id).append(": [").append(info.status()).append("] ")
                        .append(truncate(info.command(), 60)).append("\n")
        );
        return sb.toString().trim();
    }

    public List<TaskNotification> drainNotifications() {
        synchronized (lock) {
            if (notificationQueue.isEmpty()) {
                return List.of();
            }
            List<TaskNotification> result = new ArrayList<>(notificationQueue);
            notificationQueue.clear();
            return result;
        }
    }

    public boolean hasTasks() {
        return !tasks.isEmpty();
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }

    public record TaskInfo(String status, String result, String command) {}

    public record TaskNotification(String taskId, String status, String command, String result) {}
}
