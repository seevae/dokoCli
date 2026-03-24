package com.dokocli.core.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static com.dokocli.core.tool.FileToolUtils.WORKDIR;

/**
 * 任务管理器：CRUD + 依赖图，每个任务持久化为 .tasks/task_{id}.json。
 * 与 Python 版 TaskManager 行为完全一致。
 */
@Component
public class TaskManager {

    private static final Logger log = LoggerFactory.getLogger(TaskManager.class);

    private final Path tasksDir;
    private final ObjectMapper objectMapper;
    private int nextId;

    public TaskManager(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.tasksDir = WORKDIR.resolve(".tasks");
        try {
            Files.createDirectories(tasksDir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create .tasks directory", e);
        }
        this.nextId = maxId() + 1;
        log.info("TaskManager initialized, tasksDir={}, nextId={}", tasksDir, nextId);
    }

    private int maxId() {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(tasksDir, "task_*.json")) {
            int max = 0;
            for (Path f : stream) {
                String name = f.getFileName().toString();
                String idStr = name.substring("task_".length(), name.length() - ".json".length());
                try {
                    max = Math.max(max, Integer.parseInt(idStr));
                } catch (NumberFormatException ignored) {
                }
            }
            return max;
        } catch (IOException e) {
            return 0;
        }
    }

    private Task load(int taskId) throws IOException {
        Path path = tasksDir.resolve("task_" + taskId + ".json");
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("Task " + taskId + " not found");
        }
        return objectMapper.readValue(Files.readString(path), Task.class);
    }

    private void save(Task task) throws IOException {
        Path path = tasksDir.resolve("task_" + task.getId() + ".json");
        Files.writeString(path, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(task));
    }

    public String create(String subject, String description) throws IOException {
        Task task = new Task();
        task.setId(nextId);
        task.setSubject(subject);
        task.setDescription(description != null ? description : "");
        save(task);
        nextId++;
        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(task);
    }

    public String get(int taskId) throws IOException {
        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(load(taskId));
    }

    public String update(int taskId, String status,
                         List<Integer> addBlockedBy, List<Integer> addBlocks) throws IOException {
        Task task = load(taskId);

        if (status != null) {
            if (!List.of("pending", "in_progress", "completed").contains(status)) {
                throw new IllegalArgumentException("Invalid status: " + status);
            }
            task.setStatus(status);
            if ("completed".equals(status)) {
                clearDependency(taskId);
            }
        }

        if (addBlockedBy != null && !addBlockedBy.isEmpty()) {
            Set<Integer> merged = new LinkedHashSet<>(task.getBlockedBy());
            merged.addAll(addBlockedBy);
            task.setBlockedBy(new ArrayList<>(merged));
        }

        if (addBlocks != null && !addBlocks.isEmpty()) {
            Set<Integer> merged = new LinkedHashSet<>(task.getBlocks());
            merged.addAll(addBlocks);
            task.setBlocks(new ArrayList<>(merged));

            for (int blockedId : addBlocks) {
                try {
                    Task blocked = load(blockedId);
                    if (!blocked.getBlockedBy().contains(taskId)) {
                        blocked.getBlockedBy().add(taskId);
                        save(blocked);
                    }
                } catch (Exception ignored) {
                }
            }
        }

        save(task);
        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(task);
    }

    /**
     * 当任务完成时，将该任务 ID 从所有其他任务的 blockedBy 列表中移除。
     */
    private void clearDependency(int completedId) throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(tasksDir, "task_*.json")) {
            for (Path f : stream) {
                Task task = objectMapper.readValue(Files.readString(f), Task.class);
                if (task.getBlockedBy().contains(completedId)) {
                    task.getBlockedBy().remove(Integer.valueOf(completedId));
                    save(task);
                }
            }
        }
    }

    public String listAll() throws IOException {
        List<Task> tasks = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(tasksDir, "task_*.json")) {
            List<Path> files = new ArrayList<>();
            stream.forEach(files::add);
            files.sort(Comparator.comparing(Path::getFileName));
            for (Path f : files) {
                tasks.add(objectMapper.readValue(Files.readString(f), Task.class));
            }
        }

        if (tasks.isEmpty()) {
            return "No tasks.";
        }

        StringBuilder sb = new StringBuilder();
        for (Task t : tasks) {
            String marker = switch (t.getStatus()) {
                case "pending" -> "[ ]";
                case "in_progress" -> "[>]";
                case "completed" -> "[x]";
                default -> "[?]";
            };
            sb.append(marker).append(" #").append(t.getId()).append(": ").append(t.getSubject());
            if (t.getBlockedBy() != null && !t.getBlockedBy().isEmpty()) {
                sb.append(" (blocked by: ").append(t.getBlockedBy()).append(")");
            }
            sb.append("\n");
        }
        return sb.toString().trim();
    }
}
