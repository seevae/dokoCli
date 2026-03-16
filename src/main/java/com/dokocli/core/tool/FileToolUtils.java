package com.dokocli.core.tool;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 文件类工具的公共辅助方法（路径安全等），不作为单独的工具暴露给模型。
 */
public class FileToolUtils {

    public static final Path WORKDIR = Paths.get("").toAbsolutePath().normalize();
    public static final int MAX_OUTPUT_LENGTH = 50_000;

    public static Path safePath(String p) {
        Path path = WORKDIR.resolve(p).normalize();
        if (!path.startsWith(WORKDIR)) {
            throw new IllegalArgumentException("Path escapes workspace: " + p);
        }
        return path;
    }
}

