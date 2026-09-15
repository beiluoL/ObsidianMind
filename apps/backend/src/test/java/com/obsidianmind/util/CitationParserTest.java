package com.obsidianmind.util;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * CitationParser 单元测试：引用提取、编号归一化、未知编号不抛错（绝不 500）。
 */
class CitationParserTest {

    private final Set<String> known = Set.of("SRC-1", "SRC-2", "SRC-3");

    @Test
    void shouldExtractValidCitationsInOrder() {
        CitationParser.Result result = CitationParser.parse(
                "HashMap 扩容是为了… [SRC-1] 另见 [SRC-2]。", known);
        assertThat(result.cited()).containsExactly("SRC-1", "SRC-2");
        assertThat(result.unknown()).isEmpty();
    }

    @Test
    void shouldDeduplicateRepeatedCitations() {
        CitationParser.Result result = CitationParser.parse(
                "[SRC-1] 中间文字 [SRC-1] 结尾 [SRC-3]", known);
        assertThat(result.cited()).containsExactly("SRC-1", "SRC-3");
    }

    @Test
    void shouldTreatUnknownSourceIdAsUnknownWithoutThrowing() {
        CitationParser.Result result = CitationParser.parse(
                "模型幻觉引用 [SRC-9] 与 [SRC-42]", known);
        assertThat(result.cited()).isEmpty();
        assertThat(result.unknown()).containsExactly("SRC-9", "SRC-42");
    }

    @Test
    void shouldNormalizeLeadingZerosAndCase() {
        CitationParser.Result result = CitationParser.parse(
                "[src-01] 与 [SRC-2]", known);
        assertThat(result.cited()).containsExactly("SRC-1", "SRC-2");
    }

    @Test
    void shouldReturnEmptyForNoCitations() {
        assertThat(CitationParser.parse("没有任何引用的回答", known).cited()).isEmpty();
    }

    @Test
    void shouldHandleNullAndBlankAnswerSafely() {
        assertThatCode(() -> {
            assertThat(CitationParser.parse(null, known).cited()).isEmpty();
            assertThat(CitationParser.parse("", known).unknown()).isEmpty();
        }).doesNotThrowAnyException();
    }

    @Test
    void shouldIgnoreOtherBracketText() {
        CitationParser.Result result = CitationParser.parse("普通 Markdown [链接](url) 与 [注1]", known);
        assertThat(result.cited()).isEmpty();
        assertThat(result.unknown()).isEmpty();
    }
}
