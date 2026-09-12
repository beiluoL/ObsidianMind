package com.obsidianmind.domain;

/**
 * 笔记间 Wiki Link：source → target。
 */
public record NoteLink(
        String sourceNoteId,
        String targetNoteId,
        String targetName
) {
}
