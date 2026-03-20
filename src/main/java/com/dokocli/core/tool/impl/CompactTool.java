package com.dokocli.core.tool.impl;

import com.dokocli.core.tool.AgentTool;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * compact：触发对话压缩。
 * 工具本身只返回确认信息，实际的压缩逻辑由 AgentService 在工具执行后触发。
 */
@Component
public class CompactTool implements AgentTool {

    @Tool(
            name = "compact",
            description = "触发对话压缩。当你觉得对话过长、上下文可能溢出时主动调用，以摘要形式保留关键信息并继续工作。"
    )
    public String compact(
            @ToolParam(description = "希望在摘要中重点保留的内容（可选）", required = false) String focus
    ) {
        return "Compressing...";
    }
}
