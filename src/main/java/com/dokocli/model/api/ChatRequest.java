package com.dokocli.model.api;

import java.util.List;

/**
 * 对话请求
 */
public record ChatRequest(
        List<Message> messages,
        List<ToolDefinition> tools,
        ModelParameters parameters
) {
    public ChatRequest(List<Message> messages) {
        this(messages, null, new ModelParameters());
    }

    public ChatRequest(List<Message> messages, List<ToolDefinition> tools) {
        this(messages, tools, new ModelParameters());
    }
}
