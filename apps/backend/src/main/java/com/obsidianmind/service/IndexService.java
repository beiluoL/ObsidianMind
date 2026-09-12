package com.obsidianmind.service;

import com.obsidianmind.repository.VaultRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 索引服务（Phase 1）：读取 Vault → 解析 metadata → 生成 Chunk（索引任务）。
 * 暂不连接 Milvus、不调用 Embedding——只统计切块结果，为 Phase 2 链路铺路。
 * 状态机：IDLE → SCANNING → INDEXING → READY / FAILED。
 */
@Service
public class IndexService {

    private static final Logger log = LoggerFactory.getLogger(IndexService.class);

    public enum Status {
        IDLE, SCANNING, INDEXING, READY, FAILED
    }

    private final VaultRepository vaultRepository;
    private final com.obsidianmind.service.Chunker chunker;
    private final com.obsidianmind.parser.MarkdownParser markdownParser;

    private final AtomicReference<Status> status = new AtomicReference<>(Status.IDLE);
    private volatile int totalNotes;
    private volatile int indexedNotes;
    private volatile int chunkCount;
    private volatile int failedNotes;
    private volatile Instant lastRunAt;

    public IndexService(VaultRepository vaultRepository, Chunker chunker,
                        com.obsidianmind.parser.MarkdownParser markdownParser) {
        this.vaultRepository = vaultRepository;
        this.chunker = chunker;
        this.markdownParser = markdownParser;
    }

    /**
     * 异步索引任务：CAS 抢占状态（仅 IDLE/READY/FAILED 可启动，重复触发直接忽略）
     * → 全量扫描 → 逐篇 解析+切块（单篇失败计入 failedNotes 不中断）→ READY。
     * 任意阶段异常最终落到 FAILED，状态与计数器在开始时统一复位。
     */
    @Async
    public void startIndexing() {
        if (!status.compareAndSet(Status.IDLE, Status.SCANNING)
                && !status.compareAndSet(Status.READY, Status.SCANNING)
                && !status.compareAndSet(Status.FAILED, Status.SCANNING)) {
            log.info("索引已在进行中，忽略重复触发");
            return;
        }
        try {
            log.info("Vault scan started（索引任务）");
            VaultRepository.ScanResult scan = vaultRepository.scan();
            totalNotes = scan.markdownFiles();
            indexedNotes = 0;
            chunkCount = 0;
            failedNotes = 0;
            status.set(Status.INDEXING);

            for (var meta : vaultRepository.allNotes()) {
                try {
                    String raw = vaultRepository.readRaw(meta.path());
                    var parsed = markdownParser.parse(raw);
                    String title = parsed.title() != null ? parsed.title()
                            : meta.path().substring(meta.path().lastIndexOf('/') + 1).replaceAll("(?i)\\.md$", "");
                    var chunks = chunker.chunk(meta.path(), title, meta.path(), parsed);
                    chunkCount += chunks.size();
                    indexedNotes++;
                } catch (RuntimeException e) {
                    failedNotes++;
                    log.warn("索引单篇失败: {} ({})", meta.path(), e.getMessage());
                }
            }
            lastRunAt = Instant.now();
            status.set(Status.READY);
            log.info("索引任务完成: {} 篇笔记, {} 个 Chunk, 失败 {} 篇", indexedNotes, chunkCount, failedNotes);
        } catch (RuntimeException e) {
            status.set(Status.FAILED);
            log.error("索引任务失败", e);
        }
    }

    public IndexStatusResponse status() {
        return new IndexStatusResponse(
                status.get().name(),
                totalNotes,
                indexedNotes,
                failedNotes,
                chunkCount,
                lastRunAt == null ? null : lastRunAt.toString());
    }

    /**
     * 索引状态响应。
     */
    public record IndexStatusResponse(
            String status,
            int totalNotes,
            int indexedNotes,
            int failedNotes,
            int chunkCount,
            String lastRunAt) {
    }
}
