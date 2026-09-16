package com.obsidianmind.retrieval;

/**
 * 检索模式（Phase 6）。对外 API 用同名大写字符串；前端展示为 语义/关键词/混合，不暴露底层技术名。
 */
public enum RetrievalMode {
    VECTOR,
    KEYWORD,
    HYBRID;

    /** 解析用户传入的模式字符串（大小写不敏感）；null/空白用默认值；非法值抛 InvalidRequestException（400）。 */
    public static RetrievalMode parse(String raw, String defaultMode) {
        String value = raw == null || raw.isBlank() ? defaultMode : raw.strip();
        try {
            return RetrievalMode.valueOf(value.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new com.obsidianmind.exception.InvalidRequestException(
                    "非法检索模式: " + value + "（可选 VECTOR / KEYWORD / HYBRID）");
        }
    }
}
