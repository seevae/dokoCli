package com.dokocli.model.api;

import java.util.List;

/**
 * 对话响应
 */
public record ChatResponse(
        String content,
        List<ToolCall> toolCalls,
        String reasoningContent,
        Usage usage,
        String finishReason
) {
    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }

    public record Usage(int promptTokens, int completionTokens, int totalTokens) {}
}
