package com.dokocli.core.tool;

import java.util.Map;

/**
 * 工具执行器函数式接口
 */
@FunctionalInterface
public interface ToolExecutor {
    String execute(Map<String, Object> arguments) throws Exception;
}
