package com.obsidianmind.controller;

import com.obsidianmind.domain.Note;
import com.obsidianmind.dto.note.SaveNoteRequest;
import com.obsidianmind.service.NoteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Note API：读取 / 保存 / 反向链接。
 * id 为 Vault 相对路径（可含多级目录），Spring PathPattern 用 {*id} 捕获（值带前导 /，需剥离）。
 * 说明：PathPattern 语法要求 {*id} 位于末尾，无法表达 /{id}/backlinks，
 * 因此反向链接使用 GET /api/v1/notes/backlinks?note={id}（字面量路由优先级高于 {*id}，不冲突）。
 */
@RestController
@RequestMapping("/api/v1/notes")
@Tag(name = "Notes", description = "笔记读写与反向链接")
public class NoteController {

    private final NoteService noteService;

    public NoteController(NoteService noteService) {
        this.noteService = noteService;
    }

    @GetMapping("/{*id}")
    @Operation(summary = "读取笔记（解析 Frontmatter / Tags / Wiki Links）")
    public Note get(@PathVariable("id") String rawId) {
        return noteService.getNote(stripLeadingSlash(rawId));
    }

    @PutMapping("/{*id}")
    @Operation(summary = "保存笔记（写回 Markdown 原文件，拒绝路径穿越）")
    public Map<String, Object> save(@PathVariable("id") String rawId,
                                    @Valid @RequestBody SaveNoteRequest request) {
        String id = stripLeadingSlash(rawId);
        Instant modifiedAt = noteService.saveNote(id, request.content());
        return Map.of("saved", true, "path", id, "modifiedAt", modifiedAt.toString());
    }

    @GetMapping("/backlinks")
    @Operation(summary = "反向链接（note 参数为 Vault 相对路径）")
    public List<NoteService.Backlink> backlinks(@RequestParam("note") String noteId) {
        return noteService.backlinks(noteId);
    }

    private String stripLeadingSlash(String rawId) {
        return rawId.startsWith("/") ? rawId.substring(1) : rawId;
    }
}
