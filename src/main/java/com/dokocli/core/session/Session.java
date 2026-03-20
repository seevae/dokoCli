package com.dokocli.core.session;

import com.dokocli.model.api.Message;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 对话会话
 */
public class Session {
    private final String id;
    private final Instant createdAt;
    private Instant lastActive;
    private final List<Message> messages;

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
        lastActive = Instant.now();
    }

    /**
     * 原地修改消息列表（例如上下文微压缩），用于避免 {@link #getMessages()} 返回副本导致无法持久化的问题。
     */
    public void mutateMessages(Consumer<List<Message>> mutator) {
        mutator.accept(messages);
        lastActive = Instant.now();
    }

    /**
     * 用新列表整体替换当前历史（例如全文摘要压缩后重建上下文）。
     */
    public void replaceMessages(List<Message> newMessages) {
        messages.clear();
        messages.addAll(newMessages);
        lastActive = Instant.now();
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
