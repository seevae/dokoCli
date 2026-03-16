package com.dokocli.core.tool.impl;

import com.dokocli.core.tool.AgentTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * Bash 命令执行工具 - CLI Agent 的唯一工具（Spring AI @Tool）
 */
@Component
public class BashTools implements AgentTool {

    private static final Logger log = LoggerFactory.getLogger(BashTools.class);

    @Tool(
            name = "execute_bash",
            description = """
                    执行任意 Bash 命令。这是唯一的工具，所有操作都通过它完成：

                    文件操作：cat, echo 'content' > file, mkdir, rm, mv, cp, touch
                    目录浏览：ls -la, pwd, cd
                    文件搜索：grep -r 'keyword' /path, find /path -name '*.java'
                    Git 操作：git status, git diff, git log, git add, git commit
                    代码运行：python, node, mvn, gradle, java

                    注意：避免执行需要交互式输入的命令（如 vim）。
                    """
    )
    public String executeBash(
            @ToolParam(description = "要执行的 Bash 命令") String command,
            @ToolParam(description = "工作目录（绝对路径，可选）", required = false) String workingDir,
            @ToolParam(description = "超时时间（秒，默认60）", required = false) Integer timeoutSeconds
    ) throws Exception {
        int timeout = timeoutSeconds != null ? timeoutSeconds : 60;

        ProcessBuilder pb = new ProcessBuilder("/bin/bash", "-c", command);
        if (workingDir != null) {
            pb.directory(new java.io.File(workingDir));
        }
        pb.redirectErrorStream(true);

        Process process = pb.start();

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }

        boolean finished = process.waitFor(timeout, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            return "错误: 命令执行超时（" + timeout + "秒）\n部分输出:\n" + output;
        }

        int exitCode = process.exitValue();
        String result = output.toString();

        if (exitCode != 0) {
            return "命令退出码: " + exitCode + "\n输出:\n" + result;
        }

        return result.isEmpty() ? "命令执行成功（无输出）" : result;
    }
}
