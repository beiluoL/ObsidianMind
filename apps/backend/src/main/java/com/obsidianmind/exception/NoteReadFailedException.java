package com.obsidianmind.exception;

/**
 * 笔记读取失败（对应 HTTP 500）：文件存在但 IO 读取异常（权限、编码、磁盘等）。
 */
public class NoteReadFailedException extends BusinessException {
    public NoteReadFailedException(String message) {
        super("NOTE_READ_FAILED", message);
    }
}
