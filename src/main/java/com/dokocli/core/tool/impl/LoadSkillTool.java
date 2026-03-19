package com.dokocli.core.tool.impl;

import com.dokocli.core.skill.SkillLoader;
import com.dokocli.core.tool.AgentTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * load_skill：按名称加载专项知识（Layer 2）。
 * 模型在需要某领域知识时调用此工具，获取完整的 skill 指令。
 */
@Component
public class LoadSkillTool implements AgentTool {

    private static final Logger log = LoggerFactory.getLogger(LoadSkillTool.class);

    private final SkillLoader skillLoader;

    public LoadSkillTool(SkillLoader skillLoader) {
        this.skillLoader = skillLoader;
    }

    @Tool(
            name = "load_skill",
            description = """
                    Load specialized knowledge/instructions by skill name.
                    Use this tool before tackling unfamiliar topics to get step-by-step guidance.
                    Returns the full skill body with detailed instructions.
                    """
    )
    public String loadSkill(
            @ToolParam(description = "Skill name to load (see system prompt for available skills)") String name
    ) {
        log.info("Loading skill: {}", name);
        return skillLoader.getContent(name);
    }
}
