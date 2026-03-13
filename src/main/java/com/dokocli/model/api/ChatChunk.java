package com.dokocli.model.api;

/**
 * 流式响应块
 */
public record ChatChunk(
        String content,
        String finishReason
) {
    public boolean isFinished() {
        return finishReason != null;
    }
}
