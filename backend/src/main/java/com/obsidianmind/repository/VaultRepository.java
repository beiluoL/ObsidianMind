package com.obsidianmind.repository;

import com.obsidianmind.exception.BusinessException;
import com.obsidianmind.exception.InvalidRequestException;
import com.obsidianmind.exception.NoteNotFoundException;
import com.obsidianmind.exception.NoteReadFailedException;
import com.obsidianmind.exception.NoteSaveFailedException;
import com.obsidianmind.exception.VaultAccessDeniedException;
import com.obsidianmind.exception.VaultNotFoundException;
import com.obsidianmind.util.VaultPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Vault Repository —— 本地文件系统访问的唯一边界。
 * 只访问用户显式连接的 Vault 目录，扫描阶段只收集元数据（不读正文）。
 */
@Repository
public class VaultRepository {

    private static final Logger log = LoggerFactory.getLogger(VaultRepository.class);

    private volatile Path root;
    private final Map<String, FileMeta> files = new ConcurrentHashMap<>();
    private volatile int folderCount;
    private volatile Instant lastScanAt;

    public synchronized void connect(String path) {
        Path candidate = Path.of(path).toAbsolutePath().normalize();
        if (!Files.exists(candidate)) {
            throw new VaultNotFoundException("路径不存在: " + path);
        }
        if (!Files.isDirectory(candidate)) {
            throw new InvalidRequestException("路径不是目录: " + path);
        }
        if (!Files.isReadable(candidate)) {
            throw new VaultAccessDeniedException("目录不可读: " + path);
        }
        this.root = candidate;
        this.files.clear();
        this.lastScanAt = null;
        log.info("Vault connected: {}", candidate);
    }

    public synchronized void disconnect() {
        this.root = null;
        this.files.clear();
        this.folderCount = 0;
        this.lastScanAt = null;
        log.info("Vault disconnected");
    }

    public boolean isConnected() {
        return root != null;
    }

    public Path requireRoot() {
        Path current = root;
        if (current == null) {
            throw new VaultNotFoundException("未连接 Vault，请先调用 POST /api/v1/vault/connect");
        }
        return current;
    }

    /** 只在启动配置了默认路径时自动连接；路径无效则忽略（不阻塞启动）。 */
    public synchronized void tryConnectDefault(String configuredPath) {
        if (configuredPath == null || configuredPath.isBlank()) {
            return;
        }
        try {
            connect(configuredPath);
        } catch (BusinessException e) {
            log.warn("默认 Vault 路径不可用: {} ({})", configuredPath, e.getCode());
        }
    }

    public ScanResult scan() {
        Path vaultRoot = requireRoot();
        files.clear();
        folderCount = 0;
        long start = System.nanoTime();
        try {
            Files.walkFileTree(vaultRoot, new java.nio.file.SimpleFileVisitor<>() {
                @Override
                public java.nio.file.FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (!dir.equals(vaultRoot)) {
                        String name = dir.getFileName() == null ? "" : dir.getFileName().toString();
                        if (name.startsWith(".")) {
                            return java.nio.file.FileVisitResult.SKIP_SUBTREE;
                        }
                        folderCount++;
                    }
                    return java.nio.file.FileVisitResult.CONTINUE;
                }

                @Override
                public java.nio.file.FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (file.getFileName().toString().toLowerCase().endsWith(".md")) {
                        Path relative = vaultRoot.relativize(file);
                        files.put(relative.toString(), FileMeta.of(relative, file));
                    }
                    return java.nio.file.FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            log.error("Vault 扫描失败", e);
            throw new NoteReadFailedException("Vault 扫描失败: " + e.getMessage());
        }
        lastScanAt = Instant.now();
        long durationMs = (System.nanoTime() - start) / 1_000_000;
        log.info("Vault 扫描完成: {} 篇笔记, {} 个文件夹, {} ms", files.size(), folderCount, durationMs);
        return new ScanResult(files.size(), folderCount, durationMs);
    }

    /** 文件树节点（文件夹 + markdown + 其他类型），从元数据构建。 */
    public List<FileNode> listFileTree() {
        Path vaultRoot = requireRoot();
        List<FileNode> nodes = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(vaultRoot)) {
            for (Path entry : stream) {
                collectNode(vaultRoot, entry, nodes, 0);
            }
        } catch (IOException e) {
            throw new NoteReadFailedException("读取文件树失败: " + e.getMessage());
        }
        nodes.sort(Comparator.comparing(FileNode::path));
        return nodes;
    }

