package com.dokocli.core.tool;

import com.dokocli.model.api.ToolDefinition;
import com.dokocli.tool.bash.BashTools;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 工具注册中心（基于 Spring AI ToolCallbacks）
 */
@Component
public class ToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(ToolRegistry.class);
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final ToolCallback[] callbacks;
    private final ObjectMapper objectMapper;

    public ToolRegistry(BashTools bashTools, ObjectMapper objectMapper) {
        this.callbacks = ToolCallbacks.from(bashTools);
        this.objectMapper = objectMapper;
        log.info("Registered {} tools (Spring AI): {}", callbacks.length,
                List.of(callbacks).stream().map(c -> c.getToolDefinition().name()).toList());
    }

    public List<ToolDefinition> getAllToolDefinitions() {
        return List.of(callbacks).stream()
                .map(cb -> toApiToolDefinition(cb.getToolDefinition()))
                .toList();
    }

    public String executeTool(String name, Map<String, Object> arguments) throws Exception {
        ToolCallback callback = findCallback(name);
        if (callback == null) {
            throw new IllegalArgumentException("Unknown tool: " + name);
        }
        String toolInput = objectMapper.writeValueAsString(arguments);
        return callback.call(toolInput);
    }

    private ToolCallback findCallback(String name) {
        for (ToolCallback cb : callbacks) {
            if (cb.getToolDefinition().name().equals(name)) {
                return cb;
            }
        }
        return null;
    }

    private ToolDefinition toApiToolDefinition(org.springframework.ai.tool.definition.ToolDefinition def) {
        Map<String, Object> parameters;
        try {
            String schema = def.inputSchema();
            parameters = schema != null && !schema.isBlank()
                    ? objectMapper.readValue(schema, MAP_TYPE)
                    : Map.of("type", "object", "properties", Map.of());
        } catch (Exception e) {
            log.warn("Failed to parse inputSchema for tool {}: {}", def.name(), e.getMessage());
            parameters = Map.of("type", "object", "properties", Map.of());
        }
        return new ToolDefinition(def.name(), def.description(), parameters);
    }
}
