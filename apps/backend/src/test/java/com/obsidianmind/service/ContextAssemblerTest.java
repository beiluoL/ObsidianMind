package com.obsidianmind.service;

import com.obsidianmind.config.AiProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ContextAssembler 单元测试：正常映射、空结果、字符超限截断、Source ID 稳定性。
 */
class ContextAssemblerTest {

    private ContextAssembler assembler;

    @BeforeEach
    void setUp() {
        AiProperties ai = new AiProperties("ollama", null, null, null, null,
                new AiProperties.Rag(3, 500, 0.1, 1024, false, 120, 180));
        assembler = new ContextAssembler(ai);
    }

    private RetrievalService.RagSource ragSource(String title, String content) {
        return new RetrievalService.RagSource(title, "Java/" + title + ".md", "#" + title,
                content, 0.9, "doc-" + title, 0);
    }

    @Test
    void shouldMapSourcesWithStableSourceIds() {
        RetrievalService.RagRetrievalResult retrieval = new RetrievalService.RagRetrievalResult(
                "q", List.of(ragSource("HashMap", "内容A"), ragSource("G1", "内容B")), 10);

        ContextAssembler.RagContext context = assembler.assemble(retrieval);

        assertThat(context.items()).hasSize(2);
        assertThat(context.items().get(0).sourceId()).isEqualTo("SRC-1");
        assertThat(context.items().get(1).sourceId()).isEqualTo("SRC-2");
        assertThat(context.items().get(0).path()).isEqualTo("Java/HashMap.md");
        assertThat(context.items().get(0).documentId()).isEqualTo("doc-HashMap");
        assertThat(context.contextChars()).isEqualTo(6); // 内容A=3 字符 ×2
        assertThat(context.truncated()).isFalse();
    }

    @Test
    void shouldProduceEmptyContextForEmptyRetrieval() {
        RetrievalService.RagRetrievalResult retrieval =
                new RetrievalService.RagRetrievalResult("q", List.of(), 5);

        ContextAssembler.RagContext context = assembler.assemble(retrieval);

        assertThat(context.isEmpty()).isTrue();
        assertThat(context.items()).isEmpty();
    }

    @Test
    void shouldCapChunksByMaxChunks() {
        RetrievalService.RagRetrievalResult retrieval = new RetrievalService.RagRetrievalResult(
                "q", List.of(
                ragSource("A", "aa"),
                ragSource("B", "bb"),
                ragSource("C", "cc"),
                ragSource("D", "dd")), 10);

        ContextAssembler.RagContext context = assembler.assemble(retrieval);

        // maxChunks=3：只保留相似度排名前 3，第 4 条被裁且 truncated 置位
        assertThat(context.items()).hasSize(3);
        assertThat(context.items()).extracting(ContextAssembler.ContextItem::title)
                .containsExactly("A", "B", "C");
        assertThat(context.truncated()).isTrue();
    }

    @Test
    void shouldStopEntirelyWhenCharBudgetExceeded() {
        RetrievalService.RagRetrievalResult retrieval = new RetrievalService.RagRetrievalResult(
                "q", List.of(
                ragSource("A", "a".repeat(300)),
                ragSource("B", "b".repeat(300)),
                ragSource("C", "c".repeat(10))), 10);

        ContextAssembler.RagContext context = assembler.assemble(retrieval);

        // maxChars=500：第二条放不下 → 整体停止（保留高相关，不塞半条）
        assertThat(context.items()).hasSize(1);
        assertThat(context.items().get(0).title()).isEqualTo("A");
        assertThat(context.truncated()).isTrue();
    }

    @Test
    void shouldKeepMetadataIntactForCitationTraceability() {
        RetrievalService.RagRetrievalResult retrieval = new RetrievalService.RagRetrievalResult(
                "q", List.of(new RetrievalService.RagSource("HashMap", "Java/HashMap.md", "# 扩容机制",
                "内容", 0.83, "doc-1", 7)), 10);

        ContextAssembler.RagContext context = assembler.assemble(retrieval);

        ContextAssembler.ContextItem item = context.items().get(0);
        assertThat(item.heading()).isEqualTo("# 扩容机制");
        assertThat(item.chunkIndex()).isEqualTo(7);
        assertThat(item.score()).isEqualTo(0.83);
    }
}
