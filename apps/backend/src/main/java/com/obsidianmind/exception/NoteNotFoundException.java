package com.obsidianmind.exception;

/**
 * 笔记不存在（对应 HTTP 404）：按 id 读取/保存时 Vault 中无对应 Markdown 文件。
 */
public class NoteNotFoundException extends BusinessException {
    public NoteNotFoundException(String message) {
        super("NOTE_NOT_FOUND", message);
    }
}
