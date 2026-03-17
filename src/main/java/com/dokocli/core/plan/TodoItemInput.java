package com.dokocli.core.plan;

/**
 * 工具入参：单条待办（由 LLM JSON 反序列化，record 原生支持 Jackson）
 */
public record TodoItemInput(String id, String text, String status) {}
