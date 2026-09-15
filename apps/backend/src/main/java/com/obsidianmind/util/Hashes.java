package com.obsidianmind.util;

import com.obsidianmind.exception.NoteReadFailedException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 哈希工具：内容指纹（增量索引判定依据）。
 */
public final class Hashes {

    private Hashes() {
    }

    /** SHA-256 十六进制摘要（用于 Markdown 原文 contentHash）。 */
    public static String sha256Hex(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new NoteReadFailedException("SHA-256 不可用");
        }
    }
}
