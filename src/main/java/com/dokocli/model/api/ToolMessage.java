package com.dokocli.model.api;

/**
 * 工具调用结果消息
 */
public record ToolMessage(String toolCallId, String content) implements Message {
    @Override
    public String role() {
        return "tool";
    }
}
