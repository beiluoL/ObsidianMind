package com.obsidianmind.repository;

import com.obsidianmind.config.MilvusProperties;
import com.obsidianmind.domain.Chunk;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MilvusVectorStore 实机集成测试：仅在 MILVUS_IT=1 且 Milvus 已启动时运行（默认跳过）。
 * 启动方式：docker compose -f infrastructure/milvus/docker-compose.yml up -d && MILVUS_IT=1 mvn test
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MilvusVectorStoreIT {

    private MilvusVectorStore store;
    private static final String COLLECTION = "obsidianmind_it_chunks";
    private static final String VAULT = "it-vault";

    @BeforeAll
    void setUp() {
        Assumptions.assumeTrue("1".equals(System.getenv("MILVUS_IT")), "MILVUS_IT=1 未设置，跳过实机集成测试");
        store = new MilvusVectorStore(new MilvusProperties("localhost", 19530, 5, COLLECTION, 4));
    }

    private Chunk chunk(String documentId, int index, String text) {
        return new Chunk(documentId + "#c" + index, VAULT, documentId, documentId, "标题", "A / B", index,
                0, text.length(), text, "hash-" + documentId);
    }

    @Test
    void ensureUpsertCountSearchDeleteRoundTrip() {
        store.ensureCollection(COLLECTION, 4);
        Chunk c1 = chunk("Java/A.md", 0, "向量 A1");
        Chunk c2 = chunk("Java/A.md", 1, "向量 A2");
        Chunk c3 = chunk("Java/B.md", 0, "向量 B1");
        store.upsert(COLLECTION, List.of(c1, c2, c3), List.of(
                new float[]{1, 0, 0, 0}, new float[]{0, 1, 0, 0}, new float[]{0, 0, 1, 0}));

        assertThat(store.count(COLLECTION)).isEqualTo(3);

        // 增量 hash 映射（按 vaultId 边界）
        Map<String, String> hashes = store.loadDocumentHashes(COLLECTION, VAULT);
        assertThat(hashes).containsOnlyKeys("Java/A.md", "Java/B.md");

        // 相似度检索 + Source 元数据（vaultId 过滤在查询表达式内）
        List<VectorRepository.SearchResultRecord> hits =
                store.search(COLLECTION, VAULT, new float[]{1, 0, 0, 0}, 2);
        assertThat(hits).hasSize(2);
        assertThat(hits.get(0).chunkId()).isEqualTo("Java/A.md#c0");
        assertThat(hits.get(0).relativePath()).isEqualTo("Java/A.md");
        assertThat(hits.get(0).headingPath()).isEqualTo("A / B");

        // 文档级 replace：重写 A.md 为单 Chunk 后总数正确
        Chunk c1v2 = chunk("Java/A.md", 0, "向量 A1 v2");
        store.upsert(COLLECTION, List.of(c1v2), List.of(new float[]{1, 0, 0, 0}));
        assertThat(store.count(COLLECTION)).isEqualTo(2);

        // 删除清理（vaultId 限定边界）
        store.deleteByDocuments(COLLECTION, VAULT, List.of("Java/A.md", "Java/B.md"));
        assertThat(store.count(COLLECTION)).isZero();
    }
}
