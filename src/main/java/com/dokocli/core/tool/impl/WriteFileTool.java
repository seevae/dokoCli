package com.dokocli.core.tool.impl;

import com.dokocli.core.tool.AgentTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.dokocli.core.tool.FileToolUtils.safePath;


/**
 * write_file：写入文件内容。
 */
@Component
public class WriteFileTool implements AgentTool {

    private static final Logger log = LoggerFactory.getLogger(WriteFileTool.class);

    @Tool(
            name = "write_file",
            description = """
                    将文本内容写入工作目录中的文件。
                    如有必要会自动创建父目录，覆盖原有内容。
                    """
    )
    public String writeFile(
            @ToolParam(description = "要写入的文件相对路径") String path,
            @ToolParam(description = "要写入的完整文本内容") String content
    ) {
        try {
            Path fp = safePath(path);
            if (fp.getParent() != null) {
                Files.createDirectories(fp.getParent());
            }
            byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
            Files.write(fp, bytes);
            return "Wrote " + bytes.length + " bytes to " + path;
        } catch (IOException e) {
            log.warn("write_file failed for {}: {}", path, e.toString());
            return "Error: " + e.getMessage();
        }
    }
}

