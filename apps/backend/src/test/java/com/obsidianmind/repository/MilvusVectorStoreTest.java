package com.obsidianmind.repository;

import com.obsidianmind.exception.VectorStoreException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * MilvusVectorStore 契约测试（不依赖真实 Milvus）。
 * 实机集成测试见 MilvusVectorStoreIT（条件启用：MILVUS_IT=1 且 Milvus 已启动）。
 */
class MilvusVectorStoreTest {

    @Test
    void expressionQuotingEscapesQuotesAndBackslashes() {
        assertThat(MilvusVectorStore.quote("Java/HashMap.md")).isEqualTo("\"Java/HashMap.md\"");
        assertThat(MilvusVectorStore.quote("a\"b")).isEqualTo("\"a\\\"b\"");
        assertThat(MilvusVectorStore.quote("a\\b")).isEqualTo("\"a\\\\b\"");
        // 表达式注入载荷被整体转义，无法逃出字符串字面量
        assertThat(MilvusVectorStore.quote("x\" or id != \""))
                .isEqualTo("\"x\\\" or id != \\\"\"");
    }

    @Test
    void ensureCollectionRejectsNonPositiveDimension() {
        MilvusVectorStore store = new MilvusVectorStore(
                new com.obsidianmind.config.MilvusProperties("localhost", 19530, 1, "c", 1024));
        // 维度校验先于任何网络调用（不会触发连接）
        assertThatThrownBy(() -> store.ensureCollection("c", 0))
                .isInstanceOf(VectorStoreException.class)
                .hasMessageContaining("维度非法");
        assertThatThrownBy(() -> store.ensureCollection("c", -3))
                .isInstanceOf(VectorStoreException.class);
    }
}
