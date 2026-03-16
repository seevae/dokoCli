package com.dokocli.core.tool.impl;

import com.dokocli.core.tool.AgentTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.dokocli.core.tool.FileToolUtils.safePath;

/**
 * edit_file：在文件中查找并替换第一次出现的指定文本。
 */
@Component
public class EditFileTool implements AgentTool {

    private static final Logger log = LoggerFactory.getLogger(EditFileTool.class);

    @Tool(
            name = "edit_file",
            description = """
                    在文件中查找并替换第一次出现的指定文本。
                    如果未找到要替换的文本，将返回错误信息。
                    """
    )
    public String editFile(
            @ToolParam(description = "要编辑的文件相对路径") String path,
            @ToolParam(description = "需要被替换的原始文本（精确匹配）") String oldText,
            @ToolParam(description = "新的文本内容") String newText
    ) {
        try {
            Path fp = safePath(path);
            if (!Files.exists(fp)) {
                return "Error: File not found: " + path;
            }
            String content = Files.readString(fp, StandardCharsets.UTF_8);
            int idx = content.indexOf(oldText);
            if (idx < 0) {
                return "Error: Text not found in " + path;
            }
            Pattern pattern = Pattern.compile(Pattern.quote(oldText));
            Matcher matcher = pattern.matcher(content);
            String updated = matcher.replaceFirst(Matcher.quoteReplacement(newText));
            Files.writeString(fp, updated, StandardCharsets.UTF_8);
            return "Edited " + path;
        } catch (Exception e) {
            log.warn("edit_file failed for {}: {}", path, e.toString());
            return "Error: " + e.getMessage();
        }
    }
}

