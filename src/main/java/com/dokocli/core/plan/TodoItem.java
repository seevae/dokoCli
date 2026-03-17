package com.dokocli.core.plan;

/**
 * 规划中的单条任务（内部状态）
 */
public record TodoItem(String id, String text, TodoStatus status) {}
