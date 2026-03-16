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
import java.util.List;

import static com.dokocli.core.tool.FileToolUtils.MAX_OUTPUT_LENGTH;
import static com.dokocli.core.tool.FileToolUtils.safePath;

/**
 * read_file：读取文件内容。
 */
@Component
public class ReadFileTool implements AgentTool {

    private static final Logger log = LoggerFactory.getLogger(ReadFileTool.class);

    @Tool(
            name = "read_file",
            description = """
                    读取工作目录中的文件内容。
                    支持行数限制，超长内容会被截断。
                    """
    )
    public String readFile(
            @ToolParam(description = "要读取的文件相对路径，例如 src/Main.java") String path,
            @ToolParam(description = "最多读取的行数，可选", required = false) Integer limitLines
    ) {
        try {
            Path fp = safePath(path);
            if (!Files.exists(fp)) {
                return "Error: File not found: " + path;
            }
            List<String> lines = Files.readAllLines(fp, StandardCharsets.UTF_8);
            if (limitLines != null && limitLines > 0 && limitLines < lines.size()) {
                int more = lines.size() - limitLines;
                lines = lines.subList(0, limitLines);
                lines.add("... (" + more + " more lines)");
            }
            String content = String.join("\n", lines);
            if (content.length() > MAX_OUTPUT_LENGTH) {
                return content.substring(0, MAX_OUTPUT_LENGTH) + "\n... (content truncated)";
            }
            return content;
        } catch (Exception e) {
            log.warn("read_file failed for {}: {}", path, e.toString());
            return "Error: " + e.getMessage();
        }
    }
}

