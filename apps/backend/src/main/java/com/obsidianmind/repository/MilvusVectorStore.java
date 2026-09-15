package com.obsidianmind.repository;

import com.obsidianmind.config.MilvusProperties;
import com.obsidianmind.domain.Chunk;
import com.obsidianmind.exception.MilvusUnavailableException;
import com.obsidianmind.exception.VectorStoreException;
import io.milvus.grpc.DataType;
import io.milvus.param.ConnectParam;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import io.milvus.param.R;
import io.milvus.param.collection.CreateCollectionParam;
import io.milvus.param.collection.FieldType;
import io.milvus.param.collection.HasCollectionParam;
import io.milvus.param.collection.LoadCollectionParam;
import io.milvus.param.dml.DeleteParam;
import io.milvus.param.dml.InsertParam;
import io.milvus.param.dml.QueryParam;
import io.milvus.param.dml.SearchParam;
import io.milvus.param.index.CreateIndexParam;
import io.milvus.response.QueryResultsWrapper;
import io.milvus.response.SearchResultsWrapper;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Milvus 向量存储（VectorRepository 的生产实现）。
 *
 * 设计要点（详见 docs/architecture/milvus.md）：
 * - 单例 MilvusServiceClient（gRPC channel 惰性建连、复用，随应用生命周期关闭），依赖未启动不阻塞应用；
 * - Collection 固定名称（milvus.collection），由 ensureCollection 惰性创建 + HNSW/COSINE 索引 + load；
 * - upsert = delete-by-documentId + insert（文档级 replace，防止重切块后残留幽灵 Chunk）；
 * - Milvus 连接级失败抛 MilvusUnavailableException，操作级失败抛 VectorStoreException（均 503）。
 */
@Repository
public class MilvusVectorStore implements VectorRepository {

    private static final Logger log = LoggerFactory.getLogger(MilvusVectorStore.class);
    private static final String VECTOR_FIELD = "vector";
    private static final String HNSW_EXTRA_PARAM = "{\"M\":16,\"efConstruction\":200}";
    private static final List<String> SEARCH_OUT_FIELDS =
            List.of("documentId", "relativePath", "title", "headingPath", "chunkIndex", "content");

    private final MilvusProperties properties;
    private volatile io.milvus.client.MilvusServiceClient client;

    public MilvusVectorStore(MilvusProperties properties) {
        this.properties = properties;
        // client 惰性创建：SDK 构造函数会立即建连，Milvus 未启动不得阻塞应用启动
    }

    /** 惰性单例 client：首次实际使用时才建立 gRPC 连接（双检锁）。 */
    private io.milvus.client.MilvusServiceClient client() {
        io.milvus.client.MilvusServiceClient c = client;
        if (c == null) {
            synchronized (this) {
                if (client == null) {
                    try {
                        client = new io.milvus.client.MilvusServiceClient(ConnectParam.newBuilder()
                                .withHost(properties.host())
                                .withPort(properties.port())
                                .withConnectTimeout(properties.timeoutSeconds(), java.util.concurrent.TimeUnit.SECONDS)
                                .build());
                    } catch (RuntimeException e) {
                        // SDK 惰性连接在握手失败时抛原始 gRPC 异常：统一包装成语义化 503，
                        // 避免 RETRIEVAL_ERROR / INTERNAL_ERROR 掩盖真实原因
                        throw new MilvusUnavailableException(
                                "Milvus 连接失败: " + e.getMessage());
                    }
                }
                c = client;
            }
        }
        return c;
    }

    @PreDestroy
    void shutdown() {
        io.milvus.client.MilvusServiceClient c = client;
        if (c != null) {
            try {
                c.close();
            } catch (RuntimeException e) {
                log.warn("Milvus client 关闭异常: {}", e.getMessage());
            }
        }
    }

