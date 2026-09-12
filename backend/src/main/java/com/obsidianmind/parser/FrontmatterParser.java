package com.obsidianmind.parser;

import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Frontmatter 解析器：仅解析 --- 包裹的 YAML 头部，不做任何改写（只读）。
 * 使用 SnakeYAML SafeConstructor 防止反序列化攻击。
 */
@Component
public class FrontmatterParser {

    private static final String DELIMITER = "---";

    public boolean hasFrontmatter(String raw) {
        return raw != null && raw.stripLeading().startsWith(DELIMITER);
    }

    /**
     * 提取 frontmatter 块。返回 [metadata, bodyWithoutFrontmatter]。
     * 无 frontmatter 或格式非法时返回空 Map 与原文。
     */
    @SuppressWarnings("unchecked")
    public Result parse(String raw) {
        if (!hasFrontmatter(raw)) {
            return new Result(Map.of(), raw == null ? "" : raw);
        }
        String[] lines = raw.split("\r?\n", -1);
        int start = 0;
        while (start < lines.length && lines[start].isBlank()) {
            start++;
        }
        if (start >= lines.length || !DELIMITER.equals(lines[start].trim())) {
            return new Result(Map.of(), raw);
        }
        StringBuilder yamlBuilder = new StringBuilder();
        int end = -1;
        for (int i = start + 1; i < lines.length; i++) {
            if (DELIMITER.equals(lines[i].trim())) {
                end = i;
                break;
            }
            yamlBuilder.append(lines[i]).append('\n');
        }
        if (end < 0) {
            return new Result(Map.of(), raw);
        }
        StringBuilder bodyBuilder = new StringBuilder();
        for (int i = end + 1; i < lines.length; i++) {
            bodyBuilder.append(lines[i]);
            if (i < lines.length - 1) {
                bodyBuilder.append('\n');
            }
        }
        Map<String, Object> metadata = parseYaml(yamlBuilder.toString());
        return new Result(metadata, bodyBuilder.toString());
    }

    private Map<String, Object> parseYaml(String yaml) {
        if (yaml.isBlank()) {
            return Map.of();
        }
        try {
            Yaml mapper = new Yaml(new SafeConstructor(new LoaderOptions()));
            Object parsed = mapper.load(yaml);
            if (parsed instanceof Map<?, ?> map) {
                Map<String, Object> result = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (entry.getKey() instanceof String key) {
                        result.put(key, entry.getValue());
                    }
                }
                return result;
            }
            return Map.of();
        } catch (RuntimeException e) {
            // frontmatter 格式非法时不阻断主流程，按无 frontmatter 处理
            return Map.of();
        }
    }

    /**
     * frontmatter 解析结果：metadata + 去除 frontmatter 后的正文。
     */
    public record Result(Map<String, Object> metadata, String body) {
    }
}
