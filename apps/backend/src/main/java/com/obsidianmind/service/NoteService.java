package com.obsidianmind.service;

import com.obsidianmind.domain.Note;
import com.obsidianmind.parser.MarkdownParser;
import com.obsidianmind.parser.ParsedMarkdown;
import com.obsidianmind.repository.VaultRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 笔记服务：按需读取/保存 Markdown，解析 Frontmatter / Tags / Wiki Links，计算 Backlinks。
 */
@Service
public class NoteService {

    private static final Logger log = LoggerFactory.getLogger(NoteService.class);

    private final VaultRepository vaultRepository;
    private final MarkdownParser markdownParser;

    public NoteService(VaultRepository vaultRepository, MarkdownParser markdownParser) {
        this.vaultRepository = vaultRepository;
        this.markdownParser = markdownParser;
    }

    /**
     * 读取单篇笔记：标题解析优先级 frontmatter title → 正文首个 H1 → 文件名兜底；
     * 目录取相对路径前缀（根级为空串），字数按去空白后的字符数统计。
     *
     * @param id 笔记 id（Vault 相对路径，如 "AI/RAG.md"）
     * @return 聚合了元数据/标签/链接/正文的 Note
     * @throws NoteNotFoundException 笔记不存在
     */
    public Note getNote(String id) {
        String raw = vaultRepository.readRaw(id);
        ParsedMarkdown parsed = markdownParser.parse(raw);
        String fileName = id.substring(id.lastIndexOf('/') + 1);
        String fallbackTitle = fileName.replaceAll("(?i)\\.md$", "");
        String folder = id.contains("/") ? id.substring(0, id.lastIndexOf('/')) : "";
        String title = parsed.title() != null ? parsed.title() : fallbackTitle;
        log.debug("Note read: {}", id);
        return new Note(
                id,
                title,
                id,
                folder,
                raw,
                parsed.tags(),
                parsed.wikiLinks(),
                vaultRepository.noteMeta(id) != null
                        ? vaultRepository.noteMeta(id).modifiedAt()
                        : Instant.now(),
                wordCount(raw));
    }

    public Instant saveNote(String id, String content) {
        return vaultRepository.writeRaw(id, content);
    }

    /** 反向链接：扫描全部笔记内容，找出 [[target]] 指向给定笔记的来源（含上下文摘要）。 */
    public List<Backlink> backlinks(String targetNoteId) {
        String targetBaseName = targetNoteId.substring(targetNoteId.lastIndexOf('/') + 1)
                .replaceAll("(?i)\\.md$", "");
        List<Backlink> result = new ArrayList<>();
        for (var meta : vaultRepository.allNotes()) {
            if (meta.path().equals(targetNoteId)) {
                continue;
            }
            try {
                String raw = vaultRepository.readRaw(meta.path());
                ParsedMarkdown parsed = markdownParser.parse(raw);
                boolean linked = parsed.wikiLinks().stream().anyMatch(t -> matchesTarget(t, targetBaseName));
                if (linked) {
                    result.add(new Backlink(meta.path(), titleOf(meta.path(), parsed), contextOf(raw, targetBaseName)));
                }
            } catch (RuntimeException e) {
                log.warn("Backlink 扫描跳过不可读笔记: {}", meta.path());
            }
        }
        return result;
    }

    private boolean matchesTarget(String wikiTarget, String baseName) {
        String lastSeg = wikiTarget.substring(wikiTarget.lastIndexOf('/') + 1);
        return lastSeg.equalsIgnoreCase(baseName) || wikiTarget.equalsIgnoreCase(baseName);
    }

    private String titleOf(String path, ParsedMarkdown parsed) {
        if (parsed.title() != null) {
            return parsed.title();
        }
        return path.substring(path.lastIndexOf('/') + 1).replaceAll("(?i)\\.md$", "");
    }

    private String contextOf(String content, String baseName) {
        int idx = content.toLowerCase(Locale.ROOT).indexOf("[[" + baseName.toLowerCase(Locale.ROOT));
        if (idx < 0) {
            return "";
        }
        int from = Math.max(0, idx - 40);
        int to = Math.min(content.length(), idx + baseName.length() + 60);
        return content.substring(from, to).replaceAll("\n+", " ").trim();
    }

    private int wordCount(String content) {
        return content.replaceAll("\\s+", "").length();
    }

    /**
     * 反向链接条目。
     */
    public record Backlink(String noteId, String title, String context) {
    }
}
