package com.dokocli.model.api;

import java.util.stream.Stream;

/**
 * 模型客户端统一接口，所有模型实现此接口
 */
public interface ModelClient {

    /**
     * 同步对话
     */
    ChatResponse chat(ChatRequest request);

    /**
     * 流式对话
     */
    Stream<ChatChunk> stream(ChatRequest request);

    /**
     * 获取模型能力
     */
    ModelCapabilities getCapabilities();

    /**
     * 获取提供商名称
     */
    String getProvider();
}
