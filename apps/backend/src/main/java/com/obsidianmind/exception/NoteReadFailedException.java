package com.obsidianmind.exception;

public class NoteReadFailedException extends BusinessException {
    public NoteReadFailedException(String message) {
        super("NOTE_READ_FAILED", message);
    }
}
