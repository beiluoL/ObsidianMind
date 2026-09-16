package com.obsidianmind.retrieval;

import com.obsidianmind.repository.VaultRepository;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 关键词检索 Provider（Phase 6）：BM25 over Vault Chunk 内存索引。
 *
 * 擅长：精确类名 / 方法名 / 错误码 / 版本号 / 文件名（HashMap、ConcurrentHashMap、HTTP 500）。
 * 不擅长：同义改写与语义变化（那是向量路的事——互补性见 docs/architecture/hybrid-retrieval.md）。
 * 中文方案：bigram 分词（真实可用，非假装），局限如实记录（Keyword Search Limited）。
 * 安全：查询经 TextTokenizer 切成普通 token，无查询语法，无注入面。
 */
@Component
public class KeywordRetrievalProvider implements RetrievalProvider {

    private final VaultRepository vaultRepository;
    private final VaultKeywordIndex index;

    public KeywordRetrievalProvider(VaultRepository vaultRepository, VaultKeywordIndex index) {
        this.vaultRepository = vaultRepository;
        this.index = index;
    }

    @Override
    public String name() {
        return "keyword";
    }

    @Override
    public boolean isAvailable() {
        return vaultRepository.isConnected();
    }

    @Override
    public List<RetrievalCandidate> retrieve(String query, int candidateCount) {
        return index.search(query, Math.max(1, candidateCount));
    }
}
