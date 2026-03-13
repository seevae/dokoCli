package com.dokocli.model.api;

/**
 * 用户消息
 */
public record UserMessage(String content) implements Message {
    @Override
    public String role() {
        return "user";
    }
}
