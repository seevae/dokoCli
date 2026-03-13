package com.dokocli.model.api;

/**
 * 模型调用参数
 */
public record ModelParameters(
        String model,
        Double temperature,
        Integer maxTokens
) {
    public ModelParameters() {
        this(null, 0.3, null);
    }

    public ModelParameters withModel(String model) {
        return new ModelParameters(model, temperature, maxTokens);
    }
}
