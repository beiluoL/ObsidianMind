package com.obsidianmind.service;

import com.obsidianmind.config.AiProperties;
import com.obsidianmind.domain.Chunk;
import com.obsidianmind.domain.Document;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Chunker 行为测试：边界（heading/代码块/表格）、聚合、overlap、offset 不变量。
 */
class ChunkerTest {

    private final Chunker chunker = new Chunker(new AiProperties("ollama",
            new AiProperties.Ollama("http://x", "m", "e", 3),
            new AiProperties.Chunk(200, 40),
            new AiProperties.Embedding(16, 30),
            new AiProperties.Retrieval(5, 20, 0, 2, 200, 512), null));

    private Document doc(String body) {
        return new Document("Java/HashMap.md", "vault-1", "Java/HashMap.md", "HashMap", body,
                java.util.Map.of(), List.of(), List.of(), List.of(), Instant.now(), "hash123");
    }

    private String content(Chunk c, String body) {
        return body.substring(c.startOffset(), c.endOffset());
    }

    @Test
    void emptyBodyProducesNoChunks() {
        assertThat(chunker.chunk(doc(""))).isEmpty();
        assertThat(chunker.chunk(doc("   \n  \n"))).isEmpty();
    }

    @Test
    void illegalConfigurationFailsFast() {
        AiProperties bad = new AiProperties("ollama",
                new AiProperties.Ollama("http://x", "m", "e", 3),
                new AiProperties.Chunk(100, 100), // overlap >= size
                new AiProperties.Embedding(16, 30),
                new AiProperties.Retrieval(5, 20, 0, 2, 200, 512), null);
        assertThatThrownBy(() -> new Chunker(bad))
                .isInstanceOf(com.obsidianmind.exception.ConfigurationException.class);
    }

    @Test
    void respectsHeadingBoundaryAndBuildsHeadingPath() {
        String body = "# JVM\n\n## Stack\n\n栈是线程私有的内存区域。\n\n## Heap\n\n堆是对象实例分配的区域。";
        List<Chunk> chunks = chunker.chunk(doc(body));
        assertThat(chunks).hasSize(3);
        assertThat(chunks.get(0).headingPath()).isEqualTo("JVM");
        assertThat(chunks.get(1).headingPath()).isEqualTo("JVM / Stack");
        assertThat(chunks.get(2).headingPath()).isEqualTo("JVM / Heap");
        assertThat(chunks.get(0).content()).contains("# JVM");
        assertThat(chunks.get(1).content()).contains("## Stack");
        assertThat(chunks.get(2).content()).doesNotContain("Stack");
    }

    @Test
    void codeBlockIsNeverSplit() {
        String code = "```java\n" + "line1\nline2\nline3\nline4\nline5\nline6\nline7\nline8\n```";
        String filler = "\n\n段落文本。\n\n".repeat(2);
        String body = "# T\n\n" + code + filler + "更多文本。".repeat(30);
        List<Chunk> chunks = chunker.chunk(doc(body));
        assertThat(chunks).isNotEmpty();
        // 代码块必须完整出现在某一个 Chunk 中，绝不能横跨多个 Chunk
        boolean whole = chunks.stream().anyMatch(c -> c.content().contains(code));
        assertThat(whole).as("代码块应完整存在于单个 Chunk").isTrue();
        chunks.forEach(c -> assertThat(content(c, body)).isEqualTo(c.content()));
    }

    @Test
    void tableIsNeverSplit() {
        String table = "| a | b |\n| --- | --- |\n| 1 | 2 |\n| 3 | 4 |\n| 5 | 6 |\n| 7 | 8 |";
        String body = "# T\n\n" + table + "\n\n" + "正文内容。".repeat(40);
        List<Chunk> chunks = chunker.chunk(doc(body));
        assertThat(chunks).isNotEmpty();
        assertThat(chunks.stream().anyMatch(c -> c.content().contains(table))).isTrue();
        chunks.forEach(c -> assertThat(content(c, body)).isEqualTo(c.content()));
    }

    @Test
    void longParagraphIsSplitByLinesAndChunksStayWithinLimit() {
        String body = "# T\n\n" + ("这一行有三十个字左右的内容用来撑长度测试切块器行为。\n".repeat(40));
        List<Chunk> chunks = chunker.chunk(doc(body));
        assertThat(chunks.size()).isGreaterThanOrEqualTo(2);
        // 行切分 Chunk 允许接近上限（行不切断），且不产生空 Chunk
        chunks.forEach(c -> {
            assertThat(c.content().length()).isLessThanOrEqualTo(200 + 80);
            assertThat(c.content()).isNotBlank();
        });
    }

    @Test
    void overlapCarriesTailLinesBetweenConsecutiveChunks() {
        String body = "# T\n\n" + ("每个段落都有独立的句子内容便于检查重叠行为。\n\n".repeat(12));
        List<Chunk> chunks = chunker.chunk(doc(body));
        assertThat(chunks.size()).isGreaterThanOrEqualTo(2);
        for (int i = 1; i < chunks.size(); i++) {
            String prev = chunks.get(i - 1).content();
            String cur = chunks.get(i).content();
            boolean overlap = prev.lines().anyMatch(line -> !line.isBlank() && cur.contains(line));
            assertThat(overlap).as("相邻 Chunk 应共享至少一行（overlap=%d）".formatted(40)).isTrue();
        }
    }

    @Test
    void offsetsAreExactAndChunkIdsAreDeterministic() {
        String body = "# A\n\n内容一。\n\n## B\n\n内容二。更长一点的正文用来产生一定长度。";
        List<Chunk> chunks = chunker.chunk(doc(body));
        assertThat(chunks).hasSize(2);
        for (int i = 0; i < chunks.size(); i++) {
            Chunk c = chunks.get(i);
            assertThat(c.id()).isEqualTo("Java/HashMap.md#c" + i);
            assertThat(c.chunkIndex()).isEqualTo(i);
            assertThat(content(c, body)).isEqualTo(c.content());
            assertThat(c.documentId()).isEqualTo("Java/HashMap.md");
            assertThat(c.contentHash()).isEqualTo("hash123");
        }
    }

    @Test
    void oversizedCodeBlockBecomesSingleChunk() {
        String code = "```java\n" + "x = 1;\n".repeat(80) + "```";
        String body = code; // 无标题：整篇就是超限代码块
        List<Chunk> chunks = chunker.chunk(doc(body));
        assertThat(chunks).hasSize(1); // 超限代码块整块独立，不切开也不丢内容
        assertThat(chunks.get(0).content()).isEqualTo(body);
    }
}
