package com.obsidianmind.exception;

public class NoteSaveFailedException extends BusinessException {
    public NoteSaveFailedException(String message) {
        super("NOTE_SAVE_FAILED", message);
    }
}
