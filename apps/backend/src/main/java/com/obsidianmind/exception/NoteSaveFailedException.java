package com.obsidianmind.exception;

/**
 * 笔记保存失败（对应 HTTP 500）：写入 Markdown 文件时 IO 异常（只读、磁盘满等）。
 */
public class NoteSaveFailedException extends BusinessException {
    public NoteSaveFailedException(String message) {
        super("NOTE_SAVE_FAILED", message);
    }
}
