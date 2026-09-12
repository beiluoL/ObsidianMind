package com.obsidianmind.service;

import com.obsidianmind.domain.SearchResult;
import com.obsidianmind.parser.MarkdownParser;
import com.obsidianmind.parser.ParsedMarkdown;
import com.obsidianmind.repository.FileMeta;
import com.obsidianmind.repository.VaultRepository;
import com.obsidianmind.util.SnippetBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 全文搜索服务（Phase 1）。
 * score 为文本匹配分（标题/Tag/路径/正文词频加权），matchType 明确标注 TEXT_MATCH——
 * 不是向量相似度；Phase 2 接入 Milvus 后统一 SearchResult 语义。
 */
@Service
public class SearchService {

    private static final Logger log = LoggerFactory.getLogger(SearchService.class);
    private static final int MAX_RESULTS = 50;

    private final VaultRepository vaultRepository;
    private final MarkdownParser markdownParser;

    public SearchService(VaultRepository vaultRepository, MarkdownParser markdownParser) {
        this.vaultRepository = vaultRepository;
        this.markdownParser = markdownParser;
    }

    public List<SearchResult> search(String query) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) {
            return List.of();
        }
        List<String> terms = List.of(q.split("\\s+"));
        List<SearchResult> results = new ArrayList<>();
        for (var meta : vaultRepository.allNotes()) {
            try {
                String raw = vaultRepository.readRaw(meta.path());
                ParsedMarkdown parsed = markdownParser.parse(raw);
                double score = score(meta, parsed, raw, terms);
                if (score > 0) {
                    results.add(new SearchResult(
                            meta.path(),
                            titleOf(meta.path(), parsed),
                            meta.path(),
                            SnippetBuilder.build(stripFrontmatter(raw, parsed), query, 160),
                            round(score),
                            SearchResult.MATCH_TYPE_TEXT));
                }
            } catch (RuntimeException e) {
                log.warn("搜索跳过不可读笔记: {}", meta.path());
            }
        }
        results.sort((a, b) -> Double.compare(b.score(), a.score()));
        log.info("搜索完成: query='{}', 命中 {} 条", query, Math.min(results.size(), MAX_RESULTS));
        return results.size() > MAX_RESULTS ? results.subList(0, MAX_RESULTS) : results;
    }

    private double score(FileMeta meta, ParsedMarkdown parsed, String raw, List<String> terms) {
        double total = 0;
        for (String term : terms) {
            double s = 0;
            String title = titleOf(meta.path(), parsed).toLowerCase(Locale.ROOT);
            if (title.equals(term)) {
                s += 4.0;
            } else if (title.contains(term)) {
                s += 3.0;
            }
            if (parsed.tags().stream().anyMatch(t -> t.toLowerCase(Locale.ROOT).contains(term))) {
                s += 2.0;
            }
            if (meta.path().toLowerCase(Locale.ROOT).contains(term)) {
                s += 1.0;
            }
            int tf = countOccurrences(raw.toLowerCase(Locale.ROOT), term);
            if (tf > 0) {
                s += 0.4 * Math.min(tf, 5);
            }
            total += s;
        }
        return total;
    }

    private int countOccurrences(String content, String term) {
        int count = 0;
        int idx = 0;
        while ((idx = content.indexOf(term, idx)) >= 0) {
            count++;
            idx += term.length();
        }
        return count;
    }

    private String stripFrontmatter(String raw, ParsedMarkdown parsed) {
        return parsed.body() == null ? raw : parsed.body();
    }

    private String titleOf(String path, ParsedMarkdown parsed) {
        if (parsed.title() != null) {
            return parsed.title();
        }
        return path.substring(path.lastIndexOf('/') + 1).replaceAll("(?i)\\.md$", "");
    }

    private double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
