package com.obsidianmind.util;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Citation 解析器（Phase 5）：从 LLM 回答文本中提取 [SRC-n] 引用编号。
 *
 * 职责边界：只做文本解析与已知编号校验，不做来源查找、不做路径拼接——
 * Source Registry 由系统掌控，模型输出的任何编号、路径都不会被信任为事实。
 * 未知编号（模型幻觉产物）只记录不报错：一条引用解析失败绝不能让回答 500。
 */
public final class CitationParser {

    /** [SRC-1] / [src-2] 均可（模型对大小写不稳定）；编号上限 4 位，防正则回溯异常输入。 */
    private static final Pattern CITATION = Pattern.compile("\\[SRC-(\\d{1,4})]", Pattern.CASE_INSENSITIVE);

    private CitationParser() {
    }

    /**
     * 解析回答中的引用编号。
     *
     * @param answer    LLM 回答全文（允许为空/无引用）
     * @param knownIds  系统 Source Registry 中的合法编号集合（如 SRC-1、SRC-2）
     * @return cited=按出现顺序去重后的合法编号；unknown=模型编造的不存在编号（仅记录，不抛异常）
     */
    public static Result parse(String answer, Set<String> knownIds) {
        Set<String> cited = new LinkedHashSet<>();
        Set<String> unknown = new LinkedHashSet<>();
        if (answer != null) {
            Matcher matcher = CITATION.matcher(answer);
            while (matcher.find()) {
                String id = "SRC-" + matcher.group(1);
                // 前导零归一化：[SRC-01] → SRC-1
                String normalized = knownIds.contains(id) ? id : "SRC-" + Integer.parseInt(matcher.group(1));
                if (knownIds.contains(normalized)) {
                    cited.add(normalized);
                } else {
                    unknown.add(normalized);
                }
            }
        }
        return new Result(List.copyOf(cited), List.copyOf(unknown));
    }

    /** 解析结果：cited 与 unknown 均不可变；无引用时两个列表为空（正常情况，不是错误）。 */
    public record Result(List<String> cited, List<String> unknown) {

        public static Result empty() {
            return new Result(List.of(), List.of());
        }
    }
}
