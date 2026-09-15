package com.obsidianmind.service;

import com.obsidianmind.config.AiProperties;
import com.obsidianmind.config.MilvusProperties;
import com.obsidianmind.domain.Chunk;
import com.obsidianmind.domain.Document;
import com.obsidianmind.exception.NoteReadFailedException;
import com.obsidianmind.parser.MarkdownParser;
import com.obsidianmind.parser.ParsedMarkdown;
import com.obsidianmind.repository.FileMeta;
import com.obsidianmind.repository.VectorRepository;
import com.obsidianmind.repository.VaultRepository;
import com.obsidianmind.util.Hashes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 知识索引编排服务（Phase 3 核心管线）：
 * Vault → Scan → Parse → Chunk → Embedding（全局批量）→ VectorStore（增量 upsert/delete）。
 *
 * 本类只做编排与增量状态机（INDEXED/UPDATED/SKIPPED/DELETED/FAILED），
 * 解析、切块、向量化、存储细节全部委托给对应组件（见 docs/architecture/knowledge-pipeline.md §八）。
 *
 * 错误归账原则：每个受影响文件在 errors 中有且仅有一条记录（含稳定错误码），
 * 批处理失败按 Chunk 区间精确归属到文档，绝不混入"(batch)"这类无主错误。
 * MVP 同步执行（个人 Vault 规模毫秒~秒级）；耗时成为问题时再升级异步 Job，不提前引入 MQ。
 */
