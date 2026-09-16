package com.obsidianmind.retrieval;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TextTokenizer 行为测试：CJK bigram、拉丁整词、混合边界、特殊字符丢弃、大小写归一。
 */
class TextTokenizerTest {

    @Test
    void latinTokensStayWhole() {
        // 精确类名/方法名是关键词路存在的意义：不得拆碎
        assertThat(TextTokenizer.tokenize("HashMap putIfAbsent JDK21"))
                .containsExactly("hashmap", "putifabsent", "jdk21");
    }

    @Test
    void cjkSequenceBecomesOverlappingBigrams() {
        List<String> tokens = TextTokenizer.tokenize("扩容");
        assertThat(tokens).containsExactly("扩容");
        List<String> longer = TextTokenizer.tokenize("为什么需要扩容");
        assertThat(longer).containsSubsequence("为什", "什么", "么需", "需要", "要扩", "扩容");
    }

    @Test
    void singleCjkCharStaysUnigram() {
        assertThat(TextTokenizer.tokenize("的")).containsExactly("的");
    }

    @Test
    void mixedCjkAndLatinSplitAtBoundary() {
        List<String> tokens = TextTokenizer.tokenize("HashMap扩容机制");
        assertThat(tokens).contains("hashmap", "扩容", "容机", "机制");
    }

    @Test
    void punctuationAndQuerySyntaxAreDropped() {
        // 防注入根基：无查询语法，符号一律丢弃
        assertThat(TextTokenizer.tokenize("AND OR NOT ( ) + - && || ! { } [ ] ^ \" ~ * ? : \\ / HashMap"))
                .containsExactly("and", "or", "not", "hashmap");
    }

    @Test
    void caseIsNormalizedToLower() {
        assertThat(TextTokenizer.tokenize("NullPointerException")).containsExactly("nullpointerexception");
    }

    @Test
    void nullAndBlankProduceEmptyList() {
        assertThat(TextTokenizer.tokenize(null)).isEmpty();
        assertThat(TextTokenizer.tokenize("   ")).isEmpty();
    }

    @Test
    void digitsAndUnderscoreStayInToken() {
        assertThat(TextTokenizer.tokenize("HTTP_500 java17")).containsExactly("http_500", "java17");
    }
}
