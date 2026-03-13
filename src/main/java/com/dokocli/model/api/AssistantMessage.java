package com.dokocli.model.api;

import java.util.List;

/**
 * 助手消息（可能包含工具调用和推理内容）
 * reasoningContent: Kimi K2.5 等 thinking 模型返回的推理过程，带 tool_calls 时必须回传
 */
public record AssistantMessage(String content, List<ToolCall> toolCalls, String reasoningContent) implements Message {
    @Override
    public String role() {
        return "assistant";
    }

    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }
}
