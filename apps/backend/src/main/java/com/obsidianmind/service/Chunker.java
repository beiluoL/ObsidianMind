package com.obsidianmind.service;

import com.obsidianmind.domain.Chunk;
import com.obsidianmind.parser.MarkdownParser;
import com.obsidianmind.parser.ParsedMarkdown;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 简易切块器：按标题分节 → 段落聚合到 maxChars 上限。
 * Phase 2 接入 Embedding 时可替换为更精细策略（重叠窗口 / 语义切块），接口不变。
 */
@Component
public class Chunker {

    private static final int MAX_CHARS = 800;
    private static final int MIN_CHARS = 60;

    public List<Chunk> chunk(String noteId, String title, String path, ParsedMarkdown parsed) {
        List<Chunk> chunks = new ArrayList<>();
        String body = parsed.body() == null ? "" : parsed.body();
        List<Section> sections = splitByHeadings(body);
        int index = 0;
        for (Section section : sections) {
            for (String piece : aggregate(section.text())) {
                if (piece.isBlank()) {
                    continue;
                }
                Map<String, Object> metadata = Map.of(
                        "title", title,
                        "path", path,
                        "tags", parsed.tags());
                chunks.add(new Chunk(noteId + "#c" + index, noteId, piece.strip(), index, section.heading(), metadata));
                index++;
            }
        }
        if (chunks.isEmpty() && !body.isBlank()) {
            chunks.add(new Chunk(noteId + "#c0", noteId, body.strip(), 0, "", Map.of("title", title, "path", path)));
        }
        return chunks;
    }

    private List<Section> splitByHeadings(String body) {
        List<Section> sections = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        String currentHeading = "";
        for (String line : body.split("\n", -1)) {
            if (line.matches("^#{1,6}\\s+.*")) {
                if (!current.isEmpty()) {
                    sections.add(new Section(currentHeading, current.toString()));
                    current = new StringBuilder();
                }
                currentHeading = line.replaceFirst("^#+\\s+", "").trim();
            }
            current.append(line).append('\n');
        }
        if (!current.isEmpty()) {
            sections.add(new Section(currentHeading, current.toString()));
        }
        if (sections.isEmpty()) {
            sections.add(new Section("", body));
        }
        return sections;
    }

    /** 段落聚合：优先保持段落完整，超长段落按行切分到 maxChars。 */
    private List<String> aggregate(String sectionText) {
        List<String> pieces = new ArrayList<>();
        StringBuilder buf = new StringBuilder();
        for (String paragraph : sectionText.split("\n\\s*\n")) {
            if ((buf.length() + paragraph.length()) > MAX_CHARS && buf.length() >= MIN_CHARS) {
                pieces.add(buf.toString());
                buf = new StringBuilder();
            }
            if (paragraph.length() > MAX_CHARS) {
                if (!buf.isEmpty()) {
                    pieces.add(buf.toString());
                    buf = new StringBuilder();
                }
                pieces.addAll(splitByLines(paragraph));
                continue;
            }
            buf.append(paragraph.strip()).append("\n\n");
        }
        if (!buf.isEmpty()) {
            pieces.add(buf.toString());
        }
        return pieces;
    }

    private List<String> splitByLines(String paragraph) {
        List<String> parts = new ArrayList<>();
        StringBuilder buf = new StringBuilder();
        for (String line : paragraph.split("\n")) {
            if (buf.length() + line.length() > MAX_CHARS && !buf.isEmpty()) {
                parts.add(buf.toString());
                buf = new StringBuilder();
            }
            buf.append(line).append('\n');
        }
        if (!buf.isEmpty()) {
            parts.add(buf.toString());
        }
        return parts;
    }

    private record Section(String heading, String text) {
    }
}
