package com.obsidianmind.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 索引任务门面（兼容 Phase 1 异步 API）：包装 {@link KnowledgeIndexService} 的同步管线为异步任务。
 * 状态机：IDLE → SCANNING → INDEXING → READY / FAILED；重复触发直接忽略。
 * 新代码请直接使用 POST /api/v1/index/run（同步返回完整结果）。
 */
@Service
public class IndexService {

    private static final Logger log = LoggerFactory.getLogger(IndexService.class);

    public enum Status {
        IDLE, SCANNING, INDEXING, READY, FAILED
    }

    private final KnowledgeIndexService knowledgeIndexService;

    private final AtomicReference<Status> status = new AtomicReference<>(Status.IDLE);
    private volatile KnowledgeIndexService.IndexResult lastResult;
    private volatile Instant lastRunAt;

    public IndexService(KnowledgeIndexService knowledgeIndexService) {
        this.knowledgeIndexService = knowledgeIndexService;
    }

    /**
     * 异步触发一次完整增量索引；结果缓存供状态查询。
     * 任意失败（业务异常或整体 503）落到 FAILED，状态在开始时统一复位。
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
            status.set(Status.INDEXING);
            lastResult = knowledgeIndexService.run();
            lastRunAt = Instant.now();
            status.set(Status.READY);
            log.info("异步索引任务完成: total={} failed={} chunks={}",
                    lastResult.total(), lastResult.failed(), lastResult.chunkCount());
        } catch (RuntimeException e) {
            status.set(Status.FAILED);
            log.error("异步索引任务失败", e);
        }
    }

    public IndexStatusResponse status() {
        KnowledgeIndexService.IndexResult result = lastResult;
        return new IndexStatusResponse(
                status.get().name(),
                result == null ? 0 : result.total(),
                result == null ? 0 : result.indexed() + result.updated(),
                result == null ? 0 : result.failed(),
                result == null ? 0 : result.chunkCount(),
                lastRunAt == null ? null : lastRunAt.toString());
    }

    /**
     * 索引状态响应（Phase 1 契约保持不变）。
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
