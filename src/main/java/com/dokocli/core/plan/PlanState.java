package com.dokocli.core.plan;

import java.util.ArrayList;
import java.util.List;

/**
 * 规划状态：维护当前会话的任务列表，供 LLM 通过 todo 工具更新与查看。
 * 规则：最多 20 条；同时只能有一条 in_progress。
 */
public class PlanState {

    private static final int MAX_ITEMS = 20;

    private final List<TodoItem> items = new ArrayList<>();

    /**
     * 使用 LLM 传入的列表全量更新任务，校验通过后替换当前列表。
     *
     * @return 更新后的可读摘要（作为工具结果返回给模型）
     * @throws IllegalArgumentException 校验失败
     */
    public String update(List<TodoItemInput> inputs) {
        if (inputs == null) {
            throw new IllegalArgumentException("items required");
        }
        if (inputs.size() > MAX_ITEMS) {
            throw new IllegalArgumentException("Max " + MAX_ITEMS + " todos allowed");
        }

        int inProgressCount = 0;
        List<TodoItem> validated = new ArrayList<>(inputs.size());

        for (int i = 0; i < inputs.size(); i++) {
            TodoItemInput in = inputs.get(i);
            String id = in.id() != null ? in.id() : String.valueOf(i + 1);
            String text = in.text() != null ? in.text().trim() : "";
            if (text.isEmpty()) {
                throw new IllegalArgumentException("Item " + id + ": text required");
            }
            TodoStatus status = parseStatus(in.status(), id);
            if (status == TodoStatus.in_progress) {
                inProgressCount++;
            }
            validated.add(new TodoItem(id, text, status));
        }

        if (inProgressCount > 1) {
            throw new IllegalArgumentException("Only one task can be in_progress at a time");
        }

        items.clear();
        items.addAll(validated);
        return render();
    }

    public String render() {
        if (items.isEmpty()) {
            return "No todos.";
        }
        StringBuilder sb = new StringBuilder();
        long done = 0;
        for (TodoItem item : items) {
            String marker = switch (item.status()) {
                case pending -> "[ ]";
                case in_progress -> "[>]";
                case completed -> "[x]";
            };
            sb.append(marker).append(" #").append(item.id()).append(": ").append(item.text()).append('\n');
            if (item.status() == TodoStatus.completed) {
                done++;
            }
        }
        sb.append("\n(").append(done).append('/').append(items.size()).append(" completed)");
        return sb.toString();
    }

    public List<TodoItem> getItems() {
        return List.copyOf(items);
    }

    public void clear() {
        items.clear();
    }

    private static TodoStatus parseStatus(String raw, String itemId) {
        if (raw == null || raw.isBlank()) {
            return TodoStatus.pending;
        }
        try {
            return TodoStatus.valueOf(raw.trim().toLowerCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Item " + itemId + ": invalid status '" + raw + "'");
        }
    }
}
