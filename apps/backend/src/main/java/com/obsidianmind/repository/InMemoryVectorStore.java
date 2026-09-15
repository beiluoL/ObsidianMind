package com.obsidianmind.repository;

import com.obsidianmind.domain.Chunk;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 内存向量存储：仅用于测试与本地无 Milvus 环境的 Demo 验证（非 Spring Bean，不参与生产装配）。
 * 实现与 MilvusVectorStore 相同的契约，行为语义（文档级 replace / 增量 hash 映射 / 相似度排序）保持一致。
 * 相似度 = 1 - cosine distance（向量未归一化时先归一化再点积）。
 */
public class InMemoryVectorStore implements VectorRepository {

    private final Map<String, Collection> collections = new ConcurrentHashMap<>();
    private final Map<String, Integer> dimensions = new ConcurrentHashMap<>();

    @Override
    public void ensureCollection(String collection, int dimension) {
        if (dimension <= 0) {
            throw new IllegalArgumentException("dimension 必须为正数");
        }
        Integer existing = dimensions.get(collection);
        if (existing != null && existing != dimension) {
            throw new IllegalArgumentException("维度冲突: collection=" + collection
                    + " 已有维度 " + existing + "，请求 " + dimension);
        }
        dimensions.put(collection, dimension);
        collections.computeIfAbsent(collection, k -> new Collection());
    }

    @Override
    public void upsert(String collection, List<Chunk> chunks, List<float[]> vectors) {
        if (chunks.size() != vectors.size()) {
            throw new IllegalArgumentException("chunks 与 vectors 数量不符");
        }
        Collection store = collections.get(collection);
        if (store == null) {
            throw new IllegalStateException("collection 不存在: " + collection);
        }
        if (!chunks.isEmpty()) {
            store.deleteByDocuments(chunks.get(0).vaultId(), List.of(chunks.get(0).documentId()));
        }
        for (int i = 0; i < chunks.size(); i++) {
            Chunk chunk = chunks.get(i);
            store.rows.put(chunk.id(), new Row(chunk, vectors.get(i).clone()));
        }
    }

    @Override
    public void deleteByDocuments(String collection, String vaultId, List<String> documentIds) {
        Collection store = collections.get(collection);
        if (store != null) {
            store.deleteByDocuments(vaultId, documentIds);
        }
    }

    @Override
    public Map<String, String> loadDocumentHashes(String collection, String vaultId) {
        Collection store = collections.get(collection);
        Map<String, String> hashes = new LinkedHashMap<>();
        if (store != null) {
            for (Row row : store.rows.values()) {
                if (row.chunk.vaultId().equals(vaultId)) {
                    hashes.putIfAbsent(row.chunk.documentId(), row.chunk.contentHash());
                }
            }
        }
        return hashes;
    }

    @Override
    public List<SearchResultRecord> search(String collection, String vaultId, float[] queryVector, int topK) {
        Collection store = collections.get(collection);
        List<SearchResultRecord> results = new ArrayList<>();
        if (store == null) {
            return results;
        }
        for (Row row : store.rows.values()) {
            if (!row.chunk.vaultId().equals(vaultId)) {
                continue; // 检索边界：其他 Vault 的向量不可见
            }
            results.add(new SearchResultRecord(
                    row.chunk.id(), row.chunk.documentId(), row.chunk.relativePath(),
                    row.chunk.title(), row.chunk.headingPath(), row.chunk.chunkIndex(),
                    row.chunk.content(), cosine(queryVector, row.vector)));
        }
        results.sort(Comparator.comparingDouble(SearchResultRecord::score).reversed());
        return results.subList(0, Math.min(topK, results.size()));
    }

    @Override
    public long count(String collection) {
        Collection store = collections.get(collection);
        return store == null ? 0 : store.rows.size();
    }

    private static double cosine(float[] a, float[] b) {
        double dot = 0;
        double normA = 0;
        double normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += (double) a[i] * a[i];
            normB += (double) b[i] * b[i];
        }
        if (normA == 0 || normB == 0) {
            return 0;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private static final class Collection {
        private final Map<String, Row> rows = new LinkedHashMap<>();
        private final AtomicLong counter = new AtomicLong();

        void deleteByDocuments(String vaultId, List<String> documentIds) {
            documentIds.forEach(id -> rows.values().removeIf(row ->
                    row.chunk.documentId().equals(id) && row.chunk.vaultId().equals(vaultId)));
            counter.addAndGet(documentIds.size());
        }
    }

    private record Row(Chunk chunk, float[] vector) {
    }
}
