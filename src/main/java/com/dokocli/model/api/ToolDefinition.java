package com.dokocli.model.api;

import java.util.Map;

/**
 * 工具定义（用于模型 tool calling）
 */
public record ToolDefinition(
        String name,
        String description,
        Map<String, Object> parameters
) {}
