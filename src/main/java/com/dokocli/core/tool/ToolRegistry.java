package com.dokocli.core.tool;

import com.dokocli.tool.bash.BashTools;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;

/**
 * 工具注册中心
 */
@Component
public class ToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(ToolRegistry.class);

    private final Map<String, ToolDefinition> tools = new HashMap<>();
    private final ObjectMapper objectMapper;
    private final BashTools bashTools;

    public ToolRegistry(ObjectMapper objectMapper, BashTools bashTools) {
        this.objectMapper = objectMapper;
        this.bashTools = bashTools;
    }

    @PostConstruct
    public void init() {
        registerToolsFromBean(bashTools);
        log.info("Registered {} tools: {}", tools.size(), tools.keySet());
    }

    private void registerToolsFromBean(Object bean) {
        for (Method method : bean.getClass().getMethods()) {
            Tool annotation = method.getAnnotation(Tool.class);
            if (annotation != null) {
                registerTool(bean, method, annotation);
            }
        }
    }

    private void registerTool(Object bean, Method method, Tool annotation) {
        // 生成参数 schema
        Map<String, Object> schema = buildParameterSchema(method);

        // 创建执行器
        ToolExecutor executor = args -> {
            try {
                Object[] params = resolveParameters(method, args);
                Object result = method.invoke(bean, params);
                return result != null ? result.toString() : "";
            } catch (Exception e) {
                throw new RuntimeException("Tool execution failed: " + annotation.name(), e);
            }
        };

        ToolDefinition definition = new ToolDefinition(
                annotation.name(),
                annotation.description(),
                schema,
                executor
        );

        tools.put(annotation.name(), definition);
        log.debug("Registered tool: {}", annotation.name());
    }

    private Map<String, Object> buildParameterSchema(Method method) {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();

        for (Parameter param : method.getParameters()) {
            ToolParameter tp = param.getAnnotation(ToolParameter.class);
            if (tp == null) continue;

            Map<String, Object> prop = new HashMap<>();
            prop.put("type", mapJavaTypeToJsonType(param.getType()));
            prop.put("description", tp.description());
            properties.put(param.getName(), prop);

            if (tp.required()) {
                required.add(param.getName());
            }
        }

        schema.put("properties", properties);
        if (!required.isEmpty()) {
            schema.put("required", required);
        }

        return schema;
    }

    private String mapJavaTypeToJsonType(Class<?> type) {
        if (type == String.class) return "string";
        if (type == int.class || type == Integer.class) return "integer";
        if (type == boolean.class || type == Boolean.class) return "boolean";
        if (type == double.class || type == Double.class) return "number";
        return "string";
    }

    private Object[] resolveParameters(Method method, Map<String, Object> args) {
        Parameter[] params = method.getParameters();
        Object[] result = new Object[params.length];

        for (int i = 0; i < params.length; i++) {
            ToolParameter tp = params[i].getAnnotation(ToolParameter.class);
            if (tp != null) {
                Object value = args.get(params[i].getName());
                result[i] = convertValue(value, params[i].getType());
            }
        }

        return result;
    }

    private Object convertValue(Object value, Class<?> targetType) {
        if (value == null) return null;
        if (targetType.isInstance(value)) return value;
        if (targetType == String.class) return value.toString();
        if (targetType == int.class || targetType == Integer.class) {
            return ((Number) value).intValue();
        }
        if (targetType == boolean.class || targetType == Boolean.class) {
            return Boolean.valueOf(value.toString());
        }
        return objectMapper.convertValue(value, targetType);
    }

    public ToolDefinition getTool(String name) {
        return tools.get(name);
    }

    public List<com.dokocli.model.api.ToolDefinition> getAllToolDefinitions() {
        return tools.values().stream()
                .map(t -> new com.dokocli.model.api.ToolDefinition(
                        t.name(), t.description(), t.parametersSchema()
                ))
                .toList();
    }

    public String executeTool(String name, Map<String, Object> arguments) throws Exception {
        ToolDefinition tool = tools.get(name);
        if (tool == null) {
            throw new IllegalArgumentException("Unknown tool: " + name);
        }
        return tool.executor().execute(arguments);
    }
}