    @Override
    public void ensureCollection(String collection, int dimension) {
        if (dimension <= 0) {
            throw new VectorStoreException("向量维度非法: " + dimension);
        }
        Boolean exists = status(client().hasCollection(HasCollectionParam.newBuilder()
                .withCollectionName(collection).build()), "检查 Collection 是否存在").getData();
        if (Boolean.TRUE.equals(exists)) {
            log.info("Milvus Collection 已存在: {}", collection);
            return;
        }
        status(client().createCollection(CreateCollectionParam.newBuilder()
                        .withCollectionName(collection)
                        .withDescription("ObsidianMind knowledge chunks")
                        .addFieldType(FieldType.newBuilder()
                                .withName("id").withDataType(DataType.VarChar)
                                .withMaxLength(512).withPrimaryKey(true).withAutoID(false).build())
                        .addFieldType(FieldType.newBuilder()
                                .withName("vaultId").withDataType(DataType.VarChar)
                                .withMaxLength(64).build())
                        .addFieldType(FieldType.newBuilder()
                                .withName(VECTOR_FIELD).withDataType(DataType.FloatVector)
                                .withDimension(dimension).build())
                        .addFieldType(FieldType.newBuilder()
                                .withName("documentId").withDataType(DataType.VarChar)
                                .withMaxLength(512).build())
                        .addFieldType(FieldType.newBuilder()
                                .withName("relativePath").withDataType(DataType.VarChar)
                                .withMaxLength(512).build())
                        .addFieldType(FieldType.newBuilder()
                                .withName("title").withDataType(DataType.VarChar)
                                .withMaxLength(512).build())
                        .addFieldType(FieldType.newBuilder()
                                .withName("headingPath").withDataType(DataType.VarChar)
                                .withMaxLength(1024).build())
                        .addFieldType(FieldType.newBuilder()
                                .withName("chunkIndex").withDataType(DataType.Int64).build())
                        .addFieldType(FieldType.newBuilder()
                                .withName("content").withDataType(DataType.VarChar)
                                .withMaxLength(65535).build())
                        .addFieldType(FieldType.newBuilder()
                                .withName("contentHash").withDataType(DataType.VarChar)
                                .withMaxLength(64).build())
                        .build()),
                "创建 Collection " + collection);
        status(client().createIndex(CreateIndexParam.newBuilder()
                        .withCollectionName(collection)
                        .withFieldName(VECTOR_FIELD)
                        .withIndexType(IndexType.HNSW)
                        .withMetricType(MetricType.COSINE)
                        .withExtraParam(HNSW_EXTRA_PARAM)
                        .build()),
                "创建向量索引 HNSW/COSINE");
        status(client().loadCollection(LoadCollectionParam.newBuilder()
                        .withCollectionName(collection).build()),
                "加载 Collection " + collection);
        log.info("Milvus Collection 创建完成: {} (dim={}, HNSW/COSINE)", collection, dimension);
    }

    @Override
    public void upsert(String collection, List<Chunk> chunks, List<float[]> vectors) {
        if (chunks.size() != vectors.size()) {
            throw new VectorStoreException("chunks 与 vectors 数量不符: " + chunks.size() + "/" + vectors.size());
        }
        if (chunks.isEmpty()) {
            return;
        }
        deleteByDocuments(collection, chunks.get(0).vaultId(), List.of(chunks.get(0).documentId()));
        List<InsertParam.Field> fields = new ArrayList<>();
        fields.add(new InsertParam.Field("id", chunks.stream().map(Chunk::id).toList()));
        fields.add(new InsertParam.Field(VECTOR_FIELD, List.copyOf(vectors)));
        fields.add(new InsertParam.Field("vaultId", chunks.stream().map(Chunk::vaultId).toList()));
        fields.add(new InsertParam.Field("documentId", chunks.stream().map(Chunk::documentId).toList()));
        fields.add(new InsertParam.Field("relativePath", chunks.stream().map(Chunk::relativePath).toList()));
        fields.add(new InsertParam.Field("title", chunks.stream().map(Chunk::title).toList()));
        fields.add(new InsertParam.Field("headingPath", chunks.stream().map(Chunk::headingPath).toList()));
        fields.add(new InsertParam.Field("chunkIndex", chunks.stream().map(Chunk::chunkIndex).toList()));
        fields.add(new InsertParam.Field("content", chunks.stream().map(Chunk::content).toList()));
        fields.add(new InsertParam.Field("contentHash", chunks.stream().map(Chunk::contentHash).toList()));
        status(client().insert(InsertParam.newBuilder()
                        .withCollectionName(collection)
                        .withFields(fields)
                        .build()),
                "写入 " + chunks.size() + " 个 Chunk");
    }

    @Override
    public void deleteByDocuments(String collection, String vaultId, List<String> documentIds) {
        if (documentIds.isEmpty()) {
            return;
        }
        // vaultId 由服务端计算（SHA-256），非用户输入；仍走 quote 转义统一防表达式注入
        String expr = "documentId in [" + String.join(",", documentIds.stream()
                .map(MilvusVectorStore::quote).toList()) + "] and vaultId == " + quote(vaultId);
        status(client().delete(DeleteParam.newBuilder()
                        .withCollectionName(collection)
                        .withExpr(expr)
                        .build()),
                "删除文档向量 x" + documentIds.size());
    }