@Service
public class KnowledgeIndexService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeIndexService.class);

    /** 单文件失败错误码（详见 docs/architecture/knowledge-pipeline.md §六）。 */
    public static final String CODE_PARSE = "DOCUMENT_PARSE_ERROR";
    public static final String CODE_CHUNK = "CHUNK_ERROR";
    public static final String CODE_EMBEDDING = "EMBEDDING_ERROR";
    public static final String CODE_VECTOR_STORE = "VECTOR_STORE_ERROR";

    private final VaultRepository vaultRepository;
    private final MarkdownParser markdownParser;
    private final Chunker chunker;
    private final EmbeddingService embeddingService;
    private final VectorRepository vectorRepository;
    private final MilvusProperties milvusProperties;
    private final int embeddingBatchSize;

    public KnowledgeIndexService(VaultRepository vaultRepository,
                                 MarkdownParser markdownParser,
                                 Chunker chunker,
                                 EmbeddingService embeddingService,
                                 VectorRepository vectorRepository,
                                 MilvusProperties milvusProperties,
                                 AiProperties aiProperties) {
        this.vaultRepository = vaultRepository;
        this.markdownParser = markdownParser;
        this.chunker = chunker;
        this.embeddingService = embeddingService;
        this.vectorRepository = vectorRepository;
        this.milvusProperties = milvusProperties;
        this.embeddingBatchSize = Math.max(1, aiProperties.embeddingOrDefault().batchSize());
    }

    /**
     * 执行一次全量增量索引（同步）。
     *
     * @return 各状态计数与 per-file 错误清单；整体级失败（Ollama/Milvus 不可用、维度配置矛盾）直接抛业务异常（503/500）
     */
    public IndexResult run() {
        long startedAt = System.currentTimeMillis();
        vaultRepository.requireRoot(); // 未连接 Vault → VaultNotFoundException；访问受限 → 对应异常
        VaultRepository.ScanResult scan = vaultRepository.scan();
        List<FileMeta> notes = vaultRepository.allNotes();
        String vaultId = vaultRepository.requireVaultId();
        String collection = milvusProperties.collection();

        // 存量索引状态（单一事实源：向量库元数据，不引入本地缓存第二状态源；仅限当前 Vault）
        Map<String, String> storedHashes = vectorRepository.loadDocumentHashes(collection, vaultId);

        // ── 阶段 1：分类与解析切块 ─────────────────────────────────────────────
        List<PendingDoc> pending = new ArrayList<>();
        List<String> deletedIds = new ArrayList<>();
        List<DocError> errors = new ArrayList<>();
        int skipped = 0;

        for (FileMeta meta : notes) {
            String path = meta.path();
            try {
                String raw = vaultRepository.readRaw(path);
                String hash = Hashes.sha256Hex(raw);
                String storedHash = storedHashes.get(path);
                if (hash.equals(storedHash)) {
                    skipped++; // 内容未变：不做解析/切块/嵌入
                    continue;
                }
                Document document = buildDocument(vaultId, meta, raw, hash);
                pending.add(new PendingDoc(path, storedHash != null, chunker.chunk(document)));
            } catch (NoteReadFailedException e) {
                errors.add(new DocError(path, CODE_PARSE, e.getMessage()));
            } catch (RuntimeException e) {
                errors.add(new DocError(path, CODE_CHUNK, e.getMessage()));
            }
        }

        // 文件已删除：收集待清理 id
        Set<String> currentIds = new HashSet<>();
        notes.forEach(meta -> currentIds.add(meta.path()));
        storedHashes.keySet().stream()
                .filter(id -> !currentIds.contains(id))
                .forEach(deletedIds::add);

        // ── 阶段 2：全局批量向量化（batch-size 配置化；失败即中止，未尝试文档全部记 FAILED）──
        long embeddingStartedAt = System.currentTimeMillis();
        int actualDimension = -1;
        boolean embedAborted = false;
        String embedAbortMessage = null;
        for (PendingDoc doc : pending) {
            if (embedAborted) {
                doc.fail(CODE_EMBEDDING, embedAbortMessage); // 中止后未尝试的文档如实记失败
                continue;
            }
            if (doc.chunks().isEmpty()) {
                continue; // 空文档：无向量可写，非错误
            }
            List<float[]> docVectors = new ArrayList<>(doc.chunks().size());
            for (int from = 0; from < doc.chunks().size() && !embedAborted; from += embeddingBatchSize) {
                List<Chunk> batch = doc.chunks()
                        .subList(from, Math.min(from + embeddingBatchSize, doc.chunks().size()));
                try {
                    docVectors.addAll(embeddingService.embed(batch.stream().map(Chunk::content).toList()));
                } catch (RuntimeException e) {
                    embedAborted = true;
                    embedAbortMessage = e.getMessage();
                    doc.fail(CODE_EMBEDDING, embedAbortMessage);
                }
            }
            if (!embedAborted && !docVectors.isEmpty()) {
                doc.vectors = docVectors;
                if (actualDimension < 0) {
                    actualDimension = docVectors.get(0).length;
                }
            }
        }
        long embeddingMs = System.currentTimeMillis() - embeddingStartedAt;

        // 维度一致性校验：实际 embedding 维度必须与配置一致（禁止把未知维度写进 Collection）
        if (actualDimension > 0 && actualDimension != milvusProperties.vectorDimension()) {
            throw new com.obsidianmind.exception.EmbeddingException(
                    "Embedding 维度不符: 模型输出 " + actualDimension + "，配置 milvus.vector-dimension="
                            + milvusProperties.vectorDimension() + "（换模型后需同步配置并清空 Collection 重建）");
        }

        // 归账：向量化失败（含中止后未尝试）的文档逐文件记录
        for (PendingDoc doc : pending) {
            if (doc.errorCode() != null) {
                errors.add(doc.error());
            }
        }

        // ── 阶段 3：向量库写入（ensureCollection → 删除失效 → 逐文档 upsert）────────
        long storeStartedAt = System.currentTimeMillis();
        List<PendingDoc> ready = pending.stream()
                .filter(doc -> doc.errorCode() == null && !doc.chunks().isEmpty())
                .toList();
        if (!ready.isEmpty() || !deletedIds.isEmpty()) {
            try {
                vectorRepository.ensureCollection(collection,
                        actualDimension > 0 ? actualDimension : milvusProperties.vectorDimension());
            } catch (RuntimeException e) {
                // 集合初始化失败：全部待写入文档记 VECTOR_STORE_ERROR（逐文件归属）
                for (PendingDoc doc : ready) {
                    if (doc.errorCode() == null) {
                        doc.fail(CODE_VECTOR_STORE, "Collection 初始化失败: " + e.getMessage());
                        errors.add(doc.error());
                    }
                }
                ready = List.of();
            }
        }
        if (!deletedIds.isEmpty()) {
            try {
                vectorRepository.deleteByDocuments(collection, vaultId, deletedIds);
            } catch (RuntimeException e) {
                deletedIds.forEach(id -> errors.add(new DocError(id, CODE_VECTOR_STORE, e.getMessage())));
            }
        }
        for (PendingDoc doc : ready) {
            try {
                vectorRepository.upsert(collection, doc.chunks(), doc.vectors);
            } catch (RuntimeException e) {
                doc.fail(CODE_VECTOR_STORE, e.getMessage());
                errors.add(doc.error());
            }
        }
        long storeMs = System.currentTimeMillis() - storeStartedAt;

        // ── 归账 ──────────────────────────────────────────────────────────────
        int indexed = 0;
        int updated = 0;
        int chunkCount = 0;
        for (PendingDoc doc : pending) {
            if (doc.errorCode() == null) {
                if (doc.existedBefore) {
                    updated++;
                } else {
                    indexed++;
                }
                chunkCount += doc.chunks().size();
            }
        }
        int failed = errors.size();

        IndexResult result = new IndexResult(
                notes.size(), indexed, updated, skipped, deletedIds.size(), failed,
                chunkCount, System.currentTimeMillis() - startedAt, List.copyOf(errors));
        log.info("索引完成: scan={}ms files={} chunks={} indexed={} updated={} skipped={} deleted={} failed={} "
                        + "embeddingMs={} storeMs={}",
                scan.durationMs(), notes.size(), chunkCount, indexed, updated, skipped,
                deletedIds.size(), failed, embeddingMs, storeMs);
        if (!errors.isEmpty()) {
            log.warn("索引失败明细: {}", errors);
        }
        return result;
    }

    /** FileMeta + ParsedMarkdown + hash → Document（编排级粘合，解析细节仍在 MarkdownParser）。 */
    private Document buildDocument(String vaultId, FileMeta meta, String raw, String hash) {
        ParsedMarkdown parsed = markdownParser.parse(raw);
        String title = parsed.title() != null ? parsed.title()
                : meta.name().replaceAll("(?i)\\.md$", "");
        List<String> headings = parsed.body().lines()
                .filter(line -> line.matches("^#{1,6}\\s+.*"))
                .map(line -> line.replaceFirst("^#+\\s+", "").trim())
                .toList();
        return new Document(
                meta.path(),
                vaultId,
                meta.path(),
                title,
                parsed.body(),
                parsed.frontmatter(),
                parsed.tags(),
                headings,
                parsed.wikiLinks(),
                meta.modifiedAt(),
                hash);
    }

    /** 待写入文档（编排内部可变状态：向量与错误在阶段 2/3 填充）。 */
    private static final class PendingDoc {
        private final String documentId;
        private final boolean existedBefore;
        private final List<Chunk> chunks;
        private List<float[]> vectors = List.of();
        private String errorCode;
        private String errorMessage;

        private PendingDoc(String documentId, boolean existedBefore, List<Chunk> chunks) {
            this.documentId = documentId;
            this.existedBefore = existedBefore;
            this.chunks = chunks;
        }

        List<Chunk> chunks() {
            return chunks;
        }

        boolean existedBefore() {
            return existedBefore;
        }

        String errorCode() {
            return errorCode;
        }

        void fail(String code, String message) {
            if (errorCode == null) {
                this.errorCode = code;
                this.errorMessage = message;
            }
        }

        DocError error() {
            return new DocError(documentId, errorCode, errorMessage);
        }
    }

    /** 单文件错误（携带稳定错误码，见 knowledge-pipeline.md §六）。 */
    public record DocError(String documentId, String code, String message) {
    }

    /**
     * 索引结果。
     *
     * @param total      扫描到的 Markdown 文件数
     * @param indexed    新增索引成功文档数
     * @param updated    内容变化重建索引成功文档数
     * @param skipped    内容未变化跳过文档数
     * @param deleted    文件已删除并清理向量的文档数
     * @param failed     失败文档数（= errors.size()，含文件级与删除清理失败）
     * @param chunkCount 成功索引文档的 Chunk 总数
     * @param elapsedMs  总耗时
     * @param errors     per-file 错误明细
     */
    public record IndexResult(
            int total,
            int indexed,
            int updated,
            int skipped,
            int deleted,
            int failed,
            int chunkCount,
            long elapsedMs,
            List<DocError> errors) {
    }
}
