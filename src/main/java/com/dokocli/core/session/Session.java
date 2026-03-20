package com.dokocli.core.session;

import com.dokocli.model.api.Message;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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

    public Message getMessage(int index) {
        return messages.get(index);
    }

    public int messageCount() {
        return messages.size();
    }

    public void addMessage(Message message) {
        messages.add(message);
        lastActive = Instant.now();
    }

    public void replaceMessage(int index, Message message) {
        messages.set(index, message);
        lastActive = Instant.now();
    }

    public void replaceAllMessages(List<Message> newMessages) {
        messages.clear();
        messages.addAll(newMessages);
        lastActive = Instant.now();
    }

    public void clearMessages() {
        messages.clear();
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
