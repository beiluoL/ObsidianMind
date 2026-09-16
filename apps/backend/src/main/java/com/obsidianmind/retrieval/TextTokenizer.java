package com.obsidianmind.retrieval;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 检索分词器（Phase 6）：关键词检索与 Reranker 共用的唯一分词入口。
 *
 * 规则：
 * - 连续 CJK 字符 → 重叠二元组（bigram）。「HashMap 为什么需要扩容」的中文段切成
 *   为什么 /什么需/…。二元组是中文检索的标准无词典方案（Elasticsearch CJK analyzer 同策略），
 *   单字查询退化为 unigram，保证「G1 的作用」里「的」这类单字也能成词；
 * - 连续拉丁字母 / 数字 / 下划线 → 整词（HashMap、JDK21、NullPointerException），
 *   保留精确类名 / 方法名 / 错误码的完整匹配能力——这正是关键词路存在的意义；
 * - 其余字符（标点、运算符、Markdown 符号）一律丢弃。
 *
 * 安全性：查询与文档都经过"切碎成 token"处理，不存在查询语法（无 AND/OR/NOT/括号/
 * 字段语法），从根上排除 Lucene/FTS 注入面——用户输入永远只是普通 token 序列。
 */
public final class TextTokenizer {

    private TextTokenizer() {
    }

    /** 是否 CJK 统一表意文字（含扩展 A）或日文假名——按 bigram 切分。 */
    private static boolean isCjk(char c) {
        return (c >= 0x4E00 && c <= 0x9FFF)
                || (c >= 0x3400 && c <= 0x4DBF)
                || (c >= 0x3040 && c <= 0x30FF);
    }

    private static boolean isWordChar(char c) {
        return isCjk(c) || Character.isLetterOrDigit(c) || c == '_';
    }

    /**
     * 分词：小写化 → 按非词字符切段 → CJK 段切 bigram（单字成 unigram）、拉丁数字段整词。
     *
     * @param text 原始文本（查询或文档），null 视为空串
     * @return token 列表（保持出现顺序，不去重——BM25 需要词频）
     */
    public static List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return tokens;
        }
        String input = text.toLowerCase(Locale.ROOT);
        StringBuilder run = new StringBuilder();
        boolean runIsCjk = false;
        int length = input.length();
        for (int i = 0; i <= length; i++) {
            char c = i < length ? input.charAt(i) : 0;
            boolean cjk = i < length && isCjk(c);
            boolean word = i < length && isWordChar(c);
            if (word && !run.isEmpty() && cjk != runIsCjk) {
                // CJK 与拉丁的边界：当前段结束
                emit(tokens, run.toString(), runIsCjk);
                run.setLength(0);
            }
            if (word) {
                runIsCjk = cjk;
                run.append(c);
            } else if (!run.isEmpty()) {
                emit(tokens, run.toString(), runIsCjk);
                run.setLength(0);
            }
        }
        return tokens;
    }

    /** CJK 串切重叠 bigram（长度 1 时保留 unigram）；拉丁串整词保留。 */
    private static void emit(List<String> tokens, String run, boolean isCjkRun) {
        if (!isCjkRun) {
            tokens.add(run);
            return;
        }
        if (run.length() == 1) {
            tokens.add(run);
            return;
        }
        for (int i = 0; i < run.length() - 1; i++) {
            tokens.add(run.substring(i, i + 2));
        }
    }
}
