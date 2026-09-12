package com.obsidianmind.exception;

public class NoteNotFoundException extends BusinessException {
    public NoteNotFoundException(String message) {
        super("NOTE_NOT_FOUND", message);
    }
}
