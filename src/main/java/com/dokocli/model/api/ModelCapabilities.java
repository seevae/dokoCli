package com.dokocli.model.api;

/**
 * 模型能力声明
 */
public record ModelCapabilities(
        int maxContextTokens,
        boolean supportsToolCalling,
        boolean supportsVision,
        boolean supportsJsonMode
) {
    public static ModelCapabilities defaultCapabilities() {
        return new ModelCapabilities(4096, true, false, true);
    }
}
