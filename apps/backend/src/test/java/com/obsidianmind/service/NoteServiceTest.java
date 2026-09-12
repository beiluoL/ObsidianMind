package com.obsidianmind.service;

import com.obsidianmind.domain.Note;
import com.obsidianmind.parser.FrontmatterParser;
import com.obsidianmind.parser.MarkdownParser;
import com.obsidianmind.parser.WikiLinkParser;
import com.obsidianmind.repository.VaultRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * NoteService 测试：读取 / 保存 / 路径安全。
 */
class NoteServiceTest {

    @TempDir
    Path vaultDir;

    private NoteService noteService;

    @BeforeEach
    void setUp() throws Exception {
        Path ai = Files.createDirectories(vaultDir.resolve("AI"));
        Files.writeString(ai.resolve("RAG.md"), """
                ---
                title: RAG 技术原理
                tags: [AI, RAG]
                ---

                # RAG

                RAG 是检索增强生成技术，见 [[Embedding]]。
                """);
        VaultRepository repository = new VaultRepository();
        repository.connect(vaultDir.toString());
        repository.scan();
        noteService = new NoteService(repository, new MarkdownParser(new FrontmatterParser(), new WikiLinkParser()));
    }

    @Test
    void shouldReadNoteWithParsedMetadata() {
        Note note = noteService.getNote("AI/RAG.md");
        assertEquals("RAG 技术原理", note.title());
        assertEquals("AI", note.folder());
        assertEquals(java.util.List.of("AI", "RAG"), note.tags());
        assertEquals(java.util.List.of("Embedding"), note.links());
        assertTrue(note.content().contains("[[Embedding]]"));
    }

    @Test
    void shouldSaveNoteBackToOriginalFile() throws Exception {
        noteService.saveNote("AI/RAG.md", "# RAG\n\n更新后的内容");
        String onDisk = Files.readString(vaultDir.resolve("AI/RAG.md"));
        assertTrue(onDisk.contains("更新后的内容"));
        Note reloaded = noteService.getNote("AI/RAG.md");
        assertTrue(reloaded.content().contains("更新后的内容"));
    }

    @Test
    void shouldRejectTraversalOnRead() {
        assertThrows(Exception.class, () -> noteService.getNote("../outside.md"));
    }

    @Test
    void shouldThrowWhenNoteMissing() {
        assertThrows(Exception.class, () -> noteService.getNote("AI/不存在.md"));
    }

    @Test
    void shouldFindBacklinks() {
        // 增加一篇链接到 RAG 的笔记
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> {
            Files.writeString(vaultDir.resolve("AI/Embedding.md"), "# Embedding\n\n被 [[RAG]] 使用。");
            VaultRepository repository = new VaultRepository();
            repository.connect(vaultDir.toString());
            repository.scan();
            NoteService service = new NoteService(repository,
                    new MarkdownParser(new FrontmatterParser(), new WikiLinkParser()));
            java.util.List<NoteService.Backlink> backlinks = service.backlinks("AI/RAG.md");
            assertEquals(1, backlinks.size());
            assertEquals("AI/Embedding.md", backlinks.get(0).noteId());
        });
    }
}
