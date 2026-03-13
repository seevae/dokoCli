package com.dokocli.core.tool;

import java.util.Map;

/**
 * 工具定义内部表示
 */
public record ToolDefinition(
        String name,
        String description,
        Map<String, Object> parametersSchema,
        ToolExecutor executor
) {}
