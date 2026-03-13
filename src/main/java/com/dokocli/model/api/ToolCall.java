package com.dokocli.model.api;

import java.util.Map;

/**
 * 工具调用定义
 */
public record ToolCall(String id, String name, Map<String, Object> arguments) {}
