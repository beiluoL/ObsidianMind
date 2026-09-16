package com.obsidianmind.retrieval;

import com.obsidianmind.config.AiProperties;
import com.obsidianmind.config.MilvusProperties;
import com.obsidianmind.config.RetrievalProperties;
import com.obsidianmind.repository.VectorRepository;
import com.obsidianmind.repository.VaultRepository;
import com.obsidianmind.service.Chunker;
import com.obsidianmind.service.EmbeddingService;
import com.obsidianmind.parser.MarkdownParser;
import com.obsidianmind.service.RetrievalService;

/**
 * 测试装配工具：按生产相同的方式组装 Hybrid 检索栈（Provider → RRF → Reranker → Service）。
 * 只在测试源集使用；保证测试与 Spring 生产装配的一致性。
 */
public final class RetrievalStackForTest {

    private RetrievalStackForTest() {
    }

    /** 默认检索配置（可指定默认模式；Reranker 默认关闭 = NOT_CONFIGURED）。 */
    public static RetrievalProperties properties(String defaultMode) {
        return new RetrievalProperties(
                defaultMode,
                new RetrievalProperties.Rrf(60),
                new RetrievalProperties.Candidates(20, 20),
                new RetrievalProperties.Keyword(1.5, 0.75),
                new RetrievalProperties.Reranker(false, "lexical", "", 20),
                false);
    }

    public static HybridRetriever hybridRetriever(VaultRepository vaultRepository,
                                                  EmbeddingService embeddingService,
                                                  VectorRepository vectorRepository,
                                                  MilvusProperties milvusProperties,
                                                  AiProperties aiProperties,
                                                  MarkdownParser markdownParser,
                                                  Chunker chunker,
                                                  RetrievalProperties properties) {
        VectorRetrievalProvider vectorProvider = new VectorRetrievalProvider(
                vaultRepository, embeddingService, vectorRepository, milvusProperties, aiProperties);
        KeywordRetrievalProvider keywordProvider = new KeywordRetrievalProvider(
                vaultRepository, new VaultKeywordIndex(vaultRepository, markdownParser, chunker, properties));
        LexicalReranker reranker = new LexicalReranker(properties);
        return new HybridRetriever(vectorProvider, keywordProvider, reranker, aiProperties, properties);
    }

    public static RetrievalService retrievalService(HybridRetriever hybridRetriever,
                                                    AiProperties aiProperties,
                                                    RetrievalProperties properties) {
        return new RetrievalService(hybridRetriever, aiProperties, properties);
    }
}
