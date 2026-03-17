package com.dokocli.core.session;

import com.dokocli.core.plan.PlanState;
import com.dokocli.model.api.Message;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 对话会话（含消息与规划状态）
 */
public class Session {
    private final String id;
    private final Instant createdAt;
    private Instant lastActive;
    private final List<Message> messages;
    private PlanState planState;

    public Session() {
        this.id = UUID.randomUUID().toString().substring(0, 8);
        this.createdAt = Instant.now();
        this.lastActive = Instant.now();
        this.messages = new ArrayList<>();
    }

    public String getId() {
        return id;
    }

    public List<Message> getMessages() {
        return new ArrayList<>(messages);
    }

    public void addMessage(Message message) {
        messages.add(message);
        lastActive = Instant.now();
    }

    public void clearMessages() {
        messages.clear();
        if (planState != null) {
            planState.clear();
        }
        lastActive = Instant.now();
    }

    /**
     * 获取当前会话的规划状态（任务列表），首次访问时创建。
     */
    public PlanState getPlanState() {
        if (planState == null) {
            planState = new PlanState();
        }
        return planState;
    }

    public Instant getLastActive() {
        return lastActive;
    }

    /**
     * 简单的上下文窗口管理：保留最近 N 条消息
     */
    public void trimMessages(int maxMessages) {
        if (messages.size() > maxMessages) {
            // 保留系统消息（如果有）和最近的消息
            List<Message> toKeep = new ArrayList<>();
            int start = Math.max(0, messages.size() - maxMessages);
            for (int i = start; i < messages.size(); i++) {
                toKeep.add(messages.get(i));
            }
            messages.clear();
            messages.addAll(toKeep);
        }
    }
}