    @Override
    public Map<String, String> loadDocumentHashes(String collection, String vaultId) {
        Boolean exists = status(client().hasCollection(HasCollectionParam.newBuilder()
                .withCollectionName(collection).build()), "检查 Collection 是否存在").getData();
        if (!Boolean.TRUE.equals(exists)) {
            return Map.of(); // 首次运行：Collection 尚未创建，视为空索引
        }
        R<io.milvus.grpc.QueryResults> response = client().query(QueryParam.newBuilder()
                .withCollectionName(collection)
                .withExpr("vaultId == " + quote(vaultId))
                .withOutFields(List.of("documentId", "contentHash"))
                .build());
        status(response, "读取存量索引哈希");
        Map<String, String> hashes = new LinkedHashMap<>();
        for (QueryResultsWrapper.RowRecord record : new QueryResultsWrapper(response.getData()).getRowRecords()) {
            Object documentId = record.get("documentId");
            Object contentHash = record.get("contentHash");
            if (documentId != null && contentHash != null) {
                hashes.putIfAbsent(documentId.toString(), contentHash.toString());
            }
        }
        return hashes;
    }

    @Override
    public List<SearchResultRecord> search(String collection, String vaultId, float[] queryVector, int topK) {
        R<io.milvus.grpc.SearchResults> response = client().search(SearchParam.newBuilder()
                .withCollectionName(collection)
                // vaultId 边界过滤在 Milvus 查询内完成：先全库搜再内存过滤会浪费召回且可能漏结果
                .withExpr("vaultId == " + quote(vaultId))
                .withMetricType(MetricType.COSINE)
                .withVectorFieldName(VECTOR_FIELD)
                .withVectors(List.of(queryVector))
                .withTopK(topK)
                .withOutFields(SEARCH_OUT_FIELDS)
                .build());
        status(response, "向量检索");
        SearchResultsWrapper wrapper = new SearchResultsWrapper(response.getData().getResults());
        List<QueryResultsWrapper.RowRecord> records = wrapper.getRowRecords(0); // 单查询向量 → 第 0 组
        List<SearchResultsWrapper.IDScore> scores = wrapper.getIDScore(0);
        List<SearchResultRecord> results = new ArrayList<>(records.size());
        for (int i = 0; i < records.size(); i++) {
            QueryResultsWrapper.RowRecord record = records.get(i);
            results.add(new SearchResultRecord(
                    str(record, "id"),
                    str(record, "documentId"),
                    str(record, "relativePath"),
                    str(record, "title"),
                    str(record, "headingPath"),
                    (int) num(record, "chunkIndex"),
                    str(record, "content"),
                    i < scores.size() ? scores.get(i).getScore() : 0d));
        }
        return results;
    }

    @Override
    public long count(String collection) {
        R<io.milvus.grpc.GetCollectionStatisticsResponse> response = client().getCollectionStatistics(
                io.milvus.param.collection.GetCollectionStatisticsParam.newBuilder()
                        .withCollectionName(collection).build());
        status(response, "读取 Collection 统计");
        for (io.milvus.grpc.KeyValuePair stat : response.getData().getStatsList()) {
            if ("num_entities".equals(stat.getKey())) {
                try {
                    return Long.parseLong(stat.getValue());
                } catch (NumberFormatException e) {
                    return 0;
                }
            }
        }
        return 0;
    }

    private static String str(QueryResultsWrapper.RowRecord record, String field) {
        Object value = record.get(field);
        return value == null ? "" : value.toString();
    }

    private static long num(QueryResultsWrapper.RowRecord record, String field) {
        Object value = record.get(field);
        return value instanceof Number n ? n.longValue() : 0L;
    }

    /** 统一响应检查：非成功状态按语义抛 503 业务异常。 */
    private <T> R<T> status(R<T> response, String action) {
        if (response.getStatus() != R.Status.Success.getCode()) {
            String message = action + " 失败: " + response.getMessage();
            log.warn("Milvus {}: {}", action, response.getMessage());
            if (response.getStatus() == R.Status.RpcError.getCode()) {
                throw new MilvusUnavailableException(message);
            }
            throw new VectorStoreException(message);
        }
        return response;
    }

    /** Milvus 表达式字符串转义（防表达式注入）。 */
    static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