    private void collectNode(Path vaultRoot, Path entry, List<FileNode> nodes, int depth) {
        String name = entry.getFileName().toString();
        if (name.startsWith(".")) {
            return;
        }
        String relative = vaultRoot.relativize(entry).toString();
        if (Files.isDirectory(entry)) {
            nodes.add(new FileNode("folder-" + relative, name, "folder", relative));
            if (depth < 32) {
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(entry)) {
                    for (Path child : stream) {
                        collectNode(vaultRoot, child, nodes, depth + 1);
                    }
                } catch (IOException e) {
                    log.warn("目录不可读: {}", relative);
                }
            }
            return;
        }
        String lower = name.toLowerCase();
        String type = lower.endsWith(".md") ? "markdown"
                : isImage(lower) ? "image" : "other";
        nodes.add(new FileNode("file-" + relative, name, type, relative));
    }

    private boolean isImage(String lowerName) {
        return lowerName.endsWith(".png") || lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg")
                || lowerName.endsWith(".gif") || lowerName.endsWith(".svg") || lowerName.endsWith(".webp");
    }

    /* ---------- 笔记读写 ---------- */

    /** 按相对路径读取原始 Markdown（不存在抛 NOTE_NOT_FOUND）。 */
    public String readRaw(String relativePath) {
        Path target = resolveNote(relativePath);
        try {
            return Files.readString(target, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new NoteReadFailedException("读取笔记失败: " + relativePath);
        }
    }

    /** 原子写回 Markdown：先写临时文件再 MOVE 替换，失败时保留原文件。 */
    public Instant writeRaw(String relativePath, String content) {
        Path target = resolveNote(relativePath);
        Path tmp = target.resolveSibling(target.getFileName() + ".obsidianmind.tmp");
        try {
            Files.writeString(tmp, content, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            try {
                Files.deleteIfExists(tmp);
            } catch (IOException ignored) {
                // 清理临时文件失败不影响主流程
            }
            throw new NoteSaveFailedException("保存笔记失败: " + relativePath);
        }
        log.info("Note saved: {}", relativePath);
        FileMeta meta = FileMeta.of(Path.of(relativePath), target);
        files.put(relativePath, meta);
        return meta.modifiedAt();
    }

    public boolean noteExists(String relativePath) {
        return resolveNoteOrNull(relativePath) != null;
    }

    /** 安全解析：拒绝路径穿越 + 必须存在于 Vault 内。 */
    private Path resolveNote(String relativePath) {
        Path target = resolveNoteOrNull(relativePath);
        if (target == null) {
            throw new NoteNotFoundException("笔记不存在: " + relativePath);
        }
        return target;
    }

    private Path resolveNoteOrNull(String relativePath) {
        Path relative = VaultPaths.sanitizeRelative(relativePath);
        Path vaultRoot = requireRoot();
        Path resolved = VaultPaths.resolveSafe(vaultRoot, relative);
        return Files.exists(resolved) && Files.isRegularFile(resolved) ? resolved : null;
    }

    public List<FileMeta> allNotes() {
        return List.copyOf(files.values());
    }

    public FileMeta noteMeta(String relativePath) {
        return files.get(relativePath);
    }

    public Instant lastScanAt() {
        return lastScanAt;
    }

    public int folderCount() {
        return folderCount;
    }

    /**
     * 扫描结果统计。
     */
    public record ScanResult(int markdownFiles, int folders, long durationMs) {
    }

    /**
     * 文件树节点。
     */
    public record FileNode(String id, String name, String type, String path) {
    }
}
